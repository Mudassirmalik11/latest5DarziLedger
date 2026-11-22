package com.example.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.OrderStatus
import com.example.core.ui.PkrFormatUtils
import com.example.data.local.daos.OrderAnalyticsRow
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.daos.PaymentAnalyticsRow
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.DuplicateCustomerException
import com.example.data.local.repository.OrderRepository
import com.example.features.customers.DuplicateCustomerPrompt
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AnalyticsPeriod {
  DAILY,
  WEEKLY,
  MONTHLY,
  YEARLY,
}

sealed interface AnalyticsBucketLabel {
  data object Night : AnalyticsBucketLabel
  data object Morning : AnalyticsBucketLabel
  data object Afternoon : AnalyticsBucketLabel
  data object Evening : AnalyticsBucketLabel
  data class DayOfWeek(val calendarDay: Int) : AnalyticsBucketLabel
  data class WeekOfMonth(val weekNumber: Int) : AnalyticsBucketLabel
  data class MonthOfYear(val monthIndex: Int) : AnalyticsBucketLabel
}

data class AnalyticsChartBucket(
  val label: AnalyticsBucketLabel,
  val collectedPkr: Long,
  val bookedSalesPkr: Long,
  val ordersCount: Int,
)

data class PeriodAnalytics(
  val period: AnalyticsPeriod = AnalyticsPeriod.DAILY,
  val collectedPkr: Long = 0L,
  val bookedSalesPkr: Long = 0L,
  val ordersCount: Int = 0,
  val suitsCount: Int = 0,
  val deliveredCount: Int = 0,
  val buckets: List<AnalyticsChartBucket> = emptyList(),
)

data class DashboardUiState(
  val pendingCount: Int = 0,
  val dueTodayCount: Int = 0,
  val readyCount: Int = 0,
  val todayCollectionPkr: Long = 0L,
  val outstandingPkr: Long = 0L,
  val customerCount: Int = 0,
  val analytics: PeriodAnalytics = PeriodAnalytics(),
  val recentOrders: List<OrderWithCustomer> = emptyList(),
  val unpaidOrders: List<OrderWithCustomer> = emptyList(),
  val paidOrders: List<OrderWithCustomer> = emptyList(),
  val matchingOrders: List<OrderWithCustomer> = emptyList(),
  val matchingCustomers: List<CustomerEntity> = emptyList(),
)

