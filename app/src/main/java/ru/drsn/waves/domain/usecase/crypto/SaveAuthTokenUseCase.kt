package ru.drsn.waves.domain.usecase.crypto

import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.crypto.AuthToken
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject

/**
 * UseCase для сохранения полученного токена аутентификации.
 */
class SaveAuthTokenUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository // Используем CryptoRepository для сохранения
) {
    suspend operator fun invoke(token: AuthToken): Result<Unit, CryptoError> {
        return cryptoRepository.saveAuthToken(token)
    }
}