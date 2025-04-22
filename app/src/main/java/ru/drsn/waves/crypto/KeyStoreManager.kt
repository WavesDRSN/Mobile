package ru.drsn.waves.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec // Для публичного ключа
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyStoreManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        // Алиас для КЛЮЧА AES в Android Keystore
        private const val AES_KEY_ALIAS = "ru.drsn.waves.crypto.AES_WRAPPER_KEY_V2" // Изменил, чтобы избежать конфликта со старой версией, если она была
        // Имя файла для безопасного хранения ключей
        private const val PREFS_FILENAME = "ru.drsn.waves.crypto.key_storage"
        // Ключи в SharedPreferences
        private const val ENCRYPTED_PRIVATE_KEY = "encrypted_private_key_ed25519"
        private const val STORED_PUBLIC_KEY = "public_key_ed25519" // Ключ для хранения публичного ключа

        // Параметры AES/GCM
        private const val AES_MODE = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$AES_MODE/$BLOCK_MODE/$PADDING"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        private const val TAG = "KeyStoreManager"

        // Алгоритм целевой пары ключей
        private const val KEY_ALGORITHM = "Ed25519"

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

    // EncryptedSharedPreferences для хранения данных
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
            Timber.tag(TAG).e(e, "Failed to create EncryptedSharedPreferences. Falling back to regular SharedPreferences (Not Recommended).")
            // Фоллбэк на обычные SharedPreferences (НЕ РЕКОМЕНДУЕТСЯ для продакшена)
            // Это просто чтобы пример работал, если EncryptedSharedPreferences не инициализируется
            context.getSharedPreferences(PREFS_FILENAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Получает или генерирует ключ AES в Android Keystore.
     */
    private fun getOrCreateAesSecretKey(): SecretKey? {
        return try {
            if (!keyStore.containsAlias(AES_KEY_ALIAS)) {
                Timber.tag(TAG).i("AES key '$AES_KEY_ALIAS' not found, generating new one.")
                val keyGenerator = KeyGenerator.getInstance(AES_MODE, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    AES_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(128) // AES-128
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

    /**
     * Сохраняет пару ключей Ed25519: приватный ключ шифруется с помощью AES,
     * публичный ключ сохраняется в открытом виде (Base64).
     * @param keyPair Пара ключей Ed25519 для сохранения.
     * @return true, если сохранение успешно, false в противном случае.
     */
    suspend fun storeKeyPair(keyPair: KeyPair): Boolean = withContext(Dispatchers.IO) {
        if (keyPair.private.algorithm != KEY_ALGORITHM || keyPair.public.algorithm != KEY_ALGORITHM) {
            Timber.tag(TAG).e("Invalid key pair algorithm provided. Expected $KEY_ALGORITHM.")
            return@withContext false
        }

        val aesSecretKey = getOrCreateAesSecretKey()
        if (aesSecretKey == null) {
            Timber.tag(TAG).e("Failed to obtain AES key for encryption.")
            return@withContext false
        }

        try {
            // 1. Шифруем приватный ключ
            val privateKeyBytes = keyPair.private.encoded
            if (privateKeyBytes == null) {
                Timber.tag(TAG).e("Could not get encoded bytes from the private key.")
                return@withContext false
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, aesSecretKey) // IV генерируется автоматически
            val encryptedPrivateKeyBytes = cipher.doFinal(privateKeyBytes)
            val iv = cipher.iv

            if (iv == null || iv.size != GCM_IV_LENGTH_BYTES) {
                Timber.tag(TAG).e("Generated IV is invalid for private key encryption.")
                return@withContext false
            }
            // Собираем IV + шифротекст приватного ключа
            val combinedPrivateKey = ByteArray(GCM_IV_LENGTH_BYTES + encryptedPrivateKeyBytes.size)
            System.arraycopy(iv, 0, combinedPrivateKey, 0, GCM_IV_LENGTH_BYTES)
            System.arraycopy(encryptedPrivateKeyBytes, 0, combinedPrivateKey, GCM_IV_LENGTH_BYTES, encryptedPrivateKeyBytes.size)
            val base64EncryptedPrivateKey = Base64.encodeToString(combinedPrivateKey, Base64.NO_WRAP)

            // 2. Получаем байты публичного ключа
            val publicKeyBytes = keyPair.public.encoded
            if (publicKeyBytes == null) {
                Timber.tag(TAG).e("Could not get encoded bytes from the public key.")
                return@withContext false
            }
            val base64PublicKey = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)

            // 3. Сохраняем оба ключа в SharedPreferences
            sharedPreferences.edit()
                .putString(ENCRYPTED_PRIVATE_KEY, base64EncryptedPrivateKey)
                .putString(STORED_PUBLIC_KEY, base64PublicKey)
                .apply()

            Timber.tag(TAG).i("KeyPair ($KEY_ALGORITHM) stored successfully (Private Encrypted, Public Plain).")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to encrypt and store $KEY_ALGORITHM key pair")
            // Почистим на случай частичной записи
            sharedPreferences.edit()
                .remove(ENCRYPTED_PRIVATE_KEY)
                .remove(STORED_PUBLIC_KEY)
                .apply()
            false
        }
    }

    /**
     * Загружает и расшифровывает приватный ключ Ed25519.
     * @return Расшифрованный PrivateKey (Ed25519) или null, если ключ не найден или произошла ошибка.
     */
    suspend fun loadPrivateKey(): PrivateKey? = withContext(Dispatchers.IO) {
        val base64EncryptedData = sharedPreferences.getString(ENCRYPTED_PRIVATE_KEY, null)
        if (base64EncryptedData == null) {
            Timber.tag(TAG).w("No encrypted private key ($KEY_ALGORITHM) found in storage.")
            return@withContext null
        }

        val aesSecretKey = getOrCreateAesSecretKey() // Получаем ключ AES для расшифровки
        if (aesSecretKey == null) {
            Timber.tag(TAG).e("Failed to obtain AES key for decryption.")
            return@withContext null
        }

        try {
            val combined = Base64.decode(base64EncryptedData, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH_BYTES) {
                Timber.tag(TAG).e("Invalid stored private key data length.")
                return@withContext null
            }

            // Извлекаем IV и шифротекст
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, aesSecretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)

            // Восстанавливаем PrivateKey из байтов (PKCS#8)
            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
            val keySpec = PKCS8EncodedKeySpec(decryptedBytes)
            val privateKey = keyFactory.generatePrivate(keySpec)

            Timber.tag(TAG).i("Private key ($KEY_ALGORITHM) loaded and decrypted successfully.")
            privateKey

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load and decrypt private key ($KEY_ALGORITHM)")
            null
        }
    }

    /**
     * Загружает публичный ключ Ed25519 из SharedPreferences.
     * @return PublicKey (Ed25519) или null, если ключ не найден или произошла ошибка.
     */
    suspend fun loadPublicKey(): PublicKey? = withContext(Dispatchers.IO) {
        val base64PublicKey = sharedPreferences.getString(STORED_PUBLIC_KEY, null)
        if (base64PublicKey == null) {
            Timber.tag(TAG).w("No public key ($KEY_ALGORITHM) found in storage.")
            return@withContext null
        }

        return@withContext try {
            val publicKeyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP)

            // Восстанавливаем PublicKey из байтов (X.509)
            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
            val keySpec = X509EncodedKeySpec(publicKeyBytes) // Используем X509EncodedKeySpec для публичного ключа
            val publicKey = keyFactory.generatePublic(keySpec)

            Timber.tag(TAG).i("Public key ($KEY_ALGORITHM) loaded successfully.")
            publicKey
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load public key ($KEY_ALGORITHM)")
            null
        }
    }

    /**
     * Проверяет, сохранена ли пара ключей (проверяя наличие записи о приватном ключе).
     */
    suspend fun keyExists(): Boolean = withContext(Dispatchers.IO) {
        val exists = sharedPreferences.contains(ENCRYPTED_PRIVATE_KEY)
        Timber.tag(TAG).d("Key pair exists check ($KEY_ALGORITHM): $exists")
        exists
    }

    /**
     * Удаляет сохраненные ключи Ed25519 (приватный и публичный) из SharedPreferences
     * и соответствующий ключ AES из Android Keystore.
     */
    suspend fun deleteKey(): Boolean = withContext(Dispatchers.IO) {
        var prefsDeleted = false
        var keystoreDeleted = false

        try {
            // Удаляем из SharedPreferences
            val editor = sharedPreferences.edit()
            var keysRemoved = false
            if (sharedPreferences.contains(ENCRYPTED_PRIVATE_KEY)) {
                editor.remove(ENCRYPTED_PRIVATE_KEY)
                Timber.tag(TAG).i("Encrypted private key ($KEY_ALGORITHM) removed from SharedPreferences.")
                keysRemoved = true
            }
            if (sharedPreferences.contains(STORED_PUBLIC_KEY)) {
                editor.remove(STORED_PUBLIC_KEY)
                Timber.tag(TAG).i("Public key ($KEY_ALGORITHM) removed from SharedPreferences.")
                keysRemoved = true
            }
            editor.apply()

            if (!keysRemoved) {
                Timber.tag(TAG).w("No $KEY_ALGORITHM keys found in SharedPreferences for deletion.")
            }
            prefsDeleted = true // Считаем удаленными из prefs, даже если их не было

            // Удаляем ключ AES из Keystore
            if (keyStore.containsAlias(AES_KEY_ALIAS)) {
                keyStore.deleteEntry(AES_KEY_ALIAS)
                Timber.tag(TAG).i("AES key alias '$AES_KEY_ALIAS' deleted from KeyStore.")
                keystoreDeleted = true
            } else {
                Timber.tag(TAG).w("AES key alias '$AES_KEY_ALIAS' not found in KeyStore for deletion.")
                keystoreDeleted = true // Считаем удаленным из keystore, если его и не было
            }
            prefsDeleted && keystoreDeleted
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error deleting keys")
            false // Возвращаем false при любой ошибке
        }
    }
}