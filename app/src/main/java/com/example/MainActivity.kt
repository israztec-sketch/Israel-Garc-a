package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.configureAppCheck
import com.example.ui.screens.CreationDetailScreen
import com.example.ui.screens.CreationsScreen
import com.example.ui.screens.EnginesAndDistributionScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VoiceProfileSelectionScreen
import com.example.ui.theme.MohanaAiTheme
import com.example.ui.viewmodel.MohanaViewModel

private enum class MohanaNavTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    STUDIO(R.string.nav_studio, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_studio"),
    CREATIONS(R.string.nav_creations, Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy, "nav_creations"),
    ENGINES(R.string.nav_engines, Icons.Filled.SettingsSuggest, Icons.Outlined.SettingsSuggest, "nav_engines")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        setContent {
            MohanaAiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MohanaAppRoot()
                }
            }
        }
    }
}

@Composable
private fun MohanaAppRoot(
    viewModel: MohanaViewModel = viewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val searchedMetadata by viewModel.searchedMetadata.collectAsStateWithLifecycle()
    val librarySearchQuery by viewModel.librarySearchQuery.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val recoverableProjects by viewModel.recoverableProjects.collectAsStateWithLifecycle()
    val isProducing by viewModel.isProducing.collectAsStateWithLifecycle()
    val motor5State by viewModel.motor5State.collectAsStateWithLifecycle()
    val shareFeedback by viewModel.shareFeedback.collectAsStateWithLifecycle()
    val availableTtsVoices by viewModel.availableTtsVoices.collectAsStateWithLifecycle()
    val isTtsReady by viewModel.isTtsReady.collectAsStateWithLifecycle()
    val ttsStatusMessage by viewModel.ttsStatusMessage.collectAsStateWithLifecycle()
    val activeVoicePreviewId by viewModel.activeVoicePreviewId.collectAsStateWithLifecycle()
    val voicePreviewError by viewModel.voicePreviewError.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MohanaNavTab.STUDIO) }
    var selectedDetailId by remember { mutableStateOf<String?>(null) }
    var showVoiceProfileScreen by remember { mutableStateOf(false) }
    var voiceProfileCharacterId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(shareFeedback) {
        val msg = shareFeedback
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val activeProject = remember(allProjects, formState.activeProjectId) {
        allProjects.firstOrNull { it.id == formState.activeProjectId }
    }
    val detailProject = remember(allProjects, selectedDetailId) {
        allProjects.firstOrNull { it.id == selectedDetailId }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionColor = MaterialTheme.colorScheme.primary
                    )
                }
            },
            bottomBar = {
                if (!useRail && detailProject == null && !showVoiceProfileScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp
                    ) {
                        val navColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MohanaNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    selectedDetailId = null
                                    currentTab = tab
                                },
                                colors = navColors,
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && detailProject == null && !showVoiceProfileScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        val railColors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MohanaNavTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    selectedDetailId = null
                                    currentTab = tab
                                },
                                colors = railColors,
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                if (showVoiceProfileScreen) {
                    VoiceProfileSelectionScreen(
                        characters = formState.castCharacters,
                        initialCharacterId = voiceProfileCharacterId,
                        availableVoices = availableTtsVoices,
                        isTtsReady = isTtsReady,
                        ttsStatusMessage = ttsStatusMessage,
                        activePreviewId = activeVoicePreviewId,
                        previewError = voicePreviewError,
                        onUpdateCharacter = viewModel::updateCharacterProfile,
                        onAuditionVoice = { char, voice, customPhrase ->
                            viewModel.previewVoiceWithExpression(
                                previewId = "char_voice_${char.id}_${voice.voiceName}",
                                character = char,
                                voiceName = voice.voiceName,
                                customSampleText = customPhrase
                            )
                        },
                        onAuditionExpression = { char, customPhrase ->
                            viewModel.previewVoiceWithExpression(
                                previewId = "expr_${char.id}",
                                character = char,
                                voiceName = char.preferredVoiceName,
                                customSampleText = customPhrase
                            )
                        },
                        onStopPreview = { viewModel.stopVoicePreview() },
                        onDismissError = viewModel::clearVoicePreviewError,
                        onBack = { showVoiceProfileScreen = false }
                    )
                } else if (detailProject != null) {
                    val detailMetadata by remember(detailProject.id) {
                        viewModel.observeCreationMetadata(detailProject.id)
                    }.collectAsStateWithLifecycle(initialValue = null)
                    val detailSavedScript by remember(detailProject.id) {
                        viewModel.observeSavedScript(detailProject.id)
                    }.collectAsStateWithLifecycle(initialValue = null)
                    val detailTranscript by remember(detailProject.id) {
                        viewModel.observeTranscriptLines(detailProject.id)
                    }.collectAsStateWithLifecycle(initialValue = emptyList())

                    CreationDetailScreen(
                        project = detailProject,
                        metadata = detailMetadata,
                        savedScript = detailSavedScript,
                        transcriptLines = detailTranscript,
                        verifiedArtifacts = viewModel.verifyArtifacts(detailProject.artifactsJson),
                        onBack = { selectedDetailId = null },
                        onToggleFavorite = { fav -> viewModel.toggleFavorite(detailProject.id, fav) },
                        onSaveUserNotes = { notes -> viewModel.updateUserNotes(detailProject.id, notes) },
                        onSearchTranscript = { q -> viewModel.searchTranscriptLines(detailProject.id, q) },
                        onSaveEdit = { title, script ->
                            viewModel.saveEditedScript(detailProject.id, title, script)
                        },
                        onResumeProject = { id ->
                            selectedDetailId = null
                            currentTab = MohanaNavTab.STUDIO
                            viewModel.resumeProject(id)
                        },
                        onShareArtifact = viewModel::shareArtifact,
                        onDeleteProject = {
                            val targetId = detailProject.id
                            selectedDetailId = null
                            viewModel.deleteProject(targetId)
                        }
                    )
                } else {
                    when (currentTab) {
                        MohanaNavTab.STUDIO -> {
                            val verifiedActiveArtifacts = remember(activeProject?.id, activeProject?.artifactsJson) {
                                activeProject?.let { viewModel.verifyArtifacts(it.artifactsJson) }.orEmpty()
                            }
                            StudioScreen(
                                formState = formState,
                                activeProject = activeProject,
                                verifiedArtifacts = verifiedActiveArtifacts,
                                telemetry = telemetry,
                                motor5State = motor5State,
                                capabilities = capabilities,
                                recoverableProjects = recoverableProjects,
                                isProducing = isProducing,
                            availableTtsVoices = availableTtsVoices,
                            isTtsReady = isTtsReady,
                            ttsStatusMessage = ttsStatusMessage,
                            activeVoicePreviewId = activeVoicePreviewId,
                            voicePreviewError = voicePreviewError,
                            onUpdateIdea = viewModel::updateIdeaPrompt,
                            onSelectGenre = viewModel::selectGenre,
                            onSelectFormat = viewModel::selectFormat,
                            onSelectResolution = viewModel::selectResolution,
                            onUpdatePodcastMinutes = viewModel::updateVideopodcastMinutes,
                            onImportCustomVideo = viewModel::importCustomVideo,
                            onClearCustomVideo = viewModel::clearCustomVideo,
                            onUpdateCharacter = viewModel::updateCharacterProfile,
                            onAddCharacter = viewModel::addCharacterSlot,
                            onRemoveCharacter = viewModel::removeCharacterSlot,
                            onPreviewCharacterVoice = viewModel::previewCharacterVoice,
                            onPreviewCharacterVoiceOption = viewModel::previewCharacterVoiceOption,
                            onPreviewAvailableVoice = viewModel::previewAvailableVoice,
                            onPreviewDefaultSystemVoice = viewModel::previewDefaultSystemVoice,
                            onDismissVoicePreviewError = viewModel::clearVoicePreviewError,
                            onOpenVoiceProfiles = { char ->
                                voiceProfileCharacterId = char?.id
                                showVoiceProfileScreen = true
                            },
                            onApplyPreset = viewModel::applyPreset,
                            onSelectStep = viewModel::setStudioStep,
                            onStartProduction = viewModel::startNewProduction,
                            onPauseProduction = viewModel::pauseActiveProduction,
                            onResumeProject = viewModel::resumeProject,
                            onRetryFailedStep = viewModel::retryFailedStep,
                                onEnableTypographyFallback = viewModel::enableTypographyFallbackAndResume,
                                onShareArtifact = viewModel::shareArtifact
                            )
                        }

                        MohanaNavTab.CREATIONS -> CreationsScreen(
                            projects = allProjects,
                            metadataList = searchedMetadata,
                            searchQuery = librarySearchQuery,
                            onSearchQueryChange = viewModel::updateLibrarySearchQuery,
                            onToggleFavorite = viewModel::toggleFavorite,
                            verifyArtifacts = viewModel::verifyArtifacts,
                            onOpenProject = { id -> selectedDetailId = id },
                            onResumeProject = { id ->
                                currentTab = MohanaNavTab.STUDIO
                                viewModel.resumeProject(id)
                            },
                            onDeleteProject = viewModel::deleteProject
                        )

                        MohanaNavTab.ENGINES -> {
                            LaunchedEffect(Unit) {
                                viewModel.refreshStorageStats()
                            }
                            EnginesAndDistributionScreen(
                                capabilities = capabilities,
                                telemetry = telemetry,
                                storageStats = storageStats,
                                isProducing = isProducing,
                                onShareApk = viewModel::shareApkWithFamily,
                                onFreeProductionSpace = viewModel::freeFailedProductionSpace
                            )
                        }
                    }
                }
            }
        }
    }
}
