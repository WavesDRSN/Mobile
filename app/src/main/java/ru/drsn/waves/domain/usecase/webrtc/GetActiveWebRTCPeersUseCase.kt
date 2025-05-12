package ru.drsn.waves.domain.usecase.webrtc

import ru.drsn.waves.domain.model.webrtc.PeerId
import ru.drsn.waves.domain.repository.IWebRTCRepository
import javax.inject.Inject


/**
 * UseCase для получения множества идентификаторов активных пиров (с кем установлено или устанавливается соединение).
 */
class GetActiveWebRTCPeersUseCase @Inject constructor(
    private val webRTCRepository: IWebRTCRepository
) {
    operator fun invoke(): Set<PeerId> {
        return webRTCRepository.getActivePeers()
    }
}