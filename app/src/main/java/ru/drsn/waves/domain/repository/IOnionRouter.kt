package ru.drsn.waves.domain.repository

interface IOnionRouter {
    suspend fun handleIncomingOnionData(
        fromPeerId: String,
        encryptedData: ByteArray,
        otherPublicKeyEncoded : String): String
}