package com.example.data.sync

import com.example.core.model.SyncEntityType
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.entities.OrderEntity
import com.example.data.local.entities.SyncConflictEntity
import java.util.UUID
import kotlin.math.min
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ConflictResolutionChoice {
  KEEP_LOCAL,
  USE_CLOUD,
}

object ConflictResolver {
  private const val BASE_BACKOFF_MS = 5_000L
  private const val MAX_BACKOFF_MS = 900_000L // 15 minutes

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  fun computeBackoffMillis(retryCount: Int): Long {
    val safeRetry = retryCount.coerceIn(0, 6)
    var delay = BASE_BACKOFF_MS
    repeat(safeRetry) {
      delay = min(delay * 3L, MAX_BACKOFF_MS)
    }
    return delay
  }

  /**
   * Detects concurrent multi-device update conflicts:
   * If this device edited from baseVersion (= localVersion - 1), and the cloud document was
   * modified by a DIFFERENT device with remoteVersion > baseVersion (i.e. remoteVersion >= localVersion),
   * preserve both versions as a SyncConflictEntity instead of silently overwriting.
   */
  fun hasConcurrentUpdateConflict(
    localVersion: Long,
    localDeviceId: String,
    remoteVersion: Long,
    remoteDeviceId: String,
  ): Boolean {
    if (localDeviceId.isBlank() || remoteDeviceId.isBlank()) return false
    if (remoteDeviceId == localDeviceId) return false
    val baseVersion = (localVersion - 1L).coerceAtLeast(0L)
    return remoteVersion > baseVersion
  }

  fun createCustomerConflict(
    local: CustomerEntity,
    remote: CustomerEntity,
    nowMillis: Long = System.currentTimeMillis(),
  ): SyncConflictEntity = SyncConflictEntity(
    conflictId = UUID.randomUUID().toString(),
    entityType = SyncEntityType.CUSTOMER,
    entityId = local.customerId,
    localPayloadJson = json.encodeToString(local),
    remotePayloadJson = json.encodeToString(remote),
    localVersion = local.version,
    remoteVersion = remote.version,
    detectedAt = nowMillis,
  )

  fun createMeasurementConflict(
    local: MeasurementEntity,
    remote: MeasurementEntity,
    nowMillis: Long = System.currentTimeMillis(),
  ): SyncConflictEntity = SyncConflictEntity(
    conflictId = UUID.randomUUID().toString(),
    entityType = SyncEntityType.MEASUREMENT,
    entityId = local.measurementId,
    localPayloadJson = json.encodeToString(local),
    remotePayloadJson = json.encodeToString(remote),
    localVersion = local.version,
    remoteVersion = remote.version,
    detectedAt = nowMillis,
  )

  fun createOrderConflict(
    local: OrderEntity,
    remote: OrderEntity,
    nowMillis: Long = System.currentTimeMillis(),
  ): SyncConflictEntity = SyncConflictEntity(
    conflictId = UUID.randomUUID().toString(),
    entityType = SyncEntityType.ORDER,
    entityId = local.orderId,
    localPayloadJson = json.encodeToString(local),
    remotePayloadJson = json.encodeToString(remote),
    localVersion = local.version,
    remoteVersion = remote.version,
    detectedAt = nowMillis,
  )

  fun describeConflict(conflict: SyncConflictEntity): Pair<String, String> = runCatching {
    when (conflict.entityType) {
      SyncEntityType.CUSTOMER -> {
        val local = json.decodeFromString<CustomerEntity>(conflict.localPayloadJson)
        val remote = json.decodeFromString<CustomerEntity>(conflict.remotePayloadJson)
        "Local: ${local.name} • ${local.phone} (v${local.version})" to
          "Cloud: ${remote.name} • ${remote.phone} (v${remote.version})"
      }
      SyncEntityType.ORDER -> {
        val local = json.decodeFromString<OrderEntity>(conflict.localPayloadJson)
        val remote = json.decodeFromString<OrderEntity>(conflict.remotePayloadJson)
        val num = (local.orderNumber ?: remote.orderNumber)?.let { "#$it" } ?: "Pending #"
        "Local: $num • ${local.orderStatus.displayName} • Paid Rs. ${local.paidAmountPkr} (v${local.version})" to
          "Cloud: $num • ${remote.orderStatus.displayName} • Paid Rs. ${remote.paidAmountPkr} (v${remote.version})"
      }
      SyncEntityType.MEASUREMENT -> {
        val local = json.decodeFromString<MeasurementEntity>(conflict.localPayloadJson)
        val remote = json.decodeFromString<MeasurementEntity>(conflict.remotePayloadJson)
        "Local: ${local.label} • Lambai ${local.kameezLambai}\" (v${local.version})" to
          "Cloud: ${remote.label} • Lambai ${remote.kameezLambai}\" (v${remote.version})"
      }
      else -> {
        "Local version v${conflict.localVersion}" to "Cloud version v${conflict.remoteVersion}"
      }
    }
  }.getOrElse {
    "Local version v${conflict.localVersion}" to "Cloud version v${conflict.remoteVersion}"
  }
}
