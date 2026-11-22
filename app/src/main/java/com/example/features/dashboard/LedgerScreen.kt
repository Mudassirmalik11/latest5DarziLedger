package com.example.features.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.AtelierCard
import com.example.core.ui.EmptyWorkspaceCard
import com.example.core.ui.MetricTile
import com.example.core.ui.PkrFormatUtils
import com.example.core.ui.WindowWidthClass
import com.example.features.orders.OrderSummaryCard
import com.example.ui.theme.BrassSecondary
import com.example.ui.theme.IndigoTertiary
import com.example.ui.theme.PinePrimary

@Composable
fun LedgerScreen(
  viewModel: DashboardViewModel,
  widthClass: WindowWidthClass,
  onOpenOrderDetail: (orderId: String) -> Unit,
  onOpenCustomerDetail: (customerId: String) -> Unit,
  onNavigateToOrders: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val isWide = widthClass != WindowWidthClass.Compact
  var showPaymentsSheet by rememberSaveable { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("ledger_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = viewModel::updateSearchQuery,
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 980.dp)
          .testTag("dashboard_search_input"),
        placeholder = { Text(stringResource(R.string.dashboard_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
              Icon(
                Icons.Filled.Clear,
                contentDescription = stringResource(R.string.dashboard_clear_search),
              )
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
      )
    }

    if (searchQuery.isNotBlank()) {
      if (uiState.matchingCustomers.isNotEmpty()) {
        item {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 980.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.dashboard_matching_customers),
              style = MaterialTheme.typography.titleSmall,
            )
            uiState.matchingCustomers.forEach { customer ->
              AtelierCard(onClick = { onOpenCustomerDetail(customer.customerId) }) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(customer.name, style = MaterialTheme.typography.titleSmall)
                  Text(
                    customer.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }
        }
      }

      if (uiState.matchingOrders.isNotEmpty()) {
        item {
          Box(modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
            Text(
              text = stringResource(R.string.dashboard_matching_orders),
              style = MaterialTheme.typography.titleSmall,
            )
          }
        }
        items(uiState.matchingOrders, key = { "ledger_search_${it.order.orderId}" }) { item ->
          Box(modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
            OrderSummaryCard(
              item = item,
              onClick = { onOpenOrderDetail(item.order.orderId) },
            )
          }
        }
      }
    } else {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 980.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = stringResource(R.string.dashboard_today_summary),
            style = MaterialTheme.typography.titleMedium,
          )

          if (isWide) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              MetricTile(
                label = stringResource(R.string.dashboard_pending_orders),
                value = "${uiState.pendingCount}",
                icon = Icons.Filled.Schedule,
                accentColor = BrassSecondary,
                modifier = Modifier.weight(1f),
              )
              MetricTile(
                label = stringResource(R.string.dashboard_due_today),
                value = "${uiState.dueTodayCount}",
                icon = Icons.Filled.EventAvailable,
                accentColor = IndigoTertiary,
                modifier = Modifier.weight(1f),
              )
              MetricTile(
                label = stringResource(R.string.dashboard_ready_pickup),
                value = "${uiState.readyCount}",
                icon = Icons.Filled.CheckCircle,
                accentColor = PinePrimary,
                modifier = Modifier.weight(1f),
              )
              MetricTile(
                label = stringResource(R.string.dashboard_today_collection),
                value = PkrFormatUtils.formatPkr(uiState.todayCollectionPkr),
                icon = Icons.Filled.Payments,
                accentColor = PinePrimary,
                modifier = Modifier
                  .weight(1f)
                  .clickable { showPaymentsSheet = true },
              )
              MetricTile(
                label = stringResource(R.string.dashboard_outstanding_pkr),
                value = PkrFormatUtils.formatPkr(uiState.outstandingPkr),
                icon = Icons.Filled.AccountBalanceWallet,
                accentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier
                  .weight(1f)
                  .clickable { showPaymentsSheet = true },
              )
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              MetricTile(
                label = stringResource(R.string.dashboard_pending_orders),
                value = "${uiState.pendingCount}",
                icon = Icons.Filled.Schedule,
                accentColor = BrassSecondary,
                modifier = Modifier.weight(1f),
              )
              MetricTile(
                label = stringResource(R.string.dashboard_due_today),
                value = "${uiState.dueTodayCount}",
                icon = Icons.Filled.EventAvailable,
                accentColor = IndigoTertiary,
                modifier = Modifier.weight(1f),
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              MetricTile(
                label = stringResource(R.string.dashboard_ready_pickup),
                value = "${uiState.readyCount}",
                icon = Icons.Filled.CheckCircle,
                accentColor = PinePrimary,
                modifier = Modifier.weight(1f),
              )
              MetricTile(
                label = stringResource(R.string.dashboard_today_collection),
                value = PkrFormatUtils.formatPkr(uiState.todayCollectionPkr),
                icon = Icons.Filled.Payments,
                accentColor = PinePrimary,
                modifier = Modifier
                  .weight(1f)
                  .clickable { showPaymentsSheet = true },
              )
            }
            MetricTile(
              label = stringResource(R.string.dashboard_outstanding_pkr),
              value = PkrFormatUtils.formatPkr(uiState.outstandingPkr),
              icon = Icons.Filled.AccountBalanceWallet,
              accentColor = MaterialTheme.colorScheme.error,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { showPaymentsSheet = true },
            )
          }
        }
      }

      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 980.dp),
        ) {
          DashboardAnalyticsCard(
            analytics = uiState.analytics,
            isWide = isWide,
            onSelectPeriod = viewModel::selectAnalyticsPeriod,
          )
        }
      }

      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 980.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.dashboard_recent_orders),
            style = MaterialTheme.typography.titleMedium,
          )
          OutlinedButton(
            onClick = onNavigateToOrders,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("dashboard_quick_orders"),
          ) {
            Text(stringResource(R.string.dashboard_all_orders))
          }
        }
      }

      if (uiState.recentOrders.isEmpty()) {
        item {
          Box(modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
            EmptyWorkspaceCard(
              icon = Icons.AutoMirrored.Filled.ReceiptLong,
              title = stringResource(R.string.orders_empty_title),
              description = stringResource(R.string.dashboard_no_recent_orders),
            )
          }
        }
      } else {
        items(uiState.recentOrders, key = { "ledger_recent_${it.order.orderId}" }) { item ->
          Box(modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp)) {
            OrderSummaryCard(
              item = item,
              onClick = { onOpenOrderDetail(item.order.orderId) },
            )
          }
        }
      }
    }
  }

  if (showPaymentsSheet) {
    PaymentsOverviewSheet(
      todayCollectionPkr = uiState.todayCollectionPkr,
      outstandingPkr = uiState.outstandingPkr,
      unpaidOrders = uiState.unpaidOrders,
      paidOrders = uiState.paidOrders,
      onDismiss = { showPaymentsSheet = false },
      onOpenOrderDetail = onOpenOrderDetail,
    )
  }
}
