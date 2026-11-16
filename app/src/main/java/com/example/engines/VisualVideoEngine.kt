package com.example.engines

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.Surface
import com.example.domain.model.DialogueLine
import com.example.domain.model.ProductionScript
import com.example.domain.model.VideoResolution
import com.example.domain.model.VisualScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.min

private const val TAG = "VisualVideoEngine"

data class VideoRenderResult(
    val mp4File: File,
    val actualWidth: Int,
    val actualHeight: Int,
    val actualDurationMs: Long,
    val fileSizeBytes: Long
)

class VisualVideoEngine {

    suspend fun renderMp4(
        script: ProductionScript,
        timedLines: List<DialogueLine>,
        scenes: List<VisualScene>,
        masterWavFile: File,
        envelope100ms: FloatArray,
        totalDurationMs: Long,
        requestedResolution: VideoResolution,
        capabilities: DeviceCapabilities,
        outputMp4File: File,
        customVideoPath: String? = null,
        onProgress: suspend (percent: Int, detail: String) -> Unit
    ): VideoRenderResult = withContext(Dispatchers.Default) {
        outputMp4File.parentFile?.mkdirs()
        if (outputMp4File.exists()) outputMp4File.delete()

        val (width, height) = resolveSafeResolution(requestedResolution, capabilities)
        // Use 12 fps for 40+ min videopodcasts so rendering completes smoothly on mobile devices, 24 fps for shorter episodes
        val fps = if (totalDurationMs >= 20 * 60_000L) 12 else 24
        val bitrate = when {
            width >= 3840 -> 16_000_000
            width >= 1920 -> 7_500_000
            else -> 4_500_000
        }

        val timedScenes = assignSceneTimings(scenes, timedLines, totalDurationMs)

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            MusicSfxEngine.TARGET_SAMPLE_RATE,
            MusicSfxEngine.TARGET_CHANNELS
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var muxerStopped = false
        var inputSurface: Surface? = null
        var eglRenderer: EglFrameRenderer? = null

        var hudBitmap: Bitmap? = null
        var currentSceneBitmap: Bitmap? = null
        var wavStream: FileInputStream? = null
        var customOesDecoder: OesVideoFrameDecoder? = null
        var customRetriever: MediaMetadataRetriever? = null
        val pendingVideoBuffers = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
        val pendingAudioBuffers = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()

        try {
            if (!masterWavFile.exists() || masterWavFile.length() <= 44L) {
                throw IllegalStateException("El archivo WAV maestro no es válido o está vacío.")
            }

            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoEncoder.createInputSurface()
            videoEncoder.start()

            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            eglRenderer = EglFrameRenderer(inputSurface, width, height)

            val customVideoFile = customVideoPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
            var customVideoDurationMs = 0L
            var customVideoAspect = 16f / 9f
            var customVideoRotation = 0
            if (customVideoFile != null) {
                val metaRetriever = MediaMetadataRetriever()
                try {
                    metaRetriever.setDataSource(customVideoFile.absolutePath)
                    val vw = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
                    val vh = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
                    customVideoDurationMs = (metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L).coerceAtLeast(1000L)
                    customVideoRotation = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val orientedW = if (customVideoRotation == 90 || customVideoRotation == 270) vh else vw
                    val orientedH = if (customVideoRotation == 90 || customVideoRotation == 270) vw else vh
                    customVideoAspect = (orientedW.toFloat() / orientedH.coerceAtLeast(1).toFloat()).coerceAtLeast(0.2f)
                } finally {
                    runCatching { metaRetriever.release() }
                }

                customOesDecoder = runCatching {
                    val oesSurface = eglRenderer.createOesInputSurface()
                    OesVideoFrameDecoder(customVideoFile.absolutePath, oesSurface, customVideoDurationMs * 1000L)
                }.onFailure { err ->
                    Log.w(TAG, "Hardware OES decoder fallback to MediaMetadataRetriever sampler", err)
                }.getOrNull()

                if (customOesDecoder == null) {
                    customRetriever = runCatching {
                        MediaMetadataRetriever().apply { setDataSource(customVideoFile.absolutePath) }
                    }.getOrNull()
                }
            }

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoOutputFormat: MediaFormat? = null
            var audioOutputFormat: MediaFormat? = null
            var videoSamplesWritten = 0
            var audioSamplesWritten = 0
            var lastVideoPtsUs = -1L
            var lastAudioPtsUs = -1L

            val totalFrames = ((totalDurationMs * fps) / 1000L).toInt().coerceAtLeast(fps)
            val frameDurationNs = 1_000_000_000L / fps

            // Overlay Canvas bitmap rendered at 1280x720 for crisp fast HUD + subtitles, GL-scaled cleanly
            val hudWidth = 1280
            val hudHeight = 720
            val createdHudBitmap = Bitmap.createBitmap(hudWidth, hudHeight, Bitmap.Config.ARGB_8888)
            hudBitmap = createdHudBitmap
            val hudCanvas = Canvas(createdHudBitmap)
            val overlayPainter = CinemaOverlayPainter(hudWidth, hudHeight, script.title)

            var currentSceneIdx = -1
            var lastRetrieverBucket = -1L

            val wavPrefix = ByteArray(minOf(4096L, masterWavFile.length()).toInt())
            FileInputStream(masterWavFile).use { prefixIn ->
                var totalRead = 0
                while (totalRead < wavPrefix.size) {
                    val r = prefixIn.read(wavPrefix, totalRead, wavPrefix.size - totalRead)
                    if (r <= 0) break
                    totalRead += r
                }
            }
            val parsedWav = MusicSfxEngine.parseWavHeader(wavPrefix)
            val wavDataOffset = (parsedWav?.dataOffset ?: 44).toLong()

            val openedWavStream = FileInputStream(masterWavFile)
            wavStream = openedWavStream
            var headerSkipped = 0L
            while (headerSkipped < wavDataOffset) {
                val s = openedWavStream.skip(wavDataOffset - headerSkipped)
                if (s <= 0L) break
                headerSkipped += s
            }
            val pcmChunk = ByteArray(2048) // 1024 samples @ 16-bit mono
            var totalSamplesRead = 0L
            var audioInputEos = false
            var audioOutputEos = false
            var videoOutputEos = false

            var frameIndex = 0
            var lineSearchIdx = 0

            fun writeSampleMonotonic(
                activeMuxer: MediaMuxer,
                isVideo: Boolean,
                trackIndex: Int,
                data: ByteBuffer,
                info: MediaCodec.BufferInfo
            ) {
                val rawPts = info.presentationTimeUs.coerceAtLeast(0L)
                val safePts = if (isVideo) {
                    val s = if (lastVideoPtsUs >= 0L && rawPts <= lastVideoPtsUs) lastVideoPtsUs + 1L else rawPts
                    lastVideoPtsUs = s
                    videoSamplesWritten++
                    s
                } else {
                    val s = if (lastAudioPtsUs >= 0L && rawPts <= lastAudioPtsUs) lastAudioPtsUs + 1L else rawPts
                    lastAudioPtsUs = s
                    audioSamplesWritten++
                    s
                }
                val fixedInfo = MediaCodec.BufferInfo().apply {
                    set(info.offset, info.size, safePts, info.flags)
                }
                activeMuxer.writeSampleData(trackIndex, data, fixedInfo)
            }

            fun tryStartMuxer() {
                val vFmt = videoOutputFormat ?: return
                val aFmt = audioOutputFormat ?: return
                if (muxerStarted) return

                // Create MediaMuxer only once BOTH encoder formats are guaranteed ready
                val createdMuxer = MediaMuxer(outputMp4File.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = createdMuxer
                videoTrackIndex = createdMuxer.addTrack(vFmt)
                audioTrackIndex = createdMuxer.addTrack(aFmt)
                createdMuxer.start()
                muxerStarted = true

                for ((bytes, info) in pendingVideoBuffers) {
                    val buf = ByteBuffer.wrap(bytes)
                    writeSampleMonotonic(createdMuxer, isVideo = true, trackIndex = videoTrackIndex, data = buf, info = info)
                }
                pendingVideoBuffers.clear()
                for ((bytes, info) in pendingAudioBuffers) {
                    val buf = ByteBuffer.wrap(bytes)
                    writeSampleMonotonic(createdMuxer, isVideo = false, trackIndex = audioTrackIndex, data = buf, info = info)
                }
                pendingAudioBuffers.clear()
            }

            suspend fun drainVideo(endOfStream: Boolean) {
                if (endOfStream) {
                    videoEncoder.signalEndOfInputStream()
                }
                var eosIdleRetries = 0
                while (!videoOutputEos) {
                    coroutineContext.ensureActive()
                    val outIdx = videoEncoder.dequeueOutputBuffer(videoBufferInfo, if (endOfStream) 10_000L else 0L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                            eosIdleRetries++
                            if (eosIdleRetries >= 500) {
                                Log.w(TAG, "Video encoder EOS drain reached safety limit; finalizing MP4")
                                videoOutputEos = true
                                break
                            }
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            eosIdleRetries = 0
                            if (videoOutputFormat == null) {
                                videoOutputFormat = videoEncoder.outputFormat
                                tryStartMuxer()
                            }
                        }
                        outIdx >= 0 -> {
                            eosIdleRetries = 0
                            val encodedBuf = videoEncoder.getOutputBuffer(outIdx)
                            if (encodedBuf != null && videoBufferInfo.size > 0 &&
                                (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                encodedBuf.position(videoBufferInfo.offset)
                                encodedBuf.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                val activeMuxer = muxer
                                if (muxerStarted && activeMuxer != null) {
                                    writeSampleMonotonic(
                                        activeMuxer = activeMuxer,
                                        isVideo = true,
                                        trackIndex = videoTrackIndex,
                                        data = encodedBuf,
                                        info = videoBufferInfo
                                    )
                                } else {
                                    val copy = ByteArray(videoBufferInfo.size)
                                    encodedBuf.get(copy)
                                    val copyInfo = MediaCodec.BufferInfo().apply {
                                        set(0, videoBufferInfo.size, videoBufferInfo.presentationTimeUs, videoBufferInfo.flags)
                                    }
                                    pendingVideoBuffers.add(copy to copyInfo)
                                }
                            }
                            val isEos = (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            videoEncoder.releaseOutputBuffer(outIdx, false)
                            if (isEos) {
                                videoOutputEos = true
                                break
                            }
                        }
                    }
                }
            }

            suspend fun feedAndDrainAudio(targetTimeUs: Long, flushAll: Boolean) {
                while (!audioInputEos) {
                    coroutineContext.ensureActive()
                    val currentAudioPtsUs = (totalSamplesRead * 1_000_000L) / MusicSfxEngine.TARGET_SAMPLE_RATE
                    if (!flushAll && currentAudioPtsUs > targetTimeUs + 50_000L) break

                    val inIdx = audioEncoder.dequeueInputBuffer(5_000L)
                    if (inIdx < 0) break

                    val inputBuf = audioEncoder.getInputBuffer(inIdx) ?: break
                    inputBuf.clear()
                    val maxRead = minOf(pcmChunk.size, inputBuf.remaining()).let { if (it % 2 != 0) it - 1 else it }
                    val read = if (maxRead > 0) openedWavStream.read(pcmChunk, 0, maxRead) else 0
                    if (read <= 0) {
                        audioEncoder.queueInputBuffer(inIdx, 0, 0, currentAudioPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        audioInputEos = true
                    } else {
                        inputBuf.put(pcmChunk, 0, read)
                        val samples = read / 2
                        audioEncoder.queueInputBuffer(inIdx, 0, read, currentAudioPtsUs, 0)
                        totalSamplesRead += samples
                    }
                }

                while (!audioOutputEos) {
                    coroutineContext.ensureActive()
                    val outIdx = audioEncoder.dequeueOutputBuffer(audioBufferInfo, if (flushAll) 5_000L else 0L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (audioOutputFormat == null) {
                                audioOutputFormat = audioEncoder.outputFormat
                                tryStartMuxer()
                            }
                        }
                        outIdx >= 0 -> {
                            val encodedBuf = audioEncoder.getOutputBuffer(outIdx)
                            if (encodedBuf != null && audioBufferInfo.size > 0 &&
                                (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                encodedBuf.position(audioBufferInfo.offset)
                                encodedBuf.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                val activeMuxer = muxer
                                if (muxerStarted && activeMuxer != null) {
                                    writeSampleMonotonic(
                                        activeMuxer = activeMuxer,
                                        isVideo = false,
                                        trackIndex = audioTrackIndex,
                                        data = encodedBuf,
                                        info = audioBufferInfo
                                    )
                                } else {
                                    val copy = ByteArray(audioBufferInfo.size)
                                    encodedBuf.get(copy)
                                    val copyInfo = MediaCodec.BufferInfo().apply {
                                        set(0, audioBufferInfo.size, audioBufferInfo.presentationTimeUs, audioBufferInfo.flags)
                                    }
                                    pendingAudioBuffers.add(copy to copyInfo)
                                }
                            }
                            val isEos = (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            audioEncoder.releaseOutputBuffer(outIdx, false)
                            if (isEos) {
                                audioOutputEos = true
                                break
                            }
                        }
                    }
                }
            }

            while (frameIndex < totalFrames) {
                coroutineContext.ensureActive()

                val ptsNs = frameIndex * frameDurationNs
                val timeMs = (frameIndex * 1000L) / fps

                // Locate active scene
                val sceneIdx = timedScenes.indexOfLast { timeMs >= it.startTimeMs }.coerceAtLeast(0)
                var hasOesFrame = false
                if (customOesDecoder != null && customVideoDurationMs > 0L) {
                    val targetVideoUs = ((timeMs % customVideoDurationMs) * 1000L)
                    if (customOesDecoder.advanceTo(targetVideoUs)) {
                        eglRenderer.updateOesTexture()
                        hasOesFrame = true
                    } else if (customOesDecoder.hasRenderedAtLeastOnce) {
                        hasOesFrame = true
                    }
                } else if (customRetriever != null && customVideoDurationMs > 0L) {
                    val loopedMs = timeMs % customVideoDurationMs
                    val bucket = loopedMs / 350L
                    if (bucket != lastRetrieverBucket) {
                        lastRetrieverBucket = bucket
                        val sampled = runCatching {
                            customRetriever.getFrameAtTime(
                                loopedMs * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                            )
                        }.getOrNull()
                        if (sampled != null) {
                            currentSceneBitmap?.recycle()
                            currentSceneBitmap = sampled
                            eglRenderer.uploadSceneTexture(sampled)
                        }
                    }
                } else if (sceneIdx != currentSceneIdx) {
                    currentSceneIdx = sceneIdx
                    currentSceneBitmap?.recycle()
                    val scene = timedScenes.getOrNull(sceneIdx)
                    currentSceneBitmap = loadSceneBitmapOrNull(scene, width.coerceAtMost(1920), height.coerceAtMost(1080))
                    currentSceneBitmap?.let { eglRenderer.uploadSceneTexture(it) }
                }

                // Locate active dialogue line
                while (lineSearchIdx < timedLines.size - 1 &&
                    timeMs > timedLines[lineSearchIdx].startTimeMs + timedLines[lineSearchIdx].actualDurationMs + 300L
                ) {
                    lineSearchIdx++
                }
                val activeLine = timedLines.getOrNull(lineSearchIdx)?.takeIf {
                    timeMs in it.startTimeMs..(it.startTimeMs + it.actualDurationMs)
                }

                val hasVisualBg = hasOesFrame || currentSceneBitmap != null

                // Draw overlay HUD (subtitles, speaker badge, waveform, scene title if no image/video)
                val envIdx = ((timeMs / 100L).toInt()).coerceIn(0, (envelope100ms.size - 1).coerceAtLeast(0))
                val amp = if (envelope100ms.isNotEmpty()) envelope100ms[envIdx] else 0.1f
                overlayPainter.drawHud(
                    canvas = hudCanvas,
                    scene = timedScenes.getOrNull(sceneIdx),
                    hasSceneImage = hasVisualBg,
                    activeLine = activeLine,
                    timeMs = timeMs,
                    totalDurationMs = totalDurationMs,
                    amplitude = amp
                )

                // Ken Burns zoom + subtle pan across scene duration (kept subtle on custom video)
                val sceneObj = timedScenes.getOrNull(sceneIdx)
                val sceneProg = if (sceneObj != null && sceneObj.durationMs > 0L) {
                    ((timeMs - sceneObj.startTimeMs).toFloat() / sceneObj.durationMs).coerceIn(0f, 1f)
                } else 0f

                eglRenderer.drawFrame(
                    hasSceneTexture = currentSceneBitmap != null && !hasOesFrame,
                    hasOesTexture = hasOesFrame,
                    oesSourceAspect = customVideoAspect,
                    hudBitmap = createdHudBitmap,
                    kenBurnsProgress = if (hasOesFrame) 0f else sceneProg,
                    ptsNs = ptsNs
                )

                drainVideo(endOfStream = false)
                feedAndDrainAudio(targetTimeUs = ptsNs / 1000L, flushAll = false)

                frameIndex++
                if (frameIndex % (fps * 2) == 0 || frameIndex == totalFrames) {
                    val pct = ((frameIndex * 100) / totalFrames).coerceIn(1, 99)
                    onProgress(pct, "Renderizando MP4 ${width}×${height} · cuadro $frameIndex / $totalFrames")
                }
            }

            // Finish EOS on both tracks
            drainVideo(endOfStream = true)
            var audioFlushGuard = 0
            while (!audioOutputEos && audioFlushGuard < 500) {
                feedAndDrainAudio(targetTimeUs = Long.MAX_VALUE, flushAll = true)
                audioFlushGuard++
            }

            openedWavStream.close()
            wavStream = null
            currentSceneBitmap?.recycle()
            currentSceneBitmap = null
            createdHudBitmap.recycle()
            hudBitmap = null

            val activeMuxer = muxer
            if (!muxerStarted || activeMuxer == null || videoSamplesWritten <= 0 || audioSamplesWritten <= 0) {
                throw IllegalStateException(
                    "No se pudo completar el contenedor MP4 (videoSamples=$videoSamplesWritten, audioSamples=$audioSamplesWritten, muxerStarted=$muxerStarted)."
                )
            }
            muxerStopped = true
            activeMuxer.stop()
            activeMuxer.release()
            muxer = null

            if (!outputMp4File.exists() || outputMp4File.length() == 0L) {
                throw IllegalStateException("El archivo MP4 final no se generó correctamente en disco.")
            }

            val retriever = MediaMetadataRetriever()
            val (verifiedW, verifiedH, verifiedDuration) = try {
                retriever.setDataSource(outputMp4File.absolutePath)
                val vw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: width
                val vh = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: height
                val vd = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: totalDurationMs
                Triple(vw, vh, vd)
            } finally {
                runCatching { retriever.release() }
            }

            onProgress(100, "Video MP4 verificado (${verifiedW}×${verifiedH})")

            VideoRenderResult(
                mp4File = outputMp4File,
                actualWidth = verifiedW,
                actualHeight = verifiedH,
                actualDurationMs = verifiedDuration,
                fileSizeBytes = outputMp4File.length()
            )
        } finally {
            pendingVideoBuffers.clear()
            pendingAudioBuffers.clear()
            runCatching { customOesDecoder?.release() }
            runCatching { customRetriever?.release() }
            runCatching { wavStream?.close() }
            runCatching {
                if (currentSceneBitmap != null && currentSceneBitmap?.isRecycled == false) {
                    currentSceneBitmap?.recycle()
                }
            }
            currentSceneBitmap = null
            runCatching {
                if (hudBitmap != null && hudBitmap?.isRecycled == false) {
                    hudBitmap?.recycle()
                }
            }
            hudBitmap = null
            runCatching { eglRenderer?.release() }
            runCatching { inputSurface?.release() }
            runCatching { videoEncoder.stop() }
            runCatching { videoEncoder.release() }
            runCatching { audioEncoder.stop() }
            runCatching { audioEncoder.release() }
            muxer?.let { m ->
                if (muxerStarted && !muxerStopped) {
                    muxerStopped = true
                    runCatching { m.stop() }
                }
                runCatching { m.release() }
            }
            muxer = null
            if (!muxerStopped) {
                runCatching { if (outputMp4File.exists()) outputMp4File.delete() }
            }
        }
    }

    private fun resolveSafeResolution(
        requested: VideoResolution,
        caps: DeviceCapabilities
    ): Pair<Int, Int> {
        return when (requested) {
            VideoResolution.UHD_4K -> {
                if (caps.supports4k) 3840 to 2160
                else if (caps.supports1080p) 1920 to 1080
                else 1280 to 720
            }
            VideoResolution.FULL_HD_1080P -> {
                if (caps.supports1080p) 1920 to 1080 else 1280 to 720
            }
        }
    }

    private fun assignSceneTimings(
        scenes: List<VisualScene>,
        timedLines: List<DialogueLine>,
        totalDurationMs: Long
    ): List<VisualScene> {
        if (scenes.isEmpty()) {
            return listOf(
                VisualScene(
                    sceneNumber = 1,
                    title = "Escena Principal",
                    settingDescription = "",
                    visualPrompt = "",
                    mood = "MYSTERY_AMBIENT",
                    startTimeMs = 0L,
                    durationMs = totalDurationMs
                )
            )
        }
        val lineGroups = timedLines.groupBy { it.sceneNumber }
        val sorted = scenes.sortedBy { it.sceneNumber }
        var cursor = 0L
        return sorted.mapIndexed { idx, scene ->
            val linesInScene = lineGroups[scene.sceneNumber]
            val start = if (idx == 0) 0L else linesInScene?.minOfOrNull { it.startTimeMs } ?: cursor
            val nextStart = if (idx == sorted.lastIndex) {
                totalDurationMs
            } else {
                val nextSceneNum = sorted[idx + 1].sceneNumber
                lineGroups[nextSceneNum]?.minOfOrNull { it.startTimeMs }
                    ?: (start + (totalDurationMs / sorted.size)).coerceAtMost(totalDurationMs)
            }
            val dur = (nextStart - start).coerceAtLeast(1000L)
            cursor = start + dur
            scene.copy(startTimeMs = start, durationMs = dur)
        }
    }

    private fun loadSceneBitmapOrNull(scene: VisualScene?, maxW: Int, maxH: Int): Bitmap? {
        val path = scene?.imagePath ?: return null
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while ((opts.outWidth / sample) > maxW || (opts.outHeight / sample) > maxH) {
                sample *= 2
            }
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}

private class CinemaOverlayPainter(
    private val w: Int,
    private val h: Int,
    private val projectTitle: String
) {
    private val bgFallbackPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.parseColor("#090B12"), Color.parseColor("#18131E"), Color.parseColor("#090B10")),
            null,
            Shader.TileMode.CLAMP
        )
    }
    private val vignettePaint = Paint().apply {
        shader = LinearGradient(
            0f, h * 0.45f, 0f, h.toFloat(),
            Color.TRANSPARENT, Color.argb(225, 9, 11, 16),
            Shader.TileMode.CLAMP
        )
    }
    private val topBarPaint = Paint().apply {
        color = Color.argb(170, 10, 12, 18)
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8A838")
        textSize = 24f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A7AFBF")
        textSize = 20f
        typeface = Typeface.MONOSPACE
    }
    private val sceneHeadlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F3EFEA")
        textSize = 44f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val sceneDescPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B8C0D0")
        textSize = 24f
        typeface = Typeface.SANS_SERIF
    }
    private val speakerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 232, 168, 56)
    }
    private val speakerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#141008")
        textSize = 22f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val subBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 10, 12, 18)
    }
    private val subTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F7F5F0")
        textSize = 31f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8A838")
    }

    private var cachedSceneKey: Int = -1
    private var cachedSceneLayout: StaticLayout? = null
    private var cachedSubtitleKey: String? = null
    private var cachedSubtitleLayout: StaticLayout? = null
    private var cachedSubtitleBadgeW: Float = 0f

    fun drawHud(
        canvas: Canvas,
        scene: VisualScene?,
        hasSceneImage: Boolean,
        activeLine: DialogueLine?,
        timeMs: Long,
        totalDurationMs: Long,
        amplitude: Float
    ) {
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        if (!hasSceneImage) {
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgFallbackPaint)
            scene?.let {
                canvas.drawText("ESCENA ${it.sceneNumber} · ${it.title.uppercase()}", w / 2f, h * 0.36f, sceneHeadlinePaint)
                val layout = if (cachedSceneKey == it.sceneNumber && cachedSceneLayout != null) {
                    cachedSceneLayout!!
                } else {
                    StaticLayout.Builder.obtain(
                        it.settingDescription,
                        0,
                        it.settingDescription.length,
                        sceneDescPaint,
                        (w * 0.72f).toInt()
                    ).setAlignment(Layout.Alignment.ALIGN_CENTER).build().also { built ->
                        cachedSceneKey = it.sceneNumber
                        cachedSceneLayout = built
                    }
                }
                canvas.save()
                canvas.translate(w * 0.14f, h * 0.42f)
                layout.draw(canvas)
                canvas.restore()
            }
        } else {
            canvas.drawRect(0f, h * 0.45f, w.toFloat(), h.toFloat(), vignettePaint)
        }

        // Top studio HUD
        canvas.drawRect(0f, 0f, w.toFloat(), 64f, topBarPaint)
        canvas.drawText("MOHANA AI · ${projectTitle.take(38)}", 36f, 40f, titlePaint)
        val timeLabel = "${formatMs(timeMs)} / ${formatMs(totalDurationMs)}"
        val timeW = metaPaint.measureText(timeLabel)
        canvas.drawText(timeLabel, w - timeW - 36f, 40f, metaPaint)

        // Audio-reactive waveform indicator (bottom-right)
        val barCount = 16
        val baseX = w - 260f
        val baseY = h - 52f
        for (b in 0 until barCount) {
            val phase = kotlin.math.sin((timeMs * 0.012) + b * 0.7).toFloat() * 0.35f + 0.65f
            val bh = (8f + amplitude * 68f * phase).coerceIn(6f, 64f)
            val rx = baseX + b * 13f
            canvas.drawRoundRect(rx, baseY - bh, rx + 7f, baseY, 4f, 4f, wavePaint)
        }

        // Active speaker badge & synchronized subtitle (cached across identical frames)
        if (activeLine != null && activeLine.text.isNotBlank()) {
            val badgeText = "${activeLine.speakerName.uppercase()} · ${activeLine.emotion}"
            val subKey = "${activeLine.index}:${activeLine.text}"
            val subLayout = if (cachedSubtitleKey == subKey && cachedSubtitleLayout != null) {
                cachedSubtitleLayout!!
            } else {
                cachedSubtitleBadgeW = speakerTextPaint.measureText(badgeText) + 34f
                StaticLayout.Builder.obtain(
                    activeLine.text,
                    0,
                    activeLine.text.length,
                    subTextPaint,
                    w - 220
                ).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(6f, 1f).build().also { built ->
                    cachedSubtitleKey = subKey
                    cachedSubtitleLayout = built
                }
            }
            val badgeW = cachedSubtitleBadgeW

            val boxHeight = subLayout.height + 36f
            val boxTop = h - boxHeight - 36f
            val boxRect = RectF(90f, boxTop, w - 280f, h - 32f)
            canvas.drawRoundRect(boxRect, 16f, 16f, subBoxPaint)

            val badgeRect = RectF(90f, boxTop - 38f, 90f + badgeW, boxTop - 6f)
            canvas.drawRoundRect(badgeRect, 10f, 10f, speakerBgPaint)
            canvas.drawText(badgeText, 107f, boxTop - 15f, speakerTextPaint)

            canvas.save()
            canvas.translate(110f, boxTop + 18f)
            subLayout.draw(canvas)
            canvas.restore()
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return "%02d:%02d".format(min, sec)
    }
}

