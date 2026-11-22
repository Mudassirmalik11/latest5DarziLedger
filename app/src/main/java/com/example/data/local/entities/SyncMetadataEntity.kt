package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.SyncState

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
  @PrimaryKey val deviceId: String,
  val lastSuccessfulSyncAt: Long? = null,
  val lastSyncAttemptAt: Long? = null,
  val syncStatus: SyncState = SyncState.IDLE,
  val lastError: String? = null,
)
