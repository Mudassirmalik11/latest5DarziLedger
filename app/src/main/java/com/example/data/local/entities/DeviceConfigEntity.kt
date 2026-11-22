package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.DeviceRegistrationStatus
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "device_config")
data class DeviceConfigEntity(
  @PrimaryKey val deviceId: String,
  val shopId: String,
  val userUid: String = "",
  val userRole: String = "OWNER",
  val deviceName: String,
  val deviceRole: String = "PHONE",
  val registrationStatus: DeviceRegistrationStatus = DeviceRegistrationStatus.UNREGISTERED,
  val registeredAt: Long? = null,
  val updatedAt: Long,
)
