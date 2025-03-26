package ru.drsn.waves.signaling

import gRPC.v1.IceCandidate
import gRPC.v1.SessionDescription
import gRPC.v1.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.IOException

class SignalingServiceImpl : SignalingService {

    private var signalingConnection: SignalingConnection? = null

    override suspend fun connect(
        username: String,
        host: String,
        port: Int
    ) {
        signalingConnection = SignalingConnection(host, port, username)
        try {
            require(username.isNotBlank()) { "Имя пользователя не может быть пустым" }
            require(host.isNotBlank()) { "Адрес хоста не может быть пустым" }
            require(port in 1..65535) { "Некорректный порт: $port" }

            signalingConnection!!.connect()
        } catch (e: IOException) {
            throw RuntimeException("Не удалось подключиться: проблемы с сетью", e)
            //TODO: Change RuntimeException to SignalingException
        } catch (e: IllegalArgumentException) {
            Timber.e(e)
            throw RuntimeException("Bad configuration")
            //TODO: Change RuntimeException to SignalingException
        }
    }

    override suspend fun disconnect() {
        try {
            signalingConnection!!.disconnect()
            signalingConnection = null
        } catch (e: IOException) {
            throw RuntimeException("Не удалось подключиться: проблемы с сетью", e)
            //TODO: Change RuntimeException to SignalingException
        }
    }

    override suspend fun sendSDP(sessionDescription: SessionDescription) {
        TODO("Not yet implemented")
    }

    override fun observeSDP(): Flow<SessionDescription> {
        TODO("Not yet implemented")
    }

    override suspend fun sendIceCandidates(candidates: List<IceCandidate>) {
        TODO("Not yet implemented")
    }

    override fun observeIceCandidates(): Flow<List<IceCandidate>> {
        TODO("Not yet implemented")
    }

    override fun getUsersList(): StateFlow<List<User>> {
        TODO("Not yet implemented")
    }
}