package com.example.domain.model

import org.json.JSONArray
import org.json.JSONObject

enum class ContentGenre(
    val displayName: String,
    val description: String,
    val defaultMusicMood: String
) {
    CREEPYPASTA(
        displayName = "Creepypasta / Terror",
        description = "Relato sobrenatural progresivo con atmósfera oscura y giro final.",
        defaultMusicMood = "TERROR_DRONE"
    ),
    INTERVIEW_CONFESSION(
        displayName = "Entrevista / Confesión",
        description = "Diálogo entre entrevistador y testigo misterioso con silencios y revelaciones.",
        defaultMusicMood = "SUSPENSE_PULSE"
    ),
    INTERROGATION_TESTIMONY(
        displayName = "Interrogatorio / Testimonio",
        description = "Tensión psicológica, preguntas incisivas y declaración grabada.",
        defaultMusicMood = "SUSPENSE_PULSE"
    ),
    MYSTERY_SUSPENSE(
        displayName = "Misterio y Suspenso",
        description = "Investigación, pistas ocultas y clímax dramático.",
        defaultMusicMood = "MYSTERY_AMBIENT"
    ),
    SCIFI(
        displayName = "Ciencia Ficción",
        description = "Bitácoras espaciales, anomalías temporales y dilemas tecnológicos.",
        defaultMusicMood = "SCIFI_SYNTH"
    ),
    BOOK_NOVEL(
        displayName = "Libro / Novela",
        description = "Obra literaria estructurada por capítulos con continuidad narrativa.",
        defaultMusicMood = "MYSTERY_AMBIENT"
    ),
    DOCUMENT_PRESENTATION(
        displayName = "Documento / Presentación",
        description = "Informe editorial, dossier o presentación estructurada exportable.",
        defaultMusicMood = "CALM_PODCAST"
    )
}

enum class OutputFormat(
    val displayName: String,
    val subtitle: String,
    val minMinutes: Int
) {
    AUDIO_VIDEO_EPISODE(
        displayName = "Video Completo + Audio WAV",
        subtitle = "Guion, voces, música, escenas IA, subtítulos y MP4 final",
        minMinutes = 3
    ),
    VIDEOPODCAST_40MIN(
        displayName = "Videopodcast Largo (40+ min)",
        subtitle = "Producción extendida de mínimo 40 minutos en actos continuos",
        minMinutes = 40
    ),
    AUDIO_DRAMA_WAV(
        displayName = "Audio Drama Maestro (WAV)",
        subtitle = "Guion, voces diferenciadas, efectos y música en WAV",
        minMinutes = 4
    ),
    BOOK_DOCUMENT(
        displayName = "Libro / Documento / Presentación",
        subtitle = "Capítulos completos exportados a PDF, HTML, Markdown y TXT",
        minMinutes = 0
    )
}

enum class VideoResolution(
    val displayName: String,
    val width: Int,
    val height: Int
) {
    FULL_HD_1080P("Full HD 1920×1080", 1920, 1080),
    UHD_4K("4K UHD 3840×2160", 3840, 2160)
}

enum class ProductionStage(
    val displayName: String,
    val stepOrder: Int
) {
    IDEA("Idea", 0),
    SCRIPT("Guion", 1),
    DIALOGUES("Diálogos", 2),
    VOICES("Voces", 3),
    AUDIO_MIX("Audio y Música", 4),
    SCENES("Escenas", 5),
    VIDEO_MONTAGE("Montaje y Video", 6),
    BOOK_EXPORT("Libro / PDF", 6),
    COMPLETED("Completado", 7),
    PAUSED("Pausado / Recuperable", 0),
    FAILED("Requiere Atención", 0)
}

