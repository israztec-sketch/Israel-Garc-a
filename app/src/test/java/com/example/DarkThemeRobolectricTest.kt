package com.example

import android.content.Context
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CreationLibraryRepository
import com.example.data.local.CreationMetadataEntity
import com.example.data.local.MohanaDatabase
import com.example.domain.model.ContentGenre
import com.example.domain.model.DialogueLine
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionScript
import com.example.domain.model.VideoResolution
import com.example.ui.screens.CreationDetailScreen
import com.example.ui.screens.CreationsScreen
import com.example.ui.theme.AmberGold
import com.example.ui.theme.MohanaAiTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DarkThemeRobolectricTest {

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
    }

    @Test
    fun `global Material3 dark theme enforces low-light luminance hierarchy and high contrast ratios`() {
        var capturedScheme: ColorScheme? = null
        var capturedHandleColor: Color? = null

        composeRule.setContent {
            MohanaAiTheme {
                capturedScheme = MaterialTheme.colorScheme
                capturedHandleColor = LocalTextSelectionColors.current.handleColor
            }
        }
        composeRule.waitForIdle()

        val scheme = requireNotNull(capturedScheme)
        // Low-glare obsidian backgrounds for dim rooms
        assertTrue("Background luminance must be < 0.05", scheme.background.luminance() < 0.05f)
        assertTrue("Surface luminance must be < 0.05", scheme.surface.luminance() < 0.05f)

        // Monotonic surface container elevation hierarchy
        assertTrue(scheme.surfaceContainerLowest.luminance() < scheme.surfaceContainerLow.luminance())
        assertTrue(scheme.surfaceContainerLow.luminance() < scheme.surfaceContainer.luminance())
        assertTrue(scheme.surfaceContainer.luminance() < scheme.surfaceContainerHigh.luminance())
        assertTrue(scheme.surfaceContainerHigh.luminance() < scheme.surfaceContainerHighest.luminance())

        // High-contrast legibility ratios for editing and library controls in low-light
        assertTrue(
            "onBackground vs background contrast >= 7.0",
            contrastRatio(scheme.onBackground, scheme.background) >= 7.0
        )
        assertTrue(
            "onSurface vs surfaceContainerHigh contrast >= 7.0",
            contrastRatio(scheme.onSurface, scheme.surfaceContainerHigh) >= 7.0
        )
        assertTrue(
            "onSurfaceVariant vs surfaceContainerHighest contrast >= 4.5",
            contrastRatio(scheme.onSurfaceVariant, scheme.surfaceContainerHighest) >= 4.5
        )
        assertTrue(
            "onPrimary vs primary contrast >= 4.5",
            contrastRatio(scheme.onPrimary, scheme.primary) >= 4.5
        )
        assertTrue(
            "onPrimaryContainer vs primaryContainer contrast >= 4.5",
            contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5
        )
        assertTrue(
            "onErrorContainer vs errorContainer contrast >= 4.5",
            contrastRatio(scheme.onErrorContainer, scheme.errorContainer) >= 4.5
        )
        assertEquals(AmberGold, capturedHandleColor)
    }

    @Test
    fun `creations library and detail editor controls render visibly under MohanaAiTheme`() {
        val project = runBlocking {
            val base = repository.createInitialProject(
                ideaPrompt = "Entrevista en la cabina de control subterránea",
                genre = ContentGenre.INTERVIEW_CONFESSION,
                outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
                resolution = VideoResolution.FULL_HD_1080P,
                targetMinDurationMinutes = 10
            )
            val script = ProductionScript(
                title = "Cabina Subterránea",
                logline = "Un operador nocturno confiesa lo que escuchó en el nivel -4.",
                characterVisualBible = "Luz ámbar tenue sobre consola analógica.",
                totalPlannedActs = 1,
                completedActs = 1,
                actOutlines = listOf("Acto I"),
                characters = emptyList(),
                lines = listOf(
                    DialogueLine(
                        index = 0,
                        actNumber = 1,
                        sceneNumber = 1,
                        speakerName = "Entrevistador",
                        emotion = "Tenso",
                        actingNote = "En voz baja",
                        text = "¿A qué hora dejó de responder el transmisor?",
                        pauseAfterMs = 500L,
                        sfxCue = "NONE",
                        musicMood = "DARK_AMBIENT"
                    )
                ),
                scenes = emptyList(),
                chapters = emptyList()
            )
            val withScript = base.copy(title = script.title, scriptJson = script.toJson())
            db.productionDao().upsertProject(withScript)
            withScript
        }

        val now = System.currentTimeMillis()
        val meta = CreationMetadataEntity(
            creationId = project.id,
            title = project.title,
            logline = "Un operador nocturno confiesa lo que escuchó en el nivel -4.",
            genreName = ContentGenre.INTERVIEW_CONFESSION.name,
            outputFormatName = OutputFormat.AUDIO_VIDEO_EPISODE.name,
            wordCount = 120,
            characterCount = 2,
            sceneCount = 1,
            transcriptLineCount = 1,
            estimatedDurationMs = 60_000L,
            isFavorite = true,
            userNotes = "Revisar nivel de música en escena 1",
            createdAt = now,
            updatedAt = now
        )

        composeRule.setContent {
            MohanaAiTheme {
                CreationsScreen(
                    projects = listOf(project),
                    metadataList = listOf(meta),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onToggleFavorite = { _, _ -> },
                    verifyArtifacts = { emptyList() },
                    onOpenProject = {},
                    onResumeProject = {},
                    onDeleteProject = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("creations_search_input").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_chip_favorites").assertIsDisplayed()
        composeRule.onNodeWithTag("creation_card_${project.id}").assertIsDisplayed()
        composeRule.onNodeWithText("1 de 1").assertIsDisplayed()
    }

    @Test
    fun `detail editor controls render visibly in dark edit mode under MohanaAiTheme`() {
        val project = runBlocking {
            val base = repository.createInitialProject(
                ideaPrompt = "Entrevista en la cabina de control subterránea",
                genre = ContentGenre.INTERVIEW_CONFESSION,
                outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
                resolution = VideoResolution.FULL_HD_1080P,
                targetMinDurationMinutes = 10
            )
            val script = ProductionScript(
                title = "Cabina Subterránea",
                logline = "Un operador nocturno confiesa lo que escuchó en el nivel -4.",
                characterVisualBible = "Luz ámbar tenue sobre consola analógica.",
                totalPlannedActs = 1,
                completedActs = 1,
                actOutlines = listOf("Acto I"),
                characters = emptyList(),
                lines = listOf(
                    DialogueLine(
                        index = 0,
                        actNumber = 1,
                        sceneNumber = 1,
                        speakerName = "Entrevistador",
                        emotion = "Tenso",
                        actingNote = "En voz baja",
                        text = "¿A qué hora dejó de responder el transmisor?",
                        pauseAfterMs = 500L,
                        sfxCue = "NONE",
                        musicMood = "DARK_AMBIENT"
                    )
                ),
                scenes = emptyList(),
                chapters = emptyList()
            )
            val withScript = base.copy(title = script.title, scriptJson = script.toJson())
            db.productionDao().upsertProject(withScript)
            withScript
        }

        val now = System.currentTimeMillis()
        val meta = CreationMetadataEntity(
            creationId = project.id,
            title = project.title,
            logline = "Un operador nocturno confiesa lo que escuchó en el nivel -4.",
            genreName = ContentGenre.INTERVIEW_CONFESSION.name,
            outputFormatName = OutputFormat.AUDIO_VIDEO_EPISODE.name,
            wordCount = 120,
            characterCount = 2,
            sceneCount = 1,
            transcriptLineCount = 1,
            estimatedDurationMs = 60_000L,
            isFavorite = true,
            userNotes = "Revisar nivel de música en escena 1",
            createdAt = now,
            updatedAt = now
        )

        composeRule.setContent {
            MohanaAiTheme {
                CreationDetailScreen(
                    project = project,
                    metadata = meta,
                    verifiedArtifacts = emptyList(),
                    onBack = {},
                    onSaveEdit = { _, _ -> },
                    onResumeProject = {},
                    onShareArtifact = { _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("detail_user_notes_card").assertIsDisplayed()
        composeRule.onNodeWithTag("detail_notes_input").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_edit_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("project_editor_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("editor_title_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Edición activa").assertIsDisplayed()
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = fg.luminance().toDouble()
        val l2 = bg.luminance().toDouble()
        val brighter = max(l1, l2)
        val darker = min(l1, l2)
        return (brighter + 0.05) / (darker + 0.05)
    }
}
