package com.example

import android.app.Application
import com.example.ai.ActGenerationResult
import com.example.data.local.MohanaDatabase
import com.example.domain.model.ArtifactFormat
import com.example.domain.model.BookChapter
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.DialogueLine
import com.example.domain.model.ExportArtifact
import com.example.domain.model.OrchestratorStep
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionScript
import com.example.domain.model.ProductionStage
import com.example.domain.model.TimbreFilter
import com.example.domain.model.VideoResolution
import com.example.domain.model.VisualScene
import com.example.engines.AudioMixResult
import com.example.engines.DeviceCapabilities
import com.example.engines.OrchestratorEngines
import com.example.engines.VideoRenderResult
import com.example.ui.viewmodel.MohanaViewModel
import com.example.ui.viewmodel.Motor5Phase
import com.example.ui.viewmodel.Motor5State
import com.example.ui.viewmodel.StudioStep
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ViewModelStateMachineRobolectricTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        File(app.filesDir, "mohana_projects").deleteRecursively()
        app.deleteDatabase("mohana_ai_productions.db")
    }

    @After
    fun tearDown() {
        File(app.filesDir, "mohana_projects").deleteRecursively()
        runCatching { MohanaDatabase.getInstance(app).close() }
        app.deleteDatabase("mohana_ai_productions.db")
    }

    private fun awaitMotor5State(
        viewModel: MohanaViewModel,
        timeoutMs: Long = 15_000L,
        predicate: (Motor5State) -> Boolean
    ): Motor5State {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            val current = viewModel.motor5State.value
            if (predicate(current)) {
                return current
            }
            Thread.sleep(15)
        }
        throw AssertionError("Timed out waiting for Motor5State. Last state was: ${viewModel.motor5State.value}")
    }

    private class FakeStateMachineEngines : OrchestratorEngines {
        private val activeCounter = AtomicInteger(0)
        var maxConcurrent = 0
        var writingCalls = 0
        var actCalls = 0
        var voicesCalls = 0
        var mixCalls = 0
        var sceneCalls = 0
        var renderCalls = 0
        var failStep: OrchestratorStep? = null

        private suspend fun <T> guarded(step: OrchestratorStep, block: suspend () -> T): T {
            val active = activeCounter.incrementAndGet()
            if (active > maxConcurrent) maxConcurrent = active
            when (step) {
                OrchestratorStep.WRITING_FOUNDATION -> writingCalls++
                OrchestratorStep.DIALOGUE_ACTS -> actCalls++
                OrchestratorStep.VOICES_WAV -> voicesCalls++
                OrchestratorStep.MUSIC_MIX -> mixCalls++
                OrchestratorStep.SCENE_IMAGES -> sceneCalls++
                OrchestratorStep.VIDEO_RENDER -> renderCalls++
                else -> Unit
            }
            try {
                delay(10)
                if (failStep == step) {
                    throw IOException("Network failure at ${step.name}")
                }
                return block()
            } finally {
                activeCounter.decrementAndGet()
            }
        }

        override suspend fun generateStoryFoundation(
            ideaPrompt: String,
            genre: ContentGenre,
            outputFormat: OutputFormat,
            targetMinutes: Int
        ): ProductionScript = guarded(OrchestratorStep.WRITING_FOUNDATION) {
            ProductionScript(
                title = "Transmisión 09",
                logline = "Una entrevista nocturna grabada en cinta magnética.",
                characterVisualBible = "Estudio oscuro con micrófono vintage.",
                totalPlannedActs = 1,
                completedActs = 0,
                actOutlines = listOf("Acto 1"),
                characters = listOf(
                    CharacterProfile(
                        id = "c1",
                        name = "Elena",
                        role = "Entrevistadora",
                        ageStyle = "Adulta",
                        personality = "Serena",
                        visualDescription = "Estudio",
                        voicePitch = 1.0f,
                        voiceRate = 1.0f,
                        timbreFilter = TimbreFilter.WARM_STUDIO
                    )
                ),
                lines = emptyList(),
                scenes = emptyList(),
                chapters = emptyList()
            )
        }

        override suspend fun generateBookChapter(
            script: ProductionScript,
            genre: ContentGenre,
            chapterNumber: Int,
            chapterOutline: String,
            previousSummary: String
        ): BookChapter = BookChapter(chapterNumber, "Cap", chapterOutline, listOf("Texto"), listOf("Clave"))

        override suspend fun generateActDialogues(
            script: ProductionScript,
            genre: ContentGenre,
            outputFormat: OutputFormat,
            actNumber: Int,
            actOutline: String,
            previousSummary: String,
            startingSceneNumber: Int,
            startingLineIndex: Int
        ): ActGenerationResult = guarded(OrchestratorStep.DIALOGUE_ACTS) {
            ActGenerationResult(
                lines = listOf(
                    DialogueLine(
                        index = startingLineIndex,
                        actNumber = actNumber,
                        sceneNumber = startingSceneNumber,
                        speakerName = "Elena",
                        emotion = "Calma",
                        actingNote = "",
                        text = "Buenas noches, iniciamos la grabación.",
                        pauseAfterMs = 300L,
                        sfxCue = "NONE",
                        musicMood = "DARK_AMBIENT"
                    )
                ),
                scenes = listOf(
                    VisualScene(
                        sceneNumber = startingSceneNumber,
                        title = "Cabina",
                        settingDescription = "Estudio oscuro",
                        visualPrompt = "Dark studio",
                        mood = "MYSTERY"
                    )
                ),
                continuitySummary = "Inicio."
            )
        }

        override suspend fun exportDocuments(
            script: ProductionScript,
            outputDir: File
        ): List<ExportArtifact> = guarded(OrchestratorStep.SCRIPT_DOCS_EXPORT) {
            outputDir.mkdirs()
            val pdf = File(outputDir, "MOHANA_Guion.pdf").apply { writeBytes(ByteArray(64) { 1 }) }
            listOf(ExportArtifact(ArtifactFormat.PDF, pdf.absolutePath, pdf.length()))
        }

        override suspend fun synthesizeVoices(
            lines: List<DialogueLine>,
            characters: List<CharacterProfile>,
            outputDir: File,
            onLineProgress: suspend (completedIndex: Int, total: Int, updatedLine: DialogueLine) -> Unit
        ): List<DialogueLine> = guarded(OrchestratorStep.VOICES_WAV) {
            outputDir.mkdirs()
            lines.mapIndexed { idx, line ->
                val wav = File(outputDir, "line_${idx.toString().padStart(4, '0')}.wav").apply {
                    writeBytes(ByteArray(128) { 0x42 })
                }
                val updated = line.copy(synthesizedWavPath = wav.absolutePath, actualDurationMs = 1800L)
                onLineProgress(idx + 1, lines.size, updated)
                updated
            }
        }

        override suspend fun mixMasterWav(
            lines: List<DialogueLine>,
            characters: List<CharacterProfile>,
            outputWavFile: File,
            onProgress: suspend (percent: Int) -> Unit
        ): AudioMixResult = guarded(OrchestratorStep.MUSIC_MIX) {
            outputWavFile.parentFile?.mkdirs()
            outputWavFile.writeBytes(ByteArray(256) { 0x57 })
            onProgress(100)
            AudioMixResult(
                masterWavFile = outputWavFile,
                totalDurationMs = 2200L,
                timedLines = lines.map { it.copy(startTimeMs = 200L) },
                envelope100ms = FloatArray(22) { 0.35f }
            )
        }

        override suspend fun generateSceneImage(
            scene: VisualScene,
            characterVisualBible: String,
            outputFile: File
        ): File = guarded(OrchestratorStep.SCENE_IMAGES) {
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(ByteArray(128) { 0x7F })
            outputFile
        }

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
        ): VideoRenderResult = guarded(OrchestratorStep.VIDEO_RENDER) {
            outputMp4File.parentFile?.mkdirs()
            outputMp4File.writeBytes(ByteArray(512) { 0x66 })
            onProgress(100, "MP4 Listo")
            VideoRenderResult(outputMp4File, 1920, 1080, totalDurationMs, outputMp4File.length())
        }

        override suspend fun inspectCapabilities(): DeviceCapabilities = DeviceCapabilities(
            androidVersionLabel = "Android 14 (API 34)",
            apiLevel = 34,
            isAndroid14Compatible = true,
            avcEncoderName = "OMX.test.avc",
            supports1080p = true,
            supports4k = false,
            ttsEngineAvailable = true,
            spanishVoicesCount = 2,
            totalVoicesCount = 4
        )

        override fun onStageMemoryReleased(step: OrchestratorStep) {}
    }

    @Test
    fun `viewModel state machine executes phases sequentially and persists Room checkpoints after each phase`() = runBlocking {
        val engines = FakeStateMachineEngines()
        val viewModel = MohanaViewModel.forTest(app, engines)

        val observedStates = CopyOnWriteArrayList<Motor5State>()
        val dbCheckpointsAtPhaseSave = mutableMapOf<Motor5Phase, String>()
        val collectorJob = launch(Dispatchers.Unconfined) {
            viewModel.motor5State.collect { st ->
                observedStates.add(st)
                if (st is Motor5State.SavingCheckpoint) {
                    val savedEntity = viewModel.repository.getProject(st.projectId)
                    if (savedEntity != null) {
                        dbCheckpointsAtPhaseSave[st.savedPhase] = savedEntity.lastCompletedStep
                    }
                }
            }
        }

        viewModel.updateIdeaPrompt("Entrevista sobre señales nocturnas")
        viewModel.selectGenre(ContentGenre.INTERVIEW_CONFESSION)
        viewModel.selectFormat(OutputFormat.AUDIO_VIDEO_EPISODE)
        viewModel.startNewProduction()

        val finalState = awaitMotor5State(viewModel) { it is Motor5State.Completed }
        ShadowLooper.idleMainLooper()
        collectorJob.cancel()

        assertEquals("Only 1 engine may run at a time", 1, engines.maxConcurrent)
        assertTrue(finalState is Motor5State.Completed)
        val completed = finalState as Motor5State.Completed
        assertEquals(
            setOf(Motor5Phase.WRITING, Motor5Phase.VOICES, Motor5Phase.MUSIC, Motor5Phase.VISUAL),
            completed.completedPhases
        )
        assertEquals(StudioStep.STEP_3_RESULT, viewModel.formState.value.currentStep)

        val runningOrder = observedStates.filterIsInstance<Motor5State.RunningPhase>().map { it.currentPhase }
        assertEquals(
            listOf(Motor5Phase.WRITING, Motor5Phase.VOICES, Motor5Phase.MUSIC, Motor5Phase.VISUAL),
            runningOrder
        )

        assertEquals(OrchestratorStep.SCRIPT_DOCS_EXPORT.name, dbCheckpointsAtPhaseSave[Motor5Phase.WRITING])
        assertEquals(OrchestratorStep.VOICES_WAV.name, dbCheckpointsAtPhaseSave[Motor5Phase.VOICES])
        assertEquals(OrchestratorStep.MUSIC_MIX.name, dbCheckpointsAtPhaseSave[Motor5Phase.MUSIC])
        assertEquals(OrchestratorStep.COMPLETED.name, dbCheckpointsAtPhaseSave[Motor5Phase.VISUAL])
    }

    @Test
    fun `viewModel state machine pauses on failure and resumes from Room checkpoint without repeating earlier phases`() = runBlocking {
        val engines = FakeStateMachineEngines().apply {
            failStep = OrchestratorStep.MUSIC_MIX
        }
        val viewModel = MohanaViewModel.forTest(app, engines)

        viewModel.updateIdeaPrompt("Confesión en cabina")
        viewModel.selectGenre(ContentGenre.INTERVIEW_CONFESSION)
        viewModel.selectFormat(OutputFormat.AUDIO_VIDEO_EPISODE)
        viewModel.startNewProduction()

        val pausedState = awaitMotor5State(viewModel) { it is Motor5State.Paused } as Motor5State.Paused
        ShadowLooper.idleMainLooper()

        assertEquals(Motor5Phase.MUSIC, pausedState.failedPhase)
        assertEquals(setOf(Motor5Phase.WRITING, Motor5Phase.VOICES), pausedState.completedPhases)
        assertEquals(OrchestratorStep.VOICES_WAV.name, pausedState.lastSavedCheckpointStep)

        val dbRow = viewModel.repository.getProject(pausedState.projectId)
        assertNotNull(dbRow)
        assertEquals(ProductionStage.PAUSED.name, dbRow!!.stageName)
        assertEquals(OrchestratorStep.VOICES_WAV.name, dbRow.lastCompletedStep)
        assertEquals(1, engines.writingCalls)
        assertEquals(1, engines.voicesCalls)
        assertEquals(1, engines.mixCalls)

        // Recover and retry failed step via ViewModel
        engines.failStep = null
        viewModel.retryFailedStep(pausedState.projectId)

        val completedState = awaitMotor5State(viewModel) { it is Motor5State.Completed } as Motor5State.Completed
        ShadowLooper.idleMainLooper()

        assertEquals(100, completedState.progressPercent)
        // Writing and Voices must NOT execute again because Room checkpoint already persisted them
        assertEquals(1, engines.writingCalls)
        assertEquals(1, engines.voicesCalls)
        assertEquals(2, engines.mixCalls)
        assertEquals(1, engines.renderCalls)
    }
}
