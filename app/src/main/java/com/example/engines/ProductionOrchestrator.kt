package com.example.engines

import android.content.Context
import android.util.Log
import com.example.ai.ActGenerationResult
import com.example.ai.NarrativeBrain
import com.example.ai.VisualDirectorBrain
import com.example.data.local.CreationLibraryRepository
import com.example.data.local.ProductionProjectEntity
import com.example.domain.model.ArtifactFormat
import com.example.domain.model.BookChapter
import com.example.domain.model.BrainTelemetry
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.DialogueLine
import com.example.domain.model.EngineId
import com.example.domain.model.EngineState
import com.example.domain.model.EngineTelemetry
import com.example.domain.model.ExportArtifact
import com.example.domain.model.OrchestratorStep
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionScript
import com.example.domain.model.ProductionStage
import com.example.domain.model.VideoResolution
import com.example.domain.model.VisualScene
import com.example.ui.viewmodel.Motor5Phase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext

private const val TAG = "ProductionOrchestrator"
private const val AUDIO_ENVELOPE_FILENAME = "audio_envelope.bin"

data class LiveProductionTelemetry(
    val activeProjectId: String? = null,
    val stage: ProductionStage = ProductionStage.IDEA,
    val progressPercent: Int = 0,
    val statusMessage: String = "Listo para crear",
    val logs: List<String> = emptyList(),
    val engines: List<EngineTelemetry> = EngineId.entries.map { EngineTelemetry(it) },
    val brains: BrainTelemetry = BrainTelemetry(),
    val activeEngineId: EngineId? = null,
    val lastCompletedStep: OrchestratorStep? = null,
    val failedEngineId: EngineId? = null,
    val failedStep: OrchestratorStep? = null,
    val pausedDueToError: Boolean = false,
    val memoryReleaseCount: Int = 0,
    val autoCleanedBytes: Long = 0L,
    val autoCleanedFiles: Int = 0
)

data class PhaseExecutionOutcome(
    val phase: Motor5Phase,
    val entity: ProductionProjectEntity,
    val completedSuccessfully: Boolean,
    val isTerminalForFormat: Boolean = false,
    val failedStep: OrchestratorStep? = null,
    val failedEngineId: EngineId? = null,
    val errorMessage: String? = null
)

/**
 * Abstraction of all engine operations invoked sequentially by Motor 5 (ProductionOrchestrator).
 * Default implementation delegates to the real AI Brains, Android TTS, DSP Audio Mixer,
 * MediaCodec MP4 Encoder, and PDF/HTML BookDocumentEngine.
 */
interface OrchestratorEngines {
    suspend fun generateStoryFoundation(
        ideaPrompt: String,
        genre: ContentGenre,
        outputFormat: OutputFormat,
        targetMinutes: Int
    ): ProductionScript

    suspend fun generateBookChapter(
        script: ProductionScript,
        genre: ContentGenre,
        chapterNumber: Int,
        chapterOutline: String,
        previousSummary: String
    ): BookChapter

    suspend fun generateActDialogues(
        script: ProductionScript,
        genre: ContentGenre,
        outputFormat: OutputFormat,
        actNumber: Int,
        actOutline: String,
        previousSummary: String,
        startingSceneNumber: Int,
        startingLineIndex: Int
    ): ActGenerationResult

    suspend fun exportDocuments(
        script: ProductionScript,
        outputDir: File
    ): List<ExportArtifact>

    suspend fun synthesizeVoices(
        lines: List<DialogueLine>,
        characters: List<CharacterProfile>,
        outputDir: File,
        onLineProgress: suspend (completedIndex: Int, total: Int, updatedLine: DialogueLine) -> Unit
    ): List<DialogueLine>

    suspend fun mixMasterWav(
        lines: List<DialogueLine>,
        characters: List<CharacterProfile>,
        outputWavFile: File,
        onProgress: suspend (percent: Int) -> Unit
    ): AudioMixResult

    suspend fun generateSceneImage(
        scene: VisualScene,
        characterVisualBible: String,
        outputFile: File
    ): File

    suspend fun renderMp4(
        script: ProductionScript,
        timedLines: List<DialogueLine>,
        scenes: List<VisualScene>,
        masterWavFile: File,
        envelope100ms: FloatArray,
        totalDurationMs: Long,
        requestedResolution: VideoResolution,
        capabilities: DeviceCapabilities,
        outputMp4File: File,
        customVideoPath: String? = null,
        onProgress: suspend (percent: Int, detail: String) -> Unit
    ): VideoRenderResult

    suspend fun inspectCapabilities(): DeviceCapabilities

    fun onStageMemoryReleased(step: OrchestratorStep)
}

private class RealOrchestratorEngines(
    private val context: Context
) : OrchestratorEngines {
    private val narrativeBrain = NarrativeBrain()
    private val visualBrain = VisualDirectorBrain()
    private val voiceEngine = VoiceAudioEngine(context)
    private val musicSfxEngine = MusicSfxEngine()
    private val videoEngine = VisualVideoEngine()
    private val bookEngine = BookDocumentEngine()

    override suspend fun generateStoryFoundation(
        ideaPrompt: String,
        genre: ContentGenre,
        outputFormat: OutputFormat,
        targetMinutes: Int
    ): ProductionScript = narrativeBrain.generateStoryFoundation(
        ideaPrompt = ideaPrompt,
        genre = genre,
        outputFormat = outputFormat,
        targetMinutes = targetMinutes
    )

    override suspend fun generateBookChapter(
        script: ProductionScript,
        genre: ContentGenre,
        chapterNumber: Int,
        chapterOutline: String,
        previousSummary: String
    ): BookChapter = narrativeBrain.generateBookChapter(
        script = script,
        genre = genre,
        chapterNumber = chapterNumber,
        chapterOutline = chapterOutline,
        previousSummary = previousSummary
    )

    override suspend fun generateActDialogues(
        script: ProductionScript,
        genre: ContentGenre,
        outputFormat: OutputFormat,
        actNumber: Int,
        actOutline: String,
        previousSummary: String,
        startingSceneNumber: Int,
        startingLineIndex: Int
    ): ActGenerationResult = narrativeBrain.generateActDialogues(
        script = script,
        genre = genre,
        outputFormat = outputFormat,
        actNumber = actNumber,
        actOutline = actOutline,
        previousSummary = previousSummary,
        startingSceneNumber = startingSceneNumber,
        startingLineIndex = startingLineIndex
    )

    override suspend fun exportDocuments(
        script: ProductionScript,
        outputDir: File
    ): List<ExportArtifact> = bookEngine.exportAllFormats(script, outputDir)

    override suspend fun synthesizeVoices(
        lines: List<DialogueLine>,
        characters: List<CharacterProfile>,
        outputDir: File,
        onLineProgress: suspend (completedIndex: Int, total: Int, updatedLine: DialogueLine) -> Unit
    ): List<DialogueLine> = voiceEngine.synthesizeAllLines(lines, characters, outputDir, onLineProgress)

    override suspend fun mixMasterWav(
        lines: List<DialogueLine>,
        characters: List<CharacterProfile>,
        outputWavFile: File,
        onProgress: suspend (percent: Int) -> Unit
    ): AudioMixResult = musicSfxEngine.mixMasterWav(lines, characters, outputWavFile, onProgress)

    override suspend fun generateSceneImage(
        scene: VisualScene,
        characterVisualBible: String,
        outputFile: File
    ): File = visualBrain.generateSceneImage(scene, characterVisualBible, outputFile)

    override suspend fun renderMp4(
        script: ProductionScript,
        timedLines: List<DialogueLine>,
        scenes: List<VisualScene>,
        masterWavFile: File,
        envelope100ms: FloatArray,
        totalDurationMs: Long,
        requestedResolution: VideoResolution,
        capabilities: DeviceCapabilities,
        outputMp4File: File,
        customVideoPath: String?,
        onProgress: suspend (percent: Int, detail: String) -> Unit
    ): VideoRenderResult = videoEngine.renderMp4(
        script = script,
        timedLines = timedLines,
        scenes = scenes,
        masterWavFile = masterWavFile,
        envelope100ms = envelope100ms,
        totalDurationMs = totalDurationMs,
        requestedResolution = requestedResolution,
        capabilities = capabilities,
        outputMp4File = outputMp4File,
        customVideoPath = customVideoPath,
        onProgress = onProgress
    )

    override suspend fun inspectCapabilities(): DeviceCapabilities = DeviceCapabilitiesInspector.inspect(context)

    override fun onStageMemoryReleased(step: OrchestratorStep) {
        if (step in setOf(
                OrchestratorStep.VOICES_WAV,
                OrchestratorStep.MUSIC_MIX,
                OrchestratorStep.SCENE_IMAGES,
                OrchestratorStep.VIDEO_RENDER
            )
        ) {
            System.gc()
        }
    }
}

private class StepExecutionFailure(
    val step: OrchestratorStep,
    cause: Throwable
) : RuntimeException(cause.localizedMessage ?: cause.message ?: "Error en ${step.displayName}", cause)

