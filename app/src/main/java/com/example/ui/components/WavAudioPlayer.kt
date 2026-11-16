package com.example.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.engines.MusicSfxEngine
import com.example.engines.VoiceAudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

private const val TAG = "WavAudioPlayer"

data class WavMetadata(
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val waveformBars: List<Float>
) {
    val formatSummary: String
        get() {
            val khz = String.format("%.1f kHz", sampleRateHz / 1000.0)
            val ch = if (channels == 1) "Mono" else "Estéreo"
            return "$khz · ${bitsPerSample}-bit · $ch"
        }
}

object WavFileInspector {
    fun inspectWav(file: File, barCount: Int = 52): WavMetadata? {
        if (!file.exists() || file.length() <= 44L) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val prefixLen = minOf(4096L, file.length()).toInt()
                val header = ByteArray(prefixLen)
                raf.readFully(header)

                val parsed = MusicSfxEngine.parseWavHeader(header)
                val channels = (parsed?.channels ?: readShortLE(header, 22)).coerceAtLeast(1)
                val sampleRate = (parsed?.sampleRate ?: readIntLE(header, 24)).coerceAtLeast(8000)
                val bitsPerSample = (parsed?.bitsPerSample ?: readShortLE(header, 34)).coerceAtLeast(8)
                val dataStartOffset = (parsed?.dataOffset ?: 44).toLong()
                val durationMs = VoiceAudioEngine.readWavDurationMs(file)

                val dataBytes = (file.length() - dataStartOffset).coerceAtLeast(0L)
                val bytesPerFrame = channels * (bitsPerSample / 8).coerceAtLeast(1)
                val totalFrames = (dataBytes / bytesPerFrame).coerceAtLeast(1L)
                val framesPerBar = (totalFrames / barCount).coerceAtLeast(1L)

                val bars = ArrayList<Float>(barCount)
                val sampleWindowBytes = (minOf(framesPerBar, 256L) * bytesPerFrame).toInt().coerceAtLeast(2)
                val buf = ByteArray(sampleWindowBytes)

                for (bar in 0 until barCount) {
                    val frameOffset = bar * framesPerBar
                    val byteOffset = dataStartOffset + (frameOffset * bytesPerFrame)
                    if (byteOffset + 2 <= file.length()) {
                        raf.seek(byteOffset)
                        val read = raf.read(buf, 0, minOf(buf.size, (file.length() - byteOffset).toInt()))
                        var maxAmp = 0f
                        var pos = 0
                        while (pos + 1 < read) {
                            val lo = buf[pos].toInt() and 0xFF
                            val hi = buf[pos + 1].toInt()
                            val sample = (hi shl 8) or lo
                            val norm = abs(sample / 32768f)
                            if (norm > maxAmp) maxAmp = norm
                            pos += bytesPerFrame
                        }
                        bars.add(maxAmp.coerceIn(0.08f, 1f))
                    } else {
                        bars.add(0.08f)
                    }
                }

                WavMetadata(
                    sampleRateHz = sampleRate,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                    durationMs = durationMs,
                    fileSizeBytes = file.length(),
                    waveformBars = bars
                )
            }
        }.getOrNull()
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

