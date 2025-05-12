package ru.drsn.waves.domain.repository

import ru.drsn.waves.domain.model.signaling.SdpData
import ru.drsn.waves.domain.model.signaling.SignalingError
import ru.drsn.waves.domain.model.signaling.SignalingEvent
import ru.drsn.waves.domain.model.utils.Result

interface ISignalingRepository {
    /**
     * Connects to the signaling server.
     * @param username The username for this client.
     * @param host The server host.
     * @param port The server port.
     * @return Result indicating success or SignalingError.
     */
    fun connect(username: String, host: String, port: Int): Result<Unit, SignalingError>

    /**
     * Disconnects from the signaling server.
     * @return Result indicating success or SignalingError.
     */
    suspend fun disconnect(): Result<Unit, SignalingError>

    /**
     * Observes all relevant signaling events from the server.
     * This flow will emit various SignalingEvent types.
     */
    fun observeSignalingEvents(): kotlinx.coroutines.flow.Flow<SignalingEvent>

    /**
     * Sends an SDP message (offer/answer) to a target user.
     * @param sdpData The SDP data to send.
     * @return Result indicating success or SignalingError.
     */
    suspend fun sendSdp(sdpData: SdpData): Result<Unit, SignalingError>

    /**
     * Sends ICE candidates to a target user.
     * @param candidates The list of ICE candidates.
     * @param targetId The ID of the recipient.
     * @return Result indicating success or SignalingError.
     */
    suspend fun sendIceCandidates(candidates: List<gRPC.v1.Signaling.IceCandidate>, targetId: String): Result<Unit, SignalingError>

    /**
     * Relays a "new peer" notification to another user.
     * This is for your custom mechanism where a peer informs another about a third peer.
     * @param receiverId The ID of the user who should receive this notification.
     * @param newPeerId The ID of the new peer being announced.
     * @return Result indicating success or SignalingError.
     */
    suspend fun relayNewPeerNotification(receiverId: String, newPeerId: String): Result<Unit, SignalingError>

    /**
     * Gets the current username established with the signaling server.
     * @return The username, or null if not connected/set.
     */
    fun getCurrentUsername(): String?
}