enum class EngineId(val displayName: String, val subtitle: String) {
    WRITING("1. Escritura Inteligente", "Historias, creepypastas, libros y guiones"),
    INTERVIEW("2. Entrevistas y Confesiones", "Diálogos independientes, silencios y giros"),
    VOICES("3. Voces y Audio WAV", "Múltiples timbres, edades y síntesis PCM"),
    VISUAL_VIDEO("4. Visual y Render MP4", "Consistencia visual, subtítulos y MediaCodec"),
    AUTO_PIPELINE("5. Producción Automática", "Orquestación con checkpoints recuperables"),
    MUSIC_SFX("6. Música y Efectos DSP", "Atmósfera, ducking y efectos sincronizados"),
    BOOKS_DOCS("7. Libros y Documentos", "Capítulos, PDF paginado, HTML y Markdown")
}

enum class OrchestratorStep(
    val displayName: String,
    val engineId: EngineId,
    val stage: ProductionStage,
    val baseProgressPercent: Int
) {
    WRITING_FOUNDATION("Estructura y Biblia Narrativa", EngineId.WRITING, ProductionStage.SCRIPT, 12),
    BOOK_CHAPTERS("Redacción de Capítulos", EngineId.BOOKS_DOCS, ProductionStage.SCRIPT, 80),
    BOOK_EXPORT("Exportación Documental", EngineId.BOOKS_DOCS, ProductionStage.BOOK_EXPORT, 100),
    DIALOGUE_ACTS("Guion, Entrevista y Diálogos", EngineId.INTERVIEW, ProductionStage.DIALOGUES, 38),
    SCRIPT_DOCS_EXPORT("Documentos PDF/HTML del Guion", EngineId.BOOKS_DOCS, ProductionStage.DIALOGUES, 40),
    VOICES_WAV("Síntesis de Voces WAV", EngineId.VOICES, ProductionStage.VOICES, 60),
    MUSIC_MIX("Mezcla Maestra Música y SFX", EngineId.MUSIC_SFX, ProductionStage.AUDIO_MIX, 72),
    SCENE_IMAGES("Generación de Escenas IA", EngineId.VISUAL_VIDEO, ProductionStage.SCENES, 85),
    VIDEO_RENDER("Renderizado Final MP4", EngineId.VISUAL_VIDEO, ProductionStage.VIDEO_MONTAGE, 100),
    COMPLETED("Producción Completada", EngineId.AUTO_PIPELINE, ProductionStage.COMPLETED, 100)
}

enum class EngineState {
    IDLE,
    RUNNING,
    COMPLETED,
    ERROR,
    SKIPPED
}

data class EngineTelemetry(
    val engineId: EngineId,
    val state: EngineState = EngineState.IDLE,
    val detail: String = "En espera"
)

data class BrainTelemetry(
    val narrativeBrainStatus: String = "Gemini 3.8 Flash · Listo",
    val visualBrainStatus: String = "Nano Banana 2.1 + Dirección · Listo"
)

enum class TimbreFilter {
    CRISP_NARRATOR,
    WARM_STUDIO,
    DEEP_CHAMBER,
    RADIO_FILTER
}

enum class VoiceEmotionPreset(
    val displayName: String,
    val description: String,
    val audioTag: String
) {
    NATURAL_WARM(
        displayName = "Natural y Cálida",
        description = "Voz humana cercana y orgánica, ideal para conversación real",
        audioTag = "[warmly]"
    ),
    CONFESSIONAL_INTIMATE(
        displayName = "Confesión Íntima",
        description = "Tono íntimo y reflexivo con respiraciones suaves",
        audioTag = "[thoughtfully]"
    ),
    DRAMATIC_TENSE(
        displayName = "Suspenso y Tensión",
        description = "Inflexión contenida e intensidad dramática",
        audioTag = "[tense]"
    ),
    NARRATIVE_SOLEMN(
        displayName = "Narración Cinematográfica",
        description = "Locución pausada, profunda y documental",
        audioTag = "[seriously]"
    ),
    ENERGETIC_CONVERSATIONAL(
        displayName = "Dinámica y Expresiva",
        description = "Entonación viva y ágil para entrevistas o podcast",
        audioTag = "[cheerfully]"
    ),
    MYSTERIOUS_WHISPER(
        displayName = "Susurro Misterioso",
        description = "Voz velada de cabina nocturna y misterio",
        audioTag = "[whispering]"
    )
}

