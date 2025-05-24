package ru.drsn.waves.domain.model.onion

import kotlinx.serialization.Serializable
import ru.drsn.waves.domain.model.webrtc.PeerId


@Serializable
data class OnionPayload(
    val nextPeerId: PeerId?,            // кому передать этот слой
    val encryptedMessage: ByteArray    // либо вложенный OnionPayload, либо финальный E2EE-месседж
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OnionPayload

        if (nextPeerId != other.nextPeerId) return false
        if (!encryptedMessage.contentEquals(other.encryptedMessage)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nextPeerId.hashCode()
        result = 31 * result + encryptedMessage.contentHashCode()
        return result
    }
}