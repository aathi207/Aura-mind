package com.example.data.repository

import com.example.data.local.PipelineDao
import com.example.data.local.PipelineEntity
import com.example.data.model.AuraPipelineResponse
import com.example.data.model.TaskItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

class PipelineRepository(private val dao: PipelineDao) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val tasksListType = Types.newParameterizedType(List::class.java, TaskItem::class.java)
    private val tasksAdapter = moshi.adapter<List<TaskItem>>(tasksListType)
    private val responseAdapter = moshi.adapter(AuraPipelineResponse::class.java)

    val pipelines: Flow<List<PipelineEntity>> = dao.getAllPipelines()

    suspend fun savePipeline(
        inputSnippet: String,
        hasImage: Boolean,
        response: AuraPipelineResponse,
        rawJson: String
    ): Long {
        val tasksJson = tasksAdapter.toJson(response.tasks)
        val entity = PipelineEntity(
            inputSnippet = inputSnippet.take(120),
            hasImage = hasImage,
            summary = response.summary,
            tasksJson = tasksJson,
            draftReply = response.draftReply,
            rawJson = rawJson
        )
        return dao.insertPipeline(entity)
    }

    suspend fun deletePipeline(id: Long) {
        dao.deletePipelineById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    fun deserializeTasks(tasksJson: String): List<TaskItem> {
        return try {
            tasksAdapter.fromJson(tasksJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseResponseJson(jsonString: String): AuraPipelineResponse? {
        return try {
            responseAdapter.fromJson(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    fun serializeResponse(response: AuraPipelineResponse): String {
        return responseAdapter.indent("  ").toJson(response)
    }
}
