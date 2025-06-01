package ru.drsn.waves.domain.usecase.call

import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.webrtc.WebRTCError
import ru.drsn.waves.domain.repository.IWebRTCRepository
import javax.inject.Inject

class StartCallUseCase @Inject constructor(
    private val webRTCRepository: IWebRTCRepository
) {
    suspend operator fun invoke(remoteUserId: String): Result<Unit, WebRTCError> {
        return webRTCRepository.startCall(remoteUserId)
    }
}
