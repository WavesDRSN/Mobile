package ru.drsn.waves.domain.usecase.crypto

import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject

/**
 * UseCase для удаления сохраненного ключа авторизации (например, при выходе).
 */
class DeleteAuthKeysUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(): Result<Unit, CryptoError> {
        return cryptoRepository.deleteKeys()
    }
}
