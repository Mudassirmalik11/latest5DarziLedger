package com.example.features.auth

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.DeviceRegistrationStatus
import com.example.core.model.ShopUserRole
import com.example.core.ui.localizedAppText
import com.example.data.local.entities.DeviceConfigEntity
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.ShopRepository
import com.example.data.remote.AuthSessionProvider
import com.example.data.remote.DeviceAccessState
import com.example.data.remote.DeviceRegistrationDoc
import com.example.data.remote.SessionStatusUpdate
import com.example.data.remote.ShopCloudRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface DeviceGateState {
  data object Checking : DeviceGateState
  data class Authorized(
    val device: DeviceConfigEntity,
    val shopId: String = device.shopId,
    val userRole: ShopUserRole = ShopUserRole.fromString(device.userRole),
  ) : DeviceGateState
  data class NeedsRegistration(
    val localDevice: DeviceConfigEntity,
    val registeredDevices: List<DeviceRegistrationDoc>,
    val defaultName: String,
    val userRole: ShopUserRole = ShopUserRole.fromString(localDevice.userRole),
  ) : DeviceGateState
  data class DeviceLimitReached(
    val localDevice: DeviceConfigEntity,
    val registeredDevices: List<DeviceRegistrationDoc>,
    val userRole: ShopUserRole = ShopUserRole.fromString(localDevice.userRole),
  ) : DeviceGateState
  data class Revoked(
    val localDevice: DeviceConfigEntity,
    val registeredDevices: List<DeviceRegistrationDoc>,
    val userRole: ShopUserRole = ShopUserRole.fromString(localDevice.userRole),
  ) : DeviceGateState
  data class StaffAccessRevoked(
    val email: String,
  ) : DeviceGateState
  data class Error(val message: String) : DeviceGateState
}

