package ru.drsn.waves.domain.model.webrtc

import org.webrtc.PeerConnection

sealed class WebRTCEvent {
    data class SessionStateChanged(val peerId: PeerId, val state: PeerConnection.IceConnectionState) : WebRTCEvent() // Более детальное состояние
    data class MessageReceived(val peerId: PeerId, val message: String) : WebRTCEvent()

    data class BinaryMessageReceived(val peerId: PeerId, val message: ByteArray) : WebRTCEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BinaryMessageReceived
            if (peerId != other.peerId) return false
            if (!message.contentEquals(other.message)) return false
            return true
        }
        override fun hashCode(): Int {
            var result = peerId.hashCode()
            result = 31 * result + message.contentHashCode()
            return result
        }
    }

    data class DataChannelOpened(val peerId: PeerId) : WebRTCEvent()
    data class DataChannelClosed(val peerId: PeerId) : WebRTCEvent()
    data class ErrorOccurred(val peerId: PeerId?, val error: String) : WebRTCEvent() // Может быть общая ошибка или для конкретного пира
    // Событие для локально сгенерированного SDP (если нужно передать выше, хотя обычно это внутреннее)
    // data class LocalSdpReady(val peerId: PeerId, val sdp: SdpData) : WebRTCEvent()
    // Событие для локально сгенерированного ICE кандидата (аналогично)
    // data class LocalIceCandidateReady(val peerId: PeerId, val candidate: IceCandidateData) : WebRTCEvent()
}