package ru.drsn.waves.domain.usecase.chatlist

import kotlinx.coroutines.flow.Flow
import ru.drsn.waves.domain.model.chat.DomainChatSession
import ru.drsn.waves.domain.repository.IChatRepository
import javax.inject.Inject

/**
 * UseCase для наблюдения за списком всех сессий чатов пользователя.
 */
class ObserveChatSessionsUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {
    operator fun invoke(): Flow<List<DomainChatSession>> {
        return chatRepository.observeChatSessions()
    }
}
