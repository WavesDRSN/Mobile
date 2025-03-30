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
import timber.log.Timber

class WebRTCManager (
    private val context: Context,
    observer: PeerConnection.Observer,
    var username: String
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
        Timber.d("Инициализация WebRTCManager для пользователя: $username")
        initPeerConnectionFactory()
        peerConnectionFactory = createPeerConnectionFactory()
        peerConnection = createPeerConnection(observer)

        localAudioSource = peerConnectionFactory!!.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("local_audio", localAudioSource)
        peerConnection?.addTrack(localAudioTrack, listOf("local_stream"))

        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    // Инициализация PeerConnectionFactory
    private fun initPeerConnectionFactory() {
        Timber.d("Инициализация PeerConnectionFactory")
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    // Создание PeerConnectionFactory
    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        Timber.d("Создание PeerConnectionFactory")
        val options = PeerConnectionFactory.Options().apply {
            disableEncryption = false
            disableNetworkMonitor = false
        }
        return PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }

    // Создание PeerConnection
    private fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection {
        Timber.d("Создание PeerConnection")
        return peerConnectionFactory?.createPeerConnection(iceServer, observer)!!
    }

    // Создание SDP-предложения (начало вызова)
    fun call(target: String) {
        Timber.d("Создание SDP-предложения для пользователя: $target")
        peerConnection?.createOffer(object : SdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                p0?.let {
                    Timber.d("SDP-предложение успешно создано")
                    peerConnection!!.setLocalDescription(object : SdpObserver() {
                        override fun onSetSuccess() {
                            Timber.d("Локальное описание установлено. Отправка оффера пользователю: $target")
                            listener?.onTransferDataToOtherPeer(
                                DataModel(target, username, it.description, DataModelType.Offer)
                            )
                        }
                    }, it)
                }
            }
        }, mediaConstraints)
    }

    // Создание SDP-ответа (принятие вызова)
    fun answer(target: String) {
        Timber.d("Создание SDP-ответа для пользователя: $target")
        peerConnection?.createAnswer(object : SdpObserver() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                p0?.let {
                    Timber.d("SDP-ответ успешно создан")
                    peerConnection!!.setLocalDescription(object : SdpObserver() {
                        override fun onSetSuccess() {
                            Timber.d("Локальное описание установлено. Отправка ответа пользователю: $target")
                            listener?.onTransferDataToOtherPeer(
                                DataModel(target, username, it.description, DataModelType.Answer)
                            )
                        }
                    }, it)
                }
            }
        }, mediaConstraints)
    }

    // Установка удаленного SDP-описания
    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        Timber.d("Получено удаленное SDP-описание: ${sessionDescription.type}")
        peerConnection?.setRemoteDescription(SdpObserver(), sessionDescription)
    }

    // Добавление ICE-кандидата
    fun addIceCandidate(iceCandidate: IceCandidate?) {
        Timber.d("Добавление ICE-кандидата: ${iceCandidate?.sdpMid}")
        peerConnection?.addIceCandidate(iceCandidate)
    }

    // Включение/отключение микрофона
    fun toggleAudio(shouldBeMuted: Boolean) {
        Timber.d("Изменение состояния микрофона. Отключен: $shouldBeMuted")
        localAudioTrack?.setEnabled(!shouldBeMuted)
    }

    // Проверка соединения
    fun isConnected(): Boolean {
        val connected = peerConnection?.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED
        Timber.d("Проверка соединения: $connected")
        return connected
    }

    // Проверка наличия ICE-кандидатов
    fun hasIceCandidates(): Boolean {
        val hasCandidates = iceCandidates.isNotEmpty()
        Timber.d("Проверка ICE-кандидатов: $hasCandidates")
        return hasCandidates
    }

    // Получение состояния соединения
    fun getConnectionState(): PeerConnection.PeerConnectionState? {
        val state = peerConnection?.connectionState()
        Timber.d("Текущее состояние соединения: $state")
        return state
    }

    // Закрытие соединения
    fun closeConnection() {
        Timber.d("Закрытие соединения и освобождение ресурсов")
        try {
            localAudioTrack?.dispose()
            peerConnection?.close()
        } catch (e: Exception) {
            Timber.e(e, "Ошибка при закрытии соединения")
        }
    }

    // Интерфейс для передачи данных другому пиру
    interface Listener {
        fun onTransferDataToOtherPeer(data: DataModel)
    }
}
