package com.example.engines

import android.util.Log
import com.example.domain.model.ProsodyPacing
import com.example.domain.model.TtsVoiceOption
import com.example.domain.model.VoiceEmotionPreset
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "GeminiNeuralVoiceEngine"

data class NeuralVoicePersona(
    val id: String,
    val rawVoiceName: String,
    val displayName: String,
    val genderTag: String,
    val genderCategory: String,
    val styleDescription: String,
    val emotionTags: List<String>,
    val prosodyDescription: String
)

/**
 * Generates expressive, studio-grade human speech in Spanish using Firebase AI Logic
 * (`gemini-3.1-flash-tts-preview`) with emotional & prosodic acting direction so productions
 * and voice previews avoid robotic device TTS outputs.
 */
class GeminiNeuralVoiceEngine(
    private val customSynthesizer: (suspend (prompt: String, rawVoiceName: String) -> Pair<String, ByteArray>)? = null
) {

    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    @OptIn(PublicPreviewAPI::class)
    private fun modelForVoice(rawVoiceName: String): GenerativeModel {
        return modelCache.getOrPut(rawVoiceName) {
            val config = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(voice = Voice(rawVoiceName))
            }
            Firebase.ai.generativeModel(
                modelName = MODEL_NAME,
                generationConfig = config
            )
        }
    }

    suspend fun synthesizeToWavFile(
        text: String,
        voiceName: String?,
        emotion: String = "Natural",
        actingNote: String = "",
        outFile: File,
        characterIndex: Int = 0,
        emotionPreset: VoiceEmotionPreset = VoiceEmotionPreset.NATURAL_WARM,
        prosodyPacing: ProsodyPacing = ProsodyPacing.NATURAL
    ): File {
        val rawVoice = extractRawGeminiVoiceName(voiceName, characterIndex)
        val prompt = buildExpressivePrompt(
            text = text,
            rawVoice = rawVoice,
            emotion = emotion,
            actingNote = actingNote,
            emotionPreset = emotionPreset,
            prosodyPacing = prosodyPacing
        )

        val (mimeType, audioBytes) = withTimeout(45_000L) {
            val custom = customSynthesizer
            if (custom != null) {
                custom(prompt, rawVoice)
            } else {
                val response = modelForVoice(rawVoice).generateContent(prompt)
                val audioParts = response.candidates.firstOrNull()
                    ?.content
                    ?.parts
                    ?.filterIsInstance<InlineDataPart>()
                    ?.filter { it.mimeType.startsWith("audio/", ignoreCase = true) || it.inlineData.isNotEmpty() }
                    .orEmpty()

                if (audioParts.isEmpty()) {
                    throw IllegalStateException("Gemini TTS no devolvió pistas de audio para la voz $rawVoice.")
                }
                val combinedStream = ByteArrayOutputStream()
                var detectedMime = audioParts.first().mimeType
                for (part in audioParts) {
                    if (part.mimeType.isNotBlank()) detectedMime = part.mimeType
                    combinedStream.write(part.inlineData)
                }
                detectedMime to combinedStream.toByteArray()
            }
        }

        if (audioBytes.isEmpty()) {
            throw IllegalStateException("Gemini TTS devolvió audio vacío para $rawVoice.")
        }

        withContext(Dispatchers.IO) {
            outFile.parentFile?.mkdirs()
            val lowerMime = mimeType.lowercase()
            val isAlreadyWav = lowerMime.contains("wav") ||
                (audioBytes.size > 12 &&
                    audioBytes[0] == 'R'.code.toByte() &&
                    audioBytes[1] == 'I'.code.toByte() &&
                    audioBytes[2] == 'F'.code.toByte() &&
                    audioBytes[3] == 'F'.code.toByte())

            FileOutputStream(outFile).use { fos ->
                if (isAlreadyWav) {
                    fos.write(audioBytes)
                } else {
                    val sampleRate = extractSampleRateFromMime(lowerMime)
                    val channels = extractChannelsFromMime(lowerMime)
                    fos.write(buildPcm16WavHeader(audioBytes.size.toLong(), sampleRate, channels))
                    fos.write(audioBytes)
                }
                fos.flush()
            }
        }

        if (!VoiceAudioEngine.hasAudibleSpeech(outFile)) {
            Log.w(TAG, "Gemini TTS WAV produced silent audio for voice=$rawVoice")
            throw IllegalStateException("El audio neural generado por $rawVoice resultó silencioso.")
        }
        return outFile
    }

    private fun buildExpressivePrompt(
        text: String,
        rawVoice: String,
        emotion: String,
        actingNote: String,
        emotionPreset: VoiceEmotionPreset,
        prosodyPacing: ProsodyPacing
    ): String {
        val cleanText = text.trim().ifBlank { "Pausa narrativa." }
        val audioTag = mapEmotionToAudioTag(emotion).ifBlank { emotionPreset.audioTag }
        val noteLine = if (actingNote.isNotBlank()) "- Matiz interpretativo: $actingNote\n" else ""

        return buildString {
            appendLine("## PERFIL DE AUDIO")
            appendLine("Voz base: $rawVoice")
            appendLine("Idioma objetivo: Español natural (locución humana cálida, orgánica y expresiva, cero tono robótico).")
            appendLine()
            appendLine("## NOTAS DEL DIRECTOR")
            appendLine("- Estilo emocional base: ${emotionPreset.displayName} (${emotionPreset.description})")
            appendLine("- Emoción de la línea: ${emotion.ifBlank { emotionPreset.displayName }}")
            appendLine("- Cadencia prosódica: ${prosodyPacing.directorInstruction}")
            append(noteLine)
            appendLine("- Pronunciación: Fluida, con pausas respiratorias naturales y acentuación humana realista.")
            appendLine()
            appendLine("## TRANSCRIPT:")
            if (audioTag.isNotBlank()) {
                append("$audioTag $cleanText")
            } else {
                append(cleanText)
            }
        }
    }

    private fun mapEmotionToAudioTag(emotion: String): String {
        val e = emotion.lowercase()
        return when {
            "susurr" in e -> "[whispering]"
            "miedo" in e || "terror" in e || "pánico" in e -> "[fearfully]"
            "tens" in e || "urgenc" in e -> "[tense]"
            "trist" in e || "melanc" in e || "confes" in e -> "[thoughtfully]"
            "aleg" in e || "entusiasm" in e -> "[cheerfully]"
            "seren" in e || "calm" in e || "cálid" in e -> "[warmly]"
            "grave" in e || "revelac" in e || "serio" in e -> "[seriously]"
            else -> ""
        }
    }

    companion object {
        const val MODEL_NAME = "gemini-3.1-flash-tts-preview"
        const val GEMINI_VOICE_PREFIX = "gemini:"

        val NEURAL_PERSONAS: List<NeuralVoicePersona> = listOf(
            NeuralVoicePersona(
                id = "${GEMINI_VOICE_PREFIX}Kore",
                rawVoiceName = "Kore",
                displayName = "IA Humana · Sofía (Kore)",
                genderTag = "Femenina · Clara",
                genderCategory = "Femenina",
                styleDescription = "Cálida, nítida y expresiva para narración y entrevistas",
                emotionTags = listOf("Cálida", "Empática", "Clara"),
                prosodyDescription = "Respiración orgánica, articulación limpia y pausas naturales de estudio."
            ),
            NeuralVoicePersona(
                id = "${GEMINI_VOICE_PREFIX}Charon",
                rawVoiceName = "Charon",
                displayName = "IA Humana · Mateo (Charon)",
                genderTag = "Masculina · Profunda",
                genderCategory = "Masculina",
                styleDescription = "Grave, serena y cinematográfica para confesiones y misterio",
                emotionTags = listOf("Profunda", "Confesional", "Serena"),
                prosodyDescription = "Cadencia grave cinematográfica con peso dramático en cierres de frase."
            ),
            NeuralVoicePersona(
                id = "${GEMINI_VOICE_PREFIX}Aoede",
                rawVoiceName = "Aoede",
                displayName = "IA Humana · Valeria (Aoede)",
                genderTag = "Femenina · Dramática",
                genderCategory = "Femenina",
                styleDescription = "Emotiva y envolvente para relatos y ficción",
                emotionTags = listOf("Emotiva", "Teatral", "Melódica"),
                prosodyDescription = "Inflexión rica y expresiva capaz de transitar entre calma y suspenso."
            ),
            NeuralVoicePersona(
                id = "${GEMINI_VOICE_PREFIX}Fenrir",
                rawVoiceName = "Fenrir",
                displayName = "IA Humana · Bruno (Fenrir)",
                genderTag = "Masculina · Intensa",
                genderCategory = "Masculina",
                styleDescription = "Firme y dramática para interrogatorios y suspenso",
                emotionTags = listOf("Intensa", "Tensa", "Firme"),
                prosodyDescription = "Ataque vocal seguro con énfasis prosódico ideal para interrogatorios."
            ),
            NeuralVoicePersona(
                id = "${GEMINI_VOICE_PREFIX}Leda",
                rawVoiceName = "Leda",
                displayName = "IA Humana · Camila (Leda)",
                genderTag = "Femenina · Cercana",
                genderCategory = "Femenina",
                styleDescription = "Íntima y conversacional para podcast y testimonios",
                emotionTags = listOf("Íntima", "Susurro", "Cercana"),
                prosodyDescription = "Proximidad de micrófono muy natural con matices suaves y humanos."
            ),
            NeuralVoicePersona(
                id = "${GEMINI_VOICE_PREFIX}Orus",
                rawVoiceName = "Orus",
                displayName = "IA Humana · Diego (Orus)",
                genderTag = "Masculina · Documental",
                genderCategory = "Masculina",
                styleDescription = "Equilibrada y autoritaria para crónicas y audiolibros",
                emotionTags = listOf("Documental", "Autoritaria", "Templada"),
                prosodyDescription = "Ritmo constante y elocuente de locutor profesional de documental."
            ),
            NeuralVoicePersona(
                id = "${GEMINI_VOICE_PREFIX}Zephyr",
                rawVoiceName = "Zephyr",
                displayName = "IA Humana · Elena (Zephyr)",
                genderTag = "Femenina · Ágil",
                genderCategory = "Femenina",
                styleDescription = "Brillante y moderna para reporterismo y ciencia ficción",
                emotionTags = listOf("Ágil", "Brillante", "Conversacional"),
                prosodyDescription = "Dicción fresca y ágil con ritmo conversacional contemporáneo."
            ),
            NeuralVoicePersona(
                id = "${GEMINI_VOICE_PREFIX}Puck",
                rawVoiceName = "Puck",
                displayName = "IA Humana · Lucas (Puck)",
                genderTag = "Masculina · Dinámica",
                genderCategory = "Masculina",
                styleDescription = "Espontánea y natural para charlas y diálogos",
                emotionTags = listOf("Espontánea", "Viva", "Natural"),
                prosodyDescription = "Entonación desenfadada y orgánica, perfecta para diálogos reales."
            )
        )

        fun toTtsVoiceOptions(): List<TtsVoiceOption> = NEURAL_PERSONAS.map { persona ->
            TtsVoiceOption(
                voiceName = persona.id,
                displayName = "${persona.displayName} (${persona.genderTag})",
                localeTag = "es-ES",
                isLocalOffline = false,
                qualityLabel = "IA Humana · Gemini 3.1",
                genderCategory = persona.genderCategory,
                emotionTags = persona.emotionTags,
                prosodyDescription = persona.prosodyDescription
            )
        }

        fun isGeminiVoiceName(voiceName: String?): Boolean =
            voiceName?.startsWith(GEMINI_VOICE_PREFIX) == true

        fun extractRawGeminiVoiceName(voiceName: String?, fallbackIndex: Int = 0): String {
            if (!voiceName.isNullOrBlank() && voiceName.startsWith(GEMINI_VOICE_PREFIX)) {
                val raw = voiceName.removePrefix(GEMINI_VOICE_PREFIX).trim()
                if (raw.isNotEmpty()) return raw
            }
            val safeIdx = ((fallbackIndex % NEURAL_PERSONAS.size) + NEURAL_PERSONAS.size) % NEURAL_PERSONAS.size
            return NEURAL_PERSONAS[safeIdx].rawVoiceName
        }

        fun extractSampleRateFromMime(mimeType: String): Int {
            val match = Regex("rate=(\\d+)").find(mimeType)
            return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(8000, 96000) ?: 24000
        }

        fun extractChannelsFromMime(mimeType: String): Int {
            val match = Regex("channels=(\\d+)").find(mimeType)
            return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 2) ?: 1
        }

        fun buildPcm16WavHeader(
            pcmDataBytes: Long,
            sampleRate: Int = 24000,
            channels: Int = 1
        ): ByteArray {
            val bitsPerSample = 16
            val byteRate = sampleRate.toLong() * channels * (bitsPerSample / 8)
            val blockAlign = channels * (bitsPerSample / 8)
            val totalDataLen = pcmDataBytes + 36L
            val h = ByteArray(44)
            h[0] = 'R'.code.toByte(); h[1] = 'I'.code.toByte(); h[2] = 'F'.code.toByte(); h[3] = 'F'.code.toByte()
            writeIntLE(h, 4, totalDataLen.toInt())
            h[8] = 'W'.code.toByte(); h[9] = 'A'.code.toByte(); h[10] = 'V'.code.toByte(); h[11] = 'E'.code.toByte()
            h[12] = 'f'.code.toByte(); h[13] = 'm'.code.toByte(); h[14] = 't'.code.toByte(); h[15] = ' '.code.toByte()
            writeIntLE(h, 16, 16)
            writeShortLE(h, 20, 1)
            writeShortLE(h, 22, channels)
            writeIntLE(h, 24, sampleRate)
            writeIntLE(h, 28, byteRate.toInt())
            writeShortLE(h, 32, blockAlign)
            writeShortLE(h, 34, bitsPerSample)
            h[36] = 'd'.code.toByte(); h[37] = 'a'.code.toByte(); h[38] = 't'.code.toByte(); h[39] = 'a'.code.toByte()
            writeIntLE(h, 40, pcmDataBytes.toInt())
            return h
        }

        private fun writeIntLE(b: ByteArray, offset: Int, v: Int) {
            b[offset] = (v and 0xFF).toByte()
            b[offset + 1] = ((v shr 8) and 0xFF).toByte()
            b[offset + 2] = ((v shr 16) and 0xFF).toByte()
            b[offset + 3] = ((v shr 24) and 0xFF).toByte()
        }

        private fun writeShortLE(b: ByteArray, offset: Int, v: Int) {
            b[offset] = (v and 0xFF).toByte()
            b[offset + 1] = ((v shr 8) and 0xFF).toByte()
        }
    }
}
