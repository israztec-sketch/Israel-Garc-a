package com.example.ai

import android.util.Log
import com.example.domain.model.BookChapter
import com.example.domain.model.CharacterProfile
import com.example.domain.model.ContentGenre
import com.example.domain.model.DialogueLine
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionScript
import com.example.domain.model.TimbreFilter
import com.example.domain.model.VisualScene
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val TAG = "NarrativeBrain"

data class ActGenerationResult(
    val lines: List<DialogueLine>,
    val scenes: List<VisualScene>,
    val continuitySummary: String
)

class NarrativeBrain {

    private val foundationSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string("Título atractivo y evocador en español"),
            "logline" to Schema.string("Sinopsis dramática de 2 frases"),
            "characterVisualBible" to Schema.string("Descripción visual consistente de personajes, vestuario, edad, iluminación y entorno para mantener coherencia en todas las escenas"),
            "characters" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string(),
                        "name" to Schema.string(),
                        "role" to Schema.string("Ej: Entrevistador, Testigo, Narrador, Detective, Entidad"),
                        "ageStyle" to Schema.string("Joven, Adulto, Maduro o Anciano"),
                        "personality" to Schema.string(),
                        "visualDescription" to Schema.string(),
                        "voicePitch" to Schema.double("Valor entre 0.72 (grave) y 1.25 (agudo)"),
                        "voiceRate" to Schema.double("Valor entre 0.85 (pausado) y 1.15 (ágil)"),
                        "timbreFilter" to Schema.enumeration(
                            listOf("CRISP_NARRATOR", "WARM_STUDIO", "DEEP_CHAMBER", "RADIO_FILTER")
                        )
                    )
                ),
                description = "Mínimo 2 personajes diferenciados para entrevistas o historias dialogadas"
            ),
            "actOutlines" to Schema.array(
                items = Schema.string(),
                description = "Lista ordenada de actos o capítulos con progresión de suspenso, revelaciones y final desarrollado"
            ),
            "endingSummary" to Schema.string("Resolución y giro final de la obra")
        )
    )

    private val actDialoguesSchema = Schema.obj(
        properties = mapOf(
            "continuitySummary" to Schema.string("Resumen breve de los hechos clave ocurridos en este acto para mantener continuidad en el siguiente"),
            "scenes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "sceneNumber" to Schema.integer(),
                        "title" to Schema.string(),
                        "settingDescription" to Schema.string(),
                        "visualPrompt" to Schema.string("Prompt cinematográfico en inglés detallando escena, personajes de la biblia visual, iluminación y cámara, sin texto ni marcas de agua"),
                        "mood" to Schema.enumeration(
                            listOf("TERROR_DRONE", "SUSPENSE_PULSE", "MYSTERY_AMBIENT", "SCIFI_SYNTH", "CALM_PODCAST")
                        )
                    )
                )
            ),
            "lines" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "sceneNumber" to Schema.integer(),
                        "speakerName" to Schema.string(),
                        "emotion" to Schema.string("Ej: Calma, Tensión, Susurro, Miedo, Revelación, Duda"),
                        "actingNote" to Schema.string("Indicación breve de interpretación vocal o respiración"),
                        "text" to Schema.string("Diálogo o narración natural en español"),
                        "pauseAfterMs" to Schema.integer("Silencio dramático posterior en milisegundos (350 a 2200)"),
                        "sfxCue" to Schema.enumeration(
                            listOf("NONE", "IMPACT_BOOM", "HEARTBEAT", "TAPE_STATIC", "GHOST_WHOOSH", "RISER", "ROOM_TONE")
                        ),
                        "musicMood" to Schema.enumeration(
                            listOf("TERROR_DRONE", "SUSPENSE_PULSE", "MYSTERY_AMBIENT", "SCIFI_SYNTH", "CALM_PODCAST")
                        )
                    )
                )
            )
        )
    )

    private val chapterSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(),
            "subtitle" to Schema.string(),
            "bodyParagraphs" to Schema.array(
                items = Schema.string(),
                description = "Párrafos extensos, profundos y bien redactados en español"
            ),
            "keyTakeaways" to Schema.array(
                items = Schema.string(),
                description = "Puntos clave, notas o diapositivas de síntesis"
            )
        )
    )

    private val foundationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = foundationSchema
                temperature = 0.85f
            }
        )
    }

    private val foundationFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = foundationSchema
                temperature = 0.8f
            }
        )
    }

    private val actModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = actDialoguesSchema
                temperature = 0.85f
            }
        )
    }

    private val actFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = actDialoguesSchema
                temperature = 0.8f
            }
        )
    }

    private val chapterModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = chapterSchema
                temperature = 0.8f
            }
        )
    }

    private val chapterFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = chapterSchema
                temperature = 0.75f
            }
        )
    }

    private suspend fun generateJsonWithResilience(
        primaryModel: GenerativeModel,
        fallbackModel: GenerativeModel,
        prompt: String,
        operationLabel: String
    ): JSONObject {
        return try {
            val response = withTimeout(60_000L) {
                primaryModel.generateContent(prompt)
            }
            val raw = response.text?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("El Cerebro Narrativo devolvió respuesta vacía en $operationLabel.")
            parseJsonSafely(raw)
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.w(TAG, "Timeout on gemini-3.8-flash in $operationLabel, trying gemini-3.5-flash-lite", e)
            retryWithFallbackModel(fallbackModel, prompt, operationLabel)
        } catch (e: Exception) {
            if (isNonRetryableAuthOrConfigError(e)) {
                throw e
            }
            Log.w(TAG, "Primary model failed in $operationLabel (${e.message}), trying gemini-3.5-flash-lite", e)
            try {
                retryWithFallbackModel(fallbackModel, prompt, operationLabel)
            } catch (fallbackErr: Exception) {
                if (fallbackErr is CancellationException && fallbackErr !is TimeoutCancellationException) {
                    throw fallbackErr
                }
                Log.e(TAG, "Both primary and fallback models failed in $operationLabel", fallbackErr)
                throw e
            }
        }
    }

    private suspend fun retryWithFallbackModel(
        fallbackModel: GenerativeModel,
        prompt: String,
        operationLabel: String
    ): JSONObject {
        val response = withTimeout(60_000L) {
            fallbackModel.generateContent(prompt)
        }
        val raw = response.text?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Respuesta vacía en $operationLabel.")
        return parseJsonSafely(raw)
    }

    private fun isNonRetryableAuthOrConfigError(t: Throwable): Boolean {
        val msg = "${t.localizedMessage.orEmpty()} ${t.cause?.localizedMessage.orEmpty()}"
        return msg.contains("403", ignoreCase = true) ||
            msg.contains("app check", ignoreCase = true) ||
            msg.contains("attestation", ignoreCase = true) ||
            msg.contains("api key", ignoreCase = true) ||
            msg.contains("permission_denied", ignoreCase = true)
    }

    private fun parseJsonSafely(raw: String): JSONObject {
        val cleaned = raw
            .removePrefix("\uFEFF")
            .trim()
            .let { text ->
                if (text.startsWith("```")) {
                    text.removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                } else {
                    text
                }
            }
        return try {
            JSONObject(cleaned)
        } catch (e: JSONException) {
            val firstBrace = cleaned.indexOf('{')
            val lastBrace = cleaned.lastIndexOf('}')
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                JSONObject(cleaned.substring(firstBrace, lastBrace + 1))
            } else {
                throw e
            }
        }
    }

    suspend fun generateStoryFoundation(
        ideaPrompt: String,
        genre: ContentGenre,
        outputFormat: OutputFormat,
        targetMinutes: Int
    ): ProductionScript {
        val plannedActs = when (outputFormat) {
            OutputFormat.VIDEOPODCAST_40MIN -> 10
            OutputFormat.BOOK_DOCUMENT -> 6
            OutputFormat.AUDIO_VIDEO_EPISODE, OutputFormat.AUDIO_DRAMA_WAV -> 3
        }

        val formatContext = when (outputFormat) {
            OutputFormat.VIDEOPODCAST_40MIN ->
                "Videopodcast narrativo de larga duración (mínimo $targetMinutes minutos, estructurado en $plannedActs actos profundos y continuos)."
            OutputFormat.BOOK_DOCUMENT ->
                "Obra editorial completa ($plannedActs capítulos o secciones estructuradas con rigor narrativo y documental)."
            else ->
                "Producción audiovisual dramática completa en $plannedActs actos con inicio, escalada de tensión, clímax y final desarrollado."
        }

        val genreGuidance = when (genre) {
            ContentGenre.INTERVIEW_CONFESSION, ContentGenre.INTERROGATION_TESTIMONY ->
                "OBLIGATORIO: Incluye mínimo 2 personajes principales bien diferenciados (por ejemplo, un Entrevistador/Detective sereno y un Testigo/Personaje misterioso con secretos sobrenaturales o inquietantes). La entrevista debe funcionar como una confesión real con preguntas, evasivas, silencios y revelaciones progresivas."
            ContentGenre.CREEPYPASTA ->
                "Crea una creepypasta o relato de terror sobrenatural con Narrador principal y al menos una segunda voz testimonial/entidad para dar profundidad dramática y terror progresivo."
            else ->
                "Desarrolla personajes psicológicamente coherentes con voces y roles claramente diferenciados."
        }

        val prompt = """
            Eres el Cerebro Narrativo de MOHANA AI (Inteligencia Creativa).
            Diseña la arquitectura completa en ESPAÑOL para esta idea:
            "$ideaPrompt"

            Género: ${genre.displayName} (${genre.description})
            Formato: $formatContext
            $genreGuidance

            Reglas:
            - Genera exactamente $plannedActs elementos en actOutlines, cada uno describiendo un acto/capítulo consecutivo con progresión dramática real hasta un final desarrollado.
            - Define voicePitch (0.75 a 1.22) y voiceRate (0.86 a 1.12) distintos para cada personaje para que sus voces se distingan claramente.
            - En characterVisualBible describe con precisión el aspecto físico, vestimenta y ambientación para mantener consistencia visual entre escenas.
        """.trimIndent()

        try {
            val json = generateJsonWithResilience(
                primaryModel = foundationModel,
                fallbackModel = foundationFallbackModel,
                prompt = prompt,
                operationLabel = "Escritura Inteligente"
            )

            val charsArr = json.optJSONArray("characters") ?: JSONArray()
            val characters = (0 until charsArr.length()).mapNotNull { idx ->
                charsArr.optJSONObject(idx)?.let { CharacterProfile.fromJson(it) }
            }.ifEmpty {
                listOf(
                    CharacterProfile(
                        id = "host",
                        name = "Elena Vance",
                        role = "Entrevistadora",
                        ageStyle = "Adulto",
                        personality = "Analítica, serena e incisiva",
                        visualDescription = "Woman in dark tailored blazer inside dimly lit broadcast studio",
                        voicePitch = 1.08f,
                        voiceRate = 1.0f,
                        timbreFilter = TimbreFilter.WARM_STUDIO
                    ),
                    CharacterProfile(
                        id = "guest",
                        name = "Mateo Solís",
                        role = "Testigo",
                        ageStyle = "Maduro",
                        personality = "Reservado, perturbado, habla con cautela",
                        visualDescription = "Weathered man in dark coat sitting under a single overhead tungsten lamp",
                        voicePitch = 0.80f,
                        voiceRate = 0.90f,
                        timbreFilter = TimbreFilter.DEEP_CHAMBER
                    )
                )
            }

            val outlinesArr = json.optJSONArray("actOutlines") ?: JSONArray()
            val outlines = (0 until outlinesArr.length()).map { outlinesArr.optString(it) }
                .filter { it.isNotBlank() }

            return ProductionScript(
                title = json.optString("title", ideaPrompt.take(48)),
                logline = json.optString("logline", ""),
                characterVisualBible = json.optString("characterVisualBible", ""),
                totalPlannedActs = outlines.size.coerceAtLeast(1),
                completedActs = 0,
                actOutlines = outlines.ifEmpty { listOf("Acto único completo") },
                characters = characters,
                lines = emptyList(),
                scenes = emptyList(),
                chapters = emptyList(),
                endingSummary = json.optString("endingSummary", "")
            )
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                Log.e(TAG, "Timeout generating story foundation", e)
                throw IllegalStateException("Tiempo de espera agotado (60s) al conectar con Gemini en Escritura Inteligente.", e)
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error generating story foundation", e)
            throw e
        }
    }

    suspend fun generateActDialogues(
        script: ProductionScript,
        genre: ContentGenre,
        outputFormat: OutputFormat,
        actNumber: Int,
        actOutline: String,
        previousSummary: String,
        startingSceneNumber: Int,
        startingLineIndex: Int
    ): ActGenerationResult {
        val isLongPodcast = outputFormat == OutputFormat.VIDEOPODCAST_40MIN
        val targetLinesCount = if (isLongPodcast) "22 a 28" else "12 a 16"
        val scenesCount = if (isLongPodcast) 2 else 2

        val charactersDesc = script.characters.joinToString("\n") { c ->
            "- ${c.name} (${c.role}, ${c.ageStyle}): ${c.personality}"
        }

        val prompt = """
            Eres el Motor de Escritura y Entrevistas de MOHANA AI.
            Obra: "${script.title}"
            Sinopsis: ${script.logline}
            Género: ${genre.displayName}
            Biblia visual de personajes (MANTENER CONSISTENCIA): ${script.characterVisualBible}

            Personajes disponibles (usa exactamente estos nombres en speakerName):
            $charactersDesc

            Continuidad previa: ${previousSummary.ifBlank { "Inicio de la historia." }}

            Genera ahora el ACTO $actNumber de ${script.totalPlannedActs}:
            Objetivo del acto: $actOutline
            ${if (actNumber == script.totalPlannedActs) "IMPORTANTE: Este es el acto final. Desarrolla el clímax y el desenlace completo: ${script.endingSummary}" else ""}

            Instrucciones estrictas:
            1. Crea $scenesCount escenas nuevas numeradas desde $startingSceneNumber en "scenes". Cada "visualPrompt" debe estar en INGLÉS cinematográfico muy detallado, incorporando la biblia visual de los personajes presentes.
            2. Genera entre $targetLinesCount líneas de diálogo/narración en español natural, inmersivo, con preguntas, respuestas, silencios dramáticos (pauseAfterMs entre 400 y 2200), acotaciones de actuación (actingNote) e indicaciones reales de efectos sonoros (sfxCue) sin abusar.
            3. ${if (isLongPodcast) "Cada diálogo debe ser sustancial (2 a 4 oraciones bien desarrolladas) para sostener un videopodcast profundo de 40+ minutos." else "Mantén ritmo cinematográfico, suspenso progresivo y naturalidad."}
        """.trimIndent()

        try {
            val json = generateJsonWithResilience(
                primaryModel = actModel,
                fallbackModel = actFallbackModel,
                prompt = prompt,
                operationLabel = "Acto $actNumber"
            )

            val scenesArr = json.optJSONArray("scenes") ?: JSONArray()
            val scenes = (0 until scenesArr.length()).mapIndexedNotNull { idx, _ ->
                val s = scenesArr.optJSONObject(idx) ?: return@mapIndexedNotNull null
                val assignedNumber = startingSceneNumber + idx
                VisualScene(
                    sceneNumber = s.optInt("sceneNumber", assignedNumber).let {
                        if (it < startingSceneNumber) assignedNumber else it
                    },
                    title = s.optString("title", "Escena $assignedNumber"),
                    settingDescription = s.optString("settingDescription", ""),
                    visualPrompt = s.optString("visualPrompt", script.characterVisualBible),
                    mood = s.optString("mood", genre.defaultMusicMood)
                )
            }

            val validSceneNumbers = scenes.map { it.sceneNumber }.toSet()
            val defaultSceneNum = scenes.firstOrNull()?.sceneNumber ?: startingSceneNumber

            val linesArr = json.optJSONArray("lines") ?: JSONArray()
            val lines = (0 until linesArr.length()).mapIndexedNotNull { idx, _ ->
                val l = linesArr.optJSONObject(idx) ?: return@mapIndexedNotNull null
                val rawScene = l.optInt("sceneNumber", defaultSceneNum)
                val sceneNum = if (rawScene in validSceneNumbers) rawScene else defaultSceneNum
                DialogueLine(
                    index = startingLineIndex + idx,
                    actNumber = actNumber,
                    sceneNumber = sceneNum,
                    speakerName = l.optString("speakerName", script.characters.firstOrNull()?.name ?: "Narrador"),
                    emotion = l.optString("emotion", "Calma"),
                    actingNote = l.optString("actingNote", ""),
                    text = l.optString("text", "").trim(),
                    pauseAfterMs = l.optLong("pauseAfterMs", 650L).coerceIn(250L, 2800L),
                    sfxCue = l.optString("sfxCue", "NONE"),
                    musicMood = l.optString("musicMood", genre.defaultMusicMood)
                )
            }.filter { it.text.isNotBlank() }

            return ActGenerationResult(
                lines = lines,
                scenes = scenes,
                continuitySummary = json.optString("continuitySummary", actOutline)
            )
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                Log.e(TAG, "Timeout generating act $actNumber dialogues", e)
                throw IllegalStateException("Tiempo de espera agotado (60s) generando diálogos del Acto $actNumber.", e)
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error generating act $actNumber dialogues", e)
            throw e
        }
    }

    suspend fun generateBookChapter(
        script: ProductionScript,
        genre: ContentGenre,
        chapterNumber: Int,
        chapterOutline: String,
        previousSummary: String
    ): BookChapter {
        val prompt = """
            Eres el Motor de Libros y Documentos de MOHANA AI.
            Obra: "${script.title}"
            Sinopsis: ${script.logline}
            Género / Tipo: ${genre.displayName}
            Continuidad previa: ${previousSummary.ifBlank { "Inicio de la obra." }}

            Escribe el Capítulo / Sección $chapterNumber de ${script.totalPlannedActs}:
            Enfoque: $chapterOutline
            ${if (chapterNumber == script.totalPlannedActs) "Incluye conclusión desarrollada y cierre completo." else ""}

            Requisitos:
            - Escribe entre 5 y 7 párrafos extensos, ricos y coherentes en español editorial de alta calidad.
            - Incluye de 3 a 5 puntos clave / síntesis para presentación o resumen de capítulo en keyTakeaways.
        """.trimIndent()

        try {
            val json = generateJsonWithResilience(
                primaryModel = chapterModel,
                fallbackModel = chapterFallbackModel,
                prompt = prompt,
                operationLabel = "Capítulo $chapterNumber"
            )
            val pArr = json.optJSONArray("bodyParagraphs") ?: JSONArray()
            val paragraphs = (0 until pArr.length()).map { pArr.optString(it).trim() }.filter { it.isNotBlank() }
            val kArr = json.optJSONArray("keyTakeaways") ?: JSONArray()
            val takeaways = (0 until kArr.length()).map { kArr.optString(it).trim() }.filter { it.isNotBlank() }

            return BookChapter(
                chapterNumber = chapterNumber,
                title = json.optString("title", "Capítulo $chapterNumber"),
                subtitle = json.optString("subtitle", ""),
                bodyParagraphs = paragraphs,
                keyTakeaways = takeaways
            )
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                Log.e(TAG, "Timeout generating chapter $chapterNumber", e)
                throw IllegalStateException("Tiempo de espera agotado (60s) redactando Capítulo $chapterNumber.", e)
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error generating chapter $chapterNumber", e)
            throw e
        }
    }
}
