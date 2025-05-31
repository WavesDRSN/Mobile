package ru.drsn.waves.domain.model.webrtc

@JvmInline
value class PeerId(val value: String)

data class WebRTCMessage(
    val peerId: PeerId,
    val content: String = "",
    val contentBytes: ByteArray? = null
    ){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WebRTCMessage
        if (peerId != other.peerId) return false
        if (content != other.content) return false
        if (contentBytes != null) {
            if (other.contentBytes == null) return false
            if (!contentBytes.contentEquals(other.contentBytes)) return false
        } else if (other.contentBytes != null) return false
        return true
    }
    override fun hashCode(): Int {
        var result = peerId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (contentBytes?.contentHashCode() ?: 0)
        return result
    }
}