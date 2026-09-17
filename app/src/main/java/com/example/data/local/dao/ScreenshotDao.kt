package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.ActionItem
import com.example.data.local.entity.ExtractedEntityItem
import com.example.data.local.entity.ScreenshotEntity
import com.example.data.local.entity.ScreenshotWithEntities
import kotlinx.coroutines.flow.Flow

data class CategoryCount(
    val category: String,
    val count: Int
)

@Dao
interface ScreenshotDao {

    @Transaction
    @Query("SELECT * FROM screenshots ORDER BY createdAt DESC")
    fun getAllScreenshots(): Flow<List<ScreenshotWithEntities>>

    @Transaction
    @Query("SELECT * FROM screenshots WHERE needsAttention = 1 ORDER BY createdAt DESC")
    fun getInboxScreenshots(): Flow<List<ScreenshotWithEntities>>

    @Transaction
    @Query("SELECT * FROM screenshots WHERE id = :id")
    fun getScreenshotById(id: Long): Flow<ScreenshotWithEntities?>

    @Transaction
    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getScreenshotByIdSync(id: Long): ScreenshotWithEntities?

    @Transaction
    @Query("SELECT * FROM screenshots WHERE title LIKE '%' || :query || '%' OR rawText LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchScreenshots(query: String): Flow<List<ScreenshotWithEntities>>

    @Query("SELECT COUNT(*) FROM screenshots WHERE needsAttention = 1")
    fun getAttentionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM screenshots WHERE imageUri = :imageUri")
    suspend fun countByImageUri(imageUri: String): Int

    @Query("SELECT category, COUNT(*) as count FROM screenshots WHERE needsAttention = 1 GROUP BY category")
    fun getAttentionCountsByCategory(): Flow<List<CategoryCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreenshot(screenshot: ScreenshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntities(entities: List<ExtractedEntityItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<ActionItem>)

    @Update
    suspend fun updateScreenshot(screenshot: ScreenshotEntity)

    @Update
    suspend fun updateEntity(entity: ExtractedEntityItem)

    @Update
    suspend fun updateAction(action: ActionItem)

    @Query("UPDATE screenshots SET needsAttention = 0, status = :status WHERE id = :screenshotId")
    suspend fun markProcessed(screenshotId: Long, status: String = "ACTION_CREATED")

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun deleteScreenshotById(id: Long)

    @Query("DELETE FROM extracted_entities WHERE screenshotId = :screenshotId")
    suspend fun deleteEntitiesByScreenshotId(screenshotId: Long)

    @Query("DELETE FROM actions WHERE screenshotId = :screenshotId")
    suspend fun deleteActionsByScreenshotId(screenshotId: Long)

    @Transaction
    suspend fun deleteEntireScreenshot(id: Long) {
        deleteEntitiesByScreenshotId(id)
        deleteActionsByScreenshotId(id)
        deleteScreenshotById(id)
    }

    @Query("DELETE FROM screenshots")
    suspend fun deleteAllScreenshots()

    @Query("DELETE FROM extracted_entities")
    suspend fun deleteAllEntities()

    @Query("DELETE FROM actions")
    suspend fun deleteAllActions()

    @Transaction
    suspend fun clearAllData() {
        deleteAllActions()
        deleteAllEntities()
        deleteAllScreenshots()
    }

    @Query("SELECT COUNT(*) FROM screenshots")
    suspend fun getTotalScreenshotsCount(): Int

    @Query("SELECT COUNT(*) FROM actions WHERE status = 'CREATED' OR status = 'COMPLETED'")
    suspend fun getTotalActionsCompletedCount(): Int
}
