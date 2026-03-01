package com.wechatmem.app.data.local.db

import androidx.room.*

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<ConversationEntity>

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConversationEntity)

    @Update
    suspend fun update(entity: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE conversations SET summary = :summary, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String, updatedAt: String)

    @Query("SELECT * FROM conversations")
    suspend fun getAll(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE created_at >= :startDate ORDER BY updated_at DESC")
    suspend fun getConversationsSince(startDate: String): List<ConversationEntity>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :convId ORDER BY sequence")
    suspend fun getByConversation(convId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?
}

@Dao
interface ArticleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ArticleEntity)

    @Query("SELECT * FROM articles ORDER BY created_at DESC")
    suspend fun getAll(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: String): ArticleEntity?

    @Query("UPDATE articles SET summary = :summary, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String, updatedAt: String)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversationTag(ct: ConversationTagEntity)

    @Delete
    suspend fun deleteConversationTag(ct: ConversationTagEntity)

    @Query("SELECT t.* FROM tags t INNER JOIN conversation_tags ct ON t.id = ct.tag_id WHERE ct.conversation_id = :convId ORDER BY t.name")
    suspend fun getTagsForConversation(convId: String): List<TagEntity>

    @Query("SELECT ct.conversation_id FROM conversation_tags ct WHERE ct.tag_id = :tagId")
    suspend fun getConversationIdsByTag(tagId: String): List<String>

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface VectorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vectors: List<VectorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vector: VectorEntity)

    @Query("SELECT * FROM vectors")
    suspend fun getAll(): List<VectorEntity>

    @Query("SELECT * FROM vectors WHERE conversation_id = :convId")
    suspend fun getByConversation(convId: String): List<VectorEntity>

    @Query("""
        SELECT v.* FROM vectors v
        INNER JOIN conversations c ON v.conversation_id = c.id
        WHERE c.created_at >= :startDate
    """)
    suspend fun getVectorsSince(startDate: String): List<VectorEntity>
}
