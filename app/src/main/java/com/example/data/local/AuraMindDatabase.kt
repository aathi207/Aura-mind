package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PipelineEntity::class], version = 1, exportSchema = false)
abstract class AuraMindDatabase : RoomDatabase() {
    abstract fun pipelineDao(): PipelineDao

    companion object {
        @Volatile
        private var INSTANCE: AuraMindDatabase? = null

        fun getDatabase(context: Context): AuraMindDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AuraMindDatabase::class.java,
                    "auramind_database"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
