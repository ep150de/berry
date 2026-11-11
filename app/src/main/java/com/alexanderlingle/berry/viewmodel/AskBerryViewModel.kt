package com.alexanderlingle.berry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexanderlingle.berry.ai.InferenceService
import com.alexanderlingle.berry.ai.ModelManager
import com.alexanderlingle.berry.ai.PromptBuilder
import com.alexanderlingle.berry.data.BerryRepository
import com.alexanderlingle.berry.data.PatternEngine
import com.alexanderlingle.berry.util.TimeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Everything Ask Berry shows, in one state object. */
sealed interface ModelStatus {
    /** Checking disk state and RAM on first open. */
    data object Checking : ModelStatus

    /** No model file yet — the screen offers the Wi-Fi download. */
    data object NotDownloaded : ModelStatus

    /** Download in progress, 0.0–1.0 (-1.0 when the length is unknown). */
    data class Downloading(val progress: Float) : ModelStatus

    /** Model on disk and engine ready to answer. */
    data object Ready : ModelStatus

    /** Phone cannot safely hold the model (see ModelManager.lowRamReason). */
    data class LowRam(val reason: String) : ModelStatus

    /** Model is on disk but the native engine library is missing. */
    data object EngineMissing : ModelStatus

    /** Something failed; [message] is shown verbatim (already human-worded). */
    data class Error(val message: String) : ModelStatus
}

data class AskBerryUiState(
    val status: ModelStatus = ModelStatus.Checking,
    val childName: String = "Baby",
    val suggestedQuestions: List<String> = emptyList(),
    /** Last question asked, shown above its answer. */
    val lastQuestion: String? = null,
    val answer: String? = null,
    val isGenerating: Boolean = false,
    /** True when the pattern engine had too little history for predictions. */
    val thinData: Boolean = true
)

/**
 * Berry v3 ViewModel. Flow per question:
 * build context from Room + PatternEngine (never raw logs) ->
 * ensure the model is loaded -> build the short prompt ->
 * generate on-device -> show the answer.
 *
 * Nothing here touches the network except [ModelManager.download], and
 * nothing here sends logs or prompts anywhere: inference is local.
 */
class AskBerryViewModel(
    private val repo: BerryRepository,
    private val modelManager: ModelManager,
    private val inference: InferenceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AskBerryUiState())
    val uiState: StateFlow<AskBerryUiState> = _uiState.asStateFlow()

    private var generateJob: Job? = null

    init {
        refreshStatus()
    }

    /** Re-checks disk/RAM/engine state and rebuilds suggested questions. */
    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = ModelStatus.Checking)
            modelManager.lowRamReason()?.let { reason ->
                _uiState.value = _uiState.value.copy(status = ModelStatus.LowRam(reason))
                return@launch
            }
            if (!modelManager.isDownloaded()) {
                _uiState.value = _uiState.value.copy(
                    status = ModelStatus.NotDownloaded,
                    suggestedQuestions = emptyList()
                )
                return@launch
            }
            if (!inference.isAvailable) {
                _uiState.value = _uiState.value.copy(status = ModelStatus.EngineMissing)
                return@launch
            }
            val context = buildContext()
            _uiState.value = _uiState.value.copy(
                status = ModelStatus.Ready,
                childName = context.childName,
                suggestedQuestions = PromptBuilder.suggestedQuestions(context),
                thinData = !context.hasPatterns
            )
        }
    }

    /** Starts (or resumes) the one-time model download. */
    fun downloadModel(allowCellular: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = ModelStatus.Downloading(0f))
            try {
                modelManager.download(allowCellular).collect { progress ->
                    _uiState.value = _uiState.value.copy(
                        status = ModelStatus.Downloading(progress)
                    )
                }
                refreshStatus()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = ModelStatus.Error(
                        e.message ?: "Model download failed. Check your connection and try again."
                    )
                )
            }
        }
    }

    /** Asks [question] and shows the on-device answer. Cancels any in-flight answer. */
    fun ask(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                lastQuestion = trimmed,
                answer = null
            )
            try {
                val context = buildContext()
                val prompt = PromptBuilder.buildPrompt(context, trimmed)
                val loadResult = inference.load(modelManager.modelFile.absolutePath)
                if (loadResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        answer = null,
                        status = ModelStatus.Error(
                            loadResult.exceptionOrNull()?.message
                                ?: "Could not load the model."
                        )
                    )
                    return@launch
                }
                val result = inference.generate(prompt)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    answer = result.getOrNull(),
                    status = if (result.isSuccess) {
                        _uiState.value.status
                    } else {
                        ModelStatus.Error(
                            result.exceptionOrNull()?.message ?: "The model could not answer."
                        )
                    }
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    status = ModelStatus.Error(e.message ?: "Something went wrong.")
                )
            }
        }
    }

    /** Deletes the downloaded model file (frees ~1.1 GB) and resets the screen. */
    fun deleteModel() {
        inference.unload()
        modelManager.deleteModel()
        refreshStatus()
    }

    /** Frees the model from RAM (e.g. when leaving the screen). */
    fun unloadModel() {
        inference.unload()
    }

    override fun onCleared() {
        super.onCleared()
        inference.unload()
    }

    /**
     * Aggregated context only: v2 predictions, today's totals, age band.
     * Raw log entries are read to compute these aggregates and are never
     * stored on the context object or passed to the model.
     */
    private suspend fun buildContext(): com.alexanderlingle.berry.ai.AssistantContext {
        // The selected-child flow starts at -1 until Room emits; wait briefly
        // for a real id instead of snapshotting the initial value. If no
        // child exists at all (fresh install before ChildViewModel runs),
        // fall back to the no-child context rather than hanging.
        val childId = withTimeoutOrNull(3000) {
            repo.selectedChildId.first { it != -1L }
        } ?: -1L
        val child = if (childId == -1L) {
            null
        } else {
            withTimeoutOrNull(3000) {
                repo.selectedChild.first { it?.id == childId }
            } ?: repo.selectedChild.value
        }
        val now = System.currentTimeMillis()
        if (childId == -1L) {
            return PromptBuilder.buildContext("Baby", null, emptyList(), 0, 0, 0, now)
        }
        val sleeps = repo.sleepEntries(childId).first()
        val feeds = repo.feedingEntries(childId).first()
        val diapers = repo.diaperEntries(childId).first()
        val dayStart = TimeUtils.startOfDayMillis(now)
        val dayEnd = TimeUtils.startOfNextDayMillis(now)
        var sleepMillis = 0L
        for (s in sleeps) {
            sleepMillis += TimeUtils.overlapMillis(
                s.startTimeMillis, s.endTimeMillis ?: now, dayStart, dayEnd
            )
        }
        return PromptBuilder.buildContext(
            childName = child?.name ?: "Baby",
            birthdateMillis = child?.birthdateMillis,
            predictions = PatternEngine.predictAll(
                sleeps, feeds, diapers, child?.birthdateMillis, now
            ),
            todaySleepMillis = sleepMillis,
            todayFeedCount = feeds.count { it.startTimeMillis in dayStart until dayEnd },
            todayDiaperCount = diapers.count { it.timeMillis in dayStart until dayEnd },
            now = now
        )
    }
}
