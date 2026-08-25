package com.kairo.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Main Room database for Kairo AI Assistant.
 * Stores chat history, learned app flows, automation recipes, and draft history.
 */
@Database(
    entities = [
        ChatMessageEntity::class,
        AppKnowledgeEntity::class,
        AutomationRecipeEntity::class,
        DraftHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KairoDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun appKnowledgeDao(): AppKnowledgeDao
    abstract fun automationRecipeDao(): AutomationRecipeDao
    abstract fun draftHistoryDao(): DraftHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: KairoDatabase? = null

        fun getInstance(context: Context): KairoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KairoDatabase::class.java,
                    "kairo_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
