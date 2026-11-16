package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CreationLibraryRepository
import com.example.data.local.MohanaDatabase
import com.example.domain.model.ArtifactFormat
import com.example.domain.model.BookChapter
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.DialogueLine
import com.example.domain.model.ExportArtifact
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionScript
import com.example.domain.model.ProductionStage
import com.example.domain.model.TimbreFilter
import com.example.domain.model.VideoResolution
import com.example.domain.model.VisualScene
import com.example.engines.BookDocumentEngine
import com.example.engines.MusicSfxEngine
import com.example.engines.VoiceAudioEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app identity matches MOHANA AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("MOHANA AI", context.getString(R.string.app_name))
        assertEquals("Inteligencia Creativa", context.getString(R.string.app_tagline))
    }

    @Test
    fun `three step studio UI and tabs render cleanly`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("MOHANA AI").assertIsDisplayed()
        composeRule.onNodeWithTag("step_tab_1").assertIsDisplayed()
        composeRule.onNodeWithTag("step_tab_2").assertIsDisplayed()
        composeRule.onNodeWithTag("step_tab_3").assertIsDisplayed()
        composeRule.onNodeWithTag("start_production_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("nav_creations").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tu biblioteca local está vacía").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_engines").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("share_apk_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `book document engine exports valid PDF HTML MD and TXT files`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outDir = File(context.cacheDir, "test_book_exports_${System.currentTimeMillis()}")
        val script = ProductionScript(
            title = "Expediente Nocturno",
            logline = "Una crónica completa sobre transmisiones anómalas.",
            characterVisualBible = "Dark studio",
            totalPlannedActs = 1,
            completedActs = 1,
            actOutlines = listOf("Capítulo único"),
            characters = emptyList(),
            lines = emptyList(),
            scenes = emptyList(),
            chapters = listOf(
                BookChapter(
                    chapterNumber = 1,
                    title = "La Señal del Subsuelo",
                    subtitle = "Registros acústicos de 1998",
                    bodyParagraphs = listOf(
                        "Durante la madrugada del 14 de noviembre, la consola principal registró una portadora limpia.",
                        "Los operadores confirmaron que la antena externa había sido desconectada horas antes."
                    ),
                    keyTakeaways = listOf("Señal en 432 Hz", "Testimonio verificado")
                )
            )
        )

        val engine = BookDocumentEngine()
        val artifacts = engine.exportAllFormats(script, outDir)

        assertEquals(4, artifacts.size)
        val pdfArtifact = artifacts.first { it.format == ArtifactFormat.PDF }
        val pdfFile = File(pdfArtifact.filePath)
        assertTrue(pdfFile.exists() && pdfFile.length() > 500L)
        val header = pdfFile.inputStream().use { ByteArray(4).also { b -> it.read(b) } }
        assertEquals("%PDF", String(header, Charsets.US_ASCII))

        outDir.deleteRecursively()
    }

    @Test
    fun `music sfx engine generates valid RIFF WAV with pauses and envelope`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workDir = File(context.cacheDir, "test_wav_${System.currentTimeMillis()}").apply { mkdirs() }
        val lineWav = File(workDir, "line_0000.wav")
        writeDummyPcmWav(lineWav, durationMs = 1000L)

        val chars = listOf(
            CharacterProfile("1", "Elena", "Entrevistadora", "Adulto", "", "", 1.05f, 1.0f, TimbreFilter.WARM_STUDIO),
            CharacterProfile("2", "Mateo", "Testigo", "Maduro", "", "", 0.80f, 0.90f, TimbreFilter.DEEP_CHAMBER)
        )
        val lines = listOf(
            DialogueLine(
                index = 0,
                actNumber = 1,
                sceneNumber = 1,
                speakerName = "Elena",
                emotion = "Tensión",
                actingNote = "En voz baja",
                text = "¿Escuchaste la puerta cerrarse?",
                pauseAfterMs = 800L,
                sfxCue = "HEARTBEAT",
                musicMood = "TERROR_DRONE",
                synthesizedWavPath = lineWav.absolutePath,
                actualDurationMs = 1000L
            )
        )

        val masterWav = File(workDir, "master.wav")
        val mixResult = MusicSfxEngine().mixMasterWav(lines, chars, masterWav) {}

        assertTrue(masterWav.exists())
        assertTrue(masterWav.length() > 44L)
        assertTrue(mixResult.totalDurationMs >= 3000L)
        val durationRead = VoiceAudioEngine.readWavDurationMs(masterWav)
        assertTrue(durationRead >= 3000L)
        assertTrue(mixResult.envelope100ms.any { it > 0f })

        workDir.deleteRecursively()
    }

    @Test
    fun `repository supports interruption recovery and strict artifact verification`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MohanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = CreationLibraryRepository(context, db.productionDao(), db.creationsDao())

        val proj = repo.createInitialProject(
            ideaPrompt = "Interrogatorio en cabina cerrada",
            genre = ContentGenre.INTERROGATION_TESTIMONY,
            outputFormat = OutputFormat.VIDEOPODCAST_40MIN,
            resolution = VideoResolution.FULL_HD_1080P,
            targetMinDurationMinutes = 40
        )

        // Should appear in recoverable projects before completion
        val recoverable = repo.getRecoverableProjects()
        assertTrue(recoverable.any { it.id == proj.id })

        // Strict verification rejects non-existent files
        val fakeArtifact = ExportArtifact(ArtifactFormat.MP4, "/nonexistent/fake.mp4", 99999L)
        val verifiedFake = repo.verifyExistingArtifacts(ExportArtifact.listToJson(listOf(fakeArtifact)))
        assertTrue(verifiedFake.isEmpty())

        // Create real file and verify it passes
        val realFile = File(proj.directoryPath, "real_test.txt").apply { writeText("MOHANA OK") }
        val realArtifact = ExportArtifact(ArtifactFormat.TXT, realFile.absolutePath, realFile.length())
        val verifiedReal = repo.verifyExistingArtifacts(ExportArtifact.listToJson(listOf(realArtifact)))
        assertEquals(1, verifiedReal.size)
        assertFalse(verifiedReal.first().fileSizeBytes <= 0L)

        // Mark completed and ensure it leaves recoverable list
        repo.saveCheckpoint(proj.copy(stageName = ProductionStage.COMPLETED.name, progressPercent = 100))
        assertTrue(repo.getRecoverableProjects().none { it.id == proj.id })

        repo.deleteProject(proj.id)
        db.close()
    }

    private fun writeDummyPcmWav(file: File, durationMs: Long) {
        val sr = 22050
        val samples = ((sr * durationMs) / 1000L).toInt()
        val dataSize = samples * 2
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, dataSize + 36)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        header[20] = 1; header[21] = 0
        header[22] = 1; header[23] = 0
        writeIntLE(header, 24, sr)
        writeIntLE(header, 28, sr * 2)
        header[32] = 2; header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, dataSize)

        FileOutputStream(file).use { out ->
            out.write(header)
            val pcm = ByteArray(dataSize)
            for (i in 0 until samples) {
                val v = (kotlin.math.sin(2.0 * Math.PI * 220.0 * i / sr) * 12000.0).toInt()
                pcm[i * 2] = (v and 0xFF).toByte()
                pcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
            out.write(pcm)
        }
    }

    private fun writeIntLE(b: ByteArray, offset: Int, v: Int) {
        b[offset] = (v and 0xFF).toByte()
        b[offset + 1] = ((v shr 8) and 0xFF).toByte()
        b[offset + 2] = ((v shr 16) and 0xFF).toByte()
        b[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }
}
