package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
  tableName = "customers",
  foreignKeys = [
    ForeignKey(
      entity = ShopEntity::class,
      parentColumns = ["shopId"],
      childColumns = ["shopId"],
      onDelete = ForeignKey.RESTRICT,
    ),
  ],
  indices = [
    Index(value = ["shopId"]),
    Index(value = ["name"]),
    Index(value = ["normalizedPhone"]),
    Index(value = ["updatedAt"]),
  ],
)
data class CustomerEntity(
  @PrimaryKey val customerId: String,
  val shopId: String,
  val name: String,
  val phone: String,
  val normalizedPhone: String,
  val notes: String = "",
  val customId: String = "",
  val createdAt: Long,
  val updatedAt: Long,
  val version: Long = 1L,
  val lastModifiedByDevice: String,
  val isDeleted: Boolean = false,
  val deletedAt: Long? = null,
)
