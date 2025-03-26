package ru.drsn.waves.webrtc

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import ru.drsn.waves.webrtc.utils.DataModel
import ru.drsn.waves.webrtc.utils.DataModelType


class WebRTCManager (
    private val context: Context,
    observer: PeerConnection.Observer,
    val username: String
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private val iceServer = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localStream: MediaStream? = null
    private var mediaConstraints = MediaConstraints()
    private val iceCandidates = mutableListOf<IceCandidate>()

    var listener: Listener? = null

    init {
        initPeerConnectionFactory()
        peerConnectionFactory = createPeerConnectionFactory()
        peerConnection = createPeerConnection(observer)

        // Создаем источник аудио и трек
        localAudioSource = peerConnectionFactory!!.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("local_audio", localAudioSource)

        // Создаем и добавляем локальный медиапоток
        peerConnection?.addTrack(localAudioTrack, listOf("local_stream"))

        // Настройки для получения только аудио (потом можно будет добавить и видео)
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }


    //инициализщация PeerConnectionFactory
    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    // Создание PeerConnectionFactory
    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        val options = PeerConnectionFactory.Options().apply {
            disableEncryption = false // Не отключаем шифрование
            disableNetworkMonitor = false // Используем сетевой монитор
        }
        return PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }

    // Создание PeerConnection
    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection {
        return peerConnectionFactory?.createPeerConnection(iceServer, observer)!!
    }


    // Функция для начала вызова (создание SDP-предложения)
    fun call(target: String) {
        peerConnection?.createOffer(object : SdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                super.onCreateSuccess(p0)
                p0?.let {
                    peerConnection!!.setLocalDescription(object : SdpObserver() {
                        override fun onSetSuccess() {
                            super.onSetSuccess()
                            listener?.onTransferDataToOtherPeer(
                                DataModel(target, username, it.description, DataModelType.Offer)
                            )
                        }
                    }, it)
                }
            }
        }, mediaConstraints)
    }


    // Функция для ответа на вызов (создание SDP-ответа)
    fun answer(target: String) {
        peerConnection?.createAnswer(object : SdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                super.onCreateSuccess(p0)
                p0?.let {
                    peerConnection!!.setLocalDescription(object : SdpObserver() {
                        override fun onSetSuccess() {
                            super.onSetSuccess()
                            // Отправляем ответ удаленному пиру
                            listener?.onTransferDataToOtherPeer(
                                DataModel(target, username, it.description, DataModelType.Answer)
                            )
                        }
                    }, it)
                }
            }
        }, mediaConstraints)
    }


    // Установка удаленного SDP-описания (получено от другого пира)
    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(SdpObserver(), sessionDescription)
    }


    // Добавление ICE-кандидата, полученного от удаленного пира
    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    // Включение или отключение аудио
    fun toggleAudio(shouldBeMuted: Boolean) {
        localAudioTrack?.setEnabled(!shouldBeMuted)
    }

    fun isConnected(): Boolean {
        return peerConnection?.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED
    }


    fun hasIceCandidates(): Boolean {
        return iceCandidates.isNotEmpty()
    }

    fun getConnectionState(): PeerConnection.PeerConnectionState? {
        return peerConnection?.connectionState()
    }


    // Закрытие соединения и освобождение ресурсов
    fun closeConnection() {
        try {
            localAudioTrack?.dispose()
            peerConnection?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    // Интерфейс для передачи данных другому пиру
    interface Listener {
        fun onTransferDataToOtherPeer(data: DataModel)
    }
}