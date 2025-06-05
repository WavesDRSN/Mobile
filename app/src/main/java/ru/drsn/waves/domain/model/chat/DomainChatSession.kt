package ru.drsn.waves.domain.model.chat


/**
 * Представляет сессию чата в доменном слое (ОБНОВЛЕНО).
 */
data class DomainChatSession(
    val sessionId: String,
    val peerName: String,
    val peerAvatarUrl: String?,
    val peerDescription: String?,
    val lastKnownPeerProfileTimestamp: Long?, // НОВОЕ ПОЛЕ
    val lastMessagePreview: String?,
    val lastMessageTimestamp: Long,
    val unreadMessagesCount: Int,
    val isArchived: Boolean,
    val isMuted: Boolean,
    val chatType: ChatType,
    val participantIds: List<String>
)
