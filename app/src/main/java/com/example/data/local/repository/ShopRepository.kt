package com.example.data.local.repository

import androidx.room.withTransaction
import com.example.core.model.PhoneNumberUtils
import com.example.core.model.SyncEntityType
import com.example.core.model.SyncOperation
import com.example.data.local.database.DarziDatabase
import com.example.data.local.entities.ShopEntity
import com.example.data.local.entities.SyncOutboxEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ShopRepository(
  private val database: DarziDatabase,
) {
  private val shopDao = database.shopDao()
  private val outboxDao = database.syncOutboxDao()
  private val json = Json { encodeDefaults = true }

  fun observePrimaryShop(): Flow<ShopEntity?> = shopDao.observePrimaryShop()

  suspend fun ensureDefaultShop(
    defaultShopId: String = DEFAULT_SHOP_ID,
    shopName: String = "Darzi Ledger",
    contactNumber: String = "",
    email: String = "",
    nowMillis: Long = System.currentTimeMillis(),
  ): ShopEntity {
    val existing = shopDao.getPrimaryShop()
    if (existing != null) return existing
    val created = ShopEntity(
      shopId = defaultShopId,
      shopName = shopName,
      contactNumber = contactNumber,
      email = email,
      createdAt = nowMillis,
      updatedAt = nowMillis,
      version = 1L,
    )
    shopDao.upsert(created)
    return created
  }

  suspend fun updateShopProfile(
    shopName: String,
    contactNumber: String,
    email: String = "",
    nowMillis: Long = System.currentTimeMillis(),
  ): ShopEntity = database.withTransaction {
    val current = shopDao.getPrimaryShop()
    val updated = if (current == null) {
      ShopEntity(
        shopId = DEFAULT_SHOP_ID,
        shopName = shopName.trim().ifEmpty { "Darzi Ledger" },
        contactNumber = PhoneNumberUtils.formatPakistaniPhone(contactNumber),
        email = email.trim(),
        createdAt = nowMillis,
        updatedAt = nowMillis,
        version = 1L,
      )
    } else {
      current.copy(
        shopName = shopName.trim().ifEmpty { "Darzi Ledger" },
        contactNumber = PhoneNumberUtils.formatPakistaniPhone(contactNumber),
        email = email.trim().ifEmpty { current.email },
        updatedAt = nowMillis,
        version = current.version + 1L,
      )
    }
    shopDao.upsert(updated)
    outboxDao.enqueue(
      SyncOutboxEntity(
        eventId = UUID.randomUUID().toString(),
        entityType = SyncEntityType.SHOP,
        entityId = updated.shopId,
        operation = if (current == null) SyncOperation.CREATE else SyncOperation.UPDATE,
        payloadJson = json.encodeToString(updated),
        createdAt = nowMillis,
      ),
    )
    updated
  }

  suspend fun syncFromRemote(
    shopId: String,
    shopName: String,
    contactNumber: String,
    email: String,
    version: Long,
    nowMillis: Long = System.currentTimeMillis(),
  ): ShopEntity = database.withTransaction {
    val current = shopDao.getPrimaryShop()
    val merged = ShopEntity(
      shopId = current?.shopId ?: DEFAULT_SHOP_ID,
      shopName = shopName.trim().ifEmpty { current?.shopName ?: "Darzi Ledger" },
      contactNumber = contactNumber.trim(),
      email = email.trim(),
      createdAt = current?.createdAt ?: nowMillis,
      updatedAt = nowMillis,
      version = maxOf(version, current?.version ?: 1L),
    )
    shopDao.upsert(merged)
    merged
  }

  companion object {
    const val DEFAULT_SHOP_ID = "default_shop"
  }
}
