package ru.drsn.waves.domain.usecase.chat

import ru.drsn.waves.domain.model.chat.ChatError
import ru.drsn.waves.domain.model.chat.ChatType
import ru.drsn.waves.domain.model.chat.DomainChatSession
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IChatRepository
import javax.inject.Inject


/**
 * UseCase для получения или создания информации о сессии чата.
 * Полезно для получения имени собеседника/названия чата для отображения в Toolbar.
 */
class GetOrCreateChatSessionUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {
    suspend operator fun invoke(
        peerId: String, // Для личного чата это sessionId
        peerName: String, // Имя, которое мы знаем (может быть из списка контактов)
        chatType: ChatType,
        participantIds: List<String> // Для группового чата
    ): Result<DomainChatSession, ChatError> {
        return chatRepository.getOrCreateChatSession(peerId, peerName, chatType, participantIds)
    }
}