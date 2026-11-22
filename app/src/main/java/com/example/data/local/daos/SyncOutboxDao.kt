package com.example.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.model.SyncStatus
import com.example.data.local.entities.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun enqueue(event: SyncOutboxEntity)

  @Query(
    """
    SELECT * FROM sync_outbox
    WHERE syncStatus IN ('PENDING', 'FAILED')
      AND nextRetryAt <= :nowMillis
    ORDER BY createdAt ASC
    LIMIT :limit
    """
  )
  suspend fun getReadyEvents(nowMillis: Long, limit: Int = 50): List<SyncOutboxEntity>

  @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC")
  suspend fun getAllEvents(): List<SyncOutboxEntity>

  @Query("SELECT COUNT(*) FROM sync_outbox WHERE syncStatus IN ('PENDING', 'IN_PROGRESS', 'FAILED')")
  fun observePendingCount(): Flow<Int>

  @Query("UPDATE sync_outbox SET syncStatus = :status, lastError = :lastError WHERE eventId = :eventId")
  suspend fun updateStatus(eventId: String, status: SyncStatus, lastError: String? = null)

  @Query(
    """
    UPDATE sync_outbox
    SET syncStatus = 'FAILED',
        retryCount = retryCount + 1,
        nextRetryAt = :nextRetryAt,
        lastError = :error
    WHERE eventId = :eventId
    """
  )
  suspend fun markRetry(eventId: String, nextRetryAt: Long, error: String)

  @Query(
    """
    UPDATE sync_outbox
    SET syncStatus = 'CONFLICT',
        lastError = :error
    WHERE eventId = :eventId
    """
  )
  suspend fun markConflict(eventId: String, error: String)

  @Query(
    """
    UPDATE sync_outbox
    SET syncStatus = 'PENDING',
        payloadJson = :payloadJson,
        retryCount = 0,
        nextRetryAt = :nextRetryAt,
        lastError = NULL
    WHERE eventId = :eventId
    """
  )
  suspend fun updatePayloadAndReset(eventId: String, payloadJson: String, nextRetryAt: Long)

  @Query(
    """
    SELECT COUNT(*) FROM sync_outbox
    WHERE entityType = :entityType
      AND entityId = :entityId
      AND syncStatus IN ('PENDING', 'IN_PROGRESS', 'FAILED', 'CONFLICT')
    """
  )
  suspend fun countActiveForEntity(entityType: com.example.core.model.SyncEntityType, entityId: String): Int

  @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId AND syncStatus = 'CONFLICT' LIMIT 1")
  suspend fun findConflictEventForEntity(entityType: com.example.core.model.SyncEntityType, entityId: String): SyncOutboxEntity?
}
