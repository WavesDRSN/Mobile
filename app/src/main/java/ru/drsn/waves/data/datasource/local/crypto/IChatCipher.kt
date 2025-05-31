package ru.drsn.waves.data.datasource.local.crypto

// --- Интерфейс для шифрования/дешифрования ---
interface IChatCipher {
    /**
     * Шифрует данные.
     * @param plaintext Незащищенные данные.
     * @return Зашифрованные данные или null в случае ошибки.
     */
    suspend fun encrypt(plaintext: ByteArray): ByteArray? // Возвращаем ByteArray, а не Result, чтобы не усложнять сигнатуру

    /**
     * Дешифрует данные.
     * @param ciphertext Зашифрованные данные.
     * @return Расшифрованные данные или null в случае ошибки.
     */
    suspend fun decrypt(ciphertext: ByteArray): ByteArray?
}