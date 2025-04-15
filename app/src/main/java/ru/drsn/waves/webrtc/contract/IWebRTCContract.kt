package ru.drsn.waves.webrtc.contract

import gRPC.v1.Signaling.IceCandidate as GrpcIceCandidate // Даем псевдоним во избежание конфликта имен
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

interface IWebRTCManager {
    fun call(target: String)
    fun handleRemoteOffer(sender: String, sdp: String)
    fun handleRemoteAnswer(sender: String, sdp: String)
    fun handleRemoteCandidate(sender: String, candidate: GrpcIceCandidate)
    fun sendMessage(target: String, message: String)
    fun closeConnection(target: String)
    fun closeAllConnections()
    // Listener для событий, идущих "наверх" (в UI/ViewModel)
    var listener: WebRTCListener?
}

interface ISignalingController {
    // Интерфейс для отправки данных через сигналинг
    suspend fun sendSdp(target: String, sessionDescription: SessionDescription)
    suspend fun sendCandidates(target: String, candidates: List<IceCandidate>)
}

// Listener для событий от WebRTCManager к верхнему уровню (UI/ViewModel)
interface WebRTCListener {
    fun onConnectionStateChanged(target: String, state: PeerConnection.IceConnectionState)
    fun onMessageReceived(sender: String, message: String)
    fun onError(target: String?, error: String) // target может быть null для общих ошибок
}