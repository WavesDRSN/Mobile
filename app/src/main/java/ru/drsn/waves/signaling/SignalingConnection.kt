package ru.drsn.waves.signaling

import com.google.protobuf.Timestamp
import gRPC.v1.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.drsn.waves.BuildConfig
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class SignalingConnection(
    serverAddress: String,
    serverPort: Int,
    private val username: String
) {
    private val channel: ManagedChannel = ManagedChannelBuilder
        .forAddress(serverAddress, serverPort)
        .apply {
            if (BuildConfig.RELEASE) {
                usePlaintext()
            } else {
                useTransportSecurity()
            }
        }
        .build()

    private val stub: UserConnectionGrpcKt.UserConnectionCoroutineStub =
        UserConnectionGrpcKt.UserConnectionCoroutineStub(channel)

    private val _usersListFlow = MutableStateFlow<List<User>>(emptyList())
    val usersListStateFlow: StateFlow<List<User>> = _usersListFlow.asStateFlow()

    private val isConnected = AtomicBoolean(false)

    private var keepAliveJob: Job? = null

    private val requestChannel: Channel<UserConnectionRequest> = Channel(Channel.BUFFERED)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val SDPFlow = MutableSharedFlow<SessionDescription>()
    private val outgoingSDPFlow = MutableSharedFlow<SessionDescription>()
    private val iceCandidatesFlow = MutableSharedFlow<IceCandidatesMessage>(extraBufferCapacity = 10)
    private val outgoingIceCandidatesFlow = MutableSharedFlow<IceCandidatesMessage>(extraBufferCapacity = 10)


    fun connect() {
        coroutineScope.launch {
            // Сначала отправляем InitialRequest через канал
            sendInitialRequest(requestChannel)
            listenForIceCandidates()
            listenForSDP()
            // Запускаем прослушивание сообщений от сервера, преобразуя канал в Flow
            listenForServerMessages(requestChannel.receiveAsFlow())
        }
    }

    private suspend fun sendInitialRequest(requestChannel: SendChannel<UserConnectionRequest>) {
        val initialRequest = UserConnectionRequest.newBuilder()
            .setInitialRequest(
                InitialUserConnectionRequest.newBuilder()
                    .setName(username)
                    .build()
            )
            .build()
        requestChannel.send(initialRequest)
        Timber.i("Sent InitialUserConnectionRequest for user $username")
    }

    private suspend fun listenForServerMessages(requestFlow: Flow<UserConnectionRequest>) {
        stub.loadUsersList(requestFlow).collect { response ->
            processServerResponse(response, requestChannel)
        }
    }

    /**
     * Обрабатывает различные типы ответов от сервера.
     */
    private suspend fun processServerResponse(
        response: UserConnectionResponse,
        requestChannel: Channel<UserConnectionRequest>
    ) {
        when {
            response.hasInitialResponse() -> handleInitialResponse(response.initialResponse, requestChannel)
            response.hasUsersList() -> handleUsersList(response.usersList)
            else -> Timber.w("Received unhandled response type")
        }
    }

    /**
     * Функция обработки первичного ответа от сервера.
     * Устанавливает поток для пакетов поддержания жизни
     */
    private suspend fun handleInitialResponse(
        initialResponse: InitialUserConnectionResponse,
        requestChannel: Channel<UserConnectionRequest>
    ) {
        val intervalMillis = initialResponse.userKeepAliveInterval.seconds * 1000
        isConnected.set(true)
        Timber.d("Connected to signaling server as $username with keep-alive interval: $intervalMillis ms")
        startKeepAliveFlow(requestChannel, intervalMillis)
    }

    /*
    * Обрабатывает ответ со списком пользователя
    * Логика: обновляет свою переменную списка, присваивая каждый раз новую ссылку.
    * */
    private suspend fun handleUsersList(usersList: UsersList) {
        val users = usersList.usersList;
        _usersListFlow.emit(users);
        Timber.i("Active users: ${users.joinToString { it.name }}")
    }

    private suspend fun handleSessionDescription(sdp: SessionDescription) {
        Timber.i("Received SDP ${sdp.type} from ${sdp.sender}")
        outgoingSDPFlow.emit(sdp)
    }

    private fun startKeepAliveFlow(requestChannel: SendChannel<UserConnectionRequest>, intervalMillis: Long) {
        keepAliveJob?.cancel()
        keepAliveJob = coroutineScope.launch {
            while (isConnected.get()) {
                delay(intervalMillis)
                sendKeepAlive(requestChannel)
            }
        }
    }

    private suspend fun sendKeepAlive(requestChannel: SendChannel<UserConnectionRequest>) {
        val alivePacket = AlivePacket.newBuilder()
            .setKissOfThePoseidon("kiss me")
            .setTimestamp(Timestamp.getDefaultInstance())
            .build()
        val request = UserConnectionRequest.newBuilder()
            .setStillAlive(alivePacket)
            .build()
        requestChannel.send(request)
        Timber.d("Sent keep-alive packet")
    }

    private fun listenForSDP() {
        coroutineScope.launch {
            stub.exchangeSDP(SDPFlow)
                .collect{ sessionDescription ->
                    Timber.d("get SDP from ${sessionDescription.sender}}")
                    outgoingSDPFlow.emit(sessionDescription)
                }
        }
    }

    fun sendSDP(type: String, sdp: String, target: String) {
        coroutineScope.launch {
            val request = SessionDescription.newBuilder()
                    .setSdp(sdp)
                    .setType(type)
                    .setReceiver(target)
                    .build()

            SDPFlow.emit(request)

            Timber.d("Sent SDP $type to $target")
        }
    }

    private fun listenForIceCandidates() {
        coroutineScope.launch {
            stub.sendIceCandidates(iceCandidatesFlow)
                .collect { iceCandidatesMessage ->
                    Timber.d("Received ICE candidates from ${iceCandidatesMessage.sender}")
                    outgoingIceCandidatesFlow.emit(iceCandidatesMessage)
                }
        }
    }

    suspend fun sendIceCandidates(candidates: List<IceCandidate>, target: String) {
        val message = IceCandidatesMessage.newBuilder()
            .setReceiver(target)
            .addAllCandidates(candidates)
            .build()

        Timber.d("Sent ICE Candidates $candidates to $target")

        iceCandidatesFlow.emit(message)
    }


    fun observeIceCandidates(): SharedFlow<IceCandidatesMessage> = outgoingIceCandidatesFlow

    fun observeSDP(): SharedFlow<SessionDescription> = outgoingSDPFlow

    fun observeUsersList(): StateFlow<List<User>> = usersListStateFlow

    fun disconnect() {
        coroutineScope.launch {
            stub.userDisconnect(DisconnectRequest.newBuilder().setName(username).build())
            isConnected.set(false)
            channel.shutdown()
            Timber.d("Disconnected from signaling server")
        }
    }
}