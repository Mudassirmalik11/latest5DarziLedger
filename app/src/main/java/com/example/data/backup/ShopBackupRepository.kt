package com.example.data.backup

import androidx.room.withTransaction
import com.example.core.model.SnapshotJson
import com.example.core.model.SyncEntityType
import com.example.core.model.SyncOperation
import com.example.core.preferences.ShopPreferences
import com.example.core.preferences.ShopPreferencesRepository
import com.example.data.local.database.DarziDatabase
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.entities.OrderEntity
import com.example.data.local.entities.PaymentEntity
import com.example.data.local.entities.ShopEntity
import com.example.data.local.entities.SyncOutboxEntity
import com.example.data.local.repository.ShopRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ShopBackupPreferences(
  val shopName: String = "Darzi Ledger",
  val shopContact: String = "",
  val paperWidthMm: Int = 58,
)

@Serializable
data class DarziBackupBundle(
  val schemaVersion: Int = ShopBackupRepository.CURRENT_SCHEMA_VERSION,
  val appId: String = ShopBackupRepository.APP_IDENTIFIER,
  val exportedAtMillis: Long,
  val shop: ShopEntity? = null,
  val preferences: ShopBackupPreferences? = null,
  val customers: List<CustomerEntity> = emptyList(),
  val measurements: List<MeasurementEntity> = emptyList(),
  val orders: List<OrderEntity> = emptyList(),
  val payments: List<PaymentEntity> = emptyList(),
)

data class BackupSummary(
  val exportedAtMillis: Long,
  val shopName: String,
  val customerCount: Int,
  val measurementCount: Int,
  val orderCount: Int,
  val paymentCount: Int,
)

data class RestoreReport(
  val customersRestored: Int,
  val measurementsRestored: Int,
  val ordersRestored: Int,
  val paymentsRestored: Int,
)

