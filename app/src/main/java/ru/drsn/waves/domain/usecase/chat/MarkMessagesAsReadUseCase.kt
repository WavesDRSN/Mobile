package ru.drsn.waves.domain.usecase.chat

import ru.drsn.waves.domain.model.chat.ChatError
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IChatRepository
import javax.inject.Inject

/**
 * UseCase для пометки сообщений в чате как прочитанных.
 */
class MarkMessagesAsReadUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {
    suspend operator fun invoke(sessionId: String): Result<Unit, ChatError> {
        return chatRepository.markMessagesAsRead(sessionId)
    }
}