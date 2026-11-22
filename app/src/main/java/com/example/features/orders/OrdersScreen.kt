package com.example.features.orders

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.app.AppContainer
import com.example.core.model.ShopUserRole
import com.example.core.ui.EmptyWorkspaceCard
import com.example.core.ui.WindowWidthClass
import com.example.ui.theme.AtelierLandingDarkCanvas
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.OnAtelierOlive

enum class OrderFilterOption(@param:StringRes val labelRes: Int) {
  ALL(R.string.status_all),
  PENDING(R.string.status_pending),
  READY(R.string.status_ready),
  DELIVERED(R.string.status_delivered),
  CANCELLED(R.string.status_cancelled),
}

@Composable
fun OrdersScreen(
  viewModel: OrdersViewModel,
  appContainer: AppContainer,
  widthClass: WindowWidthClass,
  onCreateNewOrder: () -> Unit,
  onOpenOrderDetail: (orderId: String) -> Unit,
  onOpenCustomerDetail: (customerId: String) -> Unit,
  modifier: Modifier = Modifier,
  onShowMessage: (String) -> Unit = {},
  userRole: ShopUserRole = ShopUserRole.OWNER,
) {
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val searchScope by viewModel.searchScope.collectAsStateWithLifecycle()
  val selectedFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
  val onlyPendingBalance by viewModel.onlyPendingBalance.collectAsStateWithLifecycle()
  val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
  val orderItems by viewModel.orderItems.collectAsStateWithLifecycle()
  val selectedOrderId by viewModel.selectedOrderId.collectAsStateWithLifecycle()

  val isSplitPane = widthClass != WindowWidthClass.Compact
  val isDarkCanvas = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val canvasColor = if (isDarkCanvas) AtelierLandingDarkCanvas else MaterialTheme.colorScheme.background

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(canvasColor)
      .testTag("orders_screen"),
  ) {
    if (isSplitPane) {
      Row(modifier = Modifier.fillMaxSize()) {
        OrdersListPane(
          searchQuery = searchQuery,
          searchScope = searchScope,
          selectedFilter = selectedFilter,
          onlyPendingBalance = onlyPendingBalance,
          sortOrder = sortOrder,
          orderItems = orderItems,
          selectedOrderId = selectedOrderId,
          onSearchChange = viewModel::updateSearchQuery,
          onScopeChange = viewModel::updateSearchScope,
          onFilterSelect = viewModel::updateFilter,
          onTogglePendingBalance = viewModel::toggleOnlyPendingBalance,
          onSortSelect = viewModel::updateSortOrder,
          onOrderClick = { item -> viewModel.selectOrder(item.orderWithCustomer.order.orderId) },
          modifier = Modifier
            .weight(0.42f)
            .fillMaxHeight(),
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
          modifier = Modifier
            .weight(0.58f)
            .fillMaxHeight(),
        ) {
          val targetId = selectedOrderId ?: orderItems.firstOrNull()?.orderWithCustomer?.order?.orderId
          if (targetId == null) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              EmptyWorkspaceCard(
                icon = Icons.Filled.ReceiptLong,
                title = stringResource(R.string.orders_empty_title),
                description = stringResource(R.string.orders_empty_desc),
              )
            }
          } else {
            val detailVm: OrderDetailViewModel = viewModel(
              key = "order_detail_${userRole.name}_$targetId",
              factory = OrderDetailViewModel.Factory(
                orderId = targetId,
                orderRepository = appContainer.orderRepository,
                deviceSyncRepository = appContainer.deviceSyncRepository,
                thermalPrintManager = appContainer.thermalPrintManager,
                orderMediaStore = appContainer.orderMediaStore,
                customerRepository = appContainer.customerRepository,
                userRole = userRole,
              ),
            )
            val detailState by detailVm.uiState.collectAsStateWithLifecycle()
            val actionError by detailVm.actionError.collectAsStateWithLifecycle()
            val printMessage by detailVm.printStatusMessage.collectAsStateWithLifecycle()
            val isPrinting by detailVm.isPrinting.collectAsStateWithLifecycle()
            val previewSlip by detailVm.previewSlip.collectAsStateWithLifecycle()
            LaunchedEffect(printMessage) {
              val msg = printMessage
              if (!msg.isNullOrBlank()) {
                onShowMessage(msg)
                detailVm.clearPrintMessage()
              }
            }
            OrderDetailContent(
              uiState = detailState,
              actionError = actionError,
              onClearError = detailVm::clearError,
              onUpdateStatus = { status -> detailVm.updateStatus(status) },
              onDeleteOrder = {
                detailVm.deleteOrder {
                  viewModel.selectOrder(null)
                }
              },
              onRecordPayment = { amount, method, note, onSuccess ->
                detailVm.recordPayment(amount, method, note, onSuccess)
              },
              onSettleRemaining = { method -> detailVm.settleFullRemaining(method) },
              onOpenCustomer = onOpenCustomerDetail,
              isPrinting = isPrinting,
              previewSlip = previewSlip,
              onPrintCustomerReceipt = detailVm::printCustomerReceipt,
              onPrintKarigarSlip = detailVm::printKarigarSlip,
              onOpenPreview = detailVm::openPreview,
              onDismissPreview = detailVm::dismissPreview,
              onAddMediaUris = detailVm::addMediaUris,
              onRemoveMediaUri = detailVm::removeMediaUri,
              isOwner = detailVm.isOwner,
            )
          }
        }
      }
    } else {
      OrdersListPane(
        searchQuery = searchQuery,
        searchScope = searchScope,
        selectedFilter = selectedFilter,
        onlyPendingBalance = onlyPendingBalance,
        sortOrder = sortOrder,
        orderItems = orderItems,
        selectedOrderId = null,
        onSearchChange = viewModel::updateSearchQuery,
        onScopeChange = viewModel::updateSearchScope,
        onFilterSelect = viewModel::updateFilter,
        onTogglePendingBalance = viewModel::toggleOnlyPendingBalance,
        onSortSelect = viewModel::updateSortOrder,
        onOrderClick = { item -> onOpenOrderDetail(item.orderWithCustomer.order.orderId) },
        modifier = Modifier.fillMaxSize(),
      )
    }

    FloatingActionButton(
      onClick = onCreateNewOrder,
      shape = CircleShape,
      containerColor = AtelierOlive,
      contentColor = OnAtelierOlive,
      elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(20.dp)
        .size(60.dp)
        .testTag("add_order_fab"),
    ) {
      Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = stringResource(R.string.order_new_action),
        modifier = Modifier.size(28.dp),
      )
    }
  }
}

