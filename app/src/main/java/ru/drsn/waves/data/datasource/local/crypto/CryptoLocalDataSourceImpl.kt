package ru.drsn.waves.data.datasource.local.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import ru.drsn.waves.domain.model.crypto.AuthToken
import timber.log.Timber
import java.security.*
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoLocalDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ICryptoLocalDataSource {

    companion object {

        private const val KEY_PROFILE_DISPLAY_NAME = "profile_display_name"
        private const val KEY_PROFILE_STATUS_MESSAGE = "profile_status_message"
        private const val KEY_PROFILE_AVATAR_URI = "profile_avatar_uri"
        private const val KEY_PROFILE_LAST_EDIT_TIMESTAMP = "profile_last_edit_timestamp"

        private const val AUTH_TOKEN_KEY = "auth_jwt_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_KEY_ALIAS = "ru.drsn.waves.crypto.AES_WRAPPER_KEY_V2"
        private const val PREFS_FILENAME = "ru.drsn.waves.crypto.key_storage"
        private const val USER_PROFILE_PREFS_FILENAME = "ru.drsn.waves.profile_storage"
        private const val ENCRYPTED_PRIVATE_KEY = "encrypted_private_key_ed25519"
        private const val STORED_PUBLIC_KEY = "public_key_ed25519"
        private const val USER_NICKNAME_KEY = "user_nickname_key"
        private const val ENCRYPTED_CHAT_ENCRYPTION_KEY = "encrypted_chat_key_v1"
        private const val AES_MODE = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$AES_MODE/$BLOCK_MODE/$PADDING"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val KEY_ALGORITHM = "Ed25519"
        private const val TAG = "CryptoLocalDataSource"

        init {
            // Убедимся, что BouncyCastle провайдер добавлен
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
                Timber.tag(TAG).i("BouncyCastle provider added.")
            } else {
                Timber.tag(TAG).i("BouncyCastle provider already exists.")
            }
        }
    }


    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val profileSharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS + "_profile") // Другой алиас для мастер-ключа профиля
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context, USER_PROFILE_PREFS_FILENAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create EncryptedSharedPreferences for profile. Using fallback.")
            context.getSharedPreferences(USER_PROFILE_PREFS_FILENAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    private val cryptoSharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create EncryptedSharedPreferences. Using fallback (Not Recommended).")
            context.getSharedPreferences(PREFS_FILENAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    override suspend fun saveProfileDisplayName(name: String): Boolean = withContext(Dispatchers.IO) {
        try { profileSharedPreferences.edit().putString(KEY_PROFILE_DISPLAY_NAME, name).apply(); true }
        catch (e: Exception) { Timber.tag(TAG).e(e, "Ошибка сохранения displayName"); false }
    }
    override suspend fun loadProfileDisplayName(): String? = withContext(Dispatchers.IO) {
        try { profileSharedPreferences.getString(KEY_PROFILE_DISPLAY_NAME, null) }
        catch (e: Exception) { Timber.tag(TAG).e(e, "Ошибка загрузки displayName"); null }
    }
    override suspend fun saveProfileStatusMessage(status: String): Boolean = withContext(Dispatchers.IO) {
        try { profileSharedPreferences.edit().putString(KEY_PROFILE_STATUS_MESSAGE, status).apply(); true }
        catch (e: Exception) { Timber.tag(TAG).e(e, "Ошибка сохранения statusMessage"); false }
    }
    override suspend fun loadProfileStatusMessage(): String? = withContext(Dispatchers.IO) {
        try { profileSharedPreferences.getString(KEY_PROFILE_STATUS_MESSAGE, null) }
        catch (e: Exception) { Timber.tag(TAG).e(e, "Ошибка загрузки statusMessage"); null }
    }
    override suspend fun saveProfileAvatarUri(uri: String): Boolean = withContext(Dispatchers.IO) {
        try { profileSharedPreferences.edit().putString(KEY_PROFILE_AVATAR_URI, uri).apply(); true }
        catch (e: Exception) { Timber.tag(TAG).e(e, "Ошибка сохранения avatarUri"); false }
    }
    override suspend fun loadProfileAvatarUri(): String? = withContext(Dispatchers.IO) {
        try { profileSharedPreferences.getString(KEY_PROFILE_AVATAR_URI, null) }
        catch (e: Exception) { Timber.tag(TAG).e(e, "Ошибка загрузки avatarUri"); null }
    }
    override suspend fun saveProfileLastEditTimestamp(timestamp: Long): Boolean = withContext(Dispatchers.IO) {
        try { profileSharedPreferences.edit().putLong(KEY_PROFILE_LAST_EDIT_TIMESTAMP, timestamp).apply(); true }
        catch (e: Exception) { Timber.tag(TAG).e(e, "Ошибка сохранения lastEditTimestamp"); false }
    }
    override suspend fun loadProfileLastEditTimestamp(): Long? = withContext(Dispatchers.IO) {
        try {
            if (profileSharedPreferences.contains(KEY_PROFILE_LAST_EDIT_TIMESTAMP)) {
                profileSharedPreferences.getLong(KEY_PROFILE_LAST_EDIT_TIMESTAMP, 0L)
            } else {
                null
            }
        } catch (e: Exception) { Timber.tag(TAG).e(e, "Ошибка загрузки lastEditTimestamp"); null }
    }

    override suspend fun clearUserProfileData(): Boolean = withContext(Dispatchers.IO) {
        try {
            profileSharedPreferences.edit()
                .remove(KEY_PROFILE_DISPLAY_NAME)
                .remove(KEY_PROFILE_STATUS_MESSAGE)
                .remove(KEY_PROFILE_AVATAR_URI)
                .remove(KEY_PROFILE_LAST_EDIT_TIMESTAMP) // Очищаем и timestamp
                .apply()
            true
        } catch (e: Exception) { false }
    }

    override suspend fun wrapDataWithKeystoreKey(dataToWrap: ByteArray, keystoreWrappingKeyAlias: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val wrappingKey = getOrCreateAesSecretKey()
            if (wrappingKey == null) {
                Timber.tag(TAG).e("Ключ-обертка '$keystoreWrappingKeyAlias' недоступен.")
                return@withContext null
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey) // IV генерируется автоматически
            val iv = cipher.iv
            if (iv == null || iv.size != GCM_IV_LENGTH_BYTES) {
                Timber.tag(TAG).e("Некорректный IV при шифровании данных ключом-оберткой.")
                return@withContext null
            }
            val encryptedData = cipher.doFinal(dataToWrap)
            val result = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, result, 0, iv.size)
            System.arraycopy(encryptedData, 0, result, iv.size, encryptedData.size)
            result
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка при шифровании данных ключом-оберткой '$keystoreWrappingKeyAlias'.")
            null
        }
    }

    override suspend fun unwrapDataWithKeystoreKey(wrappedDataWithIv: ByteArray, keystoreWrappingKeyAlias: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (wrappedDataWithIv.size <= GCM_IV_LENGTH_BYTES) {
                Timber.tag(TAG).e("Некорректная длина зашифрованных данных (слишком короткие для IV).")
                return@withContext null
            }
            val wrappingKey = getOrCreateAesSecretKey()
            if (wrappingKey == null) {
                Timber.tag(TAG).e("Ключ-обертка '$keystoreWrappingKeyAlias' недоступен.")
                return@withContext null
            }

            val iv = wrappedDataWithIv.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val encryptedData = wrappedDataWithIv.copyOfRange(GCM_IV_LENGTH_BYTES, wrappedDataWithIv.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, spec)
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка при дешифровании данных ключом-оберткой '$keystoreWrappingKeyAlias'.")
            null
        }
    }


    override suspend fun saveAuthToken(token: AuthToken): Boolean = withContext(Dispatchers.IO) {
        try {
            cryptoSharedPreferences.edit().putString(AUTH_TOKEN_KEY, token).apply()
            Timber.tag(TAG).i("Auth token сохранен.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось сохранить auth token.")
            false
        }
    }

    override suspend fun loadAuthToken(): AuthToken? = withContext(Dispatchers.IO) {
        try {
            cryptoSharedPreferences.getString(AUTH_TOKEN_KEY, null).also {
                if (it != null) Timber.tag(TAG).d("Auth token загружен.")
                else Timber.tag(TAG).d("Auth token не найден.")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось загрузить auth token.")
            null
        }
    }

    override suspend fun deleteAuthToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            cryptoSharedPreferences.edit().remove(AUTH_TOKEN_KEY).apply()
            Timber.tag(TAG).i("Auth token удален.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось удалить auth token.")
            false
        }
    }

    private fun getOrCreateAesSecretKey(): SecretKey? {
        return try {
            if (!keyStore.containsAlias(AES_KEY_ALIAS)) {
                Timber.tag(TAG).i("AES key '$AES_KEY_ALIAS' not found, generating new one.")
                val keyGenerator = KeyGenerator.getInstance(AES_MODE, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    AES_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(128)
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
            keyStore.getKey(AES_KEY_ALIAS, null) as SecretKey
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get or create AES key from KeyStore")
            null
        }
    }

    override suspend fun storeKeyPair(keyPair: KeyPair): Boolean = withContext(Dispatchers.IO) {
        if (keyPair.private.algorithm != KEY_ALGORITHM || keyPair.public.algorithm != KEY_ALGORITHM) {
            Timber.tag(TAG).e("Invalid key pair algorithm provided. Expected $KEY_ALGORITHM.")
            return@withContext false
        }
        val aesSecretKey = getOrCreateAesSecretKey() ?: return@withContext false

        try {
            // 1. Шифруем приватный ключ
            val privateKeyBytes = keyPair.private.encoded ?: return@withContext false
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, aesSecretKey)
            val encryptedPrivateKeyBytes = cipher.doFinal(privateKeyBytes)
            val iv = cipher.iv ?: return@withContext false
            if (iv.size != GCM_IV_LENGTH_BYTES) return@withContext false

            val combinedPrivateKey = ByteArray(GCM_IV_LENGTH_BYTES + encryptedPrivateKeyBytes.size)
            System.arraycopy(iv, 0, combinedPrivateKey, 0, GCM_IV_LENGTH_BYTES)
            System.arraycopy(encryptedPrivateKeyBytes, 0, combinedPrivateKey, GCM_IV_LENGTH_BYTES, encryptedPrivateKeyBytes.size)
            val base64EncryptedPrivateKey = Base64.encodeToString(combinedPrivateKey, Base64.NO_WRAP)

            // 2. Публичный ключ
            val publicKeyBytes = keyPair.public.encoded ?: return@withContext false
            val base64PublicKey = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)

            // 3. Сохраняем
            cryptoSharedPreferences.edit()
                .putString(ENCRYPTED_PRIVATE_KEY, base64EncryptedPrivateKey)
                .putString(STORED_PUBLIC_KEY, base64PublicKey)
                .apply()
            Timber.tag(TAG).i("KeyPair ($KEY_ALGORITHM) stored successfully.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to encrypt and store $KEY_ALGORITHM key pair")
            cryptoSharedPreferences.edit()
                .remove(ENCRYPTED_PRIVATE_KEY)
                .remove(STORED_PUBLIC_KEY)
                .apply()
            false
        }
    }

    override suspend fun loadKeyPair(): KeyPair? = withContext(Dispatchers.IO) {
        val privateKey = loadPrivateKeyInternal() ?: return@withContext null
        val publicKey = loadPublicKeyInternal() ?: return@withContext null
        KeyPair(publicKey, privateKey)
    }

    private fun loadPrivateKeyInternal(): PrivateKey? {
        val base64EncryptedData = cryptoSharedPreferences.getString(ENCRYPTED_PRIVATE_KEY, null) ?: return null
        val aesSecretKey = getOrCreateAesSecretKey() ?: return null

        return try {
            val combined = Base64.decode(base64EncryptedData, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH_BYTES) return null

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, aesSecretKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
            val keySpec = PKCS8EncodedKeySpec(decryptedBytes)
            keyFactory.generatePrivate(keySpec)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load and decrypt private key ($KEY_ALGORITHM)")
            null
        }
    }

    private fun loadPublicKeyInternal(): PublicKey? {
        val base64PublicKey = cryptoSharedPreferences.getString(STORED_PUBLIC_KEY, null) ?: return null
        return try {
            val publicKeyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load public key ($KEY_ALGORITHM)")
            null
        }
    }


    override suspend fun keyPairExists(): Boolean = withContext(Dispatchers.IO) {
        cryptoSharedPreferences.contains(ENCRYPTED_PRIVATE_KEY) && cryptoSharedPreferences.contains(STORED_PUBLIC_KEY)
    }

    override suspend fun deleteKeyPair(): Boolean = withContext(Dispatchers.IO) {
        try {
            cryptoSharedPreferences.edit()
                .remove(ENCRYPTED_PRIVATE_KEY)
                .remove(STORED_PUBLIC_KEY)
                .apply()
            Timber.tag(TAG).i("Keys removed from SharedPreferences.")

            if (keyStore.containsAlias(AES_KEY_ALIAS)) {
                keyStore.deleteEntry(AES_KEY_ALIAS)
                Timber.tag(TAG).i("AES key alias '$AES_KEY_ALIAS' deleted from KeyStore.")
            } else {
                Timber.tag(TAG).w("AES key alias '$AES_KEY_ALIAS' not found for deletion.")
            }
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error deleting keys")
            false
        }
    }

    override suspend fun saveUserNickname(nickname: String): Boolean = withContext(Dispatchers.IO) {
        try {
            cryptoSharedPreferences.edit().putString(USER_NICKNAME_KEY, nickname).apply()
            Timber.tag(TAG).i("Никнейм пользователя '$nickname' сохранен.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось сохранить никнейм пользователя.")
            false
        }
    }

    override suspend fun loadUserNickname(): String? = withContext(Dispatchers.IO) {
        try {
            cryptoSharedPreferences.getString(USER_NICKNAME_KEY, null).also {
                if (it != null) Timber.tag(TAG).d("Никнейм пользователя '$it' загружен.")
                else Timber.tag(TAG).d("Сохраненный никнейм пользователя не найден.")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось загрузить никнейм пользователя.")
            null
        }
    }

    override suspend fun deleteUserNickname(): Boolean = withContext(Dispatchers.IO) {
        try {
            cryptoSharedPreferences.edit().remove(USER_NICKNAME_KEY).apply()
            Timber.tag(TAG).i("Никнейм пользователя удален.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось удалить никнейм пользователя.")
            false
        }
    }

    override suspend fun saveEncryptedChatKey(encryptedChatKeyB64: String): Boolean = withContext(Dispatchers.IO) {
        try {
            cryptoSharedPreferences.edit().putString(ENCRYPTED_CHAT_ENCRYPTION_KEY, encryptedChatKeyB64).apply()
            Timber.tag(TAG).i("Зашифрованный ключ шифрования чатов (CEK) сохранен.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось сохранить зашифрованный CEK.")
            false
        }
    }

    override suspend fun loadEncryptedChatKey(): String? = withContext(Dispatchers.IO) {
        try {
            cryptoSharedPreferences.getString(ENCRYPTED_CHAT_ENCRYPTION_KEY, null).also {
                if (it != null) Timber.tag(TAG).d("Зашифрованный CEK загружен.")
                else Timber.tag(TAG).d("Зашифрованный CEK не найден.")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось загрузить зашифрованный CEK.")
            null
        }
    }

    override suspend fun deleteEncryptedChatKey(): Boolean = withContext(Dispatchers.IO) {
        try {
            cryptoSharedPreferences.edit().remove(ENCRYPTED_CHAT_ENCRYPTION_KEY).apply()
            Timber.tag(TAG).i("Зашифрованный CEK удален.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось удалить зашифрованный CEK.")
            false
        }
    }

}
