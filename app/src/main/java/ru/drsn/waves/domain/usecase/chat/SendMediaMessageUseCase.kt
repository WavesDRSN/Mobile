package ru.drsn.waves.domain.usecase.chat

import ru.drsn.waves.domain.model.chat.ChatError
import ru.drsn.waves.domain.model.chat.DomainMessage
import ru.drsn.waves.domain.model.chat.MessageType
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IChatRepository
import java.util.UUID
import javax.inject.Inject

/**
 * UseCase для отправки медиа-сообщения.
 * (Пока упрощенная сигнатура, можно расширить)
 */
class SendMediaMessageUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        localMediaUri: String, // URI файла в локальной системе
        messageType: MessageType, // IMAGE, VIDEO, etc.
        originalFileName: String? = null
    ): Result<DomainMessage, ChatError> {
        if (localMediaUri.isBlank()) {
            return Result.Error(ChatError.OperationFailed("URI медиафайла не может быть пустым", null))
        }
        val localMessageId = "local_media_${UUID.randomUUID()}"
        return chatRepository.sendMediaMessage(sessionId, localMediaUri, messageType, originalFileName, localMessageId)
    }
}