@Composable
fun IntegratedWavAudioPlayer(
    wavFilePath: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val file = remember(wavFilePath) { File(wavFilePath) }
    if (!file.exists() || file.length() <= 44L) return

    var wavMetadata by remember(wavFilePath) { mutableStateOf<WavMetadata?>(null) }
    var isPrepared by remember(wavFilePath) { mutableStateOf(false) }
    var isPlaying by remember(wavFilePath) { mutableStateOf(false) }
    var isScrubbing by remember(wavFilePath) { mutableStateOf(false) }
    var currentMs by remember(wavFilePath) { mutableIntStateOf(0) }
    var totalMs by remember(wavFilePath) { mutableIntStateOf(1) }
    var playbackSpeed by remember(wavFilePath) { mutableFloatStateOf(1.0f) }
    var errorMessage by remember(wavFilePath) { mutableStateOf<String?>(null) }

    val speedOptions = remember { listOf(1.0f, 1.25f, 1.5f, 0.85f) }

    LaunchedEffect(wavFilePath) {
        val meta = withContext(Dispatchers.IO) {
            WavFileInspector.inspectWav(file)
        }
        wavMetadata = meta
        if (meta != null && meta.durationMs > 0L && totalMs <= 1) {
            totalMs = meta.durationMs.toInt()
        }
    }

    val mediaPlayer = remember(wavFilePath) {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setVolume(1.0f, 1.0f)
            setOnPreparedListener { mp ->
                isPrepared = true
                errorMessage = null
                val dur = mp.duration
                if (dur > 0) {
                    totalMs = dur
                }
            }
            setOnCompletionListener {
                isPlaying = false
                currentMs = 0
                runCatching { seekTo(0) }
            }
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra for $wavFilePath")
                isPlaying = false
                isPrepared = false
                errorMessage = "Error de reproducción WAV (código $what/$extra)"
                true
            }
            runCatching {
                setDataSource(file.absolutePath)
                prepareAsync()
            }.onFailure { err ->
                Log.e(TAG, "Failed to prepare MediaPlayer for $wavFilePath", err)
                errorMessage = err.localizedMessage ?: "No se pudo abrir el archivo WAV."
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mediaPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                runCatching {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            }
        }
    }

    LaunchedEffect(isPlaying, isScrubbing, mediaPlayer) {
        while (isPlaying) {
            if (!isScrubbing) {
                runCatching {
                    if (mediaPlayer.isPlaying) {
                        currentMs = mediaPlayer.currentPosition.coerceIn(0, totalMs.coerceAtLeast(1))
                        val dur = mediaPlayer.duration
                        if (dur > 0) totalMs = dur
                    }
                }
            }
            delay(100L)
        }
    }

    fun seekToMs(targetMs: Int) {
        val clamped = targetMs.coerceIn(0, totalMs.coerceAtLeast(1))
        currentMs = clamped
        if (isPrepared) {
            runCatching { mediaPlayer.seekTo(clamped) }
        }
    }

    fun togglePlayPause() {
        if (!isPrepared) return
        runCatching {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else {
                mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(playbackSpeed)
                mediaPlayer.start()
                isPlaying = true
            }
        }.onFailure { err ->
            Log.e(TAG, "Playback toggle failed", err)
            errorMessage = err.localizedMessage ?: "Error al iniciar la reproducción."
        }
    }

    fun cycleSpeed() {
        val nextIdx = (speedOptions.indexOf(playbackSpeed) + 1) % speedOptions.size
        val nextSpeed = speedOptions[nextIdx]
        playbackSpeed = nextSpeed
        if (isPrepared) {
            runCatching {
                val wasPlaying = mediaPlayer.isPlaying
                mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(nextSpeed)
                if (!wasPlaying && mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                }
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "audioPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wav_audio_player_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row: Badge, Title & Speed control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary.copy(
                                    alpha = if (isPlaying) pulseAlpha else 1f
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = wavMetadata?.formatSummary ?: file.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = ::cycleSpeed,
                    enabled = isPrepared,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("wav_speed_button"),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Error display if MediaPlayer fails
            errorMessage?.let { err ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Interactive Waveform Scrubber
            val bars = wavMetadata?.waveformBars ?: remember { List(52) { 0.25f } }
            val progressFraction = (currentMs.toFloat() / totalMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            val activeColor = MaterialTheme.colorScheme.primary
            val inactiveColor = MaterialTheme.colorScheme.outline

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .pointerInput(totalMs, isPrepared) {
                        detectTapGestures { offset ->
                            if (size.width > 0) {
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                seekToMs((fraction * totalMs).toInt())
                            }
                        }
                    }
                    .pointerInput(totalMs, isPrepared) {
                        detectHorizontalDragGestures(
                            onDragStart = { isScrubbing = true },
                            onDragEnd = {
                                isScrubbing = false
                                seekToMs(currentMs)
                            },
                            onDragCancel = { isScrubbing = false },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                if (size.width > 0) {
                                    val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    currentMs = (fraction * totalMs).toInt()
                                }
                            }
                        )
                    }
                    .semantics {
                        contentDescription = "Forma de onda interactiva para buscar en el audio WAV"
                    }
                    .testTag("wav_waveform_scrubber")
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val count = bars.size.coerceAtLeast(1)
                    val slotW = size.width / count
                    val barW = (slotW * 0.62f).coerceAtLeast(2f)
                    val centerY = size.height / 2f

                    for (i in bars.indices) {
                        val barFraction = (i + 0.5f) / count
                        val barColor = if (barFraction <= progressFraction) activeColor else inactiveColor
                        val barH = (bars[i] * size.height).coerceIn(4f, size.height)
                        val x = i * slotW + (slotW - barW) / 2f
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, centerY - barH / 2f),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }
            }

            // Precision Seek Slider + Timecodes
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Slider(
                    value = currentMs.toFloat().coerceIn(0f, totalMs.coerceAtLeast(1).toFloat()),
                    onValueChange = { pos ->
                        isScrubbing = true
                        currentMs = pos.toInt()
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        seekToMs(currentMs)
                    },
                    valueRange = 0f..totalMs.toFloat().coerceAtLeast(1f),
                    enabled = isPrepared,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wav_seek_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(currentMs.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("wav_elapsed_time")
                    )
                    val remaining = (totalMs - currentMs).coerceAtLeast(0)
                    Text(
                        text = "-${formatDurationMs(remaining.toLong())} · ${formatDurationMs(totalMs.toLong())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("wav_total_time")
                    )
                }
            }

            // Studio Transport Controls: -10s | Play/Pause | +10s
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { seekToMs(currentMs - 10_000) },
                    enabled = isPrepared,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("wav_rewind_10_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay10,
                        contentDescription = "Retroceder 10 segundos",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                FilledIconButton(
                    onClick = ::togglePlayPause,
                    enabled = isPrepared || errorMessage != null,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("wav_play_pause_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (!isPrepared && errorMessage == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar audio" else "Reproducir audio",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                IconButton(
                    onClick = { seekToMs(currentMs + 10_000) },
                    enabled = isPrepared,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("wav_forward_10_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forward10,
                        contentDescription = "Adelantar 10 segundos",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
