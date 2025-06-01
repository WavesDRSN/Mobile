package ru.drsn.waves.domain.usecase.call

import ru.drsn.waves.domain.model.webrtc.WebRTCError
import ru.drsn.waves.domain.repository.IWebRTCRepository
import javax.inject.Inject
import ru.drsn.waves.domain.model.utils.Result

class AcceptCallUseCase @Inject constructor(
    private val webRTCRepository: IWebRTCRepository
) {
    suspend operator fun invoke(): Result<Unit, WebRTCError> {
        return webRTCRepository.acceptCall()
    }
}