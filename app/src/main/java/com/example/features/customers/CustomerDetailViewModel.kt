package com.example.features.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ui.localizedAppText
import com.example.data.local.daos.CustomerPaymentEntry
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.entities.OrderEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.OrderRepository
import com.example.features.printing.ThermalPrintManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomerDetailUiState(
  val customer: CustomerEntity? = null,
  val displayId: String? = null,
  val currentMeasurement: MeasurementEntity? = null,
  val measurementHistory: List<MeasurementEntity> = emptyList(),
  val payments: List<CustomerPaymentEntry> = emptyList(),
  val orders: List<OrderEntity> = emptyList(),
  val isLoaded: Boolean = false,
)

class CustomerDetailViewModel(
  private val customerId: String,
  private val customerRepository: CustomerRepository,
  private val orderRepository: OrderRepository,
  private val deviceSyncRepository: DeviceSyncRepository,
  private val thermalPrintManager: ThermalPrintManager? = null,
) : ViewModel() {

  private val customerMetaFlow = combine(
    customerRepository.observeCustomer(customerId),
    customerRepository.observeAllCustomers(),
  ) { customer, allCustomers ->
    val fallbackSeq = allCustomers
      .sortedWith(compareBy<CustomerEntity> { it.createdAt }.thenBy { it.customerId })
      .indexOfFirst { it.customerId == customerId }
      .takeIf { it >= 0 }
      ?.plus(1)
      ?.toString()
    val displayId = customer?.customId?.trim()?.ifEmpty { null } ?: fallbackSeq
    customer to displayId
  }

  private val customerActivityFlow = combine(
    customerRepository.observeCurrentMeasurement(customerId),
    customerRepository.observeMeasurementHistory(customerId),
    orderRepository.observePaymentsForCustomer(customerId),
    orderRepository.observeOrdersForCustomer(customerId),
  ) { current, history, payments, orders ->
    CustomerActivityBundle(
      currentMeasurement = current,
      measurementHistory = history,
      payments = payments,
      orders = orders,
    )
  }

  val uiState: StateFlow<CustomerDetailUiState> = combine(
    customerMetaFlow,
    customerActivityFlow,
  ) { (customer, displayId), activity ->
    CustomerDetailUiState(
      customer = customer,
      displayId = displayId,
      currentMeasurement = activity.currentMeasurement,
      measurementHistory = activity.measurementHistory,
      payments = activity.payments,
      orders = activity.orders,
      isLoaded = true,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = CustomerDetailUiState(),
  )

  private val _editError = MutableStateFlow<String?>(null)
  val editError: StateFlow<String?> = _editError.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  private val _isPrinting = MutableStateFlow(false)
  val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

  private val _previewMeasurementSlip = MutableStateFlow<Pair<MeasurementEntity, String>?>(null)
  val previewMeasurementSlip: StateFlow<Pair<MeasurementEntity, String>?> =
    _previewMeasurementSlip.asStateFlow()

  fun clearEditError() {
    _editError.value = null
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }

  fun showMessage(message: String) {
    _statusMessage.value = message
  }

  fun updateCustomer(
    name: String,
    phone: String,
    notes: String,
    customId: String = "",
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch {
      _editError.value = null
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      customerRepository.updateCustomer(
        customerId = customerId,
        name = name,
        rawPhone = phone,
        notes = notes,
        customId = customId,
        deviceId = device.deviceId,
      ).fold(
        onSuccess = { onSuccess() },
        onFailure = { error ->
          _editError.value = error.message
            ?: localizedAppText("Failed to update customer.", "گاہک اپ ڈیٹ نہیں ہو سکا۔")
        },
      )
    }
  }

  fun deleteCustomer(onDeleted: () -> Unit) {
    viewModelScope.launch {
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      customerRepository.softDeleteCustomer(
        customerId = customerId,
        deviceId = device.deviceId,
      ).fold(
        onSuccess = { onDeleted() },
        onFailure = { error ->
          _statusMessage.value = error.message
            ?: localizedAppText("Could not delete customer.", "گاہک حذف نہیں ہو سکا۔")
        },
      )
    }
  }

  fun setCurrentMeasurement(measurementId: String, confirmationMessage: String) {
    viewModelScope.launch {
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      customerRepository.setCurrentMeasurement(
        customerId = customerId,
        measurementId = measurementId,
        deviceId = device.deviceId,
      ).fold(
        onSuccess = { _statusMessage.value = confirmationMessage },
        onFailure = { error ->
          _statusMessage.value = error.message
            ?: localizedAppText("Could not save measurement.", "ناپ محفوظ نہیں ہو سکا۔")
        },
      )
    }
  }

  fun deleteMeasurement(measurementId: String) {
    viewModelScope.launch {
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      customerRepository.softDeleteMeasurement(
        customerId = customerId,
        measurementId = measurementId,
        deviceId = device.deviceId,
      ).fold(
        onSuccess = { Unit },
        onFailure = { error ->
          _statusMessage.value = error.message
            ?: localizedAppText("Could not delete measurement.", "ناپ حذف نہیں ہو سکا۔")
        },
      )
    }
  }

  fun openMeasurementPreview(measurement: MeasurementEntity) {
    val customer = uiState.value.customer ?: return
    val manager = thermalPrintManager ?: return
    viewModelScope.launch {
      val preview = manager.buildMeasurementPreviewText(
        customer = customer,
        customerDisplayId = uiState.value.displayId,
        measurement = measurement,
      )
      _previewMeasurementSlip.value = measurement to preview
    }
  }

  fun dismissMeasurementPreview() {
    _previewMeasurementSlip.value = null
  }

  fun printMeasurementSlip(measurement: MeasurementEntity) {
    val customer = uiState.value.customer ?: return
    val manager = thermalPrintManager ?: return
    if (_isPrinting.value) return
    viewModelScope.launch {
      _isPrinting.value = true
      val result = manager.printMeasurementSlip(
        customer = customer,
        customerDisplayId = uiState.value.displayId,
        measurement = measurement,
      )
      _isPrinting.value = false
      result.fold(
        onSuccess = { msg -> _statusMessage.value = msg },
        onFailure = { err ->
          _statusMessage.value = err.message
            ?: localizedAppText("Printing failed.", "پرنٹنگ ناکام ہوگئی۔")
        },
      )
    }
  }

  private data class CustomerActivityBundle(
    val currentMeasurement: MeasurementEntity?,
    val measurementHistory: List<MeasurementEntity>,
    val payments: List<CustomerPaymentEntry>,
    val orders: List<OrderEntity>,
  )

  class Factory(
    private val customerId: String,
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val deviceSyncRepository: DeviceSyncRepository,
    private val thermalPrintManager: ThermalPrintManager? = null,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return CustomerDetailViewModel(
        customerId = customerId,
        customerRepository = customerRepository,
        orderRepository = orderRepository,
        deviceSyncRepository = deviceSyncRepository,
        thermalPrintManager = thermalPrintManager,
      ) as T
    }
  }
}
