package ru.drsn.waves.domain.usecase.crypto

import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.crypto.Signature
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject

class VerifySignatureUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(data: ByteArray, signature: Signature): Result<Boolean, CryptoError> {
        return cryptoRepository.verifySignature(data, signature)
    }
}