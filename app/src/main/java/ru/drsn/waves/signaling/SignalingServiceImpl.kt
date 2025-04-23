package ru.drsn.waves.signaling

import gRPC.v1.IceCandidate
import gRPC.v1.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription
import ru.drsn.waves.data.GroupStore
import ru.drsn.waves.webrtc.SdpObserver
import ru.drsn.waves.webrtc.WebRTCManager
import ru.drsn.waves.webrtc.contract.IWebRTCManager
import timber.log.Timber

class SignalingServiceImpl: SignalingService {

    lateinit var webRTCManager: IWebRTCManager

    private var signalingConnection: SafeSignalingConnection? = null
    private val _usersList = MutableStateFlow<List<User>>(emptyList()) // Внутренний StateFlow
    override val usersList: StateFlow<List<User>> = _usersList // Открытый StateFlow для подписки
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _newPeerEvent = MutableSharedFlow<User>(replay = 0) // Новый SharedFlow для ретрансляции событий

    override var userName = ""

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
                    // Отправляем событие для каждого нового пользователя
                    newList.filter { it.name != userName }.forEach { newPeer ->
                        _newPeerEvent.emit(newPeer)  // Передаем событие о новом пользователе
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
        if (sdp.isEmpty()) {
            Timber.e("Trying to send empty SDP!")
            return
        }
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
                    "group_info" -> {
                        // Обрабатываем информацию о группе
                        Timber.i("Received group info: ${sessionDescription.sdp}")
                        webRTCManager.handleGroupInfo(sessionDescription.sdp)
                    }
                    "group_info_request" -> {
                        // Send group info in response
                        val groupInfo = webRTCManager.groupStore.getAllGroups()
                        Timber.i("Send group: ${groupInfo.toString()}")
                        sendSDP("group_info", groupInfo.toString(), sessionDescription.sdp)
                    }
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