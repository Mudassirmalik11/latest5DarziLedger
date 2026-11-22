package com.example.features.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.PhoneNumberUtils
import com.example.core.ui.localizedAppText
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.DuplicateCustomerException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CustomerSearchScope {
  ALL,
  NAME,
  PHONE,
  ID,
  ADDRESS,
}

enum class CustomerSortOrder {
  NEWEST,
  OLDEST,
  NAME_AZ,
}

data class CustomerListItem(
  val customer: CustomerEntity,
  val displayId: String,
  val sequenceNumber: Int,
)

data class DuplicateCustomerPrompt(
  val existingCustomer: CustomerEntity,
  val pendingName: String,
  val pendingPhone: String,
  val pendingNotes: String,
  val pendingCustomId: String = "",
)

class CustomersViewModel(
  private val customerRepository: CustomerRepository,
  private val deviceSyncRepository: DeviceSyncRepository,
) : ViewModel() {

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchScope = MutableStateFlow(CustomerSearchScope.ALL)
  val searchScope: StateFlow<CustomerSearchScope> = _searchScope.asStateFlow()

  private val _sortOrder = MutableStateFlow(CustomerSortOrder.NEWEST)
  val sortOrder: StateFlow<CustomerSortOrder> = _sortOrder.asStateFlow()

  private val _onlyWithAddress = MutableStateFlow(false)
  val onlyWithAddress: StateFlow<Boolean> = _onlyWithAddress.asStateFlow()

  private val _selectedCustomerId = MutableStateFlow<String?>(null)
  val selectedCustomerId: StateFlow<String?> = _selectedCustomerId.asStateFlow()

  private val _duplicatePrompt = MutableStateFlow<DuplicateCustomerPrompt?>(null)
  val duplicatePrompt: StateFlow<DuplicateCustomerPrompt?> = _duplicatePrompt.asStateFlow()

  private val _formError = MutableStateFlow<String?>(null)
  val formError: StateFlow<String?> = _formError.asStateFlow()

  val customerItems: StateFlow<List<CustomerListItem>> = combine(
    customerRepository.observeAllCustomers(),
    _searchQuery,
    _searchScope,
    _sortOrder,
    _onlyWithAddress,
  ) { allCustomers, query, scope, sort, addressOnly ->
    val chronological = allCustomers.sortedWith(
      compareBy<CustomerEntity> { it.createdAt }.thenBy { it.customerId },
    )
    val indexed = chronological.mapIndexed { index, customer ->
      val sequenceNumber = index + 1
      val resolvedDisplayId = customer.customId.trim().ifEmpty { sequenceNumber.toString() }
      CustomerListItem(
        customer = customer,
        displayId = resolvedDisplayId,
        sequenceNumber = sequenceNumber,
      )
    }

    val trimmed = query.trim()
    val phoneDigits = if (trimmed.any { it.isDigit() }) {
      PhoneNumberUtils.normalizePakistaniPhone(trimmed)
    } else {
      ""
    }
    val idQuery = trimmed.removePrefix("#").removePrefix("ID:").removePrefix("id:").trim()

    val filtered = indexed.filter { item ->
      val c = item.customer
      if (addressOnly && c.notes.isBlank()) return@filter false
      if (trimmed.isEmpty()) return@filter true

      val matchesName = c.name.contains(trimmed, ignoreCase = true)
      val matchesPhone = c.phone.contains(trimmed, ignoreCase = true) ||
        (phoneDigits.isNotEmpty() && c.normalizedPhone.contains(phoneDigits))
      val matchesId = idQuery.isNotEmpty() && (
        item.displayId.equals(idQuery, ignoreCase = true) ||
          item.displayId.contains(idQuery, ignoreCase = true) ||
          c.customId.contains(idQuery, ignoreCase = true)
        )
      val matchesAddress = c.notes.contains(trimmed, ignoreCase = true)

      when (scope) {
        CustomerSearchScope.ALL -> matchesName || matchesPhone || matchesId || matchesAddress
        CustomerSearchScope.NAME -> matchesName
        CustomerSearchScope.PHONE -> matchesPhone
        CustomerSearchScope.ID -> matchesId
        CustomerSearchScope.ADDRESS -> matchesAddress
      }
    }

    when (sort) {
      CustomerSortOrder.NEWEST -> filtered.sortedWith(
        compareByDescending<CustomerListItem> { it.customer.createdAt }
          .thenByDescending { it.sequenceNumber },
      )
      CustomerSortOrder.OLDEST -> filtered.sortedWith(
        compareBy<CustomerListItem> { it.customer.createdAt }
          .thenBy { it.sequenceNumber },
      )
      CustomerSortOrder.NAME_AZ -> filtered.sortedBy { it.customer.name.lowercase() }
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = emptyList(),
  )

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun updateSearchScope(scope: CustomerSearchScope) {
    _searchScope.value = scope
  }

  fun updateSortOrder(order: CustomerSortOrder) {
    _sortOrder.value = order
  }

  fun toggleOnlyWithAddress() {
    _onlyWithAddress.value = !_onlyWithAddress.value
  }

  fun selectCustomer(customerId: String?) {
    _selectedCustomerId.value = customerId
  }

  fun clearFormError() {
    _formError.value = null
  }

  fun dismissDuplicatePrompt() {
    _duplicatePrompt.value = null
  }

  fun createCustomer(
    name: String,
    phone: String,
    notes: String,
    customId: String = "",
    allowDuplicatePhone: Boolean = false,
    onCreated: (CustomerEntity) -> Unit,
  ) {
    viewModelScope.launch {
      _formError.value = null
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      val result = customerRepository.createCustomer(
        name = name,
        rawPhone = phone,
        notes = notes,
        customId = customId,
        deviceId = device.deviceId,
        allowDuplicatePhone = allowDuplicatePhone,
      )
      result.fold(
        onSuccess = { created ->
          _duplicatePrompt.value = null
          _selectedCustomerId.value = created.customerId
          onCreated(created)
        },
        onFailure = { error ->
          if (error is DuplicateCustomerException) {
            _duplicatePrompt.value = DuplicateCustomerPrompt(
              existingCustomer = error.existingCustomer,
              pendingName = name,
              pendingPhone = phone,
              pendingNotes = notes,
              pendingCustomId = customId,
            )
          } else {
            _formError.value = error.message
              ?: localizedAppText("Could not save customer.", "گاہک محفوظ نہیں ہو سکا۔")
          }
        },
      )
    }
  }

  fun updateCustomer(
    customerId: String,
    name: String,
    phone: String,
    notes: String,
    customId: String = "",
    onUpdated: () -> Unit,
  ) {
    viewModelScope.launch {
      _formError.value = null
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      val result = customerRepository.updateCustomer(
        customerId = customerId,
        name = name,
        rawPhone = phone,
        notes = notes,
        customId = customId,
        deviceId = device.deviceId,
      )
      result.fold(
        onSuccess = { onUpdated() },
        onFailure = { error ->
          _formError.value = error.message
            ?: localizedAppText("Could not update customer.", "گاہک اپ ڈیٹ نہیں ہو سکا۔")
        },
      )
    }
  }

  class Factory(
    private val customerRepository: CustomerRepository,
    private val deviceSyncRepository: DeviceSyncRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return CustomersViewModel(customerRepository, deviceSyncRepository) as T
    }
  }
}
