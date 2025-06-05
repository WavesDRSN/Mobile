package ru.drsn.waves.domain.usecase.chat

import ru.drsn.waves.domain.model.chat.ChatError
import ru.drsn.waves.domain.model.chat.DomainMessage
import ru.drsn.waves.domain.model.chat.MessageType
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.profile.DomainUserProfile
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IChatRepository
import ru.drsn.waves.domain.usecase.profile.LoadUserProfileUseCase
import java.util.UUID
import javax.inject.Inject

class SendMyProfileInfoToPeerUseCase @Inject constructor(
    private val chatRepository: IChatRepository
) {

    suspend operator fun invoke(
        sessionId: String,
        profile: DomainUserProfile
    ): Result<Unit, ChatError> {
        return chatRepository.sendMyProfileInfoToPeer(sessionId, profile)
    }
}