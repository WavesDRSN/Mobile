package ru.drsn.waves.domain.usecase.authentication

import ru.drsn.waves.domain.model.authentication.AuthError
import ru.drsn.waves.domain.model.crypto.AuthToken
import ru.drsn.waves.domain.repository.IAuthenticationRepository
import ru.drsn.waves.domain.repository.ICryptoRepository
import ru.drsn.waves.domain.model.utils.Result
import javax.inject.Inject

class AuthenticateUseCase @Inject constructor(
    private val authenticationRepository: IAuthenticationRepository,
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(nickname: String): Result<AuthToken, AuthError> {
        // 1. Получить challenge
        val challengeResult = authenticationRepository.getChallenge(nickname)
        val challenge = when(challengeResult) {
            is Result.Success -> challengeResult.value
            is Result.Error -> return challengeResult // Пробрасываем ошибку
        }

        // 2. Подписать challenge
        val signatureResult = cryptoRepository.signData(challenge.challenge.toByteArray())
        val signature = when(signatureResult) {
            is Result.Success -> signatureResult.value
            is Result.Error -> return Result.Error(AuthError.AuthenticationFailed("не удалось подписать челлендж")) // Пробрасываем ошибку
        }

        // 3. Отправить на проверку
        return authenticationRepository.verifyAuthentication(nickname, challenge, signature)
    }
}