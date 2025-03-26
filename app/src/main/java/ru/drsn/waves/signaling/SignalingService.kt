package ru.drsn.waves.signaling

import gRPC.v1.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SignalingService {
    suspend fun connect(username: String, host: String, port: Int)  // Подключение к серверу

    suspend fun disconnect()  // Отключение от сервера

    suspend fun sendSDP(sessionDescription: SessionDescription)  // Отправка SDP

    fun observeSDP(): Flow<SessionDescription>  // Подписка на входящие SDP

    suspend fun sendIceCandidates(candidates: List<IceCandidate>)  // Отправка группы ICE-кандидатов

    fun observeIceCandidates(): Flow<List<IceCandidate>>  // Подписка на ICE-кандидаты

    fun getUsersList(): StateFlow<List<User>>  // Получение списка активных пользователей
}