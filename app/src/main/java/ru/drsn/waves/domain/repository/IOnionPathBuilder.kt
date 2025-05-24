package ru.drsn.waves.domain.repository

import ru.drsn.waves.domain.model.onion.OnionPacket
import ru.drsn.waves.domain.model.webrtc.PeerId

interface IOnionPathBuilder {
    /**
     * Строит OnionPacket для отправки через цепочку узлов.
     *
     * @param message айди отправителя сообщения.
     * @param message Финальное сообщение E2EE (в виде ByteArray).
     * @param path Список ID узлов от ПЕРВОГО RELAY до ПОЛУЧАТЕЛЯ.
     * @return OnionPacket, его payload отправляется первому узлу в path.
     */
    suspend fun buildOnionPacket(senderId: String, message: ByteArray, path: List<PeerId>): OnionPacket
}