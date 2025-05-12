package ru.drsn.waves.data.datasource.remote.webrtc

import kotlinx.coroutines.flow.Flow
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import ru.drsn.waves.domain.model.webrtc.PeerId

interface IWebRTCController {
    /**
     * Инициализирует PeerConnectionFactory. Должен быть вызван один раз.
     */
    fun initializeFactory(): Boolean

    /**
     * Создает Offer для указанного пира.
     * Эмитит LocalSdpCreated с оффером через Flow событий.
     * @param peerId Идентификатор целевого пира.
     * @param sdpConstraints Ограничения для создания SDP.
     */
    suspend fun createOffer(peerId: PeerId, sdpConstraints: MediaConstraints)

    /**
     * Обрабатывает удаленный Offer и создает Answer.
     * Эмитит LocalSdpCreated с ансвером через Flow событий.
     * @param peerId Идентификатор пира, отправившего Offer.
     * @param remoteOffer SDP удаленного оффера.
     * @param sdpConstraints Ограничения для создания SDP.
     */
    suspend fun handleRemoteOfferAndCreateAnswer(peerId: PeerId, remoteOffer: SessionDescription, sdpConstraints: MediaConstraints)

    /**
     * Обрабатывает удаленный Answer.
     * @param peerId Идентификатор пира, отправившего Answer.
     * @param remoteAnswer SDP удаленного ансвера.
     */
    suspend fun handleRemoteAnswer(peerId: PeerId, remoteAnswer: SessionDescription)

    /**
     * Добавляет удаленный ICE кандидат для указанного пира.
     * @param peerId Идентификатор пира.
     * @param iceCandidate Удаленный ICE кандидат.
     */
    suspend fun addRemoteIceCandidate(peerId: PeerId, iceCandidate: IceCandidate)

    /**
     * Отправляет сообщение через DataChannel указанному пиру.
     * @param peerId Идентификатор целевого пира.
     * @param dataChannelLabel Метка канала данных (например, "chat").
     * @param message Данные для отправки.
     * @return true, если отправка была успешной (на уровне DataChannel API), иначе false.
     */
    suspend fun sendMessage(peerId: PeerId, dataChannelLabel: String, message: ByteArray): Boolean

    /**
     * Закрывает соединение с указанным пиром.
     * @param peerId Идентификатор пира.
     */
    suspend fun closeConnection(peerId: PeerId)

    /**
     * Закрывает все активные соединения и освобождает ресурсы PeerConnectionFactory.
     */
    suspend fun closeAllConnectionsAndReleaseFactory()

    /**
     * Предоставляет Flow для наблюдения за низкоуровневыми событиями WebRTC контроллера.
     */
    fun observeEvents(): Flow<WebRTCControllerEvent>

    /**
     * Возвращает текущую конфигурацию ICE серверов.
     */
    fun getIceServersConfiguration(): List<PeerConnection.IceServer>


    /**
     * Возвращает текущее состояние PeerConnection для указанного пира.
     * Может быть полезно для отладки или специфических проверок.
     */
    fun getPeerConnectionState(peerId: PeerId): PeerConnection.SignalingState?


    /**
     * Возвращает множество ID всех пиров, для которых существует PeerConnection.
     */
    fun getActivePeerIds(): Set<PeerId>
}
