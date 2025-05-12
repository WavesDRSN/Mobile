package ru.drsn.waves.data.repository

import gRPC.v1.Signaling.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.drsn.waves.data.datasource.remote.grpc.signaling.ISignalingRemoteDataSource
import ru.drsn.waves.data.datasource.remote.grpc.signaling.SignalingServerEvent
import ru.drsn.waves.domain.model.signaling.*
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.repository.ISignalingRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


// --- Реализация Репозитория ---
@Singleton
class SignalingRepositoryImpl @Inject constructor(
    private val remoteDataSource: ISignalingRemoteDataSource
) : ISignalingRepository {

    private companion object {
        const val TAG = "SignalingRepository"
    }

    private var currentUsername: String? = null
    // Собственный CoroutineScope для репозитория, чтобы управлять жизненным циклом подписок
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var observationJob: Job? = null // Job для управления активной подпиской на события от DataSource

    // MutableSharedFlow для трансляции событий сигнализации подписчикам (UseCases/ViewModels)
    // replay = 0: новые подписчики не получают старые события
    // extraBufferCapacity: буфер для предотвращения проблем с обратным давлением, если подписчики медленные
    private val _signalingEvents = MutableSharedFlow<SignalingEvent>(replay = 0, extraBufferCapacity = 64)

    override fun connect(username: String, host: String, port: Int): Result<Unit, SignalingError> {
        // Метод не suspend, так как он только инициирует асинхронный процесс подключения и подписки.
        // Фактическое состояние соединения будет приходить через Flow событий.
        if (observationJob?.isActive == true) {
            Timber.tag(TAG).w("Уже подключено или в процессе подключения.")
            // Можно вернуть ошибку или успех, если уже подключено, в зависимости от требований
            return Result.Error(SignalingError.OperationFailed("Уже подключено или в процессе", null))
        }
        currentUsername = username
        Timber.tag(TAG).i("Подключение репозитория для пользователя: $username к $host:$port")

        // Запуск подписки на события от DataSource
        observationJob = remoteDataSource.connectAndObserve(username, host, port)
            .onStart { Timber.tag(TAG).i("Наблюдение за событиями сигнализации запущено репозиторием.") }
            .onCompletion { cause -> // Вызывается при завершении Flow (успешном или с ошибкой)
                Timber.tag(TAG).i(cause, "Наблюдение за событиями сигнализации завершено репозиторием.")
                if (cause == null || cause is kotlinx.coroutines.CancellationException) {
                    // Если завершение без ошибки или из-за отмены, считаем это отключением
                    _signalingEvents.tryEmit(SignalingEvent.Disconnected)
                } else {
                    // Если завершение с другой ошибкой
                    _signalingEvents.tryEmit(SignalingEvent.ConnectionErrorEvent(SignalingError.DisconnectedError("Поток наблюдения завершился с ошибкой", cause)))
                }
            }
            .mapNotNull { serverEvent -> mapServerEventToDomainEvent(serverEvent) } // Преобразование серверных событий в доменные
            .onEach { domainEvent -> _signalingEvents.emit(domainEvent) } // Эмиссия доменных событий подписчикам
            .catch { e -> // Обработка ошибок в самом Flow сбора событий
                Timber.tag(TAG).e(e, "Ошибка в сборе событий сигнализации репозиторием")
                _signalingEvents.emit(SignalingEvent.ConnectionErrorEvent(SignalingError.Unknown("Ошибка сбора событий в репозитории", e)))
            }
            .launchIn(repositoryScope) // Запуск Flow в скоупе репозитория

        return Result.Success(Unit) // Попытка подключения инициирована
    }

    // Преобразует "сырые" серверные события в доменные события сигнализации
    private fun mapServerEventToDomainEvent(serverEvent: SignalingServerEvent): SignalingEvent? {
        return when (serverEvent) {
            is SignalingServerEvent.ConnectionEstablished -> SignalingEvent.Connected
            is SignalingServerEvent.UsersListReceived -> SignalingEvent.UserListUpdated(serverEvent.usersList.usersList)
            is SignalingServerEvent.SdpMessageReceived -> {
                val sdpMsg = serverEvent.sdpMessage
                when (sdpMsg.type.lowercase()) { // Приводим тип к нижнему регистру для надежности
                    "offer" -> SignalingEvent.SdpOfferReceived(sdpMsg.sdp, sdpMsg.sender)
                    "answer" -> SignalingEvent.SdpAnswerReceived(sdpMsg.sdp, sdpMsg.sender)
                    // Обработка вашего кастомного типа "new_peer"
                    "new_peer" -> SignalingEvent.NewPeerNotificationReceived(sdpMsg.sdp, sdpMsg.sender) // sdpMsg.sdp содержит newPeerId
                    else -> {
                        Timber.tag(TAG).w("Получен неизвестный тип SDP: ${sdpMsg.type}")
                        null // Игнорируем неизвестные типы
                    }
                }
            }
            is SignalingServerEvent.IceCandidatesReceived -> {
                // Фильтрация кандидатов для текущего пользователя.
                // Хотя WebRTCManager обычно сам обрабатывает это, дополнительная проверка не помешает.
                if (serverEvent.iceCandidatesMessage.receiver == currentUsername) {
                    SignalingEvent.IceCandidatesReceived(serverEvent.iceCandidatesMessage.candidatesList, serverEvent.iceCandidatesMessage.sender)
                } else {
                    Timber.tag(TAG).d("Игнорируются ICE кандидаты для ${serverEvent.iceCandidatesMessage.receiver}, текущий пользователь $currentUsername")
                    null
                }
            }
            is SignalingServerEvent.ErrorOccurred -> SignalingEvent.ConnectionErrorEvent(serverEvent.error)
            is SignalingServerEvent.StreamEnded -> SignalingEvent.Disconnected // Основной стрим завершился, считаем это отключением
            // Эти события являются внутренними для DataSource или обрабатываются через ConnectionEstablished/StreamEnded
            is SignalingServerEvent.InitialResponse,
            is SignalingServerEvent.SdpStreamStatus,
            is SignalingServerEvent.IceStreamStatus -> null
        }
    }


    override fun observeSignalingEvents(): Flow<SignalingEvent> = _signalingEvents.asSharedFlow() // Предоставление Flow для подписки


    override suspend fun disconnect(): Result<Unit, SignalingError> {
        Timber.tag(TAG).i("Отключение репозитория для пользователя: $currentUsername")
        observationJob?.cancel("Пользователь инициировал отключение") // Отмена текущей подписки
        observationJob = null
        val result = remoteDataSource.disconnectFromServer() // Вызов отключения на уровне DataSource
        currentUsername = null // Сброс имени пользователя
        // Явная эмиссия события Disconnected, если это не было обработано в onCompletion
        _signalingEvents.tryEmit(SignalingEvent.Disconnected)
        return result
    }

    override suspend fun sendSdp(sdpData: SdpData): Result<Unit, SignalingError> {
        val localUsername = currentUsername // Копируем в локальную переменную для null-безопасности
        if (localUsername == null) return Result.Error(SignalingError.NotConnected("Имя пользователя не установлено"))

        // Создание gRPC объекта для отправки
        val sdpExchange = SDPExchange.newBuilder().setSessionDescription(
            SessionDescription.newBuilder()
                .setType(sdpData.type)
                .setSdp(sdpData.sdp)
                .setReceiver(sdpData.targetId)
                .setSender(localUsername) // Отправитель всегда текущий пользователь
        ).build()
        return remoteDataSource.sendSdpToServer(sdpExchange)
    }

    override suspend fun sendIceCandidates(candidates: List<gRPC.v1.Signaling.IceCandidate>, targetId: String): Result<Unit, SignalingError> {
        val localUsername = currentUsername
            ?: return Result.Error(SignalingError.NotConnected("Имя пользователя не установлено"))

        val iceExchange = ICEExchange.newBuilder().setIceCandidates(
            IceCandidatesMessage.newBuilder()
                .addAllCandidates(candidates)
                .setReceiver(targetId)
                .setSender(localUsername) // Отправитель всегда текущий пользователь
        ).build()
        return remoteDataSource.sendIceCandidatesToServer(iceExchange)
    }

    override suspend fun relayNewPeerNotification(receiverId: String, newPeerId: String): Result<Unit, SignalingError> {
        // Это транслируется в отправку SDP сообщения с типом "new_peer"
        // и newPeerId в качестве содержимого SDP.
        val localUsername = currentUsername
            ?: return Result.Error(SignalingError.NotConnected("Имя пользователя не установлено"))

        val sdpData = SdpData(
            type = "new_peer",
            sdp = newPeerId, // ID нового пира здесь является "содержимым SDP"
            targetId = receiverId,
            senderId = localUsername
        )
        return sendSdp(sdpData)
    }

    override fun getCurrentUsername(): String? = currentUsername

    // Метод для очистки ресурсов репозитория, если он привязан к жизненному циклу ViewModel, например.
    // Если репозиторий Singleton, это может быть вызвано в Application.onTerminate или аналогичном месте.
    fun cleanup() {
        Timber.tag(TAG).i("Очистка SignalingRepository.")
        repositoryScope.cancel("Инициирована очистка репозитория.")
        // Убедимся, что disconnect вызван, если подписка все еще активна
        if (observationJob?.isActive == true) {
            repositoryScope.launch { disconnect() }
        }
    }
}