enum class ProsodyPacing(
    val displayName: String,
    val directorInstruction: String
) {
    PAUSED("Pausada y reflexiva", "Ritmo pausado con silencios expresivos entre frases y articulación cuidada"),
    NATURAL("Orgánica y fluida", "Ritmo humano conversacional natural, respiración suave y entonación realista"),
    DYNAMIC("Ágil y dinámica", "Ritmo enérgico y ágil, con énfasis expresivo en las palabras clave")
}

data class TtsVoiceOption(
    val voiceName: String,
    val displayName: String,
    val localeTag: String,
    val isLocalOffline: Boolean,
    val qualityLabel: String,
    val genderCategory: String = "Universal",
    val emotionTags: List<String> = emptyList(),
    val prosodyDescription: String = ""
)

data class CharacterProfile(
    val id: String,
    val name: String,
    val role: String,
    val ageStyle: String,
    val personality: String,
    val visualDescription: String,
    val voicePitch: Float,
    val voiceRate: Float,
    val timbreFilter: TimbreFilter,
    val preferredVoiceName: String? = null,
    val emotionPreset: VoiceEmotionPreset = VoiceEmotionPreset.NATURAL_WARM,
    val prosodyPacing: ProsodyPacing = ProsodyPacing.NATURAL
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("role", role)
        put("ageStyle", ageStyle)
        put("personality", personality)
        put("visualDescription", visualDescription)
        put("voicePitch", voicePitch.toDouble())
        put("voiceRate", voiceRate.toDouble())
        put("timbreFilter", timbreFilter.name)
        put("preferredVoiceName", preferredVoiceName ?: "")
        put("emotionPreset", emotionPreset.name)
        put("prosodyPacing", prosodyPacing.name)
    }

    companion object {
        fun fromJson(obj: JSONObject): CharacterProfile = CharacterProfile(
            id = obj.optString("id", "char_1"),
            name = obj.optString("name", "Narrador"),
            role = obj.optString("role", "Narrador"),
            ageStyle = obj.optString("ageStyle", "Adulto"),
            personality = obj.optString("personality", ""),
            visualDescription = obj.optString("visualDescription", ""),
            voicePitch = obj.optDouble("voicePitch", 1.0).toFloat(),
            voiceRate = obj.optDouble("voiceRate", 1.0).toFloat(),
            timbreFilter = runCatching {
                TimbreFilter.valueOf(obj.optString("timbreFilter", "WARM_STUDIO"))
            }.getOrDefault(TimbreFilter.WARM_STUDIO),
            preferredVoiceName = obj.optString("preferredVoiceName", "").ifBlank { null },
            emotionPreset = runCatching {
                VoiceEmotionPreset.valueOf(obj.optString("emotionPreset", "NATURAL_WARM"))
            }.getOrDefault(VoiceEmotionPreset.NATURAL_WARM),
            prosodyPacing = runCatching {
                ProsodyPacing.valueOf(obj.optString("prosodyPacing", "NATURAL"))
            }.getOrDefault(ProsodyPacing.NATURAL)
        )

        fun listToJson(list: List<CharacterProfile>): String = JSONArray().apply {
            list.forEach { put(it.toJson()) }
        }.toString()

        fun listFromJson(raw: String?): List<CharacterProfile> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
    }
}

