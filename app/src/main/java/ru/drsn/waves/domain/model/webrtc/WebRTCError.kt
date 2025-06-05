package ru.drsn.waves.domain.model.webrtc

sealed class WebRTCError {
    data class ConnectionSetupFailed(val peerId: PeerId, val message: String, val cause: Throwable? = null) : WebRTCError()
    data class MessageSendFailed(val peerId: PeerId, val message: String, val cause: Throwable? = null) : WebRTCError()
    data class SessionNotFound(val peerId: PeerId) : WebRTCError()
    data class OperationFailed(val peerId: PeerId?, val message: String, val cause: Throwable? = null) : WebRTCError()
    data class InitializationFailed(val message: String, val cause: Throwable? = null) : WebRTCError()
    data class Unknown(val peerId: PeerId?, val message: String, val cause: Throwable? = null) : WebRTCError()
}