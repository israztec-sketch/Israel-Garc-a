package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CreationMetadataEntity
import com.example.data.local.InterviewTranscriptLineEntity
import com.example.data.local.ProductionProjectEntity
import com.example.data.local.SavedScriptEntity
import com.example.domain.model.ArtifactFormat
import com.example.domain.model.ExportArtifact
import com.example.domain.model.ProductionScript
import com.example.domain.model.ProductionStage
import com.example.ui.components.ConfirmDeleteCreationDialog
import com.example.ui.components.IntegratedVideoPlayer
import com.example.ui.components.IntegratedWavAudioPlayer
import com.example.ui.components.VerifiedArtifactRow
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.mohanaOutlinedTextFieldColors
import java.io.File
import java.util.Locale

@Composable
fun CreationDetailScreen(
    project: ProductionProjectEntity,
    metadata: CreationMetadataEntity? = null,
    savedScript: SavedScriptEntity? = null,
    transcriptLines: List<InterviewTranscriptLineEntity> = emptyList(),
    verifiedArtifacts: List<ExportArtifact>,
    onBack: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit = {},
    onSaveUserNotes: (String) -> Unit = {},
    onSearchTranscript: suspend (String) -> List<InterviewTranscriptLineEntity> = { emptyList() },
    onSaveEdit: (String, ProductionScript) -> Unit,
    onResumeProject: (String) -> Unit,
    onShareArtifact: (ExportArtifact, String) -> Unit,
    onDeleteProject: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val parsedScript = remember(project.scriptJson) { ProductionScript.fromJson(project.scriptJson) }
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var editedTitle by remember(project.id, project.title) { mutableStateOf(project.title) }
    var userNotesDraft by remember(project.id, metadata?.userNotes) {
        mutableStateOf(metadata?.userNotes.orEmpty())
    }
    var transcriptQuery by remember(project.id) { mutableStateOf("") }
    var searchedTranscript by remember(project.id, transcriptLines) {
        mutableStateOf<List<InterviewTranscriptLineEntity>?>(null)
    }

    LaunchedEffect(transcriptQuery, transcriptLines) {
        searchedTranscript = if (transcriptQuery.isBlank()) {
            null
        } else {
            onSearchTranscript(transcriptQuery)
        }
    }

    val editedLines = remember(project.scriptJson) {
        mutableStateListOf<String>().apply {
            parsedScript?.lines?.forEach { add(it.text) }
        }
    }
    val editedChapters = remember(project.scriptJson) {
        mutableStateListOf<String>().apply {
            parsedScript?.chapters?.forEach { add(it.bodyParagraphs.joinToString("\n\n")) }
        }
    }

    val mp4Artifact = verifiedArtifacts.firstOrNull { it.format == ArtifactFormat.MP4 }
    val wavArtifact = verifiedArtifacts.firstOrNull { it.format == ArtifactFormat.WAV }
    val isFavorite = metadata?.isFavorite == true

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top bar
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver a Mis Creaciones",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = metadata?.title?.ifBlank { project.title } ?: project.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { onToggleFavorite(!isFavorite) },
                            modifier = Modifier.testTag("detail_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (isFavorite) "Quitar de favoritos" else "Marcar como favorito",
                                tint = if (isFavorite) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (parsedScript != null) {
                            FilledTonalIconButton(
                                onClick = { isEditing = !isEditing },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isEditing) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("toggle_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Editar guion o libro"
                                )
                            }
                        }
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.testTag("detail_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Eliminar creación",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Room Metadata Strip
            if (metadata != null) {
                val chipColors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
                val chipBorder = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = {}, label = { Text("${metadata.wordCount} palabras") }, colors = chipColors, border = chipBorder)
                    AssistChip(onClick = {}, label = { Text("${metadata.transcriptLineCount} intervenciones") }, colors = chipColors, border = chipBorder)
                    AssistChip(onClick = {}, label = { Text("${metadata.characterCount} personajes") }, colors = chipColors, border = chipBorder)
                    AssistChip(onClick = {}, label = { Text("${metadata.sceneCount} escenas") }, colors = chipColors, border = chipBorder)
                    if (metadata.estimatedDurationMs > 0L) {
                        val mins = (metadata.estimatedDurationMs / 60_000L).coerceAtLeast(1L)
                        AssistChip(onClick = {}, label = { Text("~$mins min") }, colors = chipColors, border = chipBorder)
                    }
                }
            }

            // Personal Notes Card backed by Room CreationMetadataEntity
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_user_notes_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Notas y apuntes de producción",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = userNotesDraft,
                        onValueChange = { userNotesDraft = it },
                        placeholder = { Text("Guarda anotaciones de dirección, cambios de voz o recordatorios...") },
                        minLines = 2,
                        maxLines = 4,
                        colors = mohanaOutlinedTextFieldColors(),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_notes_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onSaveUserNotes(userNotesDraft) },
                            modifier = Modifier.testTag("detail_save_notes_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Guardar notas")
                        }
                    }
                }
            }

            if (project.stageName != ProductionStage.COMPLETED.name) {
                Button(
                    onClick = { onResumeProject(project.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reanudar producción (${project.progressPercent}%)")
                }
            }

            mp4Artifact?.let { IntegratedVideoPlayer(mp4FilePath = it.filePath) }
            wavArtifact?.let { IntegratedWavAudioPlayer(wavFilePath = it.filePath, title = project.title) }

            // Scene Gallery
            val sceneImages = parsedScript?.scenes?.filter {
                val p = it.imagePath
                p != null && File(p).exists() && File(p).length() > 0L
            }.orEmpty()
            if (sceneImages.isNotEmpty()) {
                Text("Escenas Generadas (${sceneImages.size})", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sceneImages.forEach { scene ->
                        Card(
                            modifier = Modifier.width(220.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column {
                                AsyncImage(
                                    model = File(scene.imagePath!!),
                                    contentDescription = scene.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(124.dp)
                                        .clip(MaterialTheme.shapes.small)
                                )
                                Text(
                                    text = "Escena ${scene.sceneNumber}: ${scene.title}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(8.dp),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            // Verified Export Artifacts
            if (verifiedArtifacts.isNotEmpty()) {
                Text("Exportaciones Verificadas", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    verifiedArtifacts.forEach { artifact ->
                        VerifiedArtifactRow(
                            artifact = artifact,
                            onShare = { onShareArtifact(artifact, project.title) }
                        )
                    }
                }
            }

            // Script / Book Reader & Editor
            if (parsedScript != null) {
                if (isEditing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("project_editor_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Editor de Proyecto",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Edición activa",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = editedTitle,
                                onValueChange = { editedTitle = it },
                                label = { Text("Título de la obra") },
                                colors = mohanaOutlinedTextFieldColors(),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("editor_title_input")
                            )
                            if (parsedScript.chapters.isNotEmpty()) {
                                parsedScript.chapters.forEachIndexed { idx, ch ->
                                    OutlinedTextField(
                                        value = editedChapters.getOrElse(idx) { "" },
                                        onValueChange = { if (idx < editedChapters.size) editedChapters[idx] = it },
                                        label = { Text("Capítulo ${ch.chapterNumber}: ${ch.title}") },
                                        minLines = 4,
                                        colors = mohanaOutlinedTextFieldColors(),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                parsedScript.lines.take(30).forEachIndexed { idx, line ->
                                    OutlinedTextField(
                                        value = editedLines.getOrElse(idx) { "" },
                                        onValueChange = { if (idx < editedLines.size) editedLines[idx] = it },
                                        label = { Text("${line.speakerName} (${line.emotion})") },
                                        minLines = 2,
                                        colors = mohanaOutlinedTextFieldColors(),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val updatedScript = if (parsedScript.chapters.isNotEmpty()) {
                                            parsedScript.copy(
                                                title = editedTitle,
                                                chapters = parsedScript.chapters.mapIndexed { idx, ch ->
                                                    val body = editedChapters.getOrElse(idx) { "" }
                                                        .split(Regex("\n{2,}"))
                                                        .map { it.trim() }
                                                        .filter { it.isNotBlank() }
                                                    ch.copy(bodyParagraphs = body.ifEmpty { ch.bodyParagraphs })
                                                }
                                            )
                                        } else {
                                            parsedScript.copy(
                                                title = editedTitle,
                                                lines = parsedScript.lines.mapIndexed { idx, l ->
                                                    if (idx < editedLines.size) l.copy(text = editedLines[idx]) else l
                                                }
                                            )
                                        }
                                        onSaveEdit(editedTitle, updatedScript)
                                        isEditing = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Guardar y Re-exportar PDF/HTML")
                                }
                                OutlinedButton(
                                    onClick = { isEditing = false },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text("Cancelar")
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (parsedScript.chapters.isNotEmpty()) "Contenido del Libro / Documento" else "Transcripción y Guion (Room)",
                        style = MaterialTheme.typography.titleMedium
                    )

                    val loglineText = savedScript?.logline?.ifBlank { parsedScript.logline } ?: parsedScript.logline
                    if (loglineText.isNotBlank()) {
                        Text(
                            text = loglineText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (parsedScript.chapters.isNotEmpty()) {
                        parsedScript.chapters.forEach { ch ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Capítulo ${ch.chapterNumber}: ${ch.title}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    ch.bodyParagraphs.forEach { p ->
                                        Text(p, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    } else {
                        if (transcriptLines.isNotEmpty()) {
                            OutlinedTextField(
                                value = transcriptQuery,
                                onValueChange = { transcriptQuery = it },
                                placeholder = { Text("Buscar en la transcripción por personaje o texto...") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (transcriptQuery.isNotBlank()) {
                                        IconButton(onClick = { transcriptQuery = "" }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda de transcripción")
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = mohanaOutlinedTextFieldColors(),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("detail_transcript_search_input")
                            )
                        }

                        val displayedTranscript = searchedTranscript ?: transcriptLines
                        if (displayedTranscript.isNotEmpty()) {
                            displayedTranscript.forEach { line ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${line.speakerName} · ${line.emotion} (Escena ${line.sceneNumber})",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = formatTranscriptTimecode(line.startTimeMs),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (line.actingNote.isNotBlank()) {
                                            Text(
                                                text = "Acotación: ${line.actingNote}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                        Text(line.dialogueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        } else if (transcriptQuery.isNotBlank()) {
                            Text(
                                text = "Sin líneas de diálogo que coincidan con \"$transcriptQuery\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            parsedScript.lines.forEach { line ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "${line.speakerName} · ${line.emotion} (Escena ${line.sceneNumber})",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (line.actingNote.isNotBlank()) {
                                            Text(
                                                text = "Acotación: ${line.actingNote}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                        Text(line.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirmation) {
        ConfirmDeleteCreationDialog(
            creationTitle = metadata?.title?.ifBlank { project.title } ?: project.title,
            metadata = metadata,
            artifactCount = verifiedArtifacts.size,
            onConfirmDelete = {
                showDeleteConfirmation = false
                onDeleteProject()
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

private fun formatTranscriptTimecode(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
