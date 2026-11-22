package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentStatus
import kotlinx.serialization.Serializable

@Serializable
@Entity(
  tableName = "orders",
  foreignKeys = [
    ForeignKey(
      entity = ShopEntity::class,
      parentColumns = ["shopId"],
      childColumns = ["shopId"],
      onDelete = ForeignKey.RESTRICT,
    ),
    ForeignKey(
      entity = CustomerEntity::class,
      parentColumns = ["customerId"],
      childColumns = ["customerId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["orderNumber"], unique = true),
    Index(value = ["shopId"]),
    Index(value = ["customerId"]),
    Index(value = ["orderStatus"]),
    Index(value = ["deliveryDate"]),
    Index(value = ["createdAt"]),
  ],
)
data class OrderEntity(
  @PrimaryKey val orderId: String,
  val shopId: String,
  val customerId: String,
  val orderNumber: Int? = null,
  val suitCount: Int,
  val deliveryDate: Long,
  val totalAmountPkr: Long,
  val paidAmountPkr: Long,
  val remainingAmountPkr: Long,
  val orderStatus: OrderStatus,
  val paymentStatus: PaymentStatus,
  val measurementSnapshotJson: String,
  val styleSnapshotJson: String,
  val notes: String = "",
  val createdAt: Long,
  val updatedAt: Long,
  val version: Long = 1L,
  val lastModifiedByDevice: String,
  val isDeleted: Boolean = false,
)
