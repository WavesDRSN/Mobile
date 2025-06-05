package ru.drsn.waves.domain.usecase.chat

import ru.drsn.waves.domain.model.chat.ChatError
import ru.drsn.waves.domain.model.chat.DomainMessage
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IChatRepository
import java.util.UUID
import javax.inject.Inject

/**
 * UseCase для отправки текстового сообщения.
 */
class SendTextMessageUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {
    suspend operator fun invoke(sessionId: String, text: String): Result<DomainMessage, ChatError> {
        if (text.isBlank()) {
            return Result.Error(ChatError.OperationFailed("Сообщение не может быть пустым", null))
        }
        // Генерируем временный локальный ID для оптимистичного обновления UI
        val localMessageId = "local_${UUID.randomUUID()}"
        return chatRepository.sendTextMessage(sessionId, text, localMessageId)
    }
}