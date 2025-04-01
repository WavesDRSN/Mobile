package ru.drsn.waves.signaling

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
import org.webrtc.SessionDescription
import ru.drsn.waves.webrtc.WebRTCManager
import timber.log.Timber

class SignalingServiceImpl(
    private val webRTCManager: WebRTCManager
): SignalingService {

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
                .onEach { newList -> _usersList.value = newList }
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
                Timber.d("received ${sessionDescription.type} from ${sessionDescription.sender}" +
                        "\n${sessionDescription.sdp}")

                when (sessionDescription.type) {
                    "offer" -> {
                        webRTCManager.username = userName

                        webRTCManager.onRemoteSessionReceived(userName, SessionDescription(SessionDescription.Type.OFFER, sessionDescription.sdp))
                        webRTCManager.answer(sessionDescription.sender)
                    }
                    "answer" -> {
                        webRTCManager.username = userName

                        webRTCManager.onRemoteSessionReceived(userName, SessionDescription(SessionDescription.Type.ANSWER, sessionDescription.sdp))
                    }
                }
            }
        }
    }
    override suspend fun sendIceCandidates(candidates: List<IceCandidate>, target: String) {
        signalingConnection!!.sendIceCandidates(candidates, target)
    }

    override fun observeIceCandidates() {
        serviceScope.launch {
            signalingConnection!!.observeIceCandidates().collect { message ->
                if (message.receiver == userName) {
                    Timber.d("${message.sender} sent ICE Candidates" +
                            "\n${message.candidatesList.joinToString("\n")}")
                    message.candidatesList.forEach { candidate ->
                        webRTCManager.addIceCandidate(userName,
                            org.webrtc.IceCandidate(
                                candidate.sdpMid,
                                candidate.sdpMLineIndex,
                                candidate.candidate
                            )
                        )
                    }
                }
            }
        }
    }
}