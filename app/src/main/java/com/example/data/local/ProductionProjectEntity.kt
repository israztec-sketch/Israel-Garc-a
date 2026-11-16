package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "production_projects")
data class ProductionProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val ideaPrompt: String,
    val genreName: String,
    val outputFormatName: String,
    val resolutionName: String,
    val targetMinDurationMinutes: Int,
    val stageName: String,
    val progressPercent: Int,
    val statusMessage: String,
    val errorMessage: String?,
    val scriptJson: String?,
    val artifactsJson: String,
    val directoryPath: String,
    val useTypographyVisualFallback: Boolean,
    val selectedCastJson: String = "[]",
    val lastCompletedStep: String = "",
    val failedStep: String? = null,
    val failedEngineId: String? = null,
    val customVideoPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
