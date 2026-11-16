package com.example.data.local

import android.content.Context
import com.example.domain.model.OrchestratorStep
import com.example.domain.model.OutputFormat
import com.example.domain.model.ProductionStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CheckpointCleanupResult(
    val completedStep: OrchestratorStep,
    val deletedFilesCount: Int,
    val freedBytes: Long
)

data class ProductionStorageStats(
    val totalProjectsBytes: Long = 0L,
    val failedProjectsBytes: Long = 0L,
    val failedProjectsCount: Int = 0,
    val tempCacheBytes: Long = 0L,
    val reclaimableBytes: Long = 0L
)

data class FailedSpaceCleanupSummary(
    val failedProjectsRemoved: Int,
    val deletedFilesCount: Int,
    val freedBytes: Long
)

/**
 * Manages automatic post-checkpoint temporary artifact cleanup (individual WAV clips,
 * temporary scene PNGs, envelope/temp buffers) and manual reclamation of failed productions.
 */
class ProductionStorageCleaner(
    private val context: Context,
    private val dao: ProductionDao
) {
    private fun projectsRootDir(): File = File(context.filesDir, "mohana_projects")

    /**
     * Automatically cleans temporary working files that are safely superseded after
     * a successful checkpoint without ever deleting files required by subsequent steps
     * or final verified deliverables in exports/.
     */
    suspend fun cleanAfterCheckpoint(
        projectDir: File,
        completedStep: OrchestratorStep,
        format: OutputFormat
    ): CheckpointCleanupResult = withContext(Dispatchers.IO) {
        var deletedFiles = 0
        var freedBytes = 0L

        // 1. Always purge transient buffer/partial files inside projectDir & app cache
        val (tmpFiles, tmpBytes) = deleteTransientBuffers(projectDir)
        deletedFiles += tmpFiles
        freedBytes += tmpBytes

        val (cacheFiles, cacheBytes) = cleanMohanaCacheTempFiles()
        deletedFiles += cacheFiles
        freedBytes += cacheBytes

        // 2. Stage-specific superseded artifact cleanup
        when (completedStep) {
            OrchestratorStep.MUSIC_MIX -> {
                // Individual line WAVs are now mixed & verified inside exports/MOHANA_Audio_Maestro.wav
                val masterWav = File(projectDir, "exports/MOHANA_Audio_Maestro.wav")
                if (masterWav.exists() && masterWav.length() > 44L) {
                    val (vf, vb) = deleteDirectoryContents(File(projectDir, "voices"))
                    deletedFiles += vf
                    freedBytes += vb

                    if (format == OutputFormat.AUDIO_DRAMA_WAV) {
                        val envFile = File(projectDir, "audio_envelope.bin")
                        if (envFile.exists()) {
                            freedBytes += envFile.length()
                            if (envFile.delete()) deletedFiles++
                        }
                        val (sf, sb) = deleteDirectoryContents(File(projectDir, "scenes"))
                        deletedFiles += sf
                        freedBytes += sb
                    }
                }
            }

            OrchestratorStep.VIDEO_RENDER,
            OrchestratorStep.COMPLETED -> {
                val isVideoFormat = format == OutputFormat.AUDIO_VIDEO_EPISODE ||
                    format == OutputFormat.VIDEOPODCAST_40MIN
                val mp4File = File(projectDir, "exports/MOHANA_Video_Final.mp4")
                val canCleanVideoTemps = !isVideoFormat || (mp4File.exists() && mp4File.length() > 0L)

                if (canCleanVideoTemps) {
                    val (vf, vb) = deleteDirectoryContents(File(projectDir, "voices"))
                    deletedFiles += vf
                    freedBytes += vb

                    val (sf, sb) = deleteDirectoryContents(File(projectDir, "scenes"))
                    deletedFiles += sf
                    freedBytes += sb

                    val envFile = File(projectDir, "audio_envelope.bin")
                    if (envFile.exists()) {
                        freedBytes += envFile.length()
                        if (envFile.delete()) deletedFiles++
                    }
                }
            }

            OrchestratorStep.BOOK_EXPORT -> {
                val (vf, vb) = deleteDirectoryContents(File(projectDir, "voices"))
                deletedFiles += vf
                freedBytes += vb

                val (sf, sb) = deleteDirectoryContents(File(projectDir, "scenes"))
                deletedFiles += sf
                freedBytes += sb

                val envFile = File(projectDir, "audio_envelope.bin")
                if (envFile.exists()) {
                    freedBytes += envFile.length()
                    if (envFile.delete()) deletedFiles++
                }
            }

            else -> Unit
        }

        CheckpointCleanupResult(
            completedStep = completedStep,
            deletedFilesCount = deletedFiles,
            freedBytes = freedBytes
        )
    }

    /**
     * Inspects local production storage to report total space and reclaimable bytes
     * belonging to failed projects, orphaned project folders, and temp cache buffers.
     */
    suspend fun inspectStorage(): ProductionStorageStats = withContext(Dispatchers.IO) {
        val projects = dao.getAllProjectsOnce()
        val knownIds = projects.map { it.id }.toSet()

        var totalBytes = 0L
        var failedBytes = 0L
        var failedCount = 0

        for (project in projects) {
            val dir = File(project.directoryPath)
            val dirSize = calculateDirectorySize(dir)
            totalBytes += dirSize
            if (isFailedProject(project)) {
                failedCount++
                failedBytes += dirSize
            }
        }

        // Also account for orphaned folders left in mohana_projects without a DB row
        val root = projectsRootDir()
        if (root.exists()) {
            root.listFiles()?.forEach { child ->
                if (child.isDirectory && child.name !in knownIds) {
                    val orphanSize = calculateDirectorySize(child)
                    totalBytes += orphanSize
                    failedBytes += orphanSize
                }
            }
        }

        val tempCacheBytes = calculateMohanaCacheBytes()

        ProductionStorageStats(
            totalProjectsBytes = totalBytes,
            failedProjectsBytes = failedBytes,
            failedProjectsCount = failedCount,
            tempCacheBytes = tempCacheBytes,
            reclaimableBytes = failedBytes + tempCacheBytes
        )
    }

    /**
     * Deletes all files on disk (WAVs, scene images, buffers, partial exports) and DB rows
     * for failed projects, plus orphaned directories and temporary cache buffers.
     * Preserves all COMPLETED and non-failed projects untouched.
     */
    suspend fun freeFailedProductionSpace(): FailedSpaceCleanupSummary = withContext(Dispatchers.IO) {
        val projects = dao.getAllProjectsOnce()
        val knownIds = projects.map { it.id }.toSet()

        var removedProjects = 0
        var deletedFiles = 0
        var freedBytes = 0L

        for (project in projects) {
            if (isFailedProject(project)) {
                val dir = File(project.directoryPath)
                val (filesCount, bytes) = countAndDeleteRecursively(dir)
                deletedFiles += filesCount
                freedBytes += bytes
                dao.deleteProjectById(project.id)
                removedProjects++
            }
        }

        // Sweep any orphan directories on disk not tracked in Room
        val root = projectsRootDir()
        if (root.exists()) {
            root.listFiles()?.forEach { child ->
                if (child.isDirectory && child.name !in knownIds) {
                    val (filesCount, bytes) = countAndDeleteRecursively(child)
                    deletedFiles += filesCount
                    freedBytes += bytes
                }
            }
        }

        val (cacheFiles, cacheBytes) = cleanMohanaCacheTempFiles()
        deletedFiles += cacheFiles
        freedBytes += cacheBytes

        // Sweep unreferenced custom video files owned by deleted/failed projects
        val survivingCustomVideos = dao.getAllProjectsOnce().mapNotNull { it.customVideoPath }.toSet()
        val customDir = File(context.filesDir, "mohana_custom_videos")
        if (customDir.exists()) {
            customDir.listFiles()?.forEach { f ->
                if (f.isFile && f.absolutePath !in survivingCustomVideos) {
                    val len = f.length()
                    if (f.delete()) {
                        deletedFiles++
                        freedBytes += len
                    }
                }
            }
        }

        FailedSpaceCleanupSummary(
            failedProjectsRemoved = removedProjects,
            deletedFilesCount = deletedFiles,
            freedBytes = freedBytes
        )
    }

    fun isFailedProject(project: ProductionProjectEntity): Boolean {
        if (project.stageName == ProductionStage.COMPLETED.name) return false
        if (project.stageName == ProductionStage.FAILED.name) return true
        return project.stageName == ProductionStage.PAUSED.name && (
            !project.failedStep.isNullOrBlank() ||
                !project.failedEngineId.isNullOrBlank() ||
                !project.errorMessage.isNullOrBlank()
            )
    }

    private fun deleteTransientBuffers(dir: File): Pair<Int, Long> {
        if (!dir.exists()) return 0 to 0L
        var files = 0
        var bytes = 0L
        dir.walkBottomUp().forEach { f ->
            if (f.isFile && (
                    f.name.endsWith(".tmp", ignoreCase = true) ||
                        f.name.endsWith(".part", ignoreCase = true) ||
                        f.name.endsWith(".bak", ignoreCase = true)
                    )
            ) {
                val len = f.length()
                if (f.delete()) {
                    files++
                    bytes += len
                }
            }
        }
        return files to bytes
    }

    private fun cleanMohanaCacheTempFiles(): Pair<Int, Long> {
        val cache = context.cacheDir ?: return 0 to 0L
        if (!cache.exists()) return 0 to 0L
        var files = 0
        var bytes = 0L
        cache.listFiles()?.forEach { f ->
            val isMohanaTemp = f.name.startsWith("mohana_", ignoreCase = true) ||
                f.name.endsWith(".tmp", ignoreCase = true) ||
                f.name.endsWith(".wav", ignoreCase = true)
            if (isMohanaTemp) {
                val (cf, cb) = countAndDeleteRecursively(f)
                files += cf
                bytes += cb
            }
        }
        return files to bytes
    }

    private fun calculateMohanaCacheBytes(): Long {
        val cache = context.cacheDir ?: return 0L
        if (!cache.exists()) return 0L
        var bytes = 0L
        cache.listFiles()?.forEach { f ->
            val isMohanaTemp = f.name.startsWith("mohana_", ignoreCase = true) ||
                f.name.endsWith(".tmp", ignoreCase = true) ||
                f.name.endsWith(".wav", ignoreCase = true)
            if (isMohanaTemp) {
                bytes += calculateDirectorySize(f)
            }
        }
        return bytes
    }

    private fun deleteDirectoryContents(dir: File): Pair<Int, Long> {
        if (!dir.exists()) return 0 to 0L
        return countAndDeleteRecursively(dir)
    }

    private fun countAndDeleteRecursively(target: File): Pair<Int, Long> {
        if (!target.exists()) return 0 to 0L
        var files = 0
        var bytes = 0L
        target.walkBottomUp().forEach { f ->
            if (f.isFile) {
                val len = f.length()
                if (f.delete()) {
                    files++
                    bytes += len
                }
            } else if (f.isDirectory) {
                f.delete()
            }
        }
        return files to bytes
    }

    private fun calculateDirectorySize(target: File): Long {
        if (!target.exists()) return 0L
        if (target.isFile) return target.length()
        return target.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