class ShopBackupRepository(
  private val database: DarziDatabase,
  private val shopRepository: ShopRepository,
  private val shopPreferencesRepository: ShopPreferencesRepository,
) {
  private val shopDao = database.shopDao()
  private val customerDao = database.customerDao()
  private val measurementDao = database.measurementDao()
  private val orderDao = database.orderDao()
  private val paymentDao = database.paymentDao()
  private val outboxDao = database.syncOutboxDao()

  private val prettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  private val compactJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  suspend fun exportToJson(nowMillis: Long = System.currentTimeMillis()): Result<String> =
    withContext(Dispatchers.IO) {
      try {
        val shop = shopRepository.ensureDefaultShop(nowMillis = nowMillis)
        val prefs = runCatching { shopPreferencesRepository.preferencesFlow.first() }
          .getOrElse { ShopPreferences() }

        val customers = customerDao.getAllForBackup()
        val measurements = measurementDao.getAllForBackup()
        val orders = orderDao.getAllForBackup()
        val payments = paymentDao.getAllForBackup()

        val bundle = DarziBackupBundle(
          schemaVersion = CURRENT_SCHEMA_VERSION,
          appId = APP_IDENTIFIER,
          exportedAtMillis = nowMillis,
          shop = shop,
          preferences = ShopBackupPreferences(
            shopName = prefs.shopName,
            shopContact = prefs.shopContact,
            paperWidthMm = prefs.paperWidthMm,
          ),
          customers = customers,
          measurements = measurements,
          orders = orders,
          payments = payments,
        )
        Result.success(prettyJson.encodeToString(bundle))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(IllegalStateException("Failed to export backup: ${e.message}", e))
      }
    }

  fun inspectBackupSummary(rawJson: String): Result<BackupSummary> =
    parseAndValidateBundle(rawJson).map { bundle ->
      BackupSummary(
        exportedAtMillis = bundle.exportedAtMillis,
        shopName = bundle.shop?.shopName
          ?: bundle.preferences?.shopName
          ?: "Darzi Ledger",
        customerCount = bundle.customers.size,
        measurementCount = bundle.measurements.size,
        orderCount = bundle.orders.size,
        paymentCount = bundle.payments.size,
      )
    }

  fun parseAndValidateBundle(rawJson: String): Result<DarziBackupBundle> {
    if (rawJson.isBlank()) {
      return Result.failure(IllegalArgumentException("Selected backup file is empty."))
    }
    val bundle = try {
      compactJson.decodeFromString<DarziBackupBundle>(rawJson)
    } catch (e: Exception) {
      return Result.failure(IllegalArgumentException("Invalid JSON backup format: ${e.message}", e))
    }

    if (bundle.appId != APP_IDENTIFIER) {
      return Result.failure(IllegalArgumentException("This file is not a Darzi Ledger backup."))
    }
    if (bundle.schemaVersion < 1 || bundle.schemaVersion > CURRENT_SCHEMA_VERSION) {
      return Result.failure(
        IllegalArgumentException("Unsupported backup version (${bundle.schemaVersion}). Please update the app."),
      )
    }

    val seenCustomerIds = HashSet<String>()
    for (customer in bundle.customers) {
      if (customer.customerId.isBlank() || customer.name.isBlank()) {
        return Result.failure(IllegalArgumentException("Backup contains an invalid customer record."))
      }
      if (!seenCustomerIds.add(customer.customerId)) {
        return Result.failure(IllegalArgumentException("Backup contains duplicate customer IDs."))
      }
    }

    val seenMeasurementIds = HashSet<String>()
    for (meas in bundle.measurements) {
      if (meas.measurementId.isBlank() || meas.customerId.isBlank()) {
        return Result.failure(IllegalArgumentException("Backup contains an invalid measurement record."))
      }
      if (!seenMeasurementIds.add(meas.measurementId)) {
        return Result.failure(IllegalArgumentException("Backup contains duplicate measurement IDs."))
      }
    }

    val seenOrderIds = HashSet<String>()
    val seenOrderNumbers = HashSet<Int>()
    for (order in bundle.orders) {
      if (order.orderId.isBlank() || order.customerId.isBlank()) {
        return Result.failure(IllegalArgumentException("Backup contains an invalid order record."))
      }
      if (!seenOrderIds.add(order.orderId)) {
        return Result.failure(IllegalArgumentException("Backup contains duplicate order IDs."))
      }
      val orderNum = order.orderNumber
      if (orderNum != null && !seenOrderNumbers.add(orderNum)) {
        return Result.failure(IllegalArgumentException("Backup contains duplicate order number #$orderNum."))
      }
      if (order.suitCount <= 0 || order.totalAmountPkr < 0L || order.paidAmountPkr < 0L || order.remainingAmountPkr < 0L) {
        return Result.failure(IllegalArgumentException("Backup contains invalid PKR or suit values in order."))
      }
      if (runCatching { SnapshotJson.decodeMeasurement(order.measurementSnapshotJson) }.isFailure ||
        runCatching { SnapshotJson.decodeStyle(order.styleSnapshotJson) }.isFailure
      ) {
        return Result.failure(IllegalArgumentException("Backup contains corrupted measurement snapshot in order."))
      }
    }

    val seenPaymentIds = HashSet<String>()
    for (payment in bundle.payments) {
      if (payment.paymentId.isBlank() || payment.orderId.isBlank() || payment.amountPkr <= 0L) {
        return Result.failure(IllegalArgumentException("Backup contains an invalid payment record."))
      }
      if (!seenPaymentIds.add(payment.paymentId)) {
        return Result.failure(IllegalArgumentException("Backup contains duplicate payment IDs."))
      }
    }

    return Result.success(bundle)
  }

  suspend fun restoreFromJson(
    rawJson: String,
    enqueueSync: Boolean = true,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<RestoreReport> = withContext(Dispatchers.IO) {
    val parsed = parseAndValidateBundle(rawJson)
    val bundle = parsed.getOrElse { return@withContext Result.failure(it) }

    val customerIdsInBundle = bundle.customers.mapTo(HashSet()) { it.customerId }
    val orderIdsInBundle = bundle.orders.mapTo(HashSet()) { it.orderId }

    try {
      database.withTransaction {
        // Verify referential integrity BEFORE modifying anything inside transaction
        for (meas in bundle.measurements) {
          if (meas.customerId !in customerIdsInBundle && customerDao.getById(meas.customerId) == null) {
            throw IllegalArgumentException(
              "Restore aborted: measurement ${meas.measurementId} references missing customer ${meas.customerId}.",
            )
          }
        }
        for (order in bundle.orders) {
          if (order.customerId !in customerIdsInBundle && customerDao.getById(order.customerId) == null) {
            throw IllegalArgumentException(
              "Restore aborted: order ${order.orderId} references missing customer ${order.customerId}.",
            )
          }
        }
        for (payment in bundle.payments) {
          if (payment.orderId !in orderIdsInBundle && orderDao.getById(payment.orderId) == null) {
            throw IllegalArgumentException(
              "Restore aborted: payment ${payment.paymentId} references missing order ${payment.orderId}.",
            )
          }
        }

        // Resolve canonical primary shop row
        val currentPrimary = shopDao.getPrimaryShop()
        val resolvedShopId = currentPrimary?.shopId ?: ShopRepository.DEFAULT_SHOP_ID
        val shopToSave = (bundle.shop ?: currentPrimary ?: ShopEntity(
          shopId = resolvedShopId,
          shopName = bundle.preferences?.shopName ?: "Darzi Ledger",
          contactNumber = bundle.preferences?.shopContact ?: "",
          email = "",
          createdAt = nowMillis,
          updatedAt = nowMillis,
          version = 1L,
        )).copy(
          shopId = resolvedShopId,
          updatedAt = maxOf(bundle.shop?.updatedAt ?: nowMillis, currentPrimary?.updatedAt ?: 0L),
          version = maxOf(bundle.shop?.version ?: 1L, currentPrimary?.version ?: 1L),
        )
        shopDao.upsert(shopToSave)
        if (enqueueSync && outboxDao.countActiveForEntity(SyncEntityType.SHOP, shopToSave.shopId) == 0) {
          outboxDao.enqueue(
            SyncOutboxEntity(
              eventId = UUID.randomUUID().toString(),
              entityType = SyncEntityType.SHOP,
              entityId = shopToSave.shopId,
              operation = SyncOperation.UPDATE,
              payloadJson = compactJson.encodeToString(shopToSave),
              createdAt = nowMillis,
            ),
          )
        }

        // Customers
        bundle.customers.forEach { rawCustomer ->
          val customer = rawCustomer.copy(shopId = resolvedShopId)
          val existed = customerDao.getById(customer.customerId) != null
          customerDao.insert(customer)
          if (enqueueSync && outboxDao.countActiveForEntity(SyncEntityType.CUSTOMER, customer.customerId) == 0) {
            outboxDao.enqueue(
              SyncOutboxEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = SyncEntityType.CUSTOMER,
                entityId = customer.customerId,
                operation = if (existed) SyncOperation.UPDATE else SyncOperation.CREATE,
                payloadJson = compactJson.encodeToString(customer),
                createdAt = nowMillis,
              ),
            )
          }
        }

        // Measurements
        bundle.measurements.forEach { meas ->
          val existed = measurementDao.getById(meas.measurementId) != null
          measurementDao.insert(meas)
          if (meas.isCurrent && !meas.isDeleted) {
            measurementDao.clearCurrentFlagExcept(meas.customerId, meas.measurementId, nowMillis)
          }
          if (enqueueSync && outboxDao.countActiveForEntity(SyncEntityType.MEASUREMENT, meas.measurementId) == 0) {
            outboxDao.enqueue(
              SyncOutboxEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = SyncEntityType.MEASUREMENT,
                entityId = meas.measurementId,
                operation = if (existed) SyncOperation.UPDATE else SyncOperation.CREATE,
                payloadJson = compactJson.encodeToString(meas),
                createdAt = nowMillis,
              ),
            )
          }
        }

        // Orders (protect unique orderNumber index against cross-order collisions)
        bundle.orders.forEach { rawOrder ->
          val num = rawOrder.orderNumber
          val safeOrder = if (num != null) {
            val holder = orderDao.getByOrderNumber(num)
            if (holder != null && holder.orderId != rawOrder.orderId) {
              rawOrder.copy(shopId = resolvedShopId, orderNumber = null)
            } else {
              rawOrder.copy(shopId = resolvedShopId)
            }
          } else {
            rawOrder.copy(shopId = resolvedShopId)
          }
          val existed = orderDao.getById(safeOrder.orderId) != null
          orderDao.insert(safeOrder)
          if (enqueueSync && outboxDao.countActiveForEntity(SyncEntityType.ORDER, safeOrder.orderId) == 0) {
            outboxDao.enqueue(
              SyncOutboxEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = SyncEntityType.ORDER,
                entityId = safeOrder.orderId,
                operation = if (existed) SyncOperation.UPDATE else SyncOperation.CREATE,
                payloadJson = compactJson.encodeToString(safeOrder),
                createdAt = nowMillis,
              ),
            )
          }
        }

        // Payments
        bundle.payments.forEach { payment ->
          val existed = paymentDao.getById(payment.paymentId) != null
          paymentDao.upsertForRestore(payment)
          if (enqueueSync && !existed && outboxDao.countActiveForEntity(SyncEntityType.PAYMENT, payment.paymentId) == 0) {
            outboxDao.enqueue(
              SyncOutboxEntity(
                eventId = UUID.randomUUID().toString(),
                entityType = SyncEntityType.PAYMENT,
                entityId = payment.paymentId,
                operation = SyncOperation.CREATE,
                payloadJson = compactJson.encodeToString(payment),
                createdAt = nowMillis,
              ),
            )
          }
        }
      }

      bundle.preferences?.let { prefs ->
        runCatching {
          shopPreferencesRepository.updateShopProfile(prefs.shopName, prefs.shopContact)
          shopPreferencesRepository.updatePaperWidth(prefs.paperWidthMm)
        }
      }

      Result.success(
        RestoreReport(
          customersRestored = bundle.customers.size,
          measurementsRestored = bundle.measurements.size,
          ordersRestored = bundle.orders.size,
          paymentsRestored = bundle.payments.size,
        ),
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(
        IllegalStateException(e.message ?: "Restore failed; existing shop data was preserved.", e),
      )
    }
  }

  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
    const val APP_IDENTIFIER = "darzi_ledger"
  }
}
