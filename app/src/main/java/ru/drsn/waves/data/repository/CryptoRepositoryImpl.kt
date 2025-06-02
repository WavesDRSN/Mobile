package ru.drsn.waves.data.repository

import com.google.protobuf.kotlin.toByteString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.drsn.waves.data.datasource.local.crypto.ICryptoLocalDataSource
import ru.drsn.waves.data.utils.SeedPhraseUtil
import ru.drsn.waves.domain.model.crypto.*
import ru.drsn.waves.domain.model.crypto.PublicKey
import ru.drsn.waves.domain.model.crypto.Signature
import ru.drsn.waves.domain.model.profile.DomainUserProfile
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ICryptoRepository
import timber.log.Timber
import java.security.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoRepositoryImpl @Inject constructor(
    private val localDataSource: ICryptoLocalDataSource
) : ICryptoRepository {

    private companion object {
        private const val TAG = "CryptoRepository"
        private const val SIGNATURE_ALGORITHM = "Ed25519" // или "EdDSA"
    }

    // Кэш для загруженных ключей для производительности
    @Volatile // Обеспечивает видимость записи между потоками
    private var currentKeyPair: KeyPair? = null
    private val initMutex = Mutex() // Для потокобезопасной инициализации и доступа к кэшу

    override suspend fun initializeKeysIfNeeded(): Result<InitializationResult.KeysLoaded, CryptoError> {
        initMutex.withLock {
            if (currentKeyPair != null) {
                Timber.tag(TAG).d("Ключи уже в кэше.")
                return Result.Success(InitializationResult.KeysLoaded)
            }

            Timber.tag(TAG).d("Попытка загрузки существующих ключей...")
            return try {
                if (localDataSource.keyPairExists()) {
                    Timber.tag(TAG).i("Пара ключей найдена в хранилище. Загрузка...")
                    val loadedKeyPair = localDataSource.loadKeyPair()
                    if (loadedKeyPair != null) {
                        currentKeyPair = loadedKeyPair
                        Timber.tag(TAG).i("Пара ключей успешно загружена в кэш.")
                        Result.Success(InitializationResult.KeysLoaded)
                    } else {
                        Timber.tag(TAG).e("Пара ключей существует в хранилище, но не удалось загрузить (возможно, повреждены).")
                        Result.Error(CryptoError.LoadError("Не удалось загрузить существующие ключи.", null))
                    }
                } else {
                    Timber.tag(TAG).i("Пара ключей не найдена в хранилище.")
                    Result.Error(CryptoError.KeyNotFound)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка во время попытки загрузки ключей")
                currentKeyPair = null
                Result.Error(CryptoError.LoadError("Исключение во время загрузки ключей", e))
            }
        }
    }

    /**
     * Генерирует новую пару ключей Ed25519 из Seed, полученного из мнемонической фразы,
     * и сохраняет их в локальном хранилище.
     * Перезаписывает существующие ключи, если они есть.
     *
     * @param mnemonicPhrase Мнемоническая фраза для генерации сида.
     * @return Result с Unit в случае успеха или CryptoError при ошибке.
     */
    override suspend fun regenerateKeysFromSeed(mnemonicPhrase: MnemonicPhrase): Result<Unit, CryptoError> {
        initMutex.withLock { // Блокируем для безопасного изменения currentKeyPair и сохранения
            Timber.tag(TAG).i("Регенерация ключей из сид-фразы...")
            return try {
                val seed = SeedPhraseUtil.generateSeedFromMnemonic(mnemonicPhrase.value)
                val keyPair = SeedPhraseUtil.generateEd25519KeyPair(seed)

                // Удаляем старые ключи перед сохранением новых, чтобы избежать конфликтов
                // или просто перезаписываем, если storeKeyPair это поддерживает.
                // localDataSource.deleteKeyPair()

                val stored = localDataSource.storeKeyPair(keyPair)
                if (stored) {
                    currentKeyPair = keyPair // Обновляем кэш
                    Timber.tag(TAG).i("Ключи успешно регенерированы из сид-фразы и сохранены.")
                    Result.Success(Unit)
                } else {
                    Timber.tag(TAG).e("Не удалось сохранить регенерированные ключи.")
                    Result.Error(CryptoError.StoreError("Не удалось сохранить регенерированные ключи", null))
                }
            } catch (e: IllegalArgumentException) { // От SeedPhraseUtil, если фраза невалидна
                Timber.tag(TAG).e(e, "Ошибка генерации сида из мнемоники: неверная фраза")
                Result.Error(CryptoError.GenerationError("Неверная сид-фраза", e))
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка при регенерации ключей из сид-фразы")
                Result.Error(CryptoError.GenerationError("Ошибка регенерации ключей", e))
            }
        }
    }
    override suspend fun saveUserProfile(userProfile: DomainUserProfile): Result<Unit, CryptoError> {
        try {
            // Сохраняем ник, если он изменился или не был сохранен
            val currentSavedNickname = localDataSource.loadUserNickname()
            if (currentSavedNickname != userProfile.userId) {
                if (!localDataSource.saveUserNickname(userProfile.userId)) {
                    return Result.Error(CryptoError.StoreError("Не удалось сохранить/обновить никнейм из профиля", null))
                }
            }

            var success = localDataSource.saveProfileDisplayName(userProfile.displayName)
            success = success && localDataSource.saveProfileStatusMessage(userProfile.statusMessage ?: "") // Сохраняем пустую строку, если null
            success = success && localDataSource.saveProfileAvatarUri(userProfile.avatarUri ?: "")       // Аналогично для URI
            success = success && localDataSource.saveProfileLastEditTimestamp(userProfile.lastLocalEditTimestamp) // Сохраняем timestamp

            return if (success) Result.Success(Unit)
            else Result.Error(CryptoError.StoreError("Не удалось сохранить одно или несколько полей профиля", null))
        } catch (e: Exception) {
            return Result.Error(CryptoError.StoreError("Исключение при сохранении профиля", e))
        }
    }

    override suspend fun loadUserProfile(): Result<DomainUserProfile, CryptoError> {
        try {
            val userId = localDataSource.loadUserNickname()
                ?: return Result.Error(CryptoError.NicknameNotFound("Никнейм не найден"))

            val displayName = localDataSource.loadProfileDisplayName() ?: userId
            val statusMessage = localDataSource.loadProfileStatusMessage()
            val avatarUri = localDataSource.loadProfileAvatarUri()
            val lastEditTimestamp = localDataSource.loadProfileLastEditTimestamp() ?: 0L // Если нет, считаем очень старым

            return Result.Success(
                DomainUserProfile(
                    userId = userId,
                    displayName = displayName,
                    statusMessage = statusMessage?.ifEmpty { null }, // Восстанавливаем null, если сохраняли пустую строку
                    avatarUri = avatarUri?.ifEmpty { null },
                    lastLocalEditTimestamp = lastEditTimestamp
                )
            )
        } catch (e: Exception) {
            return Result.Error(CryptoError.LoadError("Исключение при загрузке профиля", e))
        }
    }

    override suspend fun deleteUserProfile(): Result<Unit, CryptoError> {
        return try {
            if (localDataSource.clearUserProfileData()) Result.Success(Unit) // clearUserProfileData теперь удаляет и timestamp
            else Result.Error(CryptoError.DeletionError("Не удалось очистить данные профиля", null))
        } catch (e: Exception) {
            Result.Error(CryptoError.DeletionError("Исключение при удалении данных профиля", e))
        }
    }
    /**
     * Генерирует совершенно новую пару ключей Ed25519 и мнемоническую фразу,
     * сохраняет ключи в локальном хранилище.
     * Перезаписывает существующие ключи.
     *
     * @return Result с MnemonicPhrase для отображения пользователю или CryptoError при ошибке.
     */
    override suspend fun generateAndStoreNewKeys(): Result<InitializationResult.KeysGenerated, CryptoError> {
        return try {
            val mnemonic = SeedPhraseUtil.generateMnemonic()
            val seed = SeedPhraseUtil.generateSeedFromMnemonic(mnemonic)
            val keyPair = SeedPhraseUtil.generateEd25519KeyPair(seed)

            val stored = localDataSource.storeKeyPair(keyPair)
            if (stored) {
                currentKeyPair = keyPair // Сохраняем в кэш
                Timber.tag(TAG).i("New key pair generated and stored successfully.")
                Result.Success(InitializationResult.KeysGenerated(MnemonicPhrase(mnemonic)))
            } else {
                Timber.tag(TAG).e("Failed to store newly generated key pair.")
                Result.Error(CryptoError.StoreError("Failed to store newly generated keys"))
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to generate or store new keys")
            Result.Error(CryptoError.GenerationError("Failed during key generation/storage", e))
        }
    }

    override suspend fun getPublicKey(): Result<PublicKey, CryptoError> {
        val keyPair = getKeyPairOrError() ?: return Result.Error(CryptoError.KeyNotFound) // KeyNotFound или другая ошибка из getKeyPairOrError

        return try {
            Result.Success(keyPair.public.encoded.toByteString())
        } catch (e: Exception) {
            Result.Error(CryptoError.Unknown("Failed to encode public key", e))
        }
    }

    override suspend fun getJavaPublicKey(): Result<java.security.PublicKey, CryptoError> {
        val keyPair = getKeyPairOrError() ?: return Result.Error(CryptoError.KeyNotFound)
        return Result.Success(keyPair.public)
    }

    override suspend fun signData(data: ByteArray): Result<Signature, CryptoError> {
        val keyPair = getKeyPairOrError() ?: return Result.Error(CryptoError.KeyNotFound)

        return try {
            val signature: java.security.Signature = java.security.Signature.getInstance(
                SIGNATURE_ALGORITHM
            )
            signature.initSign(keyPair.private)
            signature.update(data)
            val signedBytes = signature.sign()
            Result.Success(signedBytes.toByteString())
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error signing data")
            Result.Error(CryptoError.SigningError("Failed to sign data", e))
        }
    }

    override suspend fun verifySignature(data: ByteArray, signature: Signature): Result<Boolean, CryptoError> {
        val keyPair = getKeyPairOrError() ?: return Result.Error(CryptoError.KeyNotFound)

        return try {
            val verifier: java.security.Signature = java.security.Signature.getInstance(
                SIGNATURE_ALGORITHM
            )
            verifier.initVerify(keyPair.public)
            verifier.update(data)
            val isValid = verifier.verify(signature.toByteArray())
            Result.Success(isValid)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error verifying signature")
            // Можно вернуть Result.Success(false) или ошибку
            Result.Error(CryptoError.VerificationError("Failed to verify signature", e))
        }
    }

    override suspend fun deleteKeys(): Result<Unit, CryptoError> {
        initMutex.withLock {
            currentKeyPair = null // Очищаем кэш
            return try {
                val deleted = localDataSource.deleteKeyPair()
                if (deleted) {
                    Timber.tag(TAG).i("Keys deleted successfully.")
                    Result.Success(Unit)
                } else {
                    Timber.tag(TAG).w("Delete operation reported failure (keys might not have existed).")
                    // Можно считать успехом, если ключей и так не было, или ошибкой
                    Result.Error(CryptoError.DeletionError("Failed to delete keys from storage", null))
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error deleting keys")
                Result.Error(CryptoError.DeletionError("Exception during key deletion", e))
            }
        }
    }

    override suspend fun isInitialized(): Boolean {
        // Проверяем наличие ключей в кэше (самый быстрый способ)
        // или обращаемся к DataSource, если кэш пуст
        initMutex.withLock {
            return currentKeyPair != null || localDataSource.keyPairExists()
        }
    }

    // Вспомогательная функция для получения кэшированной пары ключей или ошибки
    private suspend fun getKeyPairOrError(): KeyPair? {
        initMutex.withLock { // Защищаем доступ к кэшу
            if (currentKeyPair == null) {
                // Попытка загрузить, если не в кэше (на случай, если инициализация не была вызвана явно)
                Timber.tag(TAG).w("KeyPair accessed before explicit initialization or after error. Attempting lazy load.")
                if (localDataSource.keyPairExists()) {
                    currentKeyPair = localDataSource.loadKeyPair()
                }
            }
            if (currentKeyPair == null) {
                Timber.tag(TAG).e("Cannot perform operation: Crypto keys are not available (not initialized or failed to load).")
            }
            return currentKeyPair
        }
    }

    override suspend fun saveAuthToken(token: AuthToken): Result<Unit, CryptoError> {
        return try {
            val success = localDataSource.saveAuthToken(token)
            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(CryptoError.StoreError("Не удалось сохранить токен в DataSource"))
            }
        } catch (e: Exception) {
            Result.Error(CryptoError.StoreError("Исключение при сохранении токена", e))
        }
    }

    override suspend fun getAuthToken(): Result<AuthToken, CryptoError> {
        return try {
            val token = localDataSource.loadAuthToken()
            if (token != null) {
                Result.Success(token)
            } else {
                Result.Error(CryptoError.KeyNotFound) // Используем KeyNotFound как "токен не найден"
            }
        } catch (e: Exception) {
            Result.Error(CryptoError.LoadError("Исключение при загрузке токена", e))
        }
    }

    override suspend fun deleteAuthToken(): Result<Unit, CryptoError> {
        return try {
            val success = localDataSource.deleteAuthToken()
            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(CryptoError.DeletionError("Не удалось удалить токен из DataSource"))
            }
        } catch (e: Exception) {
            Result.Error(CryptoError.DeletionError("Исключение при удалении токена", e))
        }
    }

    override suspend fun saveUserNickname(nickname: String): Result<Unit, CryptoError> {
        return try {
            val success = localDataSource.saveUserNickname(nickname)
            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(CryptoError.StoreError("Не удалось сохранить никнейм в DataSource", null))
            }
        } catch (e: Exception) {
            Result.Error(CryptoError.StoreError("Исключение при сохранении никнейма", e))
        }
    }

    override suspend fun getUserNickname(): Result<String, CryptoError> {
        return try {
            val nicknameString = localDataSource.loadUserNickname()
            if (nicknameString != null) {
                Result.Success(nicknameString)
            } else {
                Result.Error(CryptoError.NicknameNotFound("Ошибка: имя пользователя не найдено."))
            }
        } catch (e: Exception) {
            Result.Error(CryptoError.LoadError("Исключение при загрузке никнейма", e))
        }
    }

    override suspend fun deleteUserNickname(): Result<Unit, CryptoError> {
        return try {
            val success = localDataSource.deleteUserNickname()
            if (success) {
                Result.Success(Unit)
            } else {
                Result.Error(CryptoError.DeletionError("Не удалось удалить никнейм из DataSource", null))
            }
        } catch (e: Exception) {
            Result.Error(CryptoError.DeletionError("Исключение при удалении никнейма", e))
        }
    }
}