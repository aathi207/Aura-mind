package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineDao {
    @Query("SELECT * FROM pipeline_history ORDER BY timestamp DESC")
    fun getAllPipelines(): Flow<List<PipelineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPipeline(entity: PipelineEntity): Long

    @Query("DELETE FROM pipeline_history WHERE id = :id")
    suspend fun deletePipelineById(id: Long)

    @Query("DELETE FROM pipeline_history")
    suspend fun clearAll()
}
