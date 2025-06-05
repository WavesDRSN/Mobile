package ru.drsn.waves.domain.usecase.chat

import ru.drsn.waves.domain.model.chat.ChatError
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IChatRepository
import javax.inject.Inject


/**
 * UseCase для загрузки предыдущей "страницы" сообщений.
 */
class LoadMoreMessagesUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {
    suspend operator fun invoke(sessionId: String, beforeTimestamp: Long, limit: Int = 20): Result<Boolean, ChatError> {
        return chatRepository.loadMoreMessages(sessionId, beforeTimestamp, limit)
    }
}