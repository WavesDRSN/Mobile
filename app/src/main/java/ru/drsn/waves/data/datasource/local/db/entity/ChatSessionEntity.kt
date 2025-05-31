package ru.drsn.waves.data.datasource.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(
    tableName = "chat_sessions",
    indices = [Index(value = ["last_message_timestamp"])] // Индекс для сортировки по времени
)
@TypeConverters(StringListConverter::class) // Применяем конвертер для participant_ids
data class ChatSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String, // Уникальный ID чата (ID другого пользователя или ID группы)

    @ColumnInfo(name = "peer_name")
    val peerName: String, // Отображаемое имя собеседника или название группы

    @ColumnInfo(name = "peer_avatar_url")
    val peerAvatarUrl: String? = null,

    @ColumnInfo(name = "last_message_id")
    val lastMessageId: String? = null,

    @ColumnInfo(name = "last_message_timestamp")
    val lastMessageTimestamp: Long = 0L,

    @ColumnInfo(name = "unread_messages_count")
    val unreadMessagesCount: Int = 0,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "is_muted")
    val isMuted: Boolean = false,

    @ColumnInfo(name = "chat_type") // "peer" или "group"
    val chatType: String,

    @ColumnInfo(name = "participant_ids") // Список ID участников (для групповых чатов)
    val participantIds: List<String> = emptyList() // Для личного чата здесь будет один ID
)
