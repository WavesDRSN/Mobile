package ru.drsn.waves.domain.model.onion

import kotlinx.serialization.Serializable

@Serializable
data class OnionPacket(
    val payload: ByteArray, // это сериализованный OnionPayload, зашифрованный через AES ключа relay-узла
    val ephemeralPublicKey: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OnionPacket

        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        return payload.contentHashCode()
    }
}