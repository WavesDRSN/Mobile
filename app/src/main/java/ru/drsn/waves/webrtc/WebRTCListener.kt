package ru.drsn.waves.webrtc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import ru.drsn.waves.signaling.SignalingService
import timber.log.Timber

class WebRTCListener(
    private val signalingService: SignalingService,
    private val target: String,
    private val dataChannels: MutableMap<String, DataChannel>
) : PeerConnection.Observer {


    // Слушаем, когда можно отправить кандидатов
    private var canSendCandidates = false

    override fun onSignalingChange(newState: PeerConnection.SignalingState) {
        Timber.d("Signaling state changed: $newState")
    }


    override fun onIceCandidate(candidate: IceCandidate) {
        Timber.d("New ICE candidate: ${candidate.sdp}")
        CoroutineScope(Dispatchers.IO).launch {
            signalingService.sendIceCandidates(
                listOf(
                    gRPC.v1.IceCandidate.newBuilder()
                        .setSdpMid(candidate.sdpMid ?: "")
                        .setSdpMLineIndex(candidate.sdpMLineIndex)
                        .setCandidate(candidate.sdp)
                        .build()
                ),
                target
            )
        }
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {
        Timber.d("ICE candidates removed")
    }

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
        Timber.d("ICE connection state changed: $newState")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        Timber.d("ICE connection receiving changed: $p0")
    }

    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
        Timber.d("ICE gathering state changed: $newState")
    }

    override fun onAddStream(stream: MediaStream) {
        Timber.d("Stream added: ${stream.id}")
    }

    override fun onRemoveStream(stream: MediaStream) {
        Timber.d("Stream removed: ${stream.id}")
    }

    override fun onDataChannel(channel: DataChannel) {
        Timber.d("Data channel created: ${channel.label()}")
        dataChannels[target] = channel
    }

    override fun onRenegotiationNeeded() {
        Timber.d("Renegotiation needed")
    }

    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
        Timber.d("Track added: ${receiver.track()!!.id()}")
    }

}
