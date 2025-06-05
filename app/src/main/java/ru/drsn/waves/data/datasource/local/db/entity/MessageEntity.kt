package ru.drsn.waves.data.datasource.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE // При удалении сессии чата, удалить все сообщения
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["sender_id"]),
        Index(value = ["status"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: String, // Уникальный ID сообщения

    @ColumnInfo(name = "session_id")
    val sessionId: String, // ID чата, к которому принадлежит сообщение

    @ColumnInfo(name = "sender_id")
    val senderId: String, // ID отправителя (никнейм)

    @ColumnInfo(name = "content", typeAffinity = ColumnInfo.BLOB) // Храним как BLOB (ByteArray)
    val content: ByteArray, // ЗАШИФРОВАННОЕ и, возможно, сжатое тело сообщения

    @ColumnInfo(name = "timestamp")
    val timestamp: Long, // Время отправки/получения

    @ColumnInfo(name = "status") // "sending", "sent", "delivered", "read", "failed"
    val status: String,

    @ColumnInfo(name = "is_outgoing")
    val isOutgoing: Boolean, // true, если сообщение отправлено текущим пользователем

    @ColumnInfo(name = "message_type") // "text", "image", "video", "audio", "file", "system"
    val messageType: String,

    @ColumnInfo(name = "file_transfer_id", index = true)
    val fileTransferId: String? = null,

    // Поля для медиа (опционально, можно вынести в отдельную таблицу, если медиа много)
    @ColumnInfo(name = "media_local_path")
    val mediaLocalPath: String? = null, // Локальный путь к медиафайлу

    @ColumnInfo(name = "media_url")
    val mediaUrl: String? = null, // URL медиафайла на сервере

    @ColumnInfo(name = "media_mime_type")
    val mediaMimeType: String? = null,

    @ColumnInfo(name = "media_file_size")
    val mediaFileSize: Long? = null,

    @ColumnInfo(name = "media_file_name")
    val mediaFileName: String? = null, // Оригинальное имя файла

    @ColumnInfo(name = "thumbnail_local_path")
    val thumbnailLocalPath: String? = null, // Локальный путь к превью

    @ColumnInfo(name = "quoted_message_id")
    val quotedMessageId: String? = null // ID цитируемого сообщения (если есть)
) {
    // equals и hashCode нужны для корректной работы DiffUtil в RecyclerView.Adapter,
    // особенно если content (ByteArray) может меняться (например, при изменении статуса шифрования, хотя это редкость)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageEntity

        if (messageId != other.messageId) return false
        // Не сравниваем content по значению, так как это ByteArray и может быть большим/изменяться
        // Сравнение по остальным полям обычно достаточно для DiffUtil
        if (sessionId != other.sessionId) return false
        if (senderId != other.senderId) return false
        if (timestamp != other.timestamp) return false
        if (status != other.status) return false
        if (isOutgoing != other.isOutgoing) return false
        if (messageType != other.messageType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + isOutgoing.hashCode()
        result = 31 * result + messageType.hashCode()
        return result
    }
}
