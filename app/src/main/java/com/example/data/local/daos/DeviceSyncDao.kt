package com.example.data.local.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.data.local.entities.DeviceConfigEntity
import com.example.data.local.entities.OrderNumberReservationEntity
import com.example.data.local.entities.PrinterConfigEntity
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.local.entities.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceSyncDao {
  // Device Config
  @Query("SELECT * FROM device_config ORDER BY updatedAt DESC LIMIT 1")
  fun observeDeviceConfig(): Flow<DeviceConfigEntity?>

  @Query("SELECT * FROM device_config ORDER BY updatedAt DESC LIMIT 1")
  suspend fun getDeviceConfig(): DeviceConfigEntity?

  @Query("SELECT * FROM device_config WHERE userUid = :userUid ORDER BY updatedAt DESC LIMIT 1")
  suspend fun getDeviceConfigForUser(userUid: String): DeviceConfigEntity?

  @Query("SELECT * FROM device_config WHERE userUid = '' ORDER BY updatedAt DESC LIMIT 1")
  suspend fun getUnclaimedDeviceConfig(): DeviceConfigEntity?

  @Query("SELECT shopId FROM device_config WHERE shopId != '' AND shopId != 'default_shop' ORDER BY updatedAt DESC LIMIT 1")
  suspend fun getLatestRealShopId(): String?

  @Query("DELETE FROM payments")
  suspend fun clearPayments()

  @Query("DELETE FROM orders")
  suspend fun clearOrders()

  @Query("DELETE FROM measurements")
  suspend fun clearMeasurements()

  @Query("DELETE FROM customers")
  suspend fun clearCustomers()

  @Query("DELETE FROM sync_outbox")
  suspend fun clearOutbox()

  @Query("DELETE FROM sync_conflicts")
  suspend fun clearConflicts()

  @Query("DELETE FROM order_number_reservations")
  suspend fun clearReservations()

  @Query("DELETE FROM shops")
  suspend fun clearShops()

  @Upsert
  suspend fun upsertDeviceConfig(config: DeviceConfigEntity)

  // Sync Metadata
  @Query("SELECT * FROM sync_metadata WHERE deviceId = :deviceId LIMIT 1")
  fun observeSyncMetadata(deviceId: String): Flow<SyncMetadataEntity?>

  @Query("SELECT * FROM sync_metadata WHERE deviceId = :deviceId LIMIT 1")
  suspend fun getSyncMetadata(deviceId: String): SyncMetadataEntity?

  @Upsert
  suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity)

  // Order Number Reservations
  @Query(
    """
    SELECT * FROM order_number_reservations
    WHERE deviceId = :deviceId AND isExhausted = 0 AND nextAvailableNumber <= endNumber
    ORDER BY startNumber ASC
    LIMIT 1
    """
  )
  suspend fun getActiveReservation(deviceId: String): OrderNumberReservationEntity?

  @Query("SELECT * FROM order_number_reservations WHERE deviceId = :deviceId ORDER BY startNumber ASC")
  fun observeReservations(deviceId: String): Flow<List<OrderNumberReservationEntity>>

  @Upsert
  suspend fun upsertReservation(reservation: OrderNumberReservationEntity)

  @Update
  suspend fun updateReservation(reservation: OrderNumberReservationEntity)

  // Printer Config
  @Query("SELECT * FROM printer_config WHERE printerId = 'default' LIMIT 1")
  fun observeDefaultPrinter(): Flow<PrinterConfigEntity?>

  @Query("SELECT * FROM printer_config WHERE printerId = 'default' LIMIT 1")
  suspend fun getDefaultPrinter(): PrinterConfigEntity?

  @Upsert
  suspend fun upsertPrinterConfig(config: PrinterConfigEntity)

  // Sync Conflicts
  @Query("SELECT * FROM sync_conflicts WHERE resolved = 0 ORDER BY detectedAt DESC")
  fun observeUnresolvedConflicts(): Flow<List<SyncConflictEntity>>

  @Query("SELECT COUNT(*) FROM sync_conflicts WHERE resolved = 0")
  fun observeUnresolvedConflictCount(): Flow<Int>

  @Query("SELECT * FROM sync_conflicts WHERE conflictId = :conflictId LIMIT 1")
  suspend fun getConflictById(conflictId: String): SyncConflictEntity?

  @Upsert
  suspend fun upsertConflict(conflict: SyncConflictEntity)

  @Query("UPDATE sync_conflicts SET resolved = 1, resolvedAt = :resolvedAt WHERE conflictId = :conflictId")
  suspend fun markConflictResolved(conflictId: String, resolvedAt: Long)

  @Query("SELECT * FROM order_number_reservations WHERE reservationId = :reservationId LIMIT 1")
  suspend fun getReservationById(reservationId: String): OrderNumberReservationEntity?

  @Query("SELECT * FROM order_number_reservations WHERE deviceId = :deviceId AND isExhausted = 0 ORDER BY startNumber ASC")
  suspend fun getActiveReservationsForDevice(deviceId: String): List<OrderNumberReservationEntity>
}
