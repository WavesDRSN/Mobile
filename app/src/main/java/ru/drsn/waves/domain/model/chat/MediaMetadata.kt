package ru.drsn.waves.domain.model.chat

data class MediaMetadata(
    val mediaUrl: String?,
    val mimeType: String?,
    val fileSize: Long?,
    val fileName: String?
)