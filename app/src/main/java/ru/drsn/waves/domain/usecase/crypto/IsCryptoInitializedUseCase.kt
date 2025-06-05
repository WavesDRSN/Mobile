package ru.drsn.waves.domain.usecase.crypto

import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject

class IsCryptoInitializedUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(): Result<Boolean, Boolean> {
        return Result.Success(cryptoRepository.isInitialized())
    }
}