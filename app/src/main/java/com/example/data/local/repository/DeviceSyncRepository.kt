package com.example.data.local.repository

import androidx.room.withTransaction
import com.example.core.model.DeviceRegistrationStatus
import com.example.core.model.ShopUserRole
import com.example.core.model.SyncState
import com.example.core.model.SyncStatus
import com.example.data.local.database.DarziDatabase
import com.example.data.local.entities.DeviceConfigEntity
import com.example.data.local.entities.OrderNumberReservationEntity
import com.example.data.local.entities.PrinterConfigEntity
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.local.entities.SyncMetadataEntity
import com.example.data.local.entities.SyncOutboxEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class DeviceSyncRepository(
  private val database: DarziDatabase,
) {
  private val deviceSyncDao = database.deviceSyncDao()
  private val outboxDao = database.syncOutboxDao()

  fun observeDeviceConfig(): Flow<DeviceConfigEntity?> = deviceSyncDao.observeDeviceConfig()

  fun observePendingOutboxCount(): Flow<Int> = outboxDao.observePendingCount()

  fun observeDefaultPrinter(): Flow<PrinterConfigEntity?> = deviceSyncDao.observeDefaultPrinter()

  fun observeUnresolvedConflicts(): Flow<List<SyncConflictEntity>> =
    deviceSyncDao.observeUnresolvedConflicts()

  fun observeUnresolvedConflictCount(): Flow<Int> =
    deviceSyncDao.observeUnresolvedConflictCount()

  fun observeSyncMetadata(deviceId: String): Flow<SyncMetadataEntity?> =
    deviceSyncDao.observeSyncMetadata(deviceId)

  fun observeReservations(deviceId: String): Flow<List<OrderNumberReservationEntity>> =
    deviceSyncDao.observeReservations(deviceId)

  suspend fun getDeviceConfigForUser(userUid: String): DeviceConfigEntity? {
    if (userUid.isBlank()) return null
    return deviceSyncDao.getDeviceConfigForUser(userUid)
  }

  suspend fun ensureLocalDeviceConfig(
    defaultName: String = "Shop Android Device",
    deviceRole: String = "PHONE",
    nowMillis: Long = System.currentTimeMillis(),
  ): DeviceConfigEntity {
    val existing = deviceSyncDao.getDeviceConfig()
    if (existing != null) return existing
    val created = DeviceConfigEntity(
      deviceId = UUID.randomUUID().toString(),
      shopId = ShopRepository.DEFAULT_SHOP_ID,
      userUid = "",
      userRole = ShopUserRole.OWNER.name,
      deviceName = defaultName,
      deviceRole = deviceRole,
      registrationStatus = DeviceRegistrationStatus.UNREGISTERED,
      updatedAt = nowMillis,
    )
    deviceSyncDao.upsertDeviceConfig(created)
    deviceSyncDao.upsertSyncMetadata(
      SyncMetadataEntity(
        deviceId = created.deviceId,
        syncStatus = SyncState.IDLE,
      ),
    )
    return created
  }

  suspend fun ensureLocalDeviceConfigForUser(
    userUid: String,
    shopId: String = ShopRepository.DEFAULT_SHOP_ID,
    userRole: ShopUserRole? = null,
    defaultName: String = "Shop Android Device",
    deviceRole: String = "PHONE",
    nowMillis: Long = System.currentTimeMillis(),
  ): DeviceConfigEntity {
    if (userUid.isBlank()) {
      return ensureLocalDeviceConfig(defaultName, deviceRole, nowMillis)
    }
    val latestTimestamp = maxOf(nowMillis, (deviceSyncDao.getDeviceConfig()?.updatedAt ?: 0L) + 1L)

    val existingForUser = deviceSyncDao.getDeviceConfigForUser(userUid)
    if (existingForUser != null) {
      val resolvedShopId = shopId.takeIf {
        it.isNotBlank() && it != ShopRepository.DEFAULT_SHOP_ID
      } ?: existingForUser.shopId
      val resolvedRole = userRole?.name ?: existingForUser.userRole.ifBlank { ShopUserRole.OWNER.name }
      val activated = existingForUser.copy(
        shopId = resolvedShopId,
        userRole = resolvedRole,
        updatedAt = latestTimestamp,
      )
      deviceSyncDao.upsertDeviceConfig(activated)
      if (deviceSyncDao.getSyncMetadata(activated.deviceId) == null) {
        deviceSyncDao.upsertSyncMetadata(
          SyncMetadataEntity(
            deviceId = activated.deviceId,
            syncStatus = SyncState.IDLE,
          ),
        )
      }
      return activated
    }

    val unclaimed = deviceSyncDao.getUnclaimedDeviceConfig()
    if (unclaimed != null) {
      val resolvedShopId = shopId.takeIf {
        it.isNotBlank() && it != ShopRepository.DEFAULT_SHOP_ID
      } ?: unclaimed.shopId
      val resolvedRole = userRole?.name ?: unclaimed.userRole.ifBlank { ShopUserRole.OWNER.name }
      val claimed = unclaimed.copy(
        userUid = userUid,
        shopId = resolvedShopId,
        userRole = resolvedRole,
        updatedAt = latestTimestamp,
      )
      deviceSyncDao.upsertDeviceConfig(claimed)
      return claimed
    }

    val existingAny = deviceSyncDao.getDeviceConfig()
    val installSuffix = existingAny?.deviceId
      ?.substringAfter("__", missingDelimiterValue = "")
      ?.takeIf { it.isNotBlank() }
      ?: existingAny?.deviceId?.takeLast(12)?.replace(Regex("[^a-zA-Z0-9_-]"), "")?.ifBlank { null }
      ?: UUID.randomUUID().toString().replace("-", "").take(12)
    val safeUidPrefix = userUid.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24).ifBlank { "user" }
    val newDeviceId = "${safeUidPrefix}__${installSuffix}".take(128)
    val created = DeviceConfigEntity(
      deviceId = newDeviceId,
      shopId = shopId.ifBlank { ShopRepository.DEFAULT_SHOP_ID },
      userUid = userUid,
      userRole = (userRole ?: ShopUserRole.OWNER).name,
      deviceName = defaultName,
      deviceRole = deviceRole,
      registrationStatus = DeviceRegistrationStatus.UNREGISTERED,
      updatedAt = latestTimestamp,
    )
    deviceSyncDao.upsertDeviceConfig(created)
    deviceSyncDao.upsertSyncMetadata(
      SyncMetadataEntity(
        deviceId = created.deviceId,
        syncStatus = SyncState.IDLE,
      ),
    )
    return created
  }

  suspend fun clearShopDataIfSwitchingShops(newShopId: String): Boolean {
    if (newShopId.isBlank() || newShopId == ShopRepository.DEFAULT_SHOP_ID) return false
    val previousRealShopId = deviceSyncDao.getLatestRealShopId()
    if (previousRealShopId.isNullOrBlank() || previousRealShopId == newShopId) {
      return false
    }
    database.withTransaction {
      deviceSyncDao.clearPayments()
      deviceSyncDao.clearOrders()
      deviceSyncDao.clearMeasurements()
      deviceSyncDao.clearCustomers()
      deviceSyncDao.clearOutbox()
      deviceSyncDao.clearConflicts()
      deviceSyncDao.clearReservations()
      deviceSyncDao.clearShops()
    }
    return true
  }

  suspend fun updateDeviceAuthorization(
    deviceId: String,
    shopId: String,
    deviceName: String,
    deviceRole: String,
    status: DeviceRegistrationStatus,
    registeredAtMillis: Long? = null,
    nowMillis: Long = System.currentTimeMillis(),
    userUid: String = "",
    userRole: String = ShopUserRole.OWNER.name,
  ): DeviceConfigEntity {
    val existing = if (userUid.isNotBlank()) {
      deviceSyncDao.getDeviceConfigForUser(userUid)
        ?: deviceSyncDao.getDeviceConfig()?.takeIf { it.deviceId == deviceId || it.userUid.isBlank() }
    } else {
      deviceSyncDao.getDeviceConfig()
    }
    val latestTimestamp = maxOf(nowMillis, (deviceSyncDao.getDeviceConfig()?.updatedAt ?: 0L) + 1L)
    val resolvedUserUid = userUid.ifBlank { existing?.userUid.orEmpty() }
    val resolvedUserRole = userRole.ifBlank { existing?.userRole ?: ShopUserRole.OWNER.name }
    val updated = (existing ?: DeviceConfigEntity(
      deviceId = deviceId,
      shopId = shopId,
      userUid = resolvedUserUid,
      userRole = resolvedUserRole,
      deviceName = deviceName,
      deviceRole = deviceRole,
      registrationStatus = status,
      registeredAt = registeredAtMillis,
      updatedAt = latestTimestamp,
    )).copy(
      shopId = shopId,
      userUid = resolvedUserUid,
      userRole = resolvedUserRole,
      deviceName = deviceName,
      deviceRole = deviceRole,
      registrationStatus = status,
      registeredAt = registeredAtMillis ?: existing?.registeredAt,
      updatedAt = latestTimestamp,
    )
    deviceSyncDao.upsertDeviceConfig(updated)
    return updated
  }

  suspend fun addOrderNumberReservation(
    deviceId: String,
    startNumber: Int,
    endNumber: Int,
    reservationId: String = UUID.randomUUID().toString(),
    nowMillis: Long = System.currentTimeMillis(),
  ): OrderNumberReservationEntity {
    require(startNumber >= 1001) { "Order numbers must start at #1001 or above." }
    require(endNumber >= startNumber) { "End number must be >= start number." }
    val entity = OrderNumberReservationEntity(
      reservationId = reservationId,
      deviceId = deviceId,
      startNumber = startNumber,
      endNumber = endNumber,
      nextAvailableNumber = startNumber,
      isExhausted = false,
      createdAt = nowMillis,
      updatedAt = nowMillis,
    )
    deviceSyncDao.upsertReservation(entity)
    return entity
  }

  suspend fun savePrinterConfig(
    printerAddress: String,
    printerName: String,
    paperWidthMm: Int,
    connectionSettingsJson: String = "{}",
    nowMillis: Long = System.currentTimeMillis(),
  ) {
    deviceSyncDao.upsertPrinterConfig(
      PrinterConfigEntity(
        printerId = "default",
        printerAddress = printerAddress,
        printerName = printerName,
        paperWidthMm = if (paperWidthMm == 80) 80 else 58,
        connectionSettingsJson = connectionSettingsJson,
        updatedAt = nowMillis,
      ),
    )
  }

  suspend fun getReadyOutboxEvents(nowMillis: Long = System.currentTimeMillis()): List<SyncOutboxEntity> =
    outboxDao.getReadyEvents(nowMillis)

  suspend fun markOutboxSynced(eventId: String) =
    outboxDao.updateStatus(eventId, SyncStatus.SYNCED, null)

  suspend fun markOutboxRetry(eventId: String, nextRetryAt: Long, error: String) =
    outboxDao.markRetry(eventId, nextRetryAt, error)
}
