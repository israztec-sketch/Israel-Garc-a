package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CreationMetadataEntity
import com.example.data.local.ProductionProjectEntity
import com.example.domain.model.ContentGenre
import com.example.domain.model.ExportArtifact
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionStage
import com.example.ui.components.ConfirmDeleteCreationDialog
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.mohanaOutlinedTextFieldColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LibraryFilter(val label: String) {
    ALL("Todos"),
    FAVORITES("Favoritos"),
    COMPLETED("Terminados"),
    RECOVERABLE("En progreso / Pausados")
}

@Composable
fun CreationsScreen(
    projects: List<ProductionProjectEntity>,
    metadataList: List<CreationMetadataEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    verifyArtifacts: (String) -> List<ExportArtifact>,
    onOpenProject: (String) -> Unit,
    onResumeProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var pendingDelete by remember { mutableStateOf<ProductionProjectEntity?>(null) }

    val metadataById = remember(metadataList) {
        metadataList.associateBy { it.creationId }
    }

    val filtered = remember(projects, metadataList, metadataById, searchQuery, filter) {
        val base = if (searchQuery.isNotBlank()) {
            val matchedIds = metadataList.map { it.creationId }.toSet()
            val byRoomSearch = projects.filter { it.id in matchedIds }
            if (byRoomSearch.isNotEmpty() || metadataList.isEmpty()) {
                byRoomSearch.ifEmpty {
                    projects.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                            it.ideaPrompt.contains(searchQuery, ignoreCase = true)
                    }
                }
            } else {
                emptyList()
            }
        } else {
            projects
        }

        val sorted = base.sortedWith(
            compareByDescending<ProductionProjectEntity> { metadataById[it.id]?.isFavorite == true }
                .thenByDescending { it.updatedAt }
        )

        when (filter) {
            LibraryFilter.ALL -> sorted
            LibraryFilter.FAVORITES -> sorted.filter { metadataById[it.id]?.isFavorite == true }
            LibraryFilter.COMPLETED -> sorted.filter { it.stageName == ProductionStage.COMPLETED.name }
            LibraryFilter.RECOVERABLE -> sorted.filter { it.stageName != ProductionStage.COMPLETED.name }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mis Creaciones",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "${filtered.size} de ${projects.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Text(
                text = "Biblioteca local Room de guiones, transcripciones de entrevistas, WAV, MP4 y libros",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Buscar por título, etiquetas, notas o personajes...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                singleLine = true,
                colors = mohanaOutlinedTextFieldColors(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("creations_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryFilter.entries.forEach { item ->
                    val selected = filter == item
                    FilterChip(
                        selected = selected,
                        onClick = { filter = item },
                        label = {
                            Text(
                                text = item.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.5.dp
                        ),
                        modifier = Modifier.testTag("filter_chip_${item.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (projects.isEmpty() || (searchQuery.isBlank() && filter == LibraryFilter.ALL)) {
                                "Tu biblioteca local está vacía"
                            } else {
                                "Sin coincidencias en este filtro"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (projects.isEmpty()) {
                                "Crea tu primera producción en el Estudio de 3 pasos."
                            } else {
                                "Prueba con otra búsqueda o cambia el filtro superior."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { project ->
                        val verified = verifyArtifacts(project.artifactsJson)
                        val meta = metadataById[project.id]
                        CreationProjectCard(
                            project = project,
                            metadata = meta,
                            verifiedArtifacts = verified,
                            onToggleFavorite = {
                                val nextFav = !(meta?.isFavorite ?: false)
                                onToggleFavorite(project.id, nextFav)
                            },
                            onOpen = { onOpenProject(project.id) },
                            onResume = { onResumeProject(project.id) },
                            onDelete = { pendingDelete = project }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        val targetMeta = metadataById[target.id]
        val targetArtifactsCount = verifyArtifacts(target.artifactsJson).size
        ConfirmDeleteCreationDialog(
            creationTitle = targetMeta?.title?.ifBlank { target.title } ?: target.title,
            metadata = targetMeta,
            artifactCount = targetArtifactsCount,
            onConfirmDelete = {
                onDeleteProject(target.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun CreationProjectCard(
    project: ProductionProjectEntity,
    metadata: CreationMetadataEntity?,
    verifiedArtifacts: List<ExportArtifact>,
    onToggleFavorite: () -> Unit,
    onOpen: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val genre = runCatching { ContentGenre.valueOf(project.genreName).displayName }.getOrDefault(project.genreName)
    val format = runCatching { OutputFormat.valueOf(project.outputFormatName).displayName }.getOrDefault(project.outputFormatName)
    val isCompleted = project.stageName == ProductionStage.COMPLETED.name && verifiedArtifacts.isNotEmpty()
    val isFavorite = metadata?.isFavorite == true
    val dateStr = remember(project.updatedAt) {
        SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.forLanguageTag("es")).format(Date(project.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("creation_card_${project.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isFavorite) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            width = if (isFavorite) 1.5.dp else 1.dp,
            color = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata?.title?.ifBlank { project.title } ?: project.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$genre · $format",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("favorite_toggle_${project.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (isFavorite) "Quitar de favoritos" else "Marcar como favorito",
                            tint = if (isFavorite) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_project_button_${project.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Eliminar proyecto",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
                        )
                    }
                }
            }

            if (metadata != null && (metadata.wordCount > 0 || metadata.transcriptLineCount > 0)) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "${metadata.wordCount} palabras · ${metadata.transcriptLineCount} líneas de diálogo · ${metadata.characterCount} voces · ${metadata.sceneCount} escenas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (metadata != null && metadata.userNotes.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = metadata.userNotes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = if (isCompleted) SuccessEmerald else WarningAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isCompleted) {
                        "Verificado: ${verifiedArtifacts.joinToString { it.format.extension.uppercase() }}"
                    } else {
                        "${project.progressPercent}% · ${project.statusMessage}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isCompleted) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                            modifier = Modifier.clickable { onResume() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reanudar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    } else {
                        Button(
                            onClick = onOpen,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abrir")
                        }
                    }
                }
            }
        }
    }
}
