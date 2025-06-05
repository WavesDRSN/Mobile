package ru.drsn.waves.data.datasource.local.compression

// --- Интерфейс для сжатия/разжатия ---
interface IChatCompressor {
    /**
     * Сжимает данные.
     * @param input Данные для сжатия.
     * @return Сжатые данные или null в случае ошибки.
     */
    suspend fun compress(input: ByteArray): ByteArray?

    /**
     * Разжимает данные.
     * @param input Сжатые данные.
     * @return Разжатые данные или null в случае ошибки.
     */
    suspend fun decompress(input: ByteArray): ByteArray?
}