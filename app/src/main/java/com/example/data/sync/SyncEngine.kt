package com.example.data.sync

import androidx.room.withTransaction
import com.example.core.model.DeviceRegistrationStatus
import com.example.core.model.ShopUserRole
import com.example.core.model.SyncEntityType
import com.example.core.model.SyncOperation
import com.example.core.model.SyncState
import com.example.core.model.SyncStatus
import com.example.data.local.database.DarziDatabase
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.entities.OrderEntity
import com.example.data.local.entities.OrderNumberReservationEntity
import com.example.data.local.entities.PaymentEntity
import com.example.data.local.entities.ShopEntity
import com.example.data.local.entities.SyncMetadataEntity
import com.example.data.local.entities.SyncOutboxEntity
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.OrderRepository
import com.example.data.local.repository.ShopRepository
import com.example.data.remote.AuthSessionProvider
import com.example.data.remote.CustomerDoc
import com.example.data.remote.MeasurementDoc
import com.example.data.remote.OperationType
import com.example.data.remote.OrderDoc
import com.example.data.remote.OrderReservationDoc
import com.example.data.remote.PaymentDoc
import com.example.data.remote.ShopDoc
import com.example.data.remote.handleFirestoreError
import com.example.data.sync.SyncMappers.toLocalEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SyncSummary(
  val pushedCount: Int = 0,
  val pulledCount: Int = 0,
  val assignedOrderNumbers: Int = 0,
  val conflictsDetected: Int = 0,
)