class ProductionOrchestrator(
    private val context: Context,
    private val repository: CreationLibraryRepository,
    private val engines: OrchestratorEngines = RealOrchestratorEngines(context)
) {
    // Rule 1: Strict sequential mutual exclusion across both orchestrator runs and individual heavy engines.
    private val orchestrationMutex = Mutex()
    private val heavyEngineMutex = Mutex()
    private val concurrentEngineCounter = AtomicInteger(0)

    private val _telemetry = MutableStateFlow(LiveProductionTelemetry())
    val telemetry: StateFlow<LiveProductionTelemetry> = _telemetry.asStateFlow()

    private fun updateEngine(id: EngineId, state: EngineState, detail: String) {
        val current = _telemetry.value
        val updatedList = current.engines.map {
            if (it.engineId == id) it.copy(state = state, detail = detail) else it
        }
        _telemetry.value = current.copy(engines = updatedList)
    }

    private fun addLog(message: String) {
        val current = _telemetry.value
        val updatedLogs = (current.logs + message).takeLast(50)
        _telemetry.value = current.copy(logs = updatedLogs)
    }

    private fun releaseStageMemory(step: OrchestratorStep) {
        engines.onStageMemoryReleased(step)
        val count = _telemetry.value.memoryReleaseCount + 1
        _telemetry.value = _telemetry.value.copy(
            activeEngineId = null,
            memoryReleaseCount = count
        )
        addLog("Memoria RAM liberada tras checkpoint de ${step.displayName}.")
    }

    private suspend fun runCheckpointCleanup(
        projectDir: File,
        completedStep: OrchestratorStep,
        format: OutputFormat
    ) {
        runCatching {
            repository.storageCleaner.cleanAfterCheckpoint(projectDir, completedStep, format)
        }.onSuccess { res ->
            if (res.deletedFilesCount > 0 || res.freedBytes > 0L) {
                val cur = _telemetry.value
                _telemetry.value = cur.copy(
                    autoCleanedFiles = cur.autoCleanedFiles + res.deletedFilesCount,
                    autoCleanedBytes = cur.autoCleanedBytes + res.freedBytes
                )
                addLog(
                    "Limpieza automática (${completedStep.displayName}): " +
                        "${res.deletedFilesCount} temporal(es) eliminados (${formatStorageSize(res.freedBytes)})."
                )
            }
        }.onFailure { err ->
            Log.w(TAG, "Non-fatal temp cleanup warning after ${completedStep.name}", err)
        }
    }

    private fun formatStorageSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }

    private suspend fun checkpoint(
        entity: ProductionProjectEntity,
        stage: ProductionStage,
        progress: Int,
        status: String,
        script: ProductionScript? = null,
        artifacts: List<ExportArtifact>? = null,
        lastCompletedStep: String = entity.lastCompletedStep,
        failedStep: String? = entity.failedStep,
        failedEngineId: String? = entity.failedEngineId,
        error: String? = null,
        syncCreations: Boolean = true
    ): ProductionProjectEntity {
        val updated = entity.copy(
            title = script?.title ?: entity.title,
            stageName = stage.name,
            progressPercent = progress.coerceIn(0, 100),
            statusMessage = status,
            errorMessage = error,
            scriptJson = script?.toJson() ?: entity.scriptJson,
            artifactsJson = artifacts?.let { ExportArtifact.listToJson(it) } ?: entity.artifactsJson,
            lastCompletedStep = lastCompletedStep,
            failedStep = failedStep,
            failedEngineId = failedEngineId
        )
        if (syncCreations) {
            repository.saveCheckpoint(updated, parsedScript = script)
        } else {
            repository.saveFastCheckpoint(updated)
        }
        _telemetry.value = _telemetry.value.copy(
            activeProjectId = updated.id,
            stage = stage,
            progressPercent = updated.progressPercent,
            statusMessage = status,
            lastCompletedStep = parseStep(updated.lastCompletedStep),
            failedEngineId = parseEngine(updated.failedEngineId),
            failedStep = parseStep(updated.failedStep),
            pausedDueToError = stage == ProductionStage.PAUSED && !updated.failedStep.isNullOrBlank()
        )
        return updated
    }

    private suspend fun <T> runSequentialEngineStep(
        step: OrchestratorStep,
        detail: String,
        block: suspend () -> T
    ): T {
        coroutineContext.ensureActive()
        return heavyEngineMutex.withLock {
            coroutineContext.ensureActive()
            val active = concurrentEngineCounter.incrementAndGet()
            if (active != 1) {
                concurrentEngineCounter.decrementAndGet()
                throw IllegalStateException(
                    "Ejecución paralela bloqueada: dos motores pesados intentaron correr al mismo tiempo."
                )
            }
            _telemetry.value = _telemetry.value.copy(activeEngineId = step.engineId)
            updateEngine(step.engineId, EngineState.RUNNING, detail)
            try {
                block()
            } catch (c: CancellationException) {
                throw c
            } catch (s: StepExecutionFailure) {
                throw s
            } catch (t: Throwable) {
                throw StepExecutionFailure(step, t)
            } finally {
                concurrentEngineCounter.decrementAndGet()
            }
        }
    }

    fun plannedPhasesFor(format: OutputFormat): List<Motor5Phase> = Motor5Phase.plannedFor(format)

    fun completedPhasesFor(entity: ProductionProjectEntity): Set<Motor5Phase> {
        val format = runCatching { OutputFormat.valueOf(entity.outputFormatName) }
            .getOrDefault(OutputFormat.AUDIO_VIDEO_EPISODE)
        val completed = mutableSetOf<Motor5Phase>()
        val lastStep = parseStep(entity.lastCompletedStep) ?: return emptySet()
        if (lastStep == OrchestratorStep.COMPLETED) {
            return Motor5Phase.plannedFor(format).toSet()
        }
        if (isStepCompleted(entity, format, Motor5Phase.WRITING.checkpointStep) ||
            (format == OutputFormat.BOOK_DOCUMENT && isStepCompleted(entity, format, OrchestratorStep.BOOK_EXPORT))
        ) {
            completed.add(Motor5Phase.WRITING)
        }
        if (format != OutputFormat.BOOK_DOCUMENT && isStepCompleted(entity, format, OrchestratorStep.VOICES_WAV)) {
            completed.add(Motor5Phase.VOICES)
        }
        if (format != OutputFormat.BOOK_DOCUMENT && isStepCompleted(entity, format, OrchestratorStep.MUSIC_MIX)) {
            completed.add(Motor5Phase.MUSIC)
        }
        if ((format == OutputFormat.AUDIO_VIDEO_EPISODE || format == OutputFormat.VIDEOPODCAST_40MIN) &&
            isStepCompleted(entity, format, OrchestratorStep.VIDEO_RENDER)
        ) {
            completed.add(Motor5Phase.VISUAL)
        }
        return completed
    }

    suspend fun prepareProductionRun(
        projectId: String,
        retryFailedOnly: Boolean = false
    ) = withContext(Dispatchers.Default) {
        orchestrationMutex.withLock {
            val entity = repository.getProject(projectId)
                ?: throw IllegalStateException("Proyecto no encontrado en almacenamiento local.")
            prepareTelemetryForRunUnlocked(entity, retryFailedOnly)
        }
    }

    private fun prepareTelemetryForRunUnlocked(
        entity: ProductionProjectEntity,
        retryFailedOnly: Boolean
    ) {
        val format = runCatching { OutputFormat.valueOf(entity.outputFormatName) }
            .getOrDefault(OutputFormat.AUDIO_VIDEO_EPISODE)
        val existingStage = runCatching { ProductionStage.valueOf(entity.stageName) }
            .getOrDefault(ProductionStage.IDEA)
        val previousFailedStep = parseStep(entity.failedStep)
        val resumePct = entity.progressPercent.coerceIn(0, 100)

        _telemetry.value = LiveProductionTelemetry(
            activeProjectId = entity.id,
            stage = if (existingStage == ProductionStage.PAUSED || existingStage == ProductionStage.FAILED) {
                parseStep(entity.lastCompletedStep)?.stage ?: ProductionStage.IDEA
            } else {
                existingStage
            },
            progressPercent = resumePct,
            statusMessage = if (retryFailedOnly && previousFailedStep != null) {
                "Reintentando únicamente ${previousFailedStep.engineId.displayName} desde ${resumePct}%..."
            } else if (resumePct > 2) {
                "Reanudando desde checkpoint exacto (${resumePct}%)..."
            } else {
                "Iniciando orquestador secuencial..."
            },
            logs = listOf(
                "Motor 5 · Proyecto: ${entity.title} · Formato: ${format.displayName} · Checkpoint: ${resumePct}%"
            ),
            lastCompletedStep = parseStep(entity.lastCompletedStep)
        )
        updateEngine(EngineId.AUTO_PIPELINE, EngineState.RUNNING, "Orquestador secuencial activo")

        val donePhases = completedPhasesFor(entity)
        if (Motor5Phase.WRITING in donePhases) {
            updateEngine(EngineId.WRITING, EngineState.COMPLETED, "En checkpoint Room")
            if (format != OutputFormat.BOOK_DOCUMENT) {
                updateEngine(EngineId.INTERVIEW, EngineState.COMPLETED, "En checkpoint Room")
            }
            updateEngine(EngineId.BOOKS_DOCS, EngineState.COMPLETED, "En checkpoint Room")
        }
        if (Motor5Phase.VOICES in donePhases) {
            updateEngine(EngineId.VOICES, EngineState.COMPLETED, "En checkpoint Room")
        }
        if (Motor5Phase.MUSIC in donePhases) {
            updateEngine(EngineId.MUSIC_SFX, EngineState.COMPLETED, "En checkpoint Room")
        }
        if (Motor5Phase.VISUAL in donePhases) {
            updateEngine(EngineId.VISUAL_VIDEO, EngineState.COMPLETED, "En checkpoint Room")
        }
    }

    /**
     * Executes a single Motor 5 phase ([Motor5Phase.WRITING], [Motor5Phase.VOICES],
     * [Motor5Phase.MUSIC], or [Motor5Phase.VISUAL]) strictly sequentially and saves
     * all intermediate and terminal checkpoints to Room Database.
     */
    suspend fun executeMotor5Phase(
        projectId: String,
        phase: Motor5Phase,
        retryFailedOnly: Boolean = false
    ): PhaseExecutionOutcome = withContext(Dispatchers.Default) {
        orchestrationMutex.withLock {
            if (_telemetry.value.activeProjectId != projectId) {
                val initial = repository.getProject(projectId)
                    ?: throw IllegalStateException("Proyecto no encontrado en almacenamiento local.")
                prepareTelemetryForRunUnlocked(initial, retryFailedOnly)
            }
            executeSinglePhaseUnlocked(projectId, phase)
        }
    }

    /**
     * Runs or resumes a complete production strictly sequentially from its exact saved checkpoint.
     * On any engine error or connectivity failure, pauses the workflow automatically while preserving
     * all prior checkpoints and recording which exact engine failed so only that step can be retried.
     */
    suspend fun runOrResumeProduction(
        projectId: String,
        retryFailedOnly: Boolean = false
    ): ProductionProjectEntity = withContext(Dispatchers.Default) {
        orchestrationMutex.withLock {
            val initialEntity = repository.getProject(projectId)
                ?: throw IllegalStateException("Proyecto no encontrado en almacenamiento local.")
            val format = runCatching { OutputFormat.valueOf(initialEntity.outputFormatName) }
                .getOrDefault(OutputFormat.AUDIO_VIDEO_EPISODE)
            prepareTelemetryForRunUnlocked(initialEntity, retryFailedOnly)

            var latestEntity = initialEntity
            for (phase in plannedPhasesFor(format)) {
                coroutineContext.ensureActive()
                val outcome = executeSinglePhaseUnlocked(projectId, phase)
                latestEntity = outcome.entity
                if (!outcome.completedSuccessfully || outcome.isTerminalForFormat) {
                    return@withLock latestEntity
                }
            }
            latestEntity
        }
    }

    private suspend fun executeSinglePhaseUnlocked(
        projectId: String,
        phase: Motor5Phase
    ): PhaseExecutionOutcome {
        var entity = repository.getProject(projectId)
            ?: throw IllegalStateException("Proyecto no encontrado en almacenamiento local.")

        val genre = runCatching { ContentGenre.valueOf(entity.genreName) }
            .getOrDefault(ContentGenre.INTERVIEW_CONFESSION)
        val format = runCatching { OutputFormat.valueOf(entity.outputFormatName) }
            .getOrDefault(OutputFormat.AUDIO_VIDEO_EPISODE)
        val resolution = runCatching { VideoResolution.valueOf(entity.resolutionName) }
            .getOrDefault(VideoResolution.FULL_HD_1080P)
        val projectDir = File(entity.directoryPath).apply { mkdirs() }
        val envelopeFile = File(projectDir, AUDIO_ENVELOPE_FILENAME)

        val previousFailedStep = parseStep(entity.failedStep)
        var script = ProductionScript.fromJson(entity.scriptJson)
        val artifacts = repository.verifyExistingArtifacts(entity.artifactsJson).toMutableList()
        var currentStepForFailure: OrchestratorStep = previousFailedStep ?: phase.checkpointStep

        try {
            if (!entity.failedStep.isNullOrBlank() || !entity.errorMessage.isNullOrBlank()) {
                entity = checkpoint(
                    entity = entity,
                    stage = _telemetry.value.stage,
                    progress = entity.progressPercent,
                    status = "Recuperando flujo en checkpoint ${entity.progressPercent}%...",
                    script = script,
                    artifacts = artifacts,
                    failedStep = null,
                    failedEngineId = null,
                    error = null
                )
            }

            return when (phase) {
                Motor5Phase.WRITING -> runWritingPhaseUnlocked(
                    entity = entity,
                    genre = genre,
                    format = format,
                    projectDir = projectDir,
                    initialScript = script,
                    artifacts = artifacts,
                    onCurrentStep = { currentStepForFailure = it }
                )
                Motor5Phase.VOICES -> runVoicesPhaseUnlocked(
                    entity = entity,
                    format = format,
                    projectDir = projectDir,
                    envelopeFile = envelopeFile,
                    initialScript = requireNotNull(script) { "El guion debe existir antes de sintetizar voces." },
                    artifacts = artifacts,
                    onCurrentStep = { currentStepForFailure = it }
                )
                Motor5Phase.MUSIC -> runMusicPhaseUnlocked(
                    entity = entity,
                    format = format,
                    projectDir = projectDir,
                    envelopeFile = envelopeFile,
                    initialScript = requireNotNull(script) { "El guion debe existir antes de mezclar música." },
                    artifacts = artifacts,
                    onCurrentStep = { currentStepForFailure = it }
                )
                Motor5Phase.VISUAL -> runVisualPhaseUnlocked(
                    entity = entity,
                    format = format,
                    resolution = resolution,
                    projectDir = projectDir,
                    envelopeFile = envelopeFile,
                    initialScript = requireNotNull(script) { "El guion debe existir antes del montaje visual." },
                    artifacts = artifacts,
                    onCurrentStep = { currentStepForFailure = it }
                )
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                updateEngine(EngineId.AUTO_PIPELINE, EngineState.IDLE, "Pausado por usuario (checkpoint guardado)")
                val latest = repository.getProject(projectId) ?: entity
                checkpoint(
                    entity = latest,
                    stage = ProductionStage.PAUSED,
                    progress = latest.progressPercent,
                    status = "Producción pausada en ${latest.progressPercent}% · Listo para reanudar",
                    script = null,
                    artifacts = artifacts
                )
            }
            throw e
        } catch (e: Exception) {
            val failedStep = (e as? StepExecutionFailure)?.step ?: currentStepForFailure
            val cause = (e as? StepExecutionFailure)?.cause ?: e
            val diagnostic = classifyEngineFailure(cause)
            Log.e(TAG, "Step ${failedStep.name} (${failedStep.engineId.displayName}) failed", cause)

            val latest = repository.getProject(projectId) ?: entity
            val preservedPct = latest.progressPercent.coerceIn(0, 99)
            val fullErrorMessage = "Falló ${failedStep.engineId.displayName} (${failedStep.displayName}): $diagnostic"

            updateEngine(failedStep.engineId, EngineState.ERROR, diagnostic)
            if (failedStep.engineId == EngineId.WRITING ||
                failedStep.engineId == EngineId.INTERVIEW ||
                failedStep.engineId == EngineId.BOOKS_DOCS
            ) {
                _telemetry.value = _telemetry.value.copy(
                    brains = _telemetry.value.brains.copy(
                        narrativeBrainStatus = "Gemini 3.8 Flash · Pausado (${diagnostic.take(64)})"
                    )
                )
            }
            updateEngine(
                EngineId.AUTO_PIPELINE,
                EngineState.IDLE,
                "Pausado automáticamente · Falló ${failedStep.engineId.displayName}"
            )
            addLog("Pausa automática al $preservedPct%: $fullErrorMessage")

            val pausedEntity = checkpoint(
                entity = latest,
                stage = ProductionStage.PAUSED,
                progress = preservedPct,
                status = "Pausado al $preservedPct% · Falló ${failedStep.engineId.displayName}",
                script = null,
                artifacts = artifacts,
                lastCompletedStep = latest.lastCompletedStep,
                failedStep = failedStep.name,
                failedEngineId = failedStep.engineId.name,
                error = fullErrorMessage
            )
            releaseStageMemory(failedStep)
            return PhaseExecutionOutcome(
                phase = phase,
                entity = pausedEntity,
                completedSuccessfully = false,
                isTerminalForFormat = false,
                failedStep = failedStep,
                failedEngineId = failedStep.engineId,
                errorMessage = fullErrorMessage
            )
        }
    }

    private suspend fun runWritingPhaseUnlocked(
        entity: ProductionProjectEntity,
        genre: ContentGenre,
        format: OutputFormat,
        projectDir: File,
        initialScript: ProductionScript?,
        artifacts: MutableList<ExportArtifact>,
        onCurrentStep: (OrchestratorStep) -> Unit
    ): PhaseExecutionOutcome {
        var currentEntity = entity
        var script = initialScript

        // ── STEP 1: STORY FOUNDATION (ENGINE 1: WRITING) ───────────────────────────
        val foundationDone = isStepCompleted(currentEntity, format, OrchestratorStep.WRITING_FOUNDATION) && script != null
        if (foundationDone) {
            updateEngine(EngineId.WRITING, EngineState.COMPLETED, "En checkpoint local (${script!!.totalPlannedActs} actos)")
            addLog("Checkpoint local: estructura base ya guardada, sin repetir llamada a API.")
        } else {
            onCurrentStep(OrchestratorStep.WRITING_FOUNDATION)
            script = runSequentialEngineStep(
                step = OrchestratorStep.WRITING_FOUNDATION,
                detail = "Diseñando historia y personajes"
            ) {
                _telemetry.value = _telemetry.value.copy(
                    stage = ProductionStage.SCRIPT,
                    brains = _telemetry.value.brains.copy(narrativeBrainStatus = "Gemini 3.8 Flash · Creando Biblia Narrativa"),
                    statusMessage = "Motor 1 · Cerebro Narrativo diseñando estructura y personajes..."
                )
                addLog("Motor 1: generando reparto, biblia visual y estructura...")

                val generatedRaw = engines.generateStoryFoundation(
                    ideaPrompt = currentEntity.ideaPrompt,
                    genre = genre,
                    outputFormat = format,
                    targetMinutes = currentEntity.targetMinDurationMinutes
                )
                val selectedCast = CharacterProfile.listFromJson(currentEntity.selectedCastJson)
                val mergedCharacters = mergeSelectedCast(generatedRaw.characters, selectedCast)
                generatedRaw.copy(characters = mergedCharacters)
            }

            val createdScript = requireNotNull(script)
            updateEngine(EngineId.WRITING, EngineState.COMPLETED, "${createdScript.totalPlannedActs} actos diseñados")
            currentEntity = checkpoint(
                entity = currentEntity,
                stage = ProductionStage.SCRIPT,
                progress = maxOf(currentEntity.progressPercent, 12),
                status = "Checkpoint 12%: estructura '${createdScript.title}' guardada",
                script = createdScript,
                lastCompletedStep = OrchestratorStep.WRITING_FOUNDATION.name
            )
            addLog("Checkpoint guardado (12%): \"${createdScript.title}\" (${createdScript.characters.size} personajes).")
            releaseStageMemory(OrchestratorStep.WRITING_FOUNDATION)
            runCheckpointCleanup(projectDir, OrchestratorStep.WRITING_FOUNDATION, format)
        }

        var activeScript = requireNotNull(script)

        // ── BRANCH A: BOOK / DOCUMENT / PRESENTATION ───────────────────────────────
        if (format == OutputFormat.BOOK_DOCUMENT) {
            updateEngine(EngineId.INTERVIEW, EngineState.SKIPPED, "No requerido en formato libro")
            updateEngine(EngineId.VOICES, EngineState.SKIPPED, "No requerido en formato libro")
            updateEngine(EngineId.MUSIC_SFX, EngineState.SKIPPED, "No requerido en formato libro")
            updateEngine(EngineId.VISUAL_VIDEO, EngineState.SKIPPED, "No requerido en formato libro")

            val chaptersDone = isStepCompleted(currentEntity, format, OrchestratorStep.BOOK_CHAPTERS) &&
                activeScript.chapters.size >= activeScript.totalPlannedActs

            if (chaptersDone) {
                updateEngine(EngineId.BOOKS_DOCS, EngineState.COMPLETED, "Capítulos en checkpoint local")
                addLog("Checkpoint local: ${activeScript.chapters.size} capítulos ya guardados.")
            } else {
                onCurrentStep(OrchestratorStep.BOOK_CHAPTERS)
                activeScript = runSequentialEngineStep(
                    step = OrchestratorStep.BOOK_CHAPTERS,
                    detail = "Redactando capítulos completos"
                ) {
                    val chapters = activeScript.chapters.toMutableList()
                    var workingScript = activeScript
                    var summary = chapters.lastOrNull()?.subtitle ?: ""

                    while (chapters.size < workingScript.totalPlannedActs) {
                        coroutineContext.ensureActive()
                        val chNum = chapters.size + 1
                        val outline = workingScript.actOutlines.getOrElse(chNum - 1) { "Capítulo $chNum" }
                        val pct = 15 + ((chNum * 65) / workingScript.totalPlannedActs)
                        _telemetry.value = _telemetry.value.copy(
                            stage = ProductionStage.SCRIPT,
                            statusMessage = "Redactando capítulo $chNum de ${workingScript.totalPlannedActs}..."
                        )
                        addLog("Motor 7: generando Capítulo $chNum (${outline.take(40)})...")
                        val chapter = engines.generateBookChapter(workingScript, genre, chNum, outline, summary)
                        chapters.add(chapter)
                        summary = chapter.title
                        workingScript = workingScript.copy(chapters = chapters.toList(), completedActs = chapters.size)
                        currentEntity = checkpoint(
                            entity = currentEntity,
                            stage = ProductionStage.SCRIPT,
                            progress = maxOf(currentEntity.progressPercent, pct),
                            status = "Checkpoint $pct%: Capítulo $chNum guardado",
                            script = workingScript,
                            syncCreations = false
                        )
                    }
                    workingScript
                }
                currentEntity = checkpoint(
                    entity = currentEntity,
                    stage = ProductionStage.SCRIPT,
                    progress = maxOf(currentEntity.progressPercent, 80),
                    status = "Checkpoint 80%: ${activeScript.chapters.size} capítulos redactados",
                    script = activeScript,
                    lastCompletedStep = OrchestratorStep.BOOK_CHAPTERS.name
                )
                releaseStageMemory(OrchestratorStep.BOOK_CHAPTERS)
                runCheckpointCleanup(projectDir, OrchestratorStep.BOOK_CHAPTERS, format)
            }

            val hasBookExports = isStepCompleted(currentEntity, format, OrchestratorStep.BOOK_EXPORT) &&
                artifacts.any { it.format == ArtifactFormat.PDF }
            if (!hasBookExports) {
                onCurrentStep(OrchestratorStep.BOOK_EXPORT)
                val exportedDocs = runSequentialEngineStep(
                    step = OrchestratorStep.BOOK_EXPORT,
                    detail = "Exportando PDF paginado, HTML, Markdown y TXT"
                ) {
                    _telemetry.value = _telemetry.value.copy(
                        stage = ProductionStage.BOOK_EXPORT,
                        statusMessage = "Exportando PDF paginado, HTML, Markdown y TXT..."
                    )
                    val docs = engines.exportDocuments(activeScript, File(projectDir, "exports"))
                    if (docs.isEmpty()) {
                        throw IllegalStateException("No se generó ningún archivo de documento en disco.")
                    }
                    docs
                }
                artifacts.clear()
                artifacts.addAll(exportedDocs)
                releaseStageMemory(OrchestratorStep.BOOK_EXPORT)
                runCheckpointCleanup(projectDir, OrchestratorStep.BOOK_EXPORT, format)
            }

            updateEngine(EngineId.BOOKS_DOCS, EngineState.COMPLETED, "${artifacts.size} formatos exportados")
            updateEngine(EngineId.AUTO_PIPELINE, EngineState.COMPLETED, "Libro y documentos terminados")
            _telemetry.value = _telemetry.value.copy(
                brains = _telemetry.value.brains.copy(narrativeBrainStatus = "Gemini 3.8 Flash · Completado")
            )

            val done = checkpoint(
                entity = currentEntity,
                stage = ProductionStage.COMPLETED,
                progress = 100,
                status = "Libro y documentos exportados (${artifacts.joinToString { it.format.extension.uppercase() }})",
                script = activeScript,
                artifacts = artifacts,
                lastCompletedStep = OrchestratorStep.COMPLETED.name,
                failedStep = null,
                failedEngineId = null,
                error = null
            )
            runCheckpointCleanup(projectDir, OrchestratorStep.COMPLETED, format)
            addLog("Producción documental completada y verificada en disco.")
            return PhaseExecutionOutcome(
                phase = Motor5Phase.WRITING,
                entity = done,
                completedSuccessfully = true,
                isTerminalForFormat = true
            )
        }

        // ── BRANCH B: DIALOGUES & SCENES SCRIPTING (ENGINE 2: INTERVIEW) ───────────
        val dialoguesDone = isStepCompleted(currentEntity, format, OrchestratorStep.DIALOGUE_ACTS) &&
            activeScript.completedActs >= activeScript.totalPlannedActs &&
            activeScript.lines.isNotEmpty()

        if (dialoguesDone) {
            updateEngine(EngineId.INTERVIEW, EngineState.COMPLETED, "En checkpoint local (${activeScript.lines.size} líneas)")
            addLog("Checkpoint local: ${activeScript.lines.size} diálogos y ${activeScript.scenes.size} escenas ya guardadas.")
        } else {
            onCurrentStep(OrchestratorStep.DIALOGUE_ACTS)
            val interviewDetail = if (genre == ContentGenre.INTERVIEW_CONFESSION || genre == ContentGenre.INTERROGATION_TESTIMONY) {
                "Construyendo entrevista / confesión"
            } else {
                "Estructurando diálogos dramáticos"
            }
            activeScript = runSequentialEngineStep(
                step = OrchestratorStep.DIALOGUE_ACTS,
                detail = interviewDetail
            ) {
                var workingScript = activeScript
                val allLines = workingScript.lines.toMutableList()
                val allScenes = workingScript.scenes.toMutableList()
                var completedActs = workingScript.completedActs
                var continuitySummary = allLines.takeLast(3).joinToString(" ") { "${it.speakerName}: ${it.text}" }

                while (completedActs < workingScript.totalPlannedActs) {
                    coroutineContext.ensureActive()
                    val actNum = completedActs + 1
                    val outline = workingScript.actOutlines.getOrElse(actNum - 1) { "Continuación narrativa acto $actNum" }
                    val pct = 14 + ((actNum * 22) / workingScript.totalPlannedActs)
                    _telemetry.value = _telemetry.value.copy(
                        stage = ProductionStage.DIALOGUES,
                        statusMessage = "Escribiendo diálogos y escenas · Acto $actNum/${workingScript.totalPlannedActs}"
                    )
                    addLog("Motor 2: generando Acto $actNum/${workingScript.totalPlannedActs}...")

                    val actResult = engines.generateActDialogues(
                        script = workingScript,
                        genre = genre,
                        outputFormat = format,
                        actNumber = actNum,
                        actOutline = outline,
                        previousSummary = continuitySummary,
                        startingSceneNumber = (allScenes.maxOfOrNull { it.sceneNumber } ?: 0) + 1,
                        startingLineIndex = allLines.size
                    )

                    allScenes.addAll(actResult.scenes)
                    allLines.addAll(actResult.lines)
                    completedActs++
                    continuitySummary = actResult.continuitySummary

                    workingScript = workingScript.copy(
                        lines = allLines.toList(),
                        scenes = allScenes.toList(),
                        completedActs = completedActs
                    )
                    currentEntity = checkpoint(
                        entity = currentEntity,
                        stage = ProductionStage.DIALOGUES,
                        progress = maxOf(currentEntity.progressPercent, pct),
                        status = "Checkpoint $pct%: Acto $actNum guardado (${allLines.size} diálogos)",
                        script = workingScript,
                        syncCreations = false
                    )
                }

                if (format == OutputFormat.VIDEOPODCAST_40MIN) {
                    val targetMs = currentEntity.targetMinDurationMinutes.coerceAtLeast(40) * 60_000L
                    var estMs = estimateLinesDurationMs(allLines)
                    var extensionCounter = 1
                    while (estMs < targetMs && extensionCounter <= 8) {
                        coroutineContext.ensureActive()
                        val actNum = completedActs + 1
                        _telemetry.value = _telemetry.value.copy(
                            stage = ProductionStage.DIALOGUES,
                            statusMessage = "Extendiendo videopodcast para alcanzar ${currentEntity.targetMinDurationMinutes} min reales..."
                        )
                        addLog("Ampliando guion (bloque adicional $extensionCounter) para cumplir mínimo 40 min...")
                        val extResult = engines.generateActDialogues(
                            script = workingScript,
                            genre = genre,
                            outputFormat = format,
                            actNumber = actNum,
                            actOutline = "Profundización extendida de testimonios, preguntas de seguimiento, detalles clave y revelación gradual (parte $extensionCounter)",
                            previousSummary = continuitySummary,
                            startingSceneNumber = (allScenes.maxOfOrNull { it.sceneNumber } ?: 0) + 1,
                            startingLineIndex = allLines.size
                        )
                        allScenes.addAll(extResult.scenes)
                        allLines.addAll(extResult.lines)
                        completedActs++
                        continuitySummary = extResult.continuitySummary
                        estMs = estimateLinesDurationMs(allLines)
                        extensionCounter++
                        workingScript = workingScript.copy(
                            totalPlannedActs = completedActs,
                            completedActs = completedActs,
                            lines = allLines.toList(),
                            scenes = allScenes.toList()
                        )
                        currentEntity = checkpoint(
                            entity = currentEntity,
                            stage = ProductionStage.DIALOGUES,
                            progress = maxOf(currentEntity.progressPercent, 38),
                            status = "Bloque extendido guardado (${allLines.size} líneas)",
                            script = workingScript,
                            syncCreations = false
                        )
                    }
                }
                workingScript
            }

            updateEngine(EngineId.INTERVIEW, EngineState.COMPLETED, "${activeScript.lines.size} diálogos diferenciados")
            currentEntity = checkpoint(
                entity = currentEntity,
                stage = ProductionStage.DIALOGUES,
                progress = maxOf(currentEntity.progressPercent, 38),
                status = "Checkpoint 38%: guion completo guardado",
                script = activeScript,
                lastCompletedStep = OrchestratorStep.DIALOGUE_ACTS.name
            )
            releaseStageMemory(OrchestratorStep.DIALOGUE_ACTS)
            runCheckpointCleanup(projectDir, OrchestratorStep.DIALOGUE_ACTS, format)
        }

        // ── STEP 3B: COMPANION SCRIPT DOCUMENTS (ENGINE 7: BOOKS_DOCS) ─────────────
        val hasScriptDocs = isStepCompleted(currentEntity, format, OrchestratorStep.SCRIPT_DOCS_EXPORT) &&
            artifacts.any { it.format == ArtifactFormat.PDF }
        if (hasScriptDocs) {
            updateEngine(EngineId.BOOKS_DOCS, EngineState.COMPLETED, "PDF/HTML del guion en checkpoint")
        } else {
            onCurrentStep(OrchestratorStep.SCRIPT_DOCS_EXPORT)
            val scriptDocs = runSequentialEngineStep(
                step = OrchestratorStep.SCRIPT_DOCS_EXPORT,
                detail = "Generando PDF y manuscrito del guion"
            ) {
                engines.exportDocuments(activeScript, File(projectDir, "exports"))
            }
            artifacts.removeAll { it.format in setOf(ArtifactFormat.PDF, ArtifactFormat.HTML, ArtifactFormat.MD, ArtifactFormat.TXT) }
            artifacts.addAll(scriptDocs)
            updateEngine(EngineId.BOOKS_DOCS, EngineState.COMPLETED, "Guion exportado en PDF/HTML/MD/TXT")
            currentEntity = checkpoint(
                entity = currentEntity,
                stage = ProductionStage.DIALOGUES,
                progress = maxOf(currentEntity.progressPercent, 40),
                status = "Checkpoint 40%: documentos del guion guardados",
                script = activeScript,
                artifacts = artifacts,
                lastCompletedStep = OrchestratorStep.SCRIPT_DOCS_EXPORT.name
            )
            releaseStageMemory(OrchestratorStep.SCRIPT_DOCS_EXPORT)
            runCheckpointCleanup(projectDir, OrchestratorStep.SCRIPT_DOCS_EXPORT, format)
        }

        return PhaseExecutionOutcome(
            phase = Motor5Phase.WRITING,
            entity = currentEntity,
            completedSuccessfully = true,
            isTerminalForFormat = false
        )
    }

    private suspend fun runVoicesPhaseUnlocked(
        entity: ProductionProjectEntity,
        format: OutputFormat,
        projectDir: File,
        envelopeFile: File,
        initialScript: ProductionScript,
        artifacts: MutableList<ExportArtifact>,
        onCurrentStep: (OrchestratorStep) -> Unit
    ): PhaseExecutionOutcome {
        if (format == OutputFormat.BOOK_DOCUMENT) {
            return PhaseExecutionOutcome(
                phase = Motor5Phase.VOICES,
                entity = entity,
                completedSuccessfully = true,
                isTerminalForFormat = false
            )
        }
        var currentEntity = entity
        var activeScript = initialScript

        val masterWavFile = File(projectDir, "exports/MOHANA_Audio_Maestro.wav")
        val existingWavArtifact = artifacts.firstOrNull { it.format == ArtifactFormat.WAV }
        val mp4File = File(projectDir, "exports/MOHANA_Video_Final.mp4")
        val mp4AlreadyDone = (format != OutputFormat.AUDIO_DRAMA_WAV) &&
            isStepCompleted(currentEntity, format, OrchestratorStep.VIDEO_RENDER) &&
            mp4File.exists() && mp4File.length() > 0L
        val musicMixDone = isStepCompleted(currentEntity, format, OrchestratorStep.MUSIC_MIX) &&
            masterWavFile.exists() && masterWavFile.length() > 44L &&
            existingWavArtifact != null &&
            (format == OutputFormat.AUDIO_DRAMA_WAV || envelopeFile.exists() || mp4AlreadyDone)

        val allWavsVerified = activeScript.lines.isNotEmpty() && activeScript.lines.all { line ->
            val p = line.synthesizedWavPath
            p != null && File(p).let { it.exists() && it.length() > 44L } && line.actualDurationMs > 0L
        }
        if ((isStepCompleted(currentEntity, format, OrchestratorStep.VOICES_WAV) && allWavsVerified) || musicMixDone || mp4AlreadyDone) {
            updateEngine(EngineId.VOICES, EngineState.COMPLETED, "En checkpoint local (${activeScript.lines.size} WAV)")
            addLog("Checkpoint local: síntesis vocal ya consolidada en proyecto sin repetir llamadas.")
        } else {
            onCurrentStep(OrchestratorStep.VOICES_WAV)
            val voicesDir = File(projectDir, "voices")
            val workingLines = activeScript.lines.toMutableList()
            val alreadyDoneWavs = workingLines.withIndex().count { (idx, line) ->
                val deterministicFile = File(voicesDir, "line_${idx.toString().padStart(4, '0')}.wav")
                val wavFile = line.synthesizedWavPath?.let { File(it) } ?: deterministicFile
                wavFile.exists() && wavFile.length() > 44L
            }
            val startVoicesPct = if (alreadyDoneWavs > 0) {
                maxOf(currentEntity.progressPercent, 42 + ((alreadyDoneWavs * 18) / workingLines.size.coerceAtLeast(1)))
            } else {
                currentEntity.progressPercent
            }
            currentEntity = checkpoint(
                entity = currentEntity,
                stage = ProductionStage.VOICES,
                progress = startVoicesPct,
                status = "Motor 3 · Sintetizando voces WAV ($alreadyDoneWavs/${workingLines.size} listas)...",
                script = activeScript,
                artifacts = artifacts,
                syncCreations = false
            )

            val synthesizedLines = runSequentialEngineStep(
                step = OrchestratorStep.VOICES_WAV,
                detail = "Sintetizando voces diferenciadas a WAV"
            ) {
                engines.synthesizeVoices(
                    lines = workingLines.toList(),
                    characters = activeScript.characters,
                    outputDir = voicesDir
                ) { doneCount, total, updatedLine ->
                    workingLines[doneCount - 1] = updatedLine
                    if (doneCount % 3 == 0 || doneCount == total) {
                        val pct = 42 + ((doneCount * 18) / total.coerceAtLeast(1))
                        val snapshot = activeScript.copy(lines = workingLines.toList())
                        activeScript = snapshot
                        currentEntity = checkpoint(
                            entity = currentEntity,
                            stage = ProductionStage.VOICES,
                            progress = maxOf(currentEntity.progressPercent, pct),
                            status = "Voces WAV: $doneCount / $total líneas sintetizadas",
                            script = snapshot,
                            artifacts = artifacts,
                            syncCreations = false
                        )
                    }
                }
            }
            activeScript = activeScript.copy(lines = synthesizedLines)
            updateEngine(EngineId.VOICES, EngineState.COMPLETED, "${synthesizedLines.size} pistas WAV listas")
            currentEntity = checkpoint(
                entity = currentEntity,
                stage = ProductionStage.VOICES,
                progress = maxOf(currentEntity.progressPercent, 60),
                status = "Checkpoint 60%: ${synthesizedLines.size} voces WAV guardadas",
                script = activeScript,
                artifacts = artifacts,
                lastCompletedStep = OrchestratorStep.VOICES_WAV.name
            )
            addLog("Checkpoint guardado (60%): síntesis vocal completada (${synthesizedLines.size} WAV).")
            releaseStageMemory(OrchestratorStep.VOICES_WAV)
            runCheckpointCleanup(projectDir, OrchestratorStep.VOICES_WAV, format)
        }

        return PhaseExecutionOutcome(
            phase = Motor5Phase.VOICES,
            entity = currentEntity,
            completedSuccessfully = true,
            isTerminalForFormat = false
        )
    }

    private suspend fun runMusicPhaseUnlocked(
        entity: ProductionProjectEntity,
        format: OutputFormat,
        projectDir: File,
        envelopeFile: File,
        initialScript: ProductionScript,
        artifacts: MutableList<ExportArtifact>,
        onCurrentStep: (OrchestratorStep) -> Unit
    ): PhaseExecutionOutcome {
        if (format == OutputFormat.BOOK_DOCUMENT) {
            return PhaseExecutionOutcome(
                phase = Motor5Phase.MUSIC,
                entity = entity,
                completedSuccessfully = true,
                isTerminalForFormat = false
            )
        }
        var currentEntity = entity
        var activeScript = initialScript

        val masterWavFile = File(projectDir, "exports/MOHANA_Audio_Maestro.wav")
        val existingWavArtifact = artifacts.firstOrNull { it.format == ArtifactFormat.WAV }
        val mp4File = File(projectDir, "exports/MOHANA_Video_Final.mp4")
        val mp4AlreadyDone = (format != OutputFormat.AUDIO_DRAMA_WAV) &&
            isStepCompleted(currentEntity, format, OrchestratorStep.VIDEO_RENDER) &&
            mp4File.exists() && mp4File.length() > 0L
        val musicMixDone = isStepCompleted(currentEntity, format, OrchestratorStep.MUSIC_MIX) &&
            masterWavFile.exists() && masterWavFile.length() > 44L &&
            existingWavArtifact != null &&
            (format == OutputFormat.AUDIO_DRAMA_WAV || envelopeFile.exists() || mp4AlreadyDone)

        if (musicMixDone) {
            val masterAudioDurationMs = existingWavArtifact?.durationMs
                ?.takeIf { it > 0L }
                ?: VoiceAudioEngine.readWavDurationMs(masterWavFile)
            updateEngine(EngineId.MUSIC_SFX, EngineState.COMPLETED, "WAV maestro en checkpoint (${masterAudioDurationMs / 1000L}s)")
            addLog("Checkpoint local: audio maestro WAV ya verificado en disco (${masterAudioDurationMs / 1000L}s).")
        } else {
            onCurrentStep(OrchestratorStep.MUSIC_MIX)
            _telemetry.value = _telemetry.value.copy(
                stage = ProductionStage.AUDIO_MIX,
                statusMessage = "Motor 6 · Mezclando música ambiental, efectos y voces WAV..."
            )

            val mixResult = runSequentialEngineStep(
                step = OrchestratorStep.MUSIC_MIX,
                detail = "Mezclando música ambiental y SFX"
            ) {
                engines.mixMasterWav(
                    lines = activeScript.lines,
                    characters = activeScript.characters,
                    outputWavFile = masterWavFile
                ) { pct ->
                    val globalPct = 62 + ((pct * 10) / 100)
                    _telemetry.value = _telemetry.value.copy(
                        progressPercent = maxOf(_telemetry.value.progressPercent, globalPct),
                        statusMessage = "Mezcla maestra WAV: $pct%"
                    )
                }
            }

            if (!mixResult.masterWavFile.exists() || mixResult.masterWavFile.length() <= 44L) {
                throw StepExecutionFailure(
                    OrchestratorStep.MUSIC_MIX,
                    IllegalStateException("Error al verificar el archivo WAV maestro en disco.")
                )
            }

            if (format != OutputFormat.AUDIO_DRAMA_WAV) {
                MusicSfxEngine.saveEnvelopeToFile(mixResult.envelope100ms, envelopeFile)
            }

            activeScript = activeScript.copy(lines = mixResult.timedLines)
            artifacts.removeAll { it.format == ArtifactFormat.WAV }
            artifacts.add(
                ExportArtifact(
                    format = ArtifactFormat.WAV,
                    filePath = mixResult.masterWavFile.absolutePath,
                    fileSizeBytes = mixResult.masterWavFile.length(),
                    durationMs = mixResult.totalDurationMs
                )
            )
            updateEngine(EngineId.MUSIC_SFX, EngineState.COMPLETED, "WAV maestro (${mixResult.totalDurationMs / 1000L}s)")
            currentEntity = checkpoint(
                entity = currentEntity,
                stage = ProductionStage.AUDIO_MIX,
                progress = maxOf(currentEntity.progressPercent, 72),
                status = "Checkpoint 72%: audio maestro WAV verificado",
                script = activeScript,
                artifacts = artifacts,
                lastCompletedStep = OrchestratorStep.MUSIC_MIX.name
            )
            addLog("Checkpoint guardado (72%): WAV maestro ${mixResult.masterWavFile.length() / 1024} KB · ${mixResult.totalDurationMs / 1000L}s.")
            releaseStageMemory(OrchestratorStep.MUSIC_MIX)
            runCheckpointCleanup(projectDir, OrchestratorStep.MUSIC_MIX, format)
        }

        if (format == OutputFormat.AUDIO_DRAMA_WAV) {
            updateEngine(EngineId.VISUAL_VIDEO, EngineState.SKIPPED, "Formato Audio Drama seleccionado")
            updateEngine(EngineId.AUTO_PIPELINE, EngineState.COMPLETED, "Audio Drama terminado")
            val doneDrama = checkpoint(
                entity = currentEntity,
                stage = ProductionStage.COMPLETED,
                progress = 100,
                status = "Audio Drama WAV y guion listos",
                script = activeScript,
                artifacts = artifacts,
                lastCompletedStep = OrchestratorStep.COMPLETED.name,
                failedStep = null,
                failedEngineId = null,
                error = null
            )
            runCheckpointCleanup(projectDir, OrchestratorStep.COMPLETED, format)
            return PhaseExecutionOutcome(
                phase = Motor5Phase.MUSIC,
                entity = doneDrama,
                completedSuccessfully = true,
                isTerminalForFormat = true
            )
        }

        return PhaseExecutionOutcome(
            phase = Motor5Phase.MUSIC,
            entity = currentEntity,
            completedSuccessfully = true,
            isTerminalForFormat = false
        )
    }

    private suspend fun runVisualPhaseUnlocked(
        entity: ProductionProjectEntity,
        format: OutputFormat,
        resolution: VideoResolution,
        projectDir: File,
        envelopeFile: File,
        initialScript: ProductionScript,
        artifacts: MutableList<ExportArtifact>,
        onCurrentStep: (OrchestratorStep) -> Unit
    ): PhaseExecutionOutcome {
        if (format == OutputFormat.BOOK_DOCUMENT || format == OutputFormat.AUDIO_DRAMA_WAV) {
            return PhaseExecutionOutcome(
                phase = Motor5Phase.VISUAL,
                entity = entity,
                completedSuccessfully = true,
                isTerminalForFormat = true
            )
        }
        var currentEntity = entity
        var activeScript = initialScript

        val masterWavFile = File(projectDir, "exports/MOHANA_Audio_Maestro.wav")
        val existingWavArtifact = artifacts.firstOrNull { it.format == ArtifactFormat.WAV }
        val masterAudioDurationMs = existingWavArtifact?.durationMs
            ?.takeIf { it > 0L }
            ?: VoiceAudioEngine.readWavDurationMs(masterWavFile)

        val mp4File = File(projectDir, "exports/MOHANA_Video_Final.mp4")
        val mp4AlreadyDone = isStepCompleted(currentEntity, format, OrchestratorStep.VIDEO_RENDER) &&
            mp4File.exists() && mp4File.length() > 0L

        // ── STEP 6: SCENE IMAGES (VISUAL DIRECTOR BRAIN -> ENGINE 4) ───────────────
        val customVideoFile = currentEntity.customVideoPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
        val scenesDir = File(projectDir, "scenes").apply { mkdirs() }
        val updatedScenes = activeScript.scenes.toMutableList()
        val allSceneImagesExist = updatedScenes.all { sc ->
            sc.imagePath?.let { File(it).let { f -> f.exists() && f.length() > 0L } } == true
        }

        if (customVideoFile != null) {
            _telemetry.value = _telemetry.value.copy(
                brains = _telemetry.value.brains.copy(visualBrainStatus = "Vídeo Propio · ${customVideoFile.name}")
            )
            addLog("Usando vídeo propio (${customVideoFile.name}) como fondo visual; omitiendo generación de escenas IA.")
            if (!isStepCompleted(currentEntity, format, OrchestratorStep.SCENE_IMAGES)) {
                currentEntity = checkpoint(
                    entity = currentEntity,
                    stage = ProductionStage.SCENES,
                    progress = maxOf(currentEntity.progressPercent, 85),
                    status = "Checkpoint 85%: vídeo propio listo para montaje MP4",
                    script = activeScript,
                    artifacts = artifacts,
                    lastCompletedStep = OrchestratorStep.SCENE_IMAGES.name
                )
                runCheckpointCleanup(projectDir, OrchestratorStep.SCENE_IMAGES, format)
            }
        } else if (currentEntity.useTypographyVisualFallback) {
            addLog("Modo explícito de Placas Tipográficas Cinematográficas activo para montaje MP4.")
            if (!isStepCompleted(currentEntity, format, OrchestratorStep.SCENE_IMAGES)) {
                currentEntity = checkpoint(
                    entity = currentEntity,
                    stage = ProductionStage.SCENES,
                    progress = maxOf(currentEntity.progressPercent, 85),
                    status = "Checkpoint 85%: placas tipográficas cinematográficas listas",
                    script = activeScript,
                    artifacts = artifacts,
                    lastCompletedStep = OrchestratorStep.SCENE_IMAGES.name
                )
                runCheckpointCleanup(projectDir, OrchestratorStep.SCENE_IMAGES, format)
            }
        } else if ((isStepCompleted(currentEntity, format, OrchestratorStep.SCENE_IMAGES) && allSceneImagesExist) || mp4AlreadyDone) {
            _telemetry.value = _telemetry.value.copy(
                brains = _telemetry.value.brains.copy(visualBrainStatus = "Nano Banana 2.1 · ${updatedScenes.size} escenas en checkpoint")
            )
            addLog("Checkpoint local: ${updatedScenes.size} escenas visuales ya consolidadas.")
        } else {
            onCurrentStep(OrchestratorStep.SCENE_IMAGES)
            _telemetry.value = _telemetry.value.copy(
                brains = _telemetry.value.brains.copy(visualBrainStatus = "Nano Banana 2.1 · Generando escenas")
            )
            runSequentialEngineStep(
                step = OrchestratorStep.SCENE_IMAGES,
                detail = "Generando escenas consistentes con IA"
            ) {
                for (i in updatedScenes.indices) {
                    coroutineContext.ensureActive()
                    val scene = updatedScenes[i]
                    val existingImg = scene.imagePath?.let { File(it) }
                    if (existingImg != null && existingImg.exists() && existingImg.length() > 0L) {
                        continue
                    }

                    val pct = 73 + (((i + 1) * 12) / updatedScenes.size.coerceAtLeast(1))
                    _telemetry.value = _telemetry.value.copy(
                        stage = ProductionStage.SCENES,
                        statusMessage = "Cerebro Visual creando escena ${i + 1}/${updatedScenes.size}..."
                    )
                    addLog("Motor 4: generando imagen IA para Escena ${scene.sceneNumber}: ${scene.title}")

                    val imgFile = File(scenesDir, "scene_${scene.sceneNumber.toString().padStart(3, '0')}.png")
                    try {
                        engines.generateSceneImage(
                            scene = scene,
                            characterVisualBible = activeScript.characterVisualBible,
                            outputFile = imgFile
                        )
                        updatedScenes[i] = scene.copy(imagePath = imgFile.absolutePath, imageErrorMessage = null)
                        activeScript = activeScript.copy(scenes = updatedScenes.toList())
                        currentEntity = checkpoint(
                            entity = currentEntity,
                            stage = ProductionStage.SCENES,
                            progress = maxOf(currentEntity.progressPercent, pct),
                            status = "Checkpoint $pct%: Escena ${i + 1} guardada",
                            script = activeScript,
                            artifacts = artifacts,
                            syncCreations = false
                        )
                    } catch (c: CancellationException) {
                        throw c
                    } catch (e: Exception) {
                        val reason = classifyEngineFailure(e)
                        updatedScenes[i] = scene.copy(imageErrorMessage = reason)
                        activeScript = activeScript.copy(scenes = updatedScenes.toList())
                        _telemetry.value = _telemetry.value.copy(
                            brains = _telemetry.value.brains.copy(visualBrainStatus = "Nano Banana 2.1 · Pausado por error")
                        )
                        currentEntity = checkpoint(
                            entity = currentEntity,
                            stage = ProductionStage.SCENES,
                            progress = currentEntity.progressPercent,
                            status = "Error en Escena ${scene.sceneNumber}: $reason",
                            script = activeScript,
                            artifacts = artifacts
                        )
                        throw StepExecutionFailure(
                            OrchestratorStep.SCENE_IMAGES,
                            IllegalStateException(
                                "Imagen IA falló en Escena ${scene.sceneNumber}: $reason. Puedes reintentar solo las escenas o activar Placas Tipográficas Cinematográficas."
                            )
                        )
                    }
                }
            }
            _telemetry.value = _telemetry.value.copy(
                brains = _telemetry.value.brains.copy(visualBrainStatus = "Nano Banana 2.1 · ${updatedScenes.size} escenas listas")
            )
            currentEntity = checkpoint(
                entity = currentEntity,
                stage = ProductionStage.SCENES,
                progress = maxOf(currentEntity.progressPercent, 85),
                status = "Checkpoint 85%: ${updatedScenes.size} escenas listas",
                script = activeScript,
                artifacts = artifacts,
                lastCompletedStep = OrchestratorStep.SCENE_IMAGES.name
            )
            releaseStageMemory(OrchestratorStep.SCENE_IMAGES)
            runCheckpointCleanup(projectDir, OrchestratorStep.SCENE_IMAGES, format)
        }

        // ── STEP 7: MP4 VIDEO RENDER (ENGINE 4: MEDIACODEC + MEDIAMUXER) ───────────
        if (!mp4AlreadyDone) {
            onCurrentStep(OrchestratorStep.VIDEO_RENDER)
            val capabilities = engines.inspectCapabilities()
            var renderEnvelope: FloatArray? = MusicSfxEngine.loadEnvelopeFromFile(envelopeFile)
                ?: FloatArray(10) { 0.25f }

            _telemetry.value = _telemetry.value.copy(
                stage = ProductionStage.VIDEO_MONTAGE,
                statusMessage = "Motor 4 · Renderizando video MP4 ${resolution.displayName} con subtítulos..."
            )
            addLog("Codificando MP4 (Codec AVC: ${capabilities.avcEncoderName})...")

            val videoResult = runSequentialEngineStep(
                step = OrchestratorStep.VIDEO_RENDER,
                detail = "Ensamblando video MP4 con subtítulos"
            ) {
                engines.renderMp4(
                    script = activeScript,
                    timedLines = activeScript.lines,
                    scenes = updatedScenes,
                    masterWavFile = masterWavFile,
                    envelope100ms = requireNotNull(renderEnvelope),
                    totalDurationMs = masterAudioDurationMs,
                    requestedResolution = resolution,
                    capabilities = capabilities,
                    outputMp4File = mp4File,
                    customVideoPath = customVideoFile?.absolutePath
                ) { pct, detail ->
                    val globalPct = 86 + ((pct * 13) / 100)
                    _telemetry.value = _telemetry.value.copy(
                        progressPercent = maxOf(_telemetry.value.progressPercent, globalPct.coerceAtMost(99)),
                        statusMessage = detail
                    )
                }
            }

            // Immediately drop large envelope array from RAM after MP4 render completes
            renderEnvelope = null

            artifacts.removeAll { it.format == ArtifactFormat.MP4 }
            artifacts.add(
                ExportArtifact(
                    format = ArtifactFormat.MP4,
                    filePath = videoResult.mp4File.absolutePath,
                    fileSizeBytes = videoResult.fileSizeBytes,
                    durationMs = videoResult.actualDurationMs,
                    resolutionLabel = "${videoResult.actualWidth}×${videoResult.actualHeight}"
                )
            )

            updateEngine(
                EngineId.VISUAL_VIDEO,
                EngineState.COMPLETED,
                "MP4 ${videoResult.actualWidth}×${videoResult.actualHeight} (${videoResult.fileSizeBytes / (1024 * 1024)} MB)"
            )
            releaseStageMemory(OrchestratorStep.VIDEO_RENDER)
            runCheckpointCleanup(projectDir, OrchestratorStep.VIDEO_RENDER, format)
        } else {
            updateEngine(EngineId.VISUAL_VIDEO, EngineState.COMPLETED, "MP4 ya verificado en checkpoint")
        }

        updateEngine(EngineId.AUTO_PIPELINE, EngineState.COMPLETED, "Producción final verificada en disco")
        _telemetry.value = _telemetry.value.copy(
            brains = _telemetry.value.brains.copy(narrativeBrainStatus = "Gemini 3.8 Flash · Completado")
        )

        val completedEntity = checkpoint(
            entity = currentEntity,
            stage = ProductionStage.COMPLETED,
            progress = 100,
            status = "Producción terminada y verificada en almacenamiento local",
            script = activeScript,
            artifacts = artifacts,
            lastCompletedStep = OrchestratorStep.COMPLETED.name,
            failedStep = null,
            failedEngineId = null,
            error = null
        )
        runCheckpointCleanup(projectDir, OrchestratorStep.COMPLETED, format)
        addLog("Producción completada al 100% y verificada en disco.")
        return PhaseExecutionOutcome(
            phase = Motor5Phase.VISUAL,
            entity = completedEntity,
            completedSuccessfully = true,
            isTerminalForFormat = true
        )
    }

    suspend fun enableTypographyVisualFallbackAndGetReady(projectId: String) {
        val entity = repository.getProject(projectId) ?: return
        repository.saveCheckpoint(
            entity.copy(
                useTypographyVisualFallback = true,
                stageName = ProductionStage.PAUSED.name,
                failedStep = OrchestratorStep.VIDEO_RENDER.name,
                failedEngineId = EngineId.VISUAL_VIDEO.name,
                errorMessage = null,
                statusMessage = "Placas Tipográficas Cinematográficas habilitadas; listo para renderizar MP4"
            )
        )
    }

    private fun orderedStepsFor(format: OutputFormat): List<OrchestratorStep> = when (format) {
        OutputFormat.BOOK_DOCUMENT -> listOf(
            OrchestratorStep.WRITING_FOUNDATION,
            OrchestratorStep.BOOK_CHAPTERS,
            OrchestratorStep.BOOK_EXPORT,
            OrchestratorStep.COMPLETED
        )
        OutputFormat.AUDIO_DRAMA_WAV -> listOf(
            OrchestratorStep.WRITING_FOUNDATION,
            OrchestratorStep.DIALOGUE_ACTS,
            OrchestratorStep.SCRIPT_DOCS_EXPORT,
            OrchestratorStep.VOICES_WAV,
            OrchestratorStep.MUSIC_MIX,
            OrchestratorStep.COMPLETED
        )
        OutputFormat.AUDIO_VIDEO_EPISODE, OutputFormat.VIDEOPODCAST_40MIN -> listOf(
            OrchestratorStep.WRITING_FOUNDATION,
            OrchestratorStep.DIALOGUE_ACTS,
            OrchestratorStep.SCRIPT_DOCS_EXPORT,
            OrchestratorStep.VOICES_WAV,
            OrchestratorStep.MUSIC_MIX,
            OrchestratorStep.SCENE_IMAGES,
            OrchestratorStep.VIDEO_RENDER,
            OrchestratorStep.COMPLETED
        )
    }

    private fun isStepCompleted(
        entity: ProductionProjectEntity,
        format: OutputFormat,
        step: OrchestratorStep
    ): Boolean {
        val completed = parseStep(entity.lastCompletedStep) ?: return false
        if (completed == OrchestratorStep.COMPLETED) return true
        val order = orderedStepsFor(format)
        val completedIdx = order.indexOf(completed)
        val targetIdx = order.indexOf(step)
        return completedIdx >= 0 && targetIdx >= 0 && completedIdx >= targetIdx
    }

    private fun parseStep(raw: String?): OrchestratorStep? {
        if (raw.isNullOrBlank()) return null
        return runCatching { OrchestratorStep.valueOf(raw) }.getOrNull()
    }

    private fun parseEngine(raw: String?): EngineId? {
        if (raw.isNullOrBlank()) return null
        return runCatching { EngineId.valueOf(raw) }.getOrNull()
    }

    private fun mergeSelectedCast(
        aiCharacters: List<CharacterProfile>,
        selectedCast: List<CharacterProfile>
    ): List<CharacterProfile> {
        if (selectedCast.isEmpty()) return aiCharacters
        val base = aiCharacters.mapIndexed { idx, aiChar ->
            val picked = selectedCast.getOrNull(idx)
            if (picked != null) {
                aiChar.copy(
                    name = picked.name.ifBlank { aiChar.name },
                    voicePitch = picked.voicePitch,
                    voiceRate = picked.voiceRate,
                    timbreFilter = picked.timbreFilter,
                    preferredVoiceName = picked.preferredVoiceName,
                    emotionPreset = picked.emotionPreset,
                    prosodyPacing = picked.prosodyPacing
                )
            } else {
                aiChar
            }
        }.toMutableList()
        if (selectedCast.size > base.size) {
            base.addAll(selectedCast.drop(base.size))
        }
        return base.toList()
    }

    private fun classifyEngineFailure(t: Throwable): String {
        val msg = t.localizedMessage ?: t.message ?: ""
        val causeMsg = t.cause?.localizedMessage ?: t.cause?.message ?: ""
        val combined = listOf(msg, causeMsg).filter { it.isNotBlank() }.distinct().joinToString(" · ")

        val isAppCheck = combined.contains("403", ignoreCase = true) ||
            combined.contains("app check", ignoreCase = true) ||
            combined.contains("attestation", ignoreCase = true) ||
            combined.contains("token is invalid", ignoreCase = true)

        if (isAppCheck) {
            return "Validación App Check rechazada (403). Si instalaste el APK en otro teléfono fuera de prueba, usa el APK Release o reinstala la última compilación ($combined)."
        }

        val isNetwork = t is UnknownHostException ||
            t is SocketTimeoutException ||
            t is ConnectException ||
            t is SSLException ||
            t.cause is UnknownHostException ||
            t.cause is SocketTimeoutException ||
            t.cause is ConnectException ||
            t is IOException && (
                combined.contains("network", ignoreCase = true) ||
                    combined.contains("timed out", ignoreCase = true) ||
                    combined.contains("unreachable", ignoreCase = true) ||
                    combined.contains("connection", ignoreCase = true)
                ) ||
            combined.contains("network", ignoreCase = true) ||
            combined.contains("timeout", ignoreCase = true) ||
            combined.contains("tiempo de espera", ignoreCase = true) ||
            combined.contains("unavailable", ignoreCase = true) ||
            combined.contains("conexión", ignoreCase = true) ||
            combined.contains("sin internet", ignoreCase = true)

        return if (isNetwork) {
            "Pérdida de conexión o tiempo de espera agotado (${combined.ifBlank { t.javaClass.simpleName }}). Tus pasos anteriores permanecen guardados."
        } else {
            combined.ifBlank { "Error inesperado (${t.javaClass.simpleName})." }
        }
    }

    private fun estimateLinesDurationMs(lines: List<DialogueLine>): Long {
        return lines.sumOf { line ->
            val words = line.text.split(Regex("\\s+")).count { it.isNotBlank() }
            (words * 425L) + line.pauseAfterMs
        }
    }
}
