package com.example.data.local.repository

import androidx.room.withTransaction
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PaymentStatus
import com.example.core.model.PhoneNumberUtils
import com.example.core.model.SnapshotJson
import com.example.core.model.StyleSnapshot
import com.example.core.model.SyncEntityType
import com.example.core.model.SyncOperation
import com.example.core.model.TailorValidators
import com.example.data.local.daos.CustomerPaymentEntry
import com.example.data.local.daos.OrderAnalyticsRow
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.daos.PaymentAnalyticsRow
import com.example.data.local.database.DarziDatabase
import com.example.data.local.entities.OrderEntity
import com.example.data.local.entities.PaymentEntity
import com.example.data.local.entities.SyncOutboxEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OrderRepository(
  private val database: DarziDatabase,
  private val shopRepository: ShopRepository,
) {
  private val orderDao = database.orderDao()
  private val paymentDao = database.paymentDao()
  private val customerDao = database.customerDao()
  private val deviceSyncDao = database.deviceSyncDao()
  private val outboxDao = database.syncOutboxDao()
  private val json = Json { encodeDefaults = true }

  fun observeAllOrders(): Flow<List<OrderWithCustomer>> =
    orderDao.observeAllOrdersWithCustomer()

  fun searchOrders(query: String, statusFilter: OrderStatus?): Flow<List<OrderWithCustomer>> {
    val trimmed = query.trim()
    val phoneDigits = if (trimmed.any { it.isDigit() }) {
      PhoneNumberUtils.normalizePakistaniPhone(trimmed)
    } else {
      ""
    }
    return orderDao.searchOrdersWithCustomer(
      query = trimmed,
      phoneDigits = phoneDigits,
      status = statusFilter,
    )
  }

  fun observeOrderDetail(orderId: String): Flow<OrderWithCustomer?> =
    orderDao.observeOrderWithCustomer(orderId)

  fun observeOrdersForCustomer(customerId: String): Flow<List<OrderEntity>> =
    orderDao.observeOrdersForCustomer(customerId)

  fun observePaymentsForOrder(orderId: String): Flow<List<PaymentEntity>> =
    paymentDao.observePaymentsForOrder(orderId)

  fun observePaymentsForCustomer(customerId: String): Flow<List<CustomerPaymentEntry>> =
    paymentDao.observePaymentsForCustomer(customerId)

  fun observeCountByStatus(status: OrderStatus): Flow<Int> =
    orderDao.observeCountByStatus(status)

  fun observeTotalOutstandingPkr(): Flow<Long> =
    orderDao.observeTotalOutstandingPkr()

  fun observeDueInRangeCount(dayStartMillis: Long, dayEndMillis: Long): Flow<Int> =
    orderDao.observeDueInRangeCount(dayStartMillis, dayEndMillis)

  fun observeCollectedInRangePkr(dayStartMillis: Long, dayEndMillis: Long): Flow<Long> =
    paymentDao.observeCollectedInRangePkr(dayStartMillis, dayEndMillis)

  fun observeOrderAnalyticsInRange(startMillis: Long, endMillis: Long): Flow<List<OrderAnalyticsRow>> =
    orderDao.observeAnalyticsInRange(startMillis, endMillis)

  fun observePaymentAnalyticsInRange(startMillis: Long, endMillis: Long): Flow<List<PaymentAnalyticsRow>> =
    paymentDao.observeAnalyticsInRange(startMillis, endMillis)

  suspend fun createOrder(
    customerId: String,
    suitCount: Int,
    deliveryDateMillis: Long,
    totalAmountPkr: Long,
    initialAdvancePkr: Long,
    initialPaymentMethod: PaymentMethod = PaymentMethod.CASH,
    measurementSnapshot: MeasurementSnapshot,
    styleSnapshot: StyleSnapshot,
    notes: String = "",
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<OrderEntity> {
    val orderError = TailorValidators.validateNewOrder(
      suitCount = suitCount,
      totalAmountPkr = totalAmountPkr,
      initialAdvancePkr = initialAdvancePkr,
    )
    if (orderError != null) {
      return Result.failure(IllegalArgumentException(orderError))
    }

    val measurementError = TailorValidators.validateMeasurement(measurementSnapshot)
    if (measurementError != null) {
      return Result.failure(IllegalArgumentException(measurementError))
    }

    return runCatching {
      database.withTransaction {
        customerDao.getById(customerId)
          ?: throw IllegalStateException("Customer not found.")
        val shop = shopRepository.ensureDefaultShop(nowMillis = nowMillis)

        val assignedOrderNumber = allocateNextLocalOrderNumber(deviceId, nowMillis)
        val remaining = totalAmountPkr - initialAdvancePkr
        val paymentStatus = computePaymentStatus(totalAmountPkr, initialAdvancePkr)

        val order = OrderEntity(
          orderId = UUID.randomUUID().toString(),
          shopId = shop.shopId,
          customerId = customerId,
          orderNumber = assignedOrderNumber,
          suitCount = suitCount,
          deliveryDate = deliveryDateMillis,
          totalAmountPkr = totalAmountPkr,
          paidAmountPkr = initialAdvancePkr,
          remainingAmountPkr = remaining,
          orderStatus = OrderStatus.PENDING,
          paymentStatus = paymentStatus,
          measurementSnapshotJson = SnapshotJson.encodeMeasurement(measurementSnapshot),
          styleSnapshotJson = SnapshotJson.encodeStyle(styleSnapshot),
          notes = notes.trim(),
          createdAt = nowMillis,
          updatedAt = nowMillis,
          version = 1L,
          lastModifiedByDevice = deviceId,
        )
        orderDao.insert(order)
        outboxDao.enqueue(
          SyncOutboxEntity(
            eventId = UUID.randomUUID().toString(),
            entityType = SyncEntityType.ORDER,
            entityId = order.orderId,
            operation = SyncOperation.CREATE,
            payloadJson = json.encodeToString(order),
            createdAt = nowMillis,
          ),
        )

        if (initialAdvancePkr > 0L) {
          val advancePayment = PaymentEntity(
            paymentId = UUID.randomUUID().toString(),
            orderId = order.orderId,
            amountPkr = initialAdvancePkr,
            paymentMethod = initialPaymentMethod,
            note = "Advance payment",
            createdAt = nowMillis,
            deviceId = deviceId,
          )
          paymentDao.insert(advancePayment)
          outboxDao.enqueue(
            SyncOutboxEntity(
              eventId = UUID.randomUUID().toString(),
              entityType = SyncEntityType.PAYMENT,
              entityId = advancePayment.paymentId,
              operation = SyncOperation.CREATE,
              payloadJson = json.encodeToString(advancePayment),
              createdAt = nowMillis,
            ),
          )
        }
        order
      }
    }
  }

  suspend fun recordPayment(
    orderId: String,
    amountPkr: Long,
    paymentMethod: PaymentMethod = PaymentMethod.CASH,
    note: String = "",
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<OrderEntity> = runCatching {
    database.withTransaction {
      val order = orderDao.getById(orderId)
        ?: throw IllegalStateException("Order not found.")
      val error = TailorValidators.validatePayment(
        amountPkr = amountPkr,
        remainingBalancePkr = order.remainingAmountPkr,
      )
      if (error != null) {
        throw IllegalArgumentException(error)
      }

      val payment = PaymentEntity(
        paymentId = UUID.randomUUID().toString(),
        orderId = orderId,
        amountPkr = amountPkr,
        paymentMethod = paymentMethod,
        note = note.trim(),
        createdAt = nowMillis,
        deviceId = deviceId,
      )
      paymentDao.insert(payment)

      val newPaidTotal = paymentDao.getTotalPaidForOrder(orderId)
      val newRemaining = (order.totalAmountPkr - newPaidTotal).coerceAtLeast(0L)
      val updatedOrder = order.copy(
        paidAmountPkr = newPaidTotal,
        remainingAmountPkr = newRemaining,
        paymentStatus = computePaymentStatus(order.totalAmountPkr, newPaidTotal),
        updatedAt = nowMillis,
        version = order.version + 1L,
        lastModifiedByDevice = deviceId,
      )
      orderDao.update(updatedOrder)

      outboxDao.enqueue(
        SyncOutboxEntity(
          eventId = UUID.randomUUID().toString(),
          entityType = SyncEntityType.PAYMENT,
          entityId = payment.paymentId,
          operation = SyncOperation.CREATE,
          payloadJson = json.encodeToString(payment),
          createdAt = nowMillis,
        ),
      )
      outboxDao.enqueue(
        SyncOutboxEntity(
          eventId = UUID.randomUUID().toString(),
          entityType = SyncEntityType.ORDER,
          entityId = updatedOrder.orderId,
          operation = SyncOperation.UPDATE,
          payloadJson = json.encodeToString(updatedOrder),
          createdAt = nowMillis,
        ),
      )
      updatedOrder
    }
  }

  suspend fun recordRemainingBalancePayment(
    orderId: String,
    paymentMethod: PaymentMethod = PaymentMethod.CASH,
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<OrderEntity> {
    val order = orderDao.getById(orderId)
      ?: return Result.failure(IllegalStateException("Order not found."))
    if (order.remainingAmountPkr <= 0L) {
      return Result.failure(IllegalStateException("Order is already fully paid."))
    }
    return recordPayment(
      orderId = orderId,
      amountPkr = order.remainingAmountPkr,
      paymentMethod = paymentMethod,
      note = "Final balance settlement",
      deviceId = deviceId,
      nowMillis = nowMillis,
    )
  }

  suspend fun updateOrderStatus(
    orderId: String,
    targetStatus: OrderStatus,
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<OrderEntity> = runCatching {
    database.withTransaction {
      val order = orderDao.getById(orderId)
        ?: throw IllegalStateException("Order not found.")
      if (!order.orderStatus.canTransitionTo(targetStatus)) {
        throw IllegalStateException(
          "Cannot transition order from ${order.orderStatus.displayName} to ${targetStatus.displayName}."
        )
      }
      if (order.orderStatus == targetStatus) return@withTransaction order

      val updated = order.copy(
        orderStatus = targetStatus,
        updatedAt = nowMillis,
        version = order.version + 1L,
        lastModifiedByDevice = deviceId,
      )
      orderDao.update(updated)
      outboxDao.enqueue(
        SyncOutboxEntity(
          eventId = UUID.randomUUID().toString(),
          entityType = SyncEntityType.ORDER,
          entityId = updated.orderId,
          operation = SyncOperation.UPDATE,
          payloadJson = json.encodeToString(updated),
          createdAt = nowMillis,
        ),
      )
      updated
    }
  }

  suspend fun softDeleteOrder(
    orderId: String,
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> = runCatching {
    database.withTransaction {
      val order = orderDao.getById(orderId)
        ?: throw IllegalStateException("Order not found.")
      if (order.isDeleted) return@withTransaction

      val updated = order.copy(
        isDeleted = true,
        updatedAt = nowMillis,
        version = order.version + 1L,
        lastModifiedByDevice = deviceId,
      )
      orderDao.update(updated)
      outboxDao.enqueue(
        SyncOutboxEntity(
          eventId = UUID.randomUUID().toString(),
          entityType = SyncEntityType.ORDER,
          entityId = updated.orderId,
          operation = SyncOperation.DELETE,
          payloadJson = json.encodeToString(updated),
          createdAt = nowMillis,
        ),
      )
    }
  }

  suspend fun assignPendingOrderNumbers(
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Int = database.withTransaction {
    var assignedCount = 0
    val pendingOrders = orderDao.getOrdersPendingOfficialNumber()
    for (order in pendingOrders) {
      val number = allocateNextLocalOrderNumber(deviceId, nowMillis) ?: break
      val updated = order.copy(
        orderNumber = number,
        updatedAt = nowMillis,
        version = order.version + 1L,
        lastModifiedByDevice = deviceId,
      )
      orderDao.update(updated)
      outboxDao.enqueue(
        SyncOutboxEntity(
          eventId = UUID.randomUUID().toString(),
          entityType = SyncEntityType.ORDER,
          entityId = updated.orderId,
          operation = SyncOperation.UPDATE,
          payloadJson = json.encodeToString(updated),
          createdAt = nowMillis,
        ),
      )
      assignedCount += 1
    }
    assignedCount
  }

  private suspend fun allocateNextLocalOrderNumber(
    deviceId: String,
    nowMillis: Long,
  ): Int? {
    val reservation = deviceSyncDao.getActiveReservation(deviceId) ?: return null
    val assigned = reservation.nextAvailableNumber
    if (assigned > reservation.endNumber) {
      deviceSyncDao.updateReservation(
        reservation.copy(isExhausted = true, updatedAt = nowMillis)
      )
      return null
    }
    val next = assigned + 1
    deviceSyncDao.updateReservation(
      reservation.copy(
        nextAvailableNumber = next,
        isExhausted = next > reservation.endNumber,
        updatedAt = nowMillis,
      )
    )
    return assigned
  }

  private fun computePaymentStatus(totalAmountPkr: Long, paidAmountPkr: Long): PaymentStatus =
    when {
      totalAmountPkr == 0L || paidAmountPkr >= totalAmountPkr -> PaymentStatus.PAID
      paidAmountPkr > 0L -> PaymentStatus.PARTIAL
      else -> PaymentStatus.UNPAID
    }
}
