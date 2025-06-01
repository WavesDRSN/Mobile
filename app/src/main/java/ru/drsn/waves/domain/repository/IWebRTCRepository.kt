package ru.drsn.waves.domain.repository

import kotlinx.coroutines.flow.Flow
import org.webrtc.MediaConstraints
import ru.drsn.waves.domain.model.signaling.IceCandidateData
import ru.drsn.waves.domain.model.signaling.SdpData
import ru.drsn.waves.domain.model.utils.Result
import ru.drsn.waves.domain.model.webrtc.PeerId
import ru.drsn.waves.domain.model.webrtc.WebRTCError
import ru.drsn.waves.domain.model.webrtc.WebRTCEvent
import ru.drsn.waves.domain.model.webrtc.WebRTCMessage
import ru.drsn.waves.domain.model.webrtc.WebRTCSessionState


interface IWebRTCRepository {
    /**
     * Инициализирует WebRTC стек (если это необходимо сделать явно).
     * Обычно инициализация PeerConnectionFactory происходит при первом обращении.
     */
    suspend fun initialize(): Result<Unit, WebRTCError>

    /**
     * Инициирует звонок (создание оффера) указанному пиру.
     * Оффер будет отправлен через ISignalingRepository.
     * @param peerId Идентификатор целевого пира.
     */
    suspend fun initiateCall(peerId: PeerId): Result<Unit, WebRTCError>

    /**
     * Обрабатывает входящий SDP (offer или answer) от удаленного пира.
     * Если это offer, будет создан answer и отправлен через ISignalingRepository.
     * @param sdpData Данные SDP (тип, сам sdp, отправитель).
     */
    suspend fun handleRemoteSdp(sdpData: SdpData): Result<Unit, WebRTCError>

    /**
     * Обрабатывает входящий ICE кандидат от удаленного пира.
     * @param iceCandidateData Данные ICE кандидата (кандидат, отправитель).
     */
    suspend fun handleRemoteIceCandidate(iceCandidateData: IceCandidateData): Result<Unit, WebRTCError>

    /**
     * Отправляет сообщение указанному пиру через DataChannel.
     * @param message Сообщение для отправки.
     */
    suspend fun sendMessage(message: WebRTCMessage): Result<Unit, WebRTCError>

    /**
     * Закрывает соединение с указанным пиром.
     * @param peerId Идентификатор пира.
     */
    suspend fun closeConnection(peerId: PeerId): Result<Unit, WebRTCError>

    /**
     * Закрывает все активные WebRTC соединения.
     */
    suspend fun closeAllConnections(): Result<Unit, WebRTCError>

    /**
     * Предоставляет Flow для наблюдения за событиями WebRTC.
     */
    fun observeWebRTCEvents(): Flow<WebRTCEvent>

    /**
     * Получает текущее состояние сессии для указанного пира.
     * @param peerId Идентификатор пира.
     * @return WebRTCSessionState или null, если сессии нет.
     */
    fun getSessionState(peerId: PeerId): WebRTCSessionState? // Может быть suspend, если требует асинхронного доступа

    /**
     * Возвращает множество ID всех пиров, с которыми установлено или устанавливается соединение.
     */
    fun getActivePeers(): Set<PeerId>

    suspend fun startCall(remoteUserId: String): Result<Unit, WebRTCError>
    suspend fun onIncomingCall(offerSdp: String, fromUserId: String): Result<Unit, WebRTCError>
    suspend fun acceptCall(): Result<Unit, WebRTCError>
    suspend fun rejectCall(): Result<Unit, WebRTCError>
    suspend fun onAnswerReceived(answerSdp: String): Result<Unit, WebRTCError>
    suspend fun endCall(): Result<Unit, WebRTCError>

}
