package ru.drsn.waves.domain.model.signaling

sealed class SignalingError {
    data class ConnectionFailed(val message: String?, val cause: Throwable? = null) : SignalingError()
    data class DisconnectedError(val message: String?, val cause: Throwable? = null) : SignalingError()
    data class MessageSendFailed(val message: String?, val cause: Throwable? = null) : SignalingError()
    data class OperationFailed(val message: String?, val cause: Throwable? = null) : SignalingError()
    data class NotConnected(val message: String = "Not connected to signaling server") : SignalingError()
    data class Unknown(val message: String?, val cause: Throwable? = null) : SignalingError()
}