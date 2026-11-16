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
import com.example.data.local.ProductionStorageStats
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
import com.example.ui.screens.EnginesAndDistributionScreen
import com.example.ui.theme.MohanaAiTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StorageCleanerRobolectricTest {

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
        context.cacheDir?.listFiles()?.filter { it.name.startsWith("mohana_") }?.forEach { it.deleteRecursively() }
    }

    private class CleanupTestEngines : OrchestratorEngines {
        var voicesCalls = 0
        var mixCalls = 0
        var sceneCalls = 0
        var renderCalls = 0
        var failOnStep: OrchestratorStep? = null

        override suspend fun generateStoryFoundation(
            ideaPrompt: String,
            genre: ContentGenre,
            outputFormat: OutputFormat,
            targetMinutes: Int
        ): ProductionScript = ProductionScript(
            title = "Crónica de Prueba",
            logline = "Test",
            characterVisualBible = "Estudio nocturno",
            totalPlannedActs = 1,
            completedActs = 0,
            actOutlines = listOf("Acto 1"),
            characters = listOf(
                CharacterProfile(
                    id = "c1",
                    name = "Elena",
                    role = "Conductora",
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

        override suspend fun generateBookChapter(
            script: ProductionScript,
            genre: ContentGenre,
            chapterNumber: Int,
            chapterOutline: String,
            previousSummary: String
        ): BookChapter = BookChapter(
            chapterNumber = chapterNumber,
            title = "Cap",
            subtitle = "Sub",
            bodyParagraphs = listOf("Texto"),
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
        ): ActGenerationResult = ActGenerationResult(
            scenes = listOf(
                VisualScene(
                    sceneNumber = startingSceneNumber,
                    title = "Cabina",
                    settingDescription = "Fondo cabina",
                    visualPrompt = "Estudio nocturno con micrófono",
                    mood = "MYSTERY"
                )
            ),
            lines = listOf(
                DialogueLine(
                    index = startingLineIndex,
                    actNumber = actNumber,
                    sceneNumber = startingSceneNumber,
                    speakerName = "Elena",
                    emotion = "serena",
                    actingNote = "",
                    text = "Hola mundo",
                    pauseAfterMs = 200L,
                    sfxCue = "NONE",
                    musicMood = "DARK_AMBIENT"
                )
            ),
            continuitySummary = "Fin"
        )

        override suspend fun exportDocuments(script: ProductionScript, outputDir: File): List<ExportArtifact> {
            outputDir.mkdirs()
            val pdf = File(outputDir, "MOHANA_Obra.pdf").apply { writeBytes(ByteArray(256) { 1 }) }
            return listOf(ExportArtifact(ArtifactFormat.PDF, pdf.absolutePath, pdf.length()))
        }

        override suspend fun synthesizeVoices(
            lines: List<DialogueLine>,
            characters: List<CharacterProfile>,
            outputDir: File,
            onLineProgress: suspend (completedIndex: Int, total: Int, updatedLine: DialogueLine) -> Unit
        ): List<DialogueLine> {
            voicesCalls++
            outputDir.mkdirs()
            val updated = lines.mapIndexed { idx, line ->
                val wav = File(outputDir, "line_${idx.toString().padStart(4, '0')}.wav").apply {
                    writeBytes(ByteArray(1024) { 7 })
                }
                val item = line.copy(synthesizedWavPath = wav.absolutePath, actualDurationMs = 1500L)
                onLineProgress(idx + 1, lines.size, item)
                item
            }
            // Leave a transient .tmp buffer to verify auto-cleanup sweeps it
            File(outputDir, "tts_buffer.tmp").writeBytes(ByteArray(512))
            return updated
        }

        override suspend fun mixMasterWav(
            lines: List<DialogueLine>,
            characters: List<CharacterProfile>,
            outputWavFile: File,
            onProgress: suspend (percent: Int) -> Unit
        ): AudioMixResult {
            mixCalls++
            outputWavFile.parentFile?.mkdirs()
            outputWavFile.writeBytes(ByteArray(4096) { 9 })
            onProgress(100)
            return AudioMixResult(
                masterWavFile = outputWavFile,
                totalDurationMs = 3000L,
                timedLines = lines.map { it.copy(startTimeMs = 0L, actualDurationMs = 1500L) },
                envelope100ms = FloatArray(30) { 0.4f }
            )
        }

        override suspend fun generateSceneImage(
            scene: VisualScene,
            characterVisualBible: String,
            outputFile: File
        ): File {
            if (failOnStep == OrchestratorStep.SCENE_IMAGES) {
                throw SocketTimeoutException("Timeout generando imagen")
            }
            sceneCalls++
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(ByteArray(2048) { 3 })
            return outputFile
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
        ): VideoRenderResult {
            renderCalls++
            outputMp4File.parentFile?.mkdirs()
            outputMp4File.writeBytes(ByteArray(8192) { 5 })
            onProgress(100, "MP4 ok")
            return VideoRenderResult(
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
            totalVoicesCount = 4,
            spanishVoicesCount = 2
        )

        override fun onStageMemoryReleased(step: OrchestratorStep) = Unit
    }

    @Test
    fun autoCleanupAfterCheckpoints_deletesTempsAndPreservesResumeAndFinalOutputs() = runBlocking {
        val project = repository.createInitialProject(
            ideaPrompt = "Limpieza automática tras checkpoints",
            genre = ContentGenre.INTERVIEW_CONFESSION,
            outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
            resolution = VideoResolution.FULL_HD_1080P,
            targetMinDurationMinutes = 1
        )
        val projectDir = File(project.directoryPath)
        val engines = CleanupTestEngines().apply { failOnStep = OrchestratorStep.SCENE_IMAGES }
        val orchestrator = ProductionOrchestrator(context, repository, engines)

        // First run: reaches MUSIC_MIX checkpoint (cleans voices/ + .tmp), then fails in SCENE_IMAGES
        val paused = orchestrator.runOrResumeProduction(project.id)
        assertEquals(ProductionStage.PAUSED.name, paused.stageName)
        assertEquals(OrchestratorStep.SCENE_IMAGES.name, paused.failedStep)

        // Verify voices/ temp clips and tts_buffer.tmp were auto-cleaned after MUSIC_MIX checkpoint
        val voicesFilesLeft = File(projectDir, "voices").listFiles()?.toList().orEmpty()
        assertTrue("voices/ should be cleaned after MUSIC_MIX", voicesFilesLeft.isEmpty())
        assertTrue("Master WAV must remain", File(projectDir, "exports/MOHANA_Audio_Maestro.wav").exists())
        assertTrue("audio_envelope.bin must remain until VIDEO_RENDER", File(projectDir, "audio_envelope.bin").exists())
        assertTrue(orchestrator.telemetry.value.autoCleanedFiles > 0)

        // Second run: retry SCENE_IMAGES -> should NOT re-run VOICES_WAV or MUSIC_MIX
        engines.failOnStep = null
        val completed = orchestrator.runOrResumeProduction(project.id, retryFailedOnly = true)
        assertEquals(ProductionStage.COMPLETED.name, completed.stageName)
        assertEquals(1, engines.voicesCalls)
        assertEquals(1, engines.mixCalls)
        assertEquals(1, engines.sceneCalls)
        assertEquals(1, engines.renderCalls)

        // Verify after VIDEO_RENDER/COMPLETED: scenes/, voices/, and audio_envelope.bin are auto-cleaned
        val scenesFilesLeft = File(projectDir, "scenes").listFiles()?.toList().orEmpty()
        assertTrue("scenes/ should be cleaned after COMPLETED", scenesFilesLeft.isEmpty())
        assertFalse("audio_envelope.bin should be cleaned after COMPLETED", File(projectDir, "audio_envelope.bin").exists())

        // Final deliverables must remain untouched
        assertTrue("Final MP4 must exist", File(projectDir, "exports/MOHANA_Video_Final.mp4").exists())
        assertTrue("Master WAV must exist", File(projectDir, "exports/MOHANA_Audio_Maestro.wav").exists())
        assertTrue("PDF must exist", File(projectDir, "exports/MOHANA_Obra.pdf").exists())
    }

    @Test
    fun freeFailedProductionSpace_removesFailedProjectsAndPreservesCompletedOnes() = runBlocking {
        val completed = repository.createInitialProject(
            ideaPrompt = "Proyecto terminado intacto",
            genre = ContentGenre.SCIFI,
            outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
            resolution = VideoResolution.FULL_HD_1080P,
            targetMinDurationMinutes = 1
        )
        val completedMp4 = File(completed.directoryPath, "exports/MOHANA_Video_Final.mp4").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(4096) { 2 })
        }
        repository.saveCheckpoint(
            completed.copy(
                stageName = ProductionStage.COMPLETED.name,
                progressPercent = 100,
                lastCompletedStep = OrchestratorStep.COMPLETED.name
            )
        )

        val failed = repository.createInitialProject(
            ideaPrompt = "Proyecto fallido con temporales",
            genre = ContentGenre.CREEPYPASTA,
            outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
            resolution = VideoResolution.FULL_HD_1080P,
            targetMinDurationMinutes = 1
        )
        File(failed.directoryPath, "voices/line_0000.wav").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(2048) { 4 })
        }
        File(failed.directoryPath, "scenes/scene_001.png").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(3072) { 6 })
        }
        repository.saveCheckpoint(
            failed.copy(
                stageName = ProductionStage.PAUSED.name,
                progressPercent = 72,
                failedStep = OrchestratorStep.SCENE_IMAGES.name,
                failedEngineId = EngineId.VISUAL_VIDEO.name,
                errorMessage = "Error de conexión"
            )
        )

        val orphanDir = File(context.filesDir, "mohana_projects/orphan_legacy").apply { mkdirs() }
        File(orphanDir, "stale_buffer.wav").writeBytes(ByteArray(1024) { 8 })
        val cacheTemp = File(context.cacheDir, "mohana_render_cache.tmp").apply { writeBytes(ByteArray(512) { 1 }) }

        val statsBefore = repository.storageCleaner.inspectStorage()
        assertEquals(1, statsBefore.failedProjectsCount)
        assertTrue(statsBefore.reclaimableBytes >= 2048L + 3072L + 1024L + 512L)

        val summary = repository.storageCleaner.freeFailedProductionSpace()
        assertEquals(1, summary.failedProjectsRemoved)
        assertTrue(summary.deletedFilesCount >= 4)
        assertTrue(summary.freedBytes >= 2048L + 3072L + 1024L + 512L)

        // Failed DB row & directory + orphan + cache temp must be gone
        assertNull(repository.getProject(failed.id))
        assertFalse(File(failed.directoryPath).exists())
        assertFalse(orphanDir.exists())
        assertFalse(cacheTemp.exists())

        // Completed project & its deliverable must remain intact
        assertNotNull(repository.getProject(completed.id))
        assertTrue(completedMp4.exists())
        assertEquals(4096L, completedMp4.length())
    }

    @Test
    fun enginesScreen_displaysStorageStatsAndTriggersFreeProductionSpaceButton() {
        var cleanClicked = false
        composeRule.setContent {
            MohanaAiTheme {
                EnginesAndDistributionScreen(
                    capabilities = null,
                    telemetry = LiveProductionTelemetry(autoCleanedFiles = 6, autoCleanedBytes = 2048L),
                    storageStats = ProductionStorageStats(
                        totalProjectsBytes = 5L * 1024L * 1024L,
                        failedProjectsBytes = 2L * 1024L * 1024L,
                        failedProjectsCount = 2,
                        tempCacheBytes = 512L * 1024L,
                        reclaimableBytes = 2560L * 1024L
                    ),
                    isProducing = false,
                    onShareApk = {},
                    onFreeProductionSpace = { cleanClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("Almacenamiento y Limpieza de Producción").assertIsDisplayed()
        composeRule.onNodeWithTag("free_production_space_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertTrue("El botón 'Liberar Espacio de Producción' debe disparar la limpieza", cleanClicked)
    }
}
