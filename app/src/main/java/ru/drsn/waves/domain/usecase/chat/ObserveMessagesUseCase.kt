package ru.drsn.waves.domain.usecase.chat

import kotlinx.coroutines.flow.Flow
import ru.drsn.waves.domain.model.chat.*
import ru.drsn.waves.domain.repository.IChatRepository
import javax.inject.Inject

/**
 * UseCase для наблюдения за сообщениями в конкретной сессии чата.
 */
class ObserveMessagesUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {
    operator fun invoke(sessionId: String): Flow<List<DomainMessage>> {
        return chatRepository.observeMessagesForSession(sessionId)
    }
}
