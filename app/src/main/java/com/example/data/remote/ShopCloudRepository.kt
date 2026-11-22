package com.example.data.remote

import com.example.core.model.DeviceRegistrationStatus
import com.example.core.model.ShopUserRole
import com.example.core.preferences.ShopPreferencesRepository
import com.example.data.local.entities.DeviceConfigEntity
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.ShopRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class ShopMembership(
  val shopId: String,
  val role: ShopUserRole,
  val isRevoked: Boolean = false,
)

sealed interface SessionStatusUpdate {
  data object Active : SessionStatusUpdate
  data class DeviceRevoked(val deviceId: String) : SessionStatusUpdate
  data object StaffRevoked : SessionStatusUpdate
}

sealed interface DeviceAccessState {
  data class Authorized(
    val currentDevice: DeviceConfigEntity,
    val allDevices: List<DeviceRegistrationDoc>,
    val shopId: String = currentDevice.shopId,
    val userRole: ShopUserRole = ShopUserRole.fromString(currentDevice.userRole),
  ) : DeviceAccessState

  data class NeedsRegistration(
    val currentDevice: DeviceConfigEntity,
    val allDevices: List<DeviceRegistrationDoc>,
    val shopId: String = currentDevice.shopId,
    val userRole: ShopUserRole = ShopUserRole.fromString(currentDevice.userRole),
  ) : DeviceAccessState

  data class DeviceLimitReached(
    val currentDevice: DeviceConfigEntity,
    val allDevices: List<DeviceRegistrationDoc>,
    val shopId: String = currentDevice.shopId,
    val userRole: ShopUserRole = ShopUserRole.fromString(currentDevice.userRole),
  ) : DeviceAccessState

  data class Revoked(
    val currentDevice: DeviceConfigEntity,
    val allDevices: List<DeviceRegistrationDoc>,
    val shopId: String = currentDevice.shopId,
    val userRole: ShopUserRole = ShopUserRole.fromString(currentDevice.userRole),
  ) : DeviceAccessState

  data class StaffAccessRevoked(
    val email: String,
    val shopId: String,
  ) : DeviceAccessState
}