class SyncEngine(
  private val database: DarziDatabase,
  private val db: FirebaseFirestore,
  private val authSessionProvider: AuthSessionProvider,
  private val connectivityMonitor: ConnectivityMonitor,
  private val deviceSyncRepository: DeviceSyncRepository,
  private val shopRepository: ShopRepository,
  private val orderRepository: OrderRepository,
) {
  private val customerDao = database.customerDao()
  private val measurementDao = database.measurementDao()
  private val orderDao = database.orderDao()
  private val paymentDao = database.paymentDao()
  private val deviceSyncDao = database.deviceSyncDao()
  private val outboxDao = database.syncOutboxDao()

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private val syncMutex = Mutex()
  private var activeSyncJob: Job? = null
  private var activeShopId: String? = null

  private val _syncState = MutableStateFlow(SyncState.IDLE)
  val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  fun startRealtimeSync(shopId: String, scope: CoroutineScope) {
    if (shopId.isBlank()) return
    if (activeShopId == shopId && activeSyncJob?.isActive == true) return
    stopRealtimeSync()
    activeShopId = shopId

    val job = Job()
    val childScope = CoroutineScope(scope.coroutineContext + job)
    activeSyncJob = job

    // Trigger sync automatically when online & device authorized, or when connectivity returns
    combine(
      connectivityMonitor.isOnline,
      deviceSyncRepository.observeDeviceConfig(),
    ) { online, device ->
      online to (device?.registrationStatus == DeviceRegistrationStatus.AUTHORIZED)
    }
      .distinctUntilChanged()
      .onEach { (online, authorized) ->
        if (!online) {
          _syncState.value = SyncState.OFFLINE
        } else if (authorized) {
          childScope.launch { syncNow() }
        }
      }
      .launchIn(childScope)

    // Auto-flush outbox whenever new local pending events appear while online & authorized
    outboxDao.observePendingCount()
      .distinctUntilChanged()
      .onEach { pending ->
        if (pending > 0 && connectivityMonitor.isOnline.value) {
          val device = deviceSyncDao.getDeviceConfig()
          if (device?.registrationStatus == DeviceRegistrationStatus.AUTHORIZED) {
            childScope.launch { syncNow() }
          }
        }
      }
      .launchIn(childScope)

    // Real-time snapshot listeners (applied directly to Room without re-enqueuing outbox)
    observeRemoteCollection(
      shopId = shopId,
      collection = "customers",
      scope = childScope,
    ) { snap ->
      val localShopId = shopRepository.ensureDefaultShop().shopId
      val docs = snap.toObjects(CustomerDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      applyRemoteCustomers(docs.map { it.toLocalEntity(localShopId) })
    }

    observeRemoteCollection(
      shopId = shopId,
      collection = "measurements",
      scope = childScope,
    ) { snap ->
      val docs = snap.toObjects(MeasurementDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      applyRemoteMeasurements(docs.map { it.toLocalEntity() })
    }

    observeRemoteCollection(
      shopId = shopId,
      collection = "orders",
      scope = childScope,
    ) { snap ->
      val localShopId = shopRepository.ensureDefaultShop().shopId
      val docs = snap.toObjects(OrderDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      applyRemoteOrders(docs.map { it.toLocalEntity(localShopId) })
    }

    observeRemoteCollection(
      shopId = shopId,
      collection = "payments",
      scope = childScope,
    ) { snap ->
      val docs = snap.toObjects(PaymentDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      applyRemotePayments(docs.map { it.toLocalEntity() })
    }

    observeRemoteCollection(
      shopId = shopId,
      collection = "order_reservations",
      scope = childScope,
    ) { snap ->
      val localDeviceId = deviceSyncDao.getDeviceConfig()?.deviceId ?: return@observeRemoteCollection
      val docs = snap.toObjects(OrderReservationDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      applyRemoteReservations(docs.filter { it.deviceId == localDeviceId }.map { it.toLocalEntity() })
    }
  }

  fun stopRealtimeSync() {
    activeSyncJob?.cancel()
    activeSyncJob = null
    activeShopId = null
  }

  suspend fun syncNow(): Result<SyncSummary> = syncMutex.withLock {
    val currentUid = authSessionProvider.currentUser?.uid?.takeIf { it.isNotBlank() }
      ?: return Result.failure(IllegalStateException("Sign-In required before syncing."))

    if (!connectivityMonitor.isOnline.value) {
      _syncState.value = SyncState.OFFLINE
      return Result.failure(IllegalStateException("Device is offline. Changes stay queued safely."))
    }

    val device = deviceSyncRepository.getDeviceConfigForUser(currentUid)
      ?: deviceSyncRepository.ensureLocalDeviceConfig()
    if (device.registrationStatus != DeviceRegistrationStatus.AUTHORIZED) {
      return Result.failure(IllegalStateException("This device is not authorized for cloud sync."))
    }
    val shopId = device.shopId.takeIf { it.isNotBlank() && it != ShopRepository.DEFAULT_SHOP_ID } ?: currentUid

    _syncState.value = SyncState.SYNCING
    _lastError.value = null
    val now = System.currentTimeMillis()
    updateSyncMetadata(device.deviceId, SyncState.SYNCING, attemptAt = now)

    return try {
      // 1. Reserve sequential #1001+ order block if needed & assign any pending offline orders
      ensureOrderNumberReservationBlock(shopId, device.deviceId)
      val assignedCount = orderRepository.assignPendingOrderNumbers(device.deviceId)

      // 2. Push local outbox events in parent-before-child dependency order
      val (pushed, conflicts) = processOutbox(shopId, device.deviceId, device.userRole)

      // 3. Pull any remote updates not yet applied locally
      val pulled = pullAllRemoteCollections(shopId, device.deviceId)

      val completedAt = System.currentTimeMillis()
      val finalState = if (conflicts > 0) SyncState.ERROR else SyncState.IDLE
      updateSyncMetadata(
        deviceId = device.deviceId,
        state = finalState,
        attemptAt = completedAt,
        successAt = completedAt,
        error = if (conflicts > 0) "$conflicts sync conflict(s) require resolution." else null,
      )
      _syncState.value = finalState
      Result.success(
        SyncSummary(
          pushedCount = pushed,
          pulledCount = pulled,
          assignedOrderNumbers = assignedCount,
          conflictsDetected = conflicts,
        ),
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val msg = e.localizedMessage ?: "Sync failed. Will retry automatically."
      _lastError.value = msg
      _syncState.value = SyncState.ERROR
      updateSyncMetadata(device.deviceId, SyncState.ERROR, attemptAt = System.currentTimeMillis(), error = msg)
      Result.failure(e)
    }
  }

  suspend fun reserveAdditionalNumberBlock(): Result<OrderNumberReservationEntity> {
    val currentUid = authSessionProvider.currentUser?.uid?.takeIf { it.isNotBlank() }
      ?: return Result.failure(IllegalStateException("Sign-In required."))
    if (!connectivityMonitor.isOnline.value) {
      return Result.failure(IllegalStateException("Internet connection required to reserve order numbers."))
    }
    val device = deviceSyncRepository.getDeviceConfigForUser(currentUid)
      ?: deviceSyncRepository.ensureLocalDeviceConfig()
    if (device.registrationStatus != DeviceRegistrationStatus.AUTHORIZED) {
      return Result.failure(IllegalStateException("Device must be authorized first."))
    }
    val shopId = device.shopId.takeIf { it.isNotBlank() && it != ShopRepository.DEFAULT_SHOP_ID } ?: currentUid
    return try {
      val reserved = reserveBlockTransaction(shopId, device.deviceId)
      orderRepository.assignPendingOrderNumbers(device.deviceId)
      Result.success(reserved)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "shops/$shopId/order_reservations")
      Result.failure(e)
    }
  }

  suspend fun resolveConflict(
    conflictId: String,
    choice: ConflictResolutionChoice,
  ): Result<Unit> {
    val device = deviceSyncRepository.ensureLocalDeviceConfig()
    val conflict = deviceSyncDao.getConflictById(conflictId)
      ?: return Result.failure(IllegalArgumentException("Conflict not found."))

    return runCatching {
      val now = System.currentTimeMillis()
      database.withTransaction {
        when (choice) {
          ConflictResolutionChoice.KEEP_LOCAL -> {
            val bumpedVersion = maxOf(conflict.localVersion, conflict.remoteVersion) + 1L
            val newPayloadJson = when (conflict.entityType) {
              SyncEntityType.CUSTOMER -> {
                val local = json.decodeFromString<CustomerEntity>(conflict.localPayloadJson)
                  .copy(version = bumpedVersion, updatedAt = now, lastModifiedByDevice = device.deviceId)
                customerDao.update(local)
                json.encodeToString(local)
              }
              SyncEntityType.MEASUREMENT -> {
                val local = json.decodeFromString<MeasurementEntity>(conflict.localPayloadJson)
                  .copy(version = bumpedVersion, updatedAt = now, lastModifiedByDevice = device.deviceId)
                measurementDao.insert(local)
                json.encodeToString(local)
              }
              SyncEntityType.ORDER -> {
                val local = json.decodeFromString<OrderEntity>(conflict.localPayloadJson)
                  .copy(version = bumpedVersion, updatedAt = now, lastModifiedByDevice = device.deviceId)
                orderDao.update(local)
                json.encodeToString(local)
              }
              else -> conflict.localPayloadJson
            }

            val conflictEvent = outboxDao.findConflictEventForEntity(conflict.entityType, conflict.entityId)
            if (conflictEvent != null) {
              outboxDao.updatePayloadAndReset(conflictEvent.eventId, newPayloadJson, now)
            } else {
              outboxDao.enqueue(
                SyncOutboxEntity(
                  eventId = UUID.randomUUID().toString(),
                  entityType = conflict.entityType,
                  entityId = conflict.entityId,
                  operation = SyncOperation.UPDATE,
                  payloadJson = newPayloadJson,
                  createdAt = now,
                ),
              )
            }
          }

          ConflictResolutionChoice.USE_CLOUD -> {
            when (conflict.entityType) {
              SyncEntityType.CUSTOMER -> {
                val remote = json.decodeFromString<CustomerEntity>(conflict.remotePayloadJson)
                customerDao.insert(remote)
              }
              SyncEntityType.MEASUREMENT -> {
                val remote = json.decodeFromString<MeasurementEntity>(conflict.remotePayloadJson)
                if (remote.isCurrent) {
                  measurementDao.clearCurrentFlagExcept(remote.customerId, remote.measurementId, now)
                }
                measurementDao.insert(remote)
              }
              SyncEntityType.ORDER -> {
                val remote = json.decodeFromString<OrderEntity>(conflict.remotePayloadJson)
                orderDao.insert(remote)
              }
              else -> Unit
            }
            val conflictEvent = outboxDao.findConflictEventForEntity(conflict.entityType, conflict.entityId)
            if (conflictEvent != null) {
              outboxDao.updateStatus(conflictEvent.eventId, SyncStatus.SYNCED, null)
            }
          }
        }
        deviceSyncDao.markConflictResolved(conflictId, now)
      }

      if (choice == ConflictResolutionChoice.KEEP_LOCAL && connectivityMonitor.isOnline.value) {
        syncNow()
      }
      Unit
    }
  }

  private fun observeRemoteCollection(
    shopId: String,
    collection: String,
    scope: CoroutineScope,
    onSnapshot: suspend (com.google.firebase.firestore.QuerySnapshot) -> Unit,
  ) {
    val path = "shops/$shopId/$collection"
    db.collection("shops").document(shopId).collection(collection)
      .snapshots()
      .onEach { snap -> onSnapshot(snap) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        if (authSessionProvider.currentUser != null) {
          handleFirestoreError(rootError, OperationType.LIST, path)
        }
      }
      .launchIn(scope)
  }

  private suspend fun ensureOrderNumberReservationBlock(uid: String, deviceId: String) {
    val existingActive = deviceSyncDao.getActiveReservationsForDevice(deviceId)
    val remaining = existingActive.sumOf { (it.endNumber - it.nextAvailableNumber + 1).coerceAtLeast(0) }
    if (remaining >= MIN_NUMBER_BUFFER) return

    // Check if Firestore already has reservations for this device that we haven't downloaded yet
    val remoteResSnap = withTimeout(WRITE_TIMEOUT_MS) {
      db.collection("shops").document(uid).collection("order_reservations")
        .whereEqualTo("deviceId", deviceId)
        .get()
        .await()
    }
    val remoteList = remoteResSnap.toObjects(
      OrderReservationDoc::class.java,
      DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
    ).map { it.toLocalEntity() }
    applyRemoteReservations(remoteList)

    val refreshedActive = deviceSyncDao.getActiveReservationsForDevice(deviceId)
    val refreshedRemaining = refreshedActive.sumOf { (it.endNumber - it.nextAvailableNumber + 1).coerceAtLeast(0) }
    if (refreshedRemaining < MIN_NUMBER_BUFFER) {
      reserveBlockTransaction(uid, deviceId)
    }
  }

  private suspend fun reserveBlockTransaction(
    uid: String,
    deviceId: String,
  ): OrderNumberReservationEntity {
    val shopRef = db.collection("shops").document(uid)
    val reservationId = UUID.randomUUID().toString()
    val resRef = shopRef.collection("order_reservations").document(reservationId)

    val (startNum, endNum) = withTimeout(WRITE_TIMEOUT_MS) {
      db.runTransaction { tx ->
        val shopSnap = tx.get(shopRef)
        val currentNext = (shopSnap.getLong("nextOrderNumber") ?: 1001L).coerceAtLeast(1001L)
        val start = currentNext
        val end = start + ORDER_BLOCK_SIZE - 1L
        tx.update(
          shopRef,
          mapOf(
            "nextOrderNumber" to (end + 1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        tx.set(
          resRef,
          mapOf(
            "ownerUid" to uid,
            "deviceId" to deviceId,
            "startNumber" to start,
            "endNumber" to end,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        start.toInt() to end.toInt()
      }.await()
    }

    return deviceSyncRepository.addOrderNumberReservation(
      deviceId = deviceId,
      startNumber = startNum,
      endNumber = endNum,
      reservationId = reservationId,
    )
  }

  private suspend fun processOutbox(
    uid: String,
    localDeviceId: String,
    userRole: String = ShopUserRole.OWNER.name,
  ): Pair<Int, Int> {
    var pushed = 0
    var conflicts = 0
    val ready = outboxDao.getReadyEvents(System.currentTimeMillis())
      .sortedWith(compareBy({ dependencyPriority(it.entityType) }, { it.createdAt }))

    for (event in ready) {
      if (!connectivityMonitor.isOnline.value) break
      outboxDao.updateStatus(event.eventId, SyncStatus.IN_PROGRESS, null)
      val path = "shops/$uid"

      try {
        when (event.entityType) {
          SyncEntityType.SHOP -> if (userRole == ShopUserRole.OWNER.name) pushShopEvent(uid, event)
          SyncEntityType.CUSTOMER -> {
            val conflict = pushCustomerEvent(uid, localDeviceId, event)
            if (conflict) {
              conflicts += 1
              continue
            }
          }
          SyncEntityType.MEASUREMENT -> {
            val conflict = pushMeasurementEvent(uid, localDeviceId, event)
            if (conflict) {
              conflicts += 1
              continue
            }
          }
          SyncEntityType.ORDER -> {
            val conflict = pushOrderEvent(uid, localDeviceId, event)
            if (conflict) {
              conflicts += 1
              continue
            }
          }
          SyncEntityType.PAYMENT -> pushPaymentEvent(uid, event)
          SyncEntityType.DEVICE_CONFIG, SyncEntityType.ORDER_RESERVATION -> Unit
        }
        // Marked SYNCED only AFTER Firestore commit awaits cleanly
        outboxDao.updateStatus(event.eventId, SyncStatus.SYNCED, null)
        pushed += 1
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "$path/${event.entityType.name.lowercase()}/${event.entityId}")
        val nextRetryAt = System.currentTimeMillis() + ConflictResolver.computeBackoffMillis(event.retryCount)
        outboxDao.markRetry(
          eventId = event.eventId,
          nextRetryAt = nextRetryAt,
          error = e.localizedMessage ?: "Push failed",
        )
      }
    }
    return pushed to conflicts
  }

  private suspend fun pushShopEvent(uid: String, event: SyncOutboxEntity) {
    val shop = json.decodeFromString<ShopEntity>(event.payloadJson)
    val docRef = db.collection("shops").document(uid)
    withTimeout(WRITE_TIMEOUT_MS) {
      val snap = docRef.get().await()
      if (snap.exists()) {
        docRef.update(
          mapOf(
            "shopName" to shop.shopName.trim().take(80).ifEmpty { "Darzi Ledger" },
            "contactNumber" to shop.contactNumber.trim().take(25),
            "email" to shop.email.trim().take(120),
            "version" to shop.version.coerceAtLeast(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      } else {
        docRef.set(
          mapOf(
            "ownerUid" to uid,
            "shopName" to shop.shopName.trim().take(80).ifEmpty { "Darzi Ledger" },
            "contactNumber" to shop.contactNumber.trim().take(25),
            "email" to shop.email.trim().take(120),
            "nextOrderNumber" to 1001L,
            "version" to shop.version.coerceAtLeast(1L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      }
    }
  }

  private suspend fun pushCustomerEvent(
    uid: String,
    localDeviceId: String,
    event: SyncOutboxEntity,
  ): Boolean {
    val local = json.decodeFromString<CustomerEntity>(event.payloadJson)
    val docRef = db.collection("shops").document(uid).collection("customers").document(local.customerId)
    val remoteSnap = withTimeout(WRITE_TIMEOUT_MS) { docRef.get().await() }

    if (!remoteSnap.exists()) {
      withTimeout(WRITE_TIMEOUT_MS) {
        docRef.set(SyncMappers.customerToCreateMap(uid, local)).await()
      }
      return false
    }

    val remoteDoc = remoteSnap.toObject(CustomerDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    if (remoteDoc != null && ConflictResolver.hasConcurrentUpdateConflict(
        localVersion = local.version,
        localDeviceId = localDeviceId,
        remoteVersion = remoteDoc.version,
        remoteDeviceId = remoteDoc.lastModifiedByDevice,
      )
    ) {
      val conflict = ConflictResolver.createCustomerConflict(
        local = local,
        remote = remoteDoc.toLocalEntity(local.shopId),
      )
      deviceSyncDao.upsertConflict(conflict)
      outboxDao.markConflict(event.eventId, "Customer edited concurrently on another device.")
      return true
    }

    withTimeout(WRITE_TIMEOUT_MS) {
      docRef.update(SyncMappers.customerToUpdateMap(local)).await()
    }
    return false
  }

  private suspend fun pushMeasurementEvent(
    uid: String,
    localDeviceId: String,
    event: SyncOutboxEntity,
  ): Boolean {
    val local = json.decodeFromString<MeasurementEntity>(event.payloadJson)
    val docRef = db.collection("shops").document(uid).collection("measurements").document(local.measurementId)
    val remoteSnap = withTimeout(WRITE_TIMEOUT_MS) { docRef.get().await() }

    if (!remoteSnap.exists()) {
      withTimeout(WRITE_TIMEOUT_MS) {
        docRef.set(SyncMappers.measurementToCreateMap(uid, local)).await()
      }
      return false
    }

    val remoteDoc = remoteSnap.toObject(MeasurementDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    if (remoteDoc != null && ConflictResolver.hasConcurrentUpdateConflict(
        localVersion = local.version,
        localDeviceId = localDeviceId,
        remoteVersion = remoteDoc.version,
        remoteDeviceId = remoteDoc.lastModifiedByDevice,
      )
    ) {
      val conflict = ConflictResolver.createMeasurementConflict(
        local = local,
        remote = remoteDoc.toLocalEntity(),
      )
      deviceSyncDao.upsertConflict(conflict)
      outboxDao.markConflict(event.eventId, "Measurement edited concurrently on another device.")
      return true
    }

    withTimeout(WRITE_TIMEOUT_MS) {
      docRef.update(SyncMappers.measurementToUpdateMap(local)).await()
    }
    return false
  }

  private suspend fun pushOrderEvent(
    uid: String,
    localDeviceId: String,
    event: SyncOutboxEntity,
  ): Boolean {
    val local = json.decodeFromString<OrderEntity>(event.payloadJson)
    val docRef = db.collection("shops").document(uid).collection("orders").document(local.orderId)
    val remoteSnap = withTimeout(WRITE_TIMEOUT_MS) { docRef.get().await() }

    if (!remoteSnap.exists()) {
      withTimeout(WRITE_TIMEOUT_MS) {
        docRef.set(SyncMappers.orderToCreateMap(uid, local)).await()
      }
      return false
    }

    val remoteDoc = remoteSnap.toObject(OrderDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    if (remoteDoc != null && ConflictResolver.hasConcurrentUpdateConflict(
        localVersion = local.version,
        localDeviceId = localDeviceId,
        remoteVersion = remoteDoc.version,
        remoteDeviceId = remoteDoc.lastModifiedByDevice,
      )
    ) {
      val conflict = ConflictResolver.createOrderConflict(
        local = local,
        remote = remoteDoc.toLocalEntity(local.shopId),
      )
      deviceSyncDao.upsertConflict(conflict)
      outboxDao.markConflict(event.eventId, "Order edited concurrently on another device.")
      return true
    }

    val remotePaid = remoteDoc?.paidAmountPkr ?: 0L
    val remoteStatus = remoteDoc?.orderStatus
    val remoteNotes = remoteDoc?.notes
    val remoteNumber = remoteDoc?.orderNumber?.toInt()

    val paymentChanged = local.paidAmountPkr != remotePaid
    val detailsChanged = local.orderStatus.name != remoteStatus ||
      local.notes != remoteNotes ||
      local.isDeleted != (remoteDoc?.deleted ?: false) ||
      (local.orderNumber != null && remoteNumber == null)

    withTimeout(WRITE_TIMEOUT_MS) {
      if (paymentChanged) {
        docRef.update(SyncMappers.orderPaymentUpdateMap(local)).await()
      }
      if (detailsChanged || !paymentChanged) {
        docRef.update(
          SyncMappers.orderDetailsUpdateMap(
            entity = local,
            includeOrderNumber = local.orderNumber != null && remoteNumber == null,
          ),
        ).await()
      }
    }
    return false
  }

  private suspend fun pushPaymentEvent(uid: String, event: SyncOutboxEntity) {
    val local = json.decodeFromString<PaymentEntity>(event.payloadJson)
    val docRef = db.collection("shops").document(uid).collection("payments").document(local.paymentId)
    withTimeout(WRITE_TIMEOUT_MS) {
      val existing = docRef.get().await()
      if (!existing.exists()) {
        docRef.set(SyncMappers.paymentToCreateMap(uid, local)).await()
      }
    }
  }

  private suspend fun pullAllRemoteCollections(uid: String, localDeviceId: String): Int {
    val shopId = shopRepository.ensureDefaultShop().shopId
    var totalPulled = 0

    val custSnap = withTimeout(WRITE_TIMEOUT_MS) {
      db.collection("shops").document(uid).collection("customers").get().await()
    }
    totalPulled += applyRemoteCustomers(
      custSnap.toObjects(CustomerDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        .map { it.toLocalEntity(shopId) },
    )

    val measSnap = withTimeout(WRITE_TIMEOUT_MS) {
      db.collection("shops").document(uid).collection("measurements").get().await()
    }
    totalPulled += applyRemoteMeasurements(
      measSnap.toObjects(MeasurementDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        .map { it.toLocalEntity() },
    )

    val orderSnap = withTimeout(WRITE_TIMEOUT_MS) {
      db.collection("shops").document(uid).collection("orders").get().await()
    }
    totalPulled += applyRemoteOrders(
      orderSnap.toObjects(OrderDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        .map { it.toLocalEntity(shopId) },
    )

    val paySnap = withTimeout(WRITE_TIMEOUT_MS) {
      db.collection("shops").document(uid).collection("payments").get().await()
    }
    totalPulled += applyRemotePayments(
      paySnap.toObjects(PaymentDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        .map { it.toLocalEntity() },
    )

    val resSnap = withTimeout(WRITE_TIMEOUT_MS) {
      db.collection("shops").document(uid).collection("order_reservations")
        .whereEqualTo("deviceId", localDeviceId)
        .get()
        .await()
    }
    applyRemoteReservations(
      resSnap.toObjects(OrderReservationDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        .map { it.toLocalEntity() },
    )

    return totalPulled
  }

  private suspend fun applyRemoteCustomers(remoteList: List<CustomerEntity>): Int {
    if (remoteList.isEmpty()) return 0
    var applied = 0
    database.withTransaction {
      for (remote in remoteList) {
        val local = customerDao.getById(remote.customerId)
        val hasPendingLocal = outboxDao.countActiveForEntity(SyncEntityType.CUSTOMER, remote.customerId) > 0
        if (hasPendingLocal) continue
        if (local == null || remote.version > local.version || remote.updatedAt > local.updatedAt) {
          customerDao.insert(remote)
          applied += 1
        }
      }
    }
    return applied
  }

  private suspend fun applyRemoteMeasurements(remoteList: List<MeasurementEntity>): Int {
    if (remoteList.isEmpty()) return 0
    var applied = 0
    database.withTransaction {
      for (remote in remoteList) {
        val parentCustomer = customerDao.getById(remote.customerId) ?: continue
        val local = measurementDao.getById(remote.measurementId)
        val hasPendingLocal = outboxDao.countActiveForEntity(SyncEntityType.MEASUREMENT, remote.measurementId) > 0
        if (hasPendingLocal) continue
        if (local == null || remote.version > local.version || remote.updatedAt > local.updatedAt) {
          if (remote.isCurrent) {
            measurementDao.clearCurrentFlagExcept(parentCustomer.customerId, remote.measurementId, remote.updatedAt)
          }
          measurementDao.insert(remote)
          applied += 1
        }
      }
    }
    return applied
  }

  private suspend fun applyRemoteOrders(remoteList: List<OrderEntity>): Int {
    if (remoteList.isEmpty()) return 0
    var applied = 0
    database.withTransaction {
      for (remote in remoteList) {
        customerDao.getById(remote.customerId) ?: continue
        val local = orderDao.getById(remote.orderId)
        val hasPendingLocal = outboxDao.countActiveForEntity(SyncEntityType.ORDER, remote.orderId) > 0
        if (hasPendingLocal) continue
        if (local == null || remote.version > local.version || remote.updatedAt > local.updatedAt) {
          orderDao.insert(remote)
          applied += 1
        }
      }
    }
    return applied
  }

  private suspend fun applyRemotePayments(remoteList: List<PaymentEntity>): Int {
    if (remoteList.isEmpty()) return 0
    var applied = 0
    database.withTransaction {
      for (remote in remoteList) {
        orderDao.getById(remote.orderId) ?: continue
        if (paymentDao.getById(remote.paymentId) == null) {
          paymentDao.insert(remote)
          applied += 1
        }
      }
    }
    return applied
  }

  private suspend fun applyRemoteReservations(remoteList: List<OrderNumberReservationEntity>) {
    if (remoteList.isEmpty()) return
    database.withTransaction {
      for (remote in remoteList) {
        if (deviceSyncDao.getReservationById(remote.reservationId) == null) {
          deviceSyncDao.upsertReservation(remote)
        }
      }
    }
  }

  private suspend fun updateSyncMetadata(
    deviceId: String,
    state: SyncState,
    attemptAt: Long,
    successAt: Long? = null,
    error: String? = null,
  ) {
    val current = deviceSyncDao.getSyncMetadata(deviceId) ?: SyncMetadataEntity(deviceId = deviceId)
    deviceSyncDao.upsertSyncMetadata(
      current.copy(
        syncStatus = state,
        lastSyncAttemptAt = attemptAt,
        lastSuccessfulSyncAt = successAt ?: current.lastSuccessfulSyncAt,
        lastError = error,
      ),
    )
  }

  private fun dependencyPriority(type: SyncEntityType): Int = when (type) {
    SyncEntityType.SHOP -> 0
    SyncEntityType.CUSTOMER -> 1
    SyncEntityType.MEASUREMENT -> 2
    SyncEntityType.ORDER -> 3
    SyncEntityType.PAYMENT -> 4
    SyncEntityType.DEVICE_CONFIG, SyncEntityType.ORDER_RESERVATION -> 5
  }

  companion object {
    const val ORDER_BLOCK_SIZE = 50L
    const val MIN_NUMBER_BUFFER = 5
    const val WRITE_TIMEOUT_MS = 15_000L
  }
}
