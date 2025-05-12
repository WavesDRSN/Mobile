package ru.drsn.waves.domain.usecase.webrtc


import kotlinx.coroutines.flow.Flow
import ru.drsn.waves.domain.model.webrtc.* // Все WebRTC доменные модели
import ru.drsn.waves.domain.repository.IWebRTCRepository
import ru.drsn.waves.domain.model.utils.Result
import javax.inject.Inject

/**
 * UseCase для инициализации WebRTC стека.
 * Вызывает соответствующий метод в репозитории.
 */
class InitializeWebRTCUseCase @Inject constructor(
    private val webRTCRepository: IWebRTCRepository
) {
    suspend operator fun invoke(): Result<Unit, WebRTCError> {
        return webRTCRepository.initialize()
    }
}