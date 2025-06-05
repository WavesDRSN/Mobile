package ru.drsn.waves.data.datasource.local.crypto

import ru.drsn.waves.domain.model.crypto.AuthToken
import java.security.KeyPair

interface ICryptoLocalDataSource {
    suspend fun keyPairExists(): Boolean
    suspend fun loadKeyPair(): KeyPair?
    suspend fun storeKeyPair(keyPair: KeyPair): Boolean
    suspend fun deleteKeyPair(): Boolean

    suspend fun saveAuthToken(token: AuthToken): Boolean
    suspend fun loadAuthToken(): AuthToken?
    suspend fun deleteAuthToken(): Boolean

    suspend fun saveUserNickname(nickname: String): Boolean
    suspend fun loadUserNickname(): String?
    suspend fun deleteUserNickname(): Boolean


    // --- Методы для ключа шифрования чатов (CEK) ---
    suspend fun saveEncryptedChatKey(encryptedChatKeyB64: String): Boolean
    suspend fun loadEncryptedChatKey(): String?
    suspend fun deleteEncryptedChatKey(): Boolean

    suspend fun saveProfileDisplayName(name: String): Boolean
    suspend fun loadProfileDisplayName(): String?
    suspend fun saveProfileStatusMessage(status: String): Boolean
    suspend fun loadProfileStatusMessage(): String?
    suspend fun saveProfileAvatarUri(uri: String): Boolean
    suspend fun loadProfileAvatarUri(): String?
    suspend fun clearUserProfileData(): Boolean // Для удаления всех данных профиля

    // --- Новые общие методы для шифрования/дешифрования данных ключом из Keystore ---
    /**
     * Шифрует предоставленные данные с использованием указанного ключа-обертки из Android Keystore.
     * @param dataToWrap Байты, которые нужно зашифровать.
     * @param keystoreWrappingKeyAlias Алиас ключа-обертки в Android Keystore.
     * @return Зашифрованные данные (включая IV) или null в случае ошибки.
     */
    suspend fun wrapDataWithKeystoreKey(
        dataToWrap: ByteArray,
        keystoreWrappingKeyAlias: String
    ): ByteArray?

    /**
     * Дешифрует предоставленные данные с использованием указанного ключа-обертки из Android Keystore.
     * @param wrappedDataWithIv Зашифрованные данные (включая IV), которые нужно дешифровать.
     * @param keystoreWrappingKeyAlias Алиас ключа-обертки в Android Keystore.
     * @return Расшифрованные данные или null в случае ошибки.
     */
    suspend fun unwrapDataWithKeystoreKey(
        wrappedDataWithIv: ByteArray,
        keystoreWrappingKeyAlias: String
    ): ByteArray?

    suspend fun loadProfileLastEditTimestamp(): Long?
    suspend fun saveProfileLastEditTimestamp(timestamp: Long): Boolean
}