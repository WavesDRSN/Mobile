package ru.drsn.waves.data.repository

import android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.drsn.waves.data.datasource.local.compression.IChatCompressor
import ru.drsn.waves.data.datasource.local.crypto.IChatCipher
import ru.drsn.waves.data.datasource.local.db.dao.ChatSessionDao
import ru.drsn.waves.data.datasource.local.db.dao.MessageDao
import ru.drsn.waves.data.datasource.local.db.entity.ChatSessionEntity
import ru.drsn.waves.data.datasource.local.db.entity.MessageEntity
import ru.drsn.waves.di.IoDispatcher
import ru.drsn.waves.domain.model.utils.Result // Общий Result
import ru.drsn.waves.domain.model.chat.* // Все доменные модели чата
import ru.drsn.waves.domain.model.webrtc.PeerId
import ru.drsn.waves.domain.model.webrtc.WebRTCEvent
import ru.drsn.waves.domain.model.webrtc.WebRTCMessage
import ru.drsn.waves.domain.model.webrtc.WebRTCSessionState
import ru.drsn.waves.domain.repository.IChatRepository
import ru.drsn.waves.domain.repository.ICryptoRepository // Для получения currentUserId
import ru.drsn.waves.domain.repository.IWebRTCRepository // Для отправки сообщений
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatSessionDao: ChatSessionDao,
    private val messageDao: MessageDao,
    private val chatCipher: IChatCipher, // Для локального шифрования/дешифрования
    private val chatCompressor: IChatCompressor, // Для сжатия/разжатия
    private val cryptoRepository: ICryptoRepository,
    private val webRTCRepository: IWebRTCRepository,
    @IoDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IChatRepository {

    private companion object {
        const val TAG = "ChatRepository"
        const val DEFAULT_MESSAGE_PAGE_LIMIT = 30
        const val MAX_PREVIEW_LENGTH = 50
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + defaultDispatcher)

    init {
        // Подписываемся на входящие сообщения от WebRTCRepository
        repositoryScope.launch { // Запускаем в скоупе репозитория
            webRTCRepository.observeWebRTCEvents()
                .filterIsInstance<WebRTCEvent.BinaryMessageReceived>() // Фильтруем только нужный тип события
                .collect { event ->
                    Timber.tag(TAG).d("Получено BinaryMessageReceived от ${event.peerId.value}")
                    Timber.tag(TAG).d("Получено BinaryMessageReceived ${event.message.size}")
                    // Предполагаем, что ID сессии чата совпадает с ID пира
                    // ID сообщения должен приходить от отправителя или генерироваться здесь, если не приходит
                    // Тип сообщения и метаданные медиа также должны как-то передаваться.
                    // Это упрощенный вариант, где мы предполагаем, что это текстовое сообщение.
                    // В реальном приложении нужен протокол поверх DataChannel для передачи типа и метаданных.

                    // Генерируем уникальный ID для входящего сообщения, если он не пришел от отправителя
                    // (лучше, чтобы ID генерировался отправителем и был глобально уникальным)
                    val messageId = "incoming_${UUID.randomUUID()}" // Пример, если ID не передается

                    // Пытаемся определить тип сообщения (пока заглушка - TEXT)
                    // В реальном приложении это может быть частью протокола поверх DataChannel
                    val messageType = MessageType.TEXT // TODO: Определять тип сообщения корректно

                    saveIncomingMessage(
                        sessionId = event.peerId.value,
                        senderId = event.peerId.value, // Отправитель - это пир, от которого пришло сообщение
                        networkBytes = event.message,
                        timestamp = System.currentTimeMillis(), // Время получения
                        messageId = messageId, // ID сообщения (лучше от отправителя)
                        messageType = messageType,
                        mediaMetadata = null // Пока без медиа
                    )
                }
        }
    }

    override fun observeChatSessions(): Flow<List<DomainChatSession>> {
        return chatSessionDao.getAllSessions()
            .map { entities ->
                withContext(defaultDispatcher) {
                    entities.mapNotNull { entity ->
                        val lastMessageEntity = entity.lastMessageId?.let { messageDao.getMessageById(it) }
                        val previewText = lastMessageEntity?.let { msg ->
                            // Дешифруем и разжимаем для превью
                            decryptAndDecompressContent(msg.content)?.let { decryptedBytes ->
                                String(decryptedBytes, StandardCharsets.UTF_8).take(MAX_PREVIEW_LENGTH)
                            }
                        } ?: "Нет сообщений"
                        mapChatSessionEntityToDomain(entity, previewText)
                    }
                }
            }
            .flowOn(defaultDispatcher)
            .catch { e ->
                Timber.tag(TAG).e(e, "Ошибка при наблюдении за сессиями чатов")
                emit(emptyList())
            }
    }

    override fun observeMessagesForSession(sessionId: String): Flow<List<DomainMessage>> {
        return messageDao.getAllMessagesForSessionStream(sessionId)
            .map { entities ->
                withContext(defaultDispatcher) {
                    entities.mapNotNull { entity ->
                        mapMessageEntityToDomain(entity) // Дешифровка и разжатие внутри
                    }
                }
            }
            .flowOn(defaultDispatcher)
            .catch { e ->
                Timber.tag(TAG).e(e, "Ошибка при наблюдении за сообщениями для сессии $sessionId")
                emit(emptyList())
            }
    }

    override suspend fun loadMoreMessages(sessionId: String, beforeTimestamp: Long, limit: Int): Result<Boolean, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                val currentMessagesCount = messageDao.getAllMessagesForSessionStream(sessionId).firstOrNull()?.size ?: 0
                val messages = messageDao.getMessagesForSessionPaged(sessionId, limit, currentMessagesCount)
                Result.Success(messages.isNotEmpty())
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка загрузки старых сообщений для сессии $sessionId")
                Result.Error(ChatError.StorageError("Не удалось загрузить сообщения", e))
            }
        }
    }

    override suspend fun sendTextMessage(sessionId: String, text: String, localMessageId: String): Result<DomainMessage, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                val currentUserIdResult = cryptoRepository.getUserNickname()
                val currentUserId = if (currentUserIdResult is Result.Success) {
                    currentUserIdResult.value
                } else {
                    Timber.tag(TAG).e("Не удалось получить ID текущего пользователя для отправки сообщения.")
                    return@withContext Result.Error(ChatError.OperationFailed("Не удалось определить отправителя", null))
                }

                val timestamp = System.currentTimeMillis()
                val messageType = MessageType.TEXT
                val textBytes = text.toByteArray(StandardCharsets.UTF_8)

                // 1. Сжатие (если есть)
                val bytesToSendOverNetwork = chatCompressor.compress(textBytes) ?: textBytes

                val webRTCMessage = WebRTCMessage(
                    peerId = PeerId(sessionId), // sessionId здесь это ID пира
                    content = text,
                    // content = Base64.getEncoder().encodeToString(bytesToSendOverNetwork) // Если WebRTC требует строку
                    contentBytes = bytesToSendOverNetwork // Если WebRTC может отправлять байты
                )
                // Адаптируй WebRTCMessage и IWebRTCRepository.sendMessage, если нужно
                // Для примера, добавим contentBytes в WebRTCMessage и изменим сигнатуру sendMessage в IWebRTCRepository
                // interface IWebRTCRepository {
                //    suspend fun sendMessage(message: WebRTCMessage): Result<Unit, WebRTCError>
                // }
                // data class WebRTCMessage(val peerId: PeerId, val contentBytes: ByteArray)

                Timber.d("Sending $bytesToSendOverNetwork")

                var messageStatusForDb = MessageStatus.SENDING // Начальный статус

                val sendResult = webRTCRepository.sendMessage(webRTCMessage) // Предполагаем, что sendMessage принимает WebRTCMessage с contentBytes
                if (sendResult is Result.Error) {
                    messageStatusForDb = MessageStatus.FAILED
                    Timber.tag(TAG).w("Не удалось отправить сообщение $localMessageId через WebRTC: ${sendResult.error}. Сохранено как FAILED.")
                    // Не прерываем, сообщение все равно сохранится локально со статусом FAILED
                } else {
                    messageStatusForDb = MessageStatus.SENT // Успешно передано в WebRTC слой
                    Timber.tag(TAG).i("Сообщение $localMessageId передано в WebRTC слой для отправки.")
                }

                // 3. Шифрование для локального хранения (шифруем те же bytesToSendOverNetwork)
                val encryptedContentForDb = chatCipher.encrypt(bytesToSendOverNetwork)
                if (encryptedContentForDb == null) {
                    Timber.tag(TAG).e("Ошибка шифрования текстового сообщения для локального хранения ($sessionId)")
                    // Если шифрование для БД не удалось, мы можем либо не сохранять сообщение,
                    // либо сохранить его незашифрованным (менее безопасно), либо вернуть ошибку.
                    // Вернем ошибку, так как локальное шифрование важно.
                    return@withContext Result.Error(ChatError.EncryptionError("Не удалось зашифровать сообщение для локального хранения", null))
                }

                val messageEntity = MessageEntity(
                    messageId = localMessageId,
                    sessionId = sessionId,
                    senderId = currentUserId,
                    content = encryptedContentForDb, // Сохраняем локально зашифрованное
                    timestamp = timestamp,
                    status = messageStatusForDb.name,
                    isOutgoing = true,
                    messageType = messageType.name
                )

                messageDao.insertMessage(messageEntity)
                updateChatSessionLastMessage(sessionId, messageEntity.messageId, timestamp)

                // Маппим для возврата (дешифровка произойдет здесь)
                val domainMessage = mapMessageEntityToDomain(messageEntity)
                if (domainMessage == null) {
                    Result.Error(ChatError.OperationFailed("Не удалось смапить сообщение после сохранения", null))
                } else {
                    Result.Success(domainMessage)
                }

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка отправки текстового сообщения для $sessionId")
                Result.Error(ChatError.OperationFailed("Ошибка отправки сообщения", e))
            }
        }
    }

    override suspend fun sendMediaMessage(
        sessionId: String,
        localMediaUri: String,
        messageType: MessageType,
        originalFileName: String?,
        localMessageId: String
    ): Result<DomainMessage, ChatError> {
        // Логика будет аналогична sendTextMessage:
        // 1. Подготовить медиафайл (возможно, сжать превью).
        // 2. Отправить "сырые" или onion-зашифрованные байты медиафайла (или его частей) через WebRTC.
        //    WebRTC DataChannel имеет ограничение на размер пакета, большие файлы нужно чанковать.
        //    Можно сначала отправить метаданные (имя, размер, тип), а потом сам файл.
        // 3. Зашифровать медиафайл (или его локальную копию) локальным chatCipher для хранения в БД/файловой системе.
        // 4. Сохранить MessageEntity с информацией о медиа.
        Timber.tag(TAG).w("sendMediaMessage еще не реализован полностью с учетом новой логики шифрования.")
        return Result.Error(ChatError.OperationFailed("Отправка медиа не реализована", null))
    }

    override suspend fun saveIncomingMessage(
        sessionId: String,
        senderId: String,
        networkBytes: ByteArray, // Это "сырые" или onion-зашифрованные байты, полученные по сети
        timestamp: Long,
        messageId: String,
        messageType: MessageType,
        mediaMetadata: MediaMetadata?
    ): Result<DomainMessage, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                Timber.tag(TAG).d("Сохранение входящего сообщения $messageId для сессии $sessionId от $senderId")

                if (chatSessionDao.getSessionById(sessionId) == null) {
                    val sessionEntity = ChatSessionEntity(
                        sessionId = sessionId,
                        peerName = senderId,
                        chatType = ChatType.PEER_TO_PEER.toString(),
                        participantIds = listOf(senderId)
                    )
                    chatSessionDao.insertOrUpdateSession(sessionEntity)
                }

                // 1. Разжатие (если отправитель сжимал)
                // Предполагаем, что если отправитель сжимал, то и мы должны разжать.
                // Нужен способ узнать, было ли сообщение сжато (например, по флагу в сообщении или по типу).
                // Пока что просто пытаемся разжать.
                val bytesToEncrypt = chatCompressor.decompress(networkBytes) ?: networkBytes

                // 2. Шифрование для локального хранения
                val encryptedContentForDb = chatCipher.encrypt(bytesToEncrypt)
                if (encryptedContentForDb == null) {
                    Timber.tag(TAG).e("Ошибка шифрования входящего сообщения $messageId для локального хранения.")
                    return@withContext Result.Error(ChatError.EncryptionError("Не удалось зашифровать входящее сообщение для БД", null))
                }

                val messageEntity = MessageEntity(
                    messageId = messageId,
                    sessionId = sessionId,
                    senderId = senderId,
                    content = encryptedContentForDb, // Сохраняем локально зашифрованное
                    timestamp = timestamp,
                    status = MessageStatus.DELIVERED.name, // Или READ, если чат активен
                    isOutgoing = false,
                    messageType = messageType.name,
                    mediaUrl = mediaMetadata?.mediaUrl,
                    mediaMimeType = mediaMetadata?.mimeType,
                    mediaFileSize = mediaMetadata?.fileSize,
                    mediaFileName = mediaMetadata?.fileName
                )
                messageDao.insertMessage(messageEntity)
                updateChatSessionLastMessage(sessionId, messageId, timestamp, incrementUnread = true)

                val domainMessage = mapMessageEntityToDomain(messageEntity) // Дешифровка для возврата
                if (domainMessage == null) {
                    Result.Error(ChatError.OperationFailed("Не удалось смапить входящее сообщение после сохранения", null))
                } else {
                    Result.Success(domainMessage)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка сохранения входящего сообщения $messageId")
                Result.Error(ChatError.StorageError("Не удалось сохранить входящее сообщение", e))
            }
        }
    }


    override suspend fun updateMessageStatus(messageId: String, newStatus: MessageStatus): Result<Unit, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                messageDao.updateMessageStatus(messageId, newStatus.name)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(ChatError.StorageError("Не удалось обновить статус сообщения", e))
            }
        }
    }

    override suspend fun markMessagesAsRead(sessionId: String): Result<Unit, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                chatSessionDao.resetUnreadCount(sessionId)
                // Здесь можно добавить логику обновления статусов конкретных сообщений на READ, если нужно
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(ChatError.StorageError("Не удалось пометить сообщения как прочитанные", e))
            }
        }
    }
    override suspend fun getOrCreateChatSession(
        peerId: String,
        peerName: String,
        chatType: ChatType,
        participantIds: List<String>
    ): Result<DomainChatSession, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                var sessionEntity = chatSessionDao.getSessionById(peerId)
                if (sessionEntity == null) {
                    sessionEntity = ChatSessionEntity(
                        sessionId = peerId,
                        peerName = peerName,
                        chatType = chatType.name,
                        participantIds = if (chatType == ChatType.PEER_TO_PEER && participantIds.isEmpty()) listOf(peerId) else participantIds.distinct(),
                        lastMessageTimestamp = System.currentTimeMillis()
                    )
                    chatSessionDao.insertOrUpdateSession(sessionEntity)
                }
                // Для превью последнего сообщения, если сессия только создана
                val lastMessagePreview = if (sessionEntity.lastMessageId != null) {
                    messageDao.getMessageById(sessionEntity.lastMessageId!!)?.let { msg ->
                        decryptAndDecompressContent(msg.content)?.let { String(it, StandardCharsets.UTF_8).take(MAX_PREVIEW_LENGTH) }
                    }
                } else "Нет сообщений"

                if (webRTCRepository.getSessionState(PeerId(peerId)) !is WebRTCSessionState.Connected) {
                    repositoryScope.launch {
                        webRTCRepository.initiateCall(PeerId(peerId))
                    }
                }

                mapChatSessionEntityToDomain(sessionEntity, lastMessagePreview)
                    ?.let { Result.Success(it) }
                    ?: Result.Error(ChatError.OperationFailed("Не удалось смапить сессию чата", null))
            } catch (e: Exception) {
                Result.Error(ChatError.StorageError("Не удалось получить/создать сессию чата", e))
            }
        }
    }


    override suspend fun deleteMessage(messageId: String, forEveryone: Boolean): Result<Unit, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                messageDao.deleteMessageById(messageId)
                // TODO: Обновить lastMessageInfo в ChatSession
                // TODO: Если forEveryone, отправить команду через WebRTC/Signaling
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(ChatError.StorageError("Не удалось удалить сообщение", e))
            }
        }
    }

    override suspend fun clearChatHistory(sessionId: String): Result<Unit, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                messageDao.deleteAllMessagesInSession(sessionId)
                chatSessionDao.updateLastMessageInfo(sessionId, "", 0L)
                chatSessionDao.resetUnreadCount(sessionId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(ChatError.StorageError("Не удалось очистить историю", e))
            }
        }
    }

    override suspend fun deleteChatSession(sessionId: String): Result<Unit, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                chatSessionDao.deleteSessionById(sessionId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(ChatError.StorageError("Не удалось удалить сессию", e))
            }
        }
    }

    private suspend fun updateChatSessionLastMessage(sessionId: String, messageId: String, timestamp: Long, incrementUnread: Boolean = false) {
        try {
            chatSessionDao.updateLastMessageInfo(sessionId, messageId, timestamp)
            if (incrementUnread) {
                // TODO: Проверять, активен ли чат, перед инкрементом
                chatSessionDao.incrementUnreadCount(sessionId)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка обновления информации о последнем сообщении для сессии $sessionId")
        }
    }

    private suspend fun decryptAndDecompressContent(encryptedContent: ByteArray): ByteArray? {
        val decryptedBytes = chatCipher.decrypt(encryptedContent)
        if (decryptedBytes == null) {
            Timber.tag(TAG).e("Ошибка дешифрования контента сообщения.")
            return null
        }
        // Предполагаем, что если дешифровка удалась, то можно пытаться разжать
        return chatCompressor.decompress(decryptedBytes) ?: decryptedBytes // Если разжатие не удалось, возвращаем дешифрованное
    }

    private suspend fun mapMessageEntityToDomain(entity: MessageEntity): DomainMessage? {
        val decryptedContentBytes = decryptAndDecompressContent(entity.content)
        val contentString = if (decryptedContentBytes != null) {
            String(decryptedContentBytes, StandardCharsets.UTF_8)
        } else {
            Timber.tag(TAG).e("Не удалось дешифровать/разжать контент для сообщения ${entity.messageId}")
            "[Ошибка отображения сообщения]" // Плейсхолдер для UI
        }

        var domainQuotedMessage: DomainQuotedMessage? = null
        if (entity.quotedMessageId != null) {
            messageDao.getMessageById(entity.quotedMessageId)?.let { quotedEntity ->
                decryptAndDecompressContent(quotedEntity.content)?.let { decryptedQuotedBytes ->
                    val preview = String(decryptedQuotedBytes, StandardCharsets.UTF_8).take(MAX_PREVIEW_LENGTH)
                    // Для senderName хорошо бы иметь маппинг ID в имя, если senderId это ID, а не ник
                    domainQuotedMessage = DomainQuotedMessage(quotedEntity.messageId, quotedEntity.senderId, preview)
                }
            }
        }

        return try {
            DomainMessage(
                messageId = entity.messageId,
                sessionId = entity.sessionId,
                senderId = entity.senderId,
                content = contentString,
                timestamp = entity.timestamp,
                status = MessageStatus.valueOf(entity.status.uppercase()),
                isOutgoing = entity.isOutgoing,
                messageType = MessageType.valueOf(entity.messageType.uppercase()),
                mediaUri = entity.mediaLocalPath ?: entity.mediaUrl,
                mediaMimeType = entity.mediaMimeType,
                mediaFileName = entity.mediaFileName,
                mediaFileSize = entity.mediaFileSize,
                thumbnailUri = entity.thumbnailLocalPath,
                quotedMessage = domainQuotedMessage
            )
        } catch (e: IllegalArgumentException) { // Ошибка парсинга enum
            Timber.tag(TAG).e(e, "Ошибка маппинга MessageEntity в Domain для ${entity.messageId}")
            null
        }
    }

    private fun mapChatSessionEntityToDomain(entity: ChatSessionEntity, lastMessagePreview: String?): DomainChatSession? {
        return try {
            DomainChatSession(
                sessionId = entity.sessionId,
                peerName = entity.peerName,
                peerAvatarUrl = entity.peerAvatarUrl,
                lastMessagePreview = lastMessagePreview,
                lastMessageTimestamp = entity.lastMessageTimestamp,
                unreadMessagesCount = entity.unreadMessagesCount,
                isArchived = entity.isArchived,
                isMuted = entity.isMuted,
                chatType = ChatType.valueOf(entity.chatType.uppercase()),
                participantIds = entity.participantIds
            )
        } catch (e: IllegalArgumentException) { // Ошибка парсинга enum
            Timber.tag(TAG).e(e, "Ошибка маппинга ChatSessionEntity в Domain для ${entity.sessionId}")
            null
        }
    }
}

