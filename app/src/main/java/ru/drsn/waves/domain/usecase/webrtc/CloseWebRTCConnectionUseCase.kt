package ru.drsn.waves.domain.usecase.webrtc

import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.webrtc.PeerId
import ru.drsn.waves.domain.model.webrtc.WebRTCError
import ru.drsn.waves.domain.repository.IWebRTCRepository
import javax.inject.Inject


/**
 * UseCase для закрытия WebRTC соединения с конкретным пиром.
 */
class CloseWebRTCConnectionUseCase @Inject constructor(
    private val webRTCRepository: IWebRTCRepository
) {
    suspend operator fun invoke(peerId: PeerId): Result<Unit, WebRTCError> {
        return webRTCRepository.closeConnection(peerId)
    }
}