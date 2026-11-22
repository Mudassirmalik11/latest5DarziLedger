package com.example.features.measurements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.ui.localizedAppText
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeasurementFormState(
  val customerName: String = "",
  val label: String = localizedAppText("Standard", "معیاری"),
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
  val neckStyle: NeckStyle = NeckStyle.BAN,
  val damanStyle: DamanStyle = DamanStyle.GOL,
  val pocketStyles: Set<PocketStyle> = emptySet(),
  val sleeveStyle: SleeveStyle = SleeveStyle.PLAIN,
  val silaiStyle: SilaiStyle = SilaiStyle.SINGLE,
  val notes: String = "",
  val isLoaded: Boolean = false,
  val isSaving: Boolean = false,
  val errorMessage: String? = null,
)

class MeasurementEditorViewModel(
  private val customerId: String,
  private val sourceMeasurementId: String?,
  private val customerRepository: CustomerRepository,
  private val deviceSyncRepository: DeviceSyncRepository,
) : ViewModel() {

  private val _formState = MutableStateFlow(MeasurementFormState())
  val formState: StateFlow<MeasurementFormState> = _formState.asStateFlow()

  init {
    viewModelScope.launch {
      val customer = customerRepository.getCustomer(customerId)
      val existing: MeasurementEntity? = sourceMeasurementId?.let {
        customerRepository.getMeasurementById(it)
      }
      val seed = existing ?: customerRepository.getCurrentMeasurement(customerId)
      _formState.update { state ->
        if (seed != null) {
          state.copy(
            customerName = customer?.name ?: "",
            label = if (seed.label.equals("Standard", ignoreCase = true)) {
              localizedAppText("Standard", "معیاری")
            } else {
              seed.label
            },
            kameezLambai = formatNumeric(seed.kameezLambai),
            tera = formatNumeric(seed.tera),
            bazu = formatNumeric(seed.bazu),
            chati = formatNumeric(seed.chati),
            kamar = formatNumeric(seed.kamar),
            gheraDaman = formatNumeric(seed.gheraDaman),
            galaCollar = formatNumeric(seed.galaCollar),
            cuff = formatNumeric(seed.cuff),
            shalwarLambai = formatNumeric(seed.shalwarLambai),
            paincha = formatNumeric(seed.paincha),
            gheraAsan = formatNumeric(seed.gheraAsan),
            neckStyle = seed.neckStyle,
            damanStyle = seed.damanStyle,
            pocketStyles = seed.pocketStyles,
            sleeveStyle = seed.sleeveStyle,
            silaiStyle = seed.silaiStyle,
            notes = seed.notes,
            isLoaded = true,
          )
        } else {
          state.copy(
            customerName = customer?.name ?: "",
            isLoaded = true,
          )
        }
      }
    }
  }

  fun loadFromMeasurement(measurement: MeasurementEntity) {
    _formState.update { state ->
      state.copy(
        label = if (measurement.label.equals("Standard", ignoreCase = true)) {
          localizedAppText("Standard", "معیاری")
        } else {
          measurement.label
        },
        kameezLambai = formatNumeric(measurement.kameezLambai),
        tera = formatNumeric(measurement.tera),
        bazu = formatNumeric(measurement.bazu),
        chati = formatNumeric(measurement.chati),
        kamar = formatNumeric(measurement.kamar),
        gheraDaman = formatNumeric(measurement.gheraDaman),
        galaCollar = formatNumeric(measurement.galaCollar),
        cuff = formatNumeric(measurement.cuff),
        shalwarLambai = formatNumeric(measurement.shalwarLambai),
        paincha = formatNumeric(measurement.paincha),
        gheraAsan = formatNumeric(measurement.gheraAsan),
        neckStyle = measurement.neckStyle,
        damanStyle = measurement.damanStyle,
        pocketStyles = measurement.pocketStyles,
        sleeveStyle = measurement.sleeveStyle,
        silaiStyle = measurement.silaiStyle,
        notes = measurement.notes,
        errorMessage = null,
      )
    }
  }

  fun updateField(transform: (MeasurementFormState) -> MeasurementFormState) {
    _formState.update { transform(it).copy(errorMessage = null) }
  }

  fun save(onSaved: () -> Unit) {
    val s = _formState.value
    val snapshot = MeasurementSnapshot(
      kameezLambai = s.kameezLambai.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Kameez Lambai (inches).", "درست قمیض لمبائی (انچ) درج کریں۔")),
      tera = s.tera.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Tera (inches).", "درست تیرا (انچ) درج کریں۔")),
      bazu = s.bazu.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Bazu (inches).", "درست بازو (انچ) درج کریں۔")),
      chati = s.chati.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Chati (inches).", "درست چھاتی (انچ) درج کریں۔")),
      kamar = s.kamar.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Kamar (inches).", "درست کمر (انچ) درج کریں۔")),
      gheraDaman = s.gheraDaman.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Ghera / Daman (inches).", "درست گھیرا / دامن (انچ) درج کریں۔")),
      galaCollar = s.galaCollar.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Gala / Collar (inches).", "درست گلا / کالر (انچ) درج کریں۔")),
      cuff = s.cuff.toDoubleOrNull() ?: 0.0,
      shalwarLambai = s.shalwarLambai.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Shalwar Lambai (inches).", "درست شلوار لمبائی (انچ) درج کریں۔")),
      paincha = s.paincha.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Paincha (inches).", "درست پانچہ (انچ) درج کریں۔")),
      gheraAsan = s.gheraAsan.toDoubleOrNull()
        ?: return setError(localizedAppText("Enter valid Ghera / Asan (inches).", "درست گھیرا / آسن (انچ) درج کریں۔")),
      notes = s.notes,
    )

    viewModelScope.launch {
      _formState.update { it.copy(isSaving = true, errorMessage = null) }
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      val editingExisting = sourceMeasurementId?.let { id ->
        customerRepository.getMeasurementById(id)?.takeIf { !it.isDeleted }
      }
      val persistLabel = if (s.label.trim() == "معیاری") "Standard" else s.label
      val result = if (editingExisting != null) {
        customerRepository.updateMeasurement(
          customerId = customerId,
          measurementId = editingExisting.measurementId,
          snapshot = snapshot,
          neckStyle = s.neckStyle,
          damanStyle = s.damanStyle,
          pocketStyles = s.pocketStyles,
          sleeveStyle = s.sleeveStyle,
          silaiStyle = s.silaiStyle,
          label = persistLabel,
          deviceId = device.deviceId,
        )
      } else {
        customerRepository.saveMeasurement(
          customerId = customerId,
          snapshot = snapshot,
          neckStyle = s.neckStyle,
          damanStyle = s.damanStyle,
          pocketStyles = s.pocketStyles,
          sleeveStyle = s.sleeveStyle,
          silaiStyle = s.silaiStyle,
          label = persistLabel,
          deviceId = device.deviceId,
        )
      }
      result.fold(
        onSuccess = {
          _formState.update { it.copy(isSaving = false) }
          onSaved()
        },
        onFailure = { error ->
          _formState.update {
            it.copy(
              isSaving = false,
              errorMessage = error.message
                ?: localizedAppText("Failed to save measurements.", "ناپ محفوظ نہیں ہو سکے۔"),
            )
          }
        },
      )
    }
  }

  private fun setError(message: String) {
    _formState.update { it.copy(errorMessage = message) }
  }

  private fun formatNumeric(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString()
    else value.toString().trimEnd('0').trimEnd('.')
  }

  class Factory(
    private val customerId: String,
    private val sourceMeasurementId: String?,
    private val customerRepository: CustomerRepository,
    private val deviceSyncRepository: DeviceSyncRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MeasurementEditorViewModel(
        customerId = customerId,
        sourceMeasurementId = sourceMeasurementId,
        customerRepository = customerRepository,
        deviceSyncRepository = deviceSyncRepository,
      ) as T
    }
  }
}
