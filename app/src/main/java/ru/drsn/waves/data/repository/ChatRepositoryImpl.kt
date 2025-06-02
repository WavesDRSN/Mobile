package ru.drsn.waves.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import ru.drsn.waves.domain.model.p2p.ChatMessagePayload
import ru.drsn.waves.domain.model.p2p.EnhancedMediaMetadata
import ru.drsn.waves.domain.model.p2p.FileChunkPayload
import ru.drsn.waves.domain.model.p2p.FileTransferCompletePayload
import ru.drsn.waves.domain.model.p2p.FileTransferErrorPayload
import ru.drsn.waves.domain.model.p2p.P2pMessageEnvelope
import ru.drsn.waves.domain.model.p2p.P2pMessageSerializer
import ru.drsn.waves.domain.model.p2p.P2pMessageType
import ru.drsn.waves.domain.model.p2p.UserProfilePayload
import ru.drsn.waves.domain.model.profile.DomainUserProfile
import ru.drsn.waves.domain.model.webrtc.PeerId
import ru.drsn.waves.domain.model.webrtc.WebRTCEvent
import ru.drsn.waves.domain.model.webrtc.WebRTCMessage
import ru.drsn.waves.domain.model.webrtc.WebRTCSessionState
import ru.drsn.waves.domain.repository.IChatRepository
import ru.drsn.waves.domain.repository.ICryptoRepository // Для получения currentUserId
import ru.drsn.waves.domain.repository.IWebRTCRepository // Для отправки сообщений
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
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
        const val CHUNK_SIZE_BYTES = 16 * 1024 // 16KB
        const val MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024 // 25 MB
        const val MEDIA_SUBDIR = "chat_media"
        const val AVATARS_SUBDIR = "avatars" // Подпапка для аватаров
        const val INCOMING_TEMP_SUBDIR = "incoming_files_temp"
        const val MAX_PREVIEW_LENGTH = 50
    }

    private val pendingP2pEnvelopesQueue = ConcurrentHashMap<String, MutableList<P2pEnvelopeQueueItem>>()

    private val queueMutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private var currentUserId = ""


    private val activeOutgoingFileTransfers = ConcurrentHashMap<String, Job>()
    private val incomingFileStreams = ConcurrentHashMap<String, FileOutputStream>() // key: fileTransferId
    private val incomingFileMetadataStore = ConcurrentHashMap<String, Any>() // key: fileTransferId

    private data class P2pEnvelopeQueueItem(
        val p2pEnvelopeJson: String,
        val associatedLocalMessageId: String? = null
    )

    private data class QueuedMessage(
        val localMessageIdForDb: String, // ID сообщения, как оно будет в БД
        val p2pEnvelopeJson: String // Сериализованный P2P конверт для отправки
    )

    init {
        repositoryScope.launch {

            val currentUserIdResult = cryptoRepository.getUserNickname()
            currentUserId = if (currentUserIdResult is Result.Success) currentUserIdResult.value else ""

            webRTCRepository.observeWebRTCEvents()
                .filterIsInstance<WebRTCEvent.BinaryMessageReceived>()
                .collect { event ->
                    Timber.tag(TAG).d("Получено BinaryMessageReceived от ${event.peerId.value}, размер: ${event.message.size}")
                    processIncomingP2pData(event.message, event.peerId.value)
                }
        }

        repositoryScope.launch {
            webRTCRepository.observeWebRTCEvents()
                .filterIsInstance<WebRTCEvent.DataChannelOpened>()
                .collect { event ->
                    Timber.tag(TAG).d("DataChannel открыт для пира: ${event.peerId.value}. Проверка очереди...")


                    val userProfileResult = cryptoRepository.loadUserProfile()
                    if (userProfileResult is Result.Success) {
                        Timber.tag(TAG).d("DataChannel открыт для пира: ${event.peerId.value}. Отправка профиля...")
                        sendMyProfileInfoToPeer(event.peerId.value, userProfileResult.value)
                    }
                    sendPendingP2pEnvelopesForPeer(event.peerId.value)
                }
        }
    }



    private suspend fun processIncomingP2pData(networkBytes: ByteArray, sourcePeerId: String) {
        val decompressedBytes = chatCompressor.decompress(networkBytes) ?: networkBytes
        val jsonString = String(decompressedBytes, StandardCharsets.UTF_8)
        val envelope = P2pMessageSerializer.deserializeEnvelope(jsonString)

        if (envelope == null) {
            Timber.tag(TAG).e("Не удалось десериализовать P2pMessageEnvelope от $sourcePeerId: $jsonString")
            return
        }

        Timber.tag(TAG).i("Получен P2P конверт от ${envelope.senderId} (через $sourcePeerId), тип: ${envelope.type}, ID: ${envelope.messageId}")

        when (envelope.type) {
            P2pMessageType.CHAT_MESSAGE -> {
                val chatPayload = P2pMessageSerializer.deserializePayload<ChatMessagePayload>(envelope.payload)
                if (chatPayload != null) {
                    saveIncomingChatMessage(envelope, chatPayload, sourcePeerId)
                } else {
                }
            }
            P2pMessageType.USER_PROFILE_INFO -> {
                val profilePayload = P2pMessageSerializer.deserializePayload<UserProfilePayload>(envelope.payload)
                if (profilePayload != null && profilePayload.userId == envelope.senderId) {
                    Timber.tag(TAG).i("Получена информация о профиле для ${profilePayload.userId} от ${envelope.senderId} (timestamp: ${envelope.timestamp})")
                    updateChatSessionProfileInfo(
                        sessionId = envelope.senderId,
                        newPeerName = profilePayload.displayName,
                        newPeerDescription = profilePayload.statusMessage,
                        newPeerAvatarUrl = profilePayload.avatarRemoteUrl, // Сначала используем URL
                        profileTimestamp = envelope.timestamp // Передаем timestamp полученного профиля
                    )
                    if (profilePayload.avatarFileId != null && profilePayload.avatarFileName != null) {
                        incomingFileMetadataStore[profilePayload.avatarFileId] = profilePayload
                    }
                } else { /* ... ошибка ... */ }
            }
            P2pMessageType.FILE_CHUNK -> {
                val chunkPayload = P2pMessageSerializer.deserializePayload<FileChunkPayload>(envelope.payload)
                if (chunkPayload != null) {
                    processIncomingFileChunk(envelope.senderId, chunkPayload)
                }
            }
            P2pMessageType.FILE_TRANSFER_COMPLETE -> {
                val completePayload = P2pMessageSerializer.deserializePayload<FileTransferCompletePayload>(envelope.payload)
                if (completePayload != null) {
                    // Определяем, был ли это файл чата или аватар
                    val metadata = incomingFileMetadataStore[completePayload.fileTransferId]
                    if (metadata is EnhancedMediaMetadata) { // Это был файл из чат-сообщения
                        val associatedMessage = messageDao.findMessageByFileTransferId(completePayload.fileTransferId) // Нужен этот метод
                        if (associatedMessage != null) {
                            finalizeFileReception(envelope.senderId, completePayload, associatedMessage.messageId, isAvatar = false)
                        } else { Timber.e("Не найдено сообщение для fileId ${completePayload.fileTransferId} при завершении") }
                    } else if (metadata is UserProfilePayload) { // Это был аватар
                        finalizeFileReception(envelope.senderId, completePayload, metadata.userId, isAvatar = true)
                    } else {
                        Timber.w("Неизвестный тип метаданных для fileId ${completePayload.fileTransferId} при завершении")
                    }
                } else { /* ... ошибка ... */ }
            }
            P2pMessageType.FILE_TRANSFER_ERROR -> {
                val errorPayload = P2pMessageSerializer.deserializePayload<FileTransferErrorPayload>(envelope.payload)
                if (errorPayload != null) {
                    Timber.e("Ошибка передачи файла ${errorPayload.fileTransferId} от ${envelope.senderId}: ${errorPayload.errorMessage}")
                    // TODO: Обновить статус сообщения в БД на FAILED, уведомить UI
                    val associatedMessage = messageDao.findMessageByFileTransferId(errorPayload.fileTransferId) // Нужен метод в DAO
                    associatedMessage?.let {
                        messageDao.updateMessageStatus(it.messageId, MessageStatus.FAILED.name)
                    }
                    incomingFileStreams.remove(errorPayload.fileTransferId)?.close()
                    File(context.cacheDir, "incoming_files/${errorPayload.fileTransferId}.part").delete()
                }
            }
            P2pMessageType.USER_PROFILE_INFO -> {
                val profilePayload = P2pMessageSerializer.deserializePayload<UserProfilePayload>(envelope.payload)
                if (profilePayload != null) {
                    Timber.tag(TAG).i("Получена информация о профиле для ${profilePayload.userId}: ${profilePayload.displayName}")
                    // TODO: Обработать информацию о профиле (сохранить, обновить UI и т.д.)
                    // Например, сохранить в отдельную таблицу профилей или обновить ChatSessionEntity.peerName/peerAvatarUrl
                } else {
                    Timber.tag(TAG).e("Не удалось десериализовать UserProfilePayload из конверта ${envelope.messageId}")
                }
            }
            P2pMessageType.ONION_ROUTE_SETUP, P2pMessageType.ONION_DATA_FORWARD -> {
                Timber.tag(TAG).i("Получено сообщение Onion-роутинга типа ${envelope.type}. Передача в соответствующий обработчик (TODO).")
                // TODO: Передать `envelope` или `envelope.payload` в модуль onion-роутинга
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

                val timestamp = System.currentTimeMillis()

                // 1. Создаем ChatMessagePayload
                val chatPayload = ChatMessagePayload(
                    chatMessageId = localMessageId, // Используем локальный ID как ID сообщения чата
                    sessionId = sessionId, // ID чата (получателя для P2P)
                    textContent = text,
                    messageType = MessageType.TEXT,
                    mediaMetadata = null,
                    quotedMessageId = null // TODO: Добавить поддержку цитирования
                )
                val chatPayloadJson = P2pMessageSerializer.serializePayload(chatPayload)
                    ?: return@withContext Result.Error(ChatError.OperationFailed("Не удалось сериализовать ChatMessagePayload", null))

                // 2. Создаем P2pMessageEnvelope
                val p2pEnvelope = P2pMessageEnvelope(
                    messageId = localMessageId, // Используем тот же ID для всего конверта
                    senderId = currentUserId,
                    timestamp = timestamp,
                    type = P2pMessageType.CHAT_MESSAGE,
                    payload = chatPayloadJson
                )

                val textBytesForDb = text.toByteArray(StandardCharsets.UTF_8)
                val compressedBytesForDb = chatCompressor.compress(textBytesForDb) ?: textBytesForDb
                val encryptedContentForDb = chatCipher.encrypt(compressedBytesForDb)
                    ?: return@withContext Result.Error(ChatError.EncryptionError("Не удалось зашифровать сообщение для БД", null))

                var messageStatusForDb = MessageStatus.SENDING
                val messageEntity = MessageEntity(
                    messageId = localMessageId, sessionId = sessionId, senderId = currentUserId,
                    content = encryptedContentForDb, timestamp = timestamp, status = messageStatusForDb.name,
                    isOutgoing = true, messageType = MessageType.TEXT.name
                )
                messageDao.insertMessage(messageEntity)
                updateChatSessionLastMessage(sessionId, messageEntity.messageId, timestamp)

                // 5. Отправка через WebRTC
                sendP2pEnvelope(sessionId, p2pEnvelope)
                // Возвращаем DomainMessage на основе сохраненной в БД сущности
                mapMessageEntityToDomain(messageDao.getMessageById(localMessageId) ?: messageEntity)
                    ?.let { Result.Success(it) }
                    ?: Result.Error(ChatError.OperationFailed("Не удалось смапить сообщение после обработки", null))

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Ошибка отправки текстового сообщения для $sessionId")
                Result.Error(ChatError.OperationFailed("Ошибка отправки сообщения", e))
            }
        }
    }

    private suspend fun saveIncomingChatMessage(envelope: P2pMessageEnvelope, payload: ChatMessagePayload, sourcePeerId: String) {
        // Для локального хранения используем текстовый контент из payload

        val localSessionId = if (payload.sessionId != currentUserId) payload.sessionId else envelope.senderId

        if (chatSessionDao.getSessionById(localSessionId) == null) {
            val sessionEntity = ChatSessionEntity(
                sessionId = localSessionId,
                peerName = envelope.senderId,
                chatType = ChatType.PEER_TO_PEER.toString(),
                participantIds = listOf(envelope.senderId)
            )
            chatSessionDao.insertOrUpdateSession(sessionEntity)
        }

        val textContent = payload.textContent ?: "" // Если текст null, используем пустую строку
        val contentToCompressSource = if (payload.messageType != MessageType.TEXT && payload.mediaMetadata != null) {
            payload.mediaMetadata.fileName // Для медиа шифруем имя файла
        } else {
            textContent
        }
        val contentToEncryptSource = chatCompressor.compress(contentToCompressSource.toByteArray(Charsets.UTF_8))
            ?: return Unit.also { Timber.tag(TAG).e("Ошибка сжатия входящего ${payload.messageType} сообщения ${payload.chatMessageId} для БД.") }
        val encryptedContentForDb = chatCipher.encrypt(contentToEncryptSource)
            ?: return Unit.also { Timber.tag(TAG).e("Ошибка шифрования входящего ${payload.messageType} сообщения ${payload.chatMessageId} для БД.") }

        val messageEntity = MessageEntity(
            messageId = payload.chatMessageId,
            sessionId = localSessionId,
            senderId = envelope.senderId,
            content = encryptedContentForDb,
            timestamp = envelope.timestamp,
            status = if (payload.messageType != MessageType.TEXT && payload.mediaMetadata != null) MessageStatus.PENDING_DOWNLOAD.name else MessageStatus.DELIVERED.name,
            isOutgoing = false,
            messageType = payload.messageType.name,
            mediaUrl = payload.mediaMetadata?.mediaUrl,
            mediaMimeType = payload.mediaMetadata?.mimeType,
            mediaFileSize = payload.mediaMetadata?.fileSize,
            mediaFileName = payload.mediaMetadata?.fileName,
            // Связываем с fileTransferId из метаданных, если это медиа
            fileTransferId = payload.mediaMetadata?.fileTransferId,
            quotedMessageId = payload.quotedMessageId
        )
        messageDao.insertMessage(messageEntity)
        // Если это медиа, сохраняем метаданные для приема чанков
        if (payload.messageType != MessageType.TEXT && payload.mediaMetadata != null) {
            incomingFileMetadataStore[payload.mediaMetadata.fileTransferId] = payload.mediaMetadata
            Timber.d("Метаданные для входящего файла ${payload.mediaMetadata.fileTransferId} сохранены.")
        }
        updateChatSessionLastMessage(localSessionId, payload.chatMessageId, envelope.timestamp, incrementUnread = true)
        Timber.tag(TAG).i("Входящее чат-сообщение ${payload.chatMessageId} (тип: ${payload.messageType}) от ${envelope.senderId} сохранено.")
    }

    override suspend fun sendMediaMessage(
        sessionId: String,
        localMediaUriString: String,
        messageType: MessageType,
        originalFileName: String?,
        localMessageId: String
    ): Result<DomainMessage, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                val currentUserId = (cryptoRepository.getUserNickname() as? Result.Success)?.value
                    ?: return@withContext Result.Error(ChatError.OperationFailed("Не удалось определить отправителя", null))

                val timestamp = System.currentTimeMillis()
                val localMediaUri = Uri.parse(localMediaUriString)

                val copiedFileInfo = copyFileToAppStorage(localMediaUri, messageType, originalFileName)
                    ?: return@withContext Result.Error(ChatError.StorageError("Не удалось обработать медиафайл", null))

                // 1. Проверка размера файла
                if (copiedFileInfo.size > MAX_FILE_SIZE_BYTES) {
                    Timber.w("Файл ${copiedFileInfo.displayName} слишком большой: ${copiedFileInfo.size} байт. Максимум: $MAX_FILE_SIZE_BYTES байт.")
                    return@withContext Result.Error(ChatError.OperationFailed("Файл слишком большой (макс. 25 МБ)", null))
                }

                // 2. Сохраняем сообщение в БД со статусом "SENDING" и метаданными
                val fileTransferId = UUID.randomUUID().toString()
                val contentForDb = (copiedFileInfo.displayName).toByteArray(StandardCharsets.UTF_8)
                val encryptedContentForDb = chatCipher.encrypt(contentForDb)
                    ?: return@withContext Result.Error(ChatError.EncryptionError("Шифрование для БД не удалось", null))

                val messageEntity = MessageEntity(
                    messageId = localMessageId, sessionId = sessionId, senderId = currentUserId,
                    content = encryptedContentForDb, timestamp = timestamp, status = MessageStatus.SENDING.name,
                    isOutgoing = true, messageType = messageType.name,
                    mediaLocalPath = copiedFileInfo.localPath,
                    mediaMimeType = copiedFileInfo.mimeType,
                    mediaFileSize = copiedFileInfo.size,
                    mediaFileName = copiedFileInfo.displayName
                    // fileTransferId = fileTransferId // Если добавили поле в MessageEntity
                )
                messageDao.insertMessage(messageEntity)
                updateChatSessionLastMessage(sessionId, messageEntity.messageId, timestamp)

                // 3. Отправляем P2P-сообщение с метаданными файла (тип CHAT_MESSAGE)
                val mediaMeta = EnhancedMediaMetadata(
                    fileTransferId = fileTransferId,
                    fileName = copiedFileInfo.displayName,
                    fileSize = copiedFileInfo.size,
                    mimeType = copiedFileInfo.mimeType
                )
                val chatPayload = ChatMessagePayload(
                    chatMessageId = localMessageId, sessionId = sessionId, textContent = null,
                    messageType = messageType, mediaMetadata = mediaMeta
                )
                val chatPayloadJson = P2pMessageSerializer.serializePayload(chatPayload)!!
                val metaEnvelope = P2pMessageEnvelope(
                    // ID для этого конверта может быть другим, или использовать localMessageId
                    messageId = "meta_${localMessageId}",
                    senderId = currentUserId, timestamp = timestamp,
                    type = P2pMessageType.CHAT_MESSAGE, payload = chatPayloadJson
                )
                // Отправляем мета-сообщение (оно встанет в очередь, если канал не готов)
                sendP2pEnvelope(sessionId, metaEnvelope, associatedLocalMessageId = localMessageId) // Связываем с ID сообщения в БД

                // 4. Сразу начинаем отправку чанков файла (они тоже встанут в очередь, если нужно)
                startSendingFileChunks(sessionId, fileTransferId, copiedFileInfo.localPath, currentUserId, localMessageId)

                mapMessageEntityToDomain(messageEntity)
                    ?.let { Result.Success(it) }
                    ?: Result.Error(ChatError.OperationFailed("Маппинг медиа-сообщения не удался", null))

            } catch (e: Exception) {
                Result.Error(ChatError.OperationFailed("Ошибка отправки медиа-сообщения", e))
            }
        }
    }

    private suspend fun startSendingFileChunks(
        targetPeerId: String,
        fileId: String, // fileTransferId
        filePath: String,
        senderId: String,
        associatedChatMessageId: String // ID чат-сообщения, к которому привязан файл
    ) {
        val file = File(filePath)
        if (!file.exists()) {
            Timber.e("Файл $filePath не найден для отправки чанков.")
            sendP2pFileTransferError(targetPeerId, fileId, "Файл не найден на устройстве отправителя.", associatedChatMessageId)
            messageDao.updateMessageStatus(associatedChatMessageId, MessageStatus.FAILED.name)
            return
        }

        val transferJob = repositoryScope.launch(defaultDispatcher) { // Выполняем в IO
            try {
                messageDao.updateMessageStatus(associatedChatMessageId, MessageStatus.UPLOADING.name) // Или SENDING_CHUNKS

                file.inputStream().use { inputStream ->
                    val totalBytes = file.length()
                    val totalChunks = ((totalBytes + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES).toInt()
                    var chunkIndex = 0
                    val buffer = ByteArray(CHUNK_SIZE_BYTES)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1 && isActive) {
                        val chunkData = if (bytesRead == CHUNK_SIZE_BYTES) buffer else buffer.copyOfRange(0, bytesRead)
                        val chunkPayload = FileChunkPayload(
                            fileTransferId = fileId, chunkIndex = chunkIndex, totalChunks = totalChunks,
                            dataBase64 = Base64.encodeToString(chunkData, Base64.NO_WRAP),
                            isLastChunk = (chunkIndex + 1 == totalChunks)
                        )
                        val payloadJson = P2pMessageSerializer.serializePayload(chunkPayload)!!
                        val envelope = P2pMessageEnvelope(
                            messageId = "chunk_${fileId}_$chunkIndex", senderId = senderId,
                            timestamp = System.currentTimeMillis(), type = P2pMessageType.FILE_CHUNK, payload = payloadJson
                        )
                        // Отправляем чанк (он встанет в очередь, если канал не готов)
                        val sendChunkResult = sendP2pEnvelope(targetPeerId, envelope, compress = false, associatedLocalMessageId = null)

                        if (sendChunkResult !is Result.Success) {
                            Timber.e("Не удалось отправить чанк #$chunkIndex для файла $fileId")
                            // Ошибка будет обработана в sendP2pEnvelope, здесь можно обновить статус
                            messageDao.updateMessageStatus(associatedChatMessageId, MessageStatus.FAILED.name)
                            sendP2pFileTransferError(targetPeerId, fileId, "Ошибка отправки чанка #$chunkIndex.", associatedChatMessageId)
                            this.cancel("Chunk send failed") // Отменяем Job этой передачи
                            return@use
                        }
                        chunkIndex++
                        Timber.v("Чанк #$chunkIndex/$totalChunks для файла $fileId поставлен в очередь/отправлен.")
                    }
                }
                if (isActive) { // Если не были отменены
                    sendP2pFileTransferComplete(targetPeerId, fileId, true, associatedChatMessageId)
                    messageDao.updateMessageStatus(associatedChatMessageId, MessageStatus.SENT.name)
                    Timber.i("Все чанки для файла $fileId успешно отправлены/поставлены в очередь.")
                } else {
                    Timber.w("Отправка чанков для файла $fileId была отменена.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Ошибка во время отправки чанков файла $fileId")
                messageDao.updateMessageStatus(associatedChatMessageId, MessageStatus.FAILED.name)
                sendP2pFileTransferError(targetPeerId, fileId, "Внутренняя ошибка при отправке файла: ${e.message}", associatedChatMessageId)
            } finally {
                activeOutgoingFileTransfers.remove(fileId)
            }
        }
        activeOutgoingFileTransfers[fileId] = transferJob
    }

    // Общий метод для отправки P2P конверта
    private suspend fun sendP2pEnvelope(
        targetPeerId: String,
        envelope: P2pMessageEnvelope,
        compress: Boolean = true,
        associatedLocalMessageId: String? = null // Для обновления статуса оригинального сообщения
    ): Result<Unit, ChatError> {
        val envelopeJson = P2pMessageSerializer.serialize(envelope)
            ?: return Result.Error(ChatError.OperationFailed("Сериализация P2PEnvelope (${envelope.type}) не удалась", null))

        val jsonBytes = envelopeJson.toByteArray(StandardCharsets.UTF_8)
        val bytesToSend = if (compress) (chatCompressor.compress(jsonBytes) ?: jsonBytes) else jsonBytes
        val webRTCMessage = WebRTCMessage(PeerId(targetPeerId), contentBytes = bytesToSend)

        if (webRTCRepository.isDataChannelReady(PeerId(targetPeerId))) {
            val sendResult = webRTCRepository.sendMessage(webRTCMessage)
            if (sendResult is Result.Success) {
                Timber.i("[OUT] P2P пиру $targetPeerId: тип ${envelope.type}, ID ${envelope.messageId} -> ОТПРАВЛЕНО")
                associatedLocalMessageId?.let { msgId -> // Обновляем статус, если это мета-сообщение файла
                    if (envelope.type == P2pMessageType.CHAT_MESSAGE && P2pMessageSerializer.deserializePayload<ChatMessagePayload>(envelope.payload)?.mediaMetadata != null) {
                        messageDao.updateMessageStatus(msgId, MessageStatus.SENT.name)
                    }
                }
                return Result.Success(Unit)
            } else {
                Timber.e("[OUT] P2P пиру $targetPeerId: тип ${envelope.type}, ID ${envelope.messageId} -> ОШИБКА ОТПРАВКИ WebRTC: ${(sendResult as Result.Error).error}")
                associatedLocalMessageId?.let { messageDao.updateMessageStatus(it, MessageStatus.FAILED.name) }
                return Result.Error(ChatError.NetworkError("Ошибка отправки P2P через WebRTC", null))
            }
        } else {
            Timber.i("[OUT] P2P пиру $targetPeerId: тип ${envelope.type}, ID ${envelope.messageId} -> В ОЧЕРЕДЬ (канал не готов)")
            queueMutex.withLock {
                pendingP2pEnvelopesQueue.computeIfAbsent(targetPeerId) { mutableListOf() }
                    .add(P2pEnvelopeQueueItem(envelopeJson, associatedLocalMessageId))
            }
            webRTCRepository.initiateCall(PeerId(targetPeerId))
            // Статус в БД для associatedLocalMessageId остается SENDING
            return Result.Success(Unit) // Сообщение поставлено в очередь
        }
    }

    // Отправка сообщений из очереди
    private suspend fun sendPendingP2pEnvelopesForPeer(peerIdString: String) {
        queueMutex.withLock {
            pendingP2pEnvelopesQueue.remove(peerIdString)?.let { itemsToSend ->
                if (itemsToSend.isNotEmpty()) {
                    Timber.tag(TAG).i("Отправка ${itemsToSend.size} ожидающих P2P конвертов пиру $peerIdString")
                    for (item in itemsToSend) {
                        val jsonBytes = item.p2pEnvelopeJson.toByteArray(StandardCharsets.UTF_8)
                        // Предполагаем, что конверт уже был сжат, если нужно, перед постановкой в очередь.
                        // Но лучше сжимать непосредственно перед отправкой.
                        val envelopeForCheck = P2pMessageSerializer.deserializeEnvelope(item.p2pEnvelopeJson)
                        val compressThis = envelopeForCheck?.type != P2pMessageType.FILE_CHUNK // Не сжимаем чанки
                        val bytesToSend = if(compressThis) (chatCompressor.compress(jsonBytes) ?: jsonBytes) else jsonBytes

                        val sendResult = webRTCRepository.sendMessage(WebRTCMessage(PeerId(peerIdString), contentBytes = bytesToSend))
                        item.associatedLocalMessageId?.let { msgId ->
                            val newStatus = if (sendResult is Result.Success) MessageStatus.SENT else MessageStatus.FAILED
                            messageDao.updateMessageStatus(msgId, newStatus.name)
                            if (newStatus == MessageStatus.FAILED) {
                                Timber.tag(TAG).w("Не удалось отправить ожидающий P2P конверт (связан с ${item.associatedLocalMessageId}) пиру $peerIdString")
                            }
                        }
                    }
                }
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

    private suspend fun sendP2pFileTransferComplete(targetPeerId: String, fileId: String, success: Boolean, associatedChatMessageId: String, hash: String? = null) {
        val currentUserId = (cryptoRepository.getUserNickname() as? Result.Success)?.value?: return
        val completePayload = FileTransferCompletePayload(fileId, success, hash)
        val payloadJson = P2pMessageSerializer.serializePayload(completePayload)!!
        val envelope = P2pMessageEnvelope("ft_comp_${UUID.randomUUID()}", currentUserId, System.currentTimeMillis(), P2pMessageType.FILE_TRANSFER_COMPLETE, payloadJson)
        sendP2pEnvelope(targetPeerId, envelope, associatedLocalMessageId = null) // Не связан напрямую с обновлением статуса чат-сообщения
    }
    private suspend fun sendP2pFileTransferError(targetPeerId: String, fileId: String, errorMessage: String, associatedChatMessageId: String) {
        val currentUserId = (cryptoRepository.getUserNickname() as? Result.Success)?.value?: return
        val errorPayload = FileTransferErrorPayload(fileId, errorMessage)
        val payloadJson = P2pMessageSerializer.serializePayload(errorPayload)!!
        val envelope = P2pMessageEnvelope("ft_err_${UUID.randomUUID()}", currentUserId, System.currentTimeMillis(), P2pMessageType.FILE_TRANSFER_ERROR, payloadJson)
        sendP2pEnvelope(targetPeerId, envelope, associatedLocalMessageId = null)
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
                participantIds = entity.participantIds,
                lastKnownPeerProfileTimestamp = entity.lastKnownPeerProfileTimestamp,
                peerDescription = entity.peerDescription
            )
        } catch (e: IllegalArgumentException) { // Ошибка парсинга enum
            Timber.tag(TAG).e(e, "Ошибка маппинга ChatSessionEntity в Domain для ${entity.sessionId}")
            null
        }
    }

    override suspend fun updateChatSessionProfileInfo(
        sessionId: String,
        newPeerName: String,
        newPeerDescription: String?,
        newPeerAvatarUrl: String?,
        profileTimestamp: Long? // НОВЫЙ ПАРАМЕТР
    ): Result<Unit, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                val session = chatSessionDao.getSessionById(sessionId)
                if (session != null) {
                    // Только обновляем, если полученный профиль новее или такой же
                    // (на случай получения старых/повторных сообщений профиля)
                    if (profileTimestamp == null || profileTimestamp >= (session.lastKnownPeerProfileTimestamp ?: 0L) ) {
                        val updatedSession = session.copy(
                            peerName = newPeerName,
                            peerDescription = newPeerDescription ?: session.peerDescription,
                            peerAvatarUrl = newPeerAvatarUrl ?: session.peerAvatarUrl,
                            lastKnownPeerProfileTimestamp = profileTimestamp ?: session.lastKnownPeerProfileTimestamp // Обновляем timestamp
                        )
                        chatSessionDao.insertOrUpdateSession(updatedSession) // Используем insertOrUpdate
                        Timber.tag(TAG).i("Информация профиля для сессии $sessionId обновлена (ts: $profileTimestamp).")
                    } else {
                        Timber.tag(TAG).i("Получен устаревший профиль для сессии $sessionId (ts: $profileTimestamp, known: ${session.lastKnownPeerProfileTimestamp}). Игнорируем.")
                    }
                    Result.Success(Unit)
                } else {
                    // Если сессии нет, но пришел профиль, можно создать сессию
                    // Это может произойти, если профиль пришел раньше, чем мы создали сессию через getOrCreateChatSession
                    val currentUserId = (cryptoRepository.getUserNickname() as? Result.Success)?.value
                    if (currentUserId != null && sessionId != currentUserId) { // Не создаем сессию с самим собой
                        Timber.w("Сессия $sessionId не найдена, но получен профиль. Создаем новую сессию.")
                        val newSession = ChatSessionEntity(
                            sessionId = sessionId,
                            peerName = newPeerName,
                            peerDescription = newPeerDescription,
                            peerAvatarUrl = newPeerAvatarUrl,
                            lastKnownPeerProfileTimestamp = profileTimestamp,
                            chatType = ChatType.PEER_TO_PEER.name, // Предполагаем P2P
                            participantIds = listOf(sessionId)
                        )
                        chatSessionDao.insertOrUpdateSession(newSession)
                        Result.Success(Unit)
                    } else {
                        Result.Error(ChatError.NotFound("Сессия чата $sessionId не найдена и не удалось создать новую"))
                    }
                }
            } catch (e: Exception) {
                Result.Error(ChatError.StorageError("Не удалось обновить профиль сессии $sessionId", e))
            }
        }
    }


    override suspend fun getSessionInfo(sessionId: String): Result<DomainUserProfile, ChatError> {

        val chatSession = chatSessionDao.getSessionById(sessionId)
            ?: return Result.Error(ChatError.NotFound("Session with id $sessionId doesn't exist"))

        return Result.Success(
            DomainUserProfile(
                userId = sessionId,
                displayName = chatSession.peerName,
                avatarUri = chatSession.peerAvatarUrl,
                statusMessage = chatSession.peerDescription,
                lastLocalEditTimestamp = 0L
            )
        )
    }


    override suspend fun sendMyProfileInfoToPeer(targetPeerId: String, profile: DomainUserProfile): Result<Unit, ChatError> {
        return withContext(defaultDispatcher) {
            try {
                // Проверка, нужно ли отправлять (если профиль не менялся с последней отправки)
                val sessionInfo = chatSessionDao.getSessionById(targetPeerId)
                if (sessionInfo?.lastKnownPeerProfileTimestamp != null &&
                    profile.lastLocalEditTimestamp <= sessionInfo.lastKnownPeerProfileTimestamp!!) {
                    Timber.d("Профиль для $targetPeerId не требует обновления (локальное изм: ${profile.lastLocalEditTimestamp}, известно пиру: ${sessionInfo.lastKnownPeerProfileTimestamp}).")
                    return@withContext Result.Success(Unit) // Уже актуально
                }

                val currentUserId = profile.userId
                var avatarFileIdForPayload: String? = null
                // ... (остальная логика подготовки avatarFileId, avatarFileName и т.д. как раньше) ...
                var avatarFileNameForPayload: String? = null
                var avatarMimeTypeForPayload: String? = null
                var avatarFileSizeForPayload: Long? = null
                var avatarLocalPathForSending: String? = null

                if (!profile.avatarUri.isNullOrBlank() && !profile.avatarUri.startsWith("http")) {
                    val avatarUri = Uri.parse(profile.avatarUri)
                    val originalFileName = getFileNameFromUri(avatarUri, "avatar_${UUID.randomUUID()}")
                    val copiedAvatarInfo = copyFileToAppStorage(avatarUri, MessageType.IMAGE, originalFileName, AVATARS_SUBDIR)
                    if (copiedAvatarInfo != null) {
                        if (copiedAvatarInfo.size <= MAX_FILE_SIZE_BYTES / 5) {
                            avatarFileIdForPayload = UUID.randomUUID().toString()
                            avatarFileNameForPayload = copiedAvatarInfo.displayName
                            avatarMimeTypeForPayload = copiedAvatarInfo.mimeType
                            avatarFileSizeForPayload = copiedAvatarInfo.size
                            avatarLocalPathForSending = copiedAvatarInfo.localPath
                        } else { Timber.w("Аватар слишком большой для P2P.") }
                    } else { Timber.w("Не удалось обработать локальный аватар ${profile.avatarUri}.") }
                }


                val userProfilePayload = UserProfilePayload(
                    userId = currentUserId,
                    displayName = profile.displayName,
                    statusMessage = profile.statusMessage,
                    avatarFileId = avatarFileIdForPayload,
                    avatarFileName = avatarFileNameForPayload,
                    avatarMimeType = avatarMimeTypeForPayload,
                    avatarFileSize = avatarFileSizeForPayload,
                    avatarRemoteUrl = if (avatarFileIdForPayload == null && profile.avatarUri?.startsWith("http") == true) profile.avatarUri else null
                )
                val payloadJson = P2pMessageSerializer.serializePayload(userProfilePayload)
                    ?: return@withContext Result.Error(ChatError.OperationFailed("Сериализация UserProfilePayload не удалась", null))

                val currentTimestamp = System.currentTimeMillis() // Фиксируем время отправки
                val envelope = P2pMessageEnvelope(
                    messageId = "profile_update_${UUID.randomUUID()}",
                    senderId = currentUserId,
                    timestamp = currentTimestamp, // Используем фиксированное время
                    type = P2pMessageType.USER_PROFILE_INFO,
                    payload = payloadJson
                )

                val sendMetaResult = sendP2pEnvelope(targetPeerId, envelope)

                if (sendMetaResult is Result.Success) {
                    // Обновляем lastKnownPeerProfileTimestamp в БД для этой сессии
                    updateChatSessionProfileTimestamp(targetPeerId, currentTimestamp)

                    if (avatarFileIdForPayload != null && avatarLocalPathForSending != null) {
                        startSendingFileChunks(targetPeerId, avatarFileIdForPayload, avatarLocalPathForSending, currentUserId, "")
                    }
                }
                sendMetaResult
            } catch (e: Exception) {
                Result.Error(ChatError.OperationFailed("Ошибка отправки профиля", e))
            }
        }
    }

    // Новый вспомогательный метод для обновления только timestamp профиля в сессии
    private suspend fun updateChatSessionProfileTimestamp(sessionId: String, timestamp: Long) {
        withContext(defaultDispatcher) {
            try {
                val session = chatSessionDao.getSessionById(sessionId)
                if (session != null) {
                    if (timestamp >= (session.lastKnownPeerProfileTimestamp ?: 0L)) {
                        chatSessionDao.insertOrUpdateSession(session.copy(lastKnownPeerProfileTimestamp = timestamp))
                        Timber.d("Обновлен lastKnownPeerProfileTimestamp для сессии $sessionId на $timestamp")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Ошибка обновления lastKnownPeerProfileTimestamp для сессии $sessionId")
            }
        }
    }



    private suspend fun copyFileToAppStorage(
        sourceUri: Uri,
        messageTypeForSubdir: MessageType, // Используется для определения подпапки, если targetSubDir null
        originalNameFromUri: String?,
        targetSubDirOverride: String? = null // Явное указание подпапки
    ): CopiedFileInfo? {
        return withContext(defaultDispatcher) {
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            var destinationFile: File? = null
            try {
                val fileName = getFileNameFromUri(sourceUri, originalNameFromUri)
                Timber.d("Копирование файла. Source URI: $sourceUri, Resolved FileName: $fileName")

                val finalTargetSubDir = targetSubDirOverride ?: when (messageTypeForSubdir) {
                    MessageType.IMAGE, MessageType.VIDEO, MessageType.AUDIO, MessageType.FILE -> MEDIA_SUBDIR
                    else -> MEDIA_SUBDIR // По умолчанию
                }
                Timber.d("Целевая подпапка: $finalTargetSubDir")

                val mediaDir = File(context.filesDir, finalTargetSubDir)
                if (!mediaDir.exists() && !mediaDir.mkdirs()) {
                    Timber.e("Не удалось создать директорию: ${mediaDir.absolutePath}")
                    return@withContext null
                }

                val extension = fileName.substringAfterLast('.', "")
                val uniqueFileNameInternal = "${UUID.randomUUID()}${if (extension.isNotEmpty()) ".$extension" else ""}"
                destinationFile = File(mediaDir, uniqueFileNameInternal)

                inputStream = if (sourceUri.scheme == "file") {
                    // Для file:/// URI, если ContentResolver не сработает, пробуем напрямую
                    val filePath = sourceUri.path
                    if (filePath != null) File(filePath).inputStream() else null
                } else {
                    context.contentResolver.openInputStream(sourceUri)
                }

                if (inputStream == null) {
                    Timber.e("Не удалось открыть InputStream для URI: $sourceUri (схема: ${sourceUri.scheme})")
                    return@withContext null
                }

                outputStream = FileOutputStream(destinationFile)
                val fileSize = inputStream.copyTo(outputStream)
                Timber.d("Файл скопирован в ${destinationFile.absolutePath}, размер: $fileSize байт")

                if (fileSize > 0) {
                    CopiedFileInfo(destinationFile.absolutePath, fileName, fileSize, context.contentResolver.getType(sourceUri))
                } else {
                    Timber.e("Скопированный файл пуст или ошибка копирования: ${destinationFile.absolutePath}")
                    destinationFile.delete()
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Ошибка при копировании файла из URI: $sourceUri в хранилище приложения.")
                destinationFile?.delete() // Пытаемся удалить, если файл был создан
                null
            } finally {
                try {
                    inputStream?.close()
                    outputStream?.close()
                } catch (ioe: IOException) {
                    Timber.e(ioe, "Ошибка при закрытии потоков.")
                }
            }
        }
    }

    // Обновленное имя и более надежная логика
    private fun getFileNameFromUri(uri: Uri, fallbackName: String?): String {
        var name: String? = null
        if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        name = it.getString(displayNameIndex)
                    }
                }
            }
        }
        if (name == null && ContentResolver.SCHEME_FILE == uri.scheme) {
            name = uri.lastPathSegment
        }
        return name?.ifBlank { null } ?: fallbackName ?: UUID.randomUUID().toString()
    }


    // --- Вспомогательные функции ---
    private data class CopiedFileInfo(
        val localPath: String,
        val displayName: String,
        val size: Long,
        val mimeType: String?
    )

    // Исправленное имя функции
    private fun getFileNameFromContentResolver(uri: Uri, fallbackName: String?): String {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { // Использование use для автоматического закрытия курсора
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        name = it.getString(displayNameIndex)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name ?: fallbackName ?: UUID.randomUUID().toString() // Если все остальное не удалось
    }


    private suspend fun processIncomingFileChunk(senderId: String, chunkPayload: FileChunkPayload) {
        withContext(defaultDispatcher) {
            try {
                val fileId = chunkPayload.fileTransferId
                // Получаем метаданные (могут быть EnhancedMediaMetadata или UserProfilePayload)
                val metadataObject = incomingFileMetadataStore[fileId]

                if (metadataObject == null) {
                    Timber.e("Получен чанк для неизвестного fileId: $fileId от $senderId. Метаданные не найдены.")
                    // Не отправляем ошибку, так как не знаем, к какому сообщению это относится,
                    // если только не хранить fileId -> chatMessageId отдельно.
                    return@withContext
                }

                val (fileNameForLog, associatedMessageIdForError) = when (metadataObject) {
                    is EnhancedMediaMetadata -> metadataObject.fileName to messageDao.findMessageByFileTransferId(fileId)?.messageId
                    is UserProfilePayload -> metadataObject.avatarFileName to metadataObject.userId // Используем userId как идентификатор для аватара
                    else -> "unknown_file" to "unknown_assoc_id"
                }

                val tempFileDir = File(context.cacheDir, INCOMING_TEMP_SUBDIR)
                if (!tempFileDir.exists()) tempFileDir.mkdirs()
                val tempFile = File(tempFileDir, "$fileId.part")

                val outputStream = incomingFileStreams.computeIfAbsent(fileId) {
                    FileOutputStream(tempFile, chunkPayload.chunkIndex > 0)
                }

                val chunkBytes = Base64.decode(chunkPayload.dataBase64, Base64.NO_WRAP)
                outputStream.write(chunkBytes)
                Timber.v("Чанк #${chunkPayload.chunkIndex + 1}/${chunkPayload.totalChunks} для файла '$fileNameForLog' (ID: $fileId) записан. Размер: ${chunkBytes.size}")

                // TODO: Обновить прогресс загрузки в UI

                if (chunkPayload.isLastChunk || (chunkPayload.chunkIndex + 1 == chunkPayload.totalChunks)) {
                    Timber.i("Получен последний чанк для файла '$fileNameForLog' (ID: $fileId). Завершение приема.")
                    outputStream.flush()
                    outputStream.close()
                    incomingFileStreams.remove(fileId)

                    val isAvatar = metadataObject is UserProfilePayload
                    val identifier = if (isAvatar) (metadataObject as UserProfilePayload).userId else associatedMessageIdForError ?: fileId

                    finalizeFileReception(senderId, FileTransferCompletePayload(fileId, true, null), identifier, isAvatar)
                }
            } catch (e: Exception) {
                Timber.e(e, "Ошибка при обработке входящего чанка файла ${chunkPayload.fileTransferId}")
                incomingFileStreams.remove(chunkPayload.fileTransferId)?.close()
                File(context.cacheDir, "$INCOMING_TEMP_SUBDIR/${chunkPayload.fileTransferId}.part").delete()

                val metadataObject = incomingFileMetadataStore[chunkPayload.fileTransferId]
                val assocId = when(metadataObject) {
                    is EnhancedMediaMetadata -> messageDao.findMessageByFileTransferId(chunkPayload.fileTransferId)?.messageId ?: chunkPayload.fileTransferId
                    is UserProfilePayload -> metadataObject.userId
                    else -> chunkPayload.fileTransferId
                }
                sendP2pFileTransferError(senderId, chunkPayload.fileTransferId, "Ошибка на стороне получателя: ${e.message}", assocId)
                if (metadataObject is EnhancedMediaMetadata) {
                    messageDao.updateMessageStatusByFileTransferId(chunkPayload.fileTransferId, MessageStatus.FAILED.name)
                }
                // Для аватара ошибку нужно обработать иначе (например, сбросить попытку загрузки)
            }
        }
    }

    private suspend fun finalizeFileReception(
        senderId: String,
        completePayload: FileTransferCompletePayload,
        associatedIdentifier: String, // Может быть chatMessageId или userId (для аватара)
        isAvatar: Boolean
    ) {
        withContext(defaultDispatcher) {
            val fileId = completePayload.fileTransferId
            val metadataOrProfilePayload = incomingFileMetadataStore.remove(fileId)

            if (metadataOrProfilePayload == null) {
                Timber.e("Не найдены метаданные для завершения приема файла $fileId (связано с $associatedIdentifier)")
                return@withContext
            }

            val tempFileDir = File(context.cacheDir, INCOMING_TEMP_SUBDIR)
            val tempFile = File(tempFileDir, "$fileId.part")

            if (!tempFile.exists()) {
                Timber.e("Временный файл $fileId.part не найден для $associatedIdentifier.")
                if (!isAvatar) messageDao.updateMessageStatus(associatedIdentifier, MessageStatus.FAILED.name)
                // Для аватара просто логируем
                return@withContext
            }

            if (completePayload.success) {
                val fileName = when(metadataOrProfilePayload) {
                    is EnhancedMediaMetadata -> metadataOrProfilePayload.fileName
                    is UserProfilePayload -> metadataOrProfilePayload.avatarFileName ?: "$fileId.jpg" // Имя для аватара
                    else -> "$fileId.dat"
                }

                val finalSubDir = if (isAvatar) AVATARS_SUBDIR else MEDIA_SUBDIR
                val finalMediaDir = File(context.filesDir, finalSubDir)
                if (!finalMediaDir.exists()) finalMediaDir.mkdirs()
                val finalFile = File(finalMediaDir, fileName)

                try {
                    if (tempFile.renameTo(finalFile)) {
                        Timber.i("Файл '$fileName' (ID: $fileId) успешно собран и сохранен в ${finalFile.absolutePath}")
                        if (isAvatar) {
                            // Обновляем путь к аватару в профиле пользователя (локально)
                            // и в ChatSessionEntity для этого пира
                            val userProfileResult = cryptoRepository.loadUserProfile() // Загружаем свой профиль, если это наш аватар
                            if (userProfileResult is Result.Success && userProfileResult.value.userId == associatedIdentifier) {
                                cryptoRepository.saveUserProfile(userProfileResult.value.copy(avatarUri = finalFile.toURI().toString()))
                            }
                            // Обновляем ChatSession, если это аватар другого пользователя
                            updateChatSessionProfileInfo(associatedIdentifier, (metadataOrProfilePayload as UserProfilePayload).displayName, metadataOrProfilePayload.statusMessage, finalFile.toURI().toString(), System.currentTimeMillis())
                        } else {
                            messageDao.updateMessageLocalPathAndStatus(associatedIdentifier, finalFile.absolutePath, MessageStatus.DOWNLOADED.name)
                        }
                    } else { /* ... ошибка перемещения ... */ }
                } catch (e: Exception) { /* ... ошибка сохранения ... */ }
            } else { /* ... отправитель сообщил о неудаче ... */ }
            if (!completePayload.success || !tempFile.exists()) tempFile.delete() // Удаляем временный файл в любом случае при ошибке или если он еще есть
        }
    }


}

