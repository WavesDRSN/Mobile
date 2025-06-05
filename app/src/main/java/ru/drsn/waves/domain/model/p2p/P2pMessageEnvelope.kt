package ru.drsn.waves.domain.model.p2p

import com.google.gson.annotations.SerializedName

/**
 * Базовый класс-конверт для всех P2P сообщений, передаваемых через WebRTC.
 * @param type Тип сообщения, см. [P2pMessageType].
 * @param payload Сериализованная в JSON строка, содержащая специфичные для типа данные.
 * @param messageId Уникальный идентификатор P2P сообщения (генерируется отправителем).
 * @param senderId Идентификатор (никнейм) отправителя P2P сообщения.
 * @param timestamp Временная метка отправки P2P сообщения.
 */
data class P2pMessageEnvelope(
    @SerializedName("id")
    val messageId: String, // Уникальный ID всего P2P-сообщения

    @SerializedName("sender_id")
    val senderId: String, // Никнейм отправителя P2P-сообщения

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("type")
    val type: P2pMessageType,

    @SerializedName("payload")
    val payload: String // JSON-строка с полезной нагрузкой
)