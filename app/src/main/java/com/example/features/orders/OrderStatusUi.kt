package com.example.features.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentStatus
import com.example.core.ui.DateFormatUtils
import com.example.core.ui.PkrFormatUtils
import com.example.core.ui.StatusPill
import com.example.data.local.daos.OrderWithCustomer
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.AtelierOliveDarkBadge
import com.example.ui.theme.AtelierPaidGreen
import com.example.ui.theme.AtelierUrgentCoral
import com.example.ui.theme.AtelierUrgentMaroonBadge
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OnAtelierOlive
import com.example.ui.theme.StatusCancelledBg
import com.example.ui.theme.StatusCancelledFg
import com.example.ui.theme.StatusDeliveredBg
import com.example.ui.theme.StatusDeliveredFg
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingFg
import com.example.ui.theme.StatusReadyBg
import com.example.ui.theme.StatusReadyFg

@Composable
fun OrderStatusBadge(
  status: OrderStatus,
  modifier: Modifier = Modifier,
) {
  val (bg, fg) = when (status) {
    OrderStatus.PENDING -> StatusPendingBg to StatusPendingFg
    OrderStatus.READY -> StatusReadyBg to StatusReadyFg
    OrderStatus.DELIVERED -> StatusDeliveredBg to StatusDeliveredFg
    OrderStatus.CANCELLED -> StatusCancelledBg to StatusCancelledFg
  }
  val label = stringResource(
    when (status) {
      OrderStatus.PENDING -> R.string.status_pending
      OrderStatus.READY -> R.string.status_ready
      OrderStatus.DELIVERED -> R.string.status_delivered
      OrderStatus.CANCELLED -> R.string.status_cancelled
    },
  )
  StatusPill(
    label = label,
    containerColor = bg,
    contentColor = fg,
    modifier = modifier,
  )
}

@Composable
fun PaymentStatusBadge(
  status: PaymentStatus,
  modifier: Modifier = Modifier,
) {
  val (bg, fg) = when (status) {
    PaymentStatus.PAID -> StatusReadyBg to StatusReadyFg
    PaymentStatus.PARTIAL -> StatusPendingBg to StatusPendingFg
    PaymentStatus.UNPAID -> StatusCancelledBg to StatusCancelledFg
  }
  val label = stringResource(
    when (status) {
      PaymentStatus.PAID -> R.string.order_detail_payment_paid
      PaymentStatus.PARTIAL -> R.string.order_detail_payment_partial
      PaymentStatus.UNPAID -> R.string.order_detail_payment_not_paid
    },
  )
  StatusPill(
    label = label,
    containerColor = bg,
    contentColor = fg,
    modifier = modifier,
  )
}

@Composable
fun OrderSummaryCard(
  item: OrderWithCustomer,
  isSelected: Boolean = false,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  customerDisplayId: String? = null,
) {
  val order = item.order
  val displayOrderId = order.orderNumber?.toString() ?: order.orderId.take(4)
  val resolvedCustomerId = customerDisplayId ?: order.customerId.take(4)
  val hasBalanceDue = order.remainingAmountPkr > 0L && order.orderStatus != OrderStatus.CANCELLED
  val stripeColor = if (hasBalanceDue) AtelierUrgentCoral else AtelierPaidGreen

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = AtelierOlive,
    contentColor = OnAtelierOlive,
    border = if (isSelected) BorderStroke(2.dp, OnAtelierOlive) else null,
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .testTag("order_card_${order.orderId}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min),
    ) {
      Box(
        modifier = Modifier
          .width(5.dp)
          .fillMaxHeight()
          .background(stripeColor),
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Top row: ORDER ID + CUSTOMER ID on left, Date + chevron on right
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = stringResource(R.string.orders_order_id_label, displayOrderId),
              style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
              ),
              color = OnAtelierOlive,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = stringResource(R.string.orders_customer_id_label, resolvedCustomerId),
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
              ),
              color = OnAtelierOlive.copy(alpha = 0.9f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.CalendarToday,
              contentDescription = null,
              tint = OnAtelierOlive.copy(alpha = 0.85f),
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = DateFormatUtils.formatDate(order.createdAt),
              style = MaterialTheme.typography.bodySmall,
              color = OnAtelierOlive.copy(alpha = 0.92f),
              maxLines = 1,
            )
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = OnAtelierOlive.copy(alpha = 0.85f),
              modifier = Modifier.size(18.dp),
            )
          }
        }

        // Customer Name + Phone
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = item.customerName,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = OnAtelierOlive,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (item.customerPhone.isNotBlank()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Phone,
                contentDescription = null,
                tint = OnAtelierOlive.copy(alpha = 0.8f),
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = item.customerPhone,
                style = MaterialTheme.typography.bodyMedium,
                color = OnAtelierOlive.copy(alpha = 0.9f),
                maxLines = 1,
              )
            }
          }
        }

        HorizontalDivider(color = OnAtelierOlive.copy(alpha = 0.22f))

        // 2x2 Metrics Grid
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OrderCardMetricTile(
              icon = Icons.Outlined.Inventory2,
              label = stringResource(R.string.orders_metric_quantity),
              value = "${order.suitCount}",
              badgeColor = AtelierOliveDarkBadge,
              iconTint = OnAtelierOlive.copy(alpha = 0.85f),
              modifier = Modifier.weight(1f),
            )
            OrderCardMetricTile(
              icon = Icons.Outlined.CreditCard,
              label = stringResource(R.string.orders_metric_cost),
              value = PkrFormatUtils.formatPkr(order.totalAmountPkr),
              badgeColor = AtelierOliveDarkBadge,
              iconTint = OnAtelierOlive.copy(alpha = 0.85f),
              modifier = Modifier.weight(1f),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OrderCardMetricTile(
              icon = Icons.Outlined.Schedule,
              label = stringResource(R.string.orders_metric_pending_amount),
              value = PkrFormatUtils.formatPkr(order.remainingAmountPkr),
              badgeColor = if (hasBalanceDue) AtelierUrgentMaroonBadge else AtelierOliveDarkBadge,
              iconTint = if (hasBalanceDue) AtelierUrgentCoral else OnAtelierOlive.copy(alpha = 0.85f),
              modifier = Modifier.weight(1f),
            )
            OrderCardMetricTile(
              icon = Icons.Outlined.LocalShipping,
              label = stringResource(R.string.orders_metric_delivery_date),
              value = DateFormatUtils.formatDate(order.deliveryDate),
              badgeColor = if (hasBalanceDue) AtelierUrgentMaroonBadge else AtelierOliveDarkBadge,
              iconTint = if (hasBalanceDue) AtelierUrgentCoral else OnAtelierOlive.copy(alpha = 0.85f),
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun OrderCardMetricTile(
  icon: ImageVector,
  label: String,
  value: String,
  badgeColor: Color,
  iconTint: Color,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = badgeColor,
      modifier = Modifier.size(38.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(20.dp),
        )
      }
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = OnAtelierOlive.copy(alpha = 0.78f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        style = TextStyle(
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = OnAtelierOlive,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
