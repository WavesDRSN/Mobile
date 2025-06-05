package ru.drsn.waves.data.datasource.remote.webrtc

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import ru.drsn.waves.domain.model.webrtc.PeerId

// Низкоуровневые события, генерируемые WebRTCControllerImpl
// Эти события будут обрабатываться WebRTCRepositoryImpl
sealed class WebRTCControllerEvent {
    // Локально сгенерированный SDP (Offer или Answer)
    data class LocalSdpCreated(val peerId: PeerId, val sdp: SessionDescription) : WebRTCControllerEvent()
    // Локально найденный ICE кандидат
    data class LocalIceCandidateFound(val peerId: PeerId, val candidate: IceCandidate) : WebRTCControllerEvent()
    // Собранные ICE кандидаты (когда gathering завершен или по таймауту)
    data class LocalIceCandidatesGathered(val peerId: PeerId, val candidates: List<IceCandidate>) : WebRTCControllerEvent()

    // Состояние соединения изменилось
    data class ConnectionStateChanged(val peerId: PeerId, val newState: PeerConnection.IceConnectionState) : WebRTCControllerEvent()
    // Канал данных открыт
    data class DataChannelOpened(val peerId: PeerId, val dataChannelLabel: String) : WebRTCControllerEvent()
    // Канал данных закрыт
    data class DataChannelClosed(val peerId: PeerId, val dataChannelLabel: String) : WebRTCControllerEvent()
    // Получено сообщение через канал данных
    data class DataChannelMessageReceived(val peerId: PeerId, val dataChannelLabel: String, val message: ByteArray) : WebRTCControllerEvent()
    // Произошла ошибка на уровне PeerConnection
    data class PeerConnectionError(val peerId: PeerId, val errorDescription: String) : WebRTCControllerEvent()
    // Уведомление о том, что удаленный пир добавил DataChannel
    data class RemoteDataChannelAvailable(val peerId: PeerId, val dataChannel: org.webrtc.DataChannel): WebRTCControllerEvent()
}