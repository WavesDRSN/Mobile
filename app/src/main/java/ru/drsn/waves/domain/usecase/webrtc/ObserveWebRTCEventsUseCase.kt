package ru.drsn.waves.domain.usecase.webrtc

import kotlinx.coroutines.flow.Flow
import ru.drsn.waves.domain.model.webrtc.WebRTCEvent
import ru.drsn.waves.domain.repository.IWebRTCRepository
import javax.inject.Inject

/**
 * UseCase для наблюдения за событиями WebRTC.
 * Предоставляет Flow событий от WebRTC репозитория.
 */
class ObserveWebRTCEventsUseCase @Inject constructor(
    private val webRTCRepository: IWebRTCRepository
) {
    operator fun invoke(): Flow<WebRTCEvent> {
        return webRTCRepository.observeWebRTCEvents()
    }
}