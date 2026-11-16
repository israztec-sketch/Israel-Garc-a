package com.example.distribution

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.domain.model.ExportArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ApkDistributionHelper {

    data class ApkStatus(
        val exists: Boolean,
        val sizeMb: Double,
        val versionName: String,
        val packageName: String
    )

    fun inspectInstalledApk(context: Context): ApkStatus {
        val sourcePath = context.applicationInfo.sourceDir
        val file = File(sourcePath)
        val sizeMb = if (file.exists()) file.length() / (1024.0 * 1024.0) else 0.0
        return ApkStatus(
            exists = file.exists() && file.length() > 0L,
            sizeMb = sizeMb,
            versionName = BuildConfig.VERSION_NAME,
            packageName = context.packageName
        )
    }

    fun shareArtifact(context: Context, artifact: ExportArtifact, title: String) {
        val file = File(artifact.filePath)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "El archivo no existe en disco.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = artifact.format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MOHANA AI — $title")
            putExtra(Intent.EXTRA_TEXT, "Compartido desde MOHANA AI · Inteligencia Creativa: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartir ${artifact.format.label}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    suspend fun shareInstalledApkWithFamily(context: Context): Result<Unit> = runCatching {
        val apkCopy = withContext(Dispatchers.IO) {
            val src = File(context.applicationInfo.sourceDir)
            if (!src.exists() || src.length() == 0L) {
                throw IllegalStateException("No se encontró el archivo APK instalado en el dispositivo.")
            }
            val exportDir = File(context.cacheDir, "share_exports").apply { mkdirs() }
            val dest = File(exportDir, "MOHANA_AI_v${BuildConfig.VERSION_NAME}.apk")
            src.copyTo(dest, overwrite = true)
            dest
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkCopy
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Instalar MOHANA AI (APK)")
            putExtra(
                Intent.EXTRA_TEXT,
                "Te comparto la app MOHANA AI (Inteligencia Creativa) para Android 14+. Abre el archivo APK para instalarla."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartir APK de MOHANA AI con familiares")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
