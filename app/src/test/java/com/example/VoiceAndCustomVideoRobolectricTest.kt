package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.DialogueLine
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProsodyPacing
import com.example.domain.model.TimbreFilter
import com.example.domain.model.VoiceEmotionPreset
import com.example.engines.GeminiNeuralVoiceEngine
import com.example.engines.LiveProductionTelemetry
import com.example.engines.MusicSfxEngine
import com.example.engines.VoiceAudioEngine
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VoiceProfileSelectionScreen
import com.example.ui.theme.MohanaAiTheme
import com.example.ui.viewmodel.StudioFormState
import com.example.ui.viewmodel.StudioStep
import com.example.ui.viewmodel.defaultCastForGenre
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceAndCustomVideoRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun writePcm16Wav(
        sampleRate: Int,
        samples: ShortArray,
        reportedDataChunkSize: Int? = null,
        includeListMetadataChunk: Boolean = false
    ): ByteArray {
        val pcmBytes = ByteArray(samples.size * 2)
        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) bb.putShort(s)

        val listChunkBytes = if (includeListMetadataChunk) {
            val payload = "INFO".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 0, 0)
            ByteArrayOutputStream().apply {
                write("LIST".toByteArray(Charsets.US_ASCII))
                write(intLE(payload.size))
                write(payload)
            }.toByteArray()
        } else {
            ByteArray(0)
        }

        val dataSizeHeader = reportedDataChunkSize ?: pcmBytes.size
        val riffChunkSize = 4 + 24 + listChunkBytes.size + 8 + pcmBytes.size

        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(intLE(riffChunkSize))
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII))
            write(intLE(16))
            write(shortLE(1)) // PCM
            write(shortLE(1)) // Mono
            write(intLE(sampleRate))
            write(intLE(sampleRate * 2))
            write(shortLE(2))
            write(shortLE(16))
            write(listChunkBytes)
            write("data".toByteArray(Charsets.US_ASCII))
            write(intLE(dataSizeHeader))
            write(pcmBytes)
        }.toByteArray()
    }

    private fun writeFloat32Wav(sampleRate: Int, samples: FloatArray): ByteArray {
        val pcmBytes = ByteArray(samples.size * 4)
        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) bb.putFloat(s)

        val riffChunkSize = 4 + 24 + 8 + pcmBytes.size
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            write(intLE(riffChunkSize))
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII))
            write(intLE(16))
            write(shortLE(3)) // IEEE Float
            write(shortLE(1))
            write(intLE(sampleRate))
            write(intLE(sampleRate * 4))
            write(shortLE(4))
            write(shortLE(32))
            write("data".toByteArray(Charsets.US_ASCII))
            write(intLE(pcmBytes.size))
            write(pcmBytes)
        }.toByteArray()
    }

    private fun intLE(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun shortLE(v: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()

    @Test
    fun `decodeWavToMonoFloat parses unpatched minus-one and zero data chunk sizes plus LIST chunks and float32`() {
        val sr = 22050
        val tone = ShortArray(sr / 2) { i ->
            (sin(2.0 * PI * 320.0 * i / sr) * 9000.0).toInt().toShort()
        }

        // Case 1: TTS streaming WAV header with data size = -1 (0xFFFFFFFF) and LIST chunk
        val unpatchedMinusOne = writePcm16Wav(
            sampleRate = sr,
            samples = tone,
            reportedDataChunkSize = -1,
            includeListMetadataChunk = true
        )
        val decoded1 = MusicSfxEngine.decodeWavToMonoFloat(unpatchedMinusOne)
        assertNotNull(decoded1)
        assertEquals(sr, decoded1!!.first)
        assertEquals(tone.size, decoded1.second.size)
        assertTrue(decoded1.second.maxOf { abs(it) } > 0.2f)

        // Case 2: TTS streaming WAV header with data size = 0
        val unpatchedZero = writePcm16Wav(
            sampleRate = sr,
            samples = tone,
            reportedDataChunkSize = 0
        )
        val decoded2 = MusicSfxEngine.decodeWavToMonoFloat(unpatchedZero)
        assertNotNull(decoded2)
        assertEquals(tone.size, decoded2!!.second.size)
        assertTrue(decoded2.second.maxOf { abs(it) } > 0.2f)

        // Case 3: 32-bit IEEE float PCM
        val floatSamples = FloatArray(sr / 4) { i ->
            (sin(2.0 * PI * 440.0 * i / sr) * 0.45).toFloat()
        }
        val floatWav = writeFloat32Wav(sr, floatSamples)
        val decoded3 = MusicSfxEngine.decodeWavToMonoFloat(floatWav)
        assertNotNull(decoded3)
        assertEquals(floatSamples.size, decoded3!!.second.size)
        assertTrue(decoded3.second.maxOf { abs(it) } > 0.35f)
    }

    @Test
    fun `hasAudibleSpeech rejects all-zero silence and accepts valid TTS wav including unpatched header`() {
        val tempDir = createTempDir(prefix = "mohana_voice_test_")
        try {
            val sr = 22050
            val silentFile = File(tempDir, "silent.wav").apply {
                writeBytes(writePcm16Wav(sr, ShortArray(sr / 2) { 0 }))
            }
            assertFalse("Silent WAV must be rejected", VoiceAudioEngine.hasAudibleSpeech(silentFile))

            val tone = ShortArray(sr / 2) { i ->
                (sin(2.0 * PI * 280.0 * i / sr) * 6000.0).toInt().toShort()
            }
            val unpatchedVoiceFile = File(tempDir, "voice_unpatched.wav").apply {
                writeBytes(writePcm16Wav(sr, tone, reportedDataChunkSize = -1))
            }
            assertTrue("Audible WAV with -1 data chunk must pass", VoiceAudioEngine.hasAudibleSpeech(unpatchedVoiceFile))
            assertTrue(VoiceAudioEngine.readWavDurationMs(unpatchedVoiceFile) in 450L..550L)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `mixMasterWav mixes strong vocal audio into master wav even when TTS voice wav has unpatched header`() = runBlocking {
        val tempDir = createTempDir(prefix = "mohana_mix_test_")
        try {
            val sr = 22050
            val voiceSamples = ShortArray(sr) { i ->
                // Soft TTS signal (~12% full scale) with unpatched 0xFFFFFFFF data size
                (sin(2.0 * PI * 350.0 * i / sr) * 3800.0).toInt().toShort()
            }
            val voiceWav = File(tempDir, "line_0000.wav").apply {
                writeBytes(writePcm16Wav(sr, voiceSamples, reportedDataChunkSize = -1))
            }
            val masterWav = File(tempDir, "MOHANA_Audio_Maestro.wav")

            val lines = listOf(
                DialogueLine(
                    index = 0,
                    actNumber = 1,
                    sceneNumber = 1,
                    speakerName = "Elena",
                    emotion = "serena",
                    actingNote = "",
                    text = "Prueba de voz nítida en mezcla maestra.",
                    pauseAfterMs = 300L,
                    sfxCue = "NONE",
                    musicMood = "DARK_AMBIENT",
                    synthesizedWavPath = voiceWav.absolutePath,
                    actualDurationMs = 1000L
                )
            )
            val characters = listOf(
                CharacterProfile(
                    id = "c1",
                    name = "Elena",
                    role = "Conductora",
                    ageStyle = "Adulta",
                    personality = "Serena",
                    visualDescription = "Estudio",
                    voicePitch = 1.0f,
                    voiceRate = 1.0f,
                    timbreFilter = TimbreFilter.CRISP_NARRATOR
                )
            )

            val engine = MusicSfxEngine()
            val result = engine.mixMasterWav(lines, characters, masterWav) {}

            assertTrue(masterWav.exists() && masterWav.length() > 44L)
            assertTrue(result.totalDurationMs >= 2000L)

            // Inspect master WAV during the vocal window (around 900ms..1900ms) -> RMS should be prominent
            val decodedMaster = MusicSfxEngine.decodeWavToMonoFloat(masterWav.readBytes(), normalizeSpeech = false)
            assertNotNull(decodedMaster)
            val pcm = decodedMaster!!.second
            val startIdx = (0.95 * MusicSfxEngine.TARGET_SAMPLE_RATE).toInt()
            val endIdx = (1.85 * MusicSfxEngine.TARGET_SAMPLE_RATE).toInt().coerceAtMost(pcm.size)
            var sumSq = 0.0
            for (i in startIdx until endIdx) {
                val s = pcm[i].toDouble()
                sumSq += s * s
            }
            val vocalWindowRms = sqrt(sumSq / (endIdx - startIdx).coerceAtLeast(1))
            assertTrue("Vocal window RMS ($vocalWindowRms) must contain audible normalized speech", vocalWindowRms > 0.15)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `studio step 1 displays custom video picker card and clear button updates state`() {
        var cleared = false
        var formState by mutableStateOf(
            StudioFormState(
                currentStep = StudioStep.STEP_1_IDEA,
                selectedFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
                customVideoPath = "/tmp/mi_video_galeria.mp4",
                customVideoLabel = "mi_video_galeria.mp4 (00:18 · 1920x1080)"
            )
        )

        composeRule.setContent {
            MohanaAiTheme {
                StudioScreen(
                    formState = formState,
                    activeProject = null,
                    verifiedArtifacts = emptyList(),
                    telemetry = LiveProductionTelemetry(),
                    capabilities = null,
                    recoverableProjects = emptyList(),
                    isProducing = false,
                    onUpdateIdea = {},
                    onSelectGenre = {},
                    onSelectFormat = {},
                    onSelectResolution = {},
                    onUpdatePodcastMinutes = {},
                    onClearCustomVideo = {
                        cleared = true
                        formState = formState.copy(customVideoPath = null, customVideoLabel = null)
                    },
                    onApplyPreset = { _, _, _ -> },
                    onSelectStep = {},
                    onStartProduction = {},
                    onPauseProduction = {},
                    onResumeProject = {},
                    onEnableTypographyFallback = {},
                    onShareArtifact = { _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pick_custom_video_button")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText("Vídeo activo: mi_video_galeria.mp4 (00:18 · 1920x1080). Se integrará con voces WAV, música y subtítulos sin generar escenas IA.")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("clear_custom_video_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitForIdle()
        assertTrue(cleared)
        composeRule.onNodeWithTag("clear_custom_video_button").assertDoesNotExist()
    }

    @Test
    fun `gemini neural voice engine synthesizes audible wav and default cast avoids robotic pitch distortion`() = runBlocking {
        val defaultCast = defaultCastForGenre(ContentGenre.INTERVIEW_CONFESSION)
        assertTrue(defaultCast.all { GeminiNeuralVoiceEngine.isGeminiVoiceName(it.preferredVoiceName) })
        assertTrue(defaultCast.all { it.voicePitch == 1.0f && it.voiceRate == 1.0f })

        val options = GeminiNeuralVoiceEngine.toTtsVoiceOptions()
        assertTrue(options.size >= 6)
        assertTrue(options.all { GeminiNeuralVoiceEngine.isGeminiVoiceName(it.voiceName) })

        val capturedRequests = mutableListOf<Pair<String, String>>()
        val sr = 24000
        val sampleShorts = ShortArray(sr / 2) { i ->
            (sin(2.0 * PI * 240.0 * i / sr) * 11000.0).toInt().toShort()
        }
        val rawPcmBytes = ByteArray(sampleShorts.size * 2)
        val bb = ByteBuffer.wrap(rawPcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in sampleShorts) bb.putShort(s)

        val neuralEngine = GeminiNeuralVoiceEngine { prompt, rawVoice ->
            capturedRequests.add(rawVoice to prompt)
            "audio/L16;codec=pcm;rate=24000" to rawPcmBytes
        }

        val tempDir = createTempDir(prefix = "mohana_neural_test_")
        try {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val voiceEngine = VoiceAudioEngine(context, neuralEngine)
            val lines = listOf(
                DialogueLine(
                    index = 0,
                    actNumber = 1,
                    sceneNumber = 1,
                    speakerName = "Mateo",
                    emotion = "Susurro",
                    actingNote = "Tono cercano e íntimo",
                    text = "Escucha bien lo que quedó en la cinta.",
                    pauseAfterMs = 400L,
                    sfxCue = "NONE",
                    musicMood = "MYSTERY_AMBIENT"
                )
            )

            val result = voiceEngine.synthesizeAllLines(lines, defaultCast, tempDir) { _, _, _ -> }

            assertEquals(1, result.size)
            assertEquals("Charon", capturedRequests.first().first)
            assertTrue(capturedRequests.first().second.contains("[whispering]"))
            val outWav = File(result.first().synthesizedWavPath!!)
            assertTrue(outWav.exists() && VoiceAudioEngine.hasAudibleSpeech(outWav))
            assertTrue(result.first().actualDurationMs in 450L..550L)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `voice profile selection screen configures emotion prosody filters and auditions natural models`() {
        var cast by mutableStateOf(defaultCastForGenre(ContentGenre.INTERVIEW_CONFESSION))
        var auditionedVoiceId: String? = null
        var auditionedCustomPhrase: String? = null
        var returnedToStudio = false
        val allVoices = GeminiNeuralVoiceEngine.toTtsVoiceOptions()

        composeRule.setContent {
            MohanaAiTheme {
                VoiceProfileSelectionScreen(
                    characters = cast,
                    initialCharacterId = cast.first().id,
                    availableVoices = allVoices,
                    isTtsReady = true,
                    ttsStatusMessage = "8 voces IA humanas (Gemini 3.1) listas",
                    activePreviewId = null,
                    previewError = null,
                    onUpdateCharacter = { updated ->
                        cast = cast.map { if (it.id == updated.id) updated else it }
                    },
                    onAuditionVoice = { _, voice, phrase ->
                        auditionedVoiceId = voice.voiceName
                        auditionedCustomPhrase = phrase
                    },
                    onAuditionExpression = { _, phrase ->
                        auditionedCustomPhrase = phrase
                    },
                    onStopPreview = {},
                    onDismissError = {},
                    onBack = { returnedToStudio = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("voice_profile_selection_screen").assertIsDisplayed()

        // Select emotional preset and prosody cadence
        composeRule.onNodeWithTag("voice_profile_list")
            .performScrollToNode(hasTestTag("emotion_preset_${VoiceEmotionPreset.DRAMATIC_TENSE.name}"))
        composeRule.onNodeWithTag("emotion_preset_${VoiceEmotionPreset.DRAMATIC_TENSE.name}")
            .performClick()

        composeRule.onNodeWithTag("voice_profile_list")
            .performScrollToNode(hasTestTag("prosody_pacing_${ProsodyPacing.PAUSED.name}"))
        composeRule.onNodeWithTag("prosody_pacing_${ProsodyPacing.PAUSED.name}")
            .performClick()
        composeRule.waitForIdle()

        assertEquals(VoiceEmotionPreset.DRAMATIC_TENSE, cast.first().emotionPreset)
        assertEquals(ProsodyPacing.PAUSED, cast.first().prosodyPacing)

        // Filter to Male neural models and select Charon
        composeRule.onNodeWithTag("voice_profile_list")
            .performScrollToNode(hasTestTag("filter_voice_MALE"))
        composeRule.onNodeWithTag("filter_voice_MALE")
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("voice_profile_list")
            .performScrollToNode(hasTestTag("profile_select_gemini:Charon"))
        composeRule.onNodeWithTag("profile_select_gemini:Charon")
            .performClick()
        composeRule.waitForIdle()
        assertEquals("gemini:Charon", cast.first().preferredVoiceName)

        // Audition Charon voice model
        composeRule.onNodeWithTag("voice_profile_list")
            .performScrollToNode(hasTestTag("profile_audition_gemini:Charon"))
        composeRule.onNodeWithTag("profile_audition_gemini:Charon")
            .performClick()
        composeRule.waitForIdle()
        assertEquals("gemini:Charon", auditionedVoiceId)
        assertTrue(!auditionedCustomPhrase.isNullOrBlank())

        // Confirm and return to studio
        composeRule.onNodeWithTag("confirm_voice_profile_button")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(returnedToStudio)
    }
}
