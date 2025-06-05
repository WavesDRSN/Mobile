package ru.drsn.waves.domain.usecase.crypto

import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.crypto.UserNickname
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject

class GetUserNicknameUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(): Result<UserNickname, CryptoError> {
        return cryptoRepository.getUserNickname()
    }
}