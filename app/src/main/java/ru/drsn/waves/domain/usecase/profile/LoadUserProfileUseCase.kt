package ru.drsn.waves.domain.usecase.profile

import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.profile.DomainUserProfile
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject


class LoadUserProfileUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(): Result<DomainUserProfile, CryptoError> {
        return cryptoRepository.loadUserProfile()
    }
}