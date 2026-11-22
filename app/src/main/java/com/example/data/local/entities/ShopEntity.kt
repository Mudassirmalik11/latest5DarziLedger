package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "shops")
data class ShopEntity(
  @PrimaryKey val shopId: String,
  val shopName: String,
  val contactNumber: String,
  val email: String = "",
  val createdAt: Long,
  val updatedAt: Long,
  val version: Long = 1L,
)
