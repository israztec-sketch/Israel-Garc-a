package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductionProjectEntity::class,
        CreationMetadataEntity::class,
        SavedScriptEntity::class,
        InterviewTranscriptLineEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class MohanaDatabase : RoomDatabase() {
    abstract fun productionDao(): ProductionDao
    abstract fun creationsDao(): CreationsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `creation_metadata` (
                        `creationId` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `logline` TEXT NOT NULL,
                        `genreName` TEXT NOT NULL,
                        `outputFormatName` TEXT NOT NULL,
                        `wordCount` INTEGER NOT NULL,
                        `characterCount` INTEGER NOT NULL,
                        `sceneCount` INTEGER NOT NULL,
                        `transcriptLineCount` INTEGER NOT NULL,
                        `estimatedDurationMs` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL DEFAULT 0,
                        `userNotes` TEXT NOT NULL,
                        `tagsCsv` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`creationId`) REFERENCES `production_projects`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_creation_metadata_genreName` ON `creation_metadata` (`genreName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_creation_metadata_isFavorite` ON `creation_metadata` (`isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_creation_metadata_updatedAt` ON `creation_metadata` (`updatedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_scripts` (
                        `projectId` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `logline` TEXT NOT NULL,
                        `characterVisualBible` TEXT NOT NULL,
                        `endingSummary` TEXT NOT NULL,
                        `fullScriptMarkdown` TEXT NOT NULL,
                        `charactersJson` TEXT NOT NULL,
                        `chaptersJson` TEXT NOT NULL,
                        `lastEditedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`projectId`) REFERENCES `production_projects`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_scripts_projectId` ON `saved_scripts` (`projectId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `interview_transcript_lines` (
                        `rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `lineIndex` INTEGER NOT NULL,
                        `actNumber` INTEGER NOT NULL,
                        `sceneNumber` INTEGER NOT NULL,
                        `speakerName` TEXT NOT NULL,
                        `emotion` TEXT NOT NULL,
                        `actingNote` TEXT NOT NULL,
                        `dialogueText` TEXT NOT NULL,
                        `pauseAfterMs` INTEGER NOT NULL,
                        `sfxCue` TEXT NOT NULL,
                        `startTimeMs` INTEGER NOT NULL,
                        `actualDurationMs` INTEGER NOT NULL,
                        FOREIGN KEY(`projectId`) REFERENCES `production_projects`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_interview_transcript_lines_projectId` ON `interview_transcript_lines` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_interview_transcript_lines_speakerName` ON `interview_transcript_lines` (`speakerName`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `production_projects` ADD COLUMN `selectedCastJson` TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `production_projects` ADD COLUMN `lastCompletedStep` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `production_projects` ADD COLUMN `failedStep` TEXT"
                )
                db.execSQL(
                    "ALTER TABLE `production_projects` ADD COLUMN `failedEngineId` TEXT"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `production_projects` ADD COLUMN `customVideoPath` TEXT"
                )
            }
        }

        @Volatile
        private var instance: MohanaDatabase? = null

        fun getInstance(context: Context): MohanaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MohanaDatabase::class.java,
                    "mohana_ai_studio.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
