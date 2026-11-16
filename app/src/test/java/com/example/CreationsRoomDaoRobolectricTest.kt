package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CreationLibraryRepository
import com.example.data.local.MohanaDatabase
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.DialogueLine
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionScript
import com.example.domain.model.ProductionStage
import com.example.domain.model.TimbreFilter
import com.example.domain.model.VideoResolution
import com.example.domain.model.VisualScene
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreationsRoomDaoRobolectricTest {

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
    fun `repository persists script transcript lines metadata and supports search and favorites`() = runTest {
        val project = repository.createInitialProject(
            ideaPrompt = "Entrevista en cabina de radio abandonada",
            genre = ContentGenre.INTERVIEW_CONFESSION,
            outputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
            resolution = VideoResolution.FULL_HD_1080P,
            targetMinDurationMinutes = 10
        )

        val initialMeta = db.creationsDao().getMetadataById(project.id)
        assertNotNull(initialMeta)
        assertEquals(0, initialMeta!!.transcriptLineCount)

        val script = ProductionScript(
            title = "Frecuencia Cero: La Confesión",
            logline = "Un locutor entrevista a una voz que transmite desde un túnel clausurado.",
            characterVisualBible = "Cabina oscura con luz ámbar",
            totalPlannedActs = 2,
            completedActs = 2,
            actOutlines = listOf("Inicio de la transmisión", "Revelación final"),
            characters = listOf(
                CharacterProfile(
                    id = "c1",
                    name = "Clara",
                    role = "Entrevistadora",
                    ageStyle = "Adulta",
                    personality = "Serena e inquisitiva",
                    visualDescription = "Auriculares de estudio",
                    voicePitch = 1.05f,
                    voiceRate = 1.0f,
                    timbreFilter = TimbreFilter.WARM_STUDIO
                ),
                CharacterProfile(
                    id = "c2",
                    name = "Julián",
                    role = "Testigo",
                    ageStyle = "Maduro",
                    personality = "Cauteloso",
                    visualDescription = "Sombra tras el vidrio",
                    voicePitch = 0.82f,
                    voiceRate = 0.92f,
                    timbreFilter = TimbreFilter.DEEP_CHAMBER
                )
            ),
            lines = listOf(
                DialogueLine(
                    index = 0,
                    actNumber = 1,
                    sceneNumber = 1,
                    speakerName = "Clara",
                    emotion = "Misterio",
                    actingNote = "Tono bajo y cercano al micrófono",
                    text = "¿Desde qué estación estás transmitiendo esta señal?",
                    pauseAfterMs = 700L,
                    sfxCue = "RADIO_STATIC",
                    musicMood = "SUSPENSE_PULSE",
                    actualDurationMs = 3100L,
                    startTimeMs = 0L
                ),
                DialogueLine(
                    index = 1,
                    actNumber = 1,
                    sceneNumber = 1,
                    speakerName = "Julián",
                    emotion = "Confesión",
                    actingNote = "Respiración contenida",
                    text = "La estación del kilómetro catorce dejó de existir en los mapas hace veinte años.",
                    pauseAfterMs = 900L,
                    sfxCue = "HEARTBEAT",
                    musicMood = "TERROR_DRONE",
                    actualDurationMs = 4600L,
                    startTimeMs = 3800L
                )
            ),
            scenes = listOf(
                VisualScene(
                    sceneNumber = 1,
                    title = "Cabina nocturna",
                    settingDescription = "Consola analógica iluminada",
                    visualPrompt = "Dark radio booth with glowing VU meters",
                    mood = "NOIR_HORROR"
                )
            ),
            chapters = emptyList(),
            endingSummary = "La señal se corta justo al amanecer dejando una cinta grabada."
        )

        repository.saveCheckpoint(
            project.copy(
                title = script.title,
                stageName = ProductionStage.COMPLETED.name,
                progressPercent = 100,
                scriptJson = script.toJson()
            )
        )

        val details = repository.observeCreationWithDetails(project.id).first()
        assertNotNull(details)
        assertEquals("Frecuencia Cero: La Confesión", details!!.metadata.title)
        assertEquals(2, details.metadata.characterCount)
        assertEquals(1, details.metadata.sceneCount)
        assertEquals(2, details.metadata.transcriptLineCount)
        assertTrue(details.metadata.wordCount >= 18)
        assertNotNull(details.script)
        assertTrue(details.script!!.fullScriptMarkdown.contains("# Frecuencia Cero: La Confesión"))
        assertTrue(details.script!!.fullScriptMarkdown.contains("Clara"))
        assertEquals(2, details.transcriptLines.size)
        assertEquals("Julián", details.transcriptLines[1].speakerName)

        // Toggle favorite and save user notes
        repository.toggleFavorite(project.id, true)
        repository.updateUserNotes(project.id, "Revisar volumen de estática al inicio")

        val favList = repository.observeFavoriteMetadata().first()
        assertEquals(1, favList.size)
        assertEquals(project.id, favList.first().creationId)

        // Search by user notes and transcript content
        val noteSearchHits = repository.searchCreationMetadata("estática").first()
        assertEquals(1, noteSearchHits.size)

        val transcriptHits = repository.searchTranscriptLines(project.id, "kilómetro catorce")
        assertEquals(1, transcriptHits.size)
        assertEquals("Julián", transcriptHits.first().speakerName)

        // Edit script title and verify favorite & notes are preserved
        val editedScript = script.copy(
            lines = script.lines.map {
                if (it.index == 0) it.copy(text = "¿Desde qué estación subterránea transmites?") else it
            }
        )
        repository.updateScriptAndTitle(project.id, "Frecuencia Cero (Edición Director)", editedScript)

        val updatedMeta = db.creationsDao().getMetadataById(project.id)!!
        assertEquals("Frecuencia Cero (Edición Director)", updatedMeta.title)
        assertTrue(updatedMeta.isFavorite)
        assertEquals("Revisar volumen de estática al inicio", updatedMeta.userNotes)

        val updatedTranscript = repository.observeTranscript(project.id).first()
        assertTrue(updatedTranscript[0].dialogueText.contains("subterránea"))

        // Deleting parent project cascades to metadata, saved_scripts, and interview_transcript_lines
        repository.deleteProject(project.id)
        assertNull(db.creationsDao().getMetadataById(project.id))
        assertNull(db.creationsDao().getScriptByProjectId(project.id))
        assertTrue(repository.observeTranscript(project.id).first().isEmpty())
    }

    @Test
    fun `migration 1 to 2 creates normalized tables without losing v1 projects`() {
        val dbName = "migration_test_${System.currentTimeMillis()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()
        val helper = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
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
                        db.execSQL(
                            """
                            INSERT INTO `production_projects` VALUES (
                                'legacy-1', 'Proyecto Legacy', 'Idea vieja', 'CREEPYPASTA',
                                'AUDIO_DRAMA_WAV', 'FULL_HD_1080P', 4, 'COMPLETED', 100,
                                'Listo', NULL, NULL, '[]', '/tmp/legacy-1', 0, 1000, 2000
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val v1Db = helper.writableDatabase
        v1Db.close()
        helper.close()

        val migratedRoomDb = Room.databaseBuilder(context, MohanaDatabase::class.java, dbName)
            .addMigrations(
                MohanaDatabase.MIGRATION_1_2,
                MohanaDatabase.MIGRATION_2_3,
                MohanaDatabase.MIGRATION_3_4,
                MohanaDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()

        try {
            runTest {
                val legacy = migratedRoomDb.productionDao().getProjectById("legacy-1")
                assertNotNull(legacy)
                assertEquals("Proyecto Legacy", legacy!!.title)

                val repo = CreationLibraryRepository(
                    context,
                    migratedRoomDb.productionDao(),
                    migratedRoomDb.creationsDao()
                )
                repo.backfillMissingCreations()
                val backfilledMeta = migratedRoomDb.creationsDao().getMetadataById("legacy-1")
                assertNotNull(backfilledMeta)
                assertEquals("Proyecto Legacy", backfilledMeta!!.title)
            }
        } finally {
            migratedRoomDb.close()
            context.deleteDatabase(dbName)
        }
    }
}
