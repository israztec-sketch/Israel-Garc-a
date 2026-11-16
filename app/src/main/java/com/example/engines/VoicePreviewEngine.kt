package com.example.engines

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.domain.model.ProsodyPacing
import com.example.domain.model.TtsVoiceOption
import com.example.domain.model.VoiceEmotionPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

interface TtsPreviewBackend {
    fun initialize(onReady: (success: Boolean, voices: List<TtsVoiceOption>, message: String) -> Unit)
    fun speak(
        text: String,
        voiceName: String?,
        pitch: Float,
        rate: Float,
        utteranceId: String,
        onStarted: (String) -> Unit,
        onCompleted: (String) -> Unit,
        onError: (String, String) -> Unit
    ): Boolean
    fun stop()
    fun shutdown()
}

class AndroidTtsPreviewBackend(private val context: Context) : TtsPreviewBackend {
    private var tts: TextToSpeech? = null
    private var voiceLookup: Map<String, Voice> = emptyMap()
    private var currentStarted: ((String) -> Unit)? = null
    private var currentCompleted: ((String) -> Unit)? = null
    private var currentError: ((String, String) -> Unit)? = null
    private var isReady = false

    override fun initialize(
        onReady: (success: Boolean, voices: List<TtsVoiceOption>, message: String) -> Unit
    ) {
        if (tts != null && isReady) {
            onReady(true, extractOptions(voiceLookup.values.toList()), "Motor de voces listo")
            return
        }
        runCatching {
            var instance: TextToSpeech? = null
            instance = TextToSpeech(context.applicationContext) { status ->
                val engine = instance
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    isReady = false
                    onReady(
                        false,
                        emptyList(),
                        "TextToSpeech local no disponible (código $status). Se usarán voces IA Gemini."
                    )
                    return@TextToSpeech
                }

                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val esEs = Locale.forLanguageTag("es-ES")
                val esMx = Locale.forLanguageTag("es-MX")
                val res = engine.setLanguage(esEs)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val mxRes = engine.setLanguage(esMx)
                    if (mxRes == TextToSpeech.LANG_MISSING_DATA || mxRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        val defRes = engine.setLanguage(Locale.getDefault())
                        if (defRes == TextToSpeech.LANG_MISSING_DATA || defRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                            engine.setLanguage(Locale.US)
                        }
                    }
                }

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        utteranceId?.let { currentStarted?.invoke(it) }
                    }

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { currentCompleted?.invoke(it) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let {
                            currentError?.invoke(it, "No se pudo reproducir la muestra de voz.")
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let {
                            currentError?.invoke(it, "Error de TextToSpeech (código $errorCode).")
                        }
                    }
                })

                val rawVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
                voiceLookup = rawVoices.associateBy { it.name }
                isReady = true
                val options = extractOptions(rawVoices)
                val summary = if (options.isNotEmpty()) {
                    "8 voces IA humanas (Gemini 3.1) + ${options.size} locales"
                } else {
                    "8 voces IA humanas (Gemini 3.1) listas"
                }
                onReady(true, options, summary)
            }
            tts = instance
        }.onFailure { err ->
            isReady = false
            onReady(
                false,
                emptyList(),
                err.localizedMessage ?: "Usando voces IA humanas Gemini 3.1."
            )
        }
    }

    override fun speak(
        text: String,
        voiceName: String?,
        pitch: Float,
        rate: Float,
        utteranceId: String,
        onStarted: (String) -> Unit,
        onCompleted: (String) -> Unit,
        onError: (String, String) -> Unit
    ): Boolean {
        val engine = tts
        if (!isReady || engine == null) return false

        currentStarted = onStarted
        currentCompleted = onCompleted
        currentError = onError

        if (!voiceName.isNullOrBlank() && !GeminiNeuralVoiceEngine.isGeminiVoiceName(voiceName)) {
            val selected = voiceLookup[voiceName]
            if (selected != null && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in selected.features) {
                runCatching { engine.voice = selected }
            }
        }
        // Keep local TTS fallback pitch close to 1.0f so it does not sound robotic
        engine.setPitch(pitch.coerceIn(0.94f, 1.06f))
        engine.setSpeechRate(rate.coerceIn(0.90f, 1.12f))

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        var status = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (status != TextToSpeech.SUCCESS && !voiceName.isNullOrBlank()) {
            runCatching {
                val es = Locale.forLanguageTag("es-ES")
                if (engine.setLanguage(es) < 0) engine.setLanguage(Locale.getDefault())
            }
            status = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
        return status == TextToSpeech.SUCCESS
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    override fun shutdown() {
        isReady = false
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        voiceLookup = emptyMap()
    }

    private fun extractOptions(rawVoices: List<Voice>): List<TtsVoiceOption> {
        val installedVoices = rawVoices.filter { v ->
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features
        }.ifEmpty { rawVoices }

        val spanishVoices = installedVoices.filter {
            it.locale.language.equals("es", ignoreCase = true)
        }
        val ordered = if (spanishVoices.isNotEmpty()) {
            spanishVoices.sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenBy { it.name }
            )
        } else {
            installedVoices.sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenBy { it.name }
            ).take(6)
        }

        return ordered.mapIndexed { idx, voice ->
            val localeTag = voice.locale.toLanguageTag().ifBlank { "es" }
            val localeDisplay = voice.locale.getDisplayCountry(Locale.forLanguageTag("es"))
                .ifBlank { voice.locale.getDisplayLanguage(Locale.forLanguageTag("es")) }
                .ifBlank { localeTag }
            val shortName = voice.name
                .substringAfterLast('#')
                .removeSuffix("-local")
                .removeSuffix("-network")
                .take(24)
            val quality = when {
                voice.quality >= Voice.QUALITY_VERY_HIGH -> "Alta fidelidad"
                voice.quality >= Voice.QUALITY_HIGH -> "Alta"
                else -> "Estándar"
            }
            TtsVoiceOption(
                voiceName = voice.name,
                displayName = "Dispositivo ${idx + 1} · $localeDisplay ($shortName)",
                localeTag = localeTag,
                isLocalOffline = !voice.isNetworkConnectionRequired,
                qualityLabel = quality,
                genderCategory = "Dispositivo",
                emotionTags = listOf("Local", if (!voice.isNetworkConnectionRequired) "Offline" else "Red"),
                prosodyDescription = "Voz estándar del motor Text-to-Speech del dispositivo (uso recomendado como respaldo sin conexión)."
            )
        }
    }
}

