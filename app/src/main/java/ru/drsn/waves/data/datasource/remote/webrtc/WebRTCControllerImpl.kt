package ru.drsn.waves.data.datasource.remote.webrtc

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.webrtc.*
import ru.drsn.waves.domain.model.webrtc.PeerId
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton


@Singleton // WebRTCController должен быть синглтоном, т.к. управляет PeerConnectionFactory
class WebRTCControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : IWebRTCController {

    // Статический компаньон для TAG, чтобы DelegatingSdpObserver мог его использовать
    companion object {
        const val TAG = "WebRTCController"
        const val DATA_CHANNEL_LABEL = "chat" // Стандартная метка для основного канала данных
        const val AUDIO_TRACK_ID = "ARDAMSa0" // Стандартный ID для аудио трека WebRTC
        const val LOCAL_STREAM_ID = "ARDAMSs0" // Стандартный ID для локального медиапотока
    }

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default) // Scope для операций контроллера
    private var peerConnectionFactory: PeerConnectionFactory? = null

    private var localAudioSources = ConcurrentHashMap<PeerId, AudioSource>()
    private var localAudioTracks = ConcurrentHashMap<PeerId, AudioTrack>()

    // Настройки ICE серверов
    private val iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:relay1.expressturn.com:3478") // Пример TURN сервера
            .setUsername("efTXYQK53J3HDFV70T")
            .setPassword("jk38ahrHzaWa2wv8")
            .createIceServer()
    )


    // Хранилища для активных соединений и каналов
    // ConcurrentHashMap для потокобезопасности
    private val peerConnections = ConcurrentHashMap<PeerId, PeerConnection>()
    private val dataChannels = ConcurrentHashMap<PeerId, DataChannel>()
    // Буфер для ICE кандидатов, которые приходят до того, как setRemoteDescription будет вызван
    private val pendingIceCandidates = ConcurrentHashMap<PeerId, MutableList<IceCandidate>>()


    // Flow для отправки событий наверх (в WebRTCRepositoryImpl)
    private val _controllerEvents = MutableSharedFlow<WebRTCControllerEvent>(replay = 0, extraBufferCapacity = 64)

    init {
        initializeFactory()
    }

    override fun initializeFactory(): Boolean {
        if (peerConnectionFactory != null) {
            Timber.tag(TAG).d("PeerConnectionFactory уже инициализирована.")
            return true
        }
        Timber.tag(TAG).d("Инициализация PeerConnectionFactory...")
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val factoryOptions = PeerConnectionFactory.Options()
        // Настройки видеокодеков не обязательны для DataChannel-only, но могут быть нужны для совместимости или будущих расширений
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(null, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(null)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()

        return if (peerConnectionFactory != null) {
            Timber.tag(TAG).i("PeerConnectionFactory успешно создана.")
            true
        } else {
            Timber.tag(TAG).e("Не удалось создать PeerConnectionFactory.")
            false
        }
    }

    override fun observeEvents() = _controllerEvents.asSharedFlow()
    override fun getIceServersConfiguration(): List<PeerConnection.IceServer> = iceServers
    override fun getActivePeerIds(): Set<PeerId> = peerConnections.keys.toSet()

    override fun getPeerConnectionState(peerId: PeerId): PeerConnection.SignalingState? {
        return peerConnections[peerId]?.signalingState()
    }

    private fun getOrCreatePeerConnection(peerId: PeerId, mediaConstraints : MediaConstraints): PeerConnection? {
        if (peerConnectionFactory == null) {
            Timber.tag(TAG).e("PeerConnectionFactory не инициализирована. Невозможно создать PeerConnection.")
            controllerScope.launch { _controllerEvents.emit(WebRTCControllerEvent.PeerConnectionError(peerId, "PeerConnectionFactory not initialized")) }
            return null
        }
        // Проверяем, существует ли уже соединение
        peerConnections[peerId]?.let { return it }

        // Создаем новое соединение
        Timber.tag(TAG).d("[${peerId.value}] Создание нового PeerConnection.")
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            // Временный список для сбора кандидатов этого соединения
            private val collectedCandidates = mutableListOf<IceCandidate>()
            private var iceGatheringJob: Job? = null


            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                Timber.tag(TAG).d("[${peerId.value}] Signaling state changed: $newState")
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Timber.tag(TAG).i("[${peerId.value}] ICE connection state changed: $newState")
                controllerScope.launch {
                    _controllerEvents.emit(WebRTCControllerEvent.ConnectionStateChanged(peerId, newState))
                }
                if (newState == PeerConnection.IceConnectionState.FAILED ||
                    newState == PeerConnection.IceConnectionState.DISCONNECTED ||
                    newState == PeerConnection.IceConnectionState.CLOSED) {
                    // Можно добавить логику очистки или переподключения здесь или выше
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Timber.tag(TAG).d("[${peerId.value}] ICE connection receiving change: $receiving")
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                Timber.tag(TAG).d("[${peerId.value}] ICE gathering state changed: $newState")
                if (newState == PeerConnection.IceGatheringState.GATHERING) {
                    // Начинаем таймер для сбора кандидатов, если Trickle ICE не используется агрессивно
                    iceGatheringJob?.cancel()
                    iceGatheringJob = controllerScope.launch {
                        delay(1000) // Таймаут для сбора кандидатов (пример)
                        if (isActive && collectedCandidates.isNotEmpty()) {
                            _controllerEvents.emit(WebRTCControllerEvent.LocalIceCandidatesGathered(peerId, ArrayList(collectedCandidates)))
                            collectedCandidates.clear() // Очищаем после отправки "пачки"
                        }
                    }
                } else if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                    iceGatheringJob?.cancel()
                    if (collectedCandidates.isNotEmpty()) {
                        controllerScope.launch {
                            _controllerEvents.emit(WebRTCControllerEvent.LocalIceCandidatesGathered(peerId, ArrayList(collectedCandidates)))
                            collectedCandidates.clear()
                        }
                    }
                }
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Timber.tag(TAG).d("[${peerId.value}] Локальный ICE кандидат найден: ${it.sdpMid}")
                    // Для Trickle ICE отправляем кандидата немедленно
                    controllerScope.launch {
                        _controllerEvents.emit(WebRTCControllerEvent.LocalIceCandidateFound(peerId, it))
                    }
                    // Также добавляем в список для возможной "пакетной" отправки
                    collectedCandidates.add(it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                Timber.tag(TAG).d("[${peerId.value}] ICE кандидаты удалены: ${candidates?.size}")
            }

            override fun onAddStream(stream: MediaStream?) { /* Не используется для DataChannel-only */ }
            override fun onRemoveStream(stream: MediaStream?) { /* Не используется для DataChannel-only */ }

            override fun onDataChannel(dataChannel: org.webrtc.DataChannel) {
                Timber.tag(TAG).i("[${peerId.value}] Удаленный DataChannel '${dataChannel.label()}' получен. Состояние: ${dataChannel.state()}")
                // Передаем событие, что удаленный канал данных доступен
                controllerScope.launch {
                    _controllerEvents.emit(WebRTCControllerEvent.RemoteDataChannelAvailable(peerId, dataChannel))
                }
                // Регистрация наблюдателя для удаленного канала будет произведена в репозитории или после этого события
            }

            override fun onRenegotiationNeeded() {
                Timber.tag(TAG).d("[${peerId.value}] Требуется пересогласование (renegotiation).")
                // В простом чате может не требоваться, но для более сложных сценариев здесь нужно создавать новый оффер
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                super.onAddTrack(receiver, mediaStreams)
                receiver?.track()?.let { track ->
                    if (track.kind() == MediaStreamTrack.AUDIO_TRACK_KIND) {
                        Timber.tag(TAG).d("[${peerId.value}] Remote AudioTrack added.")
                        // Для аудио трека обычно не нужно ничего делать с UI, он просто начинает воспроизводиться.
                        // Но мы можем уведомить об этом.
                    }
                }
            }
        }

        val connection = peerConnectionFactory!!.createPeerConnection(rtcConfig, observer)
        return if (connection != null) {
            if (mediaConstraints.mandatory.any { it.key == "OfferToReceiveAudio" && it.value == "true" } ||
                mediaConstraints.mandatory.any {it.key == "OfferToReceiveVideo" && it.value == "false"}) { // Проверка, что это не видеозвонок

                createAndAddLocalAudioTrack(connection, peerId, mediaConstraints) // mediaConstraints могут содержать настройки AEC, NS, AGC
            }

            peerConnections[peerId] = connection
            Timber.tag(TAG).i("[${peerId.value}] PeerConnection успешно создан.")
            connection
        } else {
            Timber.tag(TAG).e("[${peerId.value}] Не удалось создать PeerConnection (фабрика вернула null).")
            controllerScope.launch { _controllerEvents.emit(WebRTCControllerEvent.PeerConnectionError(peerId, "Factory returned null for PeerConnection"))}
            null
        }
    }

    // Внутренний метод для регистрации наблюдателя и сохранения DataChannel
    // Вызывается как для локально созданных, так и для удаленно полученных каналов
    internal fun registerDataChannelObserver(peerId: PeerId, dataChannel: org.webrtc.DataChannel) {
        Timber.tag(TAG).d("[${peerId.value}] Регистрация наблюдателя для DataChannel '${dataChannel.label()}'. Текущее состояние: ${dataChannel.state()}")
        dataChannels[peerId] = dataChannel // Сохраняем или перезаписываем канал для данного пира

        val dcObserver = object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {
                Timber.tag(TAG).d("[${peerId.value}] DataChannel '${dataChannel.label()}' buffered amount changed: $previousAmount")
            }

            override fun onStateChange() {
                val state = dataChannel.state()
                Timber.tag(TAG).i("[${peerId.value}] DataChannel '${dataChannel.label()}' state changed: $state")
                controllerScope.launch {
                    when (state) {
                        DataChannel.State.OPEN -> _controllerEvents.emit(WebRTCControllerEvent.DataChannelOpened(peerId, dataChannel.label()))
                        DataChannel.State.CLOSED -> _controllerEvents.emit(WebRTCControllerEvent.DataChannelClosed(peerId, dataChannel.label()))
                        else -> { /* Состояния CONNECTING, CLOSING можно логировать, если нужно */ }
                    }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                Timber.tag(TAG).d("[${peerId.value}] Сообщение получено через DataChannel '${dataChannel.label()}'. Размер: ${data.size}")
                controllerScope.launch {
                    _controllerEvents.emit(WebRTCControllerEvent.DataChannelMessageReceived(peerId, dataChannel.label(), data))
                }
            }
        }
        dataChannel.registerObserver(dcObserver)

        // Если канал уже открыт при регистрации (например, удаленный канал), эмитим событие
        if (dataChannel.state() == DataChannel.State.OPEN) {
            controllerScope.launch {
                _controllerEvents.emit(WebRTCControllerEvent.DataChannelOpened(peerId, dataChannel.label()))
            }
        }
    }


    override suspend fun createOffer(peerId: PeerId, sdpConstraints: MediaConstraints) {
        withContext(Dispatchers.Default) { // Операции WebRTC могут быть CPU-bound
            val peerConnection = getOrCreatePeerConnection(peerId, sdpConstraints) ?: return@withContext
            Timber.tag(TAG).i("[${peerId.value}] Создание Offer...")

            // Создаем DataChannel ДО создания Offer, если это инициирующая сторона
            if (dataChannels[peerId] == null) {
                Timber.tag(TAG).d("[${peerId.value}] Локальный DataChannel еще не создан, создаем...")
                val init = DataChannel.Init().apply { ordered = true } // Настройки для канала чата
                val dataChannel = peerConnection.createDataChannel(DATA_CHANNEL_LABEL, init)
                if (dataChannel != null) {
                    registerDataChannelObserver(peerId, dataChannel) // Регистрируем наблюдателя
                } else {
                    Timber.tag(TAG).e("[${peerId.value}] Не удалось создать локальный DataChannel.")
                    _controllerEvents.emit(WebRTCControllerEvent.PeerConnectionError(peerId, "Failed to create local DataChannel"))
                    return@withContext
                }
            }

            val sdpObserver = DelegatingSdpObserver(
                peerId = peerId,
                operationTag = "CreateOffer",
                controllerScope = controllerScope,
                eventEmitter = { event -> _controllerEvents.emit(event) },
                customOnCreateSuccess = { sdp ->
                    if (sdp == null) { // Дополнительная проверка на null внутри лямбды
                        Timber.tag(TAG).e("[${peerId.value}] CreateOffer: onCreateSuccess вернул null SDP.")
                        controllerScope.launch { _controllerEvents.emit(WebRTCControllerEvent.PeerConnectionError(peerId, "onCreateSuccess for Offer returned null SDP")) }
                        return@DelegatingSdpObserver
                    }
                    Timber.tag(TAG).d("[${peerId.value}] Offer успешно создан (через DelegatingSdpObserver).")
                    controllerScope.launch(Dispatchers.Default) {
                        peerConnection.setLocalDescription(
                            DelegatingSdpObserver(
                                peerId = peerId,
                                operationTag = "SetLocalDescriptionOffer",
                                controllerScope = controllerScope,
                                eventEmitter = { event -> _controllerEvents.emit(event) },
                                customOnSetSuccess = {
                                    Timber.tag(TAG).d("[${peerId.value}] Локальное описание (Offer) успешно установлено (через DelegatingSdpObserver).")
                                    controllerScope.launch { _controllerEvents.emit(WebRTCControllerEvent.LocalSdpCreated(peerId, sdp)) }
                                }
                            ), sdp
                        )
                    }
                }
            )
            peerConnection.createOffer(sdpObserver, sdpConstraints)
        }
    }

    override suspend fun handleRemoteOfferAndCreateAnswer(peerId: PeerId, remoteOffer: SessionDescription, sdpConstraints: MediaConstraints) {
        withContext(Dispatchers.Default) {
            val peerConnection = getOrCreatePeerConnection(peerId, sdpConstraints) ?: return@withContext
            Timber.tag(TAG).i("[${peerId.value}] Обработка удаленного Offer и создание Answer...")

            val setRemoteOfferObserver = DelegatingSdpObserver(
                peerId = peerId,
                operationTag = "SetRemoteDescriptionOffer",
                controllerScope = controllerScope,
                eventEmitter = { event -> _controllerEvents.emit(event) },
                customOnSetSuccess = {
                    Timber.tag(TAG).d("[${peerId.value}] Удаленное описание (Offer) успешно установлено.")
                    applyPendingIceCandidates(peerId, peerConnection) // Применяем ожидающие ICE кандидаты

                    // Создаем Answer
                    val createAnswerObserver = DelegatingSdpObserver(
                        peerId = peerId,
                        operationTag = "CreateAnswer",
                        controllerScope = controllerScope,
                        eventEmitter = { event -> _controllerEvents.emit(event) },
                        customOnCreateSuccess = { sdpAnswer ->
                            if (sdpAnswer == null) {
                                Timber.tag(TAG).e("[${peerId.value}] CreateAnswer: onCreateSuccess вернул null SDP.")
                                controllerScope.launch { _controllerEvents.emit(WebRTCControllerEvent.PeerConnectionError(peerId, "onCreateSuccess for Answer returned null SDP"))}
                                return@DelegatingSdpObserver
                            }
                            Timber.tag(TAG).d("[${peerId.value}] Answer успешно создан.")
                            controllerScope.launch(Dispatchers.Default) {
                                peerConnection.setLocalDescription(
                                    DelegatingSdpObserver(
                                        peerId = peerId,
                                        operationTag = "SetLocalDescriptionAnswer",
                                        controllerScope = controllerScope,
                                        eventEmitter = { event -> _controllerEvents.emit(event) },
                                        customOnSetSuccess = {
                                            Timber.tag(TAG).d("[${peerId.value}] Локальное описание (Answer) успешно установлено.")
                                            controllerScope.launch { _controllerEvents.emit(WebRTCControllerEvent.LocalSdpCreated(peerId, sdpAnswer)) }
                                        }
                                    ), sdpAnswer
                                )
                            }
                        }
                    )
                    peerConnection.createAnswer(createAnswerObserver, sdpConstraints)
                }
            )
            peerConnection.setRemoteDescription(setRemoteOfferObserver, remoteOffer)
        }
    }

    override suspend fun handleRemoteAnswer(peerId: PeerId, remoteAnswer: SessionDescription) {
        withContext(Dispatchers.Default) {
            val peerConnection = peerConnections[peerId]
            if (peerConnection == null) {
                Timber.tag(TAG).e("[${peerId.value}] Получен Answer, но PeerConnection не найден.")
                controllerScope.launch { _controllerEvents.emit(WebRTCControllerEvent.PeerConnectionError(peerId, "Received Answer, but PeerConnection not found"))}
                return@withContext
            }
            Timber.tag(TAG).i("[${peerId.value}] Обработка удаленного Answer...")

            val setRemoteAnswerObserver = DelegatingSdpObserver(
                peerId = peerId,
                operationTag = "SetRemoteDescriptionAnswer",
                controllerScope = controllerScope,
                eventEmitter = { event -> _controllerEvents.emit(event) },
                customOnSetSuccess = {
                    Timber.tag(TAG).d("[${peerId.value}] Удаленное описание (Answer) успешно установлено.")
                    applyPendingIceCandidates(peerId, peerConnection) // Применяем ожидающие ICE кандидаты
                    // Соединение должно начать устанавливаться (ICE checks)
                }
            )
            peerConnection.setRemoteDescription(setRemoteAnswerObserver, remoteAnswer)
        }
    }

    override suspend fun addRemoteIceCandidate(peerId: PeerId, iceCandidate: IceCandidate) {
        withContext(Dispatchers.Default) { // addIceCandidate может быть блокирующей
            val peerConnection = peerConnections[peerId]
            if (peerConnection == null || peerConnection.remoteDescription == null) {
                // Если PeerConnection еще не создан или remoteDescription не установлен,
                // буферизируем кандидата.
                Timber.tag(TAG).w("[${peerId.value}] PeerConnection не готов для ICE кандидата (RemoteDescription: ${peerConnection?.remoteDescription}). Буферизация.")
                pendingIceCandidates.computeIfAbsent(peerId) { mutableListOf() }.add(iceCandidate)
                return@withContext
            }
            Timber.tag(TAG).d("[${peerId.value}] Добавление удаленного ICE кандидата: ${iceCandidate.sdpMid}")
            try {
                peerConnection.addIceCandidate(iceCandidate)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "[${peerId.value}] Ошибка при добавлении удаленного ICE кандидата.")
                _controllerEvents.emit(WebRTCControllerEvent.PeerConnectionError(peerId, "Failed to add remote ICE candidate: ${e.message}"))
            }
        }
    }

    private fun applyPendingIceCandidates(peerId: PeerId, peerConnection: PeerConnection) {
        pendingIceCandidates.remove(peerId)?.let { candidates ->
            Timber.tag(TAG).d("[${peerId.value}] Применение ${candidates.size} ожидающих ICE кандидатов.")
            candidates.forEach { candidate ->
                try {
                    peerConnection.addIceCandidate(candidate)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "[${peerId.value}] Ошибка при применении ожидающего ICE кандидата.")
                    // Можно эмитить ошибку или просто логировать
                }
            }
        }
    }

    override fun setLocalAudioEnabled(peerId: PeerId, enabled: Boolean): Boolean {
        val audioTrack = localAudioTracks[peerId]
        return if (audioTrack != null) {
            audioTrack.setEnabled(enabled)
            Timber.tag(TAG).d("[${peerId.value}] Local audio track setEnabled: $enabled")
            true
        } else {
            Timber.tag(TAG).w("[${peerId.value}] Local audio track not found for setEnabled.")
            false
        }
    }

    // Вспомогательный метод для создания и добавления локального аудио трека
    private fun createAndAddLocalAudioTrack(pc: PeerConnection, peerId: PeerId, audioProcessingConstraints: MediaConstraints) {
        // Создаем AudioSource с учетом ограничений (AEC, NS, AGC)
        val audioSource = peerConnectionFactory?.createAudioSource(audioProcessingConstraints)
        localAudioSources[peerId] = audioSource as AudioSource

        val audioTrack = peerConnectionFactory?.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        audioTrack?.setEnabled(true) // Включен по умолчанию
        localAudioTracks[peerId] = audioTrack as AudioTrack

        // Добавляем трек в PeerConnection. Для этого обычно создается локальный MediaStream.
        val localStream = peerConnectionFactory!!.createLocalMediaStream(LOCAL_STREAM_ID)
        localStream.addTrack(audioTrack)
        pc.addStream(localStream) // Устаревший метод, но всё ещё работает
        // или pc.addTrack(audioTrack, listOf(LOCAL_STREAM_ID)) // Для Unified Plan

        Timber.tag(TAG).d("[${peerId.value}] Local AudioTrack created and added.")
    }

    override suspend fun sendMessage(peerId: PeerId, dataChannelLabel: String, message: ByteArray): Boolean {
        return withContext(Dispatchers.IO) { // Отправка данных - IO операция
            val dataChannel = dataChannels[peerId] // Предполагаем, что метка всегда одна для простоты
            if (dataChannel == null) {
                Timber.tag(TAG).e("[${peerId.value}] Невозможно отправить сообщение: DataChannel не найден.")
                _controllerEvents.emit(WebRTCControllerEvent.PeerConnectionError(peerId, "SendMessage failed: DataChannel not found for label $dataChannelLabel"))
                return@withContext false
            }
            if (dataChannel.state() != DataChannel.State.OPEN) {
                Timber.tag(TAG).e("[${peerId.value}] Невозможно отправить сообщение: DataChannel не открыт (Состояние: ${dataChannel.state()}).")
                _controllerEvents.emit(WebRTCControllerEvent.PeerConnectionError(peerId, "SendMessage failed: DataChannel not OPEN (State: ${dataChannel.state()})"))
                return@withContext false
            }

            val byteBuffer = ByteBuffer.wrap(message)
            val buffer = DataChannel.Buffer(byteBuffer, false) // false для текстовых или бинарных данных, если они уже в байтах

            if (dataChannel.send(buffer)) {
                Timber.tag(TAG).d("[${peerId.value}] Сообщение успешно отправлено через DataChannel '$dataChannelLabel'.")
                true
            } else {
                Timber.tag(TAG).w("[${peerId.value}] Не удалось отправить сообщение через DataChannel '$dataChannelLabel' (send вернул false).")
                _controllerEvents.emit(WebRTCControllerEvent.PeerConnectionError(peerId, "SendMessage failed: dataChannel.send returned false"))
                false
            }
        }
    }

    override suspend fun closeConnection(peerId: PeerId) {
        withContext(Dispatchers.Default) {
            Timber.tag(TAG).w("[${peerId.value}] Закрытие соединения...")
            dataChannels.remove(peerId)?.let {
                it.unregisterObserver()
                it.close()
                Timber.tag(TAG).d("[${peerId.value}] DataChannel закрыт.")
            }
            peerConnections.remove(peerId)?.let {
                it.close() // Закрывает PeerConnection и связанные ресурсы
                Timber.tag(TAG).d("[${peerId.value}] PeerConnection закрыт.")
            }
            localAudioTracks.remove(peerId)?.dispose()
            localAudioSources.remove(peerId)?.dispose()
            pendingIceCandidates.remove(peerId)
            Timber.tag(TAG).w("[${peerId.value}] Ресурсы соединения освобождены.")
            // Эмитим событие, что соединение было закрыто (если не было эмитировано ранее через onIceConnectionChange)
            _controllerEvents.emit(WebRTCControllerEvent.ConnectionStateChanged(peerId, PeerConnection.IceConnectionState.CLOSED))
        }
    }

    override suspend fun closeAllConnectionsAndReleaseFactory() {
        withContext(Dispatchers.Default) {
            Timber.tag(TAG).w("Закрытие всех соединений и освобождение PeerConnectionFactory...")
            val peerIds = peerConnections.keys.toList() // Копируем ключи для безопасной итерации
            peerIds.forEach { peerId ->
                closeConnection(peerId) // Используем уже существующий метод закрытия
            }
            peerConnections.clear()
            localAudioTracks.clear()
            localAudioSources.clear()
            dataChannels.clear()
            pendingIceCandidates.clear()

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
            Timber.tag(TAG).w("PeerConnectionFactory освобождена.")
            controllerScope.cancel("Все соединения закрыты, контроллер завершает работу.")
        }
    }
}
