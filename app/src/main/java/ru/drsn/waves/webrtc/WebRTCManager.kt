package ru.drsn.waves.webrtc

import android.content.Context
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import ru.drsn.waves.webrtc.utils.DataModel
import ru.drsn.waves.webrtc.utils.DataModelType
import timber.log.Timber

class WebRTCManager(
    private val context: Context,
    private val observer: PeerConnection.Observer,
    var username: String
) {
    private val connections = mutableMapOf<String, PeerConnection>()
    private val peerConnectionFactory: PeerConnectionFactory
    private val iceServer = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
    private val mediaConstraints = MediaConstraints()
    var listener: Listener? = null

    init {
        Timber.d("Initializing WebRTCManager for user: $username")
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()

        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    fun getOrCreateConnection(target: String): PeerConnection {
        return connections.getOrPut(target) {
            Timber.d("Creating new connection for: $target")
            peerConnectionFactory.createPeerConnection(iceServer, observer)!!
        }
    }

    fun sendMessageTo(target: String, message: String, type: DataModelType) {
        val connection = connections[target]
        if (connection != null) {
            Timber.d("Sending message to $target: $message")
            listener?.onTransferDataToOtherPeer(DataModel(target, username, message, type))
        } else {
            Timber.w("No active connection found for $target")
        }
    }

    fun call(target: String) {
        val peerConnection = getOrCreateConnection(target)
        peerConnection.createOffer(object : SdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Timber.d("SDP offer created for $target")
                    peerConnection.setLocalDescription(object : SdpObserver() {
                        override fun onSetSuccess() {
                            sendMessageTo(target, it.description, DataModelType.Offer)
                        }
                    }, it)
                }
            }
        }, mediaConstraints)
    }

    fun answer(target: String) {
        val peerConnection = getOrCreateConnection(target)
        peerConnection.createAnswer(object : SdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Timber.d("SDP answer created for $target")
                    peerConnection.setLocalDescription(object : SdpObserver() {
                        override fun onSetSuccess() {
                            sendMessageTo(target, it.description, DataModelType.Answer)
                        }
                    }, it)
                }
            }
        }, mediaConstraints)
    }

    fun onRemoteSessionReceived(target: String, sessionDescription: SessionDescription) {
        val peerConnection = getOrCreateConnection(target)
        peerConnection.setRemoteDescription(SdpObserver(), sessionDescription)
    }

    fun addIceCandidate(target: String, iceCandidate: IceCandidate?) {
        val peerConnection = connections[target]
        if (peerConnection != null) {
            Timber.d("Adding ICE candidate for $target")
            peerConnection.addIceCandidate(iceCandidate)
        } else {
            Timber.w("No active connection for $target")
        }
    }

    fun closeConnection(target: String) {
        connections[target]?.close()
        connections.remove(target)
        Timber.d("Connection closed for $target")
    }

    interface Listener {
        fun onTransferDataToOtherPeer(data: DataModel)
    }
}