class DeviceAuthorizationViewModel(
  private val uid: String,
  private val deviceSyncRepository: DeviceSyncRepository,
  private val shopCloudRepository: ShopCloudRepository,
  private val authSessionProvider: AuthSessionProvider,
) : ViewModel() {

  private val _gateState = MutableStateFlow<DeviceGateState>(DeviceGateState.Checking)
  val gateState: StateFlow<DeviceGateState> = _gateState.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _actionError = MutableStateFlow<String?>(null)
  val actionError: StateFlow<String?> = _actionError.asStateFlow()

  private var sessionObserverJob: Job? = null

  init {
    evaluateDeviceAccess()
  }

  fun evaluateDeviceAccess() {
    viewModelScope.launch {
      _actionError.value = null
      val localDevice = deviceSyncRepository.ensureLocalDeviceConfigForUser(userUid = uid)
      if (localDevice.registrationStatus == DeviceRegistrationStatus.AUTHORIZED &&
        localDevice.shopId.isNotBlank() &&
        localDevice.shopId != ShopRepository.DEFAULT_SHOP_ID
      ) {
        val role = ShopUserRole.fromString(localDevice.userRole)
        // Offline-first: unlock immediately for an already-authorized user device,
        // and verify cloud status silently in the background when online.
        _gateState.value = DeviceGateState.Authorized(
          device = localDevice,
          shopId = localDevice.shopId,
          userRole = role,
        )
        startSessionObserver(localDevice.shopId, localDevice.deviceId, role)
        shopCloudRepository.ensureShopAndInspectDevice().onSuccess { access ->
          when (access) {
            is DeviceAccessState.Revoked,
            is DeviceAccessState.StaffAccessRevoked,
            is DeviceAccessState.DeviceLimitReached,
            -> applyAccessState(access)
            is DeviceAccessState.Authorized -> {
              _gateState.value = DeviceGateState.Authorized(
                device = access.currentDevice,
                shopId = access.shopId,
                userRole = access.userRole,
              )
              startSessionObserver(access.shopId, access.currentDevice.deviceId, access.userRole)
            }
            is DeviceAccessState.NeedsRegistration -> Unit
          }
        }
        return@launch
      }

      sessionObserverJob?.cancel()
      _gateState.value = DeviceGateState.Checking
      shopCloudRepository.ensureShopAndInspectDevice().fold(
        onSuccess = { access -> applyAccessState(access) },
        onFailure = { err ->
          _gateState.value = DeviceGateState.Error(
            err.localizedMessage ?: localizedAppText(
              "Unable to verify device with cloud. Check connection and retry.",
              "کلاؤڈ کے ساتھ ڈیوائس کی تصدیق نہیں ہو سکی۔ انٹرنیٹ چیک کریں اور دوبارہ کوشش کریں۔",
            ),
          )
        },
      )
    }
  }

  fun registerCurrentDevice(deviceName: String, deviceRole: String) {
    viewModelScope.launch {
      _isBusy.value = true
      _actionError.value = null
      shopCloudRepository.registerCurrentDevice(deviceName, deviceRole).fold(
        onSuccess = { updated ->
          _isBusy.value = false
          val role = ShopUserRole.fromString(updated.userRole)
          _gateState.value = DeviceGateState.Authorized(
            device = updated,
            shopId = updated.shopId,
            userRole = role,
          )
          startSessionObserver(updated.shopId, updated.deviceId, role)
        },
        onFailure = { err ->
          _isBusy.value = false
          _actionError.value = err.message
            ?: localizedAppText("Failed to authorize device.", "ڈیوائس کی تصدیق نہیں ہو سکی۔")
        },
      )
    }
  }

  fun revokeDevice(targetDeviceId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      _actionError.value = null
      shopCloudRepository.revokeDevice(targetDeviceId).fold(
        onSuccess = {
          _isBusy.value = false
          evaluateDeviceAccess()
        },
        onFailure = { err ->
          _isBusy.value = false
          _actionError.value = err.message
            ?: localizedAppText("Failed to revoke device.", "ڈیوائس کی رسائی ختم نہیں ہو سکی۔")
        },
      )
    }
  }

  fun reauthorizeCurrentDevice(deviceId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      _actionError.value = null
      shopCloudRepository.reauthorizeDevice(deviceId).fold(
        onSuccess = {
          _isBusy.value = false
          evaluateDeviceAccess()
        },
        onFailure = { err ->
          _isBusy.value = false
          _actionError.value = err.message
            ?: localizedAppText("Failed to re-authorize device.", "ڈیوائس کو دوبارہ اجازت نہیں دی جا سکی۔")
        },
      )
    }
  }

  fun signOut(context: Context) {
    sessionObserverJob?.cancel()
    viewModelScope.launch {
      authSessionProvider.signOut(context)
    }
  }

  private fun applyAccessState(access: DeviceAccessState) {
    val modelLabel = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Shop Android"
    when (access) {
      is DeviceAccessState.Authorized -> {
        _gateState.value = DeviceGateState.Authorized(
          device = access.currentDevice,
          shopId = access.shopId,
          userRole = access.userRole,
        )
        startSessionObserver(access.shopId, access.currentDevice.deviceId, access.userRole)
      }
      is DeviceAccessState.NeedsRegistration -> {
        sessionObserverJob?.cancel()
        _gateState.value = DeviceGateState.NeedsRegistration(
          localDevice = access.currentDevice,
          registeredDevices = access.allDevices,
          defaultName = modelLabel.take(40),
          userRole = access.userRole,
        )
      }
      is DeviceAccessState.DeviceLimitReached -> {
        sessionObserverJob?.cancel()
        _gateState.value = DeviceGateState.DeviceLimitReached(
          localDevice = access.currentDevice,
          registeredDevices = access.allDevices,
          userRole = access.userRole,
        )
      }
      is DeviceAccessState.Revoked -> {
        sessionObserverJob?.cancel()
        _gateState.value = DeviceGateState.Revoked(
          localDevice = access.currentDevice,
          registeredDevices = access.allDevices,
          userRole = access.userRole,
        )
      }
      is DeviceAccessState.StaffAccessRevoked -> {
        sessionObserverJob?.cancel()
        _gateState.value = DeviceGateState.StaffAccessRevoked(email = access.email)
      }
    }
  }

  private fun startSessionObserver(shopId: String, deviceId: String, userRole: ShopUserRole) {
    if (shopId.isBlank() || shopId == ShopRepository.DEFAULT_SHOP_ID) return
    sessionObserverJob?.cancel()
    sessionObserverJob = viewModelScope.launch {
      shopCloudRepository.observeCurrentSessionStatus(
        shopId = shopId,
        uid = uid,
        deviceId = deviceId,
        userRole = userRole,
      )
        .catch { err ->
          if (err is CancellationException) throw err
        }
        .collect { status ->
          when (status) {
            is SessionStatusUpdate.StaffRevoked -> {
              val existing = deviceSyncRepository.getDeviceConfigForUser(uid)
              if (existing != null) {
                deviceSyncRepository.updateDeviceAuthorization(
                  deviceId = existing.deviceId,
                  shopId = shopId,
                  deviceName = existing.deviceName,
                  deviceRole = existing.deviceRole,
                  status = DeviceRegistrationStatus.REVOKED,
                  userUid = uid,
                  userRole = userRole.name,
                )
              }
              _gateState.value = DeviceGateState.StaffAccessRevoked(
                email = authSessionProvider.currentUser?.email.orEmpty(),
              )
            }
            is SessionStatusUpdate.DeviceRevoked -> {
              val existing = deviceSyncRepository.getDeviceConfigForUser(uid)
              if (existing != null && existing.deviceId == status.deviceId) {
                deviceSyncRepository.updateDeviceAuthorization(
                  deviceId = existing.deviceId,
                  shopId = shopId,
                  deviceName = existing.deviceName,
                  deviceRole = existing.deviceRole,
                  status = DeviceRegistrationStatus.REVOKED,
                  userUid = uid,
                  userRole = userRole.name,
                )
              }
              evaluateDeviceAccess()
            }
            is SessionStatusUpdate.Active -> Unit
          }
        }
    }
  }

  override fun onCleared() {
    sessionObserverJob?.cancel()
    super.onCleared()
  }

  class Factory(
    private val uid: String,
    private val deviceSyncRepository: DeviceSyncRepository,
    private val shopCloudRepository: ShopCloudRepository,
    private val authSessionProvider: AuthSessionProvider,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DeviceAuthorizationViewModel(
        uid = uid,
        deviceSyncRepository = deviceSyncRepository,
        shopCloudRepository = shopCloudRepository,
        authSessionProvider = authSessionProvider,
      ) as T
    }
  }
}