class DashboardViewModel(
  private val orderRepository: OrderRepository,
  private val customerRepository: CustomerRepository,
  private val deviceSyncRepository: DeviceSyncRepository? = null,
  private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedAnalyticsPeriod = MutableStateFlow(AnalyticsPeriod.DAILY)
  val selectedAnalyticsPeriod: StateFlow<AnalyticsPeriod> = _selectedAnalyticsPeriod.asStateFlow()

  private val _customerFormError = MutableStateFlow<String?>(null)
  val customerFormError: StateFlow<String?> = _customerFormError.asStateFlow()

  private val _duplicateCustomerPrompt = MutableStateFlow<DuplicateCustomerPrompt?>(null)
  val duplicateCustomerPrompt: StateFlow<DuplicateCustomerPrompt?> = _duplicateCustomerPrompt.asStateFlow()

  private val todayBounds = PkrFormatUtils.todayBoundsMillis(nowMillisProvider())

  @OptIn(ExperimentalCoroutinesApi::class)
  private val searchResultsFlow = _searchQuery.flatMapLatest { query ->
    if (query.isBlank()) {
      combine(
        orderRepository.observeAllOrders(),
        customerRepository.observeAllCustomers(),
      ) { orders, _ ->
        orders.take(8) to emptyList<CustomerEntity>()
      }
    } else {
      combine(
        orderRepository.searchOrders(query, null),
        customerRepository.searchCustomers(query),
      ) { orders, customers ->
        orders.take(8) to customers.take(6)
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val analyticsFlow = _selectedAnalyticsPeriod.flatMapLatest { period ->
    val now = nowMillisProvider()
    val (startMillis, endMillis) = when (period) {
      AnalyticsPeriod.DAILY -> PkrFormatUtils.todayBoundsMillis(now)
      AnalyticsPeriod.WEEKLY -> PkrFormatUtils.weekBoundsMillis(now)
      AnalyticsPeriod.MONTHLY -> PkrFormatUtils.monthBoundsMillis(now)
      AnalyticsPeriod.YEARLY -> PkrFormatUtils.yearBoundsMillis(now)
    }
    combine(
      orderRepository.observeOrderAnalyticsInRange(startMillis, endMillis),
      orderRepository.observePaymentAnalyticsInRange(startMillis, endMillis),
    ) { orders, payments ->
      AnalyticsAggregator.aggregate(
        period = period,
        referenceMillis = now,
        orders = orders,
        payments = payments,
      )
    }
  }

  private val metricsFlow = combine(
    orderRepository.observeCountByStatus(OrderStatus.PENDING),
    orderRepository.observeDueInRangeCount(todayBounds.first, todayBounds.second),
    orderRepository.observeCountByStatus(OrderStatus.READY),
    orderRepository.observeCollectedInRangePkr(todayBounds.first, todayBounds.second),
    orderRepository.observeTotalOutstandingPkr(),
  ) { pending, dueToday, ready, collectedToday, outstanding ->
    MetricsTuple(pending, dueToday, ready, collectedToday, outstanding)
  }

  val uiState: StateFlow<DashboardUiState> = combine(
    metricsFlow,
    customerRepository.observeCustomerCount(),
    orderRepository.observeAllOrders(),
    searchResultsFlow,
    analyticsFlow,
  ) { metrics, customerCount, allOrders, searchPair, analytics ->
    val unpaid = allOrders.filter {
      it.order.orderStatus != OrderStatus.CANCELLED && it.order.remainingAmountPkr > 0L
    }
    val paid = allOrders.filter { it.order.paidAmountPkr > 0L }
    DashboardUiState(
      pendingCount = metrics.pending,
      dueTodayCount = metrics.dueToday,
      readyCount = metrics.ready,
      todayCollectionPkr = metrics.collectedToday,
      outstandingPkr = metrics.outstanding,
      customerCount = customerCount,
      analytics = analytics,
      recentOrders = allOrders.take(6),
      unpaidOrders = unpaid,
      paidOrders = paid,
      matchingOrders = searchPair.first,
      matchingCustomers = searchPair.second,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = DashboardUiState(),
  )

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun selectAnalyticsPeriod(period: AnalyticsPeriod) {
    _selectedAnalyticsPeriod.value = period
  }

  fun clearCustomerFormError() {
    _customerFormError.value = null
  }

  fun dismissDuplicateCustomerPrompt() {
    _duplicateCustomerPrompt.value = null
  }

  fun createCustomer(
    name: String,
    phone: String,
    notes: String,
    customId: String = "",
    allowDuplicatePhone: Boolean = false,
    onCreated: (CustomerEntity) -> Unit,
  ) {
    val syncRepo = deviceSyncRepository ?: return
    viewModelScope.launch {
      _customerFormError.value = null
      val device = syncRepo.ensureLocalDeviceConfig()
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
          _duplicateCustomerPrompt.value = null
          onCreated(created)
        },
        onFailure = { error ->
          if (error is DuplicateCustomerException) {
            _duplicateCustomerPrompt.value = DuplicateCustomerPrompt(
              existingCustomer = error.existingCustomer,
              pendingName = name,
              pendingPhone = phone,
              pendingNotes = notes,
              pendingCustomId = customId,
            )
          } else {
            _customerFormError.value = error.message ?: "Could not save customer."
          }
        },
      )
    }
  }

  private data class MetricsTuple(
    val pending: Int,
    val dueToday: Int,
    val ready: Int,
    val collectedToday: Long,
    val outstanding: Long,
  )

  class Factory(
    private val orderRepository: OrderRepository,
    private val customerRepository: CustomerRepository,
    private val deviceSyncRepository: DeviceSyncRepository? = null,
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DashboardViewModel(
        orderRepository = orderRepository,
        customerRepository = customerRepository,
        deviceSyncRepository = deviceSyncRepository,
        nowMillisProvider = nowMillisProvider,
      ) as T
    }
  }
}

object AnalyticsAggregator {

  fun aggregate(
    period: AnalyticsPeriod,
    referenceMillis: Long,
    orders: List<OrderAnalyticsRow>,
    payments: List<PaymentAnalyticsRow>,
  ): PeriodAnalytics {
    val labels: List<AnalyticsBucketLabel> = when (period) {
      AnalyticsPeriod.DAILY -> listOf(
        AnalyticsBucketLabel.Night,
        AnalyticsBucketLabel.Morning,
        AnalyticsBucketLabel.Afternoon,
        AnalyticsBucketLabel.Evening,
      )
      AnalyticsPeriod.WEEKLY -> listOf(
        AnalyticsBucketLabel.DayOfWeek(Calendar.MONDAY),
        AnalyticsBucketLabel.DayOfWeek(Calendar.TUESDAY),
        AnalyticsBucketLabel.DayOfWeek(Calendar.WEDNESDAY),
        AnalyticsBucketLabel.DayOfWeek(Calendar.THURSDAY),
        AnalyticsBucketLabel.DayOfWeek(Calendar.FRIDAY),
        AnalyticsBucketLabel.DayOfWeek(Calendar.SATURDAY),
        AnalyticsBucketLabel.DayOfWeek(Calendar.SUNDAY),
      )
      AnalyticsPeriod.MONTHLY -> {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceMillis }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val weekCount = if (maxDay > 28) 5 else 4
        (1..weekCount).map { AnalyticsBucketLabel.WeekOfMonth(it) }
      }
      AnalyticsPeriod.YEARLY -> (0..11).map { AnalyticsBucketLabel.MonthOfYear(it) }
    }

    val collectedByBucket = LongArray(labels.size)
    val bookedByBucket = LongArray(labels.size)
    val ordersByBucket = IntArray(labels.size)

    val cal = Calendar.getInstance()
    for (order in orders) {
      cal.timeInMillis = order.createdAt
      val idx = bucketIndexFor(period, cal, labels.size)
      bookedByBucket[idx] += order.totalAmountPkr
      ordersByBucket[idx] += 1
    }
    for (payment in payments) {
      cal.timeInMillis = payment.createdAt
      val idx = bucketIndexFor(period, cal, labels.size)
      collectedByBucket[idx] += payment.amountPkr
    }

    val buckets = labels.mapIndexed { index, label ->
      AnalyticsChartBucket(
        label = label,
        collectedPkr = collectedByBucket[index],
        bookedSalesPkr = bookedByBucket[index],
        ordersCount = ordersByBucket[index],
      )
    }

    return PeriodAnalytics(
      period = period,
      collectedPkr = payments.sumOf { it.amountPkr },
      bookedSalesPkr = orders.sumOf { it.totalAmountPkr },
      ordersCount = orders.size,
      suitsCount = orders.sumOf { it.suitCount },
      deliveredCount = orders.count { it.orderStatus == OrderStatus.DELIVERED },
      buckets = buckets,
    )
  }

  private fun bucketIndexFor(
    period: AnalyticsPeriod,
    cal: Calendar,
    bucketCount: Int,
  ): Int {
    val rawIndex = when (period) {
      AnalyticsPeriod.DAILY -> cal.get(Calendar.HOUR_OF_DAY) / 6
      AnalyticsPeriod.WEEKLY -> (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
      AnalyticsPeriod.MONTHLY -> (cal.get(Calendar.DAY_OF_MONTH) - 1) / 7
      AnalyticsPeriod.YEARLY -> cal.get(Calendar.MONTH)
    }
    return rawIndex.coerceIn(0, (bucketCount - 1).coerceAtLeast(0))
  }
}
