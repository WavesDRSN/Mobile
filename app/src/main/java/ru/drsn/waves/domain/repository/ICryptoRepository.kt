package ru.drsn.waves.domain.repository

import ru.drsn.waves.domain.model.crypto.AuthToken
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.crypto.InitializationResult
import ru.drsn.waves.domain.model.crypto.JavaPublicKey
import ru.drsn.waves.domain.model.crypto.MnemonicPhrase
import ru.drsn.waves.domain.model.crypto.PublicKey
import ru.drsn.waves.domain.model.crypto.Signature
import ru.drsn.waves.domain.model.profile.DomainUserProfile
import ru.drsn.waves.domain.model.utils.Result

interface ICryptoRepository {

    /**
     * Сохраняет токен аутентификации в безопасное хранилище.
     * @param token Токен для сохранения.
     * @return Result с Unit в случае успеха или CryptoError при ошибке сохранения.
     */
    suspend fun saveAuthToken(token: AuthToken): Result<Unit, CryptoError>

    /**
     * Загружает сохраненный токен аутентификации.
     * @return Result с AuthToken в случае успеха (если токен есть) или CryptoError (если токена нет или ошибка загрузки).
     */
    suspend fun getAuthToken(): Result<AuthToken, CryptoError>

    /**
     * Удаляет сохраненный токен аутентификации.
     * @return Result с Unit в случае успеха или CryptoError при ошибке удаления.
     */
    suspend fun deleteAuthToken(): Result<Unit, CryptoError>

    /**
     * Инициализирует криптографическую подсистему.
     * Проверяет наличие ключей: если есть - загружает, если нет - генерирует новые.
     * Потокобезопасен.
     *
     * @return Result с InitializationResult в случае успеха или CryptoError при ошибке.
     */
    suspend fun initializeKeysIfNeeded(): Result<InitializationResult.KeysLoaded, CryptoError>

    /**
     * Возвращает текущий публичный ключ.
     * Требует предварительной успешной инициализации.
     *
     * @return Result с PublicKey в случае успеха или CryptoError при ошибке (например, не инициализировано).
     */
    suspend fun getPublicKey(): Result<PublicKey, CryptoError>

    /**
     * Возвращает текущий публичный ключ в виде Java PublicKey.
     * Требует предварительной успешной инициализации.
     *
     * @return Result с JavaPublicKey в случае успеха или CryptoError при ошибке.
     */
    suspend fun getJavaPublicKey(): Result<JavaPublicKey, CryptoError>


    /**
     * Подписывает данные текущим приватным ключом.
     * Требует предварительной успешной инициализации.
     *
     * @param data Данные для подписи.
     * @return Result с Signature в случае успеха или CryptoError при ошибке.
     */
    suspend fun signData(data: ByteArray): Result<Signature, CryptoError>

    /**
     * Проверяет подпись данных, используя текущий публичный ключ.
     * Требует предварительной успешной инициализации.
     *
     * @param data Подписанные данные.
     * @param signature Подпись для проверки.
     * @return Result с Boolean (true - подпись верна, false - неверна) или CryptoError при ошибке.
     */
    suspend fun verifySignature(data: ByteArray, signature: Signature): Result<Boolean, CryptoError>

    /**
     * Удаляет сохраненные ключи.
     *
     * @return Result с Unit в случае успеха или CryptoError при ошибке.
     */
    suspend fun deleteKeys(): Result<Unit, CryptoError>

    /**
     * Проверяет, инициализирована ли криптосистема (ключи загружены или сгенерированы).
     */
    suspend fun isInitialized(): Boolean

    suspend fun saveUserNickname(nickname: String): Result<Unit, CryptoError>
    suspend fun getUserNickname(): Result<String, CryptoError>
    suspend fun deleteUserNickname(): Result<Unit, CryptoError>
    suspend fun regenerateKeysFromSeed(mnemonicPhrase: MnemonicPhrase): Result<Unit, CryptoError>
    suspend fun generateAndStoreNewKeys(): Result<InitializationResult.KeysGenerated, CryptoError>
    suspend fun loadUserProfile(): Result<DomainUserProfile, CryptoError>
    suspend fun deleteUserProfile(): Result<Unit, CryptoError>
    suspend fun saveUserProfile(userProfile: DomainUserProfile): Result<Unit, CryptoError>
}
