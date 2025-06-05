package ru.drsn.waves.domain.usecase.signaling

import ru.drsn.waves.domain.model.signaling.SignalingError
import ru.drsn.waves.domain.repository.ISignalingRepository
import ru.drsn.waves.domain.model.utils.Result
import javax.inject.Inject

class DisconnectFromSignalingUseCase @Inject constructor(
    private val signalingRepository: ISignalingRepository
) {
    suspend operator fun invoke(): Result<Unit, SignalingError> {
        return signalingRepository.disconnect()
    }
}