package com.example.data.local.daos

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.core.model.OrderStatus
import com.example.data.local.entities.OrderEntity
import kotlinx.coroutines.flow.Flow

data class OrderWithCustomer(
  @Embedded val order: OrderEntity,
  val customerName: String,
  val customerPhone: String,
)

data class OrderAnalyticsRow(
  val createdAt: Long,
  val totalAmountPkr: Long,
  val suitCount: Int,
  val orderStatus: OrderStatus,
)

@Dao
interface OrderDao {
  @Query(
    """
    SELECT o.*, c.name AS customerName, c.phone AS customerPhone
    FROM orders o
    INNER JOIN customers c ON o.customerId = c.customerId
    WHERE o.isDeleted = 0
    ORDER BY o.createdAt DESC
    """
  )
  fun observeAllOrdersWithCustomer(): Flow<List<OrderWithCustomer>>

  @Query(
    """
    SELECT o.*, c.name AS customerName, c.phone AS customerPhone
    FROM orders o
    INNER JOIN customers c ON o.customerId = c.customerId
    WHERE o.isDeleted = 0
      AND (:status IS NULL OR o.orderStatus = :status)
      AND (
        :query = ''
        OR LOWER(c.name) LIKE '%' || LOWER(:query) || '%'
        OR (:phoneDigits != '' AND c.normalizedPhone LIKE '%' || :phoneDigits || '%')
        OR CAST(o.orderNumber AS TEXT) LIKE '%' || :query || '%'
      )
    ORDER BY o.createdAt DESC
    """
  )
  fun searchOrdersWithCustomer(
    query: String,
    phoneDigits: String,
    status: OrderStatus?,
  ): Flow<List<OrderWithCustomer>>

  @Query(
    """
    SELECT o.*, c.name AS customerName, c.phone AS customerPhone
    FROM orders o
    INNER JOIN customers c ON o.customerId = c.customerId
    WHERE o.orderId = :orderId
    LIMIT 1
    """
  )
  fun observeOrderWithCustomer(orderId: String): Flow<OrderWithCustomer?>

  @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
  suspend fun getById(orderId: String): OrderEntity?

  @Query(
    """
    SELECT * FROM orders
    WHERE customerId = :customerId AND isDeleted = 0
    ORDER BY createdAt DESC
    """
  )
  fun observeOrdersForCustomer(customerId: String): Flow<List<OrderEntity>>

  @Query(
    """
    SELECT * FROM orders
    WHERE orderNumber IS NULL AND isDeleted = 0
    ORDER BY createdAt ASC
    """
  )
  suspend fun getOrdersPendingOfficialNumber(): List<OrderEntity>

  @Query(
    """
    SELECT COUNT(*) FROM orders
    WHERE orderStatus = :status AND isDeleted = 0
    """
  )
  fun observeCountByStatus(status: OrderStatus): Flow<Int>

  @Query(
    """
    SELECT COALESCE(SUM(remainingAmountPkr), 0) FROM orders
    WHERE orderStatus != 'CANCELLED' AND isDeleted = 0
    """
  )
  fun observeTotalOutstandingPkr(): Flow<Long>

  @Query(
    """
    SELECT COUNT(*) FROM orders
    WHERE orderStatus IN ('PENDING', 'READY')
      AND deliveryDate >= :dayStartMillis
      AND deliveryDate < :dayEndMillis
      AND isDeleted = 0
    """
  )
  fun observeDueInRangeCount(dayStartMillis: Long, dayEndMillis: Long): Flow<Int>

  @Upsert
  suspend fun insert(order: OrderEntity)

  @Update
  suspend fun update(order: OrderEntity)

  @Query("SELECT * FROM orders ORDER BY createdAt ASC")
  suspend fun getAllForBackup(): List<OrderEntity>

  @Query("SELECT * FROM orders WHERE orderNumber = :orderNumber LIMIT 1")
  suspend fun getByOrderNumber(orderNumber: Int): OrderEntity?

  @Query(
    """
    SELECT createdAt, totalAmountPkr, suitCount, orderStatus
    FROM orders
    WHERE isDeleted = 0
      AND orderStatus != 'CANCELLED'
      AND createdAt >= :startMillis
      AND createdAt < :endMillis
    ORDER BY createdAt ASC
    """
  )
  fun observeAnalyticsInRange(startMillis: Long, endMillis: Long): Flow<List<OrderAnalyticsRow>>
}
