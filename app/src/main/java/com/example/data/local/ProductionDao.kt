package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductionDao {
    @Query("SELECT * FROM production_projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProductionProjectEntity>>

    @Query("SELECT * FROM production_projects WHERE id = :id LIMIT 1")
    fun observeProjectById(id: String): Flow<ProductionProjectEntity?>

    @Query("SELECT * FROM production_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProductionProjectEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProjectIgnore(entity: ProductionProjectEntity): Long

    @Update
    suspend fun updateProject(entity: ProductionProjectEntity)

    /**
     * Uses INSERT(IGNORE) + UPDATE instead of REPLACE so updating an existing parent project
     * never triggers SQLite ON DELETE CASCADE on child Room tables.
     */
    @Transaction
    suspend fun upsertProject(entity: ProductionProjectEntity) {
        val insertedRowId = insertProjectIgnore(entity)
        if (insertedRowId == -1L) {
            updateProject(entity)
        }
    }

    @Query("DELETE FROM production_projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("SELECT * FROM production_projects WHERE stageName != 'COMPLETED' ORDER BY updatedAt DESC")
    suspend fun getRecoverableProjects(): List<ProductionProjectEntity>

    @Query("SELECT * FROM production_projects ORDER BY updatedAt DESC")
    suspend fun getAllProjectsOnce(): List<ProductionProjectEntity>
}
