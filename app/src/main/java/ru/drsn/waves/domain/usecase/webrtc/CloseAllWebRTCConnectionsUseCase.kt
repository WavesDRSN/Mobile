package ru.drsn.waves.domain.usecase.webrtc

import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.webrtc.WebRTCError
import ru.drsn.waves.domain.repository.IWebRTCRepository
import javax.inject.Inject


/**
 * UseCase для закрытия всех активных WebRTC соединений и освобождения ресурсов.
 */
class CloseAllWebRTCConnectionsUseCase @Inject constructor(
    private val webRTCRepository: IWebRTCRepository
) {
    suspend operator fun invoke(): Result<Unit, WebRTCError> {
        return webRTCRepository.closeAllConnections()
    }
}