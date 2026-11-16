package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SpatialAudioOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ProsodyPacing
import com.example.domain.model.TimbreFilter
import com.example.domain.model.TtsVoiceOption
import com.example.domain.model.VoiceEmotionPreset
import com.example.engines.GeminiNeuralVoiceEngine
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.mohanaOutlinedTextFieldColors

enum class VoiceProfileFilter(val label: String) {
    ALL("Todas"),
    AI_HUMAN("IA Humana"),
    FEMALE("Femeninas"),
    MALE("Masculinas"),
    LOCAL_FALLBACK("Respaldo Local")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceProfileSelectionScreen(
    characters: List<CharacterProfile>,
    initialCharacterId: String?,
    availableVoices: List<TtsVoiceOption>,
    isTtsReady: Boolean,
    ttsStatusMessage: String,
    activePreviewId: String?,
    previewError: String?,
    onUpdateCharacter: (CharacterProfile) -> Unit,
    onAuditionVoice: (CharacterProfile, TtsVoiceOption, String) -> Unit,
    onAuditionExpression: (CharacterProfile, String) -> Unit,
    onStopPreview: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onStopPreview()
        onBack()
    }

    var selectedCharId by rememberSaveable(initialCharacterId) {
        mutableStateOf(
            initialCharacterId?.takeIf { id -> characters.any { it.id == id } }
                ?: characters.firstOrNull()?.id
                ?: ""
        )
    }
    val activeCharacter = characters.firstOrNull { it.id == selectedCharId }
        ?: characters.firstOrNull()

    var selectedFilter by rememberSaveable { mutableStateOf(VoiceProfileFilter.ALL) }
    var customSampleText by rememberSaveable(selectedCharId) {
        mutableStateOf(
            "Escucha cómo respiro y modulo cada pausa para sonar completamente real."
        )
    }

