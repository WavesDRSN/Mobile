package ru.drsn.waves.signaling

import gRPC.v1.IceCandidate
import gRPC.v1.IceCandidatesMessage
import gRPC.v1.SessionDescription
import gRPC.v1.User
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

class SafeSignalingConnection(private val delegate: SignalingConnection) {

    suspend fun connect() {
        executeWithHandling {
            delegate.connect()
        }
    }

    suspend fun sendSDP(
        type: String,
        sdp: String,
        target: String
    ) = executeWithHandling { delegate.sendSDP(type, sdp, target) }

    suspend fun sendIceCandidates(iceCandidates: List<IceCandidate>, target: String) =
        executeWithHandling { delegate.sendIceCandidates(iceCandidates, target) }

    suspend fun disconnect() = executeWithHandling { delegate.disconnect() }

    fun observeSDP(): SharedFlow<SessionDescription> = delegate.observeSDP()

    fun observeIceCandidates(): SharedFlow<IceCandidatesMessage> = delegate.observeIceCandidates()

    fun observeUsersList(): StateFlow<List<User>> = delegate.observeUsersList()

    private suspend fun <T> executeWithHandling(action: suspend () -> T): T {
        return try {
            action()
        } catch (e: IOException) {
            throw RuntimeException("Ошибка сети", e)
        } catch (e: IllegalArgumentException) {
            throw RuntimeException("Ошибка gRPC", e)
        } catch (e: Exception) {
            throw RuntimeException("Неизвестная ошибка", e)
        } //TODO: switch RuntimeException to SignalingException
    }
}