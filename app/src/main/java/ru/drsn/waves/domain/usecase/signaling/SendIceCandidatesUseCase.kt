package ru.drsn.waves.domain.usecase.signaling

import ru.drsn.waves.domain.model.signaling.SignalingError
import ru.drsn.waves.domain.repository.ISignalingRepository
import ru.drsn.waves.domain.model.utils.Result
import javax.inject.Inject

class SendIceCandidatesUseCase @Inject constructor(
    private val signalingRepository: ISignalingRepository
) {
    suspend operator fun invoke(candidates: List<gRPC.v1.Signaling.IceCandidate>, targetId: String): Result<Unit, SignalingError> {
        return signalingRepository.sendIceCandidates(candidates, targetId)
    }
}