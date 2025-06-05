package ru.drsn.waves.domain.usecase.signaling

import ru.drsn.waves.domain.model.signaling.SdpData
import ru.drsn.waves.domain.model.signaling.SignalingError
import ru.drsn.waves.domain.repository.ISignalingRepository
import ru.drsn.waves.domain.model.utils.Result
import javax.inject.Inject

class SendSdpUseCase @Inject constructor(
    private val signalingRepository: ISignalingRepository
) {
    suspend operator fun invoke(sdpData: SdpData): Result<Unit, SignalingError> {
        return signalingRepository.sendSdp(sdpData)
    }
}