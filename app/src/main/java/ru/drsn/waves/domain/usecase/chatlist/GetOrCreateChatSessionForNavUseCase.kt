package ru.drsn.waves.domain.usecase.chatlist

import ru.drsn.waves.domain.model.chat.DomainChatSession
import ru.drsn.waves.domain.repository.IChatRepository
import javax.inject.Inject
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.chat.ChatType
import ru.drsn.waves.domain.model.chat.ChatError

/**
 * UseCase для получения или создания сессии чата перед переходом к чату.
 * (Может быть полезно, чтобы убедиться, что сессия существует в БД,
 * особенно если мы переходим к чату с новым контактом).
 * Хотя основное создание сессии может происходить в ChatViewModel при открытии.
 */
class GetOrCreateChatSessionForNavUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {
    suspend operator fun invoke(
        peerId: String,
        peerName: String,
        chatType: ChatType, // Явное указание пакета
        participantIds: List<String>
    ): Result<DomainChatSession, ChatError> {
        return chatRepository.getOrCreateChatSession(peerId, peerName, chatType, participantIds)
    }
}
