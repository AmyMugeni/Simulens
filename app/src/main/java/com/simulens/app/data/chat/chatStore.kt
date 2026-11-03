package com.simulens.app.data.chat

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.simulens.app.rag.Mode
import com.simulens.app.rag.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** A message as the UI shows it. */
data class ChatMessage(
    val id: Long = 0,
    val fromUser: Boolean,
    val text: String,
    val sources: List<Source> = emptyList(),
    val mode: Mode = Mode.ANSWER,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromUser: Boolean,
    val text: String,
    val mode: String,          // Mode.name
    val sourcesJson: String,   // [{"n":1,"title":"...","url":"..."}]
    val createdAt: Long
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM chat_messages WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

class ChatRepository(private val dao: ChatDao) {
    private val SIX_DAYS_MS = 6 * 24 * 60 * 60 * 1000L

    val messages: Flow<List<ChatMessage>> =
        dao.observeAll().map { list -> list.map { it.toMessage() } }

    suspend fun add(message: ChatMessage): Long {
        pruneOldMessages()
        return dao.insert(message.toEntity())
    }

    suspend fun deleteMessage(id: Long) = dao.deleteById(id)

    suspend fun pruneOldMessages() {
        val cutoff = System.currentTimeMillis() - SIX_DAYS_MS
        dao.deleteOlderThan(cutoff)
    }

    suspend fun clear() = dao.clear()
}

private fun ChatMessage.toEntity() = ChatMessageEntity(
    id = id,
    fromUser = fromUser,
    text = text,
    mode = mode.name,
    sourcesJson = JSONArray().also { arr ->
        sources.forEach { arr.put(JSONObject().put("n", it.n).put("title", it.title).put("url", it.url)) }
    }.toString(),
    createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis()
)

private fun ChatMessageEntity.toMessage(): ChatMessage {
    val arr = runCatching { JSONArray(sourcesJson) }.getOrDefault(JSONArray())
    val sources = (0 until arr.length()).mapNotNull { i ->
        runCatching {
            val s = arr.getJSONObject(i)
            Source(s.getInt("n"), s.getString("title"), s.getString("url"))
        }.getOrNull()
    }
    return ChatMessage(
        id = id,
        fromUser = fromUser,
        text = text,
        sources = sources,
        mode = runCatching { Mode.valueOf(mode) }.getOrDefault(Mode.ANSWER),
        createdAt = createdAt
    )
}