@Composable
private fun OrdersListPane(
  searchQuery: String,
  searchScope: OrderSearchScope,
  selectedFilter: OrderFilterOption,
  onlyPendingBalance: Boolean,
  sortOrder: OrderSortOrder,
  orderItems: List<OrderListItem>,
  selectedOrderId: String?,
  onSearchChange: (String) -> Unit,
  onScopeChange: (OrderSearchScope) -> Unit,
  onFilterSelect: (OrderFilterOption) -> Unit,
  onTogglePendingBalance: () -> Unit,
  onSortSelect: (OrderSortOrder) -> Unit,
  onOrderClick: (OrderListItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showFiltersMenu by rememberSaveable { mutableStateOf(false) }
  val hasActiveFilter = selectedFilter != OrderFilterOption.ALL ||
    onlyPendingBalance ||
    sortOrder != OrderSortOrder.NEWEST

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 820.dp)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchChange,
          modifier = Modifier
            .weight(1f)
            .testTag("orders_search_input"),
          placeholder = {
            Text(
              text = stringResource(R.string.orders_search_simple_hint),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { onSearchChange("") },
                modifier = Modifier.testTag("orders_search_clear"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Clear,
                  contentDescription = stringResource(R.string.dashboard_clear_search),
                )
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(10.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AtelierOlive,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
          ),
        )

        Box {
          Surface(
            onClick = { showFiltersMenu = true },
            shape = RoundedCornerShape(10.dp),
            color = if (hasActiveFilter) {
              AtelierOlive
            } else {
              MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (hasActiveFilter) {
              OnAtelierOlive
            } else {
              MaterialTheme.colorScheme.onSurface
            },
            border = BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            ),
            modifier = Modifier
              .heightIn(min = 52.dp)
              .testTag("orders_filter_button"),
          ) {
            Box(
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = stringResource(R.string.orders_filters_button),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
              )
            }
          }

          DropdownMenu(
            expanded = showFiltersMenu,
            onDismissRequest = { showFiltersMenu = false },
            modifier = Modifier.testTag("orders_filter_menu"),
          ) {
            OrderFilterOption.entries.forEach { option ->
              OrderMenuOptionRow(
                labelRes = option.labelRes,
                selected = selectedFilter == option,
                onClick = {
                  onFilterSelect(option)
                  showFiltersMenu = false
                },
                testTag = "orders_filter_${option.name.lowercase()}",
              )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            OrderMenuOptionRow(
              labelRes = R.string.orders_filter_pending_balance,
              selected = onlyPendingBalance,
              onClick = {
                onTogglePendingBalance()
                showFiltersMenu = false
              },
              testTag = "orders_filter_pending_balance",
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            OrderMenuOptionRow(
              labelRes = R.string.customers_sort_newest,
              selected = sortOrder == OrderSortOrder.NEWEST,
              onClick = {
                onSortSelect(OrderSortOrder.NEWEST)
                showFiltersMenu = false
              },
              testTag = "orders_sort_newest",
            )
            OrderMenuOptionRow(
              labelRes = R.string.customers_sort_oldest,
              selected = sortOrder == OrderSortOrder.OLDEST,
              onClick = {
                onSortSelect(OrderSortOrder.OLDEST)
                showFiltersMenu = false
              },
              testTag = "orders_sort_oldest",
            )
            OrderMenuOptionRow(
              labelRes = R.string.orders_sort_delivery,
              selected = sortOrder == OrderSortOrder.DELIVERY_SOONEST,
              onClick = {
                onSortSelect(OrderSortOrder.DELIVERY_SOONEST)
                showFiltersMenu = false
              },
              testTag = "orders_sort_delivery",
            )
          }
        }
      }

      OrdersSearchByScopeBar(
        selectedScope = searchScope,
        onSelectScope = onScopeChange,
      )

      Text(
        text = stringResource(R.string.orders_total_count, orderItems.size),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag("orders_total_count_text"),
      )
    }

    if (orderItems.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 820.dp)
          .padding(16.dp),
      ) {
        EmptyWorkspaceCard(
          icon = Icons.Filled.ReceiptLong,
          title = stringResource(R.string.orders_empty_title),
          description = stringResource(R.string.orders_empty_desc),
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 820.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(orderItems, key = { it.orderWithCustomer.order.orderId }) { item ->
          OrderSummaryCard(
            item = item.orderWithCustomer,
            customerDisplayId = item.customerDisplayId,
            isSelected = selectedOrderId == item.orderWithCustomer.order.orderId,
            onClick = { onOrderClick(item) },
          )
        }
      }
    }
  }
}

