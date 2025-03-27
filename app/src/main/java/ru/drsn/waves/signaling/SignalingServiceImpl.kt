package ru.drsn.waves.signaling

import gRPC.v1.IceCandidate
import gRPC.v1.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SignalingServiceImpl(
    private var userName: String,
    ) : SignalingService {

    private var signalingConnection: SafeSignalingConnection? = null
    private var usersList: List<User> = emptyList();
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun connect(
        username: String,
        host: String,
        port: Int
    ) {
        signalingConnection = SafeSignalingConnection(SignalingConnection(host, port, username))
        signalingConnection!!.connect()

        signalingConnection!!.observeUsersList()
            .onEach { newList -> usersList = newList }
            .launchIn(serviceScope)

    }

    override suspend fun disconnect() {
        signalingConnection!!.disconnect()
        signalingConnection = null
    }

    override suspend fun sendSDP(type: String, sdp: String, target: String) {
        signalingConnection!!.sendSDP(type, sdp, target)
    }

    override fun observeSDP() {
        serviceScope.launch {
            signalingConnection!!.observeSDP().collect { sessionDescription ->
                when (sessionDescription.type) {
                    "offer" -> TODO("Логика WebRTC при получение запроса")
                    "answer" -> TODO("Логика WebRTC при получение ответа")
                }
            }
        }
    }
    override suspend fun sendIceCandidates(candidates: List<IceCandidate>, target: String) {
        signalingConnection!!.sendIceCandidates(candidates, target)
    }

    override fun observeIceCandidates() {
        CoroutineScope(Dispatchers.IO).launch {
            signalingConnection!!.observeIceCandidates().collect { message ->
                if (message.receiver == userName) {
                    TODO("WebRTC add candidate to message.sender")
                }
            }
        }
    }

    override fun getUsersList(): List<User> = usersList
}