class ShopCloudRepository(
  private val db: FirebaseFirestore,
  private val authSessionProvider: AuthSessionProvider,
  private val shopRepository: ShopRepository,
  private val shopPreferencesRepository: ShopPreferencesRepository,
  private val deviceSyncRepository: DeviceSyncRepository,
) {

  private fun requireCurrentUser(): AuthUser {
    return authSessionProvider.currentUser?.takeIf { it.uid.isNotBlank() }
      ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
  }

  private fun requireUserId(): String = requireCurrentUser().uid

  suspend fun resolveMembership(uid: String): ShopMembership {
    require(uid.isNotBlank()) { "Authenticated UID required to resolve shop membership." }
    val linkSnap = db.collection("staff_links").document(uid).get().await()
    if (!linkSnap.exists()) {
      return ShopMembership(shopId = uid, role = ShopUserRole.OWNER, isRevoked = false)
    }
    val linkDoc = linkSnap.toObject(
      StaffLinkDoc::class.java,
      DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
    )
    val ownerUid = linkDoc?.ownerUid.orEmpty()
    if (ownerUid.isBlank()) {
      return ShopMembership(shopId = uid, role = ShopUserRole.STAFF, isRevoked = true)
    }
    if (linkDoc?.status != STATUS_STAFF_ACTIVE) {
      return ShopMembership(shopId = ownerUid, role = ShopUserRole.STAFF, isRevoked = true)
    }
    val staffSnap = runCatching {
      db.collection("shops").document(ownerUid)
        .collection("staff_users").document(uid)
        .get()
        .await()
    }.getOrNull()
    val staffDoc = staffSnap?.toObject(
      ShopStaffUserDoc::class.java,
      DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
    )
    val isRevoked = staffDoc == null || staffDoc.status != STATUS_STAFF_ACTIVE
    return ShopMembership(shopId = ownerUid, role = ShopUserRole.STAFF, isRevoked = isRevoked)
  }

  fun observeShop(shopId: String): Flow<ShopDoc?> = flow {
    require(shopId.isNotBlank()) { "Shop ID required to observe shop." }
    emitAll(
      db.collection("shops").document(shopId)
        .snapshots()
        .map { snapshot ->
          snapshot.toObject(ShopDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        },
    )
  }

  fun observeRegisteredDevices(
    shopId: String,
    userUid: String,
    userRole: ShopUserRole,
  ): Flow<List<DeviceRegistrationDoc>> = flow {
    require(shopId.isNotBlank()) { "Shop ID required to observe devices." }
    val query = if (userRole == ShopUserRole.OWNER) {
      db.collection("shops").document(shopId).collection("devices")
    } else {
      db.collection("shops").document(shopId).collection("devices")
        .whereEqualTo("userUid", userUid)
    }
    emitAll(
      query.snapshots().map { snapshot ->
        snapshot.toObjects(
          DeviceRegistrationDoc::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        ).sortedWith(
          compareByDescending<DeviceRegistrationDoc> { it.status == STATUS_AUTHORIZED }
            .thenBy { it.userRole != ShopUserRole.OWNER.name }
            .thenBy { it.deviceName.lowercase() },
        )
      },
    )
  }

  fun observeShopStaffUsers(shopId: String): Flow<List<ShopStaffUserDoc>> = flow {
    require(shopId.isNotBlank()) { "Shop ID required to observe staff users." }
    emitAll(
      db.collection("shops").document(shopId).collection("staff_users")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            ShopStaffUserDoc::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ).sortedWith(
            compareByDescending<ShopStaffUserDoc> { it.status == STATUS_STAFF_ACTIVE }
              .thenBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE },
          )
        },
    )
  }

  fun observeCurrentSessionStatus(
    shopId: String,
    uid: String,
    deviceId: String,
    userRole: ShopUserRole,
  ): Flow<SessionStatusUpdate> = flow {
    val deviceFlow = db.collection("shops").document(shopId)
      .collection("devices").document(deviceId)
      .snapshots()
      .map { snap ->
        snap.toObject(
          DeviceRegistrationDoc::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }

    if (userRole == ShopUserRole.STAFF) {
      val staffFlow = db.collection("shops").document(shopId)
        .collection("staff_users").document(uid)
        .snapshots()
        .map { snap ->
          if (!snap.exists()) null
          else snap.toObject(
            ShopStaffUserDoc::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
      emitAll(
        combine(deviceFlow, staffFlow) { deviceDoc, staffDoc ->
          when {
            staffDoc != null && staffDoc.status != STATUS_STAFF_ACTIVE -> SessionStatusUpdate.StaffRevoked
            deviceDoc != null && deviceDoc.status == STATUS_REVOKED ->
              SessionStatusUpdate.DeviceRevoked(deviceId)
            else -> SessionStatusUpdate.Active
          }
        },
      )
    } else {
      emitAll(
        deviceFlow.map { deviceDoc ->
          if (deviceDoc != null && deviceDoc.status == STATUS_REVOKED) {
            SessionStatusUpdate.DeviceRevoked(deviceId)
          } else {
            SessionStatusUpdate.Active
          }
        },
      )
    }
  }

  suspend fun ensureShopAndInspectDevice(): Result<DeviceAccessState> {
    val user = try {
      requireCurrentUser()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val uid = user.uid
    var errorPath = "staff_links/$uid"
    return try {
      val membership = resolveMembership(uid)
      val shopId = membership.shopId
      val userRole = membership.role
      errorPath = "shops/$shopId"

      if (membership.isRevoked) {
        val existingConfig = deviceSyncRepository.getDeviceConfigForUser(uid)
        if (existingConfig != null) {
          deviceSyncRepository.updateDeviceAuthorization(
            deviceId = existingConfig.deviceId,
            shopId = shopId,
            deviceName = existingConfig.deviceName,
            deviceRole = existingConfig.deviceRole,
            status = DeviceRegistrationStatus.REVOKED,
            userUid = uid,
            userRole = ShopUserRole.STAFF.name,
          )
        }
        return Result.success(
          DeviceAccessState.StaffAccessRevoked(
            email = user.email,
            shopId = shopId,
          ),
        )
      }

      deviceSyncRepository.clearShopDataIfSwitchingShops(shopId)
      val localDevice = deviceSyncRepository.ensureLocalDeviceConfigForUser(
        userUid = uid,
        shopId = shopId,
        userRole = userRole,
      )
      val prefs = shopPreferencesRepository.preferencesFlow.first()
      val localShop = shopRepository.ensureDefaultShop(
        shopName = prefs.shopName,
        contactNumber = prefs.shopContact,
        email = user.email,
      )

      val shopRef = db.collection("shops").document(shopId)
      val shopSnap = shopRef.get().await()
      if (!shopSnap.exists()) {
        if (userRole != ShopUserRole.OWNER) {
          return Result.failure(
            IllegalStateException("Shop account not initialized yet. Ask the shop owner to open the app first."),
          )
        }
        val safeName = localShop.shopName.trim().take(80).ifEmpty { "Darzi Ledger" }
        val safeContact = localShop.contactNumber.trim().take(25)
        val safeEmail = user.email.ifBlank { localShop.email }.trim().take(120)
        val payload = mapOf(
          "ownerUid" to shopId,
          "shopName" to safeName,
          "contactNumber" to safeContact,
          "email" to safeEmail,
          "nextOrderNumber" to 1001L,
          "version" to localShop.version.coerceAtLeast(1L),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        shopRef.set(payload).await()
      } else {
        val remoteShop = shopSnap.toObject(
          ShopDoc::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
        if (remoteShop != null && remoteShop.shopName.isNotBlank()) {
          shopRepository.syncFromRemote(
            shopId = localShop.shopId,
            shopName = remoteShop.shopName,
            contactNumber = remoteShop.contactNumber,
            email = remoteShop.email,
            version = remoteShop.version,
          )
          shopPreferencesRepository.updateShopProfile(
            shopName = remoteShop.shopName,
            shopContact = remoteShop.contactNumber,
          )
        }
      }

      errorPath = "shops/$shopId/devices"
      val devicesQuery = if (userRole == ShopUserRole.OWNER) {
        shopRef.collection("devices")
      } else {
        shopRef.collection("devices").whereEqualTo("userUid", uid)
      }
      val devicesSnap = devicesQuery.get().await()
      val fetchedDevices = devicesSnap.toObjects(
        DeviceRegistrationDoc::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
      val userDevices = fetchedDevices.filter { doc ->
        doc.userUid.ifBlank { doc.ownerUid } == uid
      }
      val remoteCurrent = userDevices.firstOrNull { it.id == localDevice.deviceId }

      val state = when {
        remoteCurrent != null && remoteCurrent.status == STATUS_AUTHORIZED -> {
          shopRef.collection("devices").document(localDevice.deviceId).update(
            mapOf(
              "lastSeenAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          ).await()
          val syncedLocal = deviceSyncRepository.updateDeviceAuthorization(
            deviceId = localDevice.deviceId,
            shopId = shopId,
            deviceName = remoteCurrent.deviceName,
            deviceRole = remoteCurrent.deviceRole,
            status = DeviceRegistrationStatus.AUTHORIZED,
            registeredAtMillis = remoteCurrent.registeredAt?.toDate()?.time,
            userUid = uid,
            userRole = userRole.name,
          )
          DeviceAccessState.Authorized(syncedLocal, userDevices, shopId, userRole)
        }

        remoteCurrent != null && remoteCurrent.status == STATUS_REVOKED -> {
          val syncedLocal = deviceSyncRepository.updateDeviceAuthorization(
            deviceId = localDevice.deviceId,
            shopId = shopId,
            deviceName = remoteCurrent.deviceName,
            deviceRole = remoteCurrent.deviceRole,
            status = DeviceRegistrationStatus.REVOKED,
            userUid = uid,
            userRole = userRole.name,
          )
          DeviceAccessState.Revoked(syncedLocal, userDevices, shopId, userRole)
        }

        else -> {
          val activeCount = userDevices.count { it.status == STATUS_AUTHORIZED }
          if (activeCount >= MAX_ACTIVE_DEVICES_PER_USER) {
            DeviceAccessState.DeviceLimitReached(localDevice, userDevices, shopId, userRole)
          } else {
            DeviceAccessState.NeedsRegistration(localDevice, userDevices, shopId, userRole)
          }
        }
      }

      Result.success(state)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (e !is android.database.SQLException) {
        handleFirestoreError(e, OperationType.GET, errorPath)
      }
      Result.failure(e)
    }
  }

  suspend fun registerCurrentDevice(
    deviceName: String,
    deviceRole: String,
    appVersion: String = "1.0",
  ): Result<DeviceConfigEntity> {
    val cleanName = deviceName.trim().take(60)
    if (cleanName.isEmpty()) {
      return Result.failure(IllegalArgumentException("Enter a device name (e.g. Shop Phone)."))
    }
    if (deviceRole !in VALID_DEVICE_ROLES) {
      return Result.failure(IllegalArgumentException("Device role must be PHONE or TABLET."))
    }
    val user = try {
      requireCurrentUser()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val uid = user.uid
    var devicePath = "staff_links/$uid"

    return try {
      val membership = resolveMembership(uid)
      if (membership.isRevoked) {
        return Result.failure(IllegalStateException("Your shop access has been disabled by the shop owner."))
      }
      val shopId = membership.shopId
      val userRole = membership.role
      val localDevice = deviceSyncRepository.ensureLocalDeviceConfigForUser(
        userUid = uid,
        shopId = shopId,
        userRole = userRole,
      )
      devicePath = "shops/$shopId/devices/${localDevice.deviceId}"

      val shopRef = db.collection("shops").document(shopId)
      val devicesQuery = if (userRole == ShopUserRole.OWNER) {
        shopRef.collection("devices")
      } else {
        shopRef.collection("devices").whereEqualTo("userUid", uid)
      }
      val devicesSnap = devicesQuery.get().await()
      val existingDevices = devicesSnap.toObjects(
        DeviceRegistrationDoc::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
      val userDevices = existingDevices.filter { it.userUid.ifBlank { it.ownerUid } == uid }
      val activeOthers = userDevices.count {
        it.status == STATUS_AUTHORIZED && it.id != localDevice.deviceId
      }
      if (activeOthers >= MAX_ACTIVE_DEVICES_PER_USER) {
        return Result.failure(
          IllegalStateException(
            "1 of 1 device is already active for this user. Revoke the other device first.",
          ),
        )
      }

      val existingSelf = userDevices.firstOrNull { it.id == localDevice.deviceId }
      val deviceRef = shopRef.collection("devices").document(localDevice.deviceId)
      if (existingSelf == null) {
        deviceRef.set(
          mapOf(
            "ownerUid" to shopId,
            "userUid" to uid,
            "userEmail" to user.email.trim().take(120),
            "userRole" to userRole.name,
            "deviceName" to cleanName,
            "deviceRole" to deviceRole,
            "status" to STATUS_AUTHORIZED,
            "appVersion" to appVersion.take(20).ifEmpty { "1.0" },
            "registeredAt" to FieldValue.serverTimestamp(),
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      } else {
        deviceRef.update(
          mapOf(
            "deviceName" to cleanName,
            "deviceRole" to deviceRole,
            "status" to STATUS_AUTHORIZED,
            "appVersion" to appVersion.take(20).ifEmpty { "1.0" },
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      }

      val updatedLocal = deviceSyncRepository.updateDeviceAuthorization(
        deviceId = localDevice.deviceId,
        shopId = shopId,
        deviceName = cleanName,
        deviceRole = deviceRole,
        status = DeviceRegistrationStatus.AUTHORIZED,
        registeredAtMillis = System.currentTimeMillis(),
        userUid = uid,
        userRole = userRole.name,
      )
      Result.success(updatedLocal)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, devicePath)
      Result.failure(e)
    }
  }

  suspend fun revokeDevice(targetDeviceId: String, shopIdOverride: String? = null): Result<Unit> {
    if (targetDeviceId.isBlank()) {
      return Result.failure(IllegalArgumentException("Device ID is required."))
    }
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    return try {
      val shopId = shopIdOverride?.takeIf { it.isNotBlank() && it != ShopRepository.DEFAULT_SHOP_ID }
        ?: resolveMembership(uid).shopId
      val path = "shops/$shopId/devices/$targetDeviceId"
      try {
        db.document(path).update(
          mapOf(
            "status" to STATUS_REVOKED,
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, path)
        return Result.failure(e)
      }

      val localDevice = deviceSyncRepository.getDeviceConfigForUser(uid)
      if (localDevice?.deviceId == targetDeviceId) {
        deviceSyncRepository.updateDeviceAuthorization(
          deviceId = localDevice.deviceId,
          shopId = shopId,
          deviceName = localDevice.deviceName,
          deviceRole = localDevice.deviceRole,
          status = DeviceRegistrationStatus.REVOKED,
          userUid = uid,
          userRole = localDevice.userRole,
        )
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun reauthorizeDevice(targetDeviceId: String, shopIdOverride: String? = null): Result<Unit> {
    if (targetDeviceId.isBlank()) {
      return Result.failure(IllegalArgumentException("Device ID is required."))
    }
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    return try {
      val membership = resolveMembership(uid)
      val shopId = shopIdOverride?.takeIf { it.isNotBlank() && it != ShopRepository.DEFAULT_SHOP_ID }
        ?: membership.shopId
      val path = "shops/$shopId/devices/$targetDeviceId"
      val devicesQuery = if (membership.role == ShopUserRole.OWNER) {
        db.collection("shops").document(shopId).collection("devices")
      } else {
        db.collection("shops").document(shopId).collection("devices").whereEqualTo("userUid", uid)
      }
      val devicesSnap = devicesQuery.get().await()
      val devices = devicesSnap.toObjects(
        DeviceRegistrationDoc::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
      val targetDevice = devices.firstOrNull { it.id == targetDeviceId }
        ?: return Result.failure(IllegalArgumentException("Device record not found."))
      val targetUserUid = targetDevice.userUid.ifBlank { targetDevice.ownerUid }
      val activeOthers = devices.count {
        it.status == STATUS_AUTHORIZED &&
          it.id != targetDeviceId &&
          it.userUid.ifBlank { it.ownerUid } == targetUserUid
      }
      if (activeOthers >= MAX_ACTIVE_DEVICES_PER_USER) {
        return Result.failure(
          IllegalStateException(
            "1 of 1 device is already active for this user. Revoke another device first.",
          ),
        )
      }
      try {
        db.document(path).update(
          mapOf(
            "status" to STATUS_AUTHORIZED,
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, path)
        return Result.failure(e)
      }

      val localDevice = deviceSyncRepository.getDeviceConfigForUser(uid)
      if (localDevice?.deviceId == targetDeviceId) {
        deviceSyncRepository.updateDeviceAuthorization(
          deviceId = localDevice.deviceId,
          shopId = shopId,
          deviceName = localDevice.deviceName,
          deviceRole = localDevice.deviceRole,
          status = DeviceRegistrationStatus.AUTHORIZED,
          userUid = uid,
          userRole = localDevice.userRole,
        )
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun createStaffUser(
    displayName: String,
    email: String,
    password: String,
  ): Result<ShopStaffUserDoc> {
    val owner = try {
      requireCurrentUser()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val shopId = owner.uid
    val cleanName = displayName.trim().take(60)
    val cleanEmail = email.trim().take(120)
    if (cleanName.isEmpty()) {
      return Result.failure(IllegalArgumentException("Enter the user's name."))
    }
    if (!FirebaseAuthSessionProvider.isValidEmail(cleanEmail)) {
      return Result.failure(IllegalArgumentException("Enter a valid unique email address."))
    }
    if (password.length < 6) {
      return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
    }
    if (owner.email.isNotBlank() && cleanEmail.equals(owner.email.trim(), ignoreCase = true)) {
      return Result.failure(
        IllegalArgumentException("User email must be different from the shop owner's email."),
      )
    }

    return try {
      val staffCollection = db.collection("shops").document(shopId).collection("staff_users")
      val existingSnap = staffCollection.get().await()
      val existingList = existingSnap.toObjects(
        ShopStaffUserDoc::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
      val activeCount = existingList.count { it.status == STATUS_STAFF_ACTIVE }
      if (activeCount >= MAX_STAFF_USERS) {
        return Result.failure(
          IllegalStateException("Maximum of 2 users reached. Disable an existing user to add another."),
        )
      }
      val duplicate = existingList.firstOrNull { it.email.equals(cleanEmail, ignoreCase = true) }
      if (duplicate != null) {
        val message = if (duplicate.status == STATUS_STAFF_ACTIVE) {
          "This email is already assigned to another active shop user."
        } else {
          "A disabled user with this email already exists. Tap Re-enable below or use a new email."
        }
        return Result.failure(IllegalArgumentException(message))
      }

      val authResult = authSessionProvider.createStaffAccount(
        displayName = cleanName,
        email = cleanEmail,
        password = password,
      ) { createdStaff ->
        runCatching {
          val batch = db.batch()
          val staffDocRef = staffCollection.document(createdStaff.uid)
          val linkDocRef = db.collection("staff_links").document(createdStaff.uid)
          batch.set(
            staffDocRef,
            mapOf(
              "ownerUid" to shopId,
              "staffUid" to createdStaff.uid,
              "displayName" to cleanName,
              "email" to cleanEmail,
              "role" to ShopUserRole.STAFF.name,
              "status" to STATUS_STAFF_ACTIVE,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.set(
            linkDocRef,
            mapOf(
              "staffUid" to createdStaff.uid,
              "ownerUid" to shopId,
              "displayName" to cleanName,
              "email" to cleanEmail,
              "status" to STATUS_STAFF_ACTIVE,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.commit().await()
          Unit
        }.onFailure { err ->
          handleFirestoreError(err, OperationType.WRITE, "shops/$shopId/staff_users/${createdStaff.uid}")
        }
      }

      when (authResult) {
        is CreateStaffAccountResult.Success -> Result.success(
          ShopStaffUserDoc(
            id = authResult.user.uid,
            ownerUid = shopId,
            staffUid = authResult.user.uid,
            displayName = cleanName,
            email = cleanEmail,
            role = ShopUserRole.STAFF.name,
            status = STATUS_STAFF_ACTIVE,
          ),
        )
        is CreateStaffAccountResult.Error -> Result.failure(IllegalStateException(authResult.message))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "shops/$shopId/staff_users")
      Result.failure(e)
    }
  }

  suspend fun revokeStaffUser(staffUid: String): Result<Unit> {
    if (staffUid.isBlank()) {
      return Result.failure(IllegalArgumentException("Staff user ID is required."))
    }
    val shopId = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val staffPath = "shops/$shopId/staff_users/$staffUid"
    return try {
      val activeDevicesSnap = db.collection("shops").document(shopId)
        .collection("devices")
        .whereEqualTo("userUid", staffUid)
        .get()
        .await()

      val batch = db.batch()
      batch.update(
        db.document(staffPath),
        mapOf(
          "status" to STATUS_STAFF_REVOKED,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        db.collection("staff_links").document(staffUid),
        mapOf(
          "status" to STATUS_STAFF_REVOKED,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      activeDevicesSnap.documents.forEach { deviceSnap ->
        if (deviceSnap.getString("status") == STATUS_AUTHORIZED) {
          batch.update(
            deviceSnap.reference,
            mapOf(
              "status" to STATUS_REVOKED,
              "lastSeenAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, staffPath)
      Result.failure(e)
    }
  }

  suspend fun reactivateStaffUser(staffUid: String): Result<Unit> {
    if (staffUid.isBlank()) {
      return Result.failure(IllegalArgumentException("Staff user ID is required."))
    }
    val shopId = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val staffPath = "shops/$shopId/staff_users/$staffUid"
    return try {
      val staffCollection = db.collection("shops").document(shopId).collection("staff_users")
      val existingSnap = staffCollection.get().await()
      val existingList = existingSnap.toObjects(
        ShopStaffUserDoc::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
      val activeOthers = existingList.count {
        it.status == STATUS_STAFF_ACTIVE && it.staffUid != staffUid
      }
      if (activeOthers >= MAX_STAFF_USERS) {
        return Result.failure(
          IllegalStateException("2 of 2 staff users are already active. Disable one before re-enabling."),
        )
      }

      val batch = db.batch()
      batch.update(
        db.document(staffPath),
        mapOf(
          "status" to STATUS_STAFF_ACTIVE,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        db.collection("staff_links").document(staffUid),
        mapOf(
          "status" to STATUS_STAFF_ACTIVE,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, staffPath)
      Result.failure(e)
    }
  }

  suspend fun updateShopProfileRemote(
    shopName: String,
    contactNumber: String,
    version: Long,
  ): Result<Unit> {
    val cleanName = shopName.trim().take(80)
    if (cleanName.isEmpty()) {
      return Result.failure(IllegalArgumentException("Shop name cannot be empty."))
    }
    val cleanContact = contactNumber.trim().take(25)
    val user = try {
      requireCurrentUser()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val email = user.email.take(120)
    val path = "shops/${user.uid}"
    return try {
      db.document(path).update(
        mapOf(
          "shopName" to cleanName,
          "contactNumber" to cleanContact,
          "email" to email,
          "version" to version.coerceAtLeast(1L),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  companion object {
    const val MAX_ACTIVE_DEVICES_PER_USER = 1
    const val MAX_STAFF_USERS = 2
    const val STATUS_AUTHORIZED = "AUTHORIZED"
    const val STATUS_REVOKED = "REVOKED"
    const val STATUS_STAFF_ACTIVE = "ACTIVE"
    const val STATUS_STAFF_REVOKED = "REVOKED"
    val VALID_DEVICE_ROLES = setOf("PHONE", "TABLET")
  }
}
