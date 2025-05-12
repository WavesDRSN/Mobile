package ru.drsn.waves.domain.model.webrtc

sealed class WebRTCSessionState {
    data object Idle : WebRTCSessionState()
    data object Connecting : WebRTCSessionState()
    data object Connected : WebRTCSessionState()
    data object Disconnected : WebRTCSessionState()
    data class Failed(val reason: String) : WebRTCSessionState()
}