package ru.drsn.waves.domain.usecase.authentication

import ru.drsn.waves.domain.model.authentication.AuthError
import ru.drsn.waves.domain.model.authentication.NicknameReservation
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IAuthenticationRepository
import ru.drsn.waves.domain.repository.ICryptoRepository
import ru.drsn.waves.domain.usecase.crypto.GetPublicKeyUseCase
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authenticationRepository: IAuthenticationRepository,
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(nicknameReservation: NicknameReservation): Result<Unit, AuthError> {
        val key = when(val publicKeyResponse = cryptoRepository.getPublicKey()) {
            is Result.Success -> publicKeyResponse.value
            is Result.Error -> return Result.Error(AuthError.RegistrationFailed(publicKeyResponse.error.toString()))
        }
        return authenticationRepository.register(nicknameReservation.reservationToken, key)
    }
}