package ru.drsn.waves.domain.model.signaling

data class SdpData(
    val type: String,
    val sdp: String,
    val targetId: String, // ID of the recipient
    val senderId: String  // ID of the sender
)