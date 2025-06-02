package ru.drsn.waves.domain.model.p2p

import com.google.gson.annotations.SerializedName

data class EnhancedMediaMetadata(
    @SerializedName("file_transfer_id") // Уникальный ID для этой сессии передачи файла
    val fileTransferId: String,
    @SerializedName("file_name")
    val fileName: String,
    @SerializedName("file_size")
    val fileSize: Long,
    @SerializedName("mime_type")
    val mimeType: String?,
    @SerializedName("media_url") // Может быть null для P2P, или использоваться для превью
    val mediaUrl: String? = null
)