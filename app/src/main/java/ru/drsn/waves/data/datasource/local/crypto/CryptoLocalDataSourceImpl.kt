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
import java.io.IOException
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
        private const val AUTH_TOKEN_KEY = "auth_jwt_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_KEY_ALIAS = "ru.drsn.waves.crypto.AES_WRAPPER_KEY_V2"
        private const val PREFS_FILENAME = "ru.drsn.waves.crypto.key_storage"
        private const val ENCRYPTED_PRIVATE_KEY = "encrypted_private_key_ed25519"
        private const val STORED_PUBLIC_KEY = "public_key_ed25519"
        private const val USER_NICKNAME_KEY = "user_nickname_key"
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

    override suspend fun saveAuthToken(token: AuthToken): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.edit().putString(AUTH_TOKEN_KEY, token).apply()
            Timber.tag(TAG).i("Auth token сохранен.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось сохранить auth token.")
            false
        }
    }

    override suspend fun loadAuthToken(): AuthToken? = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.getString(AUTH_TOKEN_KEY, null).also {
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
            sharedPreferences.edit().remove(AUTH_TOKEN_KEY).apply()
            Timber.tag(TAG).i("Auth token удален.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось удалить auth token.")
            false
        }
    }


    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val sharedPreferences by lazy {
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
            sharedPreferences.edit()
                .putString(ENCRYPTED_PRIVATE_KEY, base64EncryptedPrivateKey)
                .putString(STORED_PUBLIC_KEY, base64PublicKey)
                .apply()
            Timber.tag(TAG).i("KeyPair ($KEY_ALGORITHM) stored successfully.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to encrypt and store $KEY_ALGORITHM key pair")
            sharedPreferences.edit()
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
        val base64EncryptedData = sharedPreferences.getString(ENCRYPTED_PRIVATE_KEY, null) ?: return null
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
        val base64PublicKey = sharedPreferences.getString(STORED_PUBLIC_KEY, null) ?: return null
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
        sharedPreferences.contains(ENCRYPTED_PRIVATE_KEY) && sharedPreferences.contains(STORED_PUBLIC_KEY)
    }

    override suspend fun deleteKeyPair(): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.edit()
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
            sharedPreferences.edit().putString(USER_NICKNAME_KEY, nickname).apply()
            Timber.tag(TAG).i("Никнейм пользователя '$nickname' сохранен.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось сохранить никнейм пользователя.")
            false
        }
    }

    override suspend fun loadUserNickname(): String? = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.getString(USER_NICKNAME_KEY, null).also {
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
            sharedPreferences.edit().remove(USER_NICKNAME_KEY).apply()
            Timber.tag(TAG).i("Никнейм пользователя удален.")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось удалить никнейм пользователя.")
            false
        }
    }

}
