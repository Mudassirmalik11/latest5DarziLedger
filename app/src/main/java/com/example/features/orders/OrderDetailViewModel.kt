package com.example.features.orders

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.ShopUserRole
import com.example.core.model.SnapshotJson
import com.example.core.model.StyleSnapshot
import com.example.core.ui.localizedAppText
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.entities.PaymentEntity
import com.example.data.local.entities.PrinterConfigEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.OrderRepository
import com.example.features.printing.PrintSlipType
import com.example.features.printing.ThermalPrintManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OrderDetailUiState(
  val orderWithCustomer: OrderWithCustomer? = null,
  val payments: List<PaymentEntity> = emptyList(),
  val measurementSnapshot: MeasurementSnapshot? = null,
  val styleSnapshot: StyleSnapshot? = null,
  val sourceMeasurementLabel: String? = null,
  val configuredPrinter: PrinterConfigEntity? = null,
  val mediaUris: List<String> = emptyList(),
  val isLoaded: Boolean = false,
)

class OrderDetailViewModel(
  private val orderId: String,
  private val orderRepository: OrderRepository,
  private val deviceSyncRepository: DeviceSyncRepository,
  private val thermalPrintManager: ThermalPrintManager,
  private val orderMediaStore: OrderMediaStore? = null,
  private val customerRepository: CustomerRepository? = null,
  val userRole: ShopUserRole = ShopUserRole.OWNER,
) : ViewModel() {

  val isOwner: Boolean = userRole == ShopUserRole.OWNER

  val uiState: StateFlow<OrderDetailUiState> = combine(
    orderRepository.observeOrderDetail(orderId),
    orderRepository.observePaymentsForOrder(orderId),
    thermalPrintManager.observeDefaultPrinter(),
    orderMediaStore?.observeMediaUris(orderId) ?: flowOf(emptyList()),
  ) { orderDetail, payments, printer, mediaUris ->
    val measurement = orderDetail?.order?.measurementSnapshotJson?.let { raw ->
      runCatching { SnapshotJson.decodeMeasurement(raw) }.getOrNull()
    }
    val style = orderDetail?.order?.styleSnapshotJson?.let { raw ->
      runCatching { SnapshotJson.decodeStyle(raw) }.getOrNull()
    }
    val measurementLabel = measurement?.sourceMeasurementId?.let { measId ->
      runCatching { customerRepository?.getMeasurementById(measId)?.label }.getOrNull()
    }
    OrderDetailUiState(
      orderWithCustomer = orderDetail,
      payments = payments,
      measurementSnapshot = measurement,
      styleSnapshot = style,
      sourceMeasurementLabel = measurementLabel,
      configuredPrinter = printer,
      mediaUris = mediaUris,
      isLoaded = true,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = OrderDetailUiState(),
  )

  private val _actionError = MutableStateFlow<String?>(null)
  val actionError: StateFlow<String?> = _actionError.asStateFlow()

  private val _printStatusMessage = MutableStateFlow<String?>(null)
  val printStatusMessage: StateFlow<String?> = _printStatusMessage.asStateFlow()

  private val _isPrinting = MutableStateFlow(false)
  val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

  private val _previewSlip = MutableStateFlow<Pair<PrintSlipType, String>?>(null)
  val previewSlip: StateFlow<Pair<PrintSlipType, String>?> = _previewSlip.asStateFlow()

  fun clearError() {
    _actionError.value = null
  }

  fun clearPrintMessage() {
    _printStatusMessage.value = null
  }

  fun openPreview(slipType: PrintSlipType) {
    val state = uiState.value
    val detail = state.orderWithCustomer ?: return
    viewModelScope.launch {
      val text = thermalPrintManager.buildPreviewText(
        orderDetail = detail,
        payments = state.payments,
        slipType = slipType,
      )
      _previewSlip.value = slipType to text
    }
  }

  fun dismissPreview() {
    _previewSlip.value = null
  }

  fun printCustomerReceipt() {
    val state = uiState.value
    val detail = state.orderWithCustomer ?: return
    if (_isPrinting.value) return
    viewModelScope.launch {
      _isPrinting.value = true
      val result = thermalPrintManager.printCustomerReceipt(detail, state.payments)
      _isPrinting.value = false
      result.fold(
        onSuccess = { msg -> _printStatusMessage.value = msg },
        onFailure = { err ->
          _printStatusMessage.value = err.message
            ?: localizedAppText("Customer receipt printing failed.", "گاہک کی رسید پرنٹ نہیں ہو سکی۔")
        },
      )
    }
  }

  fun printKarigarSlip() {
    val detail = uiState.value.orderWithCustomer ?: return
    if (_isPrinting.value) return
    viewModelScope.launch {
      _isPrinting.value = true
      val result = thermalPrintManager.printKarigarSlip(detail)
      _isPrinting.value = false
      result.fold(
        onSuccess = { msg -> _printStatusMessage.value = msg },
        onFailure = { err ->
          _printStatusMessage.value = err.message
            ?: localizedAppText("Karigar slip printing failed.", "کاریگر پرچی پرنٹ نہیں ہو سکی۔")
        },
      )
    }
  }

  fun updateStatus(targetStatus: OrderStatus, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _actionError.value = null
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      orderRepository.updateOrderStatus(
        orderId = orderId,
        targetStatus = targetStatus,
        deviceId = device.deviceId,
      ).fold(
        onSuccess = { onSuccess() },
        onFailure = { err ->
          _actionError.value = err.message
            ?: localizedAppText("Status update failed.", "حالت اپ ڈیٹ نہیں ہو سکی۔")
        },
      )
    }
  }

  fun deleteOrder(onDeleted: () -> Unit) {
    if (!isOwner) {
      _actionError.value = localizedAppText(
        "Only the shop owner can delete orders.",
        "آرڈر صرف دکان کا مالک حذف کر سکتا ہے۔",
      )
      return
    }
    viewModelScope.launch {
      _actionError.value = null
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      orderRepository.softDeleteOrder(
        orderId = orderId,
        deviceId = device.deviceId,
      ).fold(
        onSuccess = { onDeleted() },
        onFailure = { err ->
          _actionError.value = err.message
            ?: localizedAppText("Could not delete order.", "آرڈر حذف نہیں ہو سکا۔")
        },
      )
    }
  }

  fun recordPayment(
    amountText: String,
    method: PaymentMethod,
    note: String,
    onSuccess: () -> Unit,
  ) {
    if (!isOwner) {
      _actionError.value = localizedAppText(
        "Only the shop owner can record payments.",
        "ادائیگی صرف دکان کا مالک درج کر سکتا ہے۔",
      )
      return
    }
    val amount = amountText.toLongOrNull()
    if (amount == null) {
      _actionError.value = localizedAppText("Enter a valid PKR amount.", "روپے میں درست رقم درج کریں۔")
      return
    }
    viewModelScope.launch {
      _actionError.value = null
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      orderRepository.recordPayment(
        orderId = orderId,
        amountPkr = amount,
        paymentMethod = method,
        note = note,
        deviceId = device.deviceId,
      ).fold(
        onSuccess = { onSuccess() },
        onFailure = { err ->
          _actionError.value = err.message
            ?: localizedAppText("Payment failed.", "ادائیگی محفوظ نہیں ہو سکی۔")
        },
      )
    }
  }

  fun settleFullRemaining(method: PaymentMethod = PaymentMethod.CASH, onSuccess: () -> Unit = {}) {
    if (!isOwner) {
      _actionError.value = localizedAppText(
        "Only the shop owner can collect payments.",
        "ادائیگی صرف دکان کا مالک درج کر سکتا ہے۔",
      )
      return
    }
    viewModelScope.launch {
      _actionError.value = null
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      orderRepository.recordRemainingBalancePayment(
        orderId = orderId,
        paymentMethod = method,
        deviceId = device.deviceId,
      ).fold(
        onSuccess = { onSuccess() },
        onFailure = { err ->
          _actionError.value = err.message
            ?: localizedAppText("Settlement failed.", "مکمل ادائیگی نہیں ہو سکی۔")
        },
      )
    }
  }

  fun addMediaUris(uris: List<Uri>) {
    orderMediaStore?.addMediaUris(orderId, uris)
  }

  fun removeMediaUri(uriString: String) {
    orderMediaStore?.removeMediaUri(orderId, uriString)
  }

  class Factory(
    private val orderId: String,
    private val orderRepository: OrderRepository,
    private val deviceSyncRepository: DeviceSyncRepository,
    private val thermalPrintManager: ThermalPrintManager,
    private val orderMediaStore: OrderMediaStore? = null,
    private val customerRepository: CustomerRepository? = null,
    private val userRole: ShopUserRole = ShopUserRole.OWNER,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return OrderDetailViewModel(
        orderId = orderId,
        orderRepository = orderRepository,
        deviceSyncRepository = deviceSyncRepository,
        thermalPrintManager = thermalPrintManager,
        orderMediaStore = orderMediaStore,
        customerRepository = customerRepository,
        userRole = userRole,
      ) as T
    }
  }
}
