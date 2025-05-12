package ru.drsn.waves.domain.usecase.webrtc

import ru.drsn.waves.domain.model.webrtc.PeerId
import ru.drsn.waves.domain.model.webrtc.WebRTCSessionState
import ru.drsn.waves.domain.repository.IWebRTCRepository
import javax.inject.Inject


/**
 * UseCase для получения текущего состояния WebRTC сессии с конкретным пиром.
 */
class GetWebRTCSessionStateUseCase @Inject constructor(
    private val webRTCRepository: IWebRTCRepository
) {
    // Этот метод может быть suspend или нет, в зависимости от реализации в IWebRTCRepository.
    // Если getSessionState в репозитории suspend, то и здесь должен быть suspend.
    operator fun invoke(peerId: PeerId): WebRTCSessionState? {
        return webRTCRepository.getSessionState(peerId)
    }
}