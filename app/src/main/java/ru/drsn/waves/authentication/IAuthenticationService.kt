package ru.drsn.waves.authentication

import com.google.protobuf.ByteString
import ru.drsn.waves.crypto.CryptoService

interface IAuthenticationService {
    fun openConnection(serverAddress: String, serverPort: Int)
    suspend fun reserveNickname(nickname: String)
    suspend fun register(publicKey: ByteString)
    suspend fun authenticate(nickname: String)
}