package ru.drsn.waves.webrtc

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import ru.drsn.waves.signaling.SignalingService
import ru.drsn.waves.webrtc.utils.DataModel
import ru.drsn.waves.webrtc.utils.DataModelType
import timber.log.Timber
import java.nio.ByteBuffer

class WebRTCManager(
    private val context: Context
) {
    lateinit var signalingService: SignalingService
    private val connections = mutableMapOf<String, PeerConnection>()
    private val dataChannels = mutableMapOf<String, DataChannel>()
    private val observers = mutableMapOf<String, WebRTCListener>()


    private val peerConnectionFactory: PeerConnectionFactory
    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
    private val mediaConstraints = MediaConstraints()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var listener: Listener? = null

    init {
        Timber.d("Initializing WebRTCManager")
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
         // Создаём нового слушателя


        return connections.getOrPut(target) {
            val observer = WebRTCListener(signalingService, target, dataChannels)
            observers.put(target, observer)
            Timber.d("Creating new connection for: $target")
            peerConnectionFactory.createPeerConnection(iceServer, observer)!!
        }
    }

    fun getObserver(target: String): WebRTCListener {
        return observers[target]!!
    }

    private fun createDataChannel(peerConnection: PeerConnection, target: String) {
        val config = DataChannel.Init()
        val dataChannel = peerConnection.createDataChannel("chat", config)

        // Устанавливаем обработчик для входящих сообщений
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onMessage(buffer: DataChannel.Buffer?) {
                val message = String(buffer?.data?.array() ?: byteArrayOf())
                Timber.d("Received message from $target: $message")
                // Здесь можно обработать полученное сообщение
                // Например, отправить его в UI или сохранить
            }

            override fun onBufferedAmountChange(amount: Long) {
                // Можно отслеживать количество буферизированных данных
            }

            override fun onStateChange() {
                // Можно отслеживать изменение состояния канала
            }
        })
    }

    // Метод для отправки текстового сообщения через DataChannel
    fun sendMessage(target: String, username: String, message: String) {
        val dataChannel = dataChannels[target]
        val byteBuffer = ByteBuffer.wrap(message.toByteArray())
        val buffer = DataChannel.Buffer(byteBuffer, false)

        dataChannel?.send(buffer)
    }

    fun call(target: String) {
        val peerConnection = getOrCreateConnection(target)
        peerConnection.createOffer(object : SdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Timber.d("SDP offer created for $target")
                    peerConnection.setLocalDescription(object : SdpObserver() {
                        override fun onSetSuccess() {
                            serviceScope.launch {
                                signalingService.sendSDP("offer", it.description, target)
                                createDataChannel(peerConnection, target)
                            }  // Теперь отправляем SDP
                        }

                        override fun onSetFailure(error: String?) {
                            Timber.e("Failed to set local SDP: $error")
                        }
                    }, it)
                }
            }

            override fun onCreateFailure(error: String?) {
                Timber.e("SDP offer creation failed: $error")
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
                            serviceScope.launch {
                                signalingService.sendSDP("answer", it.description, target)  // Теперь отправляем SDP
                                createDataChannel(peerConnection, target)
                            }
                        }

                        override fun onSetFailure(error: String?) {
                            Timber.e("Failed to set local SDP: $error")
                        }
                    }, it)
                }
            }

            override fun onCreateFailure(error: String?) {
                Timber.e("SDP answer creation failed: $error")
            }
        }, mediaConstraints)
    }

    fun onRemoteSessionReceived(target: String, sessionDescription: SessionDescription) {
        val peerConnection = getOrCreateConnection(target)
        peerConnection.setRemoteDescription(SdpObserver(), sessionDescription)
    }

    fun addIceCandidate(target: String, iceCandidate: IceCandidate?) {
        val peerConnection = getOrCreateConnection(target)
        Timber.d("Adding ICE candidate for $target")
        peerConnection.addIceCandidate(iceCandidate)

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
