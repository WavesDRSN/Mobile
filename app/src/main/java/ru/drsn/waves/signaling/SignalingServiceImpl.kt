package ru.drsn.waves.signaling

import com.google.protobuf.Empty
import gRPC.v1.IceCandidate
import gRPC.v1.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SignalingServiceImpl: SignalingService {

    private var signalingConnection: SafeSignalingConnection? = null
    private val _usersList = MutableStateFlow<List<User>>(emptyList()) // Внутренний StateFlow
    val usersList: StateFlow<List<User>> = _usersList // Открытый StateFlow для подписки
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var userName: String = ""

    override suspend fun connect(
        username: String,
        host: String,
        port: Int
    ) {

        signalingConnection = SafeSignalingConnection(SignalingConnection(host, port, username))
        signalingConnection!!.connect()

        userName = username

        signalingConnection!!.observeUsersList()
            .onEach { newList -> _usersList.value = newList }
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
}