private class OesVideoFrameDecoder(
    videoPath: String,
    private val targetSurface: Surface,
    private val durationUsFallback: Long
) {
    private val extractor = MediaExtractor()
    private val decoder: MediaCodec
    private var videoTrackIndex = -1
    private var durationUs = durationUsFallback.coerceAtLeast(1_000_000L)
    private var lastDecodedPtsUs = -1L
    private var inputEos = false
    private val bufferInfo = MediaCodec.BufferInfo()
    var hasRenderedAtLeastOnce = false
        private set

    init {
        extractor.setDataSource(videoPath)
        var trackFormat: MediaFormat? = null
        var mime: String? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val m = fmt.getString(MediaFormat.KEY_MIME)
            if (m != null && m.startsWith("video/")) {
                videoTrackIndex = i
                trackFormat = fmt
                mime = m
                if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = fmt.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1_000_000L)
                }
                break
            }
        }
        val selectedMime = mime ?: throw IllegalStateException("El vídeo propio no contiene pista de vídeo.")
        val selectedFmt = trackFormat ?: throw IllegalStateException("Formato de vídeo no reconocido.")
        extractor.selectTrack(videoTrackIndex)

        decoder = MediaCodec.createDecoderByType(selectedMime)
        decoder.configure(selectedFmt, targetSurface, null, 0)
        decoder.start()
    }

    fun advanceTo(rawTargetUs: Long): Boolean {
        val targetUs = if (durationUs > 0L) rawTargetUs % durationUs else rawTargetUs
        if (lastDecodedPtsUs > targetUs + 250_000L) {
            // Looped back to start of custom video clip
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            decoder.flush()
            inputEos = false
            lastDecodedPtsUs = -1L
        }

        if (lastDecodedPtsUs >= targetUs && hasRenderedAtLeastOnce) {
            return true
        }

        var renderedNew = false
        var guard = 0
        while (guard < 48) {
            guard++
            if (!inputEos) {
                val inIdx = decoder.dequeueInputBuffer(2_000L)
                if (inIdx >= 0) {
                    val inBuf = decoder.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            val sampleTime = extractor.sampleTime.coerceAtLeast(0L)
                            decoder.queueInputBuffer(inIdx, 0, size, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outIdx = decoder.dequeueOutputBuffer(bufferInfo, 4_000L)
            if (outIdx >= 0) {
                val pts = bufferInfo.presentationTimeUs
                val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                val shouldRender = (pts >= targetUs - 20_000L) || !hasRenderedAtLeastOnce || isEos
                decoder.releaseOutputBuffer(outIdx, shouldRender)
                if (shouldRender) {
                    lastDecodedPtsUs = pts
                    hasRenderedAtLeastOnce = true
                    renderedNew = true
                    if (pts >= targetUs - 20_000L) break
                }
                if (isEos) {
                    extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    decoder.flush()
                    inputEos = false
                    lastDecodedPtsUs = -1L
                    break
                }
            } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputEos) {
                break
            }
        }
        return renderedNew || hasRenderedAtLeastOnce
    }

    fun release() {
        runCatching { decoder.stop() }
        runCatching { decoder.release() }
        runCatching { extractor.release() }
    }
}

private class EglFrameRenderer(
    surface: Surface,
    private val width: Int,
    private val height: Int
) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var oesProgram = 0
    private var sceneTexId = 0
    private var hudTexId = 0
    private var oesTexId = 0

    private var surfaceTexture: SurfaceTexture? = null
    private var oesSurface: Surface? = null
    private var frameThread: HandlerThread? = null
    private val frameSyncObj = Object()
    @Volatile
    private var frameAvailable = false
    private val stMatrix = FloatArray(16)

    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer
    private val oesTexCoordBuffer: FloatBuffer
    private val kbCoordsScratch = FloatArray(8)
    private val oesCoordsScratch = FloatArray(8)
    private val fullCoords = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
    private val aPosHandle: Int
    private val aTexHandle: Int
    private val uTexHandle: Int

    private val oesPosHandle: Int
    private val oesTexHandle: Int
    private val oesSamplerHandle: Int
    private val oesMatrixHandle: Int

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1, // EGL_RECORDABLE_ANDROID
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        if (numConfigs[0] <= 0 || configs[0] == null) {
            val fallbackAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            EGL14.eglChooseConfig(eglDisplay, fallbackAttribs, 0, configs, 0, 1, numConfigs, 0)
        }
        val selectedConfig = configs[0]
            ?: throw IllegalStateException("No se encontró configuración EGL válida para renderizado de video.")
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, selectedConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, selectedConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT || eglSurface == EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("No se pudo inicializar contexto/superficie EGL para MediaCodec.")
        }
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        val vs = """
            attribute vec4 aPos;
            attribute vec2 aTex;
            varying vec2 vTex;
            void main() {
                gl_Position = aPos;
                vTex = aTex;
            }
        """.trimIndent()
        val fs = """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D uTex;
            void main() {
                gl_FragColor = texture2D(uTex, vTex);
            }
        """.trimIndent()

        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vShader)
        GLES20.glAttachShader(program, fShader)
        GLES20.glLinkProgram(program)

        aPosHandle = GLES20.glGetAttribLocation(program, "aPos")
        aTexHandle = GLES20.glGetAttribLocation(program, "aTex")
        uTexHandle = GLES20.glGetUniformLocation(program, "uTex")

        val oesVs = """
            attribute vec4 aPos;
            attribute vec2 aTex;
            uniform mat4 uStMatrix;
            varying vec2 vTex;
            void main() {
                gl_Position = aPos;
                vTex = (uStMatrix * vec4(aTex, 0.0, 1.0)).xy;
            }
        """.trimIndent()
        val oesFs = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES sTex;
            void main() {
                gl_FragColor = texture2D(sTex, vTex);
            }
        """.trimIndent()
        val oesVShader = compileShader(GLES20.GL_VERTEX_SHADER, oesVs)
        val oesFShader = compileShader(GLES20.GL_FRAGMENT_SHADER, oesFs)
        oesProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(oesProgram, oesVShader)
        GLES20.glAttachShader(oesProgram, oesFShader)
        GLES20.glLinkProgram(oesProgram)

        oesPosHandle = GLES20.glGetAttribLocation(oesProgram, "aPos")
        oesTexHandle = GLES20.glGetAttribLocation(oesProgram, "aTex")
        oesSamplerHandle = GLES20.glGetUniformLocation(oesProgram, "sTex")
        oesMatrixHandle = GLES20.glGetUniformLocation(oesProgram, "uStMatrix")

        val texIds = IntArray(3)
        GLES20.glGenTextures(3, texIds, 0)
        sceneTexId = texIds[0]
        hudTexId = texIds[1]
        oesTexId = texIds[2]
        for (id in listOf(sceneTexId, hudTexId)) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        android.opengl.Matrix.setIdentityM(stMatrix, 0)

        val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        vertexBuffer = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(verts); position(0)
        }
        texCoordBuffer = ByteBuffer.allocateDirect(fullCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(fullCoords); position(0)
        }
        val glUv = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        oesTexCoordBuffer = ByteBuffer.allocateDirect(glUv.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(glUv); position(0)
        }
    }

    fun createOesInputSurface(): Surface {
        oesSurface?.let { return it }
        val thread = HandlerThread("MohanaOesFrameThread").apply { start() }
        frameThread = thread
        val handler = Handler(thread.looper)
        val st = SurfaceTexture(oesTexId).apply {
            setDefaultBufferSize(width, height)
            setOnFrameAvailableListener({
                synchronized(frameSyncObj) {
                    frameAvailable = true
                    frameSyncObj.notifyAll()
                }
            }, handler)
        }
        surfaceTexture = st
        return Surface(st).also { oesSurface = it }
    }

    fun updateOesTexture() {
        val st = surfaceTexture ?: return
        synchronized(frameSyncObj) {
            if (!frameAvailable) {
                runCatching { frameSyncObj.wait(40L) }
            }
            if (frameAvailable) {
                frameAvailable = false
                st.updateTexImage()
                st.getTransformMatrix(stMatrix)
            }
        }
    }

    fun uploadSceneTexture(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    fun drawFrame(
        hasSceneTexture: Boolean,
        hasOesTexture: Boolean,
        oesSourceAspect: Float,
        hudBitmap: Bitmap,
        kenBurnsProgress: Float,
        ptsNs: Long
    ) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0.035f, 0.043f, 0.063f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (hasOesTexture) {
            // Center-crop aspect fit so portrait or ultra-wide custom videos fill 16:9 without distortion
            val targetAspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
            var uMin = 0f; var uMax = 1f
            var vMin = 0f; var vMax = 1f
            if (oesSourceAspect > targetAspect + 0.01f) {
                val scale = targetAspect / oesSourceAspect
                val margin = (1f - scale) / 2f
                uMin = margin; uMax = 1f - margin
            } else if (oesSourceAspect < targetAspect - 0.01f) {
                val scale = oesSourceAspect / targetAspect
                val margin = (1f - scale) / 2f
                vMin = margin; vMax = 1f - margin
            }
            oesCoordsScratch[0] = uMin; oesCoordsScratch[1] = vMin
            oesCoordsScratch[2] = uMax; oesCoordsScratch[3] = vMin
            oesCoordsScratch[4] = uMin; oesCoordsScratch[5] = vMax
            oesCoordsScratch[6] = uMax; oesCoordsScratch[7] = vMax
            oesTexCoordBuffer.clear()
            oesTexCoordBuffer.put(oesCoordsScratch)
            oesTexCoordBuffer.position(0)

            GLES20.glUseProgram(oesProgram)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
            GLES20.glUniform1i(oesSamplerHandle, 0)
            GLES20.glUniformMatrix4fv(oesMatrixHandle, 1, false, stMatrix, 0)
            GLES20.glEnableVertexAttribArray(oesPosHandle)
            GLES20.glVertexAttribPointer(oesPosHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(oesTexHandle)
            GLES20.glVertexAttribPointer(oesTexHandle, 2, GLES20.GL_FLOAT, false, 0, oesTexCoordBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPosHandle)
        GLES20.glVertexAttribPointer(aPosHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        if (hasSceneTexture && !hasOesTexture) {
            val inset = 0.045f * kenBurnsProgress
            val pan = 0.02f * kenBurnsProgress
            kbCoordsScratch[0] = inset + pan
            kbCoordsScratch[1] = 1f - inset
            kbCoordsScratch[2] = 1f - inset + pan
            kbCoordsScratch[3] = 1f - inset
            kbCoordsScratch[4] = inset + pan
            kbCoordsScratch[5] = inset
            kbCoordsScratch[6] = 1f - inset + pan
            kbCoordsScratch[7] = inset
            texCoordBuffer.clear()
            texCoordBuffer.put(kbCoordsScratch)
            texCoordBuffer.position(0)

            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexId)
            GLES20.glUniform1i(uTexHandle, 0)
            GLES20.glEnableVertexAttribArray(aTexHandle)
            GLES20.glVertexAttribPointer(aTexHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        // Draw HUD overlay with alpha blending
        texCoordBuffer.clear()
        texCoordBuffer.put(fullCoords)
        texCoordBuffer.position(0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, hudTexId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, hudBitmap, 0)
        GLES20.glUniform1i(uTexHandle, 0)
        GLES20.glEnableVertexAttribArray(aTexHandle)
        GLES20.glVertexAttribPointer(aTexHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun release() {
        runCatching { oesSurface?.release() }
        runCatching { surfaceTexture?.release() }
        runCatching { frameThread?.quitSafely() }
        oesSurface = null
        surfaceTexture = null
        frameThread = null
        if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }
}
