package ru.drsn.waves.signaling

import io.grpc.ManagedChannelBuilder
import ru.drsn.waves.BuildConfig
import timber.log.Timber

class SignalingConnection (private val host: String, private val port: Int) {
    val channel = ManagedChannelBuilder
        .forAddress(host, port)
        .apply {
            if (BuildConfig.RELEASE) {
                useTransportSecurity() // ЕСЛИ РЕЛИЗ - TLS
            } else {
                usePlaintext() // ЕСЛИ DEBUG - плейн текст. убрать после полной настройки сервера
            }
        }
        .build()

    init {
        Timber.d("Created connection with $host:$port");
    }
    fun shutdown() {
        Timber.d("Завершаем gRPC соединение...")
        channel.shutdown()
    }
}