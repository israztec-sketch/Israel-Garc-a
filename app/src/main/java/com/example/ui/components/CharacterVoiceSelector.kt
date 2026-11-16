package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.CharacterProfile
import com.example.domain.model.TimbreFilter
import com.example.domain.model.TtsVoiceOption
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.mohanaOutlinedTextFieldColors
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterVoiceSelectorSection(
    characters: List<CharacterProfile>,
    availableVoices: List<TtsVoiceOption>,
    isTtsReady: Boolean,
    ttsStatusMessage: String,
    activePreviewId: String?,
    previewError: String?,
    onUpdateCharacter: (CharacterProfile) -> Unit,
    onAddCharacter: () -> Unit,
    onRemoveCharacter: (String) -> Unit,
    onPreviewCharacter: (CharacterProfile) -> Unit,
    onPreviewCharacterVoiceOption: (CharacterProfile, TtsVoiceOption) -> Unit = { char, voice ->
        onPreviewCharacter(char.copy(preferredVoiceName = voice.voiceName))
    },
    onPreviewAvailableVoice: (TtsVoiceOption) -> Unit,
    onPreviewDefaultSystemVoice: () -> Unit,
    onDismissPreviewError: () -> Unit,
    onOpenVoiceProfiles: (CharacterProfile?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("character_selection_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "4. Personajes y Voces Naturales (IA Humana)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = ttsStatusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isTtsReady) SuccessEmerald else WarningAmber
                )
            }
            if (characters.size < 4) {
                OutlinedButton(
                    onClick = onAddCharacter,
                    modifier = Modifier.testTag("add_character_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir voz")
                }
            }
        }

        FilledTonalButton(
            onClick = { onOpenVoiceProfiles(characters.firstOrNull()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("open_voice_studio_button")
        ) {
            Icon(
                imageVector = Icons.Filled.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Abrir Estudio de Voces IA (Emoción y Prosodia)")
        }

        if (!previewError.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = previewError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissPreviewError) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar aviso de voz",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Direct audition bar for Gemini 3.1 Human AI voices + Android device TTS voices
        if (availableVoices.isNotEmpty()) {
            Text(
                text = "Voces naturales IA y del dispositivo (pulsa el altavoz para escuchar muestra):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableVoices.forEach { voice ->
                    val voicePreviewId = "voice_${voice.voiceName}"
                    val isPlayingThis = activePreviewId == voicePreviewId
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlayingThis) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.width(230.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${voice.qualityLabel} · ${if (voice.isLocalOffline) "Offline" else "Nube IA"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onPreviewAvailableVoice(voice) },
                                modifier = Modifier.testTag("preview_available_voice_${voice.voiceName}")
                            ) {
                                Icon(
                                    imageVector = if (isPlayingThis) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isPlayingThis) {
                                        "Detener muestra de ${voice.displayName}"
                                    } else {
                                        "Escuchar muestra de ${voice.displayName}"
                                    },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voz predeterminada de Android Text-to-Speech",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "Diferencia cada personaje ajustando su tono y velocidad abajo.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val playingDefault = activePreviewId == "system_default"
                    FilledTonalButton(
                        onClick = onPreviewDefaultSystemVoice,
                        modifier = Modifier.testTag("preview_system_default_voice")
                    ) {
                        Icon(
                            imageVector = if (playingDefault) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (playingDefault) "Detener" else "Probar TTS")
                    }
                }
            }
        }

        // Character Cards
        characters.forEachIndexed { index, character ->
            val charPreviewId = "char_${character.id}"
            val isCharacterPlaying = activePreviewId == charPreviewId

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(
                    width = if (isCharacterPlaying) 1.5.dp else 1.dp,
                    color = if (isCharacterPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("character_card_${character.id}")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Personaje ${index + 1} · ${character.role}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (characters.size > 2) {
                            TextButton(onClick = { onRemoveCharacter(character.id) }) {
                                Text("Quitar", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = character.name,
                        onValueChange = { updatedName ->
                            onUpdateCharacter(character.copy(name = updatedName))
                        },
                        label = { Text("Nombre del personaje") },
                        singleLine = true,
                        colors = mohanaOutlinedTextFieldColors(),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("character_name_input_${character.id}")
                    )

                    if (availableVoices.isNotEmpty()) {
                        Text(
                            text = "Voz asignada (recomendado: IA Humana para cero tono robótico)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableVoices.forEachIndexed { voiceIndex, voice ->
                                val isSelected = character.preferredVoiceName == voice.voiceName ||
                                    (character.preferredVoiceName == null && index % availableVoices.size == voiceIndex)
                                val rowPreviewId = "char_voice_${character.id}_${voice.voiceName}"
                                val isRowPlaying = activePreviewId == rowPreviewId

                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    onUpdateCharacter(
                                                        character.copy(preferredVoiceName = voice.voiceName)
                                                    )
                                                },
                                                leadingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                } else null,
                                                label = {
                                                    Text(
                                                        text = voice.displayName,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.testTag("select_voice_${character.id}_${voice.voiceName}")
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                onPreviewCharacterVoiceOption(character, voice)
                                            },
                                            modifier = Modifier.testTag("preview_voice_${character.id}_${voice.voiceName}")
                                        ) {
                                            Icon(
                                                imageVector = if (isRowPlaying) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "Escuchar ${voice.displayName} para ${character.name}",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Timbre Filter Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TimbreFilter.entries.forEach { timbre ->
                            val isSelectedTimbre = character.timbreFilter == timbre
                            FilterChip(
                                selected = isSelectedTimbre,
                                onClick = {
                                    onUpdateCharacter(character.copy(timbreFilter = timbre))
                                },
                                label = { Text(timbreLabel(timbre)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelectedTimbre,
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    // Pitch & Rate Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tono: ${String.format(Locale.US, "%.2fx", character.voicePitch)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Slider(
                                value = character.voicePitch,
                                onValueChange = { p ->
                                    onUpdateCharacter(character.copy(voicePitch = p))
                                },
                                valueRange = 0.70f..1.30f
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ritmo: ${String.format(Locale.US, "%.2fx", character.voiceRate)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Slider(
                                value = character.voiceRate,
                                onValueChange = { r ->
                                    onUpdateCharacter(character.copy(voiceRate = r))
                                },
                                valueRange = 0.80f..1.25f
                            )
                        }
                    }

                    Text(
                        text = "Emoción: ${character.emotionPreset.displayName} · Prosodia: ${character.prosodyPacing.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenVoiceProfiles(character) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_voice_profiles_${character.id}")
                        ) {
                            Text("Perfil IA / Emoción")
                        }

                        // Primary Character Voice Preview Button
                        FilledTonalButton(
                            onClick = { onPreviewCharacter(character) },
                            modifier = Modifier
                                .weight(1.35f)
                                .height(48.dp)
                                .testTag("preview_character_button_${character.id}")
                        ) {
                            Icon(
                                imageVector = if (isCharacterPlaying) {
                                    Icons.Filled.Stop
                                } else {
                                    Icons.Filled.RecordVoiceOver
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCharacterPlaying) {
                                    "Detener muestra de ${character.name.ifBlank { "personaje" }}"
                                } else {
                                    "Escuchar muestra de ${character.name.ifBlank { "personaje" }}"
                                },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isCharacterPlaying) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun timbreLabel(filter: TimbreFilter): String = when (filter) {
    TimbreFilter.CRISP_NARRATOR -> "Narrador Nítido"
    TimbreFilter.WARM_STUDIO -> "Estudio Cálido"
    TimbreFilter.DEEP_CHAMBER -> "Cámara Profunda"
    TimbreFilter.RADIO_FILTER -> "Radio / Archivo"
}
