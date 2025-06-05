package ru.drsn.waves.domain.usecase.profile

import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject


class DeleteUserProfileUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(): Result<Unit, CryptoError> {
        return cryptoRepository.deleteUserProfile()
    }
}