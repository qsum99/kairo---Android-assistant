package com.kairo.assistant.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int = 50): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesSnapshot(limit: Int = 10): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getCount(): Int
}

@Dao
interface AppKnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(knowledge: AppKnowledgeEntity): Long

    @Query("SELECT * FROM app_knowledge WHERE packageName = :packageName ORDER BY lastUsed DESC")
    suspend fun getKnowledgeForApp(packageName: String): List<AppKnowledgeEntity>

    @Query("SELECT * FROM app_knowledge WHERE taskDescription LIKE '%' || :query || '%' ORDER BY successCount DESC LIMIT 5")
    suspend fun searchKnowledge(query: String): List<AppKnowledgeEntity>

    @Query("UPDATE app_knowledge SET successCount = successCount + 1, lastUsed = :timestamp WHERE id = :id")
    suspend fun markSuccess(id: Long, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(knowledge: AppKnowledgeEntity)
}

@Dao
interface AutomationRecipeDao {
    @Insert
    suspend fun insert(recipe: AutomationRecipeEntity): Long

    @Update
    suspend fun update(recipe: AutomationRecipeEntity)

    @Delete
    suspend fun delete(recipe: AutomationRecipeEntity)

    @Query("SELECT * FROM automation_recipes WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun getEnabledRecipes(): Flow<List<AutomationRecipeEntity>>

    @Query("SELECT * FROM automation_recipes ORDER BY createdAt DESC")
    fun getAllRecipes(): Flow<List<AutomationRecipeEntity>>

    @Query("UPDATE automation_recipes SET lastTriggered = :timestamp WHERE id = :id")
    suspend fun markTriggered(id: Long, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface DraftHistoryDao {
    @Insert
    suspend fun insert(draft: DraftHistoryEntity): Long

    @Query("SELECT * FROM draft_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDrafts(limit: Int = 20): Flow<List<DraftHistoryEntity>>

    @Query("SELECT * FROM draft_history WHERE platform = :platform ORDER BY timestamp DESC LIMIT :limit")
    fun getDraftsByPlatform(platform: String, limit: Int = 10): Flow<List<DraftHistoryEntity>>

    @Delete
    suspend fun delete(draft: DraftHistoryEntity)

    @Query("DELETE FROM draft_history")
    suspend fun clearAll()
}