data class DialogueLine(
    val index: Int,
    val actNumber: Int,
    val sceneNumber: Int,
    val speakerName: String,
    val emotion: String,
    val actingNote: String,
    val text: String,
    val pauseAfterMs: Long,
    val sfxCue: String,
    val musicMood: String,
    val synthesizedWavPath: String? = null,
    val actualDurationMs: Long = 0L,
    val startTimeMs: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("index", index)
        put("actNumber", actNumber)
        put("sceneNumber", sceneNumber)
        put("speakerName", speakerName)
        put("emotion", emotion)
        put("actingNote", actingNote)
        put("text", text)
        put("pauseAfterMs", pauseAfterMs)
        put("sfxCue", sfxCue)
        put("musicMood", musicMood)
        put("synthesizedWavPath", synthesizedWavPath ?: "")
        put("actualDurationMs", actualDurationMs)
        put("startTimeMs", startTimeMs)
    }

    companion object {
        fun fromJson(obj: JSONObject): DialogueLine = DialogueLine(
            index = obj.optInt("index", 0),
            actNumber = obj.optInt("actNumber", 1),
            sceneNumber = obj.optInt("sceneNumber", 1),
            speakerName = obj.optString("speakerName", "Narrador"),
            emotion = obj.optString("emotion", "Calma"),
            actingNote = obj.optString("actingNote", ""),
            text = obj.optString("text", ""),
            pauseAfterMs = obj.optLong("pauseAfterMs", 600L),
            sfxCue = obj.optString("sfxCue", "NONE"),
            musicMood = obj.optString("musicMood", "MYSTERY_AMBIENT"),
            synthesizedWavPath = obj.optString("synthesizedWavPath", "").ifBlank { null },
            actualDurationMs = obj.optLong("actualDurationMs", 0L),
            startTimeMs = obj.optLong("startTimeMs", 0L)
        )
    }
}

data class VisualScene(
    val sceneNumber: Int,
    val title: String,
    val settingDescription: String,
    val visualPrompt: String,
    val mood: String,
    val imagePath: String? = null,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 0L,
    val imageErrorMessage: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sceneNumber", sceneNumber)
        put("title", title)
        put("settingDescription", settingDescription)
        put("visualPrompt", visualPrompt)
        put("mood", mood)
        put("imagePath", imagePath ?: "")
        put("startTimeMs", startTimeMs)
        put("durationMs", durationMs)
        put("imageErrorMessage", imageErrorMessage ?: "")
    }

    companion object {
        fun fromJson(obj: JSONObject): VisualScene = VisualScene(
            sceneNumber = obj.optInt("sceneNumber", 1),
            title = obj.optString("title", "Escena"),
            settingDescription = obj.optString("settingDescription", ""),
            visualPrompt = obj.optString("visualPrompt", ""),
            mood = obj.optString("mood", "DARK_CINEMA"),
            imagePath = obj.optString("imagePath", "").ifBlank { null },
            startTimeMs = obj.optLong("startTimeMs", 0L),
            durationMs = obj.optLong("durationMs", 0L),
            imageErrorMessage = obj.optString("imageErrorMessage", "").ifBlank { null }
        )
    }
}

data class BookChapter(
    val chapterNumber: Int,
    val title: String,
    val subtitle: String,
    val bodyParagraphs: List<String>,
    val keyTakeaways: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("chapterNumber", chapterNumber)
        put("title", title)
        put("subtitle", subtitle)
        put("bodyParagraphs", JSONArray(bodyParagraphs))
        put("keyTakeaways", JSONArray(keyTakeaways))
    }

    companion object {
        fun fromJson(obj: JSONObject): BookChapter {
            val pArr = obj.optJSONArray("bodyParagraphs") ?: JSONArray()
            val paragraphs = (0 until pArr.length()).map { pArr.optString(it) }
            val kArr = obj.optJSONArray("keyTakeaways") ?: JSONArray()
            val takeaways = (0 until kArr.length()).map { kArr.optString(it) }
            return BookChapter(
                chapterNumber = obj.optInt("chapterNumber", 1),
                title = obj.optString("title", "Capítulo"),
                subtitle = obj.optString("subtitle", ""),
                bodyParagraphs = paragraphs,
                keyTakeaways = takeaways
            )
        }
    }
}

