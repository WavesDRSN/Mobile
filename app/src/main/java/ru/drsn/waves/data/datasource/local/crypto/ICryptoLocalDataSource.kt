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
}