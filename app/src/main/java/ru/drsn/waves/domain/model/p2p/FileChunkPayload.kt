package ru.drsn.waves.domain.model.p2p

import com.google.gson.annotations.SerializedName

data class FileChunkPayload(
    @SerializedName("file_transfer_id") // Связываем чанк с конкретной передачей
    val fileTransferId: String,
    @SerializedName("chunk_index")
    val chunkIndex: Int,
    @SerializedName("total_chunks")
    val totalChunks: Int,
    @SerializedName("data_base64")
    val dataBase64: String,
    @SerializedName("is_last_chunk")
    val isLastChunk: Boolean = (chunkIndex + 1 == totalChunks) // Автоматический расчет
)