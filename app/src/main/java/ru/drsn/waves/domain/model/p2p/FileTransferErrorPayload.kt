package ru.drsn.waves.domain.model.p2p

import com.google.gson.annotations.SerializedName

data class FileTransferErrorPayload(
    @SerializedName("file_transfer_id")
    val fileTransferId: String,
    @SerializedName("error_message")
    val errorMessage: String,
    @SerializedName("chunk_index_failed") // Опционально, если ошибка связана с конкретным чанком
    val chunkIndexFailed: Int? = null
)