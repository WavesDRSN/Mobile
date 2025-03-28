package ru.drsn.waves.signaling

import gRPC.v1.*
import kotlinx.coroutines.flow.StateFlow

interface SignalingService {
    suspend fun connect(username: String, host: String, port: Int)  // Подключение к серверу

    suspend fun disconnect()  // Отключение от сервера

    suspend fun sendSDP(type: String, sdp: String, target: String)  // Отправка SDP

    fun observeSDP()  // Подписка на входящие SDP

    suspend fun sendIceCandidates(candidates: List<IceCandidate>, target: String)  // Отправка группы ICE-кандидатов

    fun observeIceCandidates()  // Подписка на ICE-кандидаты

}