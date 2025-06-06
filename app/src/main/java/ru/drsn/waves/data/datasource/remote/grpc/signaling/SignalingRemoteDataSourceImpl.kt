package ru.drsn.waves.data.datasource.remote.grpc.signaling

import com.google.protobuf.Timestamp
import gRPC.v1.Signaling.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.drsn.waves.data.datasource.remote.grpc.AuthTokenInterceptor
import ru.drsn.waves.domain.model.signaling.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import ru.drsn.waves.domain.model.utils.Result

@Singleton
class SignalingRemoteDataSourceImpl @Inject constructor(
    private val authTokenInterceptor: AuthTokenInterceptor
) : ISignalingRemoteDataSource {
    private companion object {
        const val TAG = "SignalingRemoteDS"
        const val KEEP_ALIVE_TIMEOUT_SECONDS = 30L // Таймаут для gRPC вызовов keep-alive
        const val SHUTDOWN_TIMEOUT_SECONDS = 5L    // Таймаут для ожидания закрытия канала
    }

    private var managedChannel: ManagedChannel? = null

    private var mainServiceStub: UserConnectionGrpcKt.UserConnectionCoroutineStub? = null // Для основного потока usersList
    private var sdpServiceStub: UserConnectionGrpcKt.UserConnectionCoroutineStub? = null // Отдельный стаб для потока SDP
    private var iceServiceStub: UserConnectionGrpcKt.UserConnectionCoroutineStub? = null // Отдельный стаб для потока ICE


    private val dataSourceScope = CoroutineScope(Dispatchers.IO + SupervisorJob()) // Собственный скоуп для DataSource
    private var currentUsername: String? = null
    private var currentUserKey: String? = null

    // Каналы для отправки данных в gRPC стримы
    private var mainRequestChannel: Channel<UserConnectionRequest>? = null
    private var sdpRequestChannel: Channel<SDPExchange>? = null
    private var iceRequestChannel: Channel<ICEExchange>? = null

    private val isFullyConnected = AtomicBoolean(false) // Флаг, что все стримы инициализированы и соединение готово
    private var keepAliveJob: Job? = null


    override fun connectAndObserve(
        username: String,
        host: String,
        port: Int
    ): Flow<SignalingServerEvent> = channelFlow { // Используем channelFlow для управления жизненным циклом Flow
        currentUsername = username
        try {
            Timber.tag(TAG).i("Попытка подключения к $host:$port от имени $username")
            // Создание нового ManagedChannel
            val newChannel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(KEEP_ALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .keepAliveTimeout(KEEP_ALIVE_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
                .idleTimeout(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
                .intercept(authTokenInterceptor)
                .build()
            managedChannel = newChannel
            // Инициализация gRPC стабов
            mainServiceStub = UserConnectionGrpcKt.UserConnectionCoroutineStub(newChannel)
            sdpServiceStub = UserConnectionGrpcKt.UserConnectionCoroutineStub(newChannel)
            iceServiceStub = UserConnectionGrpcKt.UserConnectionCoroutineStub(newChannel)

            // Инициализация каналов для отправки сообщений в стримы
            mainRequestChannel = Channel(Channel.BUFFERED)
            sdpRequestChannel = Channel(Channel.BUFFERED)
            iceRequestChannel = Channel(Channel.BUFFERED)

            // Запуск корутин для прослушивания каждого gRPC стрима
            launchMainConnectionStream(mainRequestChannel!!, this)
            launchSdpStream(sdpRequestChannel!!, this)
            launchIceStream(iceRequestChannel!!, this)

            // Отправка первоначального запроса для основного стрима
            sendInitialMainRequest(mainRequestChannel!!)

            Timber.tag(TAG).i("Flow наблюдения за сигнализацией запущен для $username.")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Ошибка подключения для $username")
            // Отправка события ошибки в Flow
            send(SignalingServerEvent.ErrorOccurred(SignalingError.ConnectionFailed("Ошибка начальной настройки соединения", e)))
            closeChannelsAndShutdown() // Закрытие ресурсов при ошибке
            return@channelFlow // Завершение channelFlow
        }

        // Этот блок будет выполнен, когда Flow будет закрыт (отписан)
        awaitClose {
            Timber.tag(TAG).i("Flow наблюдения закрывается для $username.")
            // Запуск закрытия ресурсов в собственном скоупе dataSourceScope, чтобы не блокировать вызывающий поток
            dataSourceScope.launch {
                closeChannelsAndShutdown()
            }
        }
    }.catch { e -> // Обработка исключений, возникших в самом Flow
        Timber.tag(TAG).e(e, "Ошибка в Flow connectAndObserve для $username")
        emit(SignalingServerEvent.ErrorOccurred(SignalingError.Unknown("Ошибка в Flow", e)))
        closeChannelsAndShutdown() // Гарантированное закрытие ресурсов
    }.flowOn(Dispatchers.IO) // Выполнение всей логики Flow в IO диспатчере


    // Запускает и управляет основным gRPC стримом (loadUsersList)
    private fun CoroutineScope.launchMainConnectionStream(
        requestChan: Channel<UserConnectionRequest>,
        flowCollector: kotlinx.coroutines.channels.ProducerScope<SignalingServerEvent> // ProducerScope для отправки событий в channelFlow
    ) = launch {
        try {
            mainServiceStub?.loadUsersList(requestChan.receiveAsFlow()) // requestChan.receiveAsFlow() преобразует Channel в Flow
                ?.collect { response -> // Сбор сообщений от сервера
                    when {
                        response.hasInitialResponse() -> {
                            currentUserKey = response.initialResponse.userKey
                            flowCollector.send(SignalingServerEvent.InitialResponse(response.initialResponse))
                            startKeepAlive(requestChan, response.initialResponse.userKeepAliveInterval.seconds)
                            // После получения InitialResponse для основного стрима, отправляем Initial запросы для SDP и ICE стримов
                            sendInitialSdpRequest(sdpRequestChannel!!, currentUserKey!!)
                            sendInitialIceRequest(iceRequestChannel!!, currentUserKey!!)
                            // Только после успешной инициализации всех частей считаем соединение полностью установленным
                            flowCollector.send(SignalingServerEvent.ConnectionEstablished)
                            isFullyConnected.set(true)
                        }
                        response.hasUsersList() -> flowCollector.send(SignalingServerEvent.UsersListReceived(response.usersList))
                        else -> Timber.tag(TAG).w("Получен необрабатываемый тип ответа в основном стриме")
                    }
                }
        } catch (e: StatusRuntimeException) {
            Timber.tag(TAG).e(e, "StatusRuntimeException в основном gRPC стриме: ${e.status}")
            flowCollector.trySend(SignalingServerEvent.ErrorOccurred(SignalingError.DisconnectedError("Ошибка основного gRPC стрима: ${e.status}", e)))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Исключение в основном стриме")
            flowCollector.trySend(SignalingServerEvent.ErrorOccurred(SignalingError.Unknown("Ошибка сбора сообщений в основном стриме", e)))
        } finally {
            Timber.tag(TAG).i("Основной стрим соединения завершен для $currentUsername")
            flowCollector.trySend(SignalingServerEvent.StreamEnded) // Уведомление репозитория о завершении стрима
            isFullyConnected.set(false) // Сброс флага полного подключения
        }
    }

    // Запускает и управляет gRPC стримом для обмена SDP (exchangeSDP)
    private fun CoroutineScope.launchSdpStream(
        sdpChan: Channel<SDPExchange>,
        flowCollector: kotlinx.coroutines.channels.ProducerScope<SignalingServerEvent>
    ) = launch {
        try {
            sdpServiceStub?.exchangeSDP(sdpChan.receiveAsFlow())
                ?.collect { response ->
                    when {
                        response.hasInitialResponse() -> {
                            Timber.tag(TAG).d("Первичный ответ SDP стрима: approved=${response.initialResponse.approved}")
                            flowCollector.send(SignalingServerEvent.SdpStreamStatus(response.initialResponse.approved))
                        }
                        response.hasSessionDescription() -> {
                            Timber.tag(TAG).d("Получено SDP сообщение от ${response.sessionDescription.sender}")
                            flowCollector.send(SignalingServerEvent.SdpMessageReceived(response.sessionDescription))
                        }
                        else -> Timber.tag(TAG).w("Получен необрабатываемый тип SDP обмена")
                    }
                }
        } catch (e: StatusRuntimeException) {
            Timber.tag(TAG).e(e, "StatusRuntimeException в gRPC стриме SDP: ${e.status}")
            // Ошибки отдельных стримов (SDP/ICE) могут не означать полного разрыва, если основной стрим жив.
            // Репозиторий может решить, как реагировать.
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Исключение в SDP стриме")
        } finally {
            Timber.tag(TAG).i("SDP стрим завершен для $currentUsername")
        }
    }

    // Запускает и управляет gRPC стримом для обмена ICE кандидатами (sendIceCandidates)
    private fun CoroutineScope.launchIceStream(
        iceChan: Channel<ICEExchange>,
        flowCollector: kotlinx.coroutines.channels.ProducerScope<SignalingServerEvent>
    ) = launch {
        try {
            iceServiceStub?.sendIceCandidates(iceChan.receiveAsFlow())
                ?.collect { response ->
                    when {
                        response.hasInitialResponse() -> {
                            Timber.tag(TAG).d("Первичный ответ ICE стрима: approved=${response.initialResponse.approved}")
                            flowCollector.send(SignalingServerEvent.IceStreamStatus(response.initialResponse.approved))
                        }
                        response.hasIceCandidates() -> {
                            Timber.tag(TAG).d("Получены ICE кандидаты от ${response.iceCandidates.sender}")
                            flowCollector.send(SignalingServerEvent.IceCandidatesReceived(response.iceCandidates))
                        }
                        else -> Timber.tag(TAG).w("Получен необрабатываемый тип ICE обмена")
                    }
                }
        } catch (e: StatusRuntimeException) {
            Timber.tag(TAG).e(e, "StatusRuntimeException в gRPC стриме ICE: ${e.status}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Исключение в ICE стриме")
        } finally {
            Timber.tag(TAG).i("ICE стрим завершен для $currentUsername")
        }
    }

    // Отправка первоначального запроса для основного стрима
    private suspend fun sendInitialMainRequest(requestChan: SendChannel<UserConnectionRequest>) {
        val username = currentUsername ?: return // Проверка, что имя пользователя установлено
        val initialRequest = UserConnectionRequest.newBuilder()
            .setInitialRequest(InitialUserConnectionRequest.newBuilder().setName(username))
            .build()
        try {
            requestChan.send(initialRequest)
            Timber.tag(TAG).i("Отправлен InitialUserConnectionRequest для пользователя $username")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось отправить первоначальный основной запрос")
            // Ошибка будет обработана коллектором Flow, если канал закроется
        }
    }

    // Отправка первоначального запроса для SDP стрима
    private suspend fun sendInitialSdpRequest(sdpChan: SendChannel<SDPExchange>, userKey: String) {
        val request = SDPExchange.newBuilder()
            .setInitialRequest(SDPStreamInitialRequest.newBuilder().setKey(userKey))
            .build()
        try {
            sdpChan.send(request)
            Timber.tag(TAG).d("Отправлен первоначальный SDP запрос с ключом $userKey")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось отправить первоначальный SDP запрос")
        }
    }

    // Отправка первоначального запроса для ICE стрима
    private suspend fun sendInitialIceRequest(iceChan: SendChannel<ICEExchange>, userKey: String) {
        val request = ICEExchange.newBuilder()
            .setInitialRequest(ICEStreamInitialRequest.newBuilder().setKey(userKey))
            .build()
        try {
            iceChan.send(request)
            Timber.tag(TAG).d("Отправлен первоначальный ICE запрос с ключом $userKey")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Не удалось отправить первоначальный ICE запрос")
        }
    }

    // Запуск периодической отправки keep-alive пакетов
    private fun startKeepAlive(requestChan: SendChannel<UserConnectionRequest>, intervalSeconds: Long) {
        keepAliveJob?.cancel() // Отмена предыдущего job, если есть
        if (intervalSeconds <= 0) {
            Timber.tag(TAG).w("Некорректный интервал keep-alive: $intervalSeconds. Keep-alive отключен.")
            return
        }
        keepAliveJob = dataSourceScope.launch {
            while (isFullyConnected.get()) { // Используем isFullyConnected, который устанавливается после всех инициализаций
                delay(intervalSeconds * 1000)
                if (!isFullyConnected.get()) break // Двойная проверка перед отправкой

                val alivePacket = AlivePacket.newBuilder()
                    .setKissOfThePoseidon("kiss_me_${System.currentTimeMillis()}") // Уникальное сообщение для отладки
                    .setTimestamp(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000))
                    .build()
                val request = UserConnectionRequest.newBuilder().setStillAlive(alivePacket).build()
                try {
                    requestChan.send(request)
                    Timber.tag(TAG).v("Отправлен keep-alive пакет")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Не удалось отправить keep-alive пакет. Остановка keep-alive.")
                    isFullyConnected.set(false) // Предполагаем, что соединение потеряно
                    break // Выход из цикла
                }
            }
            Timber.tag(TAG).i("Работа keep-alive завершена для $currentUsername")
        }
    }

    override suspend fun sendSdpToServer(sdpExchange: SDPExchange): Result<Unit, SignalingError> {
        val sdpChan = sdpRequestChannel // Локальная копия для проверки на null
        return if (!isFullyConnected.get() || sdpChan == null || sdpChan.isClosedForSend) {
            Result.Error(SignalingError.NotConnected("SDP стрим не готов или закрыт"))
        } else {
            try {
                sdpChan.send(sdpExchange)
                Timber.tag(TAG).d("Отправлено SDP на сервер: type=${sdpExchange.sessionDescription.type}, target=${sdpExchange.sessionDescription.receiver}")
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Не удалось отправить SDP на сервер")
                Result.Error(SignalingError.MessageSendFailed("Не удалось отправить SDP", e))
            }
        }
    }

    override suspend fun sendIceCandidatesToServer(iceExchange: ICEExchange): Result<Unit, SignalingError> {
        val iceChan = iceRequestChannel // Локальная копия
        return if (!isFullyConnected.get() || iceChan == null || iceChan.isClosedForSend) {
            Result.Error(SignalingError.NotConnected("ICE стрим не готов или закрыт"))
        } else {
            try {
                iceChan.send(iceExchange)
                Timber.tag(TAG).d("Отправлено ${iceExchange.iceCandidates.candidatesCount} ICE кандидатов на сервер для ${iceExchange.iceCandidates.receiver}")
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Не удалось отправить ICE кандидатов на сервер")
                Result.Error(SignalingError.MessageSendFailed("Не удалось отправить ICE кандидатов", e))
            }
        }
    }

    override suspend fun disconnectFromServer(): Result<Unit, SignalingError> = withContext(Dispatchers.IO) {
        Timber.tag(TAG).i("Отключение от сервера от имени $currentUsername...")
        isFullyConnected.set(false) // Сброс флага
        keepAliveJob?.cancel()      // Отмена keep-alive

        // Вежливое информирование сервера об отключении, если возможно
        if (managedChannel?.isShutdown == false && currentUsername != null && mainRequestChannel?.isClosedForSend == false) {
            try {
                mainServiceStub?.userDisconnect(DisconnectRequest.newBuilder().setName(currentUsername).build())
                Timber.tag(TAG).d("Отправлен запрос на отключение (userDisconnect) на сервер.")
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Не удалось отправить вежливый запрос на отключение.")
            }
        }
        closeChannelsAndShutdown() // Закрытие всех каналов и gRPC соединения
        currentUsername = null
        currentUserKey = null
        Result.Success(Unit)
    }

    // Закрывает все каналы отправки и gRPC ManagedChannel
    private suspend fun closeChannelsAndShutdown() {
        withContext(Dispatchers.IO) { // Гарантируем выполнение в IO
            Timber.tag(TAG).d("Закрытие gRPC каналов отправки и ManagedChannel.")
            mainRequestChannel?.close()
            sdpRequestChannel?.close()
            iceRequestChannel?.close()
            // Обнуление каналов, чтобы избежать их повторного использования
            mainRequestChannel = null
            sdpRequestChannel = null
            iceRequestChannel = null

            managedChannel?.let { ch -> // Используем локальную переменную для null-безопасности
                if (!ch.isShutdown) {
                    try {
                        ch.shutdown() // Инициируем закрытие
                        if (!ch.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { // Ожидаем завершения
                            Timber.tag(TAG).w("gRPC канал не завершился вовремя, принудительное закрытие.")
                            ch.shutdownNow() // Принудительное закрытие
                        } else {
                            Timber.tag(TAG).i("gRPC канал успешно закрыт.")
                        }
                    } catch (e: InterruptedException) {
                        Timber.tag(TAG).w(e, "Прервано во время закрытия канала, принудительное закрытие.")
                        ch.shutdownNow()
                        Thread.currentThread().interrupt() // Восстанавливаем флаг прерывания
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Исключение во время закрытия канала.")
                    }
                }
            }
            // Обнуление gRPC ресурсов
            managedChannel = null
            mainServiceStub = null
            sdpServiceStub = null
            iceServiceStub = null
            Timber.tag(TAG).i("Очистка ресурсов DataSource завершена.")
        }
    }

    override fun getUserKey(): String? = currentUserKey
}