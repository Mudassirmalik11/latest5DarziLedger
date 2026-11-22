package com.example.data.local.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.data.local.entities.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
  @Query(
    """
    SELECT * FROM customers
    WHERE isDeleted = 0
    ORDER BY updatedAt DESC
    """
  )
  fun observeAllActive(): Flow<List<CustomerEntity>>

  @Query(
    """
    SELECT * FROM customers
    WHERE isDeleted = 0
      AND (
        LOWER(name) LIKE '%' || LOWER(:textQuery) || '%'
        OR (:phoneDigits != '' AND normalizedPhone LIKE '%' || :phoneDigits || '%')
      )
    ORDER BY name ASC
    """
  )
  fun searchActive(textQuery: String, phoneDigits: String): Flow<List<CustomerEntity>>

  @Query("SELECT * FROM customers WHERE customerId = :customerId LIMIT 1")
  fun observeById(customerId: String): Flow<CustomerEntity?>

  @Query("SELECT * FROM customers WHERE customerId = :customerId LIMIT 1")
  suspend fun getById(customerId: String): CustomerEntity?

  @Query(
    """
    SELECT * FROM customers
    WHERE normalizedPhone = :normalizedPhone AND isDeleted = 0
    LIMIT 1
    """
  )
  suspend fun findActiveByNormalizedPhone(normalizedPhone: String): CustomerEntity?

  @Upsert
  suspend fun insert(customer: CustomerEntity)

  @Update
  suspend fun update(customer: CustomerEntity)

  @Query("SELECT COUNT(*) FROM customers WHERE isDeleted = 0")
  fun observeActiveCount(): Flow<Int>

  @Query("SELECT * FROM customers ORDER BY createdAt ASC")
  suspend fun getAllForBackup(): List<CustomerEntity>
}
