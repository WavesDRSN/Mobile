package ru.drsn.waves.domain.usecase.crypto

import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.crypto.AuthToken
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject

/**
 * UseCase для получения сохраненного токена аутентификации.
 */
class GetAuthTokenUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(): Result<AuthToken, CryptoError> {
        return cryptoRepository.getAuthToken()
    }
}
