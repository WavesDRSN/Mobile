package ru.drsn.waves.signaling

import gRPC.v1.Signaling.*
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
import ru.drsn.waves.webrtc.contract.IWebRTCManager
import timber.log.Timber

class SignalingServiceImpl: SignalingService {

    lateinit var webRTCManager: IWebRTCManager

    private var signalingConnection: SafeSignalingConnection? = null
    private val _usersList = MutableStateFlow<List<User>>(emptyList()) // Внутренний StateFlow
    override val usersList: StateFlow<List<User>> = _usersList // Открытый StateFlow для подписки
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _newPeerEvent = MutableSharedFlow<User>(replay = 0) // Новый SharedFlow для ретрансляции событий
    override val newPeerEvent: SharedFlow<User> = _newPeerEvent // Открытый SharedFlow для подписки на новые подключения

    override var userName = ""

    // Это коллекция для пользователей, с которыми уже установлено соединение
    private val connectedPeers = mutableSetOf<String>()



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

    override fun updateUserList(users: List<User>) {
        val selfId = userName
        val previousUsers = _usersList.value
        val newUsers = users.filter { it.name != selfId }

        newUsers.forEach { newUser ->
            if (newUser !in previousUsers) {
                Timber.i("SignalingServiceImpl: Новый пользователь $newUser")
                // Генерируем событие о новом пировом подключении
                serviceScope.launch {
                    _newPeerEvent.emit(newUser)
                }
            }
        }

        _usersList.value = users
    }

    override suspend fun relayNewPeer(receiver: String, newPeerId: String) {
        // Формируем SDP-сообщение для нового пира (можно использовать любой формат, который вам удобен)
        val sdpMessage = newPeerId

        // Вызываем sendSDP для отправки сообщения как тип "new_peer"
        Timber.i("Sending new peer information to $receiver: $sdpMessage")

        // Отправляем это сообщение как обычный SDP, где type - это "new_peer", sdp - это сам новый peerId
        sendSDP("new_peer", sdpMessage, receiver)
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
                    "new_peer" -> {
                        // Обрабатываем новое подключение
                        Timber.i("New peer detected: ${sessionDescription.sdp}")
                        connectToNewPeer(sessionDescription.sdp)
                    }
                    else -> Timber.w("Received unknown SDP type: ${sessionDescription.type}")
                }
            }
        }
    }

    private fun connectToNewPeer(peerId: String) {
        // Логика подключения к новому пиру
        Timber.i("Connecting to new peer: $peerId")
        webRTCManager.call(peerId)
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