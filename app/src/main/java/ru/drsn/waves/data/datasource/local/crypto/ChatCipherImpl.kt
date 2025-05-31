package ru.drsn.waves.data.datasource.local.crypto

import android.util.Base64 // Используем android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.drsn.waves.di.IoDispatcher
import timber.log.Timber
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ChatCipherImpl @Inject constructor(
    private val localDataSource: ICryptoLocalDataSource, // Для сохранения/загрузки и обертки/развертки CEK
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IChatCipher {

    private companion object {
        const val TAG = "ChatCipher"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val AES_ALGORITHM = "AES"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

        // Используем тот же алиас ключа-обертки, что и для EdDSA ключей
        // Этот алиас будет передан в localDataSource для операций wrap/unwrap
        const val KEYSTORE_ALIAS_FOR_CEK_WRAPPING = "ru.drsn.waves.crypto.AES_WRAPPER_KEY_V2"
    }

    @Volatile
    private var decryptedChatKey: SecretKey? = null
    private val keyMutex = Mutex()

    private suspend fun getOrCreateChatEncryptionKey(): SecretKey? = keyMutex.withLock {
        if (decryptedChatKey != null) {
            return decryptedChatKey
        }

        return withContext(ioDispatcher) {
            try {
                val encryptedCekB64 = localDataSource.loadEncryptedChatKey()
                if (encryptedCekB64 != null) {
                    Timber.tag(TAG).d("Зашифрованный CEK найден, дешифруем...")
                    val encryptedCekBytesWithIv = Base64.decode(encryptedCekB64, Base64.NO_WRAP)
                    // Делегируем дешифрование CEK (развертку) в localDataSource
                    val unwrappedKeyBytes = localDataSource.unwrapDataWithKeystoreKey(
                        encryptedCekBytesWithIv,
                        KEYSTORE_ALIAS_FOR_CEK_WRAPPING
                    )
                    if (unwrappedKeyBytes != null) {
                        decryptedChatKey = SecretKeySpec(unwrappedKeyBytes, AES_ALGORITHM)
                        Timber.tag(TAG).i("CEK успешно дешифрован и загружен в кэш.")
                        return@withContext decryptedChatKey
                    } else {
                        Timber.tag(TAG).e("Не удалось дешифровать сохраненный CEK. Удаляем старый ключ.")
                        localDataSource.deleteEncryptedChatKey()
                    }
                }

                Timber.tag(TAG).i("CEK не найден или удален. Генерируем новый CEK...")
                val newRawCek = generateNewAesKey(AES_KEY_SIZE_BITS)
                // Делегируем шифрование CEK (обертку) в localDataSource
                val wrappedCekBytesWithIv = localDataSource.wrapDataWithKeystoreKey(
                    newRawCek.encoded,
                    KEYSTORE_ALIAS_FOR_CEK_WRAPPING
                )
                if (wrappedCekBytesWithIv != null) {
                    val wrappedCekB64 = Base64.encodeToString(wrappedCekBytesWithIv, Base64.NO_WRAP)
                    if (localDataSource.saveEncryptedChatKey(wrappedCekB64)) {
                        decryptedChatKey = newRawCek
                        Timber.tag(TAG).i("Новый CEK сгенерирован, зашифрован ключом-оберткой и сохранен.")
                        return@withContext newRawCek
                    } else {
                        Timber.tag(TAG).e("Не удалось сохранить новый зашифрованный CEK.")
                    }
                } else {
                    Timber.tag(TAG).e("Не удалось зашифровать новый CEK с помощью ключа-обертки.")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка при получении/генерации ключа шифрования чатов (CEK).")
            }
            return@withContext null
        }
    }

    private fun generateNewAesKey(keySizeBits: Int): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGenerator.init(keySizeBits, SecureRandom())
        return keyGenerator.generateKey()
    }

    override suspend fun encrypt(plaintext: ByteArray): ByteArray? {
        // ... (логика шифрования plaintext с помощью decryptedChatKey остается такой же, как и раньше) ...
        return withContext(ioDispatcher) {
            try {
                val key = getOrCreateChatEncryptionKey() ?: return@withContext null
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                val iv = ByteArray(GCM_IV_LENGTH_BYTES)
                SecureRandom().nextBytes(iv)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
                val ciphertext = cipher.doFinal(plaintext)
                val result = ByteArray(iv.size + ciphertext.size)
                System.arraycopy(iv, 0, result, 0, iv.size)
                System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
                result
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка шифрования данных чата.")
                null
            }
        }
    }

    override suspend fun decrypt(ciphertextWithIv: ByteArray): ByteArray? {
        // ... (логика дешифрования ciphertextWithIv с помощью decryptedChatKey остается такой же, как и раньше) ...
        return withContext(ioDispatcher) {
            try {
                if (ciphertextWithIv.size <= GCM_IV_LENGTH_BYTES) return@withContext null
                val key = getOrCreateChatEncryptionKey() ?: return@withContext null
                val iv = ciphertextWithIv.copyOfRange(0, GCM_IV_LENGTH_BYTES)
                val ciphertext = ciphertextWithIv.copyOfRange(GCM_IV_LENGTH_BYTES, ciphertextWithIv.size)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
                cipher.doFinal(ciphertext)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка дешифрования данных чата.")
                null
            }
        }
    }
}