package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
  tableName = "order_number_reservations",
  indices = [
    Index(value = ["deviceId", "isExhausted"]),
  ],
)
data class OrderNumberReservationEntity(
  @PrimaryKey val reservationId: String,
  val deviceId: String,
  val startNumber: Int,
  val endNumber: Int,
  val nextAvailableNumber: Int,
  val isExhausted: Boolean = false,
  val createdAt: Long,
  val updatedAt: Long,
)
