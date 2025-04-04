// WebRTCManager.kt
package ru.drsn.waves.webrtc

import android.content.Context
import gRPC.v1.IceCandidate as GrpcIceCandidate
import kotlinx.coroutines.*
import org.webrtc.*
import ru.drsn.waves.signaling.SignalingService // Используем интерфейс
import ru.drsn.waves.webrtc.contract.ISignalingController
import ru.drsn.waves.webrtc.contract.IWebRTCManager
import ru.drsn.waves.webrtc.contract.WebRTCListener
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

class WebRTCManager(
    private val context: Context // Зависимость от интерфейса сигналинга
) : IWebRTCManager, ISignalingController { // Реализует оба интерфейса

    lateinit var signalingService: SignalingService
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default) // Scope для управления корутинами менеджера
    private val peerConnectionFactory: PeerConnectionFactory
    private val iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        // !!! ВАЖНО: Добавь сюда свои TURN серверы для надежности !!!
        PeerConnection.IceServer.builder("turn:relay1.expressturn.com:3478")
           .setUsername("efTXYQK53J3HDFV70T")
           .setPassword("jk38ahrHzaWa2wv8")
           .createIceServer()
    )
    private val defaultPeerConnectionConstraints = MediaConstraints() // Обычно пустые для DataChannel
    private val defaultSdpConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false")) // Мы не ждем аудио
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false")) // Мы не ждем видео
        // Для DataChannel OfferToReceiveAudio/Video не нужны, но могут помочь совместимости
    }

    // Используем ConcurrentHashMap для потокобезопасности, т.к. обращение может быть из разных потоков
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val dataChannels = ConcurrentHashMap<String, DataChannel>()
    private val dataChannelHandlers = ConcurrentHashMap<String, DataChannelHandler>()

    override var listener: WebRTCListener? = null

    init {
        Timber.d("Initializing WebRTCManager...")
        // 1. Инициализация Factory
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true) // Для отладки
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/") // Пример field trial
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        // 2. Создание Factory
        val options = PeerConnectionFactory.Options().apply {
            // disableEncryption = true // Не рекомендуется, только для отладки!
            // disableNetworkMonitor = true // Может понадобиться в специфичных случаях
        }
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(null, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(null)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            // Не нужны для DataChannel-only, но пусть будут:
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()
        Timber.d("PeerConnectionFactory created.")
    }

    // --- Реализация IWebRTCManager ---

    override fun call(target: String) {
        managerScope.launch {
            Timber.i("[$target] Initiating call...")
            val peerConnection = getOrCreatePeerConnection(target)

            // Создаем DataChannel ДО создания Offer
            createAndRegisterDataChannel(target, peerConnection)

            // Создаем Offer
            peerConnection.createOffer(object : SdpObserver() {
                override fun onCreateSuccess(p0: SessionDescription?) {
                    if (p0 == null) {
                        Timber.e("[$target] Failed to create offer: SDP is null")
                        listener?.onError(target, "Failed to create offer: SDP is null")
                        return
                    }
                    Timber.d("[$target] Offer created successfully.")
                    // Устанавливаем локальное описание
                    peerConnection.setLocalDescription(object : SdpObserver() {
                        override fun onSetSuccess() {
                            Timber.d("[$target] Local description (offer) set successfully.")
                            // Отправляем Offer через сигналинг
                            managerScope.launch { sendSdp(target, p0) }
                        }
                        override fun onSetFailure(p0: String?) {
                            Timber.e("[$target] Failed to set local description (offer): $p0")
                            listener?.onError(target, "Failed to set local offer: $p0")
                        }
                    }, p0)
                }
                override fun onCreateFailure(p0: String?) {
                    Timber.e("[$target] Failed to create offer: $p0")
                    listener?.onError(target, "Failed to create offer: $p0")
                }
            }, defaultSdpConstraints) // Используем SDP constraints
        }
    }

    override fun handleRemoteOffer(sender: String, sdp: String) {
        managerScope.launch {
            Timber.i("[$sender] Handling remote offer...")
            val peerConnection = getOrCreatePeerConnection(sender)
            val remoteDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)

            // Устанавливаем удаленное описание
            peerConnection.setRemoteDescription(object : SdpObserver() {
                override fun onSetSuccess() {
                    Timber.d("[$sender] Remote description (offer) set successfully.")
                    // Создаем Answer
                    peerConnection.createAnswer(object : SdpObserver() {
                        override fun onCreateSuccess(p0: SessionDescription?) {
                            if (p0 == null) {
                                Timber.e("[$sender] Failed to create answer: SDP is null")
                                listener?.onError(sender, "Failed to create answer: SDP is null")
                                return
                            }
                            Timber.d("[$sender] Answer created successfully.")
                            // Устанавливаем локальное описание (answer)
                            peerConnection.setLocalDescription(object : SdpObserver() {
                                override fun onSetSuccess() {
                                    Timber.d("[$sender] Local description (answer) set successfully.")
                                    // Отправляем Answer через сигналинг
                                    managerScope.launch { sendSdp(sender, p0) }
                                }
                                override fun onSetFailure(p0: String?) {
                                    Timber.e("[$sender] Failed to set local description (answer): $p0")
                                    listener?.onError(sender, "Failed to set local answer: $p0")
                                }
                            }, p0)
                        }
                        override fun onCreateFailure(p0: String?) {
                            Timber.e("[$sender] Failed to create answer: $p0")
                            listener?.onError(sender, "Failed to create answer: $p0")
                        }
                    }, defaultSdpConstraints) // Используем те же constraints для Answer
                }
                override fun onSetFailure(p0: String?) {
                    Timber.e("[$sender] Failed to set remote description (offer): $p0")
                    listener?.onError(sender, "Failed to set remote offer: $p0")
                }
            }, remoteDescription)
        }
    }

    override fun handleRemoteAnswer(sender: String, sdp: String) {
        managerScope.launch {
            Timber.i("[$sender] Handling remote answer...")
            val peerConnection = peerConnections[sender]
            if (peerConnection == null) {
                Timber.e("[$sender] Received answer, but no PeerConnection found.")
                listener?.onError(sender, "Received answer, but no connection exists.")
                return@launch
            }
            val remoteDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            // Устанавливаем удаленное описание (answer)
            peerConnection.setRemoteDescription(object : SdpObserver() {
                override fun onSetSuccess() {
                    Timber.d("[$sender] Remote description (answer) set successfully.")
                    // Соединение должно начать устанавливаться (ICE checks)
                }
                override fun onSetFailure(p0: String?) {
                    Timber.e("[$sender] Failed to set remote description (answer): $p0")
                    listener?.onError(sender, "Failed to set remote answer: $p0")
                }
            }, remoteDescription)
        }
    }

    override fun handleRemoteCandidate(sender: String, candidate: GrpcIceCandidate) {
        managerScope.launch { // Можно выполнять на IO, если addIceCandidate блокирует
            val peerConnection = peerConnections[sender]
            if (peerConnection == null) {
                Timber.w("[$sender] Received ICE candidate, but no PeerConnection found (might be late).")
                // Можно буферизировать кандидатов, если соединение еще не создано
                return@launch
            }
            val iceCandidate = IceCandidate(
                candidate.sdpMid,
                candidate.sdpMLineIndex,
                candidate.candidate
            )
            Timber.d("[$sender] Adding remote ICE candidate: ${iceCandidate.sdpMid}")
            peerConnection.addIceCandidate(iceCandidate)
        }
    }

    override fun sendMessage(target: String, message: String) {
        managerScope.launch(Dispatchers.IO) { // Отправка данных - IO операция
            val dataChannel = dataChannels[target]
            if (dataChannel == null) {
                Timber.e("[$target] Cannot send message: DataChannel not found.")
                listener?.onError(target, "Cannot send message: DataChannel not found.")
                return@launch
            }
            if (dataChannel.state() != DataChannel.State.OPEN) {
                Timber.e("[$target] Cannot send message: DataChannel is not open (State: ${dataChannel.state()}).")
                listener?.onError(target, "Cannot send message: DataChannel is not open.")
                return@launch
            }

            val byteBuffer = ByteBuffer.wrap(message.toByteArray())
            val buffer = DataChannel.Buffer(byteBuffer, false) // false для текстовых данных

            if (dataChannel.send(buffer)) {
                Timber.d("[$target] Message sent successfully.")
            } else {
                Timber.w("[$target] Failed to send message (send returned false).")
                listener?.onError(target, "Failed to send message (send buffer error).")
            }
        }
    }

    override fun closeConnection(target: String) {
        managerScope.launch {
            Timber.w("[$target] Closing connection...")
            dataChannels[target]?.let {
                it.unregisterObserver()
                it.close()
                Timber.d("[$target] DataChannel closed.")
            }
            peerConnections[target]?.let {
                it.close()
                Timber.d("[$target] PeerConnection closed.")
            }
            // Удаляем из карт
            dataChannels.remove(target)
            dataChannelHandlers.remove(target)
            peerConnections.remove(target)
            Timber.w("[$target] Connection resources released.")
        }
    }

    override fun closeAllConnections() {
        Timber.w("Closing all connections...")
        // Создаем копию ключей, чтобы избежать ConcurrentModificationException
        val targets = peerConnections.keys.toList()
        targets.forEach { closeConnection(it) }
        // Можно также остановить Factory, если менеджер уничтожается
        // peerConnectionFactory.dispose()
        // PeerConnectionFactory.shutdownInternalTracer()
        managerScope.cancel() // Отменяем все корутины менеджера
        Timber.w("All connections closed and manager scope cancelled.")
    }

    // --- Реализация ISignalingController ---

    override suspend fun sendSdp(target: String, sessionDescription: SessionDescription) {
        Timber.d("[$target] Sending SDP (${sessionDescription.type})...")
        try {
            signalingService.sendSDP(
                type = sessionDescription.type.toString().lowercase(), // "offer" или "answer"
                sdp = sessionDescription.description,
                target = target
            )
            Timber.i("[$target] SDP (${sessionDescription.type}) sent via SignalingService.")
        } catch (e: Exception) {
            Timber.e(e, "[$target] Failed to send SDP (${sessionDescription.type})")
            listener?.onError(target, "Failed to send SDP: ${e.message}")
        }
    }

    override suspend fun sendCandidates(target: String, candidates: List<IceCandidate>) {
        if (candidates.isEmpty()) return // Не отправляем пустой список

        Timber.d("[$target] Sending ${candidates.size} ICE candidate(s)...")
        try {
            // Конвертируем в gRPC формат
            val grpcCandidates = candidates.map { ice ->
                GrpcIceCandidate.newBuilder()
                    .setSdpMid(ice.sdpMid ?: "")
                    .setSdpMLineIndex(ice.sdpMLineIndex)
                    .setCandidate(ice.sdp)
                    .build()
            }
            Timber.d(grpcCandidates.toString())
            signalingService.sendIceCandidates(grpcCandidates, target)
            Timber.i("[$target] ${grpcCandidates.size} ICE candidate(s) sent via SignalingService.")
        } catch (e: Exception) {
            Timber.e(e, "[$target] Failed to send ICE candidates")
            listener?.onError(target, "Failed to send ICE candidates: ${e.message}")
        }
    }

    // --- Приватные хелперы ---

    private fun getOrCreatePeerConnection(target: String): PeerConnection {
        // synchronized нужен, если этот метод может вызываться конкурентно до добавления в map
        return peerConnections[target] ?: synchronized(this) {
            peerConnections[target] ?: run { // Double-checked locking
                Timber.d("[$target] Creating new PeerConnection.")
                val observer = PeerConnectionHandler(
                    target = target,
                    signalingController = this, // WebRTCManager сам реализует ISignalingController
                    webRTCListener = listener,
                    onDataChannelAvailable = { dataChannel -> // Лямбда для обработки входящего канала
                        Timber.d("[$target] Handling incoming DataChannel from PeerConnectionHandler.")
                        registerDataChannel(target, dataChannel)
                    }
                )
                val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                    // iceTransportPolicy = PeerConnection.IceTransportsType.RELAY // Только через TURN (для тестов)
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN // Стандарт де-факто
                    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                    // keyType = PeerConnection.KeyType.ECDSA // Алгоритм DTLS
                }
                val connection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
                if (connection == null) {
                    Timber.e("[$target] Failed to create PeerConnection (factory returned null).")
                    throw RuntimeException("PeerConnectionFactory returned null for target $target")
                } else {
                    Timber.i("[$target] PeerConnection created successfully.")
                    peerConnections[target] = connection // Сохраняем
                    connection
                }
            }
        }
    }

    // Создает локальный DataChannel
    private fun createAndRegisterDataChannel(target: String, peerConnection: PeerConnection): DataChannel? {
        Timber.d("[$target] Creating local DataChannel 'chat'.")
        val init = DataChannel.Init().apply {
            ordered = true // Для чата важен порядок сообщений
            maxRetransmits = -1 // По умолчанию для надежной доставки
            negotiated = false // Мы не согласовывали ID заранее
            id = -1 // Пусть WebRTC сам назначит ID
        }
        val dataChannel = peerConnection.createDataChannel("chat", init)
        if (dataChannel == null) {
            Timber.e("[$target] Failed to create local DataChannel (createDataChannel returned null).")
            listener?.onError(target, "Failed to create local DataChannel.")
            return null
        } else {
            Timber.i("[$target] Local DataChannel 'chat' created. State: ${dataChannel.state()}")
            registerDataChannel(target, dataChannel)
            return dataChannel
        }
    }

    // Регистрирует обработчик для любого (локального или удаленного) DataChannel
    private fun registerDataChannel(target: String, dataChannel: DataChannel) {
        Timber.d("[$target] Registering DataChannelHandler for label '${dataChannel.label()}'.")
        val handler = DataChannelHandler(target, listener, dataChannel)
        dataChannel.registerObserver(handler)
        dataChannels[target] = dataChannel // Сохраняем канал
        dataChannelHandlers[target] = handler // Сохраняем его обработчик (если нужно)
        // Проверим состояние, если канал пришел от удаленного пира, он может быть уже OPEN
        Timber.d("[$target] DataChannel state after registration: ${dataChannel.state()}")
        if (dataChannel.state() == DataChannel.State.OPEN) {
            // Можно уведомить листенер, что канал готов
        }
    }
}