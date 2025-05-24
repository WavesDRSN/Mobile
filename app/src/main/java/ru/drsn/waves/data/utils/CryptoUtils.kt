package ru.drsn.waves.data.utils

import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // GCM рекомендует 12 байт
    private const val TAG_LENGTH = 128 // длина тега аутентификации в битах

    fun encryptWithSharedSecret(data: ByteArray, sharedSecret: ByteArray): ByteArray {
        try {
            // Вычислить общий секрет с публичным ключом другого пользователя

            if (sharedSecret.isEmpty()) {
                Timber.e("Failed to compute shared secret.")
                return ByteArray(0)
            }

            // Преобразовать общий секрет в 256-битный AES ключ (используем SHA-256)
            val aesKey = MessageDigest.getInstance("SHA-256").digest(sharedSecret)

            // Зашифровать данные с использованием этого ключа
            return encryptData(data, aesKey)

        } catch (e: Exception) {
            Timber.e("Encryption failed: ${e.message}")
            return ByteArray(0)
        }
    }

    // Шифрование: возвращает IV + ciphertext
    fun encryptData(data: ByteArray, key: ByteArray): ByteArray {
        try {
            // Создаём массив IV (инициализационный вектор) нужной длины
            val iv = ByteArray(IV_SIZE)

            // Генерируем случайный IV через криптостойкий генератор
            SecureRandom().nextBytes(iv)

            // Инициализируем шифр AES в режиме GCM (с защитой целостности) без паддинга
            val cipher = Cipher.getInstance(TRANSFORMATION)

            // Создаём ключ из переданных байт
            val secretKey = SecretKeySpec(key, ALGORITHM)

            // Указываем параметры: длина тега аутентичности и IV
            val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)

            // Инициализируем шифр на шифрование
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            // Шифруем данные: результат включает ciphertext и AEAD-тег
            val cipherText = cipher.doFinal(data)

            // Возвращаем IV + ciphertext — IV нужен для расшифровки
            return iv + cipherText
        } catch (e: Exception) {
            // В случае ошибки печатаем стек вызовов и возвращаем пустой массив
            e.printStackTrace()
            return ByteArray(0)
        }
    }

    // Расшифровка: ожидает IV + ciphertext
    fun decryptData(encryptedData: ByteArray, key: ByteArray?): ByteArray {
        try {
            // Преобразуем входной ключ в строго 32-байтный через SHA-256
            val key = MessageDigest.getInstance("SHA-256").digest(key)

            // Если данных меньше чем размер IV — ошибка
            if (encryptedData.size < IV_SIZE) return ByteArray(0)

            // Извлекаем IV из начала массива
            val iv = encryptedData.copyOfRange(0, IV_SIZE)

            // Оставшиеся байты — это зашифрованные данные + AEAD-тег
            val cipherText = encryptedData.copyOfRange(IV_SIZE, encryptedData.size)

            // Инициализируем шифр AES-GCM
            val cipher = Cipher.getInstance(TRANSFORMATION)

            // Готовим ключ и параметры (такой же IV, как при шифровании)
            val secretKey = SecretKeySpec(key, ALGORITHM)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)

            // Настраиваем шифр на режим расшифровки
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            // Пытаемся расшифровать: если AEAD-тег не совпадёт — будет ошибка
            return cipher.doFinal(cipherText)

        } catch (e: AEADBadTagException) {
            // AEAD тег не прошёл проверку — значит, данные подделаны или ключ неверный
            Timber.e("BAD TAG - tampered or wrong key!")
            throw e // Пробрасываем ошибку выше — это важно
        } catch (e: Exception) {
            // Иная ошибка при расшифровке — выводим стек и возвращаем пустой массив
            e.printStackTrace()
            return ByteArray(0)
        }
    }
}