class VoicePreviewEngine(
    private val backend: TtsPreviewBackend,
    private val neuralPreviewSynth: (suspend (text: String, voiceName: String, emotionPreset: VoiceEmotionPreset, prosodyPacing: ProsodyPacing, outFile: File) -> File)? = null,
    private val previewCacheDir: File? = null
) {
    constructor(context: Context) : this(
        backend = AndroidTtsPreviewBackend(context),
        neuralPreviewSynth = { text, voiceName, emotionPreset, prosodyPacing, outFile ->
            GeminiNeuralVoiceEngine().synthesizeToWavFile(
                text = text,
                voiceName = voiceName,
                emotion = emotionPreset.displayName,
                actingNote = emotionPreset.description,
                outFile = outFile,
                emotionPreset = emotionPreset,
                prosodyPacing = prosodyPacing
            )
        },
        previewCacheDir = File(context.cacheDir, "mohana_voice_previews")
    )

    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var neuralJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(
        if (neuralPreviewSynth != null) GeminiNeuralVoiceEngine.toTtsVoiceOptions() else emptyList()
    )
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private val _isTtsReady = MutableStateFlow(neuralPreviewSynth != null)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _ttsStatusMessage = MutableStateFlow(
        if (neuralPreviewSynth != null) "Voces IA humanas (Gemini 3.1) listas · detectando respaldo local..."
        else "Inicializando voces Text-to-Speech..."
    )
    val ttsStatusMessage: StateFlow<String> = _ttsStatusMessage.asStateFlow()

    private val _activePreviewId = MutableStateFlow<String?>(null)
    val activePreviewId: StateFlow<String?> = _activePreviewId.asStateFlow()

    private val _previewError = MutableStateFlow<String?>(null)
    val previewError: StateFlow<String?> = _previewError.asStateFlow()

    private var activeUtteranceId: String? = null

    fun initialize() {
        val neuralVoices = if (neuralPreviewSynth != null) {
            GeminiNeuralVoiceEngine.toTtsVoiceOptions()
        } else {
            emptyList()
        }
        backend.initialize { success, localVoices, message ->
            _availableVoices.value = neuralVoices + localVoices
            _isTtsReady.value = success || neuralVoices.isNotEmpty()
            _ttsStatusMessage.value = message
            if (!success && neuralVoices.isEmpty()) {
                _previewError.value = message
            } else {
                _previewError.value = null
            }
        }
    }

    fun previewVoice(
        previewId: String,
        voiceName: String?,
        sampleText: String,
        pitch: Float = 1.0f,
        rate: Float = 1.0f,
        emotionPreset: VoiceEmotionPreset = VoiceEmotionPreset.NATURAL_WARM,
        prosodyPacing: ProsodyPacing = ProsodyPacing.NATURAL
    ) {
        if (_activePreviewId.value == previewId) {
            stopPreview()
            return
        }

        val cleanSample = sampleText.trim().ifBlank {
            "Hola, soy una voz de estudio natural lista para tu producción en Mohana AI."
        }

        val synth = neuralPreviewSynth
        val wantsGeminiNeural = synth != null &&
            (GeminiNeuralVoiceEngine.isGeminiVoiceName(voiceName) || voiceName.isNullOrBlank())

        if (wantsGeminiNeural && synth != null) {
            stopMediaOnly()
            backend.stop()
            val resolvedVoice = voiceName ?: GeminiNeuralVoiceEngine.NEURAL_PERSONAS.first().id
            val utteranceId = "mohana_neural_${UUID.randomUUID()}"
            activeUtteranceId = utteranceId
            _activePreviewId.value = previewId
            _previewError.value = null

            neuralJob = previewScope.launch {
                try {
                    val cacheRoot = previewCacheDir ?: File(System.getProperty("java.io.tmpdir"), "mohana_previews")
                    cacheRoot.mkdirs()
                    val sig = "${resolvedVoice}_${emotionPreset.name}_${prosodyPacing.name}_${cleanSample.hashCode().toUInt().toString(16)}"
                    val cacheKey = "${sig.replace(":", "_")}.wav"
                    val cachedWav = File(cacheRoot, cacheKey)

                    val wavFile = if (cachedWav.exists() && cachedWav.length() > 44L && VoiceAudioEngine.hasAudibleSpeech(cachedWav)) {
                        cachedWav
                    } else {
                        synth(cleanSample, resolvedVoice, emotionPreset, prosodyPacing, cachedWav)
                    }

                    if (activeUtteranceId != utteranceId) return@launch
                    playWavWithMediaPlayer(wavFile, utteranceId)
                } catch (c: CancellationException) {
                    throw c
                } catch (err: Throwable) {
                    if (activeUtteranceId != utteranceId) return@launch
                    // Fallback transparently to local Android TTS if network/Gemini preview fails
                    val spokeFallback = speakViaLocalBackend(
                        previewId = previewId,
                        voiceName = null,
                        sampleText = cleanSample,
                        pitch = pitch,
                        rate = rate,
                        utteranceId = utteranceId
                    )
                    if (!spokeFallback) {
                        activeUtteranceId = null
                        _activePreviewId.value = null
                        _previewError.value = "Error en muestra de voz IA: ${err.localizedMessage ?: "sin conexión"}"
                    }
                }
            }
            return
        }

        if (!_isTtsReady.value) {
            _previewError.value = "El motor de voz aún no está listo."
            return
        }

        stopMediaOnly()
        val utteranceId = "mohana_preview_${UUID.randomUUID()}"
        activeUtteranceId = utteranceId
        _activePreviewId.value = previewId
        _previewError.value = null

        val queued = speakViaLocalBackend(
            previewId = previewId,
            voiceName = voiceName,
            sampleText = cleanSample,
            pitch = pitch,
            rate = rate,
            utteranceId = utteranceId
        )

        if (!queued) {
            activeUtteranceId = null
            _activePreviewId.value = null
            _previewError.value = "No se pudo reproducir la muestra de voz en TextToSpeech."
        }
    }

    private fun speakViaLocalBackend(
        previewId: String,
        voiceName: String?,
        sampleText: String,
        pitch: Float,
        rate: Float,
        utteranceId: String
    ): Boolean {
        return backend.speak(
            text = sampleText,
            voiceName = voiceName,
            pitch = pitch,
            rate = rate,
            utteranceId = utteranceId,
            onStarted = { startedId ->
                if (activeUtteranceId == startedId) {
                    _activePreviewId.value = previewId
                }
            },
            onCompleted = { doneId ->
                if (activeUtteranceId == doneId) {
                    activeUtteranceId = null
                    _activePreviewId.value = null
                }
            },
            onError = { errId, message ->
                if (activeUtteranceId == errId) {
                    activeUtteranceId = null
                    _activePreviewId.value = null
                    _previewError.value = message
                }
            }
        )
    }

    private fun playWavWithMediaPlayer(wavFile: File, utteranceId: String) {
        stopMediaOnly()
        runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(wavFile.absolutePath)
                setVolume(1.0f, 1.0f)
                setOnCompletionListener {
                    if (activeUtteranceId == utteranceId) {
                        activeUtteranceId = null
                        _activePreviewId.value = null
                    }
                    stopMediaOnly()
                }
                setOnErrorListener { _, _, _ ->
                    if (activeUtteranceId == utteranceId) {
                        activeUtteranceId = null
                        _activePreviewId.value = null
                        _previewError.value = "No se pudo reproducir el archivo de muestra de voz."
                    }
                    stopMediaOnly()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = mp
        }.onFailure { err ->
            if (activeUtteranceId == utteranceId) {
                activeUtteranceId = null
                _activePreviewId.value = null
                _previewError.value = err.localizedMessage ?: "Error reproduciendo audio de muestra."
            }
            stopMediaOnly()
        }
    }

    private fun stopMediaOnly() {
        neuralJob?.cancel()
        neuralJob = null
        runCatching {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        }
        mediaPlayer = null
    }

    fun stopPreview() {
        activeUtteranceId = null
        _activePreviewId.value = null
        stopMediaOnly()
        backend.stop()
    }

    fun clearError() {
        _previewError.value = null
    }

    fun shutdown() {
        stopPreview()
        previewScope.cancel()
        backend.shutdown()
        _isTtsReady.value = false
    }
}