    val filteredVoices = remember(availableVoices, selectedFilter) {
        when (selectedFilter) {
            VoiceProfileFilter.ALL -> availableVoices
            VoiceProfileFilter.AI_HUMAN -> availableVoices.filter {
                GeminiNeuralVoiceEngine.isGeminiVoiceName(it.voiceName)
            }
            VoiceProfileFilter.FEMALE -> availableVoices.filter {
                it.genderCategory.equals("Femenina", ignoreCase = true)
            }
            VoiceProfileFilter.MALE -> availableVoices.filter {
                it.genderCategory.equals("Masculina", ignoreCase = true)
            }
            VoiceProfileFilter.LOCAL_FALLBACK -> availableVoices.filter {
                !GeminiNeuralVoiceEngine.isGeminiVoiceName(it.voiceName)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("voice_profile_selection_screen")
    ) {
        // Top Studio Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onStopPreview()
                            onBack()
                        },
                        modifier = Modifier.testTag("voice_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al Estudio"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Estudio de Voces IA Naturales",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Modelos emocionales y prosódicos · Cero voz robótica",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        color = if (isTtsReady) SuccessEmerald.copy(alpha = 0.16f) else WarningAmber.copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (isTtsReady) "Gemini 3.1 Listo" else "Preparando",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTtsReady) SuccessEmerald else WarningAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Character Switcher Pills
                if (characters.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        characters.forEach { character ->
                            val isSelectedChar = character.id == activeCharacter?.id
                            FilterChip(
                                selected = isSelectedChar,
                                onClick = { selectedCharId = character.id },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.RecordVoiceOver,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text("${character.name.ifBlank { "Personaje" }} · ${character.role}")
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("character_switcher_${character.id}")
                            )
                        }
                    }
                }
            }
        }

        // Scrollable Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (activeCharacter == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Añade al menos un personaje en el Estudio para configurar su voz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize()
                        .testTag("voice_profile_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Error banner if preview fails
                    if (!previewError.isNullOrBlank()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = previewError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = onDismissError) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Cerrar aviso",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Active Voice & Expression Summary Card
                    item {
                        val activeOption = availableVoices.firstOrNull {
                            it.voiceName == activeCharacter.preferredVoiceName
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
                                            )
                                        )
                                    )
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Perfil activo de ${activeCharacter.name.ifBlank { activeCharacter.role }}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (activePreviewId != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.GraphicEq,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Audicionando",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = activeOption?.displayName ?: (activeCharacter.preferredVoiceName ?: "IA Humana · Sofía (Kore)"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Emoción: ${activeCharacter.emotionPreset.displayName} · Prosodia: ${activeCharacter.prosodyPacing.displayName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = ttsStatusMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Emotional, Prosodic & Acoustic Expression Studio Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Psychology,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "1. Emoción e Intención Interpretativa",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    VoiceEmotionPreset.entries.forEach { preset ->
                                        val selected = activeCharacter.emotionPreset == preset
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                onUpdateCharacter(activeCharacter.copy(emotionPreset = preset))
                                            },
                                            label = { Text(preset.displayName) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.testTag("emotion_preset_${preset.name}")
                                        )
                                    }
                                }
                                Text(
                                    text = activeCharacter.emotionPreset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                Text(
                                    text = "2. Prosodia, Pausas y Respiración",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ProsodyPacing.entries.forEach { pacing ->
                                        val selected = activeCharacter.prosodyPacing == pacing
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                onUpdateCharacter(activeCharacter.copy(prosodyPacing = pacing))
                                            },
                                            label = { Text(pacing.displayName) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            modifier = Modifier.testTag("prosody_pacing_${pacing.name}")
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                Text(
                                    text = "3. Acústica Limpia de Estudio (sin filtro metálico)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TimbreFilter.entries.forEach { filter ->
                                        val selected = activeCharacter.timbreFilter == filter
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                onUpdateCharacter(activeCharacter.copy(timbreFilter = filter))
                                            },
                                            label = { Text(cleanTimbreLabel(filter)) },
                                            modifier = Modifier.testTag("timbre_filter_${filter.name}")
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                OutlinedTextField(
                                    value = customSampleText,
                                    onValueChange = { customSampleText = it },
                                    label = { Text("Frase para audicionar emoción y prosodia") },
                                    minLines = 2,
                                    maxLines = 3,
                                    colors = mohanaOutlinedTextFieldColors(),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_audition_text_input")
                                )

                                val exprPreviewId = "expr_${activeCharacter.id}"
                                val isPlayingExpression = activePreviewId == exprPreviewId
                                FilledTonalButton(
                                    onClick = {
                                        onAuditionExpression(activeCharacter, customSampleText)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("audition_expression_button")
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingExpression) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isPlayingExpression) {
                                            "Detener prueba expresiva"
                                        } else {
                                            "Escuchar prueba con esta Emoción y Prosodia"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Filter Header for Voice Model Catalog
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "4. Modelos de Voz Disponibles (${filteredVoices.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VoiceProfileFilter.entries.forEach { filter ->
                                    val count = when (filter) {
                                        VoiceProfileFilter.ALL -> availableVoices.size
                                        VoiceProfileFilter.AI_HUMAN -> availableVoices.count {
                                            GeminiNeuralVoiceEngine.isGeminiVoiceName(it.voiceName)
                                        }
                                        VoiceProfileFilter.FEMALE -> availableVoices.count {
                                            it.genderCategory.equals("Femenina", ignoreCase = true)
                                        }
                                        VoiceProfileFilter.MALE -> availableVoices.count {
                                            it.genderCategory.equals("Masculina", ignoreCase = true)
                                        }
                                        VoiceProfileFilter.LOCAL_FALLBACK -> availableVoices.count {
                                            !GeminiNeuralVoiceEngine.isGeminiVoiceName(it.voiceName)
                                        }
                                    }
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = { Text("${filter.label} ($count)") },
                                        modifier = Modifier.testTag("filter_voice_${filter.name}")
                                    )
                                }
                            }
                        }
                    }

                    if (filteredVoices.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SpatialAudioOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "No hay modelos en esta categoría. Cambia el filtro superior para explorar las voces neuronales.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredVoices, key = { it.voiceName }) { voice ->
                            val isAssigned = activeCharacter.preferredVoiceName == voice.voiceName
                            val isNeural = GeminiNeuralVoiceEngine.isGeminiVoiceName(voice.voiceName)
                            val auditionId = "char_voice_${activeCharacter.id}_${voice.voiceName}"
                            val isAuditioning = activePreviewId == auditionId

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAssigned) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                ),
                                border = BorderStroke(
                                    width = if (isAssigned || isAuditioning) 1.5.dp else 1.dp,
                                    color = when {
                                        isAssigned -> MaterialTheme.colorScheme.primary
                                        isAuditioning -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onUpdateCharacter(
                                            activeCharacter.copy(
                                                preferredVoiceName = voice.voiceName,
                                                voicePitch = 1.0f,
                                                voiceRate = 1.0f
                                            )
                                        )
                                    }
                                    .testTag("voice_profile_card_${voice.voiceName}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isNeural) {
                                                    Icon(
                                                        imageVector = Icons.Filled.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(
                                                    text = voice.displayName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = "${voice.qualityLabel} · ${if (voice.isLocalOffline) "Offline dispositivo" else "Modelo Emocional Nube"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isNeural) SuccessEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        AnimatedVisibility(visible = isAssigned) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = "Activa",
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Activa",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (voice.prosodyDescription.isNotBlank()) {
                                        Text(
                                            text = voice.prosodyDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (voice.emotionTags.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            voice.emotionTags.forEach { tag ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    shape = MaterialTheme.shapes.extraSmall
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onAuditionVoice(activeCharacter, voice, customSampleText)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("profile_audition_${voice.voiceName}")
                                        ) {
                                            Icon(
                                                imageVector = if (isAuditioning) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isAuditioning) "Detener" else "Audicionar")
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                onUpdateCharacter(
                                                    activeCharacter.copy(
                                                        preferredVoiceName = voice.voiceName,
                                                        voicePitch = 1.0f,
                                                        voiceRate = 1.0f
                                                    )
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("profile_select_${voice.voiceName}")
                                        ) {
                                            Text(if (isAssigned) "Seleccionada" else "Usar Voz")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sticky Bottom Confirm Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        onStopPreview()
                        onBack()
                    },
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_voice_profile_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Aplicar Perfil y Volver al Estudio",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

private fun cleanTimbreLabel(filter: TimbreFilter): String = when (filter) {
    TimbreFilter.CRISP_NARRATOR -> "Narrador Nítido"
    TimbreFilter.WARM_STUDIO -> "Estudio Cálido"
    TimbreFilter.DEEP_CHAMBER -> "Acústica Profunda"
    TimbreFilter.RADIO_FILTER -> "Micrófono Broadcast"
}
