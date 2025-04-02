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
import timber.log.Timber

class SignalingServiceImpl: SignalingService {

    lateinit var webRTCManager: WebRTCManager

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
                    newList.forEach {
                        webRTCManager.getOrCreateConnection(it.name)
                    }
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
                Timber.d("received ${sessionDescription.type} from ${sessionDescription.sender}" +
                        "\n${sessionDescription.sdp}")

                val peerConnection = webRTCManager.getOrCreateConnection(sessionDescription.sender)

                val remoteSDP = SessionDescription(
                    if (sessionDescription.type == "offer") SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER,
                    sessionDescription.sdp
                )

                peerConnection.setRemoteDescription(object : SdpObserver() {
                    override fun onSetSuccess() {
                        Timber.d("Remote SDP set successfully for ${sessionDescription.sender}")

                        if (sessionDescription.type == "offer") {
                            webRTCManager.answer(sessionDescription.sender)
                        }
                    }

                    override fun onSetFailure(error: String?) {
                        Timber.e("Failed to set remote SDP: $error")
                    }
                }, remoteSDP)
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
                        val tmp = org.webrtc.IceCandidate(
                            candidate.sdpMid,
                            (candidate.sdpMLineIndex ?: 0),
                            candidate.candidate.toString()
                        )

                        webRTCManager.addIceCandidate(message.sender,
                            tmp
                        )

                        Timber.d(tmp.toString())
                    }
                }
            }
        }
    }
}