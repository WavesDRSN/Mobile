package ru.drsn.waves.domain.model.call

data class CallSession(
    val id: String,
    val initiatorId: String,
    val receiverId: String,
    val state: CallState,
    val direction: CallDirection,
    val isMuted: Boolean = false,
    val startTime: Long? = null,
    val endTime: Long? = null
)