package ru.drsn.waves.domain.model.signaling

import gRPC.v1.Signaling.User

typealias SignalingUser = User

sealed class SignalingEvent {
    // User list updates
    data class UserListUpdated(val users: List<SignalingUser>) : SignalingEvent()
    data class NewUserJoinedList(val user: SignalingUser) : SignalingEvent() // Specific event for a new user appearing

    // SDP messages
    data class SdpOfferReceived(val sdp: String, val senderId: String) : SignalingEvent()
    data class SdpAnswerReceived(val sdp: String, val senderId: String) : SignalingEvent()
    data class NewPeerNotificationReceived(val newPeerId: String, val senderId: String): SignalingEvent() // For your custom "new_peer" SDP

    // ICE candidates
    data class IceCandidatesReceived(val candidates: List<gRPC.v1.Signaling.IceCandidate>, val senderId: String) : SignalingEvent()

    // Connection status
    data object Connected : SignalingEvent()
    data object Disconnected : SignalingEvent()
    data class ConnectionErrorEvent(val error: SignalingError) : SignalingEvent()

    data class CallRejected(val from: String) : SignalingEvent()
    data class CallEnded(val from: String) : SignalingEvent()
}