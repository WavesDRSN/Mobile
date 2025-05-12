package ru.drsn.waves.data.datasource.remote.grpc.signaling

import gRPC.v1.Signaling.ICEExchange
import gRPC.v1.Signaling.SDPExchange
import kotlinx.coroutines.flow.Flow
import ru.drsn.waves.domain.model.signaling.SignalingError
import ru.drsn.waves.domain.model.utils.Result


/**
 * Определяет контракт для взаимодействия с удаленным сервером сигнализации.
 * Этот слой отвечает за "сырое" gRPC взаимодействие.
 */
interface ISignalingRemoteDataSource {
    /**
     * Устанавливает соединение с сервером сигнализации и начинает прослушивание сообщений.
     * @param username Имя пользователя для этого клиента.
     * @param host Хост сервера.
     * @param port Порт сервера.
     * @return Flow, эмитящий "сырые" серверные события (gRPC ответы или статусы соединения).
     */
    fun connectAndObserve(
        username: String,
        host: String,
        port: Int
    ): Flow<SignalingServerEvent> // SignalingServerEvent будет оборачивать gRPC ответы или статусы

    /**
     * Отправляет SDP сообщение через gRPC.
     */
    suspend fun sendSdpToServer(sdpExchange: SDPExchange): Result<Unit, SignalingError>

    /**
     * Отправляет ICE кандидатов через gRPC.
     */
    suspend fun sendIceCandidatesToServer(iceExchange: ICEExchange): Result<Unit, SignalingError>

    /**
     * Отключается от сервера и освобождает ресурсы.
     */
    suspend fun disconnectFromServer(): Result<Unit, SignalingError>

    /**
     * Получает текущий ключ пользователя, предоставленный сервером.
     */
    fun getUserKey(): String?
}