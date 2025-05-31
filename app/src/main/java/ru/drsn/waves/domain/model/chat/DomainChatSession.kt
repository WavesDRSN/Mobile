package ru.drsn.waves.domain.model.chat

/**
 * Представляет сессию чата в доменном слое.
 */
data class DomainChatSession(
    val sessionId: String, // ID другого пользователя или ID группы
    val peerName: String,
    val peerAvatarUrl: String?,
    val lastMessagePreview: String?, // Текстовое превью последнего сообщения (дешифрованное)
    val lastMessageTimestamp: Long,
    val unreadMessagesCount: Int,
    val isArchived: Boolean,
    val isMuted: Boolean,
    val chatType: ChatType,
    val participantIds: List<String> // Никнеймы участников
)