package ru.drsn.waves.signaling

import com.google.protobuf.Timestamp
import gRPC.v1.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import ru.drsn.waves.BuildConfig
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class SignalingConnection(
    private val serverAddress: String,
    private val serverPort: Int,
    private val username: String
) {
    private val channel: ManagedChannel = ManagedChannelBuilder
        .forAddress(serverAddress, serverPort)
        .usePlaintext() // Отключает TLS, можно заменить на `useTransportSecurity()`
        .build()

    private val stub: UserConnectionGrpcKt.UserConnectionCoroutineStub =
        UserConnectionGrpcKt.UserConnectionCoroutineStub(channel)

    private val isConnected = AtomicBoolean(false)
    private val keepAliveIntervalFlow = MutableSharedFlow<Long>(replay = 1)

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        coroutineScope.launch {
            val requestFlow = channelFlow {
                send(UserConnectionRequest.newBuilder()
                    .setInitialRequest(
                        InitialUserConnectionRequest.newBuilder().setName(username).build()
                    )
                    .build()
                )

                keepAliveIntervalFlow.collect { interval ->
                    while (isConnected.get()) {
                        delay(interval)
                        send(UserConnectionRequest.newBuilder()
                            .setStillAlive(AlivePacket.newBuilder()
                                .setKissOfThePoseidon("some_value")
                                .setTimestamp(Timestamp.getDefaultInstance())
                                .build()
                            )
                            .build()
                        )
                    }
                }
            }

            // Слушаем ответы от сервера
            stub.loadUsersList(requestFlow).collect { response ->
                when {
                    response.hasInitialResponse() -> {
                        val interval = response.initialResponse.userKeepAliveInterval.seconds * 1000
                        keepAliveIntervalFlow.emit(interval)
                        isConnected.set(true)
                        Timber.i("Connected to signaling server as $username")
                    }
                    response.hasUsersList() -> {
                        val users = response.usersList.usersList
                        Timber.i("Active users: ${users.joinToString { it.name }}")
                    }
                }
            }
        }
    }

}