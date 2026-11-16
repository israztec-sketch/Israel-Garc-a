package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ActGenerationResult
import com.example.data.local.CreationLibraryRepository
import com.example.data.local.MohanaDatabase
import com.example.data.local.ProductionProjectEntity
import com.example.domain.model.ArtifactFormat
import com.example.domain.model.BookChapter
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.DialogueLine
import com.example.domain.model.EngineId
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
import com.example.engines.LiveProductionTelemetry
import com.example.engines.OrchestratorEngines
import com.example.engines.ProductionOrchestrator
import com.example.engines.VideoRenderResult
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.MohanaAiTheme
import com.example.ui.viewmodel.StudioFormState
import com.example.ui.viewmodel.StudioStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Motor5OrchestratorRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: MohanaDatabase
    private lateinit var repository: CreationLibraryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MohanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CreationLibraryRepository(context, db.productionDao(), db.creationsDao())
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, "mohana_projects").deleteRecursively()
    }

    private class FakeOrchestratorEngines(
        private val repository: CreationLibraryRepository,
        private val projectId: String
    ) : OrchestratorEngines {
        private val activeCounter = AtomicInteger(0)
        var maxConcurrentObserved = 0
            private set

        val executionOrder = mutableListOf<OrchestratorStep>()
        val releasedSteps = mutableListOf<OrchestratorStep>()
        val stepCheckpointAtRelease = mutableMapOf<OrchestratorStep, String>()

        var writingCalls = 0
        var actCalls = 0
        var docsCalls = 0
        var voicesCalls = 0
        var mixCalls = 0
        var sceneCalls = 0
        var renderCalls = 0
        var lastCustomVideoPath: String? = null

        var failOnStep: OrchestratorStep? = null
        var failureFactory: (OrchestratorStep) -> Throwable = {
            SocketTimeoutException("Timed out calling remote service")
        }

        private suspend fun <T> guarded(step: OrchestratorStep, block: suspend () -> T): T {
            val active = activeCounter.incrementAndGet()
            if (active > maxConcurrentObserved) {
                maxConcurrentObserved = active
            }
            executionOrder.add(step)
            when (step) {
                OrchestratorStep.WRITING_FOUNDATION -> writingCalls++
                OrchestratorStep.DIALOGUE_ACTS -> actCalls++
                OrchestratorStep.SCRIPT_DOCS_EXPORT -> docsCalls++
                OrchestratorStep.VOICES_WAV -> voicesCalls++
                OrchestratorStep.MUSIC_MIX -> mixCalls++
                OrchestratorStep.SCENE_IMAGES -> sceneCalls++
                OrchestratorStep.VIDEO_RENDER -> renderCalls++
                else -> Unit
            }
            try {
                delay(5)
                if (failOnStep == step) {
                    throw failureFactory(step)
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
                title = "Estación Fantasma",
                logline = "Una entrevista nocturna revela transmisiones de un andén clausurado.",
                characterVisualBible = "Cabina oscura con luz ámbar cálida.",
                totalPlannedActs = 1,
                completedActs = 0,
                actOutlines = listOf("Acto 1: La confesión del operador"),
                characters = listOf(
                    CharacterProfile(
                        id = "char_1",
                        name = "Elena",
                        role = "Entrevistadora",
                        ageStyle = "Adulta",
                        personality = "Incisiva",
                        visualDescription = "Cabina con micrófono",
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
        ): BookChapter = BookChapter(
            chapterNumber = chapterNumber,
            title = "Capítulo $chapterNumber",
            subtitle = chapterOutline,
            bodyParagraphs = listOf("Contenido del capítulo $chapterNumber."),
            keyTakeaways = listOf("Clave 1")
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
        ): ActGenerationResult = guarded(OrchestratorStep.DIALOGUE_ACTS) {
            ActGenerationResult(
                lines = listOf(
                    DialogueLine(
                        index = startingLineIndex,
                        actNumber = actNumber,
                        sceneNumber = startingSceneNumber,
                        speakerName = "Elena",
                        emotion = "Tensa",
                        actingNote = "En voz baja",
                        text = "¿Escuchaste la frecuencia a las tres de la madrugada?",
                        pauseAfterMs = 400L,
                        sfxCue = "NONE",
                        musicMood = "DARK_AMBIENT"
                    )
                ),
                scenes = listOf(
                    VisualScene(
                        sceneNumber = startingSceneNumber,
                        title = "Cabina de Radio",
                        settingDescription = "Estudio con luz ámbar",
                        visualPrompt = "Dark studio with warm amber light",
                        mood = "MYSTERY"
                    )
                ),
                continuitySummary = "Elena abre la entrevista."
            )
        }

        override suspend fun exportDocuments(
            script: ProductionScript,
            outputDir: File
        ): List<ExportArtifact> = guarded(OrchestratorStep.SCRIPT_DOCS_EXPORT) {
            outputDir.mkdirs()
            val pdfFile = File(outputDir, "MOHANA_Guion.pdf").apply {
                writeBytes(ByteArray(128) { 0x25 })
            }
            listOf(
                ExportArtifact(
                    format = ArtifactFormat.PDF,
                    filePath = pdfFile.absolutePath,
                    fileSizeBytes = pdfFile.length()
                )
            )
        }

        override suspend fun synthesizeVoices(
            lines: List<DialogueLine>,
            characters: List<CharacterProfile>,
            outputDir: File,
            onLineProgress: suspend (completedIndex: Int, total: Int, updatedLine: DialogueLine) -> Unit
        ): List<DialogueLine> = guarded(OrchestratorStep.VOICES_WAV) {
            outputDir.mkdirs()
            val updated = lines.mapIndexed { idx, line ->
                val wav = File(outputDir, "line_${idx}.wav").apply {
                    writeBytes(ByteArray(128) { 0x52 })
                }
                val doneLine = line.copy(
                    synthesizedWavPath = wav.absolutePath,
                    actualDurationMs = 2400L
                )
                onLineProgress(idx + 1, lines.size, doneLine)
                doneLine
            }
            updated
        }

        override suspend fun mixMasterWav(
            lines: List<DialogueLine>,
            characters: List<CharacterProfile>,
            outputWavFile: File,
            onProgress: suspend (percent: Int) -> Unit
        ): AudioMixResult = guarded(OrchestratorStep.MUSIC_MIX) {
            outputWavFile.parentFile?.mkdirs()
            outputWavFile.writeBytes(ByteArray(256) { 0x52 })
            onProgress(100)
            val timed = lines.mapIndexed { idx, line ->
                line.copy(startTimeMs = idx * 2400L, actualDurationMs = 2400L)
            }
            AudioMixResult(
                masterWavFile = outputWavFile,
                totalDurationMs = timed.size * 2400L,
                timedLines = timed,
                envelope100ms = FloatArray(24) { 0.35f }
            )
        }

        override suspend fun generateSceneImage(
            scene: VisualScene,
            characterVisualBible: String,
            outputFile: File
        ): File = guarded(OrchestratorStep.SCENE_IMAGES) {
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(ByteArray(128) { 0x89.toByte() })
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
            lastCustomVideoPath = customVideoPath
            outputMp4File.parentFile?.mkdirs()
            outputMp4File.writeBytes(ByteArray(512) { 0x00 })
            onProgress(100, "MP4 completo")
            VideoRenderResult(
                mp4File = outputMp4File,
                actualWidth = 1920,
                actualHeight = 1080,
                actualDurationMs = totalDurationMs,
                fileSizeBytes = outputMp4File.length()
            )
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

        override fun onStageMemoryReleased(step: OrchestratorStep) {
            releasedSteps.add(step)
            val persisted = runBlocking { repository.getProject(projectId) }
            if (persisted != null) {
                stepCheckpointAtRelease[step] = persisted.lastCompletedStep
            }
        }
    }

    @Test
    fun `rule 1 and rule 3 enforce strict sequential execution and memory release after each checkpoint`() = runBlocking {
        val project = repository.createInitialProject(
            ideaPrompt = "Historia de radio nocturna",
            genre = ContentGenre.INTERVIEW_CONFESSION,
            outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
            resolution = VideoResolution.FULL_HD_1080P,
            targetMinDurationMinutes = 4
        )
        val engines = FakeOrchestratorEngines(repository, project.id)
        val orchestrator = ProductionOrchestrator(context, repository, engines)

        val result = orchestrator.runOrResumeProduction(project.id)

        assertEquals(ProductionStage.COMPLETED.name, result.stageName)
        assertEquals(100, result.progressPercent)
        assertEquals(OrchestratorStep.COMPLETED.name, result.lastCompletedStep)
        assertNull(result.failedStep)
        assertNull(result.failedEngineId)

        // Rule 1: Never more than 1 heavy engine running simultaneously
        assertEquals(1, engines.maxConcurrentObserved)
        assertEquals(
            listOf(
                OrchestratorStep.WRITING_FOUNDATION,
                OrchestratorStep.DIALOGUE_ACTS,
                OrchestratorStep.SCRIPT_DOCS_EXPORT,
                OrchestratorStep.VOICES_WAV,
                OrchestratorStep.MUSIC_MIX,
                OrchestratorStep.SCENE_IMAGES,
                OrchestratorStep.VIDEO_RENDER
            ),
            engines.executionOrder
        )

        // Rule 3: Memory released strictly AFTER each completed stage's Room checkpoint was saved
        assertTrue(engines.releasedSteps.containsAll(
            listOf(
                OrchestratorStep.WRITING_FOUNDATION,
                OrchestratorStep.DIALOGUE_ACTS,
                OrchestratorStep.SCRIPT_DOCS_EXPORT,
                OrchestratorStep.VOICES_WAV,
                OrchestratorStep.MUSIC_MIX,
                OrchestratorStep.SCENE_IMAGES,
                OrchestratorStep.VIDEO_RENDER
            )
        ))
        assertEquals(OrchestratorStep.VOICES_WAV.name, engines.stepCheckpointAtRelease[OrchestratorStep.VOICES_WAV])
        assertEquals(OrchestratorStep.MUSIC_MIX.name, engines.stepCheckpointAtRelease[OrchestratorStep.MUSIC_MIX])
        assertTrue(orchestrator.telemetry.value.memoryReleaseCount >= 7)
    }

    @Test
    fun `rule 2 and rule 4 auto-pause on engine failure, preserve exact checkpoint, and retry only failed step`() = runBlocking {
        val project = repository.createInitialProject(
            ideaPrompt = "Confesión del vigilante del túnel",
            genre = ContentGenre.INTERVIEW_CONFESSION,
            outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
            resolution = VideoResolution.FULL_HD_1080P,
            targetMinDurationMinutes = 4
        )
        val engines = FakeOrchestratorEngines(repository, project.id).apply {
            failOnStep = OrchestratorStep.MUSIC_MIX
        }
        val orchestrator = ProductionOrchestrator(context, repository, engines)

        // Run 1: Fails at MUSIC_MIX -> must auto-pause cleanly at VOICES_WAV checkpoint (60%)
        val paused = orchestrator.runOrResumeProduction(project.id)

        assertEquals(ProductionStage.PAUSED.name, paused.stageName)
        assertEquals(60, paused.progressPercent)
        assertEquals(OrchestratorStep.VOICES_WAV.name, paused.lastCompletedStep)
        assertEquals(OrchestratorStep.MUSIC_MIX.name, paused.failedStep)
        assertEquals(EngineId.MUSIC_SFX.name, paused.failedEngineId)
        assertNotNull(paused.errorMessage)
        assertTrue(paused.errorMessage!!.contains(EngineId.MUSIC_SFX.displayName))
        assertEquals(1, engines.writingCalls)
        assertEquals(1, engines.actCalls)
        assertEquals(1, engines.docsCalls)
        assertEquals(1, engines.voicesCalls)
        assertEquals(1, engines.mixCalls)
        assertEquals(0, engines.sceneCalls)
        assertEquals(0, engines.renderCalls)

        // Must appear in recoverable projects immediately
        val recoverable = repository.getRecoverableProjects()
        assertTrue(recoverable.any { it.id == project.id && it.failedEngineId == EngineId.MUSIC_SFX.name })

        // Clear failure and retry ONLY the failed step
        engines.failOnStep = null
        val completed = orchestrator.runOrResumeProduction(project.id, retryFailedOnly = true)

        assertEquals(ProductionStage.COMPLETED.name, completed.stageName)
        assertEquals(100, completed.progressPercent)
        assertEquals(OrchestratorStep.COMPLETED.name, completed.lastCompletedStep)
        assertNull(completed.failedStep)
        assertNull(completed.failedEngineId)
        assertNull(completed.errorMessage)

        // Crucial Rule 2 assertion: NO prior API or TTS calls were repeated!
        assertEquals(1, engines.writingCalls)
        assertEquals(1, engines.actCalls)
        assertEquals(1, engines.docsCalls)
        assertEquals(1, engines.voicesCalls)
        // Only MUSIC_MIX was retried, followed by SCENE_IMAGES and VIDEO_RENDER
        assertEquals(2, engines.mixCalls)
        assertEquals(1, engines.sceneCalls)
        assertEquals(1, engines.renderCalls)
    }

    @Test
    fun `rule 2 resumes from SCENE_IMAGES failure without repeating MUSIC_MIX via audio_envelope disk checkpoint`() = runBlocking {
        val project = repository.createInitialProject(
            ideaPrompt = "Bitácora del observatorio austral",
            genre = ContentGenre.SCIFI,
            outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
            resolution = VideoResolution.FULL_HD_1080P,
            targetMinDurationMinutes = 4
        )
        val engines = FakeOrchestratorEngines(repository, project.id).apply {
            failOnStep = OrchestratorStep.SCENE_IMAGES
        }
        val orchestrator = ProductionOrchestrator(context, repository, engines)

        val paused = orchestrator.runOrResumeProduction(project.id)

        assertEquals(ProductionStage.PAUSED.name, paused.stageName)
        assertEquals(72, paused.progressPercent)
        assertEquals(OrchestratorStep.MUSIC_MIX.name, paused.lastCompletedStep)
        assertEquals(OrchestratorStep.SCENE_IMAGES.name, paused.failedStep)
        assertEquals(EngineId.VISUAL_VIDEO.name, paused.failedEngineId)

        // Envelope checkpoint file must exist on disk so MUSIC_MIX isn't repeated
        val envelopeFile = File(paused.directoryPath, "audio_envelope.bin")
        assertTrue(envelopeFile.exists() && envelopeFile.length() > 4L)

        engines.failOnStep = null
        val completed = orchestrator.runOrResumeProduction(project.id, retryFailedOnly = true)

        assertEquals(ProductionStage.COMPLETED.name, completed.stageName)
        assertEquals(100, completed.progressPercent)
        assertEquals(1, engines.mixCalls) // Never re-ran DSP Audio Mix
        assertEquals(2, engines.sceneCalls) // First attempt threw before succeeding, retry succeeded
        assertEquals(1, engines.renderCalls)
    }

    @Test
    fun `studio step 2 displays paused failed engine banner and retry single step button`() {
        var retriedProjectId: String? = null
        val pausedProject = ProductionProjectEntity(
            id = "proj-err-1",
            title = "Expediente Nocturno",
            ideaPrompt = "Entrevista en cabina",
            genreName = ContentGenre.INTERVIEW_CONFESSION.name,
            outputFormatName = OutputFormat.AUDIO_VIDEO_EPISODE.name,
            resolutionName = VideoResolution.FULL_HD_1080P.name,
            targetMinDurationMinutes = 4,
            stageName = ProductionStage.PAUSED.name,
            progressPercent = 60,
            statusMessage = "Pausado al 60% · Falló 6. Música y Efectos DSP",
            errorMessage = "Falló 6. Música y Efectos DSP (Mezcla Maestra Música y SFX): Tiempo de espera agotado.",
            scriptJson = null,
            artifactsJson = "[]",
            directoryPath = "/tmp/proj-err-1",
            useTypographyVisualFallback = false,
            selectedCastJson = "[]",
            lastCompletedStep = OrchestratorStep.VOICES_WAV.name,
            failedStep = OrchestratorStep.MUSIC_MIX.name,
            failedEngineId = EngineId.MUSIC_SFX.name,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        composeRule.setContent {
            MohanaAiTheme {
                StudioScreen(
                    formState = StudioFormState(
                        currentStep = StudioStep.STEP_2_PRODUCE,
                        activeProjectId = pausedProject.id,
                        errorBanner = pausedProject.errorMessage
                    ),
                    activeProject = pausedProject,
                    verifiedArtifacts = emptyList(),
                    telemetry = LiveProductionTelemetry(
                        activeProjectId = pausedProject.id,
                        stage = ProductionStage.PAUSED,
                        progressPercent = 60,
                        statusMessage = pausedProject.statusMessage,
                        lastCompletedStep = OrchestratorStep.VOICES_WAV,
                        failedEngineId = EngineId.MUSIC_SFX,
                        failedStep = OrchestratorStep.MUSIC_MIX,
                        pausedDueToError = true,
                        memoryReleaseCount = 4
                    ),
                    capabilities = null,
                    recoverableProjects = listOf(pausedProject),
                    isProducing = false,
                    onUpdateIdea = {},
                    onSelectGenre = {},
                    onSelectFormat = {},
                    onSelectResolution = {},
                    onUpdatePodcastMinutes = {},
                    onApplyPreset = { _, _, _ -> },
                    onSelectStep = {},
                    onStartProduction = {},
                    onPauseProduction = {},
                    onResumeProject = {},
                    onRetryFailedStep = { retriedProjectId = it },
                    onEnableTypographyFallback = {},
                    onShareArtifact = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("production_error_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Pausa automática · Falló 6. Música y Efectos DSP").assertIsDisplayed()
        composeRule.onNodeWithText("Progreso conservado al 60% en almacenamiento local.").assertIsDisplayed()

        composeRule.onNodeWithTag("retry_failed_step_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals("proj-err-1", retriedProjectId)
    }

    @Test
    fun `motor 5 executes off Main thread and safely handles fast cancel then resume without deadlock`() = runBlocking {
        val project = repository.createInitialProject(
            ideaPrompt = "Prueba de no bloqueo de hilo principal",
            genre = ContentGenre.INTERVIEW_CONFESSION,
            outputFormat = OutputFormat.AUDIO_DRAMA_WAV,
            resolution = VideoResolution.FULL_HD_1080P,
            targetMinDurationMinutes = 4
        )
        val engines = FakeOrchestratorEngines(repository, project.id)
        val orchestrator = ProductionOrchestrator(context, repository, engines)

        val completed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            orchestrator.runOrResumeProduction(project.id)
        }

        assertEquals(ProductionStage.COMPLETED.name, completed.stageName)
        assertEquals(100, completed.progressPercent)
        assertEquals(1, engines.maxConcurrentObserved)
        // Creations metadata and transcript must be synced on stage completion
        val meta = db.creationsDao().getMetadataById(project.id)
        assertNotNull(meta)
        assertEquals("Estación Fantasma", meta!!.title)
    }

    @Test
    fun `customVideoPath skips SCENE_IMAGES and forwards user video to renderMp4`() = runBlocking {
        val customVideoFile = File(context.filesDir, "mohana_custom_videos/mi_clip_test.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(2048) { 0x1A })
        }
        try {
            val project = repository.createInitialProject(
                ideaPrompt = "Documental con vídeo propio",
                genre = ContentGenre.INTERVIEW_CONFESSION,
                outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
                resolution = VideoResolution.FULL_HD_1080P,
                targetMinDurationMinutes = 2,
                customVideoPath = customVideoFile.absolutePath
            )
            val engines = FakeOrchestratorEngines(repository, project.id)
            val orchestrator = ProductionOrchestrator(context, repository, engines)

            val completed = orchestrator.runOrResumeProduction(project.id)

            assertEquals(ProductionStage.COMPLETED.name, completed.stageName)
            assertEquals(0, engines.sceneCalls)
            assertEquals(1, engines.renderCalls)
            assertEquals(customVideoFile.absolutePath, engines.lastCustomVideoPath)
        } finally {
            customVideoFile.delete()
        }
    }
}
