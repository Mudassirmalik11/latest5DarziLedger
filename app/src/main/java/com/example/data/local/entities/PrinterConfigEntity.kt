package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "printer_config")
data class PrinterConfigEntity(
  @PrimaryKey val printerId: String = "default",
  val printerAddress: String,
  val printerName: String,
  val paperWidthMm: Int = 58,
  val connectionSettingsJson: String = "{}",
  val updatedAt: Long,
)