data class ProductionScript(
    val title: String,
    val logline: String,
    val characterVisualBible: String,
    val totalPlannedActs: Int,
    val completedActs: Int,
    val actOutlines: List<String>,
    val characters: List<CharacterProfile>,
    val lines: List<DialogueLine>,
    val scenes: List<VisualScene>,
    val chapters: List<BookChapter>,
    val endingSummary: String = ""
) {
    val wordCount: Int
        get() = lines.sumOf { it.text.split(Regex("\\s+")).count { w -> w.isNotBlank() } } +
            chapters.sumOf { ch -> ch.bodyParagraphs.sumOf { it.split(Regex("\\s+")).count { w -> w.isNotBlank() } } }

    fun toJson(): String = JSONObject().apply {
        put("title", title)
        put("logline", logline)
        put("characterVisualBible", characterVisualBible)
        put("totalPlannedActs", totalPlannedActs)
        put("completedActs", completedActs)
        put("actOutlines", JSONArray(actOutlines))
        put("characters", JSONArray().apply { characters.forEach { put(it.toJson()) } })
        put("lines", JSONArray().apply { lines.forEach { put(it.toJson()) } })
        put("scenes", JSONArray().apply { scenes.forEach { put(it.toJson()) } })
        put("chapters", JSONArray().apply { chapters.forEach { put(it.toJson()) } })
        put("endingSummary", endingSummary)
    }.toString()

    companion object {
        fun fromJson(raw: String?): ProductionScript? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val obj = JSONObject(raw)
                val outlinesArr = obj.optJSONArray("actOutlines") ?: JSONArray()
                val charsArr = obj.optJSONArray("characters") ?: JSONArray()
                val linesArr = obj.optJSONArray("lines") ?: JSONArray()
                val scenesArr = obj.optJSONArray("scenes") ?: JSONArray()
                val chaptersArr = obj.optJSONArray("chapters") ?: JSONArray()

                ProductionScript(
                    title = obj.optString("title", "Sin título"),
                    logline = obj.optString("logline", ""),
                    characterVisualBible = obj.optString("characterVisualBible", ""),
                    totalPlannedActs = obj.optInt("totalPlannedActs", 1),
                    completedActs = obj.optInt("completedActs", 0),
                    actOutlines = (0 until outlinesArr.length()).map { outlinesArr.optString(it) },
                    characters = (0 until charsArr.length()).map { CharacterProfile.fromJson(charsArr.getJSONObject(it)) },
                    lines = (0 until linesArr.length()).map { DialogueLine.fromJson(linesArr.getJSONObject(it)) },
                    scenes = (0 until scenesArr.length()).map { VisualScene.fromJson(scenesArr.getJSONObject(it)) },
                    chapters = (0 until chaptersArr.length()).map { BookChapter.fromJson(chaptersArr.getJSONObject(it)) },
                    endingSummary = obj.optString("endingSummary", "")
                )
            }.getOrNull()
        }
    }
}

enum class ArtifactFormat(val extension: String, val mimeType: String, val label: String) {
    MP4("mp4", "video/mp4", "Video MP4"),
    WAV("wav", "audio/wav", "Audio WAV"),
    PDF("pdf", "application/pdf", "Documento PDF"),
    HTML("html", "text/html", "Presentación HTML"),
    MD("md", "text/markdown", "Markdown"),
    TXT("txt", "text/plain", "Texto Plano")
}

data class ExportArtifact(
    val format: ArtifactFormat,
    val filePath: String,
    val fileSizeBytes: Long,
    val durationMs: Long = 0L,
    val resolutionLabel: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("format", format.name)
        put("filePath", filePath)
        put("fileSizeBytes", fileSizeBytes)
        put("durationMs", durationMs)
        put("resolutionLabel", resolutionLabel)
    }

    companion object {
        fun fromJson(obj: JSONObject): ExportArtifact = ExportArtifact(
            format = runCatching { ArtifactFormat.valueOf(obj.optString("format", "TXT")) }.getOrDefault(ArtifactFormat.TXT),
            filePath = obj.optString("filePath", ""),
            fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
            durationMs = obj.optLong("durationMs", 0L),
            resolutionLabel = obj.optString("resolutionLabel", "")
        )

        fun listToJson(list: List<ExportArtifact>): String = JSONArray().apply {
            list.forEach { put(it.toJson()) }
        }.toString()

        fun listFromJson(raw: String?): List<ExportArtifact> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
    }
}
