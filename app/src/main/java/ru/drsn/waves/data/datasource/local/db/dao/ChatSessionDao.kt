package ru.drsn.waves.data.datasource.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.drsn.waves.data.datasource.local.db.entity.ChatSessionEntity

@Dao
interface ChatSessionDao {

    /**
     * Вставляет или обновляет (если существует) сессию чата.
     * Полезно, когда приходит новое сообщение и нужно обновить lastMessageTimestamp и т.д.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSession(session: ChatSessionEntity)

    /**
     * Вставляет или обновляет список сессий.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSessions(sessions: List<ChatSessionEntity>)

    /**
     * Получает все сессии чатов, отсортированные по времени последнего сообщения (новые сверху).
     * Возвращает Flow, что позволяет UI автоматически обновляться при изменениях.
     */
    @Query("SELECT * FROM chat_sessions ORDER BY last_message_timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    /**
     * Получает конкретную сессию чата по ее ID.
     */
    @Query("SELECT * FROM chat_sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    /**
     * Обновляет существующую сессию чата.
     * Используется, например, для изменения peerName, isArchived, isMuted.
     */
    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    /**
     * Увеличивает счетчик непрочитанных сообщений для указанной сессии.
     */
    @Query("UPDATE chat_sessions SET unread_messages_count = unread_messages_count + 1 WHERE session_id = :sessionId")
    suspend fun incrementUnreadCount(sessionId: String)

    /**
     * Сбрасывает счетчик непрочитанных сообщений для указанной сессии.
     */
    @Query("UPDATE chat_sessions SET unread_messages_count = 0 WHERE session_id = :sessionId")
    suspend fun resetUnreadCount(sessionId: String)

    /**
     * Обновляет информацию о последнем сообщении в сессии.
     */
    @Query("UPDATE chat_sessions SET last_message_id = :lastMessageId, last_message_timestamp = :timestamp WHERE session_id = :sessionId")
    suspend fun updateLastMessageInfo(sessionId: String, lastMessageId: String, timestamp: Long)


    /**
     * Удаляет указанную сессию чата.
     * Сообщения, связанные с этой сессией, также будут удалены из-за onDelete = ForeignKey.CASCADE.
     */
    @Delete
    suspend fun deleteSession(session: ChatSessionEntity)

    /**
     * Удаляет сессию чата по ее ID.
     */
    @Query("DELETE FROM chat_sessions WHERE session_id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    /**
     * Очищает все сессии чатов (и каскадно все сообщения).
     * Осторожно использовать!
     */
    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllSessions()
}