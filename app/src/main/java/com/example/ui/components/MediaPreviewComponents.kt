package com.example.ui.components

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.model.ArtifactFormat
import com.example.domain.model.ExportArtifact
import com.example.ui.theme.SuccessEmerald
import java.io.File

@Composable
fun IntegratedVideoPlayer(
    mp4FilePath: String,
    modifier: Modifier = Modifier
) {
    val file = remember(mp4FilePath) { File(mp4FilePath) }
    if (!file.exists() || file.length() == 0L) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reproductor MP4 Integrado",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoURI(Uri.fromFile(file))
                            setOnPreparedListener { mp ->
                                mp.isLooping = false
                                seekTo(100)
                            }
                        }
                    },
                    update = { videoView ->
                        val tagPath = videoView.tag as? String
                        if (tagPath != mp4FilePath) {
                            videoView.tag = mp4FilePath
                            videoView.setVideoURI(Uri.fromFile(file))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VerifiedArtifactRow(
    artifact: ExportArtifact,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val file = File(artifact.filePath)
    val verified = file.exists() && file.length() > 0L
    if (!verified) return

    val sizeText = if (file.length() >= 1024L * 1024L) {
        String.format("%.2f MB", file.length() / (1024.0 * 1024.0))
    } else {
        "${(file.length() / 1024L).coerceAtLeast(1L)} KB"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = when (artifact.format) {
                        ArtifactFormat.MP4 -> Icons.Filled.Movie
                        ArtifactFormat.WAV -> Icons.Filled.AudioFile
                        else -> Icons.Filled.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = artifact.format.label,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Archivo verificado",
                            tint = SuccessEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    val meta = buildList {
                        add(sizeText)
                        if (artifact.resolutionLabel.isNotBlank()) add(artifact.resolutionLabel)
                        if (artifact.durationMs > 0L) add(formatDurationMs(artifact.durationMs))
                    }.joinToString(" · ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.testTag("share_${artifact.format.extension}_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Compartir ${artifact.format.label}",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Enviar")
            }
        }
    }
}

fun formatDurationMs(ms: Long): String {
    val secTotal = (ms / 1000L).coerceAtLeast(0L)
    val hrs = secTotal / 3600L
    val mins = (secTotal % 3600L) / 60L
    val secs = secTotal % 60L
    return if (hrs > 0L) {
        "%d:%02d:%02d".format(hrs, mins, secs)
    } else {
        "%02d:%02d".format(mins, secs)
    }
}
