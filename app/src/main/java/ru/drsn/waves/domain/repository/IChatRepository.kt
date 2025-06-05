package ru.drsn.waves.domain.repository


import kotlinx.coroutines.flow.Flow
import ru.drsn.waves.domain.model.chat.ChatType
import ru.drsn.waves.domain.model.chat.DomainChatSession
import ru.drsn.waves.domain.model.chat.DomainMessage
import ru.drsn.waves.domain.model.chat.MessageStatus
import ru.drsn.waves.domain.model.chat.MessageType
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.chat.ChatError
import ru.drsn.waves.domain.model.chat.MediaMetadata
import ru.drsn.waves.domain.model.profile.DomainUserProfile

interface IChatRepository {

    /**
     * Наблюдает за списком всех сессий чатов пользователя.
     * @return Flow со списком DomainChatSession, отсортированных по времени последнего сообщения.
     */
    fun observeChatSessions(): Flow<List<DomainChatSession>>

    /**
     * Наблюдает за сообщениями в конкретной сессии чата.
     * Реализует пагинацию (lazy loading) для эффективной загрузки истории.
     * @param sessionId ID сессии чата.
     * @return Flow со списком DomainMessage.
     */
    fun observeMessagesForSession(sessionId: String): Flow<List<DomainMessage>> // Может быть с пагинацией через Paging 3

    /**
     * Загружает предыдущую "страницу" сообщений для указанной сессии.
     * @param sessionId ID сессии чата.
     * @param beforeTimestamp Временная метка сообщения, до которого нужно загрузить (для пагинации).
     * @param limit Количество сообщений для загрузки.
     * @return Result с Boolean (true, если были загружены еще сообщения, false - если это конец истории) или ChatError.
     */
    suspend fun loadMoreMessages(sessionId: String, beforeTimestamp: Long, limit: Int = 20): Result<Boolean, ChatError>


    /**
     * Отправляет новое текстовое сообщение.
     * Сообщение сохраняется локально и отправляется через сеть (WebRTC).
     * @param sessionId ID сессии чата.
     * @param text Текст сообщения.
     * @param localMessageId Временный ID, сгенерированный на клиенте для отслеживания.
     * @return Result с DomainMessage (сохраненное сообщение с присвоенным ID) или ChatError.
     */
    suspend fun sendTextMessage(sessionId: String, text: String, localMessageId: String): Result<DomainMessage, ChatError>

    /**
     * Отправляет медиа-сообщение (изображение, видео и т.д.).
     * @param sessionId ID сессии чата.
     * @param localMediaUri URI локального медиафайла.
     * @param messageType Тип медиа.
     * @param localMessageId Временный ID, сгенерированный на клиенте.
     * @return Result с DomainMessage или ChatError.
     */
    suspend fun sendMediaMessage(
        sessionId: String,
        localMediaUriString: String, // Используем String для URI, т.к. android.net.Uri не для domain слоя
        messageType: MessageType,
        originalFileName: String?,
        localMessageId: String
    ): Result<DomainMessage, ChatError>


    /**
     * Обновляет статус сообщения (например, "delivered", "read").
     * @param messageId ID сообщения.
     * @param newStatus Новый статус.
     */
    suspend fun updateMessageStatus(messageId: String, newStatus: MessageStatus): Result<Unit, ChatError>

    /**
     * Помечает все сообщения в чате как прочитанные.
     * @param sessionId ID сессии чата.
     */
    suspend fun markMessagesAsRead(sessionId: String): Result<Unit, ChatError>

    /**
     * Создает или находит существующую сессию чата.
     * @param peerId ID собеседника (для личного чата).
     * @param peerName Имя собеседника.
     * @param chatType Тип чата.
     * @param participantIds Список участников (для группового).
     * @return Result с DomainChatSession или ChatError.
     */
    suspend fun getOrCreateChatSession(
        peerId: String, // Для личного чата это и есть sessionId
        peerName: String,
        chatType: ChatType,
        participantIds: List<String>
    ): Result<DomainChatSession, ChatError>

    /**
     * Удаляет сообщение.
     * @param messageId ID сообщения.
     * @param forEveryone Удалить для всех участников (если поддерживается сервером) или только локально.
     */
    suspend fun deleteMessage(messageId: String, forEveryone: Boolean): Result<Unit, ChatError>

    /**
     * Очищает историю сообщений в чате (только локально).
     * @param sessionId ID сессии чата.
     */
    suspend fun clearChatHistory(sessionId: String): Result<Unit, ChatError>

    /**
     * Удаляет сессию чата (включая всю историю сообщений локально).
     * @param sessionId ID сессии чата.
     */
    suspend fun deleteChatSession(sessionId: String): Result<Unit, ChatError>

    suspend fun updateChatSessionProfileInfo(
        sessionId: String,
        newPeerName: String,
        newPeerDescription: String?,
        newPeerAvatarUrl: String?,
        profileTimestamp: Long? // НОВЫЙ ПАРАМЕТР
    ): Result<Unit, ChatError>

    suspend fun getSessionInfo(sessionId: String): Result<DomainUserProfile, ChatError>

    /**
     * Отправляет информацию о профиле текущего пользователя указанному пиру.
     * Если профиль содержит локальный аватар, инициирует его P2P передачу.
     * @param targetPeerId ID пира-получателя.
     * @param profile Данные профиля текущего пользователя для отправки.
     */
    suspend fun sendMyProfileInfoToPeer(targetPeerId: String, profile: DomainUserProfile): Result<Unit, ChatError>
}

