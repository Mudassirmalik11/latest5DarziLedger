package com.example.features.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.PaymentMethod
import com.example.core.model.PocketStyle
import com.example.core.model.PocketStyleCodec
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import com.example.core.ui.PkrFormatUtils
import com.example.core.ui.localizedAppText
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateOrderFormState(
  val selectedCustomer: CustomerEntity? = null,
  val availableMeasurements: List<MeasurementEntity> = emptyList(),
  val hasSavedMeasurement: Boolean = false,
  val sourceMeasurementId: String? = null,
  val alsoUpdateCustomerMeasurements: Boolean = true,
  // Measurement fields
  val kameezLambai: String = "",
  val tera: String = "",
  val bazu: String = "",
  val chati: String = "",
  val kamar: String = "",
  val gheraDaman: String = "",
  val galaCollar: String = "",
  val cuff: String = "9.5",
  val shalwarLambai: String = "",
  val paincha: String = "",
  val gheraAsan: String = "",
  // Style fields
  val neckStyle: NeckStyle = NeckStyle.BAN,
  val damanStyle: DamanStyle = DamanStyle.GOL,
  val pocketStyles: Set<PocketStyle> = emptySet(),
  val sleeveStyle: SleeveStyle = SleeveStyle.PLAIN,
  val silaiStyle: SilaiStyle = SilaiStyle.SINGLE,
  // Order & billing fields
  val suitCount: Int = 1,
  val deliveryDateMillis: Long = PkrFormatUtils.startOfDayPlusDays(3),
  val totalBillPkrText: String = "",
  val advancePkrText: String = "0",
  val advancePaymentMethod: PaymentMethod = PaymentMethod.CASH,
  val notes: String = "",
  val isSaving: Boolean = false,
  val errorMessage: String? = null,
) {
  val selectedMeasurement: MeasurementEntity?
    get() = sourceMeasurementId?.let { id ->
      availableMeasurements.firstOrNull { it.measurementId == id }
    } ?: availableMeasurements.firstOrNull()

  val totalBillPkr: Long? get() = totalBillPkrText.toLongOrNull()
  val advancePkr: Long? get() = if (advancePkrText.isBlank()) 0L else advancePkrText.toLongOrNull()
  val remainingPreviewPkr: Long?
    get() {
      val total = totalBillPkr ?: return null
      val adv = advancePkr ?: return null
      return total - adv
    }
}

