package com.example.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.ExportArtifact
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionScript
import com.example.domain.model.ProductionStage
import com.example.domain.model.VideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CustomVideoImportResult(
    val filePath: String,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)

class CreationLibraryRepository(
    private val context: Context,
    private val dao: ProductionDao,
    private val creationsDao: CreationsDao
) {
    val storageCleaner = ProductionStorageCleaner(context, dao)

    val allProjects: Flow<List<ProductionProjectEntity>> = dao.observeAllProjects()

    val allMetadata: Flow<List<CreationMetadataEntity>> = creationsDao.observeAllMetadata()

    fun observeProject(id: String): Flow<ProductionProjectEntity?> = dao.observeProjectById(id)

    suspend fun getProject(id: String): ProductionProjectEntity? = dao.getProjectById(id)

    suspend fun getRecoverableProjects(): List<ProductionProjectEntity> = dao.getRecoverableProjects()

    fun observeCreationsWithDetails(): Flow<List<CreationWithDetails>> =
        creationsDao.observeAllCreationsWithDetails()

    fun observeCreationWithDetails(projectId: String): Flow<CreationWithDetails?> =
        creationsDao.observeCreationWithDetails(projectId)

    fun observeMetadata(projectId: String): Flow<CreationMetadataEntity?> =
        creationsDao.observeMetadataById(projectId)

    fun observeFavoriteMetadata(): Flow<List<CreationMetadataEntity>> =
        creationsDao.observeFavoriteCreations()

    fun searchCreationMetadata(query: String): Flow<List<CreationMetadataEntity>> =
        if (query.isBlank()) {
            creationsDao.observeAllMetadata()
        } else {
            creationsDao.searchMetadata(query.trim())
        }

    fun observeScript(projectId: String): Flow<SavedScriptEntity?> =
        creationsDao.observeScriptByProjectId(projectId)

    fun observeTranscript(projectId: String): Flow<List<InterviewTranscriptLineEntity>> =
        creationsDao.observeTranscriptByProjectId(projectId)

    suspend fun searchTranscriptLines(
        projectId: String,
        query: String
    ): List<InterviewTranscriptLineEntity> =
        creationsDao.searchTranscriptLines(projectId, query.trim())

    suspend fun toggleFavorite(projectId: String, isFavorite: Boolean) {
        creationsDao.updateFavorite(
            projectId = projectId,
            isFavorite = isFavorite,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateUserNotes(projectId: String, notes: String) {
        creationsDao.updateUserNotes(
            projectId = projectId,
            notes = notes.trim(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun getProjectDirectory(projectId: String): File {
        val root = File(context.filesDir, "mohana_projects")
        if (!root.exists()) root.mkdirs()
        val dir = File(root, projectId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCustomVideosDirectory(): File {
        val dir = File(context.filesDir, "mohana_custom_videos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun importCustomVideoFromUri(uri: Uri): CustomVideoImportResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Video_Propio.mp4"
        val destFile = File(getCustomVideosDirectory(), "custom_${UUID.randomUUID()}.mp4")
        val resolver = context.contentResolver
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            } ?: throw IllegalStateException("No se pudo abrir el vídeo seleccionado.")

            if (!destFile.exists() || destFile.length() == 0L) {
                throw IllegalStateException("El archivo de vídeo seleccionado está vacío.")
            }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(destFile.absolutePath)
                val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (hasVideo != "yes" && (width <= 0 || height <= 0)) {
                    throw IllegalStateException("El archivo seleccionado no contiene una pista de vídeo válida.")
                }
                CustomVideoImportResult(
                    filePath = destFile.absolutePath,
                    displayName = displayName,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    sizeBytes = destFile.length()
                )
            } finally {
                runCatching { retriever.release() }
            }
        } catch (e: Exception) {
            runCatching { if (destFile.exists()) destFile.delete() }
            throw e
        }
    }

    private fun resolveDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    suspend fun deleteUnreferencedCustomVideo(videoPath: String?) = withContext(Dispatchers.IO) {
        if (videoPath.isNullOrBlank()) return@withContext
        val file = File(videoPath)
        val root = getCustomVideosDirectory()
        if (!file.absolutePath.startsWith(root.absolutePath)) return@withContext
        val stillReferenced = dao.getAllProjectsOnce().any { it.customVideoPath == videoPath }
        if (!stillReferenced && file.exists()) {
            runCatching { file.delete() }
        }
    }

    suspend fun createInitialProject(
        ideaPrompt: String,
        genre: ContentGenre,
        outputFormat: OutputFormat,
        resolution: VideoResolution,
        targetMinDurationMinutes: Int,
        selectedCast: List<CharacterProfile> = emptyList(),
        customVideoPath: String? = null
    ): ProductionProjectEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dir = getProjectDirectory(id)
        val now = System.currentTimeMillis()
        val previewTitle = ideaPrompt.trim().take(48).ifBlank { genre.displayName }
        val entity = ProductionProjectEntity(
            id = id,
            title = previewTitle,
            ideaPrompt = ideaPrompt.trim(),
            genreName = genre.name,
            outputFormatName = outputFormat.name,
            resolutionName = resolution.name,
            targetMinDurationMinutes = targetMinDurationMinutes,
            stageName = ProductionStage.IDEA.name,
            progressPercent = 2,
            statusMessage = "Proyecto inicializado en almacenamiento local",
            errorMessage = null,
            scriptJson = null,
            artifactsJson = "[]",
            directoryPath = dir.absolutePath,
            useTypographyVisualFallback = false,
            selectedCastJson = CharacterProfile.listToJson(selectedCast),
            lastCompletedStep = "",
            failedStep = null,
            failedEngineId = null,
            customVideoPath = customVideoPath?.takeIf { it.isNotBlank() && File(it).exists() },
            createdAt = now,
            updatedAt = now
        )
        dao.upsertProject(entity)
        syncToCreationsTables(entity, null)
        entity
    }

    suspend fun saveFastCheckpoint(entity: ProductionProjectEntity): ProductionProjectEntity =
        withContext(Dispatchers.IO) {
            val updated = entity.copy(updatedAt = System.currentTimeMillis())
            dao.upsertProject(updated)
            updated
        }

    suspend fun saveCheckpoint(
        entity: ProductionProjectEntity,
        parsedScript: ProductionScript? = null
    ): ProductionProjectEntity = withContext(Dispatchers.IO) {
        val updated = entity.copy(updatedAt = System.currentTimeMillis())
        dao.upsertProject(updated)
        val resolvedScript = parsedScript ?: ProductionScript.fromJson(updated.scriptJson)
        syncToCreationsTables(updated, resolvedScript)
        updated
    }

    suspend fun updateScriptAndTitle(
        projectId: String,
        newTitle: String,
        updatedScript: ProductionScript
    ) {
        val current = dao.getProjectById(projectId) ?: return
        val cleanTitle = newTitle.trim().ifBlank { current.title }
        val normalizedScript = updatedScript.copy(title = cleanTitle)
        val updated = current.copy(
            title = cleanTitle,
            scriptJson = normalizedScript.toJson(),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertProject(updated)
        syncToCreationsTables(updated, normalizedScript)
    }

    suspend fun backfillMissingCreations() = withContext(Dispatchers.IO) {
        val projects = dao.getAllProjectsOnce()
        for (project in projects) {
            if (creationsDao.getMetadataById(project.id) == null) {
                syncToCreationsTables(project, ProductionScript.fromJson(project.scriptJson))
            }
        }
    }

    private suspend fun syncToCreationsTables(
        entity: ProductionProjectEntity,
        script: ProductionScript?
    ) {
        val existingMeta = creationsDao.getMetadataById(entity.id)
        val genreLabel = runCatching { ContentGenre.valueOf(entity.genreName).displayName }
            .getOrDefault(entity.genreName)
        val formatLabel = runCatching { OutputFormat.valueOf(entity.outputFormatName).displayName }
            .getOrDefault(entity.outputFormatName)

        val tags = buildList {
            add(genreLabel)
            add(formatLabel)
            script?.characters?.forEach { if (it.name.isNotBlank()) add(it.name) }
        }.distinct().joinToString(", ")

        val estimatedDurationMs = if (script != null && script.lines.isNotEmpty()) {
            val measured = script.lines.sumOf { line ->
                val speech = if (line.actualDurationMs > 0L) {
                    line.actualDurationMs
                } else {
                    (line.text.split(Regex("\\s+")).count { it.isNotBlank() } * 400L).coerceAtLeast(1200L)
                }
                speech + line.pauseAfterMs.coerceAtLeast(0L)
            }
            measured
        } else if (script != null) {
            script.wordCount * 400L
        } else {
            entity.targetMinDurationMinutes * 60_000L
        }

        val metadata = CreationMetadataEntity(
            creationId = entity.id,
            title = script?.title?.takeIf { it.isNotBlank() } ?: entity.title,
            logline = script?.logline?.takeIf { it.isNotBlank() } ?: entity.ideaPrompt,
            genreName = entity.genreName,
            outputFormatName = entity.outputFormatName,
            wordCount = script?.wordCount ?: 0,
            characterCount = script?.characters?.size ?: 0,
            sceneCount = script?.scenes?.size ?: 0,
            transcriptLineCount = script?.lines?.size ?: 0,
            estimatedDurationMs = estimatedDurationMs,
            isFavorite = existingMeta?.isFavorite ?: false,
            userNotes = existingMeta?.userNotes ?: "",
            tagsCsv = tags,
            createdAt = existingMeta?.createdAt ?: entity.createdAt,
            updatedAt = entity.updatedAt
        )

        if (script == null) {
            creationsDao.upsertMetadata(metadata)
            return
        }

        val savedScript = SavedScriptEntity(
            projectId = entity.id,
            title = script.title.ifBlank { entity.title },
            logline = script.logline,
            characterVisualBible = script.characterVisualBible,
            endingSummary = script.endingSummary,
            fullScriptMarkdown = buildScriptMarkdown(script),
            charactersJson = JSONArray().apply { script.characters.forEach { put(it.toJson()) } }.toString(),
            chaptersJson = JSONArray().apply { script.chapters.forEach { put(it.toJson()) } }.toString(),
            lastEditedAt = entity.updatedAt
        )

        var runningTimeMs = 0L
        val transcriptLines = script.lines.map { line ->
            val start = if (line.startTimeMs > 0L || line.index == 0) line.startTimeMs else runningTimeMs
            val dur = if (line.actualDurationMs > 0L) {
                line.actualDurationMs
            } else {
                (line.text.split(Regex("\\s+")).count { it.isNotBlank() } * 400L).coerceAtLeast(1200L)
            }
            runningTimeMs = start + dur + line.pauseAfterMs.coerceAtLeast(0L)
            InterviewTranscriptLineEntity(
                projectId = entity.id,
                lineIndex = line.index,
                actNumber = line.actNumber,
                sceneNumber = line.sceneNumber,
                speakerName = line.speakerName,
                emotion = line.emotion,
                actingNote = line.actingNote,
                dialogueText = line.text,
                pauseAfterMs = line.pauseAfterMs,
                sfxCue = line.sfxCue,
                startTimeMs = start,
                actualDurationMs = line.actualDurationMs
            )
        }

        creationsDao.saveCreationScriptAndTranscript(
            metadata = metadata,
            script = savedScript,
            transcriptLines = transcriptLines
        )
    }

    private fun buildScriptMarkdown(script: ProductionScript): String = buildString {
        appendLine("# ${script.title}")
        if (script.logline.isNotBlank()) {
            appendLine()
            appendLine("> ${script.logline}")
        }
        if (script.characters.isNotEmpty()) {
            appendLine()
            appendLine("## Personajes")
            script.characters.forEach { c ->
                appendLine("- **${c.name}** (${c.role}, ${c.ageStyle}): ${c.personality}")
            }
        }
        if (script.chapters.isNotEmpty()) {
            script.chapters.forEach { ch ->
                appendLine()
                appendLine("## Capítulo ${ch.chapterNumber}: ${ch.title}")
                if (ch.subtitle.isNotBlank()) appendLine("*${ch.subtitle}*")
                ch.bodyParagraphs.forEach { p ->
                    appendLine()
                    appendLine(p)
                }
            }
        } else if (script.lines.isNotEmpty()) {
            appendLine()
            appendLine("## Transcripción y Guion")
            var lastAct = -1
            script.lines.forEach { l ->
                if (l.actNumber != lastAct) {
                    lastAct = l.actNumber
                    appendLine()
                    appendLine("### Acto $lastAct · Escena ${l.sceneNumber}")
                }
                val note = if (l.actingNote.isNotBlank()) " _(${l.actingNote})_" else ""
                val sfx = if (l.sfxCue.isNotBlank() && l.sfxCue != "NONE") " `[SFX: ${l.sfxCue}]`" else ""
                appendLine("- **${l.speakerName}** [${l.emotion}]$note$sfx: ${l.text}")
            }
        }
        if (script.endingSummary.isNotBlank()) {
            appendLine()
            appendLine("## Desenlace")
            appendLine(script.endingSummary)
        }
    }.trim()

    /**
     * Strictly verifies that every artifact claimed in the project actually exists on disk
     * and has non-zero size. Never reports a missing file as valid.
     */
    fun verifyExistingArtifacts(artifactsJson: String): List<ExportArtifact> {
        return ExportArtifact.listFromJson(artifactsJson).mapNotNull { artifact ->
            val file = File(artifact.filePath)
            if (file.exists() && file.length() > 0L) {
                artifact.copy(fileSizeBytes = file.length())
            } else {
                null
            }
        }
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val current = dao.getProjectById(projectId)
        if (current != null) {
            runCatching { File(current.directoryPath).deleteRecursively() }
        }
        dao.deleteProjectById(projectId)
        if (current?.customVideoPath != null) {
            deleteUnreferencedCustomVideo(current.customVideoPath)
        }
    }
}
