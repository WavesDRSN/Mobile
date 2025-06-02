package ru.drsn.waves.domain.model.p2p

import com.google.gson.annotations.SerializedName
import ru.drsn.waves.domain.model.chat.MediaMetadata
import ru.drsn.waves.domain.model.chat.MessageType

/**
 * Полезная нагрузка для обычного сообщения чата.
 */
data class ChatMessagePayload(
    @SerializedName("chat_message_id")
    val chatMessageId: String, // Уникальный ID именно этого сообщения чата (может совпадать с P2pMessageEnvelope.id)

    @SerializedName("session_id") // ID чата (для P2P это ID получателя, для группы - ID группы)
    val sessionId: String,

    @SerializedName("text_content")
    val textContent: String?, // Текстовое содержимое (если это текстовое сообщение)

    @SerializedName("message_type")
    val messageType: MessageType, // TEXT, IMAGE, FILE и т.д.

    @SerializedName("media_meta")
    val mediaMetadata: EnhancedMediaMetadata? = null, // Метаданные для медиафайлов

    // Можно добавить другие поля, специфичные для чат-сообщений,
    // например, ID цитируемого сообщения, информация о форварде и т.д.
    @SerializedName("quoted_message_id")
    val quotedMessageId: String? = null
)