@Composable
private fun OrderMenuOptionRow(
  @StringRes labelRes: Int,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  DropdownMenuItem(
    text = { Text(stringResource(labelRes)) },
    trailingIcon = if (selected) {
      { Icon(Icons.Filled.Check, contentDescription = null) }
    } else {
      null
    },
    onClick = onClick,
    modifier = Modifier.testTag(testTag),
  )
}

@Composable
private fun OrdersSearchByScopeBar(
  selectedScope: OrderSearchScope,
  onSelectScope: (OrderSearchScope) -> Unit,
) {
  val scopeItems = listOf(
    OrderSearchScope.ALL to R.string.customers_scope_all,
    OrderSearchScope.NAME to R.string.customers_scope_name,
    OrderSearchScope.PHONE to R.string.customers_scope_phone,
    OrderSearchScope.ID to R.string.customers_scope_id,
  )

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = stringResource(R.string.customers_search_by_label),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
      Row(
        modifier = Modifier.padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        scopeItems.forEach { (scope, labelRes) ->
          val isSelected = selectedScope == scope
          Surface(
            onClick = { onSelectScope(scope) },
            shape = RoundedCornerShape(999.dp),
            color = if (isSelected) AtelierOlive else Color.Transparent,
            contentColor = if (isSelected) OnAtelierOlive else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("orders_scope_${scope.name.lowercase()}"),
          ) {
            Box(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
              )
            }
          }
        }
      }
    }
  }
}
