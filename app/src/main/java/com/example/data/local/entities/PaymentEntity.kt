package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.PaymentMethod
import kotlinx.serialization.Serializable

@Serializable
@Entity(
  tableName = "payments",
  foreignKeys = [
    ForeignKey(
      entity = OrderEntity::class,
      parentColumns = ["orderId"],
      childColumns = ["orderId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["orderId"]),
    Index(value = ["createdAt"]),
  ],
)
data class PaymentEntity(
  @PrimaryKey val paymentId: String,
  val orderId: String,
  val amountPkr: Long,
  val paymentMethod: PaymentMethod,
  val note: String = "",
  val createdAt: Long,
  val deviceId: String,
)
