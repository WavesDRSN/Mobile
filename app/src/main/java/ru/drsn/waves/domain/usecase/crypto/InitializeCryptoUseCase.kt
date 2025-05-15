package ru.drsn.waves.domain.usecase.crypto

import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.crypto.InitializationResult
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject

class InitializeCryptoUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    /**
     * Пытается загрузить существующие ключи.
     * Возвращает KeysLoaded в случае успеха, или CryptoError (KeyNotFound, LoadError).
     */
    suspend operator fun invoke(): Result<InitializationResult.KeysLoaded, CryptoError> {
        return cryptoRepository.initializeKeysIfNeeded()
    }
}