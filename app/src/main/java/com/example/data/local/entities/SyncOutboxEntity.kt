package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.SyncEntityType
import com.example.core.model.SyncOperation
import com.example.core.model.SyncStatus

@Entity(
  tableName = "sync_outbox",
  indices = [
    Index(value = ["syncStatus"]),
    Index(value = ["createdAt"]),
    Index(value = ["entityType", "entityId"]),
  ],
)
data class SyncOutboxEntity(
  @PrimaryKey val eventId: String,
  val entityType: SyncEntityType,
  val entityId: String,
  val operation: SyncOperation,
  val payloadJson: String,
  val createdAt: Long,
  val retryCount: Int = 0,
  val nextRetryAt: Long = 0L,
  val syncStatus: SyncStatus = SyncStatus.PENDING,
  val lastError: String? = null,
)
