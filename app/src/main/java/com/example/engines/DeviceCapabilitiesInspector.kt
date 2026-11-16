package com.example.engines

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class DeviceCapabilities(
    val androidVersionLabel: String,
    val apiLevel: Int,
    val isAndroid14Compatible: Boolean,
    val avcEncoderName: String,
    val supports1080p: Boolean,
    val supports4k: Boolean,
    val ttsEngineAvailable: Boolean,
    val spanishVoicesCount: Int,
    val totalVoicesCount: Int
)

object DeviceCapabilitiesInspector {

    suspend fun inspect(context: Context): DeviceCapabilities = withContext(Dispatchers.Default) {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        var encoderName = "No detectado"
        var supports1080p = false
        var supports4k = false

        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            if (!info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }) {
                continue
            }
            val caps = runCatching { info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC) }.getOrNull()
            val videoCaps = caps?.videoCapabilities ?: continue

            val fhd = videoCaps.isSizeSupported(1920, 1080)
            val uhd = videoCaps.isSizeSupported(3840, 2160)
            if (fhd || uhd) {
                if (encoderName == "No detectado" || !info.name.contains("google", ignoreCase = true)) {
                    encoderName = info.name
                }
                if (fhd) supports1080p = true
                if (uhd) supports4k = true
            }
        }

        val (ttsReady, esVoices, totalVoices) = inspectTtsVoices(context)

        DeviceCapabilities(
            androidVersionLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            apiLevel = Build.VERSION.SDK_INT,
            isAndroid14Compatible = Build.VERSION.SDK_INT >= 34 || Build.VERSION.SDK_INT >= 24,
            avcEncoderName = encoderName,
            supports1080p = supports1080p,
            supports4k = supports4k,
            ttsEngineAvailable = ttsReady,
            spanishVoicesCount = esVoices,
            totalVoicesCount = totalVoices
        )
    }

    private suspend fun inspectTtsVoices(context: Context): Triple<Boolean, Int, Int> {
        val deferred = CompletableDeferred<Triple<Boolean, Int, Int>>()
        var ttsRef: TextToSpeech? = null
        withContext(Dispatchers.Main) {
            ttsRef = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val voices = ttsRef?.voices.orEmpty()
                    val spanish = voices.count { v ->
                        v.locale.language.equals("es", ignoreCase = true)
                    }
                    deferred.complete(Triple(true, spanish, voices.size))
                } else {
                    deferred.complete(Triple(false, 0, 0))
                }
            }
        }
        val result = withTimeoutOrNull(4_000L) { deferred.await() } ?: Triple(false, 0, 0)
        withContext(Dispatchers.Main) {
            runCatching { ttsRef?.shutdown() }
        }
        return result
    }
}
