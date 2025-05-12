package ru.drsn.waves.domain.usecase.webrtc

import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.webrtc.WebRTCError
import ru.drsn.waves.domain.model.webrtc.WebRTCMessage
import ru.drsn.waves.domain.repository.IWebRTCRepository
import javax.inject.Inject


/**
 * UseCase для отправки сообщения через WebRTC DataChannel.
 */
class SendMessageWebRTCUseCase @Inject constructor(
    private val webRTCRepository: IWebRTCRepository
) {
    suspend operator fun invoke(message: WebRTCMessage): Result<Unit, WebRTCError> {
        return webRTCRepository.sendMessage(message)
    }
}