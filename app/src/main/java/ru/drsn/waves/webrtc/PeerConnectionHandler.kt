package ru.drsn.waves.webrtc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.*
import ru.drsn.waves.webrtc.contract.ISignalingController
import ru.drsn.waves.webrtc.contract.WebRTCListener
import timber.log.Timber

// Обработчик событий одного PeerConnection
class PeerConnectionHandler(
    private val target: String,
    private val signalingController: ISignalingController, // Для отправки кандидатов
    private val webRTCListener: WebRTCListener?, // Для уведомления об ошибках/состоянии
    private val onDataChannelAvailable: (DataChannel) -> Unit // Callback для передачи DataChannel менеджеру
) : PeerConnection.Observer {

    private val handlerScope = CoroutineScope(Dispatchers.Default) // Используем Default для CPU-bound задач WebRTC

    override fun onIceCandidate(candidate: IceCandidate?) {
        if (candidate != null) {
            Timber.d("[$target] New local ICE candidate: ${candidate.sdp}")
            handlerScope.launch(Dispatchers.IO) { // IO для сетевой операции сигналинга
                try {
                    // Отправляем кандидата немедленно (Trickle ICE)
                    signalingController.sendCandidates(target, listOf(candidate))
                    Timber.d("[$target] Sent ICE candidate.")
                } catch (e: Exception) {
                    Timber.e(e, "[$target] Failed to send ICE candidate")
                    webRTCListener?.onError(target, "Failed to send ICE candidate: ${e.message}")
                }
            }
        }
    }

    override fun onDataChannel(dataChannel: DataChannel) {
        Timber.d("[$target] Remote data channel created: Label=${dataChannel.label()}, State=${dataChannel.state()}")
        onDataChannelAvailable(dataChannel) // Передаем канал выше для регистрации обработчика
    }

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
        Timber.i("[$target] ICE connection state changed: $newState")
        webRTCListener?.onConnectionStateChanged(target, newState)
        // Здесь можно добавить логику обработки FAILED, DISCONNECTED, CLOSED
        if (newState == PeerConnection.IceConnectionState.FAILED) {
            webRTCListener?.onError(target, "ICE connection failed.")
            // Можно попробовать ICE restart или закрыть соединение
        }
    }

    override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
        Timber.d("[$target] Signaling state changed: $newState")
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        Timber.d("[$target] ICE connection receiving change: $receiving")
    }

    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
        Timber.d("[$target] ICE gathering state changed: $newState")
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        Timber.d("[$target] ICE candidates removed: ${candidates?.size}")
    }

    override fun onAddStream(stream: MediaStream?) {
        Timber.d("[$target] Stream added: ${stream?.id}")
        // Не используется для DataChannel-only, но оставляем для полноты
    }

    override fun onRemoveStream(stream: MediaStream?) {
        Timber.d("[$target] Stream removed: ${stream?.id}")
    }

    override fun onRenegotiationNeeded() {
        Timber.d("[$target] Renegotiation needed")
        // В простом чате может не требовать обработки, но стоит знать о событии
    }

    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        Timber.d("[$target] Track added: ${receiver?.track()?.id()}")
        // Не используется для DataChannel-only
    }
}