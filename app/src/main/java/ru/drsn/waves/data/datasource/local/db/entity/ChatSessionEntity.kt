package ru.drsn.waves.data.datasource.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(
    tableName = "chat_sessions",
    indices = [Index(value = ["last_message_timestamp"])]
)
@TypeConverters(StringListConverter::class)
data class ChatSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "peer_name")
    var peerName: String,

    @ColumnInfo(name = "peer_avatar_url")
    var peerAvatarUrl: String? = null,

    @ColumnInfo(name = "peer_description")
    var peerDescription: String? = null,
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
    @ColumnInfo(name = "chat_type")
    val chatType: String,
    @ColumnInfo(name = "participant_ids")
    val participantIds: List<String> = emptyList()
)