package ru.drsn.waves.webrtc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.launch
import ru.drsn.waves.signaling.SignalingService
import timber.log.Timber
import ru.drsn.waves.data.GroupStore

class MeshOrchestrator(
    private val signalingService: SignalingService,
    private val webRTCManager: WebRTCManager,
) {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val username = webRTCManager.username

    /**
     * Создать новую группу и отправить информацию всем пользователям.
     */
    fun createGroup(groupName: String) {
        // Создаем группу с одним пользователем (с собой)
        webRTCManager.addUserToGroup(groupName, username)

        // Отправляем всем пользователям информацию о группе
        serviceScope.launch {
            signalingService.usersList.value.forEach { user ->
                if (user.name != username) {
                    // Рассылаем информацию о группе всем, кроме себя
                    signalingService.sendSDP(
                        "group_info",
                        "$groupName:${setOf(username).joinToString()}",
                        user.name
                    )
                }
            }
        }

        Timber.i("Group $groupName created with participant: $username")
    }

    /**
     * Подключиться к группе, добавив себя и распространяя информацию.
     */
    fun joinGroup(groupName: String) {
        // Получаем список участников группы
        val members = webRTCManager.getGroupMembers(groupName)
        if (members != null) {
            // Подключаемся к каждому участнику группы
            members.forEach { member ->
                if (member != username) {
                    // Пытаемся подключиться к каждому участнику
                    Timber.i("Connecting to member $member in group $groupName")
                    webRTCManager.call(member)
                }
            }

            // После добавления себя:
            webRTCManager.addUserToGroup(groupName, username)

            // Новый список участников (включая себя)
            val updatedMembers = members.toMutableSet().apply { add(username) }

            // Рассылаем ВСЕМ участникам, включая новых
            serviceScope.launch {
                signalingService.usersList.value.forEach { user ->
                    if (user.name != username) {
                        signalingService.sendSDP("group_info",
                            webRTCManager.groupStore.getAllGroups().toString(), user.name)
                    }
                }
            }

            Timber.i("Joined group $groupName and added self to the member list.")
        } else {
            Timber.w("Group $groupName not found!")
        }
    }
}
