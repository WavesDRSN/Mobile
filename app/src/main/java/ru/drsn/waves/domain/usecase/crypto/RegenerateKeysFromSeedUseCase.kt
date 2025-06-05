package ru.drsn.waves.domain.usecase.crypto

import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.crypto.MnemonicPhrase
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ICryptoRepository
import javax.inject.Inject

/**
 * UseCase для регенерации ключей из предоставленной пользователем сид-фразы.
 * Сохраняет регенерированные ключи.
 */
class RegenerateKeysFromSeedUseCase @Inject constructor(
    private val cryptoRepository: ICryptoRepository
) {
    suspend operator fun invoke(mnemonicPhrase: MnemonicPhrase): Result<Unit, CryptoError> {
        return cryptoRepository.regenerateKeysFromSeed(mnemonicPhrase)
    }
}
