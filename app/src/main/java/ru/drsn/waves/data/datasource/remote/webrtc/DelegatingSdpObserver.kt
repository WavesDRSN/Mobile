package ru.drsn.waves.data.datasource.remote.webrtc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import ru.drsn.waves.domain.model.webrtc.PeerId
import timber.log.Timber

internal class DelegatingSdpObserver(
    private val peerId: PeerId,
    private val operationTag: String, // Например, "CreateOffer", "SetLocalDescriptionOffer"
    private val controllerScope: CoroutineScope,
    private val eventEmitter: suspend (WebRTCControllerEvent) -> Unit, // Лямбда для отправки событий
    private val customOnCreateSuccess: ((sdp: SessionDescription?) -> Unit)? = null,
    private val customOnCreateFailure: ((error: String?) -> Unit)? = null,
    private val customOnSetSuccess: (() -> Unit)? = null,
    private val customOnSetFailure: ((error: String?) -> Unit)? = null
) : SdpObserver {

    override fun onCreateSuccess(sdp: SessionDescription?) {
        Timber.tag(WebRTCControllerImpl.TAG).d("[${peerId.value}] $operationTag: onCreateSuccess. Тип SDP: ${sdp?.type}")
        if (customOnCreateSuccess != null) {
            customOnCreateSuccess.invoke(sdp)
        } else {
            // Действие по умолчанию, если specificAction не предоставлен (например, просто логирование)
            if (sdp == null) {
                Timber.tag(WebRTCControllerImpl.TAG).e("[${peerId.value}] $operationTag: onCreateSuccess вернул null SDP.")
                controllerScope.launch {
                    eventEmitter(WebRTCControllerEvent.PeerConnectionError(peerId, "$operationTag onCreateSuccess вернул null SDP"))
                }
            }
        }
    }

    override fun onCreateFailure(error: String?) {
        Timber.tag(WebRTCControllerImpl.TAG).e("[${peerId.value}] $operationTag: onCreateFailure. Ошибка: $error")
        if (customOnCreateFailure != null) {
            customOnCreateFailure.invoke(error)
        } else {
            controllerScope.launch {
                eventEmitter(WebRTCControllerEvent.PeerConnectionError(peerId, "$operationTag onCreateFailure: $error"))
            }
        }
    }

    override fun onSetSuccess() {
        Timber.tag(WebRTCControllerImpl.TAG).d("[${peerId.value}] $operationTag: onSetSuccess.")
        customOnSetSuccess?.invoke()
    }

    override fun onSetFailure(error: String?) {
        Timber.tag(WebRTCControllerImpl.TAG).e("[${peerId.value}] $operationTag: onSetFailure. Ошибка: $error")
        if (customOnSetFailure != null) {
            customOnSetFailure.invoke(error)
        } else {
            controllerScope.launch {
                eventEmitter(WebRTCControllerEvent.PeerConnectionError(peerId, "$operationTag onSetFailure: $error"))
            }
        }
    }
}