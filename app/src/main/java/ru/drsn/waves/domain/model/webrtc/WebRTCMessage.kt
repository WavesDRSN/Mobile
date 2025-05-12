package ru.drsn.waves.domain.model.webrtc

@JvmInline
value class PeerId(val value: String)

// Сообщение для передачи через DataChannel
data class WebRTCMessage(
    val peerId: PeerId,
    val content: String // Или ByteArray, если передаются бинарные данные
)