package com.example.engines

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.domain.model.CharacterProfile
import com.example.domain.model.DialogueLine
import com.example.domain.model.TimbreFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

private const val VOICE_ENGINE_TAG = "VoiceAudioEngine"

class VoiceAudioEngine(
    private val context: Context,
    private val neuralVoiceEngine: GeminiNeuralVoiceEngine = GeminiNeuralVoiceEngine()
) {

    private val pendingUtterances = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val utteranceCounter = AtomicLong(0L)

    /**
     * Synthesizes all dialogue lines into real audible WAV files.
     * Prioritizes natural expressive Gemini 3.1 Flash TTS human voices (`gemini-3.1-flash-tts-preview`),
     * with automatic fallback to local Android TextToSpeech (pitch-stabilized to avoid robotic distortion)
     * if a local TTS voice was explicitly selected or if the device is offline.
     */
    suspend fun synthesizeAllLines(
        lines: List<DialogueLine>,
        characters: List<CharacterProfile>,
        outputDir: File,
        onLineProgress: suspend (completedIndex: Int, total: Int, updatedLine: DialogueLine) -> Unit
    ): List<DialogueLine> {
        outputDir.mkdirs()
        var lazyTts: TextToSpeech? = null
        var cachedAllVoicesByName: Map<String, Voice>? = null
        var cachedSpanishVoices: List<Voice>? = null

        suspend fun ensureLocalTts(): TextToSpeech {
            val current = lazyTts
            if (current != null) return current
            val created = createAndInitTts()
            lazyTts = created
            cachedAllVoicesByName = runCatching {
                withContext(Dispatchers.Main) {
                    created.voices?.associateBy { it.name }.orEmpty()
                }
            }.getOrDefault(emptyMap())
            cachedSpanishVoices = selectDistinctSpanishVoices(created)
            return created
        }

        try {
            val characterMap = characters.associateBy { it.name.lowercase().trim() }
            val resultLines = mutableListOf<DialogueLine>()

            for ((idx, line) in lines.withIndex()) {
                coroutineContext.ensureActive()

                val wavFile = File(outputDir, "line_${idx.toString().padStart(4, '0')}.wav")
                val existingFile = line.synthesizedWavPath?.let { File(it) } ?: wavFile
                if (existingFile.exists() && existingFile.length() > 44L && hasAudibleSpeech(existingFile)) {
                    val durMs = if (line.actualDurationMs > 0L) line.actualDurationMs else readWavDurationMs(existingFile).coerceAtLeast(600L)
                    val resumedLine = line.copy(
                        synthesizedWavPath = existingFile.absolutePath,
                        actualDurationMs = durMs
                    )
                    resultLines.add(resumedLine)
                    onLineProgress(idx + 1, lines.size, resumedLine)
                    continue
                }

                val speakerKey = line.speakerName.lowercase().trim()
                val matchedCharIndex = characters.indexOfFirst { it.name.lowercase().trim() == speakerKey }
                    .let { exact ->
                        if (exact >= 0) exact
                        else characters.indexOfFirst {
                            val cn = it.name.lowercase().trim()
                            cn.isNotBlank() && (speakerKey.contains(cn) || cn.contains(speakerKey))
                        }
                    }
                    .let { found -> if (found >= 0) found else (idx % characters.size.coerceAtLeast(1)) }

                val charProfile = characterMap[speakerKey]
                    ?: characters.getOrNull(matchedCharIndex)
                    ?: CharacterProfile(
                        id = "default",
                        name = line.speakerName,
                        role = "Narrador",
                        ageStyle = "Adulto",
                        personality = "",
                        visualDescription = "",
                        voicePitch = 1.0f,
                        voiceRate = 1.0f,
                        timbreFilter = TimbreFilter.CRISP_NARRATOR,
                        preferredVoiceName = GeminiNeuralVoiceEngine.NEURAL_PERSONAS[matchedCharIndex % GeminiNeuralVoiceEngine.NEURAL_PERSONAS.size].id
                    )

                val preferred = charProfile.preferredVoiceName
                val shouldUseNeuralAi = preferred.isNullOrBlank() || GeminiNeuralVoiceEngine.isGeminiVoiceName(preferred)

                var synthesizedOk = false
                var lastErr: Throwable? = null

                // 1. Primary path: Expressive Gemini 3.1 Flash TTS human voices (zero robot effect)
                if (shouldUseNeuralAi) {
                    for (attempt in 0 until 2) {
                        coroutineContext.ensureActive()
                        if (wavFile.exists()) wavFile.delete()
                        try {
                            neuralVoiceEngine.synthesizeToWavFile(
                                text = line.text,
                                voiceName = preferred,
                                emotion = line.emotion,
                                actingNote = line.actingNote,
                                outFile = wavFile,
                                characterIndex = matchedCharIndex,
                                emotionPreset = charProfile.emotionPreset,
                                prosodyPacing = charProfile.prosodyPacing
                            )
                            synthesizedOk = true
                            break
                        } catch (c: CancellationException) {
                            throw c
                        } catch (e: Throwable) {
                            lastErr = e
                            Log.w(VOICE_ENGINE_TAG, "Gemini neural TTS attempt ${attempt + 1} failed for line $idx", e)
                            if (attempt == 0) delay(500L)
                        }
                    }
                }

                // 2. Fallback / Local TTS path (when offline or user explicitly selected a local device TTS voice)
                if (!synthesizedOk) {
                    val tts = ensureLocalTts()
                    val allVoices = cachedAllVoicesByName.orEmpty()
                    val spanishVoices = cachedSpanishVoices.orEmpty()
                    val preferredLocal = preferred
                        ?.takeIf { !GeminiNeuralVoiceEngine.isGeminiVoiceName(it) }
                        ?.let { allVoices[it] }
                        ?.takeIf { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
                    val assignedVoice = preferredLocal
                        ?: spanishVoices.getOrNull(matchedCharIndex % spanishVoices.size.coerceAtLeast(1))
                    val fallbackVoice = spanishVoices.firstOrNull { it != assignedVoice }

                    // Keep local TTS pitch/rate very close to 1.0x so device formant shifters don't sound metallic/robotic
                    val (pitch, rate) = computeNaturalLocalTtsParameters(charProfile, line.emotion)

                    val voiceCandidates: List<Voice?> = buildList {
                        if (assignedVoice != null) add(assignedVoice)
                        if (fallbackVoice != null && fallbackVoice != assignedVoice) add(fallbackVoice)
                        add(null)
                    }

                    for ((attempt, candidateVoice) in voiceCandidates.withIndex()) {
                        coroutineContext.ensureActive()
                        if (wavFile.exists()) wavFile.delete()
                        try {
                            if (attempt == voiceCandidates.lastIndex && attempt > 0) {
                                withContext(Dispatchers.Main) {
                                    val es = Locale.forLanguageTag("es-ES")
                                    if (tts.setLanguage(es) < 0) {
                                        tts.setLanguage(Locale.getDefault())
                                    }
                                }
                            }
                            synthesizeSingleLine(
                                tts = tts,
                                text = line.text,
                                voice = candidateVoice,
                                pitch = pitch,
                                rate = rate,
                                outFile = wavFile,
                                utteranceId = "mohana_utt_${idx}_${attempt}_${utteranceCounter.incrementAndGet()}"
                            )
                            if (!hasAudibleSpeech(wavFile)) {
                                throw IllegalStateException("La síntesis TTS produjo silencio PCM en ${wavFile.name}.")
                            }
                            synthesizedOk = true
                            break
                        } catch (c: CancellationException) {
                            throw c
                        } catch (e: Throwable) {
                            lastErr = e
                        }
                    }
                }

                if (!synthesizedOk) {
                    if (wavFile.exists()) wavFile.delete()
                    throw IllegalStateException(
                        "No se pudo obtener audio de voz audible para \"${line.speakerName}\" " +
                            "(${lastErr?.localizedMessage ?: "sin salida PCM"}).",
                        lastErr
                    )
                }

                val actualMs = readWavDurationMs(wavFile).coerceAtLeast(600L)
                val updated = line.copy(
                    synthesizedWavPath = wavFile.absolutePath,
                    actualDurationMs = actualMs
                )
                resultLines.add(updated)
                onLineProgress(idx + 1, lines.size, updated)
            }
            return resultLines
        } finally {
            pendingUtterances.values.forEach { it.cancel() }
            pendingUtterances.clear()
            val ttsToClose = lazyTts
            if (ttsToClose != null) {
                withContext(Dispatchers.Main) {
                    runCatching {
                        ttsToClose.stop()
                        ttsToClose.shutdown()
                    }
                }
            }
        }
    }

    private suspend fun createAndInitTts(): TextToSpeech {
        val initDeferred = CompletableDeferred<Int>()
        var ttsInstance: TextToSpeech? = null
        withContext(Dispatchers.Main) {
            ttsInstance = TextToSpeech(context.applicationContext) { status ->
                initDeferred.complete(status)
            }
        }
        val status = withTimeout(10_000L) { initDeferred.await() }
        val tts = ttsInstance ?: throw IllegalStateException("No se pudo inicializar TextToSpeech.")
        if (status != TextToSpeech.SUCCESS) {
            withContext(Dispatchers.Main) { runCatching { tts.shutdown() } }
            throw IllegalStateException("El motor TextToSpeech del dispositivo devolvió error ($status).")
        }

        withContext(Dispatchers.Main) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            val esLocale = Locale.forLanguageTag("es-ES")
            val esMxLocale = Locale.forLanguageTag("es-MX")
            val langResult = tts.setLanguage(esLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                val mxResult = tts.setLanguage(esMxLocale)
                if (mxResult == TextToSpeech.LANG_MISSING_DATA || mxResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val esUsResult = tts.setLanguage(Locale.forLanguageTag("es-US"))
                    if (esUsResult == TextToSpeech.LANG_MISSING_DATA || esUsResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        val defRes = tts.setLanguage(Locale.getDefault())
                        if (defRes == TextToSpeech.LANG_MISSING_DATA || defRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts.setLanguage(Locale.US)
                        }
                    }
                }
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { pendingUtterances.remove(it)?.complete(Unit) }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let {
                        pendingUtterances.remove(it)?.completeExceptionally(
                            IllegalStateException("Error sintetizando voz para $it")
                        )
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    utteranceId?.let {
                        pendingUtterances.remove(it)?.completeExceptionally(
                            IllegalStateException("Error TTS código $errorCode en $it")
                        )
                    }
                }
            })
        }
        return tts
    }

    private suspend fun selectDistinctSpanishVoices(tts: TextToSpeech): List<Voice> =
        withContext(Dispatchers.Main) {
            val allVoices = runCatching { tts.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            val installed = allVoices.filter { v ->
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features
            }.ifEmpty { allVoices }

            val spanishVoices = installed.filter {
                it.locale.language.equals("es", ignoreCase = true)
            }
            val naturalNetworkSpanish = spanishVoices
                .filter { it.quality >= Voice.QUALITY_HIGH }
                .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })

            when {
                naturalNetworkSpanish.isNotEmpty() -> naturalNetworkSpanish
                spanishVoices.isNotEmpty() -> spanishVoices.sortedBy { it.name }
                else -> installed.sortedByDescending { it.quality }
            }
        }

    private fun computeNaturalLocalTtsParameters(
        character: CharacterProfile,
        emotion: String
    ): Pair<Float, Float> {
        val emoLower = emotion.lowercase()
        // Clamp pitch tightly near 1.0f to prevent robotic formant artifacts on Android TTS
        var pitch = character.voicePitch.coerceIn(0.94f, 1.06f)
        var rate = character.voiceRate.coerceIn(0.90f, 1.12f)

        when {
            "susurro" in emoLower || "miedo" in emoLower -> {
                pitch = (pitch * 0.98f).coerceIn(0.94f, 1.06f)
                rate = (rate * 0.92f).coerceIn(0.88f, 1.12f)
            }
            "urgencia" in emoLower || "tensión" in emoLower -> {
                pitch = (pitch * 1.02f).coerceIn(0.94f, 1.06f)
                rate = (rate * 1.05f).coerceIn(0.90f, 1.12f)
            }
        }
        return pitch to rate
    }

    private suspend fun synthesizeSingleLine(
        tts: TextToSpeech,
        text: String,
        voice: Voice?,
        pitch: Float,
        rate: Float,
        outFile: File,
        utteranceId: String
    ) {
        val cleanText = text.trim().ifBlank { "Pausa narrativa." }
        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1000)
        val trimmedText = if (cleanText.length > maxLen) cleanText.take(maxLen) else cleanText

        val deferred = CompletableDeferred<Unit>()
        pendingUtterances[utteranceId] = deferred

        try {
            withContext(Dispatchers.Main) {
                if (voice != null) {
                    runCatching { tts.voice = voice }
                }
                tts.setPitch(pitch)
                tts.setSpeechRate(rate)
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                val result = tts.synthesizeToFile(trimmedText, params, outFile, utteranceId)
                if (result != TextToSpeech.SUCCESS) {
                    pendingUtterances.remove(utteranceId)
                    throw IllegalStateException(
                        "TextToSpeech.synthesizeToFile falló (código $result). Verifica que el dispositivo tenga un motor TTS de voz instalado."
                    )
                }
            }
            val timeoutMs = maxOf(45_000L, trimmedText.length * 160L)
            withTimeout(timeoutMs) {
                deferred.await()
            }
            if (!outFile.exists() || outFile.length() <= 44L) {
                throw IllegalStateException("El archivo WAV sintetizado está vacío: ${outFile.name}")
            }
        } catch (e: CancellationException) {
            pendingUtterances.remove(utteranceId)
            throw e
        } catch (e: Exception) {
            pendingUtterances.remove(utteranceId)
            throw e
        }
    }

    companion object {
        fun hasAudibleSpeech(file: File): Boolean {
            if (!file.exists() || file.length() <= 44L) return false
            return runCatching {
                val decoded = MusicSfxEngine.decodeWavToMonoFloat(file.readBytes(), normalizeSpeech = false)
                    ?: return@runCatching false
                val samples = decoded.second
                var maxAbs = 0f
                for (s in samples) {
                    val a = abs(s)
                    if (a > maxAbs) {
                        maxAbs = a
                        if (maxAbs >= 0.002f) return@runCatching true
                    }
                }
                false
            }.getOrDefault(false)
        }

        fun readWavDurationMs(file: File): Long {
            if (!file.exists() || file.length() <= 44L) return 0L
            return runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    val prefixLen = minOf(4096L, file.length()).toInt()
                    val header = ByteArray(prefixLen)
                    raf.readFully(header)

                    val info = MusicSfxEngine.parseWavHeader(header)
                    if (info != null) {
                        val bytesPerSecond = info.sampleRate.toLong() * info.channels * (info.bitsPerSample / 8).coerceAtLeast(1)
                        val actualDataBytes = (file.length() - info.dataOffset.toLong()).coerceAtLeast(0L)
                        val effectiveDataBytes = if (info.dataLength > 0 && actualDataBytes > 0L) {
                            minOf(info.dataLength.toLong(), actualDataBytes)
                        } else {
                            actualDataBytes
                        }
                        if (bytesPerSecond > 0L) {
                            return@use (effectiveDataBytes * 1000L) / bytesPerSecond
                        }
                    }

                    val sampleRate = readIntLE(header, 24)
                    val channels = readShortLE(header, 22)
                    val bitsPerSample = readShortLE(header, 34)
                    val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8).coerceAtLeast(1)
                    if (bytesPerSecond <= 0) return@use 0L
                    val dataSize = (file.length() - 44L).coerceAtLeast(0L)
                    (dataSize * 1000L) / bytesPerSecond
                }
            }.getOrDefault(0L)
        }

        private fun readIntLE(b: ByteArray, offset: Int): Int {
            return (b[offset].toInt() and 0xFF) or
                ((b[offset + 1].toInt() and 0xFF) shl 8) or
                ((b[offset + 2].toInt() and 0xFF) shl 16) or
                ((b[offset + 3].toInt() and 0xFF) shl 24)
        }

        private fun readShortLE(b: ByteArray, offset: Int): Int {
            return (b[offset].toInt() and 0xFF) or
                ((b[offset + 1].toInt() and 0xFF) shl 8)
        }
    }
}
