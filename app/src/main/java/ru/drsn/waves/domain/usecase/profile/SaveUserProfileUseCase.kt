package ru.drsn.waves.domain.usecase.profile

import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.profile.DomainUserProfile
import ru.drsn.waves.domain.repository.ICryptoRepository // Используем ICryptoRepository для доступа к профилю
import javax.inject.Inject

class SaveUserProfileUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(userProfile: DomainUserProfile): Result<Unit, CryptoError> {
        return cryptoRepository.saveUserProfile(userProfile)
    }
}
