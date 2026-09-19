package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AuraMindDatabase
import com.example.data.local.PipelineEntity
import com.example.data.model.AuraPipelineResponse
import com.example.data.model.TaskItem
import com.example.data.remote.GeminiOrchestrator
import com.example.data.repository.PipelineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AuraPreset(
    val title: String,
    val iconEmoji: String,
    val content: String
)

data class AuraUiState(
    val inputText: String = "",
    val attachedBitmap: Bitmap? = null,
    val attachedUri: Uri? = null,
    val isAnalyzing: Boolean = false,
    val activePipeline: AuraPipelineResponse? = null,
    val activeTasks: List<TaskItem> = emptyList(),
    val rawJson: String = "",
    val isFallback: Boolean = false,
    val statusNote: String? = null,
    val activeFilter: String = "All",
    val snackbarMessage: String? = null,
    val showSavedSheet: Boolean = false,
    val showRawJsonSheet: Boolean = false,
    val showApiKeyDialog: Boolean = false
)

class AuraMindViewModel(application: Application) : AndroidViewModel(application) {

    private val orchestrator = GeminiOrchestrator()
    private val database = AuraMindDatabase.getDatabase(application)
    private val repository = PipelineRepository(database.pipelineDao())

    val savedPipelines: StateFlow<List<PipelineEntity>> = repository.pipelines
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(AuraUiState())
    val uiState: StateFlow<AuraUiState> = _uiState.asStateFlow()

    val presets = listOf(
        AuraPreset(
            title = "P0 Outage",
            iconEmoji = "🚨",
            content = "URGENT: Payments microservice crashed in production at 3:15 PM! Customers cannot checkout on iOS and Android. Backend team must rollback commit 4f8b2 immediately. Operations lead needs to alert Tier 1 support ASAP. Also prepare incident postmortem report by Friday 5 PM. Low priority: clean up staging log tables whenever."
        ),
        AuraPreset(
            title = "Launch Sprint",
            iconEmoji = "🚀",
            content = "Sprint planning brain-dump: Marketing landing page must go live by tomorrow 10 AM. Alice needs to fix mobile viewport cutoff on hero banner before EOD. Bob to review analytics tracking tag integration by Thursday 3 PM. We also need to send press release drafts to stakeholders. Minor: update footer copyright."
        ),
        AuraPreset(
            title = "Slack Dump",
            iconEmoji = "💬",
            content = "Quick recap from client sync with Apex Corp: they are concerned about database latency spikes during peak load. We must run benchmark load tests by Wednesday 2 PM. We also promised to email the updated security whitepaper by tomorrow. Can someone verify if SOC2 questionnaires were signed? Minor: order lunch for Monday's workshop."
        ),
        AuraPreset(
            title = "Client Bug",
            iconEmoji = "⚠️",
            content = "CRITICAL: Enterprise client reported data export CSV is corrupting UTF-8 characters on reports. Must reproduce and patch before 6 PM today. Need to draft customer advisory response immediately. Coordinate with QA for hotfix deployment."
        )
    )

