package com.example.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ui.AtelierCard
import com.example.core.ui.EmptyWorkspaceCard
import com.example.core.ui.PkrFormatUtils
import com.example.core.ui.StatusPill
import com.example.data.local.daos.OrderWithCustomer
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingFg
import com.example.ui.theme.StatusReadyBg
import com.example.ui.theme.StatusReadyFg

private enum class PaymentsFilter {
  BALANCE_DUE,
  PAID_ORDERS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsOverviewSheet(
  todayCollectionPkr: Long,
  outstandingPkr: Long,
  unpaidOrders: List<OrderWithCustomer>,
  paidOrders: List<OrderWithCustomer>,
  onDismiss: () -> Unit,
  onOpenOrderDetail: (orderId: String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedFilter by rememberSaveable { mutableStateOf(PaymentsFilter.BALANCE_DUE) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.testTag("payments_sheet"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.AccountBalanceWallet,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Column {
          Text(
            text = stringResource(R.string.payments_sheet_title),
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = stringResource(R.string.payments_tap_to_record),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          modifier = Modifier.weight(1f),
          shape = MaterialTheme.shapes.medium,
          color = StatusReadyBg,
          contentColor = StatusReadyFg,
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = stringResource(R.string.dashboard_today_collection),
              style = MaterialTheme.typography.labelMedium,
            )
            Text(
              text = PkrFormatUtils.formatPkr(todayCollectionPkr),
              style = MonoMetricStyle,
            )
          }
        }
        Surface(
          modifier = Modifier.weight(1f),
          shape = MaterialTheme.shapes.medium,
          color = StatusPendingBg,
          contentColor = StatusPendingFg,
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = stringResource(R.string.dashboard_outstanding_pkr),
              style = MaterialTheme.typography.labelMedium,
            )
            Text(
              text = PkrFormatUtils.formatPkr(outstandingPkr),
              style = MonoMetricStyle,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = selectedFilter == PaymentsFilter.BALANCE_DUE,
          onClick = { selectedFilter = PaymentsFilter.BALANCE_DUE },
          label = { Text("${stringResource(R.string.payments_filter_balance_due)} (${unpaidOrders.size})") },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("payments_filter_due"),
        )
        FilterChip(
          selected = selectedFilter == PaymentsFilter.PAID_ORDERS,
          onClick = { selectedFilter = PaymentsFilter.PAID_ORDERS },
          label = { Text("${stringResource(R.string.payments_filter_paid)} (${paidOrders.size})") },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("payments_filter_paid"),
        )
      }

      val visibleOrders = if (selectedFilter == PaymentsFilter.BALANCE_DUE) unpaidOrders else paidOrders

      if (visibleOrders.isEmpty()) {
        EmptyWorkspaceCard(
          icon = if (selectedFilter == PaymentsFilter.BALANCE_DUE) {
            Icons.Filled.CheckCircle
          } else {
            Icons.Filled.Payments
          },
          title = stringResource(R.string.payments_sheet_title),
          description = stringResource(
            if (selectedFilter == PaymentsFilter.BALANCE_DUE) {
              R.string.payments_empty_due
            } else {
              R.string.payments_empty_paid
            },
          ),
        )
        Spacer(modifier = Modifier.height(16.dp))
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
          contentPadding = PaddingValues(bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(visibleOrders, key = { "pay_${it.order.orderId}" }) { item ->
            val order = item.order
            val orderLabel = order.orderNumber?.let { "#$it" } ?: stringResource(R.string.order_number_pending)
            AtelierCard(
              onClick = {
                onDismiss()
                onOpenOrderDetail(order.orderId)
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("payments_order_${order.orderId}"),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Text(
                    text = "$orderLabel • ${item.customerName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = stringResource(
                      R.string.payments_paid_of_total,
                      PkrFormatUtils.formatPkr(order.paidAmountPkr),
                      PkrFormatUtils.formatPkr(order.totalAmountPkr),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }

                if (order.remainingAmountPkr > 0L) {
                  StatusPill(
                    label = stringResource(
                      R.string.payments_due_amount,
                      PkrFormatUtils.formatPkr(order.remainingAmountPkr),
                    ),
                    containerColor = StatusPendingBg,
                    contentColor = StatusPendingFg,
                  )
                } else {
                  StatusPill(
                    label = PkrFormatUtils.formatPkr(order.paidAmountPkr),
                    containerColor = StatusReadyBg,
                    contentColor = StatusReadyFg,
                    icon = Icons.Filled.CheckCircle,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
