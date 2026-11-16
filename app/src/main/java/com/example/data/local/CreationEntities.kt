package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "creation_metadata",
    foreignKeys = [
        ForeignKey(
            entity = ProductionProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["creationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["genreName"]),
        Index(value = ["isFavorite"]),
        Index(value = ["updatedAt"])
    ]
)
data class CreationMetadataEntity(
    @PrimaryKey val creationId: String,
    val title: String,
    val logline: String,
    val genreName: String,
    val outputFormatName: String,
    val wordCount: Int,
    val characterCount: Int,
    val sceneCount: Int,
    val transcriptLineCount: Int,
    val estimatedDurationMs: Long,
    val isFavorite: Boolean = false,
    val userNotes: String = "",
    val tagsCsv: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "saved_scripts",
    foreignKeys = [
        ForeignKey(
            entity = ProductionProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class SavedScriptEntity(
    @PrimaryKey val projectId: String,
    val title: String,
    val logline: String,
    val characterVisualBible: String,
    val endingSummary: String,
    val fullScriptMarkdown: String,
    val charactersJson: String,
    val chaptersJson: String,
    val lastEditedAt: Long
)

@Entity(
    tableName = "interview_transcript_lines",
    foreignKeys = [
        ForeignKey(
            entity = ProductionProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["speakerName"])
    ]
)
data class InterviewTranscriptLineEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0L,
    val projectId: String,
    val lineIndex: Int,
    val actNumber: Int,
    val sceneNumber: Int,
    val speakerName: String,
    val emotion: String,
    val actingNote: String,
    val dialogueText: String,
    val pauseAfterMs: Long,
    val sfxCue: String,
    val startTimeMs: Long,
    val actualDurationMs: Long
)

data class CreationWithDetails(
    @Embedded val metadata: CreationMetadataEntity,
    @Relation(
        parentColumn = "creationId",
        entityColumn = "projectId"
    )
    val script: SavedScriptEntity?,
    @Relation(
        parentColumn = "creationId",
        entityColumn = "projectId"
    )
    val transcriptLines: List<InterviewTranscriptLineEntity>
)
