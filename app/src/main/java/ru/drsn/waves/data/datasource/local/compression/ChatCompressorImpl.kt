package ru.drsn.waves.data.datasource.local.compression

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.drsn.waves.di.IoDispatcher
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatCompressorImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IChatCompressor {

    private companion object {
        const val TAG = "ChatCompressor"
        const val BUFFER_SIZE = 1024 // Размер буфера для потоков
    }

    override suspend fun compress(input: ByteArray): ByteArray? {
        if (input.isEmpty()) {
            Timber.tag(TAG).w("Попытка сжать пустой массив байт.")
            return input // Возвращаем пустой массив как есть
        }
        return withContext(ioDispatcher) {
            try {
                val outputStream = ByteArrayOutputStream()
                // Используем GZIPOutputStream для автоматического добавления GZIP заголовков/трейлеров
                GZIPOutputStream(outputStream).use { gzipOutputStream ->
                    gzipOutputStream.write(input)
                } // .use {} автоматически вызовет finish() и close() для GZIPOutputStream
                val compressedBytes = outputStream.toByteArray()
                Timber.tag(TAG).d("Сжатие: ${input.size} байт -> ${compressedBytes.size} байт")
                compressedBytes
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка при сжатии данных.")
                null // Возвращаем null в случае ошибки
            }
        }
    }

    override suspend fun decompress(input: ByteArray): ByteArray? {
        if (input.isEmpty()) {
            Timber.tag(TAG).w("Попытка разжать пустой массив байт.")
            return input
        }
        return withContext(ioDispatcher) {
            try {
                val inputStream = input.inputStream()
                val outputStream = ByteArrayOutputStream()
                // Используем GZIPInputStream для автоматической обработки GZIP формата
                GZIPInputStream(inputStream).use { gzipInputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var len: Int
                    while (gzipInputStream.read(buffer).also { len = it } > 0) {
                        outputStream.write(buffer, 0, len)
                    }
                }
                val decompressedBytes = outputStream.toByteArray()
                Timber.tag(TAG).d("Разжатие: ${input.size} байт -> ${decompressedBytes.size} байт")
                decompressedBytes
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка при разжатии данных.")
                null
            }
        }
    }
}
