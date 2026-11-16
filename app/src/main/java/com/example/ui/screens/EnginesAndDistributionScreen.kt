package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ProductionStorageStats
import com.example.distribution.ApkDistributionHelper
import com.example.domain.model.EngineId
import com.example.engines.DeviceCapabilities
import com.example.engines.LiveProductionTelemetry
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber

@Composable
fun EnginesAndDistributionScreen(
    capabilities: DeviceCapabilities?,
    telemetry: LiveProductionTelemetry,
    onShareApk: () -> Unit,
    modifier: Modifier = Modifier,
    storageStats: ProductionStorageStats = ProductionStorageStats(),
    isProducing: Boolean = false,
    onFreeProductionSpace: () -> Unit = {}
) {
    val context = LocalContext.current
    val apkStatus = remember { ApkDistributionHelper.inspectInstalledApk(context) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Motores, Cerebros y APK",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Arquitectura real verificada de MOHANA AI y distribución familiar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // APK Distribution Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Distribución Familiar (APK)", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "v${apkStatus.versionName} · ${String.format("%.2f MB", apkStatus.sizeMb)} · Android 14+ compatible",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Envía el paquete APK real de MOHANA AI directamente a tus familiares por WhatsApp, Telegram, Nearby Share o Drive. En el dispositivo receptor, abre el APK y permite instalar desde esa app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = onShareApk,
                        enabled = apkStatus.exists,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("share_apk_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_share_apk))
                    }
                }
            }

            // Storage & Temp Cleanup Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Almacenamiento y Limpieza de Producción", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Limpieza automática activa tras cada checkpoint exitoso",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    CapabilityRow(
                        label = "Espacio total de proyectos",
                        value = formatMegabytes(storageStats.totalProjectsBytes),
                        ok = true
                    )
                    CapabilityRow(
                        label = "Proyectos fallidos / recuperables",
                        value = "${storageStats.failedProjectsCount} (${formatMegabytes(storageStats.reclaimableBytes)} recuperables)",
                        ok = storageStats.failedProjectsCount == 0 && storageStats.reclaimableBytes == 0L
                    )
                    CapabilityRow(
                        label = "Limpieza automática en sesión",
                        value = "${telemetry.autoCleanedFiles} archivos (${formatMegabytes(telemetry.autoCleanedBytes)})",
                        ok = true
                    )

                    Text(
                        text = "Elimina clips WAV individuales, imágenes temporales de escenas, buffers y carpetas de proyectos fallidos sin tocar tus producciones completadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FilledTonalButton(
                        onClick = onFreeProductionSpace,
                        enabled = !isProducing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("free_production_space_button")
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_free_production_space))
                    }
                }
            }

            // Hardware & OS Capabilities Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capacidades Reales del Dispositivo", style = MaterialTheme.typography.titleMedium)
                    }
                    if (capabilities == null) {
                        Text("Inspeccionando hardware y codecs...", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        CapabilityRow("Sistema", capabilities.androidVersionLabel, true)
                        CapabilityRow("Codec Video AVC", capabilities.avcEncoderName, capabilities.supports1080p)
                        CapabilityRow("Full HD 1920×1080", if (capabilities.supports1080p) "Soportado" else "Fallback 720p", capabilities.supports1080p)
                        CapabilityRow("4K UHD 3840×2160", if (capabilities.supports4k) "Soportado por hardware" else "No soportado (auto 1080p)", capabilities.supports4k)
                        CapabilityRow("Motor TextToSpeech", if (capabilities.ttsEngineAvailable) "${capabilities.spanishVoicesCount} voces ES / ${capabilities.totalVoicesCount} totales" else "No disponible", capabilities.ttsEngineAvailable)
                    }
                }
            }

            // 2 Brains
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Los Dos Cerebros IA (Firebase AI Logic)", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "1. Cerebro Narrativo y Dramático: ${telemetry.brains.narrativeBrainStatus}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "2. Cerebro Visual y de Dirección: ${telemetry.brains.visualBrainStatus}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 7 Engines
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Los Siete Motores Conectados", style = MaterialTheme.typography.titleMedium)
                    EngineId.entries.forEach { engine ->
                        val live = telemetry.engines.firstOrNull { it.engineId == engine }
                        Column {
                            Text(
                                text = engine.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${engine.subtitle} · Estado: ${live?.detail ?: "Listo"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CapabilityRow(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (ok) SuccessEmerald else WarningAmber,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(value, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatMegabytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
