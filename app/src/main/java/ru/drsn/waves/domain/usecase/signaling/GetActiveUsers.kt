package ru.drsn.waves.domain.usecase.signaling

import ru.drsn.waves.domain.model.signaling.SignalingError
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ISignalingRepository
import javax.inject.Inject

class GetActiveUsers @Inject constructor(
    private val signalingRepository: ISignalingRepository
) {
    suspend operator fun invoke(): Result<List<String>, SignalingError> {
        return Result.Success(signalingRepository.getCurrentOnlineUsers())
    }
}