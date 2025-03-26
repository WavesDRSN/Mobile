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
            if (!BuildConfig.RELEASE) {
                usePlaintext()
            } else {
                useTransportSecurity()
            }
        }
        .build()

    private val stub: UserConnectionGrpcKt.UserConnectionCoroutineStub =
        UserConnectionGrpcKt.UserConnectionCoroutineStub(channel)

    private val isConnected = AtomicBoolean(false)

    private var keepAliveJob: Job? = null

    private val requestChannel: Channel<UserConnectionRequest> = Channel(Channel.BUFFERED)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val sdpFlow = MutableSharedFlow<SessionDescription>()
    private val iceCandidatesFlow = MutableSharedFlow<IceCandidatesMessage>(extraBufferCapacity = 10)
    private val outgoingIceCandidatesFlow = MutableSharedFlow<IceCandidatesMessage>(extraBufferCapacity = 10)


    fun connect() {
        coroutineScope.launch {
            // Сначала отправляем InitialRequest через канал
            sendInitialRequest(requestChannel)
            // Запускаем прослушивание сообщений от сервера, преобразуя канал в Flow
            listenForServerMessages(requestChannel.receiveAsFlow())
            listenForIceCandidates()
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
            response.hasSessionDescription() -> handleSessionDescription(response.sessionDescription)
            else -> Timber.w("Received unhandled response type")
        }
    }

    private suspend fun handleInitialResponse(
        initialResponse: InitialUserConnectionResponse,
        requestChannel: Channel<UserConnectionRequest>
    ) {
        val intervalMillis = initialResponse.userKeepAliveInterval.seconds * 1000
        isConnected.set(true)
        Timber.i("Connected to signaling server as $username with keep-alive interval: $intervalMillis ms")
        startKeepAliveFlow(requestChannel, intervalMillis)
    }

    private fun handleUsersList(usersList: UsersList) {
        val users = usersList.usersList
        Timber.i("Active users: ${users.joinToString { it.name }}")
    }

    private suspend fun handleSessionDescription(sdp: SessionDescription) {
        Timber.i("Received SDP ${sdp.type} from ${sdp.sender}")
        sdpFlow.emit(sdp)
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

    fun sendSDP(type: String, sdp: String, target: String) {
        coroutineScope.launch {
            stub.exchangeSDP(
                SessionDescription.newBuilder()
                    .setSdp(sdp)
                    .setType(type)
                    .setReceiver(target)
                    .build()
            )
            Timber.i("Sent SDP $type to $target")
        }
    }

    private fun listenForIceCandidates() {
        coroutineScope.launch {
            try {
                stub.sendIceCandidates(iceCandidatesFlow)
                    .collect { iceCandidatesMessage ->
                        Timber.i("Received ICE candidates from ${iceCandidatesMessage.sender}")
                        outgoingIceCandidatesFlow.emit(iceCandidatesMessage)
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error receiving ICE candidates")
            }
        }
    }

    suspend fun sendIceCandidate(candidate: IceCandidate, target: String) {
        val message = IceCandidatesMessage.newBuilder()
            .setReceiver(target)
            .addCandidates(candidate)
            .build()

        iceCandidatesFlow.emit(message)
    }

    fun observeIceCandidates(): SharedFlow<IceCandidatesMessage> = outgoingIceCandidatesFlow

    fun observeSDP(): SharedFlow<SessionDescription> = sdpFlow

    fun disconnect() {
        coroutineScope.launch {
            stub.userDisconnect(DisconnectRequest.newBuilder().setName(username).build())
            isConnected.set(false)
            channel.shutdown()
            Timber.i("Disconnected from signaling server")
        }
    }
}