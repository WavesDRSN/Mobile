package ru.drsn.waves.domain.usecase.crypto

import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.crypto.InitializationResult
import ru.drsn.waves.domain.model.crypto.MnemonicPhrase
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject


/**
 * UseCase для генерации новой пары ключей и мнемонической фразы.
 * Сохраняет ключи и возвращает мнемонику для отображения пользователю.
 */
class GenerateNewKeysUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(): Result<InitializationResult.KeysGenerated, CryptoError> {
        return cryptoRepository.generateAndStoreNewKeys()
    }
}
