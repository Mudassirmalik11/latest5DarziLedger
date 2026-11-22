package com.example.features.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.OrderStatus
import com.example.core.model.PhoneNumberUtils
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class OrderSearchScope {
  ALL,
  NAME,
  PHONE,
  ID,
}

enum class OrderSortOrder {
  NEWEST,
  OLDEST,
  DELIVERY_SOONEST,
}

data class OrderListItem(
  val orderWithCustomer: OrderWithCustomer,
  val displayOrderId: String,
  val customerDisplayId: String,
)

class OrdersViewModel(
  private val orderRepository: OrderRepository,
  private val customerRepository: CustomerRepository,
) : ViewModel() {

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchScope = MutableStateFlow(OrderSearchScope.ALL)
  val searchScope: StateFlow<OrderSearchScope> = _searchScope.asStateFlow()

  private val _statusFilter = MutableStateFlow(OrderFilterOption.ALL)
  val statusFilter: StateFlow<OrderFilterOption> = _statusFilter.asStateFlow()

  private val _onlyPendingBalance = MutableStateFlow(false)
  val onlyPendingBalance: StateFlow<Boolean> = _onlyPendingBalance.asStateFlow()

  private val _sortOrder = MutableStateFlow(OrderSortOrder.NEWEST)
  val sortOrder: StateFlow<OrderSortOrder> = _sortOrder.asStateFlow()

  private val _selectedOrderId = MutableStateFlow<String?>(null)
  val selectedOrderId: StateFlow<String?> = _selectedOrderId.asStateFlow()

  val orderItems: StateFlow<List<OrderListItem>> = combine(
    orderRepository.observeAllOrders(),
    customerRepository.observeAllCustomers(),
    _searchQuery,
    _searchScope,
    combine(_statusFilter, _onlyPendingBalance, _sortOrder) { status, pendingOnly, sort ->
      Triple(status, pendingOnly, sort)
    },
  ) { allOrders, allCustomers, query, scope, filterTriple ->
    val (filter, pendingOnly, sort) = filterTriple

    val customerIdMap = buildCustomerDisplayIdMap(allCustomers)

    val mappedStatus = when (filter) {
      OrderFilterOption.ALL -> null
      OrderFilterOption.PENDING -> OrderStatus.PENDING
      OrderFilterOption.READY -> OrderStatus.READY
      OrderFilterOption.DELIVERED -> OrderStatus.DELIVERED
      OrderFilterOption.CANCELLED -> OrderStatus.CANCELLED
    }

    val trimmed = query.trim()
    val phoneDigits = if (trimmed.any { it.isDigit() }) {
      PhoneNumberUtils.normalizePakistaniPhone(trimmed)
    } else {
      ""
    }
    val idQuery = trimmed
      .removePrefix("#")
      .removePrefix("ID:")
      .removePrefix("id:")
      .removePrefix("ORDER ID:")
      .removePrefix("CUSTOMER ID:")
      .trim()

    val enriched = allOrders.map { item ->
      val order = item.order
      val displayOrderId = order.orderNumber?.toString() ?: order.orderId.take(4)
      val customerDisplayId = customerIdMap[order.customerId] ?: order.customerId.take(4)
      OrderListItem(
        orderWithCustomer = item,
        displayOrderId = displayOrderId,
        customerDisplayId = customerDisplayId,
      )
    }

    val filtered = enriched.filter { listItem ->
      val item = listItem.orderWithCustomer
      val order = item.order
      if (mappedStatus != null && order.orderStatus != mappedStatus) return@filter false
      if (pendingOnly && (order.remainingAmountPkr <= 0L || order.orderStatus == OrderStatus.CANCELLED)) {
        return@filter false
      }
      if (trimmed.isEmpty()) return@filter true

      val matchesName = item.customerName.contains(trimmed, ignoreCase = true)
      val matchesPhone = item.customerPhone.contains(trimmed, ignoreCase = true) ||
        (phoneDigits.isNotEmpty() &&
          PhoneNumberUtils.normalizePakistaniPhone(item.customerPhone).contains(phoneDigits))
      val matchesId = idQuery.isNotEmpty() && (
        listItem.displayOrderId.contains(idQuery, ignoreCase = true) ||
          order.orderId.contains(idQuery, ignoreCase = true) ||
          listItem.customerDisplayId.contains(idQuery, ignoreCase = true)
        )

      when (scope) {
        OrderSearchScope.ALL -> matchesName || matchesPhone || matchesId
        OrderSearchScope.NAME -> matchesName
        OrderSearchScope.PHONE -> matchesPhone
        OrderSearchScope.ID -> matchesId
      }
    }

    when (sort) {
      OrderSortOrder.NEWEST -> filtered.sortedByDescending { it.orderWithCustomer.order.createdAt }
      OrderSortOrder.OLDEST -> filtered.sortedBy { it.orderWithCustomer.order.createdAt }
      OrderSortOrder.DELIVERY_SOONEST -> filtered.sortedBy { it.orderWithCustomer.order.deliveryDate }
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = emptyList(),
  )

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun updateSearchScope(scope: OrderSearchScope) {
    _searchScope.value = scope
  }

  fun updateFilter(filter: OrderFilterOption) {
    _statusFilter.value = filter
  }

  fun toggleOnlyPendingBalance() {
    _onlyPendingBalance.value = !_onlyPendingBalance.value
  }

  fun updateSortOrder(sort: OrderSortOrder) {
    _sortOrder.value = sort
  }

  fun selectOrder(orderId: String?) {
    _selectedOrderId.value = orderId
  }

  private fun buildCustomerDisplayIdMap(customers: List<CustomerEntity>): Map<String, String> {
    val chronological = customers.sortedWith(
      compareBy<CustomerEntity> { it.createdAt }.thenBy { it.customerId },
    )
    return buildMap {
      chronological.forEachIndexed { index, customer ->
        val seq = (index + 1).toString()
        put(customer.customerId, customer.customId.trim().ifEmpty { seq })
      }
    }
  }

  class Factory(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return OrdersViewModel(orderRepository, customerRepository) as T
    }
  }
}
