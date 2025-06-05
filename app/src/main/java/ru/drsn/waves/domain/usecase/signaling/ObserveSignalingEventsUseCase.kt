package ru.drsn.waves.domain.usecase.signaling

import kotlinx.coroutines.flow.Flow
import ru.drsn.waves.domain.model.signaling.SignalingEvent
import ru.drsn.waves.domain.repository.ISignalingRepository
import javax.inject.Inject

class ObserveSignalingEventsUseCase @Inject constructor(
    private val signalingRepository: ISignalingRepository
) {
    operator fun invoke(): Flow<SignalingEvent> {
        return signalingRepository.observeSignalingEvents()
    }
}