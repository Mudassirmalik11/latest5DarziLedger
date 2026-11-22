package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.SyncEntityType

@Entity(
  tableName = "sync_conflicts",
  indices = [
    Index(value = ["resolved"]),
    Index(value = ["entityType", "entityId"]),
  ],
)
data class SyncConflictEntity(
  @PrimaryKey val conflictId: String,
  val entityType: SyncEntityType,
  val entityId: String,
  val localPayloadJson: String,
  val remotePayloadJson: String,
  val localVersion: Long,
  val remoteVersion: Long,
  val detectedAt: Long,
  val resolved: Boolean = false,
  val resolvedAt: Long? = null,
)
