package ru.drsn.waves.domain.usecase.authentication

import ru.drsn.waves.domain.model.authentication.AuthError
import ru.drsn.waves.domain.model.authentication.NicknameReservation
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.IAuthenticationRepository
import javax.inject.Inject

class ReserveNicknameUseCase @Inject constructor(
    private val authenticationRepository: IAuthenticationRepository
) {
    suspend operator fun invoke(nickname: String): Result<NicknameReservation, AuthError> {
        return authenticationRepository.reserveNickname(nickname)
    }
}