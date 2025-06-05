package ru.drsn.waves.domain.model.signaling

data class IceCandidateData(
    val candidateInfo: gRPC.v1.Signaling.IceCandidate, // Using gRPC model directly for now
    val targetId: String,
    val senderId: String
)