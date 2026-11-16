package com.example.ui.viewmodel

import com.example.domain.model.EngineId
import com.example.domain.model.OrchestratorStep
import com.example.domain.model.OutputFormat

/**
 * Sequential phases orchestrated by Motor 5 inside [MohanaViewModel].
 * Every phase runs strictly one at a time and persists a verified checkpoint
 * in Room Database upon completion before advancing to the next phase.
 */
enum class Motor5Phase(
    val phaseNumber: Int,
    val label: String,
    val description: String,
    val primaryEngine: EngineId,
    val checkpointStep: OrchestratorStep
) {
    WRITING(
        phaseNumber = 1,
        label = "Escritura",
        description = "Biblia narrativa, diálogos/capítulos y documentos",
        primaryEngine = EngineId.WRITING,
        checkpointStep = OrchestratorStep.SCRIPT_DOCS_EXPORT
    ),
    VOICES(
        phaseNumber = 2,
        label = "Voces",
        description = "Síntesis vocal natural por personaje en WAV",
        primaryEngine = EngineId.VOICES,
        checkpointStep = OrchestratorStep.VOICES_WAV
    ),
    MUSIC(
        phaseNumber = 3,
        label = "Música",
        description = "Atmósfera DSP, efectos y ducking en WAV maestro",
        primaryEngine = EngineId.MUSIC_SFX,
        checkpointStep = OrchestratorStep.MUSIC_MIX
    ),
    VISUAL(
        phaseNumber = 4,
        label = "Visual",
        description = "Escenas IA / vídeo propio y montaje MP4",
        primaryEngine = EngineId.VISUAL_VIDEO,
        checkpointStep = OrchestratorStep.COMPLETED
    );

    companion object {
        fun plannedFor(format: OutputFormat): List<Motor5Phase> = when (format) {
            OutputFormat.BOOK_DOCUMENT -> listOf(WRITING)
            OutputFormat.AUDIO_DRAMA_WAV -> listOf(WRITING, VOICES, MUSIC)
            OutputFormat.AUDIO_VIDEO_EPISODE, OutputFormat.VIDEOPODCAST_40MIN ->
                listOf(WRITING, VOICES, MUSIC, VISUAL)
        }
    }
}

sealed interface Motor5State {
    val projectId: String?
    val completedPhases: Set<Motor5Phase>
    val progressPercent: Int

    data object Idle : Motor5State {
        override val projectId: String? = null
        override val completedPhases: Set<Motor5Phase> = emptySet()
        override val progressPercent: Int = 0
    }

    data class RunningPhase(
        override val projectId: String,
        val currentPhase: Motor5Phase,
        val plannedPhases: List<Motor5Phase>,
        override val completedPhases: Set<Motor5Phase>,
        override val progressPercent: Int,
        val statusDetail: String
    ) : Motor5State

    data class SavingCheckpoint(
        override val projectId: String,
        val savedPhase: Motor5Phase,
        val plannedPhases: List<Motor5Phase>,
        override val completedPhases: Set<Motor5Phase>,
        override val progressPercent: Int
    ) : Motor5State

    data class Paused(
        override val projectId: String,
        val failedPhase: Motor5Phase?,
        val failedEngineName: String?,
        val errorMessage: String?,
        val plannedPhases: List<Motor5Phase>,
        override val completedPhases: Set<Motor5Phase>,
        override val progressPercent: Int,
        val lastSavedCheckpointStep: String
    ) : Motor5State

    data class Completed(
        override val projectId: String,
        val plannedPhases: List<Motor5Phase>,
        override val completedPhases: Set<Motor5Phase>,
        override val progressPercent: Int = 100
    ) : Motor5State
}
