package com.example.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.core.model.PaymentMethod
import com.example.data.local.entities.PaymentEntity
import kotlinx.coroutines.flow.Flow

data class PaymentAnalyticsRow(
  val createdAt: Long,
  val amountPkr: Long,
)

data class CustomerPaymentEntry(
  val paymentId: String,
  val orderId: String,
  val orderNumber: Int?,
  val amountPkr: Long,
  val paymentMethod: PaymentMethod,
  val note: String,
  val createdAt: Long,
)

@Dao
interface PaymentDao {
  @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY createdAt ASC")
  fun observePaymentsForOrder(orderId: String): Flow<List<PaymentEntity>>

  @Query(
    """
    SELECT p.paymentId, p.orderId, o.orderNumber, p.amountPkr, p.paymentMethod, p.note, p.createdAt
    FROM payments p
    INNER JOIN orders o ON p.orderId = o.orderId
    WHERE o.customerId = :customerId AND o.isDeleted = 0
    ORDER BY p.createdAt DESC
    """
  )
  fun observePaymentsForCustomer(customerId: String): Flow<List<CustomerPaymentEntry>>

  @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY createdAt ASC")
  suspend fun getPaymentsForOrder(orderId: String): List<PaymentEntity>

  @Query("SELECT COALESCE(SUM(amountPkr), 0) FROM payments WHERE orderId = :orderId")
  suspend fun getTotalPaidForOrder(orderId: String): Long

  @Query(
    """
    SELECT COALESCE(SUM(amountPkr), 0) FROM payments
    WHERE createdAt >= :dayStartMillis AND createdAt < :dayEndMillis
    """
  )
  fun observeCollectedInRangePkr(dayStartMillis: Long, dayEndMillis: Long): Flow<Long>

  @Query("SELECT * FROM payments WHERE paymentId = :paymentId LIMIT 1")
  suspend fun getById(paymentId: String): PaymentEntity?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(payment: PaymentEntity)

  @Upsert
  suspend fun upsertForRestore(payment: PaymentEntity)

  @Query("SELECT * FROM payments ORDER BY createdAt ASC")
  suspend fun getAllForBackup(): List<PaymentEntity>

  @Query(
    """
    SELECT createdAt, amountPkr
    FROM payments
    WHERE createdAt >= :startMillis AND createdAt < :endMillis
    ORDER BY createdAt ASC
    """
  )
  fun observeAnalyticsInRange(startMillis: Long, endMillis: Long): Flow<List<PaymentAnalyticsRow>>
}
