package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CreationLibraryRepository
import com.example.data.local.MohanaDatabase
import com.example.domain.model.ContentGenre
import com.example.domain.model.OutputFormat
import com.example.domain.model.VideoResolution
import com.example.ui.screens.CreationDetailScreen
import com.example.ui.screens.CreationsScreen
import com.example.ui.theme.MohanaAiTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeleteConfirmationDialogRobolectricTest {

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
    fun `creations screen requires confirmation before deleting from Room and cancels safely`() {
        val project = runBlocking {
            repository.createInitialProject(
                ideaPrompt = "Cinta de vigilancia del piso 9",
                genre = ContentGenre.CREEPYPASTA,
                outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
                resolution = VideoResolution.FULL_HD_1080P,
                targetMinDurationMinutes = 5
            )
        }

        composeRule.setContent {
            MohanaAiTheme {
                val projects by repository.allProjects.collectAsState(initial = emptyList())
                val metadata by repository.allMetadata.collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()

                CreationsScreen(
                    projects = projects,
                    metadataList = metadata,
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onToggleFavorite = { id, fav -> scope.launch { repository.toggleFavorite(id, fav) } },
                    verifyArtifacts = repository::verifyExistingArtifacts,
                    onOpenProject = {},
                    onResumeProject = {},
                    onDeleteProject = { id -> scope.launch { repository.deleteProject(id) } }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("creation_card_${project.id}").assertIsDisplayed()

        // Step 1: Click delete icon -> dialog appears, item is NOT deleted yet
        composeRule.onNodeWithTag("delete_project_button_${project.id}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delete_confirmation_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("¿Eliminar esta creación?").assertIsDisplayed()
        composeRule.onNodeWithText("Esta acción no se puede deshacer.").assertIsDisplayed()

        // Step 2: Cancel deletion -> dialog closes and item remains in Room
        composeRule.onNodeWithTag("cancel_delete_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delete_confirmation_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("creation_card_${project.id}").assertIsDisplayed()
        runBlocking {
            assertNotNull(db.productionDao().getProjectById(project.id))
            assertNotNull(db.creationsDao().getMetadataById(project.id))
        }

        // Step 3: Click delete icon again and confirm -> removes item from Room
        composeRule.onNodeWithTag("delete_project_button_${project.id}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_delete_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("delete_confirmation_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("creation_card_${project.id}").assertDoesNotExist()
        runBlocking {
            assertNull(db.productionDao().getProjectById(project.id))
            assertNull(db.creationsDao().getMetadataById(project.id))
        }
    }

    @Test
    fun `detail screen shows delete confirmation dialog and respects cancel vs confirm`() {
        val project = runBlocking {
            repository.createInitialProject(
                ideaPrompt = "Entrevista en la estación fantasma",
                genre = ContentGenre.INTERVIEW_CONFESSION,
                outputFormat = OutputFormat.AUDIO_DRAMA_WAV,
                resolution = VideoResolution.FULL_HD_1080P,
                targetMinDurationMinutes = 4
            )
        }
        var deleteCount = 0

        composeRule.setContent {
            MohanaAiTheme {
                CreationDetailScreen(
                    project = project,
                    verifiedArtifacts = emptyList(),
                    onBack = {},
                    onSaveEdit = { _, _ -> },
                    onResumeProject = {},
                    onShareArtifact = { _, _ -> },
                    onDeleteProject = { deleteCount++ }
                )
            }
        }

        composeRule.waitForIdle()

        // Tap detail delete -> cancel preserves count = 0
        composeRule.onNodeWithTag("detail_delete_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delete_confirmation_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_delete_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delete_confirmation_dialog").assertDoesNotExist()
        assertEquals(0, deleteCount)

        // Tap detail delete -> confirm triggers onDeleteProject once
        composeRule.onNodeWithTag("detail_delete_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_delete_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("delete_confirmation_dialog").assertDoesNotExist()
        assertEquals(1, deleteCount)
    }
}
