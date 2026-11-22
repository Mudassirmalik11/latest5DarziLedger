package com.example.data.local.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.data.local.entities.MeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
  @Query(
    """
    SELECT * FROM measurements
    WHERE customerId = :customerId AND isDeleted = 0 AND isCurrent = 1
    ORDER BY updatedAt DESC
    LIMIT 1
    """
  )
  fun observeCurrentForCustomer(customerId: String): Flow<MeasurementEntity?>

  @Query(
    """
    SELECT * FROM measurements
    WHERE customerId = :customerId AND isDeleted = 0 AND isCurrent = 1
    ORDER BY updatedAt DESC
    LIMIT 1
    """
  )
  suspend fun getCurrentForCustomer(customerId: String): MeasurementEntity?

  @Query(
    """
    SELECT * FROM measurements
    WHERE customerId = :customerId AND isDeleted = 0
    ORDER BY createdAt DESC
    """
  )
  fun observeHistoryForCustomer(customerId: String): Flow<List<MeasurementEntity>>

  @Query(
    """
    SELECT * FROM measurements
    WHERE customerId = :customerId AND isDeleted = 0
    ORDER BY isCurrent DESC, createdAt DESC
    """
  )
  suspend fun getHistoryForCustomer(customerId: String): List<MeasurementEntity>

  @Query("SELECT * FROM measurements WHERE measurementId = :measurementId LIMIT 1")
  suspend fun getById(measurementId: String): MeasurementEntity?

  @Query(
    """
    UPDATE measurements
    SET isCurrent = 0, updatedAt = :updatedAt
    WHERE customerId = :customerId AND isCurrent = 1
    """
  )
  suspend fun clearCurrentFlagForCustomer(customerId: String, updatedAt: Long)

  @Query(
    """
    UPDATE measurements
    SET isCurrent = 0, updatedAt = :updatedAt
    WHERE customerId = :customerId AND measurementId != :keepMeasurementId AND isCurrent = 1
    """
  )
  suspend fun clearCurrentFlagExcept(customerId: String, keepMeasurementId: String, updatedAt: Long)

  @Upsert
  suspend fun insert(measurement: MeasurementEntity)

  @Query(
    """
    SELECT * FROM measurements
    WHERE customerId = :customerId AND isDeleted = 0
    ORDER BY createdAt DESC
    LIMIT 1
    """
  )
  suspend fun getLatestActiveForCustomer(customerId: String): MeasurementEntity?

  @Query("SELECT * FROM measurements ORDER BY createdAt ASC")
  suspend fun getAllForBackup(): List<MeasurementEntity>
}
