package com.example.data.local.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.data.local.entities.ShopEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
  @Query("SELECT * FROM shops LIMIT 1")
  fun observePrimaryShop(): Flow<ShopEntity?>

  @Query("SELECT * FROM shops WHERE shopId = :shopId LIMIT 1")
  suspend fun getById(shopId: String): ShopEntity?

  @Query("SELECT * FROM shops LIMIT 1")
  suspend fun getPrimaryShop(): ShopEntity?

  @Query("SELECT * FROM shops")
  suspend fun getAll(): List<ShopEntity>

  @Upsert
  suspend fun upsert(shop: ShopEntity)
}
