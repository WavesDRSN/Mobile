package ru.drsn.waves.domain.usecase.chat

import ru.drsn.waves.domain.model.chat.ChatError
import ru.drsn.waves.domain.model.profile.DomainUserProfile
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IChatRepository
import javax.inject.Inject

class GetSessionInfoUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {
    suspend operator fun invoke(sessionId: String): Result<DomainUserProfile, ChatError> {
        return chatRepository.getSessionInfo(sessionId)
    }
}