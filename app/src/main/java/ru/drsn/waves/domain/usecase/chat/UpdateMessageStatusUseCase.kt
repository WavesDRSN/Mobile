package ru.drsn.waves.domain.usecase.chat

import ru.drsn.waves.domain.model.chat.ChatError
import ru.drsn.waves.domain.model.chat.MessageStatus
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IChatRepository
import javax.inject.Inject


/**
 * UseCase для обновления статуса сообщения (например, доставлено, прочитано).
 * Этот UseCase может вызываться, когда приходят подтверждения с сервера или от другого пира.
 */
class UpdateMessageStatusUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {
    suspend operator fun invoke(messageId: String, newStatus: MessageStatus): Result<Unit, ChatError> {
        return chatRepository.updateMessageStatus(messageId, newStatus)
    }
}