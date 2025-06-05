package ru.drsn.waves.domain.model.chat

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED,
    PENDING_DOWNLOAD, // Для медиа, которое еще не загружено
    DOWNLOADED,
    UPLOADING;
}