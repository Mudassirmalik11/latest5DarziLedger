package com.example.data.local.repository

import androidx.room.withTransaction
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.PhoneNumberUtils
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.SyncEntityType
import com.example.core.model.SyncOperation
import com.example.core.model.TailorValidators
import com.example.data.local.database.DarziDatabase
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.entities.SyncOutboxEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CustomerRepository(
  private val database: DarziDatabase,
  private val shopRepository: ShopRepository,
) {
  private val customerDao = database.customerDao()
  private val measurementDao = database.measurementDao()
  private val outboxDao = database.syncOutboxDao()
  private val json = Json { encodeDefaults = true }

  fun observeAllCustomers(): Flow<List<CustomerEntity>> = customerDao.observeAllActive()

  fun observeCustomerCount(): Flow<Int> = customerDao.observeActiveCount()

  fun searchCustomers(query: String): Flow<List<CustomerEntity>> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return customerDao.observeAllActive()
    val phoneDigits = PhoneNumberUtils.normalizePakistaniPhone(trimmed)
    return customerDao.searchActive(textQuery = trimmed, phoneDigits = phoneDigits)
  }

  fun observeCustomer(customerId: String): Flow<CustomerEntity?> =
    customerDao.observeById(customerId)

  suspend fun getCustomer(customerId: String): CustomerEntity? =
    customerDao.getById(customerId)

  suspend fun findDuplicateByPhone(rawPhone: String): CustomerEntity? {
    val normalized = PhoneNumberUtils.normalizePakistaniPhone(rawPhone)
    if (normalized.isEmpty()) return null
    return customerDao.findActiveByNormalizedPhone(normalized)
  }

  fun observeCurrentMeasurement(customerId: String): Flow<MeasurementEntity?> =
    measurementDao.observeCurrentForCustomer(customerId)

  suspend fun getCurrentMeasurement(customerId: String): MeasurementEntity? =
    measurementDao.getCurrentForCustomer(customerId)

  suspend fun getMeasurementById(measurementId: String): MeasurementEntity? =
    measurementDao.getById(measurementId)

  fun observeMeasurementHistory(customerId: String): Flow<List<MeasurementEntity>> =
    measurementDao.observeHistoryForCustomer(customerId)

  suspend fun getMeasurementHistory(customerId: String): List<MeasurementEntity> =
    measurementDao.getHistoryForCustomer(customerId)

  suspend fun createCustomer(
    name: String,
    rawPhone: String,
    notes: String = "",
    customId: String = "",
    deviceId: String,
    allowDuplicatePhone: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<CustomerEntity> {
    val validationError = TailorValidators.validateCustomer(name, rawPhone)
    if (validationError != null) {
      return Result.failure(IllegalArgumentException(validationError))
    }

    val normalizedPhone = PhoneNumberUtils.normalizePakistaniPhone(rawPhone)
    val formattedPhone = PhoneNumberUtils.formatPakistaniPhone(rawPhone)

    return runCatching {
      database.withTransaction {
        if (!allowDuplicatePhone && normalizedPhone.isNotBlank()) {
          val duplicate = customerDao.findActiveByNormalizedPhone(normalizedPhone)
          if (duplicate != null) {
            throw DuplicateCustomerException(duplicate)
          }
        }
        val shop = shopRepository.ensureDefaultShop(nowMillis = nowMillis)
        val customer = CustomerEntity(
          customerId = UUID.randomUUID().toString(),
          shopId = shop.shopId,
          name = name.trim(),
          phone = formattedPhone,
          normalizedPhone = normalizedPhone,
          notes = notes.trim(),
          customId = customId.trim(),
          createdAt = nowMillis,
          updatedAt = nowMillis,
          version = 1L,
          lastModifiedByDevice = deviceId,
        )
        customerDao.insert(customer)
        outboxDao.enqueue(
          SyncOutboxEntity(
            eventId = UUID.randomUUID().toString(),
            entityType = SyncEntityType.CUSTOMER,
            entityId = customer.customerId,
            operation = SyncOperation.CREATE,
            payloadJson = json.encodeToString(customer),
            createdAt = nowMillis,
          ),
        )
        customer
      }
    }
  }

  suspend fun updateCustomer(
    customerId: String,
    name: String,
    rawPhone: String,
    notes: String,
    customId: String = "",
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<CustomerEntity> {
    val validationError = TailorValidators.validateCustomer(name, rawPhone)
    if (validationError != null) {
      return Result.failure(IllegalArgumentException(validationError))
    }

    return runCatching {
      database.withTransaction {
        val existing = customerDao.getById(customerId)
          ?: throw IllegalStateException("Customer not found.")
        val normalizedPhone = PhoneNumberUtils.normalizePakistaniPhone(rawPhone)
        val formattedPhone = PhoneNumberUtils.formatPakistaniPhone(rawPhone)

        val updated = existing.copy(
          name = name.trim(),
          phone = formattedPhone,
          normalizedPhone = normalizedPhone,
          notes = notes.trim(),
          customId = customId.trim(),
          updatedAt = nowMillis,
          version = existing.version + 1L,
          lastModifiedByDevice = deviceId,
        )
        customerDao.update(updated)
        outboxDao.enqueue(
          SyncOutboxEntity(
            eventId = UUID.randomUUID().toString(),
            entityType = SyncEntityType.CUSTOMER,
            entityId = updated.customerId,
            operation = SyncOperation.UPDATE,
            payloadJson = json.encodeToString(updated),
            createdAt = nowMillis,
          ),
        )
        updated
      }
    }
  }

  suspend fun softDeleteCustomer(
    customerId: String,
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> = runCatching {
    database.withTransaction {
      val existing = customerDao.getById(customerId)
        ?: throw IllegalStateException("Customer not found.")
      val deleted = existing.copy(
        isDeleted = true,
        deletedAt = nowMillis,
        updatedAt = nowMillis,
        version = existing.version + 1L,
        lastModifiedByDevice = deviceId,
      )
      customerDao.update(deleted)
      outboxDao.enqueue(
        SyncOutboxEntity(
          eventId = UUID.randomUUID().toString(),
          entityType = SyncEntityType.CUSTOMER,
          entityId = deleted.customerId,
          operation = SyncOperation.DELETE,
          payloadJson = json.encodeToString(deleted),
          createdAt = nowMillis,
        ),
      )
    }
  }

  suspend fun saveMeasurement(
    customerId: String,
    snapshot: MeasurementSnapshot,
    neckStyle: NeckStyle,
    damanStyle: DamanStyle,
    pocketStyle: PocketStyle?,
    sleeveStyle: SleeveStyle,
    silaiStyle: SilaiStyle,
    label: String = "Standard",
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<MeasurementEntity> = saveMeasurement(
    customerId = customerId,
    snapshot = snapshot,
    neckStyle = neckStyle,
    damanStyle = damanStyle,
    pocketStyles = setOfNotNull(pocketStyle),
    sleeveStyle = sleeveStyle,
    silaiStyle = silaiStyle,
    label = label,
    deviceId = deviceId,
    nowMillis = nowMillis,
  )

  suspend fun saveMeasurement(
    customerId: String,
    snapshot: MeasurementSnapshot,
    neckStyle: NeckStyle,
    damanStyle: DamanStyle,
    pocketStyles: Set<PocketStyle> = emptySet(),
    sleeveStyle: SleeveStyle,
    silaiStyle: SilaiStyle,
    label: String = "Standard",
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<MeasurementEntity> {
    val validationError = TailorValidators.validateMeasurement(snapshot)
    if (validationError != null) {
      return Result.failure(IllegalArgumentException(validationError))
    }

    return runCatching {
      database.withTransaction {
        customerDao.getById(customerId)
          ?: throw IllegalStateException("Customer does not exist.")
        measurementDao.clearCurrentFlagForCustomer(customerId, nowMillis)

        val entity = MeasurementEntity(
          measurementId = UUID.randomUUID().toString(),
          customerId = customerId,
          label = label.trim().ifEmpty { "Standard" },
          isCurrent = true,
          kameezLambai = snapshot.kameezLambai,
          tera = snapshot.tera,
          bazu = snapshot.bazu,
          chati = snapshot.chati,
          kamar = snapshot.kamar,
          gheraDaman = snapshot.gheraDaman,
          galaCollar = snapshot.galaCollar,
          cuff = snapshot.cuff,
          shalwarLambai = snapshot.shalwarLambai,
          paincha = snapshot.paincha,
          gheraAsan = snapshot.gheraAsan,
          neckStyle = neckStyle,
          damanStyle = damanStyle,
          pocketStyles = pocketStyles,
          sleeveStyle = sleeveStyle,
          silaiStyle = silaiStyle,
          notes = snapshot.notes.trim(),
          createdAt = nowMillis,
          updatedAt = nowMillis,
          version = 1L,
          lastModifiedByDevice = deviceId,
        )
        measurementDao.insert(entity)
        outboxDao.enqueue(
          SyncOutboxEntity(
            eventId = UUID.randomUUID().toString(),
            entityType = SyncEntityType.MEASUREMENT,
            entityId = entity.measurementId,
            operation = SyncOperation.CREATE,
            payloadJson = json.encodeToString(entity),
            createdAt = nowMillis,
          ),
        )
        entity
      }
    }
  }

  suspend fun updateMeasurement(
    customerId: String,
    measurementId: String,
    snapshot: MeasurementSnapshot,
    neckStyle: NeckStyle,
    damanStyle: DamanStyle,
    pocketStyle: PocketStyle?,
    sleeveStyle: SleeveStyle,
    silaiStyle: SilaiStyle,
    label: String = "Standard",
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<MeasurementEntity> = updateMeasurement(
    customerId = customerId,
    measurementId = measurementId,
    snapshot = snapshot,
    neckStyle = neckStyle,
    damanStyle = damanStyle,
    pocketStyles = setOfNotNull(pocketStyle),
    sleeveStyle = sleeveStyle,
    silaiStyle = silaiStyle,
    label = label,
    deviceId = deviceId,
    nowMillis = nowMillis,
  )

  suspend fun updateMeasurement(
    customerId: String,
    measurementId: String,
    snapshot: MeasurementSnapshot,
    neckStyle: NeckStyle,
    damanStyle: DamanStyle,
    pocketStyles: Set<PocketStyle> = emptySet(),
    sleeveStyle: SleeveStyle,
    silaiStyle: SilaiStyle,
    label: String = "Standard",
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<MeasurementEntity> {
    val validationError = TailorValidators.validateMeasurement(snapshot)
    if (validationError != null) {
      return Result.failure(IllegalArgumentException(validationError))
    }

    return runCatching {
      database.withTransaction {
        val existing = measurementDao.getById(measurementId)
          ?.takeIf { it.customerId == customerId && !it.isDeleted }
          ?: throw IllegalStateException("Measurement record not found.")

        val updated = existing.copy(
          label = label.trim().ifEmpty { "Standard" },
          kameezLambai = snapshot.kameezLambai,
          tera = snapshot.tera,
          bazu = snapshot.bazu,
          chati = snapshot.chati,
          kamar = snapshot.kamar,
          gheraDaman = snapshot.gheraDaman,
          galaCollar = snapshot.galaCollar,
          cuff = snapshot.cuff,
          shalwarLambai = snapshot.shalwarLambai,
          paincha = snapshot.paincha,
          gheraAsan = snapshot.gheraAsan,
          neckStyle = neckStyle,
          damanStyle = damanStyle,
          pocketStyles = pocketStyles,
          sleeveStyle = sleeveStyle,
          silaiStyle = silaiStyle,
          notes = snapshot.notes.trim(),
          updatedAt = nowMillis,
          version = existing.version + 1L,
          lastModifiedByDevice = deviceId,
        )
        measurementDao.insert(updated)
        outboxDao.enqueue(
          SyncOutboxEntity(
            eventId = UUID.randomUUID().toString(),
            entityType = SyncEntityType.MEASUREMENT,
            entityId = updated.measurementId,
            operation = SyncOperation.UPDATE,
            payloadJson = json.encodeToString(updated),
            createdAt = nowMillis,
          ),
        )
        updated
      }
    }
  }

  suspend fun setCurrentMeasurement(
    customerId: String,
    measurementId: String,
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> = runCatching {
    database.withTransaction {
      val target = measurementDao.getById(measurementId)
        ?.takeIf { it.customerId == customerId && !it.isDeleted }
        ?: throw IllegalStateException("Measurement record not found.")

      val previousCurrent = measurementDao.getCurrentForCustomer(customerId)
      if (previousCurrent != null && previousCurrent.measurementId != target.measurementId) {
        val clearedPrev = previousCurrent.copy(
          isCurrent = false,
          updatedAt = nowMillis,
          version = previousCurrent.version + 1L,
          lastModifiedByDevice = deviceId,
        )
        measurementDao.insert(clearedPrev)
        outboxDao.enqueue(
          SyncOutboxEntity(
            eventId = UUID.randomUUID().toString(),
            entityType = SyncEntityType.MEASUREMENT,
            entityId = clearedPrev.measurementId,
            operation = SyncOperation.UPDATE,
            payloadJson = json.encodeToString(clearedPrev),
            createdAt = nowMillis,
          ),
        )
      }
      measurementDao.clearCurrentFlagExcept(customerId, target.measurementId, nowMillis)

      val updatedTarget = target.copy(
        isCurrent = true,
        updatedAt = nowMillis,
        version = target.version + 1L,
        lastModifiedByDevice = deviceId,
      )
      measurementDao.insert(updatedTarget)
      outboxDao.enqueue(
        SyncOutboxEntity(
          eventId = UUID.randomUUID().toString(),
          entityType = SyncEntityType.MEASUREMENT,
          entityId = updatedTarget.measurementId,
          operation = SyncOperation.UPDATE,
          payloadJson = json.encodeToString(updatedTarget),
          createdAt = nowMillis,
        ),
      )
    }
  }

  suspend fun softDeleteMeasurement(
    customerId: String,
    measurementId: String,
    deviceId: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Result<Unit> = runCatching {
    database.withTransaction {
      val existing = measurementDao.getById(measurementId)
        ?.takeIf { it.customerId == customerId }
        ?: throw IllegalStateException("Measurement record not found.")
      if (existing.isDeleted) return@withTransaction

      val wasCurrent = existing.isCurrent
      val deleted = existing.copy(
        isDeleted = true,
        isCurrent = false,
        updatedAt = nowMillis,
        version = existing.version + 1L,
        lastModifiedByDevice = deviceId,
      )
      measurementDao.insert(deleted)
      outboxDao.enqueue(
        SyncOutboxEntity(
          eventId = UUID.randomUUID().toString(),
          entityType = SyncEntityType.MEASUREMENT,
          entityId = deleted.measurementId,
          operation = SyncOperation.DELETE,
          payloadJson = json.encodeToString(deleted),
          createdAt = nowMillis,
        ),
      )

      if (wasCurrent) {
        val fallback = measurementDao.getLatestActiveForCustomer(customerId)
        if (fallback != null && !fallback.isCurrent) {
          val promoted = fallback.copy(
            isCurrent = true,
            updatedAt = nowMillis,
            version = fallback.version + 1L,
            lastModifiedByDevice = deviceId,
          )
          measurementDao.insert(promoted)
          outboxDao.enqueue(
            SyncOutboxEntity(
              eventId = UUID.randomUUID().toString(),
              entityType = SyncEntityType.MEASUREMENT,
              entityId = promoted.measurementId,
              operation = SyncOperation.UPDATE,
              payloadJson = json.encodeToString(promoted),
              createdAt = nowMillis,
            ),
          )
        }
      }
    }
  }
}

class DuplicateCustomerException(val existingCustomer: CustomerEntity) :
  IllegalStateException("Customer already exists with phone ${existingCustomer.phone}")
