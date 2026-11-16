package com.example.engines

import com.example.domain.model.CharacterProfile
import com.example.domain.model.DialogueLine
import com.example.domain.model.TimbreFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

data class AudioMixResult(
    val masterWavFile: File,
    val totalDurationMs: Long,
    val timedLines: List<DialogueLine>,
    val envelope100ms: FloatArray
)

class MusicSfxEngine {

    companion object {
        const val TARGET_SAMPLE_RATE = 44100
        const val TARGET_CHANNELS = 1
        const val BITS_PER_SAMPLE = 16

        fun saveEnvelopeToFile(envelope: FloatArray, file: File) {
            file.parentFile?.mkdirs()
            DataOutputStream(BufferedOutputStream(FileOutputStream(file), 8192)).use { dos ->
                dos.writeInt(envelope.size)
                for (v in envelope) {
                    dos.writeFloat(v)
                }
                dos.flush()
            }
        }

        fun loadEnvelopeFromFile(file: File): FloatArray? {
            if (!file.exists() || file.length() < 4L) return null
            return runCatching {
                DataInputStream(BufferedInputStream(FileInputStream(file), 8192)).use { dis ->
                    val size = dis.readInt()
                    if (size <= 0 || size > 2_000_000) return@use null
                    FloatArray(size) { dis.readFloat() }
                }
            }.getOrNull()
        }

        data class ParsedWavInfo(
            val audioFormat: Int,
            val channels: Int,
            val sampleRate: Int,
            val bitsPerSample: Int,
            val dataOffset: Int,
            val dataLength: Int
        )

        fun parseWavHeader(bytes: ByteArray): ParsedWavInfo? {
            if (bytes.size <= 44) return null
            var pos = 12
            var audioFormat = 1
            var sampleRate = 22050
            var channels = 1
            var bitsPerSample = 16
            var dataStart = -1
            var dataLen = 0

            while (pos + 8 <= bytes.size) {
                val id = String(bytes, pos, 4, Charsets.US_ASCII)
                val chunkSizeU = readUInt32LE(bytes, pos + 4)
                val payloadPos = pos + 8
                val available = (bytes.size - payloadPos).coerceAtLeast(0)

                if (id == "fmt " && payloadPos + 16 <= bytes.size) {
                    audioFormat = readShortLE(bytes, payloadPos)
                    channels = readShortLE(bytes, payloadPos + 2)
                    sampleRate = readIntLE(bytes, payloadPos + 4)
                    bitsPerSample = readShortLE(bytes, payloadPos + 14)
                } else if (id == "data") {
                    dataStart = payloadPos
                    dataLen = if (chunkSizeU == 0L || chunkSizeU >= 0x7FFFFFF0L) {
                        available
                    } else {
                        minOf(chunkSizeU, available.toLong()).toInt()
                    }
                    break
                }

                if (chunkSizeU >= 0x7FFFFFF0L) break
                val step = 8L + chunkSizeU + (chunkSizeU and 1L)
                if (step <= 8L && chunkSizeU > 0L) break
                if (pos.toLong() + step > bytes.size) break
                pos += step.toInt()
            }

            // Fallback scan for literal "data" chunk if intermediate chunk sizes were malformed
            if (dataStart < 0) {
                var scan = 12
                while (scan + 8 <= bytes.size) {
                    if (bytes[scan] == 'd'.code.toByte() &&
                        bytes[scan + 1] == 'a'.code.toByte() &&
                        bytes[scan + 2] == 't'.code.toByte() &&
                        bytes[scan + 3] == 'a'.code.toByte()
                    ) {
                        dataStart = scan + 8
                        val chunkSizeU = readUInt32LE(bytes, scan + 4)
                        val available = (bytes.size - dataStart).coerceAtLeast(0)
                        dataLen = if (chunkSizeU == 0L || chunkSizeU >= 0x7FFFFFF0L) {
                            available
                        } else {
                            minOf(chunkSizeU, available.toLong()).toInt()
                        }
                        break
                    }
                    scan++
                }
            }

            if (dataStart < 0 || dataLen <= 0 || channels <= 0 || sampleRate <= 0) return null
            return ParsedWavInfo(
                audioFormat = audioFormat,
                channels = channels,
                sampleRate = sampleRate,
                bitsPerSample = bitsPerSample,
                dataOffset = dataStart,
                dataLength = dataLen
            )
        }

        fun decodeWavToMonoFloat(bytes: ByteArray, normalizeSpeech: Boolean = true): Pair<Int, FloatArray>? {
            val info = parseWavHeader(bytes) ?: return null
            val bytesPerSample = (info.bitsPerSample / 8).coerceAtLeast(1)
            val frameBytes = info.channels * bytesPerSample
            if (frameBytes <= 0) return null
            val srcFrames = info.dataLength / frameBytes
            if (srcFrames <= 0) return null

            val isFloat = info.audioFormat == 3 || (info.audioFormat == 0xFFFE && info.bitsPerSample == 32)
            var peakAbs = 0f

            val monoSrc = FloatArray(srcFrames) { f ->
                var sum = 0f
                for (c in 0 until info.channels) {
                    val offset = info.dataOffset + f * frameBytes + c * bytesPerSample
                    val sample = when {
                        isFloat && info.bitsPerSample == 32 -> {
                            Float.fromBits(readIntLE(bytes, offset)).coerceIn(-1f, 1f)
                        }
                        info.bitsPerSample == 8 -> {
                            ((bytes[offset].toInt() and 0xFF) - 128) / 128f
                        }
                        info.bitsPerSample == 16 -> {
                            val lo = bytes[offset].toInt() and 0xFF
                            val hi = bytes[offset + 1].toInt()
                            ((hi shl 8) or lo) / 32768f
                        }
                        info.bitsPerSample == 24 -> {
                            val b0 = bytes[offset].toInt() and 0xFF
                            val b1 = bytes[offset + 1].toInt() and 0xFF
                            val b2 = bytes[offset + 2].toInt()
                            ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                        }
                        info.bitsPerSample == 32 -> {
                            readIntLE(bytes, offset) / 2147483648f
                        }
                        else -> 0f
                    }
                    sum += sample
                }
                val avg = sum / info.channels
                val a = abs(avg)
                if (a > peakAbs) peakAbs = a
                avg
            }

            if (normalizeSpeech && peakAbs > 0.002f && peakAbs < 0.72f) {
                val gain = min(5.5f, 0.78f / peakAbs)
                for (i in monoSrc.indices) {
                    monoSrc[i] = (monoSrc[i] * gain).coerceIn(-0.98f, 0.98f)
                }
            }
            return info.sampleRate to monoSrc
        }

        private fun readUInt32LE(b: ByteArray, offset: Int): Long {
            return (b[offset].toLong() and 0xFFL) or
                ((b[offset + 1].toLong() and 0xFFL) shl 8) or
                ((b[offset + 2].toLong() and 0xFFL) shl 16) or
                ((b[offset + 3].toLong() and 0xFFL) shl 24)
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

    /**
     * Mixes all synthesized WAV voice segments with dramatic silences, character timbre DSP,
     * procedurally synthesized ambient music bed with sidechain ducking, and synchronized SFX.
     * Streams directly to disk so even 40+ minute productions never exhaust heap memory.
     */
    suspend fun mixMasterWav(
        lines: List<DialogueLine>,
        characters: List<CharacterProfile>,
        outputWavFile: File,
        onProgress: suspend (percent: Int) -> Unit
    ): AudioMixResult = withContext(Dispatchers.IO) {
        outputWavFile.parentFile?.mkdirs()
        val charMap = characters.associateBy { it.name.lowercase() }

        // 1. Compute exact timeline with pauses
        var cursorMs = 900L // Cinematic intro breath
        val timedLines = lines.map { line ->
            val wavFile = line.synthesizedWavPath?.let { File(it) }
            val durationMs = if (wavFile != null && wavFile.exists()) {
                VoiceAudioEngine.readWavDurationMs(wavFile).coerceAtLeast(line.actualDurationMs)
            } else {
                line.actualDurationMs.coerceAtLeast(1000L)
            }
            val start = cursorMs
            cursorMs += durationMs + line.pauseAfterMs.coerceIn(250L, 3000L)
            line.copy(startTimeMs = start, actualDurationMs = durationMs)
        }
        val totalDurationMs = cursorMs + 1500L // Outro tail
        val totalSamples = ((totalDurationMs * TARGET_SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(TARGET_SAMPLE_RATE)

        // Envelope at 10Hz (every 100ms = 4410 samples) for reactive waveform visualization
        val samplesPerEnvelopeBin = TARGET_SAMPLE_RATE / 10
        val envelopeSize = (totalSamples / samplesPerEnvelopeBin) + 2
        val envelope = FloatArray(envelopeSize)

        val chunkSamples = 4410 // 100ms per write chunk
        val byteBuffer = ByteArray(chunkSamples * 2)

        // Pre-write 44-byte WAV header placeholder
        BufferedOutputStream(FileOutputStream(outputWavFile), 65536).use { out ->
            out.write(buildWavHeader(0L))

            var lineIdx = 0
            var activeLineSamples: FloatArray? = null
            var activeLineStartSample = 0L
            var activeLineEndSample = 0L

            var sfxSamples: FloatArray? = null
            var sfxStartSample = 0L

            var duckGain = 1.0f
            var currentSampleIndex = 0L
            var prevNoise = 0f
            var lfoPhase = 0.0
            var rngState = 0x5DEECE66D.toInt()
            var currentMood = timedLines.firstOrNull()?.musicMood ?: "MYSTERY_AMBIENT"

            val totalLong = totalSamples.toLong()
            while (currentSampleIndex < totalLong) {
                coroutineContext.ensureActive()
                val remaining = (totalLong - currentSampleIndex).toInt()
                val count = min(chunkSamples, remaining)

                var peakInChunk = 0f

                for (i in 0 until count) {
                    val globalSample = currentSampleIndex + i
                    val timeSec = globalSample.toDouble() / TARGET_SAMPLE_RATE

                    // Release finished line/SFX PCM arrays promptly from RAM
                    if (activeLineSamples != null && globalSample >= activeLineEndSample) {
                        activeLineSamples = null
                    }

                    // Advance active line pointer
                    while (lineIdx < timedLines.size) {
                        val candidate = timedLines[lineIdx]
                        val candStart = (candidate.startTimeMs * TARGET_SAMPLE_RATE) / 1000L
                        val candEnd = candStart + ((candidate.actualDurationMs * TARGET_SAMPLE_RATE) / 1000L)
                        if (globalSample < candStart) {
                            break
                        }
                        if (globalSample in candStart until candEnd) {
                            if (activeLineStartSample != candStart) {
                                activeLineSamples = null
                                sfxSamples = null
                                currentMood = candidate.musicMood
                                val wavFile = candidate.synthesizedWavPath?.let { File(it) }
                                val timbre = charMap[candidate.speakerName.lowercase()]?.timbreFilter
                                    ?: TimbreFilter.CRISP_NARRATOR
                                activeLineSamples = if (wavFile != null && wavFile.exists()) {
                                    applyTimbreDsp(loadAndResampleWav(wavFile), timbre)
                                } else {
                                    FloatArray(0)
                                }
                                activeLineStartSample = candStart
                                activeLineEndSample = candStart + (activeLineSamples?.size ?: 0)

                                if (candidate.sfxCue != "NONE") {
                                    sfxSamples = generateSfx(candidate.sfxCue)
                                    sfxStartSample = candStart
                                }
                            }
                            break
                        } else {
                            lineIdx++
                            if (lineIdx < timedLines.size) {
                                currentMood = timedLines[lineIdx].musicMood
                            }
                        }
                    }

                    // Voice sample
                    var voiceSample = 0f
                    val activeArr = activeLineSamples
                    val isSpeaking = if (activeArr != null && globalSample in activeLineStartSample until activeLineEndSample) {
                        val localIdx = (globalSample - activeLineStartSample).toInt()
                        if (localIdx in activeArr.indices) {
                            voiceSample = activeArr[localIdx] * 1.05f
                        }
                        true
                    } else {
                        false
                    }

                    // Responsive sidechain ducking (fast drop when character speaks, smooth swell on pause)
                    val targetDuck = if (isSpeaking) 0.12f else 0.34f
                    val duckCoeff = if (targetDuck < duckGain) 0.008f else 0.0012f
                    duckGain += (targetDuck - duckGain) * duckCoeff

                    // Procedural ambient bed synthesis (lock-free fast xorshift32 noise)
                    lfoPhase += 0.15 / TARGET_SAMPLE_RATE
                    rngState = rngState xor (rngState shl 13)
                    rngState = rngState xor (rngState ushr 17)
                    rngState = rngState xor (rngState shl 5)
                    val noise = (rngState ushr 8) * (1.0f / 8388608.0f) - 1.0f
                    prevNoise = prevNoise * 0.96f + noise * 0.04f // dark warm wind/room noise
                    val musicSample = synthesizeAmbientSample(timeSec, lfoPhase, prevNoise, currentMood)

                    // Fade in/out envelope on master track
                    val fadeIn = (globalSample.toFloat() / (TARGET_SAMPLE_RATE * 1.2f)).coerceIn(0f, 1f)
                    val fadeOut = ((totalLong - globalSample).toFloat() / (TARGET_SAMPLE_RATE * 1.5f)).coerceIn(0f, 1f)

                    // SFX sample
                    var sfxSample = 0f
                    val sfxArr = sfxSamples
                    if (sfxArr != null) {
                        val sfxOffset = (globalSample - sfxStartSample).toInt()
                        if (sfxOffset in sfxArr.indices) {
                            sfxSample = sfxArr[sfxOffset] * 0.45f
                        } else if (sfxOffset >= sfxArr.size) {
                            sfxSamples = null
                        }
                    }

                    val mixed = ((voiceSample + (musicSample * duckGain) + sfxSample) * fadeIn * fadeOut)
                    val limited = tanh(mixed.toDouble()).toFloat() // soft analog limiter

                    val absVal = abs(limited)
                    if (absVal > peakInChunk) peakInChunk = absVal

                    val pcm16 = (limited * 32760f).toInt().coerceIn(-32768, 32767)
                    byteBuffer[i * 2] = (pcm16 and 0xFF).toByte()
                    byteBuffer[i * 2 + 1] = ((pcm16 shr 8) and 0xFF).toByte()
                }

                out.write(byteBuffer, 0, count * 2)

                val envIdx = (currentSampleIndex / samplesPerEnvelopeBin).toInt()
                if (envIdx in envelope.indices) {
                    envelope[envIdx] = peakInChunk
                }

                currentSampleIndex += count
                if ((currentSampleIndex / chunkSamples) % 15L == 0L) {
                    val pct = ((currentSampleIndex * 100L) / totalLong).toInt().coerceIn(0, 99)
                    onProgress(pct)
                }
            }
            activeLineSamples = null
            sfxSamples = null
            out.flush()
        }

        // Patch RIFF header with actual written sizes
        val dataBytes = outputWavFile.length() - 44L
        RandomAccessFile(outputWavFile, "rw").use { raf ->
            raf.seek(0)
            raf.write(buildWavHeader(dataBytes))
        }
        onProgress(100)

        AudioMixResult(
            masterWavFile = outputWavFile,
            totalDurationMs = totalDurationMs,
            timedLines = timedLines,
            envelope100ms = envelope
        )
    }

    private fun synthesizeAmbientSample(
        t: Double,
        lfoPhase: Double,
        filteredNoise: Float,
        mood: String
    ): Float {
        val lfo = (0.5 + 0.5 * sin(2.0 * PI * lfoPhase)).toFloat()
        return when (mood) {
            "TERROR_DRONE" -> {
                // Dark D1 (36.7Hz) + tritone G#1 (51.9Hz) + dissonant upper drone + cold wind
                val sub = sin(2.0 * PI * 36.71 * t) * 0.35
                val tritone = sin(2.0 * PI * 51.91 * t) * (0.14 + 0.10 * lfo)
                val high = sin(2.0 * PI * 146.83 * t) * 0.08 * (1.0 - lfo)
                (sub + tritone + high + filteredNoise * 0.35).toFloat()
            }
            "SUSPENSE_PULSE" -> {
                // Tense pulsing bass at ~66 BPM
                val pulseEnv = max(0.0, sin(2.0 * PI * 1.1 * t))
                val pulse = sin(2.0 * PI * 55.0 * t) * 0.32 * pulseEnv
                val pad = sin(2.0 * PI * 110.0 * t) * 0.12 + sin(2.0 * PI * 164.81 * t) * 0.08
                (pulse + pad + filteredNoise * 0.22).toFloat()
            }
            "SCIFI_SYNTH" -> {
                val root = sin(2.0 * PI * 65.41 * t) * 0.26
                val fifth = sin(2.0 * PI * 98.0 * t) * 0.18
                val shimmer = sin(2.0 * PI * (261.63 + 2.5 * sin(2.0 * PI * 0.4 * t)) * t) * 0.09 * lfo
                (root + fifth + shimmer + filteredNoise * 0.15).toFloat()
            }
            "CALM_PODCAST" -> {
                val root = sin(2.0 * PI * 73.42 * t) * 0.22
                val third = sin(2.0 * PI * 110.0 * t) * 0.14
                val fifth = sin(2.0 * PI * 146.83 * t) * 0.10 * lfo
                (root + third + fifth + filteredNoise * 0.08).toFloat()
            }
            else -> { // MYSTERY_AMBIENT
                val root = sin(2.0 * PI * 49.0 * t) * 0.28
                val minorThird = sin(2.0 * PI * 116.54 * t) * 0.14 * lfo
                val fifth = sin(2.0 * PI * 146.83 * t) * 0.11
                (root + minorThird + fifth + filteredNoise * 0.22).toFloat()
            }
        }
    }

    private fun generateSfx(cue: String): FloatArray {
        val sr = TARGET_SAMPLE_RATE
        return when (cue) {
            "IMPACT_BOOM" -> {
                val len = (sr * 1.3).toInt()
                FloatArray(len) { i ->
                    val t = i.toDouble() / sr
                    val freq = 95.0 * kotlin.math.exp(-3.5 * t) + 28.0
                    val env = kotlin.math.exp(-3.2 * t)
                    (sin(2.0 * PI * freq * t) * env).toFloat()
                }
            }
            "HEARTBEAT" -> {
                val len = (sr * 1.2).toInt()
                FloatArray(len) { i ->
                    val t = i.toDouble() / sr
                    val beat1 = kotlin.math.exp(-22.0 * t) * sin(2.0 * PI * 52.0 * t)
                    val t2 = (t - 0.28).coerceAtLeast(0.0)
                    val beat2 = if (t >= 0.28) kotlin.math.exp(-24.0 * t2) * sin(2.0 * PI * 48.0 * t2) * 0.75 else 0.0
                    (beat1 + beat2).toFloat()
                }
            }
            "TAPE_STATIC" -> {
                val len = (sr * 0.65).toInt()
                var lp = 0f
                FloatArray(len) { i ->
                    val env = sin(PI * i / len).toFloat()
                    val n = (Math.random() * 2.0 - 1.0).toFloat()
                    lp = lp * 0.6f + n * 0.4f
                    (n - lp) * env * 0.65f
                }
            }
            "RISER" -> {
                val len = (sr * 1.6).toInt()
                FloatArray(len) { i ->
                    val progress = i.toDouble() / len
                    val freq = 120.0 + progress * 620.0
                    (sin(2.0 * PI * freq * (i.toDouble() / sr)) * progress * 0.55).toFloat()
                }
            }
            "GHOST_WHOOSH" -> {
                val len = (sr * 1.1).toInt()
                var prev = 0f
                FloatArray(len) { i ->
                    val env = sin(PI * i / len).toFloat()
                    val n = (Math.random() * 2.0 - 1.0).toFloat()
                    prev = prev * 0.90f + n * 0.10f
                    prev * env * 1.3f
                }
            }
            else -> FloatArray(0)
        }
    }

    private fun applyTimbreDsp(samples: FloatArray, filter: TimbreFilter): FloatArray {
        if (samples.isEmpty()) return samples
        return when (filter) {
            TimbreFilter.CRISP_NARRATOR -> samples
            TimbreFilter.WARM_STUDIO -> {
                var lp = 0f
                FloatArray(samples.size) { i ->
                    lp = lp * 0.88f + samples[i] * 0.12f
                    tanh((samples[i] * 0.96f + lp * 0.10f).toDouble()).toFloat()
                }
            }
            TimbreFilter.DEEP_CHAMBER -> {
                // Natural soft dual-tap acoustic studio room (no metallic 24ms comb resonance)
                val tap1 = (TARGET_SAMPLE_RATE * 0.043).toInt()
                val tap2 = (TARGET_SAMPLE_RATE * 0.071).toInt()
                var lp = 0f
                var roomLp = 0f
                FloatArray(samples.size) { i ->
                    val dry = samples[i]
                    lp = lp * 0.90f + dry * 0.10f
                    val early = if (i >= tap1) samples[i - tap1] * 0.075f else 0f
                    val late = if (i >= tap2) samples[i - tap2] * 0.045f else 0f
                    roomLp = roomLp * 0.72f + (early + late) * 0.28f
                    tanh((dry * 0.95f + lp * 0.09f + roomLp).toDouble()).toFloat()
                }
            }
            TimbreFilter.RADIO_FILTER -> {
                // Clean broadcast condenser mic warmth (gentle sub-bass rolloff + soft saturation)
                var subLp = 0f
                var highLp = 0f
                FloatArray(samples.size) { i ->
                    val x = samples[i]
                    subLp = subLp * 0.985f + x * 0.015f
                    val hp = x - subLp
                    highLp = highLp * 0.65f + hp * 0.35f
                    tanh((hp * 0.85f + highLp * 0.22f).toDouble()).toFloat()
                }
            }
        }
    }

    private fun loadAndResampleWav(file: File): FloatArray {
        val bytes = file.readBytes()
        val decoded = decodeWavToMonoFloat(bytes, normalizeSpeech = true) ?: return FloatArray(0)
        val sampleRate = decoded.first
        val monoSrc = decoded.second
        val srcFrames = monoSrc.size
        if (srcFrames <= 0) return FloatArray(0)

        if (sampleRate == TARGET_SAMPLE_RATE) return monoSrc

        val ratio = sampleRate.toDouble() / TARGET_SAMPLE_RATE
        val dstFrames = (srcFrames / ratio).toInt().coerceAtLeast(1)
        return FloatArray(dstFrames) { i ->
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
            val idx1 = (idx0 + 1).coerceIn(0, srcFrames - 1)
            val frac = (srcPos - idx0).toFloat()
            monoSrc[idx0] * (1f - frac) + monoSrc[idx1] * frac
        }
    }

    private fun buildWavHeader(dataSize: Long): ByteArray {
        val totalDataLen = dataSize + 36L
        val byteRate = TARGET_SAMPLE_RATE.toLong() * TARGET_CHANNELS * (BITS_PER_SAMPLE / 8)
        val header = ByteArray(44)
        // RIFF
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalDataLen.toInt())
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        // fmt
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16) // PCM chunk size
        writeShortLE(header, 20, 1) // AudioFormat = 1 (PCM)
        writeShortLE(header, 22, TARGET_CHANNELS)
        writeIntLE(header, 24, TARGET_SAMPLE_RATE)
        writeIntLE(header, 28, byteRate.toInt())
        writeShortLE(header, 32, (TARGET_CHANNELS * BITS_PER_SAMPLE / 8))
        writeShortLE(header, 34, BITS_PER_SAMPLE)
        // data
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, dataSize.toInt())
        return header
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
