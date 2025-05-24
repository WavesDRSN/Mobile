package ru.drsn.waves.data.datasource.local.crypto

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import ru.drsn.waves.data.repository.CryptoRepositoryImpl
import ru.drsn.waves.domain.model.crypto.AuthToken
import ru.drsn.waves.domain.model.crypto.CryptoError
import ru.drsn.waves.domain.model.utils.Result
import timber.log.Timber
import java.security.AlgorithmParameterGenerator
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import javax.crypto.spec.DHParameterSpec

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

    suspend fun getDhKeyFactory(): KeyFactory
    suspend fun getDhPublicKeyEncoded(): String?
    suspend fun generateDhKeyPair(): KeyPair
    suspend fun loadDhKeyPair(): KeyPair?

    suspend fun loadStaticPrivateKey(): ByteArray
    suspend fun computeAndStoreSharedSecret(peerId: String, otherPublicKeyEncoded: String): ByteArray
    suspend fun generateEphemeralKeyPair(): AsymmetricCipherKeyPair?
}