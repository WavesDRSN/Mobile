package ru.drsn.waves.data.repository

import com.google.protobuf.kotlin.toByteString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.drsn.waves.crypto.SeedPhraseUtil // Используем существующий Util
import ru.drsn.waves.data.datasource.local.crypto.ICryptoLocalDataSource
import ru.drsn.waves.domain.model.crypto.*
import ru.drsn.waves.domain.model.crypto.PublicKey
import ru.drsn.waves.domain.model.crypto.Signature
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

    override suspend fun initializeKeysIfNeeded(): Result<InitializationResult, CryptoError> {
        // Используем Mutex, чтобы избежать гонки состояний при одновременных вызовах
        initMutex.withLock {
            // Если ключи уже в кэше, значит инициализация прошла успешно
            if (currentKeyPair != null) {
                Timber.tag(TAG).d("Already initialized (keys in cache).")
                return Result.Success(InitializationResult.KeysLoaded)
            }

            Timber.tag(TAG).d("Initializing keys...")
            return try {
                if (localDataSource.keyPairExists()) {
                    Timber.tag(TAG).i("Key pair found in storage. Loading...")
                    val loadedKeyPair = localDataSource.loadKeyPair()
                    if (loadedKeyPair != null) {
                        currentKeyPair = loadedKeyPair // Сохраняем в кэш
                        Timber.tag(TAG).i("Key pair loaded successfully.")
                        Result.Success(InitializationResult.KeysLoaded)
                    } else {
                        // Ключи вроде есть, но загрузить не удалось
                        Timber.tag(TAG).e("Key pair exists in storage but failed to load.")
                        // Попробуем удалить "сломанные" ключи перед генерацией новых
                        localDataSource.deleteKeyPair()
                        generateAndStoreNewKeys() // Генерируем новые
                    }
                } else {
                    Timber.tag(TAG).i("No key pair found in storage. Generating new ones...")
                    generateAndStoreNewKeys() // Генерируем новые
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Initialization failed")
                currentKeyPair = null // Сбрасываем кэш при ошибке
                Result.Error(CryptoError.InitializationError("Failed during initialization", e))
            }
        }
    }

    // Вспомогательная функция для генерации и сохранения новых ключей
    private suspend fun generateAndStoreNewKeys(): Result<InitializationResult, CryptoError> {
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
}