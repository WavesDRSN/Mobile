package ru.drsn.waves.data.datasource.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.drsn.waves.data.datasource.local.db.entity.MessageEntity

@Dao
interface MessageDao {

    /**
     * Вставляет новое сообщение. Если сообщение с таким ID уже существует, оно будет заменено.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE) // REPLACE может быть полезен, если статусы приходят позже
    suspend fun insertMessage(message: MessageEntity)

    /**
     * Вставляет список сообщений.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * Получает сообщения для указанной сессии чата, отсортированные по времени (новые снизу),
     * с возможностью пагинации (загрузка порциями).
     * @param sessionId ID сессии чата.
     * @param limit Количество сообщений для загрузки.
     * @param offset Смещение (сколько сообщений пропустить с начала).
     * @return Список сообщений.
     */
    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesForSessionPaged(sessionId: String, limit: Int, offset: Int): List<MessageEntity>

    /**
     * Получает все сообщения для указанной сессии чата, отсортированные по времени (новые снизу).
     * Используется для получения полной истории или если пагинация не нужна.
     */
    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getAllMessagesForSessionStream(sessionId: String): Flow<List<MessageEntity>>


    /**
     * Получает конкретное сообщение по его ID.
     */
    @Query("SELECT * FROM messages WHERE message_id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    /**
     * Обновляет статус указанного сообщения.
     */
    @Query("UPDATE messages SET status = :newStatus WHERE message_id = :messageId")
    suspend fun updateMessageStatus(messageId: String, newStatus: String)

    /**
     * Обновляет локальный путь к медиафайлу (например, после загрузки).
     */
    @Query("UPDATE messages SET media_local_path = :localPath WHERE message_id = :messageId")
    suspend fun updateMediaLocalPath(messageId: String, localPath: String)


    /**
     * Удаляет указанное сообщение.
     */
    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    /**
     * Удаляет сообщение по его ID.
     */
    @Query("DELETE FROM messages WHERE message_id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    /**
     * Удаляет все сообщения в указанной сессии чата.
     * Вызывается автоматически, если сессия удаляется (из-за ForeignKey.CASCADE),
     * но может быть полезен для явной очистки чата без удаления самой сессии.
     */
    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteAllMessagesInSession(sessionId: String)

    /**
     * Очищает все сообщения из базы данных.
     * Осторожно использовать!
     */
    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}