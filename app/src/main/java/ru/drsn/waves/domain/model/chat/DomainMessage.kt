package ru.drsn.waves.domain.model.chat

/**
 * Представляет сообщение в доменном слое.
 * Содержимое здесь уже дешифровано и готово к отображению.
 */
data class DomainMessage(
    val messageId: String,
    val sessionId: String,
    val senderId: String, // Никнейм отправителя
    val content: String,  // ДЕШИФРОВАННОЕ и разжатое тело сообщения (текст)
    val timestamp: Long,
    val status: MessageStatus,
    val isOutgoing: Boolean,
    val messageType: MessageType, // Текстовое, изображение, и т.д.
    val mediaUri: String?, // URI для отображения/загрузки медиа (может быть локальным или удаленным)
    val mediaMimeType: String?,
    val mediaFileName: String?,
    val mediaFileSize: Long?,
    val thumbnailUri: String?, // URI для превью медиа
    val quotedMessage: DomainQuotedMessage? // Информация о цитируемом сообщении
)