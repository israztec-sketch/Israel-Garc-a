package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ProductionProjectEntity
import com.example.domain.model.ArtifactFormat
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.EngineId
import com.example.domain.model.EngineState
import com.example.domain.model.ExportArtifact
import com.example.domain.model.OrchestratorStep
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionScript
import com.example.domain.model.ProductionStage
import com.example.domain.model.TtsVoiceOption
import com.example.domain.model.VideoResolution
import com.example.engines.DeviceCapabilities
import com.example.engines.LiveProductionTelemetry
import com.example.ui.components.CharacterVoiceSelectorSection
import com.example.ui.components.IntegratedVideoPlayer
import com.example.ui.components.IntegratedWavAudioPlayer
import com.example.ui.components.VerifiedArtifactRow
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.mohanaOutlinedTextFieldColors
import com.example.ui.viewmodel.Motor5Phase
import com.example.ui.viewmodel.Motor5State
import com.example.ui.viewmodel.StudioFormState
import com.example.ui.viewmodel.StudioStep

@Composable
fun StudioScreen(
    formState: StudioFormState,
    activeProject: ProductionProjectEntity?,
    verifiedArtifacts: List<ExportArtifact>,
    telemetry: LiveProductionTelemetry,
    motor5State: Motor5State = Motor5State.Idle,
    capabilities: DeviceCapabilities?,
    recoverableProjects: List<ProductionProjectEntity>,
    isProducing: Boolean,
    availableTtsVoices: List<TtsVoiceOption> = emptyList(),
    isTtsReady: Boolean = false,
    ttsStatusMessage: String = "Inicializando voces Text-to-Speech...",
    activeVoicePreviewId: String? = null,
    voicePreviewError: String? = null,
    onUpdateIdea: (String) -> Unit,
    onSelectGenre: (ContentGenre) -> Unit,
    onSelectFormat: (OutputFormat) -> Unit,
    onSelectResolution: (VideoResolution) -> Unit,
    onUpdatePodcastMinutes: (Int) -> Unit,
    onImportCustomVideo: (Uri) -> Unit = {},
    onClearCustomVideo: () -> Unit = {},
    onUpdateCharacter: (CharacterProfile) -> Unit = {},
    onAddCharacter: () -> Unit = {},
    onRemoveCharacter: (String) -> Unit = {},
    onPreviewCharacterVoice: (CharacterProfile) -> Unit = {},
    onPreviewCharacterVoiceOption: (CharacterProfile, TtsVoiceOption) -> Unit = { _, _ -> },
    onPreviewAvailableVoice: (TtsVoiceOption) -> Unit = {},
    onPreviewDefaultSystemVoice: () -> Unit = {},
    onDismissVoicePreviewError: () -> Unit = {},
    onOpenVoiceProfiles: (CharacterProfile?) -> Unit = {},
    onApplyPreset: (String, ContentGenre, OutputFormat) -> Unit,
    onSelectStep: (StudioStep) -> Unit,
    onStartProduction: () -> Unit,
    onPauseProduction: () -> Unit,
    onResumeProject: (String) -> Unit,
    onRetryFailedStep: (String) -> Unit = {},
    onEnableTypographyFallback: (String) -> Unit,
    onShareArtifact: (ExportArtifact, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Brand Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "2 Cerebros · 7 Motores",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 3-Step Indicator Bar
            ThreeStepBar(
                currentStep = formState.currentStep,
                hasActiveProject = activeProject != null,
                onSelectStep = onSelectStep
            )

            // Interruption Recovery Banner
            val firstRecoverable = recoverableProjects.firstOrNull {
                it.id != formState.activeProjectId || !isProducing
            }
            AnimatedVisibility(visible = firstRecoverable != null && !isProducing && formState.currentStep == StudioStep.STEP_1_IDEA) {
                firstRecoverable?.let { proj ->
                    RecoveryBannerCard(
                        project = proj,
                        onResume = { onResumeProject(proj.id) }
                    )
                }
            }

            when (formState.currentStep) {
                StudioStep.STEP_1_IDEA -> Step1IdeaContent(
                    formState = formState,
                    capabilities = capabilities,
                    isProducing = isProducing,
                    availableTtsVoices = availableTtsVoices,
                    isTtsReady = isTtsReady,
                    ttsStatusMessage = ttsStatusMessage,
                    activeVoicePreviewId = activeVoicePreviewId,
                    voicePreviewError = voicePreviewError,
                    onUpdateIdea = onUpdateIdea,
                    onSelectGenre = onSelectGenre,
                    onSelectFormat = onSelectFormat,
                    onSelectResolution = onSelectResolution,
                    onUpdatePodcastMinutes = onUpdatePodcastMinutes,
                    onImportCustomVideo = onImportCustomVideo,
                    onClearCustomVideo = onClearCustomVideo,
                    onUpdateCharacter = onUpdateCharacter,
                    onAddCharacter = onAddCharacter,
                    onRemoveCharacter = onRemoveCharacter,
                    onPreviewCharacterVoice = onPreviewCharacterVoice,
                    onPreviewCharacterVoiceOption = onPreviewCharacterVoiceOption,
                    onPreviewAvailableVoice = onPreviewAvailableVoice,
                    onPreviewDefaultSystemVoice = onPreviewDefaultSystemVoice,
                    onDismissVoicePreviewError = onDismissVoicePreviewError,
                    onOpenVoiceProfiles = onOpenVoiceProfiles,
                    onApplyPreset = onApplyPreset,
                    onStartProduction = onStartProduction
                )

                StudioStep.STEP_2_PRODUCE -> Step2ProductionContent(
                    activeProject = activeProject,
                    telemetry = telemetry,
                    motor5State = motor5State,
                    isProducing = isProducing,
                    errorBanner = formState.errorBanner ?: activeProject?.errorMessage,
                    onPause = onPauseProduction,
                    onResume = { activeProject?.let { onResumeProject(it.id) } },
                    onRetryFailedStep = { activeProject?.let { onRetryFailedStep(it.id) } },
                    onEnableTypographyFallback = { activeProject?.let { onEnableTypographyFallback(it.id) } },
                    onGoToStep1 = { onSelectStep(StudioStep.STEP_1_IDEA) },
                    onGoToStep3 = { onSelectStep(StudioStep.STEP_3_RESULT) }
                )

                StudioStep.STEP_3_RESULT -> Step3ResultContent(
                    activeProject = activeProject,
                    verifiedArtifacts = verifiedArtifacts,
                    activeVoicePreviewId = activeVoicePreviewId,
                    onPreviewCharacterVoice = onPreviewCharacterVoice,
                    onShareArtifact = onShareArtifact,
                    onNewProject = { onSelectStep(StudioStep.STEP_1_IDEA) },
                    onResumeProject = { activeProject?.let { onResumeProject(it.id) } }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThreeStepBar(
    currentStep: StudioStep,
    hasActiveProject: Boolean,
    onSelectStep: (StudioStep) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StudioStep.entries.forEach { step ->
            val selected = step == currentStep
            val enabled = step == StudioStep.STEP_1_IDEA || hasActiveProject
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = enabled) { onSelectStep(step) }
                    .testTag("step_tab_${step.number}"),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(
                        text = "${step.number}. ${step.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            selected -> MaterialTheme.colorScheme.primary
                            enabled -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoveryBannerCard(
    project: ProductionProjectEntity,
    onResume: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Restore, contentDescription = null, tint = WarningAmber)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Producción recuperable (${project.progressPercent}%)",
                        style = MaterialTheme.typography.titleMedium,
                        color = WarningAmber
                    )
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    if (!project.failedEngineId.isNullOrBlank()) {
                        val failedEngineName = runCatching {
                            EngineId.valueOf(project.failedEngineId).displayName
                        }.getOrDefault(project.failedEngineId)
                        Text(
                            text = "Pausado por error en: $failedEngineName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onResume,
                modifier = Modifier.testTag("resume_banner_button")
            ) {
                Text("Reanudar")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step1IdeaContent(
    formState: StudioFormState,
    capabilities: DeviceCapabilities?,
    isProducing: Boolean,
    availableTtsVoices: List<TtsVoiceOption>,
    isTtsReady: Boolean,
    ttsStatusMessage: String,
    activeVoicePreviewId: String?,
    voicePreviewError: String?,
    onUpdateIdea: (String) -> Unit,
    onSelectGenre: (ContentGenre) -> Unit,
    onSelectFormat: (OutputFormat) -> Unit,
    onSelectResolution: (VideoResolution) -> Unit,
    onUpdatePodcastMinutes: (Int) -> Unit,
    onImportCustomVideo: (Uri) -> Unit,
    onClearCustomVideo: () -> Unit,
    onUpdateCharacter: (CharacterProfile) -> Unit,
    onAddCharacter: () -> Unit,
    onRemoveCharacter: (String) -> Unit,
    onPreviewCharacterVoice: (CharacterProfile) -> Unit,
    onPreviewCharacterVoiceOption: (CharacterProfile, TtsVoiceOption) -> Unit,
    onPreviewAvailableVoice: (TtsVoiceOption) -> Unit,
    onPreviewDefaultSystemVoice: () -> Unit,
    onDismissVoicePreviewError: () -> Unit,
    onOpenVoiceProfiles: (CharacterProfile?) -> Unit,
    onApplyPreset: (String, ContentGenre, OutputFormat) -> Unit,
    onStartProduction: () -> Unit
) {
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportCustomVideo(uri)
        }
    }
    // Hero Banner Card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(MaterialTheme.shapes.medium)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_hero_1794630611028),
            contentDescription = stringResource(R.string.hero_banner_desc),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.90f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = "De una idea a obra terminada",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Guion · Diálogos · Voces WAV · Música DSP · Escenas IA · Video MP4 / PDF",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    // Idea Prompt Input
    OutlinedTextField(
        value = formState.ideaPrompt,
        onValueChange = onUpdateIdea,
        label = { Text("Describe tu idea, historia, entrevista o libro") },
        placeholder = { Text("Ej. Un vigilante nocturno confiesa en entrevista lo que grabó en la estación cerrada del metro...") },
        minLines = 3,
        maxLines = 6,
        colors = mohanaOutlinedTextFieldColors(),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("idea_input")
    )

    // Quick Presets
    Text(
        text = "Plantillas rápidas",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PresetChip("Confesión Sobrenatural") {
            onApplyPreset(
                "Entrevista nocturna en estudio cerrado con un exoperador de radio que captó transmisiones desde una frecuencia inexistente y revela qué respondió al otro lado.",
                ContentGenre.INTERVIEW_CONFESSION,
                OutputFormat.AUDIO_VIDEO_EPISODE
            )
        }
        PresetChip("Creepypasta Nocturna") {
            onApplyPreset(
                "El faro del kilómetro 19 dejó de emitir luz en 1998, pero cada madrugada sigue enviando reportes meteorológicos con nombres de pasajeros actuales.",
                ContentGenre.CREEPYPASTA,
                OutputFormat.AUDIO_VIDEO_EPISODE
            )
        }
        PresetChip("Videopodcast 40+ min") {
            onApplyPreset(
                "Expediente completo e entrevista en profundidad sobre los archivos sonoros perdidos del Observatorio Austral y el testimonio del único ingeniero sobreviviente.",
                ContentGenre.INTERVIEW_CONFESSION,
                OutputFormat.VIDEOPODCAST_40MIN
            )
        }
        PresetChip("Libro / Dossier PDF") {
            onApplyPreset(
                "Crónica y tratado completo sobre arquitectura acústica del suspenso, diseño de atmósferas narrativas y casos históricos de radio-teatro.",
                ContentGenre.BOOK_NOVEL,
                OutputFormat.BOOK_DOCUMENT
            )
        }
    }

    // Genre Selector
    Text(
        text = "1. Género y Escritura Inteligente",
        style = MaterialTheme.typography.titleMedium
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ContentGenre.entries.forEach { genre ->
            val selected = formState.selectedGenre == genre
            FilterChip(
                selected = selected,
                onClick = { onSelectGenre(genre) },
                label = { Text(genre.displayName) },
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
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    // Output Format Selector
    Text(
        text = "2. Formato de Producción",
        style = MaterialTheme.typography.titleMedium
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutputFormat.entries.forEach { format ->
            val selected = formState.selectedFormat == format
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectFormat(format) }
                    .testTag("format_${format.name}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = format.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = format.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Videopodcast Duration Selector (minimum 40 min)
    AnimatedVisibility(visible = formState.selectedFormat == OutputFormat.VIDEOPODCAST_40MIN) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Duración mínima del Videopodcast: ${formState.videopodcastMinutes} minutos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Se encadenan actos continuos con verificación de duración real >= 40 min en audio y MP4.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = formState.videopodcastMinutes.toFloat(),
                    onValueChange = { onUpdatePodcastMinutes(it.toInt()) },
                    valueRange = 40f..75f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }

    // Video Resolution Selector (when video output selected)
    AnimatedVisibility(
        visible = formState.selectedFormat == OutputFormat.AUDIO_VIDEO_EPISODE ||
            formState.selectedFormat == OutputFormat.VIDEOPODCAST_40MIN
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "3. Resolución de Exportación MP4",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoResolution.entries.forEach { res ->
                    val selected = formState.selectedResolution == res
                    val supported = when (res) {
                        VideoResolution.FULL_HD_1080P -> capabilities?.supports1080p ?: true
                        VideoResolution.UHD_4K -> capabilities?.supports4k ?: false
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectResolution(res) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = res.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (supported) "Soportado por codec" else "Auto-ajuste 1080p si falla 4K",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (supported) SuccessEmerald else WarningAmber
                            )
                        }
                    }
                }
            }

            // Custom User Background Video Picker (Zero-permission Android Photo Picker)
            val hasCustomVideo = !formState.customVideoPath.isNullOrBlank()
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (hasCustomVideo) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                ),
                border = BorderStroke(
                    width = if (hasCustomVideo) 1.5.dp else 1.dp,
                    color = if (hasCustomVideo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.custom_video_section_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = if (hasCustomVideo) {
                            "Vídeo activo: ${formState.customVideoLabel ?: "Vídeo propio"}. Se integrará con voces WAV, música y subtítulos sin generar escenas IA."
                        } else {
                            "Opcional: elige un vídeo MP4 de tu galería para usarlo como fondo cinematográfico con nuestras voces, música DSP y subtítulos."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasCustomVideo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            enabled = !isProducing && !formState.isImportingCustomVideo,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pick_custom_video_button")
                        ) {
                            if (formState.isImportingCustomVideo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cargando vídeo...")
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.VideoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (hasCustomVideo) "Cambiar vídeo"
                                    else stringResource(R.string.btn_pick_custom_video)
                                )
                            }
                        }
                        if (hasCustomVideo) {
                            OutlinedButton(
                                onClick = onClearCustomVideo,
                                enabled = !isProducing,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("clear_custom_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = stringResource(R.string.btn_clear_custom_video),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.btn_clear_custom_video),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Character & Android TextToSpeech Voice Selection (when format produces voices)
    AnimatedVisibility(visible = formState.selectedFormat != OutputFormat.BOOK_DOCUMENT) {
        CharacterVoiceSelectorSection(
            characters = formState.castCharacters,
            availableVoices = availableTtsVoices,
            isTtsReady = isTtsReady,
            ttsStatusMessage = ttsStatusMessage,
            activePreviewId = activeVoicePreviewId,
            previewError = voicePreviewError,
            onUpdateCharacter = onUpdateCharacter,
            onAddCharacter = onAddCharacter,
            onRemoveCharacter = onRemoveCharacter,
            onPreviewCharacter = onPreviewCharacterVoice,
            onPreviewCharacterVoiceOption = onPreviewCharacterVoiceOption,
            onPreviewAvailableVoice = onPreviewAvailableVoice,
            onPreviewDefaultSystemVoice = onPreviewDefaultSystemVoice,
            onDismissPreviewError = onDismissVoicePreviewError,
            onOpenVoiceProfiles = onOpenVoiceProfiles
        )
    }

    formState.errorBanner?.let { err ->
        ErrorAlertCard(err)
    }

    Button(
        onClick = onStartProduction,
        enabled = !isProducing,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("start_production_button")
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.btn_start_production),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Step2ProductionContent(
    activeProject: ProductionProjectEntity?,
    telemetry: LiveProductionTelemetry,
    motor5State: Motor5State,
    isProducing: Boolean,
    errorBanner: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetryFailedStep: () -> Unit,
    onEnableTypographyFallback: () -> Unit,
    onGoToStep1: () -> Unit,
    onGoToStep3: () -> Unit
) {
    val progressPct = if (isProducing) telemetry.progressPercent else (activeProject?.progressPercent ?: telemetry.progressPercent)
    val statusMsg = if (isProducing) telemetry.statusMessage else (activeProject?.statusMessage ?: telemetry.statusMessage)
    val stage = if (isProducing) telemetry.stage else runCatching {
        ProductionStage.valueOf(activeProject?.stageName ?: "IDEA")
    }.getOrDefault(telemetry.stage)
    val failedEngine = telemetry.failedEngineId ?: activeProject?.failedEngineId?.let { raw ->
        runCatching { EngineId.valueOf(raw) }.getOrNull()
    }
    val failedStep = telemetry.failedStep ?: activeProject?.failedStep?.let { raw ->
        runCatching { OrchestratorStep.valueOf(raw) }.getOrNull()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeProject?.title ?: "Producción en curso",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Etapa actual: ${stage.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "$progressPct%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LinearProgressIndicator(
                progress = { (progressPct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.small)
            )

            Text(
                text = statusMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isProducing) {
                    OutlinedButton(
                        onClick = onPause,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pause_production_button")
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_pause_production))
                    }
                } else if (stage != ProductionStage.COMPLETED) {
                    val hasFailedStep = failedStep != null || !errorBanner.isNullOrBlank()
                    Button(
                        onClick = if (hasFailedStep) onRetryFailedStep else onResume,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(if (hasFailedStep) "retry_failed_step_button" else "resume_production_button")
                    ) {
                        Icon(
                            imageVector = if (hasFailedStep) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasFailedStep) {
                                "Reintentar solo ${failedStep?.displayName ?: failedEngine?.displayName ?: "paso fallido"}"
                            } else {
                                stringResource(R.string.btn_resume_production)
                            },
                            maxLines = 1
                        )
                    }
                } else {
                    Button(
                        onClick = onGoToStep3,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver Producto Final")
                    }
                }
                OutlinedButton(onClick = onGoToStep1) {
                    Text("Idea")
                }
            }
        }
    }

    // Transparent Auto-Paused Engine Failure Card + Single-Step Retry + Visual Fallback
    if (!errorBanner.isNullOrBlank()) {
        ErrorAlertCard(
            message = errorBanner,
            failedEngineLabel = failedEngine?.displayName,
            checkpointPct = progressPct
        )
        if ((failedStep == OrchestratorStep.SCENE_IMAGES || errorBanner.contains("Imagen IA", ignoreCase = true)) && !isProducing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Opciones para continuar el montaje MP4",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "La generación de imágenes requiere Firebase Blaze activo. Puedes reintentar solo las escenas o continuar explícitamente con Placas Cinematográficas Tipográficas (manteniendo voces WAV, música DSP y subtítulos sincronizados).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onEnableTypographyFallback, modifier = Modifier.weight(1f)) {
                            Text("Usar Placas Tipográficas")
                        }
                        OutlinedButton(onClick = onRetryFailedStep) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reintentar Escenas")
                        }
                    }
                }
            }
        }
    }

    // Motor 5 ViewModel Sequential State Machine Card (Room Checkpoints per Phase)
    Motor5StateMachineCard(
        activeProject = activeProject,
        motor5State = motor5State,
        failedStep = failedStep
    )

    // Live 2 Brains + 7 Engines Status (Strict Sequential Orchestration)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("2 Cerebros IA", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Secuencial (1 motor) · RAM liberada: ${telemetry.memoryReleaseCount}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Narrativo: ${telemetry.brains.narrativeBrainStatus}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "Visual: ${telemetry.brains.visualBrainStatus}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("7 Motores en Operación", style = MaterialTheme.typography.titleMedium)
            telemetry.engines.forEach { eng ->
                val color = when (eng.state) {
                    EngineState.COMPLETED -> SuccessEmerald
                    EngineState.RUNNING -> MaterialTheme.colorScheme.primary
                    EngineState.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = eng.engineId.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = eng.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        maxLines = if (eng.state == EngineState.ERROR) 2 else 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Live Logs Console
    if (telemetry.logs.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Registro de Producción",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                telemetry.logs.takeLast(10).forEach { line ->
                    Text(
                        text = "› $line",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Motor5StateMachineCard(
    activeProject: ProductionProjectEntity?,
    motor5State: Motor5State,
    failedStep: OrchestratorStep?
) {
    val format = runCatching {
        OutputFormat.valueOf(activeProject?.outputFormatName ?: OutputFormat.AUDIO_VIDEO_EPISODE.name)
    }.getOrDefault(OutputFormat.AUDIO_VIDEO_EPISODE)
    val plannedPhases = when (motor5State) {
        is Motor5State.RunningPhase -> motor5State.plannedPhases
        is Motor5State.SavingCheckpoint -> motor5State.plannedPhases
        is Motor5State.Paused -> motor5State.plannedPhases
        is Motor5State.Completed -> motor5State.plannedPhases
        Motor5State.Idle -> Motor5Phase.plannedFor(format)
    }

    val stateSummaryText = when (motor5State) {
        Motor5State.Idle -> "En espera de inicio secuencial"
        is Motor5State.RunningPhase -> "Fase activa: ${motor5State.currentPhase.label} (1 a la vez)"
        is Motor5State.SavingCheckpoint -> "Guardando checkpoint en Room: ${motor5State.savedPhase.label}"
        is Motor5State.Paused -> if (motor5State.failedPhase != null) {
            "Pausado en ${motor5State.failedPhase.label} (checkpoint preservado)"
        } else {
            "Pausado en ${motor5State.progressPercent}% (checkpoint Room listo)"
        }
        is Motor5State.Completed -> "Todas las fases verificadas y guardadas"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("motor5_state_machine_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Motor 5 · Orquestador Secuencial",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Room DB",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessEmerald
                )
            }
            Text(
                text = stateSummaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val lastSavedStepName = activeProject?.lastCompletedStep.orEmpty()
            val lastSavedStep = runCatching {
                if (lastSavedStepName.isBlank()) null else OrchestratorStep.valueOf(lastSavedStepName)
            }.getOrNull()

            plannedPhases.forEach { phase ->
                val isCompleted = phase in motor5State.completedPhases ||
                    lastSavedStep == OrchestratorStep.COMPLETED ||
                    (lastSavedStep != null && lastSavedStep.ordinal >= phase.checkpointStep.ordinal)
                val isRunning = motor5State is Motor5State.RunningPhase && motor5State.currentPhase == phase
                val isSaving = motor5State is Motor5State.SavingCheckpoint && motor5State.savedPhase == phase
                val isFailed = (motor5State is Motor5State.Paused && motor5State.failedPhase == phase) ||
                    (failedStep != null && failedStep.engineId == phase.primaryEngine)

                val badgeColor = when {
                    isFailed -> MaterialTheme.colorScheme.error
                    isRunning || isSaving -> MaterialTheme.colorScheme.primary
                    isCompleted -> SuccessEmerald
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val badgeLabel = when {
                    isFailed -> "Error (pausado)"
                    isSaving -> "Guardando en Room..."
                    isRunning -> "Ejecutando..."
                    isCompleted -> "Checkpoint Room"
                    else -> "Pendiente"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("motor5_phase_row_${phase.name}"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${phase.phaseNumber}. ${phase.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isRunning || isFailed) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isRunning || isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor
                    )
                }
            }
        }
    }
}

@Composable
private fun Step3ResultContent(
    activeProject: ProductionProjectEntity?,
    verifiedArtifacts: List<ExportArtifact>,
    activeVoicePreviewId: String?,
    onPreviewCharacterVoice: (CharacterProfile) -> Unit,
    onShareArtifact: (ExportArtifact, String) -> Unit,
    onNewProject: () -> Unit,
    onResumeProject: () -> Unit
) {
    if (activeProject == null) {
        Text("Selecciona o crea un proyecto en el Paso 1.")
        return
    }

    val mp4Artifact = verifiedArtifacts.firstOrNull { it.format == ArtifactFormat.MP4 }
    val wavArtifact = verifiedArtifacts.firstOrNull { it.format == ArtifactFormat.WAV }
    val script = ProductionScript.fromJson(activeProject.scriptJson)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (verifiedArtifacts.isNotEmpty()) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = if (verifiedArtifacts.isNotEmpty()) SuccessEmerald else WarningAmber
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = activeProject.title,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Text(
                text = if (verifiedArtifacts.isNotEmpty()) {
                    "Archivos reales verificados en almacenamiento local (${verifiedArtifacts.size})."
                } else {
                    "Aún no hay archivos terminados verificados en disco para este proyecto."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (verifiedArtifacts.isEmpty() && activeProject.stageName != ProductionStage.COMPLETED.name) {
                Button(onClick = onResumeProject) {
                    Text("Continuar producción pendiente")
                }
            }
        }
    }

    mp4Artifact?.let { mp4 ->
        IntegratedVideoPlayer(mp4FilePath = mp4.filePath)
    }

    wavArtifact?.let { wav ->
        IntegratedWavAudioPlayer(wavFilePath = wav.filePath, title = activeProject.title)
    }

    if (verifiedArtifacts.isNotEmpty()) {
        Text(
            text = "Archivos Generados y Exportación",
            style = MaterialTheme.typography.titleMedium
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            verifiedArtifacts.forEach { artifact ->
                VerifiedArtifactRow(
                    artifact = artifact,
                    onShare = { onShareArtifact(artifact, activeProject.title) }
                )
            }
        }
    }

    script?.let { s ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sinopsis y Reparto (${s.characters.size} voces · ${s.wordCount} palabras)", style = MaterialTheme.typography.titleMedium)
                if (s.logline.isNotBlank()) {
                    Text(s.logline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.characters.forEach { c ->
                    val isPlaying = activeVoicePreviewId == "char_${c.id}"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "• ${c.name} (${c.role}, ${c.ageStyle}) — tono ${c.voicePitch}x · filtro ${c.timbreFilter.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onPreviewCharacterVoice(c) },
                            modifier = Modifier.testTag("preview_cast_voice_${c.id}")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Escuchar voz de ${c.name}",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    OutlinedButton(
        onClick = onNewProject,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Icon(Icons.Filled.AddCircleOutline, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Crear Nueva Producción")
    }
}

@Composable
private fun ErrorAlertCard(
    message: String,
    failedEngineLabel: String? = null,
    checkpointPct: Int? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.testTag("production_error_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!failedEngineLabel.isNullOrBlank()) {
                    Text(
                        text = "Pausa automática · Falló $failedEngineLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (checkpointPct != null && checkpointPct > 0) {
                    Text(
                        text = "Progreso conservado al $checkpointPct% en almacenamiento local.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
