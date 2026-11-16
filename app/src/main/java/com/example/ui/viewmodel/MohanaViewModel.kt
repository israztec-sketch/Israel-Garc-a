package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CreationLibraryRepository
import com.example.data.local.CreationMetadataEntity
import com.example.data.local.InterviewTranscriptLineEntity
import com.example.data.local.MohanaDatabase
import com.example.data.local.ProductionProjectEntity
import com.example.data.local.ProductionStorageStats
import com.example.data.local.SavedScriptEntity
import com.example.distribution.ApkDistributionHelper
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.ExportArtifact
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionScript
import com.example.domain.model.ProductionStage
import com.example.domain.model.TimbreFilter
import com.example.domain.model.TtsVoiceOption
import com.example.domain.model.VideoResolution
import com.example.engines.BookDocumentEngine
import com.example.engines.DeviceCapabilities
import com.example.engines.DeviceCapabilitiesInspector
import com.example.engines.LiveProductionTelemetry
import com.example.engines.OrchestratorEngines
import com.example.engines.ProductionOrchestrator
import com.example.engines.VoicePreviewEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

private const val TAG = "MohanaViewModel"

enum class StudioStep(val number: Int, val label: String) {
    STEP_1_IDEA(1, "Idea"),
    STEP_2_PRODUCE(2, "Producción"),
    STEP_3_RESULT(3, "Resultado")
}

data class StudioFormState(
    val ideaPrompt: String = "",
    val selectedGenre: ContentGenre = ContentGenre.INTERVIEW_CONFESSION,
    val selectedFormat: OutputFormat = OutputFormat.AUDIO_VIDEO_EPISODE,
    val selectedResolution: VideoResolution = VideoResolution.FULL_HD_1080P,
    val videopodcastMinutes: Int = 42,
    val castCharacters: List<CharacterProfile> = defaultCastForGenre(ContentGenre.INTERVIEW_CONFESSION),
    val customVideoPath: String? = null,
    val customVideoLabel: String? = null,
    val isImportingCustomVideo: Boolean = false,
    val currentStep: StudioStep = StudioStep.STEP_1_IDEA,
    val activeProjectId: String? = null,
    val errorBanner: String? = null
)

