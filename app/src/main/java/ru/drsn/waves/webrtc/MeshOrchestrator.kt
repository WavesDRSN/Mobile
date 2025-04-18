package ru.drsn.waves.webrtc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.drsn.waves.signaling.SignalingService
import timber.log.Timber
import kotlinx.coroutines.delay

class MeshOrchestrator(
    private val signalingService: SignalingService,
    private val webRTCManager: WebRTCManager
) {

    private val orchestratorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectedPeers = mutableSetOf<String>()

    init {
        // Подписка на список пользователей и подключение к новым
        orchestratorScope.launch {
            signalingService.usersList.collectLatest { users ->
                val selfId = signalingService.userName
                val others = users.filter { it.name != selfId }

                Timber.d("MeshOrchestrator: Обнаружено ${others.size} других пользователей: $others")

                others.forEach { peerId ->
                    if (peerId.name !in connectedPeers) {
                        Timber.i("MeshOrchestrator: Подключаемся к новому peer ${peerId.name}")
                        connectToPeer(peerId.name)
                    }
                }
            }
        }

        // Подписка на ретрансляцию событий о новых пользователях
        orchestratorScope.launch {
            signalingService.newPeerEvent.collect { newPeer ->
                val selfId = signalingService.userName
//                if (newPeer.name != selfId && newPeer.name !in connectedPeers) {
//                    Timber.i("MeshOrchestrator: Получили ретрансляцию о новом peer ${newPeer.name}. Подключаемся.")
//                    connectToPeer(newPeer.name)
//                }

                // Ретранслируем нового пира всем остальным участникам
                connectedPeers.filter { it != newPeer.name }.forEach { knownPeer ->
                    Timber.d("MeshOrchestrator: Ретранслируем нового пира ${newPeer.name} для $knownPeer")
                    signalingService.relayNewPeer(knownPeer, newPeer.name)
                }

                // Сообщаем новому пиру о уже подключенных
                connectedPeers.forEach { knownPeerId ->
                    Timber.d("MeshOrchestrator: Сообщаем новому пиру ${newPeer.name} о уже подключённом $knownPeerId")
                    signalingService.relayNewPeer(newPeer.name, knownPeerId)
                }
            }
        }
    }

    private suspend fun connectToPeer(peerId: String) {
        // Проверка, подключены ли уже
        if (peerId in connectedPeers) {
            Timber.d("MeshOrchestrator: Уже подключены к $peerId")
            return
        }
        delay(1000)  // Задержка в 3 секунды (3000 миллисекунд)

        // Инициируем соединение
        Timber.i("MeshOrchestrator: Инициируем вызов к $peerId")
        webRTCManager.call(peerId)

        connectedPeers += peerId

        orchestratorScope.launch {
            // Сообщаем другим пирами о новом
            connectedPeers.filter { it != peerId }.forEach { knownPeer ->
                Timber.d("MeshOrchestrator: Ретранслируем нового пира $peerId для $knownPeer")
                signalingService.relayNewPeer(knownPeer, peerId)
            }

//            // Сообщаем новому пиру о других
//            val knownPeers = connectedPeers.filter { it != peerId }
//            knownPeers.forEach { knownPeerId ->
//                Timber.d("MeshOrchestrator: Сообщаем новому пиру $peerId о уже подключённом $knownPeerId")
//                signalingService.relayNewPeer(peerId, knownPeerId)
//            }
        }
    }

    fun close() {
        orchestratorScope.cancel()
        Timber.w("MeshOrchestrator: Остановлен и все задачи отменены.")
    }
}
