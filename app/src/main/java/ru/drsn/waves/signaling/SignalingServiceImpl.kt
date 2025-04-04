package ru.drsn.waves.signaling

import gRPC.v1.IceCandidate
import gRPC.v1.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription
import ru.drsn.waves.webrtc.SdpObserver
import ru.drsn.waves.webrtc.WebRTCManager
import ru.drsn.waves.webrtc.contract.IWebRTCManager
import timber.log.Timber

class SignalingServiceImpl: SignalingService {

    lateinit var webRTCManager: IWebRTCManager

    private var signalingConnection: SafeSignalingConnection? = null
    private val _usersList = MutableStateFlow<List<User>>(emptyList()) // Внутренний StateFlow
    val usersList: StateFlow<List<User>> = _usersList // Открытый StateFlow для подписки
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var userName: String = ""

    override fun connect(
        username: String,
        host: String,
        port: Int
    ) {

        signalingConnection = SafeSignalingConnection(SignalingConnection(host, port, username))

        userName = username
        serviceScope.launch {
            signalingConnection!!.connect()

            signalingConnection!!.observeUsersList()
                .onEach { newList ->
                    _usersList.value = newList
                }
                .launchIn(serviceScope)

            observeSDP()
            observeIceCandidates()
        }
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
                Timber.d(
                    "received ${sessionDescription.type} from ${sessionDescription.sender}" +
                            "\n${sessionDescription.sdp}")
                when (sessionDescription.type.lowercase()) {
                    "offer" -> webRTCManager.handleRemoteOffer(sessionDescription.sender, sessionDescription.sdp)
                    "answer" -> webRTCManager.handleRemoteAnswer(sessionDescription.sender, sessionDescription.sdp)
                    else -> Timber.w("Received unknown SDP type: ${sessionDescription.type}")
                }
            }
        }
    }

    override suspend fun sendIceCandidates(candidates: List<IceCandidate>, target: String) {
        signalingConnection!!.sendIceCandidates(candidates, target)
    }

    override fun observeIceCandidates() {
        serviceScope.launch {
            signalingConnection!!.observeIceCandidates() // Flow<IceCandidatesMessage>
                .collect { message -> // message - это gRPC.v1.IceCandidatesMessage
                    // Важно: обрабатываем кандидатов только для себя!
                    if (message.receiver.equals(userName)) {
                        Timber.i("Received ${message.candidatesList.size} ICE candidate(s) from ${message.sender}")
                        message.candidatesList.forEach { grpcCandidate ->
                            // Передаем каждого кандидата в WebRTCManager
                            webRTCManager.handleRemoteCandidate(message.sender, grpcCandidate)
                        }
                    } else {
                        // Это сообщение не для нас, игнорируем (или логируем для отладки)
                        Timber.d("Ignoring ICE candidates message intended for ${message.receiver}")
                    }
                }
        }
    }
}