fun defaultCastForGenre(genre: ContentGenre): List<CharacterProfile> = when (genre) {
    ContentGenre.INTERVIEW_CONFESSION -> listOf(
        CharacterProfile(
            id = "char_1",
            name = "Elena",
            role = "Entrevistadora",
            ageStyle = "Adulta",
            personality = "Serena, empática e incisiva",
            visualDescription = "Conductora de estudio con micrófono condensador",
            voicePitch = 1.0f,
            voiceRate = 1.0f,
            timbreFilter = TimbreFilter.WARM_STUDIO,
            preferredVoiceName = "gemini:Kore"
        ),
        CharacterProfile(
            id = "char_2",
            name = "Mateo",
            role = "Testigo / Confidente",
            ageStyle = "Maduro",
            personality = "Inquieto y reservado",
            visualDescription = "Invitado en penumbra frente al micrófono",
            voicePitch = 1.0f,
            voiceRate = 1.0f,
            timbreFilter = TimbreFilter.WARM_STUDIO,
            preferredVoiceName = "gemini:Charon"
        )
    )
    ContentGenre.INTERROGATION_TESTIMONY -> listOf(
        CharacterProfile(
            id = "char_1",
            name = "Inspectora Vera",
            role = "Interrogadora",
            ageStyle = "Adulta",
            personality = "Directa y analítica",
            visualDescription = "Sala de interrogatorios con lámpara cenital",
            voicePitch = 1.0f,
            voiceRate = 1.0f,
            timbreFilter = TimbreFilter.CRISP_NARRATOR,
            preferredVoiceName = "gemini:Zephyr"
        ),
        CharacterProfile(
            id = "char_2",
            name = "Carlos",
            role = "Declarante",
            ageStyle = "Adulto",
            personality = "Nervioso pero detallista",
            visualDescription = "Declarante frente a grabadora de cinta",
            voicePitch = 1.0f,
            voiceRate = 1.0f,
            timbreFilter = TimbreFilter.WARM_STUDIO,
            preferredVoiceName = "gemini:Fenrir"
        )
    )
    ContentGenre.SCIFI -> listOf(
        CharacterProfile(
            id = "char_1",
            name = "Comandante Nadia",
            role = "Narradora / Capitana",
            ageStyle = "Adulta",
            personality = "Templada y precisa",
            visualDescription = "Puente orbital con iluminación cian",
            voicePitch = 1.0f,
            voiceRate = 1.0f,
            timbreFilter = TimbreFilter.CRISP_NARRATOR,
            preferredVoiceName = "gemini:Aoede"
        ),
        CharacterProfile(
            id = "char_2",
            name = "Núcleo AURA",
            role = "Voz de a bordo",
            ageStyle = "Adulta",
            personality = "Metódica y serena",
            visualDescription = "Consola holográfica azul",
            voicePitch = 1.0f,
            voiceRate = 1.0f,
            timbreFilter = TimbreFilter.DEEP_CHAMBER,
            preferredVoiceName = "gemini:Orus"
        )
    )
    else -> listOf(
        CharacterProfile(
            id = "char_1",
            name = "Lucía",
            role = "Narradora Principal",
            ageStyle = "Adulta",
            personality = "Envolvente y atmosférica",
            visualDescription = "Cabina oscura con luz cálida",
            voicePitch = 1.0f,
            voiceRate = 1.0f,
            timbreFilter = TimbreFilter.WARM_STUDIO,
            preferredVoiceName = "gemini:Leda"
        ),
        CharacterProfile(
            id = "char_2",
            name = "Julián",
            role = "Voz Secundaria / Testigo",
            ageStyle = "Maduro",
            personality = "Grave y misterioso",
            visualDescription = "Silueta en pasillo nocturno",
            voicePitch = 1.0f,
            voiceRate = 1.0f,
            timbreFilter = TimbreFilter.DEEP_CHAMBER,
            preferredVoiceName = "gemini:Charon"
        )
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MohanaViewModel private constructor(
    application: Application,
    customEngines: OrchestratorEngines?
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, null)

    internal companion object {
        fun forTest(application: Application, engines: OrchestratorEngines): MohanaViewModel =
            MohanaViewModel(application, engines)
    }

    private val database = MohanaDatabase.getInstance(application)
    val repository = CreationLibraryRepository(
        application,
        database.productionDao(),
        database.creationsDao()
    )
    private val orchestrator = if (customEngines != null) {
        ProductionOrchestrator(application, repository, customEngines)
    } else {
        ProductionOrchestrator(application, repository)
    }
    private val voicePreviewEngine = VoicePreviewEngine(application)
    private val bookEngine = BookDocumentEngine()
    private val storageCleaner = repository.storageCleaner

    val allProjects: StateFlow<List<ProductionProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMetadata: StateFlow<List<CreationMetadataEntity>> = repository.allMetadata
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _librarySearchQuery = MutableStateFlow("")
    val librarySearchQuery: StateFlow<String> = _librarySearchQuery.asStateFlow()

    val searchedMetadata: StateFlow<List<CreationMetadataEntity>> = _librarySearchQuery
        .flatMapLatest { query -> repository.searchCreationMetadata(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableTtsVoices: StateFlow<List<TtsVoiceOption>> = voicePreviewEngine.availableVoices
    val isTtsReady: StateFlow<Boolean> = voicePreviewEngine.isTtsReady
    val ttsStatusMessage: StateFlow<String> = voicePreviewEngine.ttsStatusMessage
    val activeVoicePreviewId: StateFlow<String?> = voicePreviewEngine.activePreviewId
    val voicePreviewError: StateFlow<String?> = voicePreviewEngine.previewError

    val telemetry: StateFlow<LiveProductionTelemetry> = orchestrator.telemetry

    private val _formState = MutableStateFlow(StudioFormState())
    val formState: StateFlow<StudioFormState> = _formState.asStateFlow()

    private val _capabilities = MutableStateFlow<DeviceCapabilities?>(null)
    val capabilities: StateFlow<DeviceCapabilities?> = _capabilities.asStateFlow()

    private val _recoverableProjects = MutableStateFlow<List<ProductionProjectEntity>>(emptyList())
    val recoverableProjects: StateFlow<List<ProductionProjectEntity>> = _recoverableProjects.asStateFlow()

    private val _isProducing = MutableStateFlow(false)
    val isProducing: StateFlow<Boolean> = _isProducing.asStateFlow()

    private val _motor5State = MutableStateFlow<Motor5State>(Motor5State.Idle)
    val motor5State: StateFlow<Motor5State> = _motor5State.asStateFlow()

    private val _shareFeedback = MutableStateFlow<String?>(null)
    val shareFeedback: StateFlow<String?> = _shareFeedback.asStateFlow()

    private val _storageStats = MutableStateFlow(ProductionStorageStats())
    val storageStats: StateFlow<ProductionStorageStats> = _storageStats.asStateFlow()

    private var activeProductionJob: Job? = null
    private val productionRunSeq = AtomicLong(0L)

    init {
        refreshDeviceCapabilities()
        checkRecoverableProjects()
        refreshStorageStats()
        backfillLibraryMetadata()
        voicePreviewEngine.initialize()
    }

    override fun onCleared() {
        super.onCleared()
        voicePreviewEngine.shutdown()
    }

    private fun backfillLibraryMetadata() {
        viewModelScope.launch {
            runCatching { repository.backfillMissingCreations() }
                .onFailure { Log.w(TAG, "Metadata backfill warning", it) }
        }
    }

    fun updateLibrarySearchQuery(query: String) {
        _librarySearchQuery.value = query
    }

    fun observeCreationMetadata(projectId: String): Flow<CreationMetadataEntity?> =
        repository.observeMetadata(projectId)

    fun observeSavedScript(projectId: String): Flow<SavedScriptEntity?> =
        repository.observeScript(projectId)

    fun observeTranscriptLines(projectId: String): Flow<List<InterviewTranscriptLineEntity>> =
        repository.observeTranscript(projectId)

    suspend fun searchTranscriptLines(
        projectId: String,
        query: String
    ): List<InterviewTranscriptLineEntity> =
        repository.searchTranscriptLines(projectId, query)

    fun toggleFavorite(projectId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(projectId, isFavorite)
        }
    }

    fun updateUserNotes(projectId: String, notes: String) {
        viewModelScope.launch {
            repository.updateUserNotes(projectId, notes)
            _shareFeedback.value = "Notas personales guardadas en Room."
        }
    }

    fun refreshDeviceCapabilities() {
        viewModelScope.launch {
            runCatching { DeviceCapabilitiesInspector.inspect(getApplication()) }
                .onSuccess { _capabilities.value = it }
                .onFailure { Log.e(TAG, "Failed to inspect capabilities", it) }
        }
    }

    fun checkRecoverableProjects() {
        viewModelScope.launch {
            runCatching { repository.getRecoverableProjects() }
                .onSuccess { list ->
                    _recoverableProjects.value = list
                }
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            runCatching { storageCleaner.inspectStorage() }
                .onSuccess { stats -> _storageStats.value = stats }
                .onFailure { Log.w(TAG, "Storage stats inspection warning", it) }
        }
    }

    fun freeFailedProductionSpace() {
        if (_isProducing.value) {
            _shareFeedback.value = "Pausa la producción activa antes de liberar espacio."
            return
        }
        viewModelScope.launch {
            val summary = storageCleaner.freeFailedProductionSpace()
            val activeId = _formState.value.activeProjectId
            if (activeId != null && repository.getProject(activeId) == null) {
                _formState.update {
                    it.copy(
                        activeProjectId = null,
                        currentStep = StudioStep.STEP_1_IDEA,
                        errorBanner = null
                    )
                }
            }
            checkRecoverableProjects()
            refreshStorageStats()
            _shareFeedback.value = if (summary.failedProjectsRemoved == 0 && summary.deletedFilesCount == 0) {
                "No hay archivos de proyectos fallidos ni temporales por liberar."
            } else {
                val mb = String.format("%.2f MB", summary.freedBytes / (1024.0 * 1024.0))
                "Espacio liberado: $mb (${summary.failedProjectsRemoved} proyectos fallidos y ${summary.deletedFilesCount} archivos eliminados)."
            }
        }
    }

    fun updateIdeaPrompt(text: String) {
        _formState.update { it.copy(ideaPrompt = text, errorBanner = null) }
    }

    fun selectGenre(genre: ContentGenre) {
        val suggestedFormat = when (genre) {
            ContentGenre.BOOK_NOVEL, ContentGenre.DOCUMENT_PRESENTATION -> OutputFormat.BOOK_DOCUMENT
            else -> if (_formState.value.selectedFormat == OutputFormat.BOOK_DOCUMENT) {
                OutputFormat.AUDIO_VIDEO_EPISODE
            } else {
                _formState.value.selectedFormat
            }
        }
        val currentCast = _formState.value.castCharacters
        val nextDefaults = defaultCastForGenre(genre)
        val mergedCast = nextDefaults.mapIndexed { idx, def ->
            val prev = currentCast.getOrNull(idx)
            if (prev != null) {
                def.copy(preferredVoiceName = prev.preferredVoiceName)
            } else {
                def
            }
        }
        _formState.update {
            it.copy(
                selectedGenre = genre,
                selectedFormat = suggestedFormat,
                castCharacters = mergedCast
            )
        }
    }

    fun updateCharacterProfile(updated: CharacterProfile) {
        _formState.update { state ->
            state.copy(
                castCharacters = state.castCharacters.map {
                    if (it.id == updated.id) updated else it
                }
            )
        }
    }

    fun addCharacterSlot() {
        _formState.update { state ->
            if (state.castCharacters.size >= 4) return@update state
            val nextIdx = state.castCharacters.size + 1
            val voices = availableTtsVoices.value
            val voice = if (voices.isNotEmpty()) voices[(nextIdx - 1) % voices.size].voiceName else null
            val added = CharacterProfile(
                id = "char_${System.currentTimeMillis()}",
                name = "Personaje $nextIdx",
                role = if (nextIdx == 3) "Testigo / Voz Extra" else "Voz Adicional",
                ageStyle = "Adulto",
                personality = "Natural y expresivo",
                visualDescription = "Silueta en estudio nocturno",
                voicePitch = 1.0f,
                voiceRate = 1.0f,
                timbreFilter = TimbreFilter.WARM_STUDIO,
                preferredVoiceName = voice
            )
            state.copy(castCharacters = state.castCharacters + added)
        }
    }

    fun removeCharacterSlot(characterId: String) {
        _formState.update { state ->
            if (state.castCharacters.size <= 2) return@update state
            state.copy(castCharacters = state.castCharacters.filterNot { it.id == characterId })
        }
    }

    fun previewCharacterVoice(character: CharacterProfile) {
        val sampleName = character.name.ifBlank { character.role }
        val sampleText = buildSampleLineForCharacter(sampleName, character.role, _formState.value.selectedGenre)
        voicePreviewEngine.previewVoice(
            previewId = "char_${character.id}",
            voiceName = character.preferredVoiceName,
            sampleText = sampleText,
            pitch = character.voicePitch,
            rate = character.voiceRate,
            emotionPreset = character.emotionPreset,
            prosodyPacing = character.prosodyPacing
        )
    }

    fun previewCharacterVoiceOption(character: CharacterProfile, voice: TtsVoiceOption) {
        val sampleName = character.name.ifBlank { character.role }
        val sampleText = buildSampleLineForCharacter(sampleName, character.role, _formState.value.selectedGenre)
        voicePreviewEngine.previewVoice(
            previewId = "char_voice_${character.id}_${voice.voiceName}",
            voiceName = voice.voiceName,
            sampleText = sampleText,
            pitch = character.voicePitch,
            rate = character.voiceRate,
            emotionPreset = character.emotionPreset,
            prosodyPacing = character.prosodyPacing
        )
    }

    fun previewVoiceWithExpression(
        previewId: String,
        character: CharacterProfile,
        voiceName: String?,
        customSampleText: String
    ) {
        val sampleName = character.name.ifBlank { character.role }
        val text = customSampleText.trim().ifBlank {
            buildSampleLineForCharacter(sampleName, character.role, _formState.value.selectedGenre)
        }
        voicePreviewEngine.previewVoice(
            previewId = previewId,
            voiceName = voiceName,
            sampleText = text,
            pitch = character.voicePitch,
            rate = character.voiceRate,
            emotionPreset = character.emotionPreset,
            prosodyPacing = character.prosodyPacing
        )
    }

    fun previewAvailableVoice(voice: TtsVoiceOption) {
        voicePreviewEngine.previewVoice(
            previewId = "voice_${voice.voiceName}",
            voiceName = voice.voiceName,
            sampleText = "Hola, estás escuchando una muestra de ${voice.displayName} para tu historia en Mohana AI.",
            pitch = 1.0f,
            rate = 1.0f
        )
    }

    fun previewDefaultSystemVoice() {
        voicePreviewEngine.previewVoice(
            previewId = "system_default",
            voiceName = null,
            sampleText = "Esta es una muestra de voz humana de estudio lista para tu producción en Mohana AI.",
            pitch = 1.0f,
            rate = 1.0f
        )
    }

    fun clearVoicePreviewError() {
        voicePreviewEngine.clearError()
    }

    fun stopVoicePreview() {
        voicePreviewEngine.stopPreview()
    }

    private fun buildSampleLineForCharacter(
        name: String,
        role: String,
        genre: ContentGenre
    ): String = when (genre) {
        ContentGenre.CREEPYPASTA ->
            "Soy $name. Esa madrugada la grabación continuó sonando, aunque nadie estaba dentro de la cabina."
        ContentGenre.INTERVIEW_CONFESSION ->
            "Aquí $name, en mi rol de $role. Escucha con atención lo que ocurrió aquella noche en la estación."
        ContentGenre.INTERROGATION_TESTIMONY ->
            "Declara $name. Todo lo que voy a contar en esta cinta ocurrió exactamente a medianoche."
        ContentGenre.SCIFI ->
            "Bitácora de $name, $role. Detectamos una anomalía acústica procedente del sector exterior."
        else ->
            "Hola, soy $name. Esta es la prueba de voz asignada para mi papel de $role en tu proyecto."
    }

    fun selectFormat(format: OutputFormat) {
        _formState.update { it.copy(selectedFormat = format) }
    }

    fun selectResolution(resolution: VideoResolution) {
        _formState.update { it.copy(selectedResolution = resolution) }
    }

    fun updateVideopodcastMinutes(minutes: Int) {
        _formState.update { it.copy(videopodcastMinutes = minutes.coerceIn(40, 90)) }
    }

    fun importCustomVideo(uri: Uri) {
        if (_isProducing.value) return
        val previousPath = _formState.value.customVideoPath
        _formState.update { it.copy(isImportingCustomVideo = true, errorBanner = null) }
        viewModelScope.launch {
            runCatching {
                repository.importCustomVideoFromUri(uri)
            }.onSuccess { imported ->
                if (previousPath != null && previousPath != imported.filePath) {
                    repository.deleteUnreferencedCustomVideo(previousPath)
                }
                val durSecs = (imported.durationMs / 1000L).coerceAtLeast(1L)
                val dims = if (imported.width > 0 && imported.height > 0) "${imported.width}×${imported.height} · " else ""
                val mb = String.format("%.1f MB", imported.sizeBytes / (1024.0 * 1024.0))
                val label = "${imported.displayName} ($dims${durSecs}s · $mb)"
                _formState.update {
                    it.copy(
                        customVideoPath = imported.filePath,
                        customVideoLabel = label,
                        isImportingCustomVideo = false
                    )
                }
                _shareFeedback.value = "Vídeo propio listo para el montaje MP4."
            }.onFailure { err ->
                _formState.update {
                    it.copy(
                        isImportingCustomVideo = false,
                        errorBanner = err.localizedMessage ?: "No se pudo cargar el vídeo seleccionado."
                    )
                }
            }
        }
    }

    fun clearCustomVideo() {
        val prevPath = _formState.value.customVideoPath
        _formState.update {
            it.copy(customVideoPath = null, customVideoLabel = null)
        }
        if (prevPath != null) {
            viewModelScope.launch {
                repository.deleteUnreferencedCustomVideo(prevPath)
            }
        }
    }

    fun applyPreset(prompt: String, genre: ContentGenre, format: OutputFormat) {
        _formState.update {
            it.copy(
                ideaPrompt = prompt,
                selectedGenre = genre,
                selectedFormat = format,
                castCharacters = defaultCastForGenre(genre),
                errorBanner = null
            )
        }
    }

    fun setStudioStep(step: StudioStep) {
        _formState.update { it.copy(currentStep = step) }
    }

    fun startNewProduction() {
        val state = _formState.value
        if (state.ideaPrompt.isBlank()) {
            _formState.update { it.copy(errorBanner = "Escribe una idea o elige una plantilla para comenzar.") }
            return
        }
        if (_isProducing.value) return
        voicePreviewEngine.stopPreview()

        viewModelScope.launch {
            val targetMins = if (state.selectedFormat == OutputFormat.VIDEOPODCAST_40MIN) {
                state.videopodcastMinutes.coerceAtLeast(40)
            } else {
                state.selectedFormat.minMinutes
            }

            val created = repository.createInitialProject(
                ideaPrompt = state.ideaPrompt,
                genre = state.selectedGenre,
                outputFormat = state.selectedFormat,
                resolution = state.selectedResolution,
                targetMinDurationMinutes = targetMins,
                selectedCast = state.castCharacters,
                customVideoPath = if (
                    state.selectedFormat == OutputFormat.AUDIO_VIDEO_EPISODE ||
                    state.selectedFormat == OutputFormat.VIDEOPODCAST_40MIN
                ) state.customVideoPath else null
            )

            _formState.update {
                it.copy(
                    activeProjectId = created.id,
                    currentStep = StudioStep.STEP_2_PRODUCE,
                    errorBanner = null
                )
            }
            launchProductionJob(created.id)
        }
    }

    fun resumeProject(projectId: String) {
        if (_isProducing.value) return
        voicePreviewEngine.stopPreview()
        _formState.update {
            it.copy(
                activeProjectId = projectId,
                currentStep = StudioStep.STEP_2_PRODUCE,
                errorBanner = null
            )
        }
        launchProductionJob(projectId, retryFailedOnly = false)
    }

    fun retryFailedStep(projectId: String) {
        if (_isProducing.value) return
        voicePreviewEngine.stopPreview()
        _formState.update {
            it.copy(
                activeProjectId = projectId,
                currentStep = StudioStep.STEP_2_PRODUCE,
                errorBanner = null
            )
        }
        launchProductionJob(projectId, retryFailedOnly = true)
    }

    fun enableTypographyFallbackAndResume(projectId: String) {
        if (_isProducing.value) return
        viewModelScope.launch {
            orchestrator.enableTypographyVisualFallbackAndGetReady(projectId)
            retryFailedStep(projectId)
        }
    }

    fun pauseActiveProduction() {
        val jobToCancel = activeProductionJob
        activeProductionJob = null
        productionRunSeq.incrementAndGet()
        jobToCancel?.cancel()
        _isProducing.value = false
        checkRecoverableProjects()
    }

    private fun launchProductionJob(
        projectId: String,
        retryFailedOnly: Boolean = false
    ) {
        val previousJob = activeProductionJob
        val myRunId = productionRunSeq.incrementAndGet()
        _isProducing.value = true
        activeProductionJob = viewModelScope.launch {
            try {
                // Ensure any cancelling previous run finishes releasing orchestrationMutex before starting
                previousJob?.cancelAndJoin()

                val initialEntity = repository.getProject(projectId)
                    ?: throw IllegalStateException("Proyecto no encontrado en almacenamiento local.")
                val format = runCatching { OutputFormat.valueOf(initialEntity.outputFormatName) }
                    .getOrDefault(OutputFormat.AUDIO_VIDEO_EPISODE)
                val plannedPhases = orchestrator.plannedPhasesFor(format)
                val completedPhases = orchestrator.completedPhasesFor(initialEntity).toMutableSet()

                orchestrator.prepareProductionRun(projectId, retryFailedOnly)

                var latestEntity = initialEntity
                for (phase in plannedPhases) {
                    coroutineContext.ensureActive()
                    if (phase in completedPhases) {
                        continue
                    }

                    _motor5State.value = Motor5State.RunningPhase(
                        projectId = projectId,
                        currentPhase = phase,
                        plannedPhases = plannedPhases,
                        completedPhases = completedPhases.toSet(),
                        progressPercent = latestEntity.progressPercent,
                        statusDetail = "Motor ${phase.phaseNumber}/${plannedPhases.size} · ${phase.label}: ${phase.description}"
                    )

                    val outcome = orchestrator.executeMotor5Phase(
                        projectId = projectId,
                        phase = phase,
                        retryFailedOnly = retryFailedOnly
                    )
                    latestEntity = outcome.entity
                    if (productionRunSeq.get() != myRunId) return@launch

                    if (!outcome.completedSuccessfully) {
                        val pausedEntity = repository.saveCheckpoint(latestEntity)
                        _motor5State.value = Motor5State.Paused(
                            projectId = projectId,
                            failedPhase = phase,
                            failedEngineName = outcome.failedEngineId?.displayName,
                            errorMessage = outcome.errorMessage ?: pausedEntity.errorMessage,
                            plannedPhases = plannedPhases,
                            completedPhases = completedPhases.toSet(),
                            progressPercent = pausedEntity.progressPercent,
                            lastSavedCheckpointStep = pausedEntity.lastCompletedStep
                        )
                        _formState.update {
                            it.copy(
                                activeProjectId = pausedEntity.id,
                                currentStep = StudioStep.STEP_2_PRODUCE,
                                errorBanner = outcome.errorMessage ?: pausedEntity.errorMessage
                            )
                        }
                        return@launch
                    }

                    // Persist verified phase completion checkpoint in Room Database before transitioning
                    completedPhases.add(phase)
                    _motor5State.value = Motor5State.SavingCheckpoint(
                        projectId = projectId,
                        savedPhase = phase,
                        plannedPhases = plannedPhases,
                        completedPhases = completedPhases.toSet(),
                        progressPercent = latestEntity.progressPercent
                    )
                    latestEntity = repository.saveCheckpoint(latestEntity)
                    checkRecoverableProjects()

                    if (outcome.isTerminalForFormat) {
                        break
                    }
                }

                if (productionRunSeq.get() != myRunId) return@launch
                if (latestEntity.stageName == ProductionStage.COMPLETED.name) {
                    _motor5State.value = Motor5State.Completed(
                        projectId = projectId,
                        plannedPhases = plannedPhases,
                        completedPhases = plannedPhases.toSet(),
                        progressPercent = 100
                    )
                    _formState.update {
                        it.copy(
                            activeProjectId = latestEntity.id,
                            currentStep = StudioStep.STEP_3_RESULT,
                            errorBanner = null
                        )
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Production job paused/cancelled cleanly")
                withContext(NonCancellable) {
                    if (productionRunSeq.get() == myRunId) {
                        val paused = repository.getProject(projectId)
                        if (paused != null) {
                            val fmt = runCatching { OutputFormat.valueOf(paused.outputFormatName) }
                                .getOrDefault(OutputFormat.AUDIO_VIDEO_EPISODE)
                            _motor5State.value = Motor5State.Paused(
                                projectId = projectId,
                                failedPhase = null,
                                failedEngineName = null,
                                errorMessage = null,
                                plannedPhases = orchestrator.plannedPhasesFor(fmt),
                                completedPhases = orchestrator.completedPhasesFor(paused),
                                progressPercent = paused.progressPercent,
                                lastSavedCheckpointStep = paused.lastCompletedStep
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Production failed", e)
                if (productionRunSeq.get() == myRunId) {
                    val msg = e.localizedMessage ?: "Error en el proceso de producción."
                    val currentSnapshot = repository.getProject(projectId)
                    val fmt = currentSnapshot?.let {
                        runCatching { OutputFormat.valueOf(it.outputFormatName) }
                            .getOrDefault(OutputFormat.AUDIO_VIDEO_EPISODE)
                    } ?: _formState.value.selectedFormat
                    _motor5State.value = Motor5State.Paused(
                        projectId = projectId,
                        failedPhase = (_motor5State.value as? Motor5State.RunningPhase)?.currentPhase,
                        failedEngineName = null,
                        errorMessage = msg,
                        plannedPhases = orchestrator.plannedPhasesFor(fmt),
                        completedPhases = currentSnapshot?.let { orchestrator.completedPhasesFor(it) } ?: emptySet(),
                        progressPercent = currentSnapshot?.progressPercent ?: 0,
                        lastSavedCheckpointStep = currentSnapshot?.lastCompletedStep.orEmpty()
                    )
                    _formState.update {
                        it.copy(
                            errorBanner = msg
                        )
                    }
                }
            } finally {
                if (productionRunSeq.get() == myRunId) {
                    activeProductionJob = null
                    _isProducing.value = false
                }
                checkRecoverableProjects()
                refreshStorageStats()
            }
        }
    }

    fun openProjectInStep3(projectId: String) {
        _formState.update {
            it.copy(
                activeProjectId = projectId,
                currentStep = StudioStep.STEP_3_RESULT,
                errorBanner = null
            )
        }
    }

    fun saveEditedScript(projectId: String, newTitle: String, updatedScript: ProductionScript) {
        viewModelScope.launch {
            repository.updateScriptAndTitle(projectId, newTitle, updatedScript)
            val entity = repository.getProject(projectId) ?: return@launch
            val existingArtifacts = repository.verifyExistingArtifacts(entity.artifactsJson).toMutableList()
            val exportsDir = File(entity.directoryPath, "exports")
            val reExportedDocs = bookEngine.exportAllFormats(updatedScript, exportsDir)
            existingArtifacts.removeAll { it.format in reExportedDocs.map { d -> d.format }.toSet() }
            existingArtifacts.addAll(reExportedDocs)
            repository.saveCheckpoint(
                entity.copy(
                    title = newTitle.trim().ifBlank { entity.title },
                    scriptJson = updatedScript.toJson(),
                    artifactsJson = ExportArtifact.listToJson(existingArtifacts),
                    statusMessage = "Guion actualizado y documentos PDF/HTML/MD re-exportados"
                )
            )
            _shareFeedback.value = "Cambios guardados y documentos actualizados."
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            if (_formState.value.activeProjectId == projectId) {
                pauseActiveProduction()
                _formState.update { it.copy(activeProjectId = null, currentStep = StudioStep.STEP_1_IDEA) }
            }
            repository.deleteProject(projectId)
            checkRecoverableProjects()
            refreshStorageStats()
            _shareFeedback.value = "Creación eliminada de Mis Creaciones."
        }
    }

    fun verifyArtifacts(artifactsJson: String): List<ExportArtifact> {
        return repository.verifyExistingArtifacts(artifactsJson)
    }

    fun shareArtifact(artifact: ExportArtifact, title: String) {
        ApkDistributionHelper.shareArtifact(getApplication(), artifact, title)
    }

    fun shareApkWithFamily() {
        viewModelScope.launch {
            val res = ApkDistributionHelper.shareInstalledApkWithFamily(getApplication())
            res.onFailure { err ->
                _shareFeedback.value = err.localizedMessage ?: "No se pudo preparar el APK para compartir."
            }
        }
    }

    fun clearFeedback() {
        _shareFeedback.value = null
    }
}
