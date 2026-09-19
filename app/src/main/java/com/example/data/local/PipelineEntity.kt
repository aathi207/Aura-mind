package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pipeline_history")
data class PipelineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val inputSnippet: String,
    val hasImage: Boolean,
    val summary: String,
    val tasksJson: String,
    val draftReply: String,
    val rawJson: String
)
