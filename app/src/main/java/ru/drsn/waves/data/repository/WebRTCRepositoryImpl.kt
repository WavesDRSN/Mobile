package ru.drsn.waves.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import ru.drsn.waves.data.datasource.remote.webrtc.IWebRTCController
import ru.drsn.waves.data.datasource.remote.webrtc.WebRTCControllerEvent
import ru.drsn.waves.data.datasource.remote.webrtc.WebRTCControllerImpl
import ru.drsn.waves.domain.model.signaling.SignalingEvent
import ru.drsn.waves.domain.model.signaling.SdpData as SignalingSdpData // Переименовываем для ясности
import ru.drsn.waves.domain.model.signaling.IceCandidateData as SignalingIceCandidateData // Переименовываем
import ru.drsn.waves.domain.model.webrtc.*
import ru.drsn.waves.domain.repository.ISignalingRepository
import ru.drsn.waves.domain.repository.IWebRTCRepository
import ru.drsn.waves.domain.model.utils.Result
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Репозиторий обычно синглтон
class WebRTCRepositoryImpl @Inject constructor(
    private val webRTCController: IWebRTCController, // Контроллер для низкоуровневых операций WebRTC
    private val signalingRepository: ISignalingRepository // Репозиторий для отправки/получения сигнальных сообщений
) : IWebRTCRepository {

    private companion object {
        const val TAG = "WebRTCRepository"
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _webRTCEvents = MutableSharedFlow<WebRTCEvent>(replay = 0, extraBufferCapacity = 64)

    // Ограничения для SDP по умолчанию (для DataChannel-only)
    private val defaultSdpConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    // Мьютекс для синхронизации доступа к состоянию инициализации, если необходимо
    private val initializationMutex = Mutex()
    private var isInitialized = false
    private val callSet: MutableSet<String> = mutableSetOf()

    private val dataChannelStates = ConcurrentHashMap<PeerId, Boolean>()


    init {
        // Подписываемся на события от WebRTCController
        webRTCController.observeEvents()
            .onEach { controllerEvent -> processControllerEvent(controllerEvent) }
            .launchIn(repositoryScope)

        // Подписываемся на события от SignalingRepository
        signalingRepository.observeSignalingEvents()
            .onEach { signalingEvent -> processSignalingEvent(signalingEvent) }
            .launchIn(repositoryScope)
    }

    override suspend fun initialize(): Result<Unit, WebRTCError> {
        initializationMutex.withLock {
            if (isInitialized) return Result.Success(Unit)
            val success = webRTCController.initializeFactory() // Предполагаем, что это не suspend
            return if (success) {
                Timber.d("WebRTCRepo initialized")
                isInitialized = true
                Result.Success(Unit)
            } else {
                Result.Error(WebRTCError.InitializationFailed("Failed to initialize WebRTCController factory", null))
            }
        }
    }

    // Обработка событий от WebRTCController
    private suspend fun processControllerEvent(event: WebRTCControllerEvent) {
        Timber.tag(TAG).d("Получено событие от WebRTCController: $event")
        when (event) {
            is WebRTCControllerEvent.LocalSdpCreated -> {
                // Локально создан Offer или Answer, нужно отправить его через сигналинг
                val signalingSdp = SignalingSdpData(
                    type = event.sdp.type.canonicalForm(), // "offer" или "answer"
                    sdp = event.sdp.description,
                    targetId = event.peerId.value, // Отправляем тому же пиру, для которого создали
                    senderId = signalingRepository.getCurrentUsername() ?: "unknown_sender" // Текущий пользователь
                )
                val result = signalingRepository.sendSdp(signalingSdp)
                if (result is Result.Error) {
                    _webRTCEvents.emit(WebRTCEvent.ErrorOccurred(event.peerId, "Failed to send SDP: ${result.error}"))
                }
            }
            is WebRTCControllerEvent.LocalIceCandidateFound -> {
                // Найден локальный ICE кандидат, отправляем его (Trickle ICE)
                val iceData = SignalingIceCandidateData(
                    candidateInfo = org.webrtc.IceCandidate(event.candidate.sdpMid, event.candidate.sdpMLineIndex, event.candidate.sdp).toGrpc(), // Пример конвертации
                    targetId = event.peerId.value,
                    senderId = signalingRepository.getCurrentUsername() ?: "unknown_sender"
                )
                // Отправляем одиночный кандидат
                val result = signalingRepository.sendIceCandidates(listOf(iceData.candidateInfo), iceData.targetId)
                if (result is Result.Error) {
                    _webRTCEvents.emit(WebRTCEvent.ErrorOccurred(event.peerId, "Failed to send ICE candidate: ${result.error}"))
                }
            }
            is WebRTCControllerEvent.LocalIceCandidatesGathered -> {
                // Собрана "пачка" кандидатов (если не используется Trickle ICE или как дополнение)
                if (event.candidates.isNotEmpty()) {
                    val grpcCandidates = event.candidates.map { it.toGrpc() }
                    val targetId = event.peerId.value
                    val result = signalingRepository.sendIceCandidates(grpcCandidates, targetId)
                    if (result is Result.Error) {
                        _webRTCEvents.emit(WebRTCEvent.ErrorOccurred(event.peerId, "Failed to send gathered ICE candidates: ${result.error}"))
                    }
                }
            }
            is WebRTCControllerEvent.ConnectionStateChanged -> {
                _webRTCEvents.emit(WebRTCEvent.SessionStateChanged(event.peerId, event.newState))
                // Обновляем состояние DataChannel при изменении состояния ICE
                if (event.newState == PeerConnection.IceConnectionState.CLOSED ||
                    event.newState == PeerConnection.IceConnectionState.FAILED ||
                    event.newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    dataChannelStates.remove(event.peerId) // Считаем канал не готовым
                }
            }
            is WebRTCControllerEvent.DataChannelOpened -> {
                dataChannelStates[event.peerId] = true // Канал открыт
                _webRTCEvents.emit(WebRTCEvent.DataChannelOpened(event.peerId))
            }
            is WebRTCControllerEvent.DataChannelClosed -> {
                dataChannelStates.remove(event.peerId) // Канал закрыт
                _webRTCEvents.emit(WebRTCEvent.DataChannelClosed(event.peerId))
            }
            is WebRTCControllerEvent.DataChannelMessageReceived -> {
                // Эмитим событие с сырыми байтами
                _webRTCEvents.emit(WebRTCEvent.BinaryMessageReceived(event.peerId, event.message))
            }
            is WebRTCControllerEvent.PeerConnectionError -> {
                _webRTCEvents.emit(WebRTCEvent.ErrorOccurred(event.peerId, event.errorDescription))
                dataChannelStates.remove(event.peerId) // При ошибке соединения считаем канал не готовым
            }
            is WebRTCControllerEvent.RemoteDataChannelAvailable -> {
                (webRTCController as? WebRTCControllerImpl)?.registerDataChannelObserver(event.peerId, event.dataChannel)
            }
        }
    }

    // Обработка событий от SignalingRepository
    private suspend fun processSignalingEvent(event: ru.drsn.waves.domain.model.signaling.SignalingEvent) {
        Timber.tag(TAG).d("Получено событие от SignalingRepository: $event")
        when (event) {
            is ru.drsn.waves.domain.model.signaling.SignalingEvent.SdpOfferReceived -> {
                val peerId = PeerId(event.senderId)
                val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, event.sdp)
                webRTCController.handleRemoteOfferAndCreateAnswer(peerId, remoteSdp, defaultSdpConstraints)
            }
            is ru.drsn.waves.domain.model.signaling.SignalingEvent.SdpAnswerReceived -> {
                val peerId = PeerId(event.senderId)
                val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, event.sdp)
                webRTCController.handleRemoteAnswer(peerId, remoteSdp)
            }
            is ru.drsn.waves.domain.model.signaling.SignalingEvent.IceCandidatesReceived -> {
                val peerId = PeerId(event.senderId)
                event.candidates.forEach { grpcCandidate ->
                    // Конвертируем gRPC модель в org.webrtc.IceCandidate
                    val rtcIceCandidate = IceCandidate(
                        grpcCandidate.sdpMid,
                        grpcCandidate.sdpMLineIndex,
                        grpcCandidate.candidate
                    )
                    webRTCController.addRemoteIceCandidate(peerId, rtcIceCandidate)
                }
            }
            // Другие события сигналинга (UserListUpdated, Connected, Disconnected, NewPeerNotificationReceived)
            // могут обрабатываться на уровне ViewModel или других UseCase, если они не влияют напрямую на WebRTC сессию.
            // Например, NewPeerNotificationReceived может инициировать вызов initiateCall.
            is ru.drsn.waves.domain.model.signaling.SignalingEvent.NewPeerNotificationReceived -> {
                // Пример: если пришло уведомление о новом пире, инициируем звонок ему
                // val newPeerId = PeerId(event.newPeerId)
                // initiateCall(newPeerId) // Это может быть сделано в ViewModel, подписанной на это событие
                Timber.tag(TAG).i("Получено уведомление о новом пире ${event.newPeerId} от ${event.senderId}. Обработка на уровне ViewModel/UseCase.")
            }
            is SignalingEvent.UserListUpdated -> {

                val users = signalingRepository.getCurrentOnlineUsers()

                for (candidate: String in callSet) {
                    if (users.contains(candidate)) initiateCall(PeerId(candidate))
                }
            }
            else -> { /* Остальные события сигналинга обрабатываются выше по стеку */ }
        }
    }

    override suspend fun initiateCall(peerId: PeerId): Result<Unit, WebRTCError> {
        if (!isInitialized) { initialize() } // Гарантируем инициализацию
        Timber.tag(TAG).i("Инициация звонка пиру: ${peerId.value}")
        // WebRTCController создаст Offer и эмитнет LocalSdpCreated,
        // который будет обработан в processControllerEvent и отправлен через сигналинг.

        if (!signalingRepository.getCurrentOnlineUsers().contains(peerId.value)) {
            if (!callSet.contains(peerId.value)) callSet.add(peerId.value)
            return Result.Error(WebRTCError.ConnectionSetupFailed(peerId, "User are currently offline and was added to call set"))
        }

        webRTCController.createOffer(peerId, defaultSdpConstraints)
        return Result.Success(Unit) // Возвращаем успех немедленно, т.к. процесс асинхронный
    }

    override suspend fun handleRemoteSdp(sdpData: SignalingSdpData): Result<Unit, WebRTCError> {
        if (!isInitialized) { initialize() }
        val peerId = PeerId(sdpData.senderId) // SDP пришел от этого отправителя
        val sdpType = SessionDescription.Type.fromCanonicalForm(sdpData.type.lowercase())
        val sessionDescription = SessionDescription(sdpType, sdpData.sdp)

        Timber.tag(TAG).i("Обработка удаленного SDP от ${peerId.value}, тип: ${sdpData.type}")
        when (sdpType) {
            SessionDescription.Type.OFFER -> {
                webRTCController.handleRemoteOfferAndCreateAnswer(peerId, sessionDescription, defaultSdpConstraints)
            }
            SessionDescription.Type.ANSWER -> {
                webRTCController.handleRemoteAnswer(peerId, sessionDescription)
            }
            else -> {
                val errorMsg = "Неподдерживаемый тип SDP: ${sdpData.type}"
                Timber.tag(TAG).e(errorMsg)
                _webRTCEvents.emit(WebRTCEvent.ErrorOccurred(peerId, errorMsg))
                return Result.Error(WebRTCError.OperationFailed(peerId, errorMsg, null))
            }
        }
        return Result.Success(Unit)
    }

    override suspend fun handleRemoteIceCandidate(iceCandidateData: SignalingIceCandidateData): Result<Unit, WebRTCError> {
        if (!isInitialized) { initialize() }
        val peerId = PeerId(iceCandidateData.senderId) // Кандидат пришел от этого отправителя
        val rtcIceCandidate = IceCandidate(
            iceCandidateData.candidateInfo.sdpMid,
            iceCandidateData.candidateInfo.sdpMLineIndex,
            iceCandidateData.candidateInfo.candidate
        )
        Timber.tag(TAG).i("Обработка удаленного ICE кандидата от ${peerId.value}")
        webRTCController.addRemoteIceCandidate(peerId, rtcIceCandidate)
        return Result.Success(Unit)
    }

    override suspend fun sendMessage(message: WebRTCMessage): Result<Unit, WebRTCError> {
        if (!isInitialized) {
            val error = WebRTCError.OperationFailed(message.peerId, "WebRTC не инициализирован", null)
            _webRTCEvents.emit(WebRTCEvent.ErrorOccurred(message.peerId, error.message))
            return Result.Error(error)
        }
        Timber.tag(TAG).d("Отправка сообщения пиру ${message.peerId.value}: ${message.content.take(50)}...")

        val success = webRTCController.sendMessage(
            message.peerId,
            WebRTCControllerImpl.DATA_CHANNEL_LABEL, // Используем стандартную метку
            message.contentBytes!!
        )
        return if (success) {
            Result.Success(Unit)
        } else {
            val error = WebRTCError.MessageSendFailed(message.peerId, "Не удалось отправить сообщение через контроллер", null)
            _webRTCEvents.emit(WebRTCEvent.ErrorOccurred(message.peerId, error.message))
            Result.Error(error)
        }
    }

    override suspend fun closeConnection(peerId: PeerId): Result<Unit, WebRTCError> {
        Timber.tag(TAG).w("Закрытие соединения с пиром: ${peerId.value}")
        webRTCController.closeConnection(peerId)
        // Событие о закрытии будет эмитировано из WebRTCControllerEvent.ConnectionStateChanged
        return Result.Success(Unit)
    }

    override suspend fun closeAllConnections(): Result<Unit, WebRTCError> {
        Timber.tag(TAG).w("Закрытие всех WebRTC соединений.")
        webRTCController.closeAllConnectionsAndReleaseFactory()
        isInitialized = false // Сбрасываем флаг инициализации
        // События о закрытии для каждого пира будут эмитированы из контроллера
        repositoryScope.cancel("Все соединения WebRTC закрыты, репозиторий завершает работу.") // Отменяем скоуп репозитория
        return Result.Success(Unit)
    }

    override fun observeWebRTCEvents(): Flow<WebRTCEvent> = _webRTCEvents.asSharedFlow()

    override fun getSessionState(peerId: PeerId): WebRTCSessionState? {
        // Это потребует от WebRTCController предоставлять более высокоуровневое состояние
        // или репозиторий должен сам его отслеживать на основе событий ConnectionStateChanged.
        // Для простоты, вернем null или потребуем доработки.
        // Пример: можно хранить Map<PeerId, PeerConnection.IceConnectionState> и маппить его.
        val pcState = webRTCController.getPeerConnectionState(peerId) // Это SignalingState, не IceConnectionState
        val iceState = currentIceStates[peerId] // Предположим, мы храним это
        return iceState?.toWebRTCSessionState()
    }

    private val currentIceStates = ConcurrentHashMap<PeerId, PeerConnection.IceConnectionState>()

    override suspend fun isDataChannelReady(peerId: PeerId): Boolean {
        // Проверяем наше внутреннее состояние, обновляемое по событию DataChannelOpened
        return dataChannelStates[peerId] == true
    }

    override fun getActivePeers(): Set<PeerId> {
        return webRTCController.getActivePeerIds()
    }

    // Вспомогательная функция для конвертации состояния ICE в доменное состояние сессии
    private fun PeerConnection.IceConnectionState.toWebRTCSessionState(): WebRTCSessionState {
        return when (this) {
            PeerConnection.IceConnectionState.NEW,
            PeerConnection.IceConnectionState.CHECKING -> WebRTCSessionState.Connecting
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> WebRTCSessionState.Connected
            PeerConnection.IceConnectionState.DISCONNECTED -> WebRTCSessionState.Disconnected
            PeerConnection.IceConnectionState.FAILED -> WebRTCSessionState.Failed("ICE connection failed")
            PeerConnection.IceConnectionState.CLOSED -> WebRTCSessionState.Disconnected // Или отдельное состояние Closed
        }
    }
    // Вспомогательная функция для конвертации org.webrtc.IceCandidate в gRPC модель
    private fun org.webrtc.IceCandidate.toGrpc(): gRPC.v1.Signaling.IceCandidate {
        return gRPC.v1.Signaling.IceCandidate.newBuilder()
            .setSdpMid(this.sdpMid ?: "")
            .setSdpMLineIndex(this.sdpMLineIndex)
            .setCandidate(this.sdp)
            .build()
    }
}
