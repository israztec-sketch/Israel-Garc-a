package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CreationsDao {

    @Query("SELECT * FROM creation_metadata ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeAllMetadata(): Flow<List<CreationMetadataEntity>>

    @Query("SELECT * FROM creation_metadata WHERE creationId = :projectId LIMIT 1")
    fun observeMetadataById(projectId: String): Flow<CreationMetadataEntity?>

    @Query("SELECT * FROM creation_metadata WHERE creationId = :projectId LIMIT 1")
    suspend fun getMetadataById(projectId: String): CreationMetadataEntity?

    @Query("SELECT * FROM creation_metadata WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavoriteCreations(): Flow<List<CreationMetadataEntity>>

    @Query(
        """
        SELECT * FROM creation_metadata
        WHERE title LIKE '%' || :query || '%'
           OR logline LIKE '%' || :query || '%'
           OR tagsCsv LIKE '%' || :query || '%'
           OR userNotes LIKE '%' || :query || '%'
        ORDER BY isFavorite DESC, updatedAt DESC
        """
    )
    fun searchMetadata(query: String): Flow<List<CreationMetadataEntity>>

    @Transaction
    @Query("SELECT * FROM creation_metadata ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeAllCreationsWithDetails(): Flow<List<CreationWithDetails>>

    @Transaction
    @Query("SELECT * FROM creation_metadata WHERE creationId = :projectId LIMIT 1")
    fun observeCreationWithDetails(projectId: String): Flow<CreationWithDetails?>

    @Query("SELECT * FROM saved_scripts WHERE projectId = :projectId LIMIT 1")
    fun observeScriptByProjectId(projectId: String): Flow<SavedScriptEntity?>

    @Query("SELECT * FROM saved_scripts WHERE projectId = :projectId LIMIT 1")
    suspend fun getScriptByProjectId(projectId: String): SavedScriptEntity?

    @Query("SELECT * FROM interview_transcript_lines WHERE projectId = :projectId ORDER BY lineIndex ASC")
    fun observeTranscriptByProjectId(projectId: String): Flow<List<InterviewTranscriptLineEntity>>

    @Query(
        """
        SELECT * FROM interview_transcript_lines
        WHERE projectId = :projectId
          AND (dialogueText LIKE '%' || :query || '%' OR speakerName LIKE '%' || :query || '%')
        ORDER BY lineIndex ASC
        """
    )
    suspend fun searchTranscriptLines(projectId: String, query: String): List<InterviewTranscriptLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: CreationMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScript(script: SavedScriptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscriptLines(lines: List<InterviewTranscriptLineEntity>)

    @Query("DELETE FROM interview_transcript_lines WHERE projectId = :projectId")
    suspend fun deleteTranscriptForProject(projectId: String)

    @Query("UPDATE creation_metadata SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE creationId = :projectId")
    suspend fun updateFavorite(projectId: String, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE creation_metadata SET userNotes = :notes, updatedAt = :updatedAt WHERE creationId = :projectId")
    suspend fun updateUserNotes(projectId: String, notes: String, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    suspend fun saveCreationScriptAndTranscript(
        metadata: CreationMetadataEntity,
        script: SavedScriptEntity,
        transcriptLines: List<InterviewTranscriptLineEntity>
    ) {
        upsertMetadata(metadata)
        upsertScript(script)
        deleteTranscriptForProject(metadata.creationId)
        if (transcriptLines.isNotEmpty()) {
            insertTranscriptLines(transcriptLines)
        }
    }
}
