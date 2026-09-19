package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class AuraPipelineResponse(
    @param:Json(name = "summary") val summary: String = "",
    @param:Json(name = "tasks") val tasks: List<TaskItem> = emptyList(),
    @param:Json(name = "draft_reply") val draftReply: String = ""
)

@JsonClass(generateAdapter = true)
data class TaskItem(
    @param:Json(name = "title") val title: String = "",
    @param:Json(name = "priority") val priority: String = "Medium",
    @param:Json(name = "deadline") val deadline: String = "None",
    @Transient val id: String = UUID.randomUUID().toString(),
    @Transient val isCompleted: Boolean = false
) {
    fun copyWithCompletion(completed: Boolean): TaskItem {
        return TaskItem(
            title = this.title,
            priority = this.priority,
            deadline = this.deadline,
            id = this.id,
            isCompleted = completed
        )
    }
}

enum class TaskPriority(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    companion object {
        fun fromString(value: String): TaskPriority {
            return when (value.trim().lowercase()) {
                "high", "p0", "p1", "urgent", "critical" -> HIGH
                "low", "p3", "p4", "minor" -> LOW
                else -> MEDIUM
            }
        }
    }
}