class CreateOrderViewModel(
  initialCustomerId: String?,
  private val customerRepository: CustomerRepository,
  private val orderRepository: OrderRepository,
  private val deviceSyncRepository: DeviceSyncRepository,
) : ViewModel() {

  val allCustomers: StateFlow<List<CustomerEntity>> =
    customerRepository.observeAllCustomers().stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = emptyList(),
    )

  private val _formState = MutableStateFlow(CreateOrderFormState())
  val formState: StateFlow<CreateOrderFormState> = _formState.asStateFlow()

  init {
    if (initialCustomerId != null) {
      selectCustomerById(initialCustomerId)
    }
  }

  fun selectCustomerById(customerId: String) {
    viewModelScope.launch {
      val customer = customerRepository.getCustomer(customerId) ?: return@launch
      loadCustomerMeasurements(customer)
    }
  }

  fun selectCustomer(customer: CustomerEntity) {
    viewModelScope.launch {
      loadCustomerMeasurements(customer)
    }
  }

  private suspend fun loadCustomerMeasurements(customer: CustomerEntity) {
    val measurements = customerRepository.getMeasurementHistory(customer.customerId)
    val defaultMeasurement = measurements.firstOrNull { it.isCurrent }
      ?: measurements.firstOrNull()
      ?: customerRepository.getCurrentMeasurement(customer.customerId)
    val resolvedList = if (measurements.isNotEmpty()) {
      measurements
    } else {
      listOfNotNull(defaultMeasurement)
    }
    applyCustomerAndMeasurements(customer, resolvedList, defaultMeasurement)
  }

  fun selectMeasurement(measurement: MeasurementEntity) {
    _formState.update { state ->
      applyMeasurementToState(state, measurement)
    }
  }

  private fun applyCustomerAndMeasurements(
    customer: CustomerEntity,
    measurements: List<MeasurementEntity>,
    selectedMeas: MeasurementEntity?,
  ) {
    _formState.update { state ->
      val base = state.copy(
        selectedCustomer = customer,
        availableMeasurements = measurements,
      )
      if (selectedMeas != null) {
        applyMeasurementToState(base, selectedMeas).copy(
          alsoUpdateCustomerMeasurements = false,
        )
      } else {
        base.copy(
          hasSavedMeasurement = false,
          sourceMeasurementId = null,
          alsoUpdateCustomerMeasurements = true,
          kameezLambai = "",
          tera = "",
          bazu = "",
          chati = "",
          kamar = "",
          gheraDaman = "",
          galaCollar = "",
          cuff = "9.5",
          shalwarLambai = "",
          paincha = "",
          gheraAsan = "",
          neckStyle = NeckStyle.BAN,
          damanStyle = DamanStyle.GOL,
          pocketStyles = emptySet(),
          sleeveStyle = SleeveStyle.PLAIN,
          silaiStyle = SilaiStyle.SINGLE,
          errorMessage = null,
        )
      }
    }
  }

  private fun applyMeasurementToState(
    state: CreateOrderFormState,
    meas: MeasurementEntity,
  ): CreateOrderFormState = state.copy(
    hasSavedMeasurement = true,
    sourceMeasurementId = meas.measurementId,
    kameezLambai = formatNumeric(meas.kameezLambai),
    tera = formatNumeric(meas.tera),
    bazu = formatNumeric(meas.bazu),
    chati = formatNumeric(meas.chati),
    kamar = formatNumeric(meas.kamar),
    gheraDaman = formatNumeric(meas.gheraDaman),
    galaCollar = formatNumeric(meas.galaCollar),
    cuff = formatNumeric(meas.cuff),
    shalwarLambai = formatNumeric(meas.shalwarLambai),
    paincha = formatNumeric(meas.paincha),
    gheraAsan = formatNumeric(meas.gheraAsan),
    neckStyle = meas.neckStyle,
    damanStyle = meas.damanStyle,
    pocketStyles = meas.pocketStyles,
    sleeveStyle = meas.sleeveStyle,
    silaiStyle = meas.silaiStyle,
    errorMessage = null,
  )

  fun updateForm(transform: (CreateOrderFormState) -> CreateOrderFormState) {
    _formState.update { transform(it).copy(errorMessage = null) }
  }

  fun submitOrder(onOrderCreated: (orderId: String) -> Unit) {
    val s = _formState.value
    val customer = s.selectedCustomer
      ?: return setError(localizedAppText("Please select a customer first.", "براہ کرم پہلے گاہک منتخب کریں۔"))

    val snapshot = MeasurementSnapshot(
      sourceMeasurementId = s.sourceMeasurementId,
      kameezLambai = s.kameezLambai.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Kameez Lambai.", "درست قمیض لمبائی درج کریں۔")),
      tera = s.tera.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Tera.", "درست تیرا درج کریں۔")),
      bazu = s.bazu.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Bazu.", "درست بازو درج کریں۔")),
      chati = s.chati.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Chati.", "درست چھاتی درج کریں۔")),
      kamar = s.kamar.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Kamar.", "درست کمر درج کریں۔")),
      gheraDaman = s.gheraDaman.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Ghera / Daman.", "درست گھیرا / دامن درج کریں۔")),
      galaCollar = s.galaCollar.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Gala / Collar.", "درست گلا / کالر درج کریں۔")),
      cuff = s.cuff.toDoubleOrNull() ?: 0.0,
      shalwarLambai = s.shalwarLambai.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Shalwar Lambai.", "درست شلوار لمبائی درج کریں۔")),
      paincha = s.paincha.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Paincha.", "درست پانچہ درج کریں۔")),
      gheraAsan = s.gheraAsan.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Ghera / Asan.", "درست گھیرا / آسن درج کریں۔")),
    )

    val orderedPockets = PocketStyleCodec.ordered(s.pocketStyles)
    val styleSnapshot = StyleSnapshot(
      neckStyle = s.neckStyle,
      damanStyle = s.damanStyle,
      pocketStyle = orderedPockets.firstOrNull(),
      pocketStyles = orderedPockets,
      sleeveStyle = s.sleeveStyle,
      silaiStyle = s.silaiStyle,
    )

    val totalPkr = s.totalBillPkr
      ?: return setError(localizedAppText("Enter valid Total Bill in PKR.", "روپے میں درست کل بل درج کریں۔"))
    val advancePkr = s.advancePkr
      ?: return setError(localizedAppText("Enter valid Advance Payment in PKR.", "روپے میں درست پیشگی ادائیگی درج کریں۔"))

    viewModelScope.launch {
      _formState.update { it.copy(isSaving = true, errorMessage = null) }
      val device = deviceSyncRepository.ensureLocalDeviceConfig()

      if (s.alsoUpdateCustomerMeasurements) {
        val measResult = customerRepository.saveMeasurement(
          customerId = customer.customerId,
          snapshot = snapshot,
          neckStyle = s.neckStyle,
          damanStyle = s.damanStyle,
          pocketStyles = s.pocketStyles,
          sleeveStyle = s.sleeveStyle,
          silaiStyle = s.silaiStyle,
          label = s.selectedMeasurement?.label ?: "Standard",
          deviceId = device.deviceId,
        )
        if (measResult.isFailure) {
          _formState.update {
            it.copy(
              isSaving = false,
              errorMessage = measResult.exceptionOrNull()?.message
                ?: localizedAppText("Invalid measurements.", "ناپ درست نہیں ہیں۔"),
            )
          }
          return@launch
        }
      }

      orderRepository.createOrder(
        customerId = customer.customerId,
        suitCount = s.suitCount,
        deliveryDateMillis = s.deliveryDateMillis,
        totalAmountPkr = totalPkr,
        initialAdvancePkr = advancePkr,
        initialPaymentMethod = s.advancePaymentMethod,
        measurementSnapshot = snapshot,
        styleSnapshot = styleSnapshot,
        notes = s.notes,
        deviceId = device.deviceId,
      ).fold(
        onSuccess = { created ->
          _formState.update { it.copy(isSaving = false) }
          onOrderCreated(created.orderId)
        },
        onFailure = { err ->
          _formState.update {
            it.copy(
              isSaving = false,
              errorMessage = err.message
                ?: localizedAppText("Failed to create order.", "آرڈر محفوظ نہیں ہو سکا۔"),
            )
          }
        },
      )
    }
  }

  private fun setError(msg: String) {
    _formState.update { it.copy(errorMessage = msg) }
  }

  private fun formatNumeric(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString()
    else value.toString().trimEnd('0').trimEnd('.')
  }

  class Factory(
    private val initialCustomerId: String?,
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val deviceSyncRepository: DeviceSyncRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return CreateOrderViewModel(
        initialCustomerId = initialCustomerId,
        customerRepository = customerRepository,
        orderRepository = orderRepository,
        deviceSyncRepository = deviceSyncRepository,
      ) as T
    }
  }
}
