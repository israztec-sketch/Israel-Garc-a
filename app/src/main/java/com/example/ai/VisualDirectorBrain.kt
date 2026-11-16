package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.VisualScene
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream

private const val TAG = "VisualDirectorBrain"

class VisualDirectorBrain {

    private val imageModel by lazy {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )
    }

    /**
     * Generates a real 16:9 image for a scene using Gemini Nano Banana 2.1, maintaining character
     * visual consistency via the characterVisualBible, and saves it as a PNG on disk.
     * Never returns fake Canvas drawings as AI images.
     */
    suspend fun generateSceneImage(
        scene: VisualScene,
        characterVisualBible: String,
        outputFile: File
    ): File {
        val fullPrompt = """
            Cinematic widescreen still frame, moody dramatic lighting, photorealistic film grain,
            no text, no subtitles, no watermarks, no UI overlays.
            Character visual consistency guide: $characterVisualBible
            Scene setting & action: ${scene.visualPrompt}
            Atmosphere: ${scene.mood}
        """.trimIndent()

        var bitmap: Bitmap? = null
        try {
            val generatedBitmap = withTimeout(60_000L) {
                val response = imageModel.generateContent(fullPrompt)
                val imagePart = response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()
                imagePart?.image ?: throw IllegalStateException(
                    "El modelo de imagen no devolvió píxeles para la escena ${scene.sceneNumber}."
                )
            }
            bitmap = generatedBitmap

            withContext(Dispatchers.IO) {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { out ->
                    generatedBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                if (!outputFile.exists() || outputFile.length() == 0L) {
                    throw IllegalStateException("No se pudo guardar el archivo de escena en disco.")
                }
            }
            return outputFile
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Scene ${scene.sceneNumber} image generation failed", e)
            throw e
        } finally {
            runCatching {
                if (bitmap != null && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            bitmap = null
        }
    }
}