    init {
        // Pre-fill with first preset so user gets immediate gratification upon first launch
        loadPreset(presets[0])
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun loadPreset(preset: AuraPreset) {
        _uiState.update {
            it.copy(
                inputText = preset.content,
                snackbarMessage = "Loaded preset: ${preset.title}"
            )
        }
    }

    fun setAttachedImage(bitmap: Bitmap?, uri: Uri?) {
        _uiState.update {
            it.copy(
                attachedBitmap = bitmap,
                attachedUri = uri,
                snackbarMessage = if (bitmap != null) "Visual artifact attached" else null
            )
        }
    }

    fun clearAttachedImage() {
        _uiState.update {
            it.copy(
                attachedBitmap = null,
                attachedUri = null,
                snackbarMessage = "Removed visual artifact"
            )
        }
    }

    fun clearCanvas() {
        _uiState.update {
            it.copy(
                inputText = "",
                attachedBitmap = null,
                attachedUri = null,
                activePipeline = null,
                activeTasks = emptyList(),
                rawJson = "",
                isFallback = false,
                statusNote = null,
                snackbarMessage = "Canvas reset"
            )
        }
    }

    fun setFilter(filter: String) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun setShowSavedSheet(show: Boolean) {
        _uiState.update { it.copy(showSavedSheet = show) }
    }

    fun setShowRawJsonSheet(show: Boolean) {
        _uiState.update { it.copy(showRawJsonSheet = show) }
    }

    fun setShowApiKeyDialog(show: Boolean) {
        _uiState.update { it.copy(showApiKeyDialog = show) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun orchestrate() {
        val currentState = _uiState.value
        if (currentState.inputText.isBlank() && currentState.attachedBitmap == null) {
            _uiState.update { it.copy(snackbarMessage = "Please enter text or attach an image to orchestrate") }
            return
        }

        _uiState.update { it.copy(isAnalyzing = true, statusNote = "Synthesizing workflow pipeline...") }

        viewModelScope.launch {
            try {
                val result = orchestrator.orchestrate(
                    currentState.inputText,
                    currentState.attachedBitmap
                )

                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        activePipeline = result.response,
                        activeTasks = result.response.tasks,
                        rawJson = result.rawJson,
                        isFallback = result.isFallback,
                        statusNote = result.note,
                        snackbarMessage = if (result.isFallback) "Pipeline synthesized via AuraMind Engine" else "Pipeline orchestrated via Gemini API"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        snackbarMessage = "Orchestration error: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleTask(taskId: String) {
        _uiState.update { state ->
            val updated = state.activeTasks.map {
                if (it.id == taskId) it.copyWithCompletion(!it.isCompleted) else it
            }
            state.copy(activeTasks = updated)
        }
    }

    fun addNewTask(title: String, priority: String, deadline: String) {
        if (title.isBlank()) return
        val newTask = TaskItem(
            title = title.trim(),
            priority = priority,
            deadline = if (deadline.isBlank()) "None" else deadline.trim(),
            id = UUID.randomUUID().toString(),
            isCompleted = false
        )
        _uiState.update { state ->
            val updatedTasks = state.activeTasks + newTask
            val updatedPipeline = state.activePipeline?.copy(tasks = updatedTasks)
                ?: AuraPipelineResponse(
                    summary = "Custom operational pipeline",
                    tasks = updatedTasks,
                    draftReply = "None"
                )
            val updatedRawJson = repository.serializeResponse(updatedPipeline)
            state.copy(
                activeTasks = updatedTasks,
                activePipeline = updatedPipeline,
                rawJson = updatedRawJson,
                snackbarMessage = "Task added to pipeline"
            )
        }
    }

    fun deleteTask(taskId: String) {
        _uiState.update { state ->
            val updatedTasks = state.activeTasks.filter { it.id != taskId }
            val updatedPipeline = state.activePipeline?.copy(tasks = updatedTasks)
            val updatedRawJson = updatedPipeline?.let { repository.serializeResponse(it) } ?: ""
            state.copy(
                activeTasks = updatedTasks,
                activePipeline = updatedPipeline,
                rawJson = updatedRawJson,
                snackbarMessage = "Task removed"
            )
        }
    }

    fun saveCurrentPipeline() {
        val state = _uiState.value
        val pipeline = state.activePipeline ?: return

        viewModelScope.launch {
            try {
                val inputSnippet = if (state.inputText.isNotBlank()) state.inputText else "Visual artifact input"
                repository.savePipeline(
                    inputSnippet = inputSnippet,
                    hasImage = state.attachedBitmap != null,
                    response = pipeline.copy(tasks = state.activeTasks),
                    rawJson = state.rawJson
                )
                _uiState.update { it.copy(snackbarMessage = "Workflow saved to Local History") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to save: ${e.message}") }
            }
        }
    }

    fun loadSavedPipeline(entity: PipelineEntity) {
        val tasks = repository.deserializeTasks(entity.tasksJson)
        val pipeline = AuraPipelineResponse(
            summary = entity.summary,
            tasks = tasks,
            draftReply = entity.draftReply
        )
        _uiState.update {
            it.copy(
                inputText = entity.inputSnippet,
                activePipeline = pipeline,
                activeTasks = tasks,
                rawJson = entity.rawJson,
                isFallback = false,
                statusNote = null,
                showSavedSheet = false,
                snackbarMessage = "Loaded pipeline from history"
            )
        }
    }

    fun deleteSavedPipeline(id: Long) {
        viewModelScope.launch {
            repository.deletePipeline(id)
            _uiState.update { it.copy(snackbarMessage = "Removed from history") }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
            _uiState.update { it.copy(snackbarMessage = "History cleared") }
        }
    }
}
