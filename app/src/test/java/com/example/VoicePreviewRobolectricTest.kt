package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CreationLibraryRepository
import com.example.data.local.MohanaDatabase
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.OutputFormat
import com.example.domain.model.TimbreFilter
import com.example.domain.model.TtsVoiceOption
import com.example.domain.model.VideoResolution
import com.example.engines.TtsPreviewBackend
import com.example.engines.VoicePreviewEngine
import com.example.ui.components.CharacterVoiceSelectorSection
import com.example.ui.theme.MohanaAiTheme
import com.example.ui.viewmodel.defaultCastForGenre
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoicePreviewRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeTtsBackend(
        private val voices: List<TtsVoiceOption>
    ) : TtsPreviewBackend {
        val spokenRequests = mutableListOf<Triple<String?, Float, Float>>()
        var stopCount = 0
        var lastOnCompleted: ((String) -> Unit)? = null
        var lastUtteranceId: String? = null

        override fun initialize(
            onReady: (success: Boolean, voices: List<TtsVoiceOption>, message: String) -> Unit
        ) {
            onReady(true, voices, "${voices.size} voces listas")
        }

        override fun speak(
            text: String,
            voiceName: String?,
            pitch: Float,
            rate: Float,
            utteranceId: String,
            onStarted: (String) -> Unit,
            onCompleted: (String) -> Unit,
            onError: (String, String) -> Unit
        ): Boolean {
            spokenRequests.add(Triple(voiceName, pitch, rate))
            lastUtteranceId = utteranceId
            lastOnCompleted = onCompleted
            onStarted(utteranceId)
            return true
        }

        override fun stop() {
            stopCount++
        }

        override fun shutdown() = Unit
    }

    @Test
    fun `voice preview engine plays stop toggles and completes utterances`() {
        val voiceA = TtsVoiceOption("es-es-x-ana-local", "Voz 1 · España (ana)", "es-ES", true, "Alta")
        val voiceB = TtsVoiceOption("es-mx-x-carlos-local", "Voz 2 · México (carlos)", "es-MX", true, "Alta")
        val backend = FakeTtsBackend(listOf(voiceA, voiceB))
        val engine = VoicePreviewEngine(backend)

        engine.initialize()
        assertTrue(engine.isTtsReady.value)
        assertEquals(2, engine.availableVoices.value.size)

        // Play voice A
        engine.previewVoice("char_1", voiceA.voiceName, "Prueba A", pitch = 1.05f, rate = 1.0f)
        assertEquals("char_1", engine.activePreviewId.value)
        assertEquals(1, backend.spokenRequests.size)
        assertEquals(voiceA.voiceName, backend.spokenRequests[0].first)
        assertEquals(1.05f, backend.spokenRequests[0].second)

        // Tapping same preview ID stops playback
        engine.previewVoice("char_1", voiceA.voiceName, "Prueba A", pitch = 1.05f, rate = 1.0f)
        assertNull(engine.activePreviewId.value)
        assertEquals(1, backend.stopCount)

        // Play voice B and complete utterance via listener
        engine.previewVoice("char_2", voiceB.voiceName, "Prueba B", pitch = 0.82f, rate = 0.92f)
        assertEquals("char_2", engine.activePreviewId.value)
        backend.lastOnCompleted?.invoke(backend.lastUtteranceId!!)
        assertNull(engine.activePreviewId.value)
    }

    @Test
    fun `character selection UI previews each available voice and toggles stop state`() {
        val voiceA = TtsVoiceOption("es-es-x-ana-local", "Voz 1 · España (ana)", "es-ES", true, "Alta")
        val voiceB = TtsVoiceOption("es-mx-x-carlos-local", "Voz 2 · México (carlos)", "es-MX", true, "Alta")
        val backend = FakeTtsBackend(listOf(voiceA, voiceB))
        val engine = VoicePreviewEngine(backend).apply { initialize() }

        var characters by mutableStateOf(defaultCastForGenre(ContentGenre.INTERVIEW_CONFESSION))

        composeRule.setContent {
            MohanaAiTheme {
                val voices by engine.availableVoices.collectAsState()
                val ready by engine.isTtsReady.collectAsState()
                val status by engine.ttsStatusMessage.collectAsState()
                val activeId by engine.activePreviewId.collectAsState()
                val error by engine.previewError.collectAsState()

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CharacterVoiceSelectorSection(
                        characters = characters,
                        availableVoices = voices,
                        isTtsReady = ready,
                        ttsStatusMessage = status,
                        activePreviewId = activeId,
                        previewError = error,
                        onUpdateCharacter = { updated ->
                            characters = characters.map { if (it.id == updated.id) updated else it }
                        },
                        onAddCharacter = {},
                        onRemoveCharacter = {},
                        onPreviewCharacter = { ch ->
                            engine.previewVoice(
                                previewId = "char_${ch.id}",
                                voiceName = ch.preferredVoiceName,
                                sampleText = "Muestra de ${ch.name}",
                                pitch = ch.voicePitch,
                                rate = ch.voiceRate
                            )
                        },
                        onPreviewCharacterVoiceOption = { ch, v ->
                            engine.previewVoice(
                                previewId = "char_voice_${ch.id}_${v.voiceName}",
                                voiceName = v.voiceName,
                                sampleText = "Muestra de ${ch.name} con ${v.displayName}",
                                pitch = ch.voicePitch,
                                rate = ch.voiceRate
                            )
                        },
                        onPreviewAvailableVoice = { v ->
                            engine.previewVoice(
                                previewId = "voice_${v.voiceName}",
                                voiceName = v.voiceName,
                                sampleText = "Muestra de ${v.displayName}"
                            )
                        },
                        onPreviewDefaultSystemVoice = {},
                        onDismissPreviewError = { engine.clearError() }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("character_selection_section").assertIsDisplayed()

        // Preview each available voice option directly
        composeRule.onNodeWithTag("preview_available_voice_${voiceA.voiceName}")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("voice_${voiceA.voiceName}", engine.activePreviewId.value)
        assertEquals(voiceA.voiceName, backend.spokenRequests.last().first)

        composeRule.onNodeWithTag("preview_available_voice_${voiceB.voiceName}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("voice_${voiceB.voiceName}", engine.activePreviewId.value)
        assertEquals(voiceB.voiceName, backend.spokenRequests.last().first)

        // Select Voice B for character 1 and preview inside character card
        composeRule.onNodeWithTag("select_voice_char_1_${voiceB.voiceName}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(voiceB.voiceName, characters.first().preferredVoiceName)

        composeRule.onNodeWithTag("preview_character_button_char_1")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("char_char_1", engine.activePreviewId.value)
        composeRule.onNodeWithText("Detener muestra de Elena").performScrollTo().assertIsDisplayed()

        // Click again to stop
        composeRule.onNodeWithTag("preview_character_button_char_1")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertNull(engine.activePreviewId.value)
        composeRule.onNodeWithText("Escuchar muestra de Elena").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `selected cast with preferredVoiceName persists through Room and MIGRATION_2_3`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MohanaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = CreationLibraryRepository(context, db.productionDao(), db.creationsDao())

        val cast = listOf(
            CharacterProfile(
                id = "c1",
                name = "Elena",
                role = "Entrevistadora",
                ageStyle = "Adulta",
                personality = "Serena",
                visualDescription = "Estudio",
                voicePitch = 1.05f,
                voiceRate = 1.0f,
                timbreFilter = TimbreFilter.WARM_STUDIO,
                preferredVoiceName = "es-es-x-ana-local"
            )
        )
        val project = repo.createInitialProject(
            ideaPrompt = "Prueba de reparto con voz elegida",
            genre = ContentGenre.INTERVIEW_CONFESSION,
            outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
            resolution = VideoResolution.FULL_HD_1080P,
            targetMinDurationMinutes = 5,
            selectedCast = cast
        )

        val stored = db.productionDao().getProjectById(project.id)
        assertNotNull(stored)
        val parsedCast = CharacterProfile.listFromJson(stored!!.selectedCastJson)
        assertEquals(1, parsedCast.size)
        assertEquals("es-es-x-ana-local", parsedCast[0].preferredVoiceName)
        db.close()

        // Verify MIGRATION_2_3 keeps v2 rows intact and initializes selectedCastJson
        val dbName = "migration_v2_v3_${System.currentTimeMillis()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(sqDb: SupportSQLiteDatabase) {
                        sqDb.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `production_projects` (
                                `id` TEXT NOT NULL PRIMARY KEY,
                                `title` TEXT NOT NULL,
                                `ideaPrompt` TEXT NOT NULL,
                                `genreName` TEXT NOT NULL,
                                `outputFormatName` TEXT NOT NULL,
                                `resolutionName` TEXT NOT NULL,
                                `targetMinDurationMinutes` INTEGER NOT NULL,
                                `stageName` TEXT NOT NULL,
                                `progressPercent` INTEGER NOT NULL,
                                `statusMessage` TEXT NOT NULL,
                                `errorMessage` TEXT,
                                `scriptJson` TEXT,
                                `artifactsJson` TEXT NOT NULL,
                                `directoryPath` TEXT NOT NULL,
                                `useTypographyVisualFallback` INTEGER NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        sqDb.execSQL(
                            """
                            INSERT INTO `production_projects` VALUES (
                                'v2-proj', 'Proyecto v2', 'Idea', 'CREEPYPASTA',
                                'AUDIO_DRAMA_WAV', 'FULL_HD_1080P', 4, 'COMPLETED', 100,
                                'Listo', NULL, NULL, '[]', '/tmp/v2-proj', 0, 1000, 2000
                            )
                            """.trimIndent()
                        )
                        MohanaDatabase.MIGRATION_1_2.migrate(sqDb)
                    }

                    override fun onUpgrade(sqDb: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        val upgraded = Room.databaseBuilder(context, MohanaDatabase::class.java, dbName)
            .addMigrations(
                MohanaDatabase.MIGRATION_1_2,
                MohanaDatabase.MIGRATION_2_3,
                MohanaDatabase.MIGRATION_3_4,
                MohanaDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()
        try {
            val row = upgraded.productionDao().getProjectById("v2-proj")
            assertNotNull(row)
            assertEquals("[]", row!!.selectedCastJson)
            assertEquals("", row.lastCompletedStep)
        } finally {
            upgraded.close()
            context.deleteDatabase(dbName)
        }
    }
}
