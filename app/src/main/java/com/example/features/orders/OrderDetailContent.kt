package com.example.features.orders

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PaymentStatus
import com.example.core.ui.DateFormatUtils
import com.example.core.ui.PkrFormatUtils
import com.example.core.ui.StatusPill
import com.example.features.customers.MeasurementMetricSpec
import com.example.features.customers.MeasurementMetricTile
import com.example.features.customers.StyleChoiceTile
import com.example.features.customers.TailorBadgeGlyph
import com.example.features.customers.localizedDamanStyle
import com.example.features.customers.localizedNeckStyle
import com.example.features.customers.localizedPocketStyles
import com.example.features.customers.localizedSilaiStyle
import com.example.features.customers.localizedSleeveStyle
import com.example.features.printing.PrintSlipType
import com.example.ui.theme.AtelierLandingDarkCanvas
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.AtelierOliveDarkBadge
import com.example.ui.theme.AtelierOliveDarkCard
import com.example.ui.theme.AtelierUrgentCoral
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OnAtelierOlive

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderDetailContent(
  uiState: OrderDetailUiState,
  actionError: String?,
  onClearError: () -> Unit,
  onUpdateStatus: (OrderStatus) -> Unit,
  onRecordPayment: (amountText: String, method: PaymentMethod, note: String, onSuccess: () -> Unit) -> Unit,
  onSettleRemaining: (PaymentMethod) -> Unit,
  onOpenCustomer: (customerId: String) -> Unit,
  modifier: Modifier = Modifier,
  onDeleteOrder: () -> Unit = {},
  isPrinting: Boolean = false,
  previewSlip: Pair<PrintSlipType, String>? = null,
  onPrintCustomerReceipt: () -> Unit = {},
  onPrintKarigarSlip: () -> Unit = {},
  onOpenPreview: (PrintSlipType) -> Unit = {},
  onDismissPreview: () -> Unit = {},
  onAddMediaUris: (List<Uri>) -> Unit = {},
  onRemoveMediaUri: (String) -> Unit = {},
  isOwner: Boolean = true,
) {
  val item = uiState.orderWithCustomer ?: return
  val order = item.order

  var showPaymentDialog by rememberSaveable { mutableStateOf(false) }
  var showDeleteOrCancelDialog by rememberSaveable { mutableStateOf(false) }
  var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

  val isDarkCanvas = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val canvasColor = if (isDarkCanvas) AtelierLandingDarkCanvas else MaterialTheme.colorScheme.background
  val cardSurface = if (isDarkCanvas) AtelierOliveDarkCard else MaterialTheme.colorScheme.surfaceContainerLow
  val badgeSurface = if (isDarkCanvas) AtelierOliveDarkBadge else MaterialTheme.colorScheme.surfaceContainerHigh
  val primaryText = if (isDarkCanvas) OnAtelierOlive else MaterialTheme.colorScheme.onSurface
  val mutedText = if (isDarkCanvas) OnAtelierOlive.copy(alpha = 0.68f) else MaterialTheme.colorScheme.onSurfaceVariant
  val dividerColor = if (isDarkCanvas) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant
  val tileIconTint = if (isDarkCanvas) Color(0xFFB3BEA4) else AtelierOlive

  val displayOrderId = order.orderNumber?.toString() ?: order.orderId.take(4)
  val canCollectPayment = order.remainingAmountPkr > 0L && order.orderStatus != OrderStatus.CANCELLED
  val canCollectAsOwner = isOwner && canCollectPayment
  val canDeleteAsOwner = isOwner
  val disabledTileSurface = if (isDarkCanvas) {
    Color.White.copy(alpha = 0.06f)
  } else {
    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
  }
  val disabledTileBorder = if (isDarkCanvas) {
    Color.White.copy(alpha = 0.05f)
  } else {
    dividerColor.copy(alpha = 0.45f)
  }
  val disabledIconTint = if (isDarkCanvas) {
    OnAtelierOlive.copy(alpha = 0.34f)
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
  }
  val disabledTextColor = if (isDarkCanvas) {
    OnAtelierOlive.copy(alpha = 0.40f)
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.44f)
  }
  val allowedNextStatuses = order.orderStatus.allowedNextStatuses()

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
    onResult = { uris ->
      if (uris.isNotEmpty()) {
        onAddMediaUris(uris)
      }
    },
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(canvasColor),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 820.dp)
        .testTag("order_detail_content"),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      // Customer Name + Order ID Pill Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .clickable { onOpenCustomer(order.customerId) }
              .padding(vertical = 4.dp, horizontal = 2.dp),
          ) {
            Text(
              text = item.customerName,
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
              color = primaryText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (item.customerPhone.isNotBlank()) {
              Text(
                text = item.customerPhone,
                style = MaterialTheme.typography.bodySmall,
                color = mutedText,
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(999.dp),
            color = cardSurface,
            border = BorderStroke(1.dp, dividerColor),
            modifier = Modifier.testTag("order_detail_number"),
          ) {
            Text(
              text = stringResource(R.string.order_detail_id_pill, displayOrderId),
              style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText,
              ),
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
          }
        }
      }

      // Main Dark Summary Card
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = cardSurface,
          border = BorderStroke(1.dp, dividerColor),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("order_billing_card"),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            // Row 1: Date Created | Delivery Date
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              DetailSummaryMetric(
                icon = Icons.Outlined.CalendarToday,
                label = stringResource(R.string.order_detail_date_created),
                value = DateFormatUtils.formatDate(order.createdAt),
                badgeSurface = badgeSurface,
                iconTint = tileIconTint,
                labelColor = mutedText,
                valueColor = primaryText,
                modifier = Modifier.weight(1f),
              )
              DetailSummaryMetric(
                icon = Icons.Outlined.LocalShipping,
                label = stringResource(R.string.order_detail_delivery_date),
                value = DateFormatUtils.formatDate(order.deliveryDate),
                badgeSurface = badgeSurface,
                iconTint = tileIconTint,
                labelColor = mutedText,
                valueColor = primaryText,
                modifier = Modifier.weight(1f),
              )
            }

            HorizontalDivider(color = dividerColor)

            // Row 2: Quantity | Pending Amount
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              DetailSummaryMetric(
                icon = Icons.Outlined.Inventory2,
                label = stringResource(R.string.order_detail_quantity),
                value = "${order.suitCount}",
                badgeSurface = badgeSurface,
                iconTint = tileIconTint,
                labelColor = mutedText,
                valueColor = primaryText,
                modifier = Modifier.weight(1f),
              )
              DetailSummaryMetric(
                icon = Icons.Outlined.Schedule,
                label = stringResource(R.string.order_detail_pending_amount),
                value = PkrFormatUtils.formatPkr(order.remainingAmountPkr),
                badgeSurface = badgeSurface,
                iconTint = if (canCollectPayment) AtelierUrgentCoral else tileIconTint,
                labelColor = mutedText,
                valueColor = primaryText,
                modifier = Modifier.weight(1f),
              )
            }

            HorizontalDivider(color = dividerColor)

            // Row 3: Total Cost | Status
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              DetailSummaryMetric(
                icon = Icons.Outlined.CreditCard,
                label = stringResource(R.string.order_detail_total_cost),
                value = PkrFormatUtils.formatPkr(order.totalAmountPkr),
                badgeSurface = badgeSurface,
                iconTint = tileIconTint,
                labelColor = mutedText,
                valueColor = primaryText,
                modifier = Modifier.weight(1f),
              )

              val paymentStatusLabel = when (order.paymentStatus) {
                PaymentStatus.PAID -> stringResource(R.string.order_detail_payment_paid)
                PaymentStatus.PARTIAL -> stringResource(R.string.order_detail_payment_partial)
                PaymentStatus.UNPAID -> stringResource(R.string.order_detail_payment_not_paid)
              }
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                DetailSummaryMetric(
                  icon = Icons.Outlined.CheckCircle,
                  label = stringResource(R.string.order_detail_status_label),
                  value = paymentStatusLabel,
                  badgeSurface = badgeSurface,
                  iconTint = tileIconTint,
                  labelColor = mutedText,
                  valueColor = primaryText,
                )
                Box(modifier = Modifier.padding(start = 46.dp)) {
                  OrderStatusBadge(status = order.orderStatus)
                }
              }
            }

            HorizontalDivider(color = dividerColor)

            // Row 4: Additional Note
            DetailSummaryMetric(
              icon = Icons.Outlined.Description,
              label = stringResource(R.string.order_detail_additional_note),
              value = order.notes.ifBlank { stringResource(R.string.order_detail_no_note) },
              badgeSurface = badgeSurface,
              iconTint = tileIconTint,
              labelColor = mutedText,
              valueColor = if (order.notes.isNotBlank()) primaryText else mutedText,
              valueFontWeight = if (order.notes.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
              maxValueLines = 3,
              modifier = Modifier.fillMaxWidth(),
            )

            // Compact Workflow Status Buttons (when Ready / Delivered / Reopen allowed)
            val workflowTransitions = allowedNextStatuses.filter { it != OrderStatus.CANCELLED }
            if (workflowTransitions.isNotEmpty()) {
              HorizontalDivider(color = dividerColor)
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                if (OrderStatus.READY in workflowTransitions) {
                  Button(
                    onClick = { onUpdateStatus(OrderStatus.READY) },
                    colors = ButtonDefaults.buttonColors(
                      containerColor = AtelierOlive,
                      contentColor = OnAtelierOlive,
                    ),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("order_mark_ready_button"),
                  ) {
                    Text(stringResource(R.string.order_mark_ready))
                  }
                }
                if (OrderStatus.DELIVERED in workflowTransitions) {
                  Button(
                    onClick = { onUpdateStatus(OrderStatus.DELIVERED) },
                    colors = ButtonDefaults.buttonColors(
                      containerColor = AtelierOlive,
                      contentColor = OnAtelierOlive,
                    ),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("order_mark_delivered_button"),
                  ) {
                    Text(stringResource(R.string.order_mark_delivered))
                  }
                }
                if (OrderStatus.PENDING in workflowTransitions) {
                  OutlinedButton(
                    onClick = { onUpdateStatus(OrderStatus.PENDING) },
                    modifier = Modifier.heightIn(min = 48.dp),
                  ) {
                    Text(stringResource(R.string.order_reopen_pending))
                  }
                }
              }
            }
          }
        }
      }

      // Actions Section
      item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = stringResource(R.string.order_detail_actions_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = primaryText,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OrderActionTile(
              icon = Icons.Outlined.CheckCircle,
              label = stringResource(R.string.order_action_mark_paid),
              surfaceColor = if (canCollectAsOwner) cardSurface else disabledTileSurface,
              borderColor = if (canCollectAsOwner) dividerColor else disabledTileBorder,
              iconTint = if (canCollectAsOwner) tileIconTint else disabledIconTint,
              textColor = if (canCollectAsOwner) primaryText else disabledTextColor,
              enabled = canCollectAsOwner,
              onClick = {
                if (isOwner) onSettleRemaining(PaymentMethod.CASH)
              },
              modifier = Modifier
                .weight(1f)
                .testTag("order_settle_remaining_button"),
            )

            OrderActionTile(
              icon = Icons.Outlined.DonutLarge,
              label = stringResource(R.string.order_action_partial_payment),
              surfaceColor = if (canCollectAsOwner) cardSurface else disabledTileSurface,
              borderColor = if (canCollectAsOwner) dividerColor else disabledTileBorder,
              iconTint = if (canCollectAsOwner) tileIconTint else disabledIconTint,
              textColor = if (canCollectAsOwner) primaryText else disabledTextColor,
              enabled = canCollectAsOwner,
              onClick = {
                if (isOwner) {
                  onClearError()
                  showPaymentDialog = true
                }
              },
              modifier = Modifier
                .weight(1f)
                .testTag("order_record_payment_button"),
            )

            OrderActionTile(
              icon = Icons.Outlined.Delete,
              label = stringResource(R.string.order_action_delete),
              surfaceColor = if (canDeleteAsOwner) cardSurface else disabledTileSurface,
              borderColor = if (canDeleteAsOwner) dividerColor else disabledTileBorder,
              iconTint = if (canDeleteAsOwner) AtelierUrgentCoral else disabledIconTint,
              textColor = if (canDeleteAsOwner) AtelierUrgentCoral else disabledTextColor,
              enabled = canDeleteAsOwner,
              onClick = {
                if (isOwner) showDeleteOrCancelDialog = true
              },
              modifier = Modifier
                .weight(1f)
                .testTag("order_cancel_button"),
            )

            OrderActionTile(
              icon = Icons.Outlined.Save,
              label = stringResource(R.string.order_action_view_receipt),
              surfaceColor = cardSurface,
              borderColor = dividerColor,
              iconTint = tileIconTint,
              textColor = primaryText,
              enabled = true,
              onClick = { onOpenPreview(PrintSlipType.CUSTOMER_RECEIPT) },
              modifier = Modifier
                .weight(1f)
                .testTag("order_preview_customer_button"),
            )
          }
        }
      }

      // Tabs Row: Payments | Measurement | Media
      item {
        val tabs = listOf(
          Triple(0, stringResource(R.string.order_tab_payments), "order_tab_payments"),
          Triple(1, stringResource(R.string.order_tab_measurement), "order_tab_measurement"),
          Triple(2, stringResource(R.string.order_tab_media), "order_tab_media"),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEach { (index, title, tag) ->
              val isSelected = selectedTabIndex == index
              Column(
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .clickable { selectedTabIndex = index }
                  .testTag(tag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = title,
                  style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                  ),
                  color = if (isSelected) primaryText else mutedText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                )
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(if (isSelected) primaryText else Color.Transparent),
                )
              }
            }
          }
          HorizontalDivider(color = dividerColor)
        }
      }

      // Active Tab Body
      item {
        when (selectedTabIndex) {
          0 -> PaymentsTabSection(
            payments = uiState.payments,
            cardSurface = cardSurface,
            dividerColor = dividerColor,
            mutedText = mutedText,
          )
          1 -> MeasurementTabSection(
            measurementSnapshot = uiState.measurementSnapshot,
            styleSnapshot = uiState.styleSnapshot,
            sourceMeasurementLabel = uiState.sourceMeasurementLabel,
            cardSurface = cardSurface,
            dividerColor = dividerColor,
            primaryText = primaryText,
            mutedText = mutedText,
          )
          else -> MediaTabSection(
            mediaUris = uiState.mediaUris,
            cardSurface = cardSurface,
            dividerColor = dividerColor,
            primaryText = primaryText,
            mutedText = mutedText,
            onPickPhotos = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
              )
            },
            onRemoveUri = onRemoveMediaUri,
          )
        }
      }

      // Scroll-below Thermal Printing & Karigar Workshop Slip Controls
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = cardSurface,
          border = BorderStroke(1.dp, dividerColor),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("order_printing_card"),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Print,
                  contentDescription = null,
                  tint = AtelierOlive,
                )
                Text(
                  text = stringResource(R.string.order_printing_section),
                  style = MaterialTheme.typography.titleMedium,
                  color = primaryText,
                )
              }
              val printer = uiState.configuredPrinter
              StatusPill(
                label = printer?.let { "${it.paperWidthMm}mm" } ?: stringResource(R.string.printer_status_not_set),
                containerColor = if (printer != null) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  badgeSurface
                },
                contentColor = if (printer != null) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  mutedText
                },
              )
            }

            val printerLabel = uiState.configuredPrinter?.let {
              stringResource(R.string.order_printer_configured, it.printerName, it.printerAddress)
            } ?: stringResource(R.string.order_printer_unconfigured)
            Text(
              text = printerLabel,
              style = MaterialTheme.typography.bodySmall,
              color = mutedText,
            )

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { onOpenPreview(PrintSlipType.KARIGAR_SLIP) },
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("order_preview_karigar_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Visibility,
                  contentDescription = null,
                  modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp),
                )
                Text(stringResource(R.string.order_preview_karigar_button))
              }

              Button(
                onClick = onPrintCustomerReceipt,
                enabled = !isPrinting,
                colors = ButtonDefaults.buttonColors(
                  containerColor = AtelierOlive,
                  contentColor = OnAtelierOlive,
                ),
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("order_print_customer_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Print,
                  contentDescription = null,
                  modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp),
                )
                Text(stringResource(R.string.order_print_customer))
              }

              OutlinedButton(
                onClick = onPrintKarigarSlip,
                enabled = !isPrinting,
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("order_print_karigar_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Print,
                  contentDescription = null,
                  modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp),
                )
                Text(stringResource(R.string.order_print_karigar))
              }
            }
          }
        }
      }
    }
  }

  if (showPaymentDialog && isOwner) {
    RecordPaymentDialog(
      maxRemainingPkr = order.remainingAmountPkr,
      errorMessage = actionError,
      onDismiss = { showPaymentDialog = false },
      onConfirm = { amountText, method, note ->
        onRecordPayment(amountText, method, note) {
          showPaymentDialog = false
        }
      },
    )
  }

  if (showDeleteOrCancelDialog && isOwner) {
    val canCancel = OrderStatus.CANCELLED in allowedNextStatuses
    AlertDialog(
      onDismissRequest = { showDeleteOrCancelDialog = false },
      title = { Text(stringResource(R.string.order_delete_confirm_title)) },
      text = { Text(stringResource(R.string.order_delete_confirm_desc)) },
      confirmButton = {
        Button(
          onClick = {
            showDeleteOrCancelDialog = false
            onDeleteOrder()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("order_confirm_delete_button"),
        ) {
          Text(stringResource(R.string.order_delete_forever_button))
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (canCancel) {
            OutlinedButton(
              onClick = {
                showDeleteOrCancelDialog = false
                onUpdateStatus(OrderStatus.CANCELLED)
              },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("order_confirm_cancel_button"),
            ) {
              Text(stringResource(R.string.order_cancel_action))
            }
          }
          TextButton(
            onClick = { showDeleteOrCancelDialog = false },
            modifier = Modifier.heightIn(min = 48.dp),
          ) {
            Text(stringResource(R.string.order_keep_button))
          }
        }
      },
    )
  }

  if (previewSlip != null) {
    val (slipType, previewBody) = previewSlip
    val slipDirection = LocalLayoutDirection.current
    val localizedContext = LocalContext.current
    val localizedConfig = LocalConfiguration.current
    val previewTitle = stringResource(
      if (slipType == PrintSlipType.KARIGAR_SLIP) {
        R.string.order_preview_karigar_title
      } else {
        R.string.order_preview_customer_title
      },
    )
    val printNowLabel = stringResource(R.string.action_print_now)
    val closeLabel = stringResource(R.string.action_close)
    val textDirection = if (slipDirection == LayoutDirection.Rtl) {
      TextDirection.Rtl
    } else {
      TextDirection.Ltr
    }
    AlertDialog(
      onDismissRequest = onDismissPreview,
      modifier = Modifier.testTag("slip_preview_dialog"),
      title = {
        CompositionLocalProvider(
          LocalContext provides localizedContext,
          LocalConfiguration provides localizedConfig,
          LocalLayoutDirection provides slipDirection,
        ) {
          Text(text = previewTitle)
        }
      },
      text = {
        CompositionLocalProvider(
          LocalContext provides localizedContext,
          LocalConfiguration provides localizedConfig,
          LocalLayoutDirection provides slipDirection,
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 420.dp),
          ) {
            Text(
              text = previewBody,
              softWrap = false,
              style = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                lineHeight = 18.sp,
                textDirection = textDirection,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
                .testTag("slip_preview_text"),
            )
          }
        }
      },
      confirmButton = {
        CompositionLocalProvider(
          LocalContext provides localizedContext,
          LocalConfiguration provides localizedConfig,
          LocalLayoutDirection provides slipDirection,
        ) {
          Button(
            onClick = {
              onDismissPreview()
              if (slipType == PrintSlipType.KARIGAR_SLIP) {
                onPrintKarigarSlip()
              } else {
                onPrintCustomerReceipt()
              }
            },
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("slip_preview_print_button"),
          ) {
            Text(printNowLabel)
          }
        }
      },
      dismissButton = {
        CompositionLocalProvider(
          LocalContext provides localizedContext,
          LocalConfiguration provides localizedConfig,
          LocalLayoutDirection provides slipDirection,
        ) {
          TextButton(
            onClick = onDismissPreview,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("slip_preview_close_button"),
          ) {
            Text(closeLabel)
          }
        }
      },
    )
  }
}

@Composable
private fun DetailSummaryMetric(
  icon: ImageVector,
  label: String,
  value: String,
  badgeSurface: Color,
  iconTint: Color,
  labelColor: Color,
  valueColor: Color,
  modifier: Modifier = Modifier,
  valueFontWeight: FontWeight = FontWeight.Bold,
  maxValueLines: Int = 1,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = badgeSurface,
      modifier = Modifier.size(36.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(19.dp),
        )
      }
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = labelColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        style = TextStyle(
          fontSize = 14.sp,
          fontWeight = valueFontWeight,
          color = valueColor,
        ),
        maxLines = maxValueLines,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun OrderActionTile(
  icon: ImageVector,
  label: String,
  surfaceColor: Color,
  borderColor: Color,
  iconTint: Color,
  textColor: Color,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(14.dp),
    color = surfaceColor,
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier.heightIn(min = 88.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(26.dp),
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = textColor,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun PaymentsTabSection(
  payments: List<com.example.data.local.entities.PaymentEntity>,
  cardSurface: Color,
  dividerColor: Color,
  mutedText: Color,
) {
  if (payments.isEmpty()) {
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = cardSurface,
      border = BorderStroke(1.dp, dividerColor),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = stringResource(R.string.order_payments_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = mutedText,
        modifier = Modifier.padding(16.dp),
      )
    }
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      payments.forEach { payment ->
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = AtelierOlive,
          contentColor = OnAtelierOlive,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("order_payment_item_${payment.paymentId}"),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = stringResource(
                  R.string.order_payment_item_amount,
                  PkrFormatUtils.formatPkr(payment.amountPkr),
                ),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OnAtelierOlive,
              )
              Text(
                text = stringResource(
                  R.string.order_payment_item_date,
                  DateFormatUtils.formatDate(payment.createdAt),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = OnAtelierOlive.copy(alpha = 0.92f),
              )
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color.Black.copy(alpha = 0.22f),
              contentColor = OnAtelierOlive,
            ) {
              Text(
                text = com.example.features.customers.localizedPaymentMethod(payment.paymentMethod),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MeasurementTabSection(
  measurementSnapshot: com.example.core.model.MeasurementSnapshot?,
  styleSnapshot: com.example.core.model.StyleSnapshot?,
  sourceMeasurementLabel: String?,
  cardSurface: Color,
  dividerColor: Color,
  primaryText: Color,
  mutedText: Color,
) {
  if (measurementSnapshot == null || styleSnapshot == null) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = cardSurface,
      border = BorderStroke(1.dp, dividerColor),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = stringResource(R.string.customer_no_measurements),
        style = MaterialTheme.typography.bodyMedium,
        color = mutedText,
        modifier = Modifier.padding(16.dp),
      )
    }
    return
  }

  val measurementPairs = listOf(
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.KAMEEZ_LENGTH,
      label = stringResource(R.string.measurement_label_kameez_length),
      value = DateFormatUtils.formatInches(measurementSnapshot.kameezLambai),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.SHOULDER,
      label = stringResource(R.string.measurement_label_shoulder),
      value = DateFormatUtils.formatInches(measurementSnapshot.tera),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.CHEST,
      label = stringResource(R.string.measurement_label_chest),
      value = DateFormatUtils.formatInches(measurementSnapshot.chati),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.SLEEVES,
      label = stringResource(R.string.measurement_label_sleeves),
      value = DateFormatUtils.formatInches(measurementSnapshot.bazu),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.WAIST,
      label = stringResource(R.string.measurement_label_waist),
      value = DateFormatUtils.formatInches(measurementSnapshot.kamar),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.GHERA,
      label = stringResource(R.string.measurement_label_ghera_daman),
      value = DateFormatUtils.formatInches(measurementSnapshot.gheraDaman),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.COLLAR,
      label = stringResource(R.string.measurement_label_collar_gala),
      value = DateFormatUtils.formatInches(measurementSnapshot.galaCollar),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.CUFF,
      label = stringResource(R.string.measurement_label_cuff),
      value = DateFormatUtils.formatInches(measurementSnapshot.cuff),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.SHALWAR,
      label = stringResource(R.string.measurement_label_shalwar),
      value = DateFormatUtils.formatInches(measurementSnapshot.shalwarLambai),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.PAINCHA,
      label = stringResource(R.string.measurement_label_paincha),
      value = DateFormatUtils.formatInches(measurementSnapshot.paincha),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.ASAN,
      label = stringResource(R.string.measurement_label_asan),
      value = DateFormatUtils.formatInches(measurementSnapshot.gheraAsan),
    ),
  )

  val styleItems = listOf(
    TailorBadgeGlyph.COLLAR to localizedNeckStyle(styleSnapshot.neckStyle),
    TailorBadgeGlyph.POCKET to localizedPocketStyles(styleSnapshot.effectivePocketStyles()),
    TailorBadgeGlyph.CUFF to localizedSleeveStyle(styleSnapshot.sleeveStyle),
    TailorBadgeGlyph.SILAI to localizedSilaiStyle(styleSnapshot.silaiStyle),
    TailorBadgeGlyph.DAMAN to localizedDamanStyle(styleSnapshot.damanStyle),
  )

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = cardSurface,
    border = BorderStroke(1.dp, dividerColor),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("order_measurement_tab_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = AtelierOlive,
            )
            Text(
              text = stringResource(R.string.order_snapshot_section),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = primaryText,
            )
          }
          if (!sourceMeasurementLabel.isNullOrBlank()) {
            val resolvedLabel = if (sourceMeasurementLabel.equals("Standard", ignoreCase = true)) {
              stringResource(R.string.measurement_type_male_shalwar_kameez)
            } else {
              sourceMeasurementLabel
            }
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = AtelierOlive.copy(alpha = 0.22f),
              modifier = Modifier.testTag("order_measurement_label_pill"),
            ) {
              Text(
                text = resolvedLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }
        }

        Text(
          text = stringResource(R.string.order_snapshot_immutable_hint),
          style = MaterialTheme.typography.bodySmall,
          color = mutedText,
        )
      }

      HorizontalDivider(color = dividerColor)

      // 2-Column Measurement Metrics Grid (matching Customer Detail measurements card)
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        measurementPairs.chunked(2).forEach { pair ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            pair.forEach { metric ->
              MeasurementMetricTile(
                glyph = metric.glyph,
                label = metric.label,
                value = metric.value,
                labelColor = mutedText,
                valueColor = primaryText,
                modifier = Modifier.weight(1f),
              )
            }
            if (pair.size == 1) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }

      HorizontalDivider(color = dividerColor)

      // 2-Column Stitching Styles Grid
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        styleItems.chunked(2).forEach { pair ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            pair.forEach { (glyph, styleLabel) ->
              StyleChoiceTile(
                glyph = glyph,
                label = styleLabel,
                textColor = primaryText,
                modifier = Modifier.weight(1f),
              )
            }
            if (pair.size == 1) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }

      if (measurementSnapshot.notes.isNotBlank()) {
        HorizontalDivider(color = dividerColor)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = stringResource(R.string.order_detail_additional_note),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = primaryText,
          )
          Text(
            text = measurementSnapshot.notes,
            style = MaterialTheme.typography.bodyMedium,
            color = mutedText,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaTabSection(
  mediaUris: List<String>,
  cardSurface: Color,
  dividerColor: Color,
  primaryText: Color,
  mutedText: Color,
  onPickPhotos: () -> Unit,
  onRemoveUri: (String) -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = cardSurface,
    border = BorderStroke(1.dp, dividerColor),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("order_media_tab_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.PhotoLibrary,
            contentDescription = null,
            tint = AtelierOlive,
          )
          Text(
            text = stringResource(R.string.order_tab_media),
            style = MaterialTheme.typography.titleMedium,
            color = primaryText,
          )
        }

        Button(
          onClick = onPickPhotos,
          colors = ButtonDefaults.buttonColors(
            containerColor = AtelierOlive,
            contentColor = OnAtelierOlive,
          ),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("order_media_add_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.AddPhotoAlternate,
            contentDescription = null,
            modifier = Modifier
              .padding(end = 6.dp)
              .size(18.dp),
          )
          Text(stringResource(R.string.order_media_add_photo))
        }
      }

      if (mediaUris.isEmpty()) {
        Text(
          text = stringResource(R.string.order_media_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = mutedText,
        )
      } else {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          maxItemsInEachRow = 3,
        ) {
          mediaUris.forEach { uriString ->
            Box(
              modifier = Modifier
                .size(104.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.25f)),
            ) {
              AsyncImage(
                model = uriString,
                contentDescription = stringResource(R.string.order_tab_media),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
              Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(4.dp)
                  .size(28.dp),
              ) {
                IconButton(
                  onClick = { onRemoveUri(uriString) },
                  modifier = Modifier.fillMaxSize(),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.order_media_remove_cd),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordPaymentDialog(
  maxRemainingPkr: Long,
  errorMessage: String?,
  onDismiss: () -> Unit,
  onConfirm: (amountText: String, method: PaymentMethod, note: String) -> Unit,
) {
  var amountText by rememberSaveable { mutableStateOf("") }
  var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
  var note by rememberSaveable { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.order_record_payment)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = stringResource(
            R.string.order_payment_outstanding_balance,
            PkrFormatUtils.formatPkr(maxRemainingPkr),
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = amountText,
          onValueChange = { raw -> amountText = raw.filter { it.isDigit() } },
          label = { Text(stringResource(R.string.order_payment_amount_label)) },
          prefix = { Text(stringResource(R.string.currency_pkr_prefix)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_amount_input"),
        )

        Text(
          text = stringResource(R.string.order_payment_method_label),
          style = MaterialTheme.typography.labelMedium,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          PaymentMethod.entries.forEach { option ->
            FilterChip(
              selected = method == option,
              onClick = { method = option },
              label = { Text(com.example.features.customers.localizedPaymentMethod(option)) },
              modifier = Modifier.heightIn(min = 48.dp),
            )
          }
        }

        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text(stringResource(R.string.order_payment_note_label)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        if (!errorMessage.isNullOrBlank()) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("payment_dialog_error"),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(amountText, method, note) },
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("payment_confirm_button"),
      ) {
        Text(stringResource(R.string.order_payment_save_button))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp),
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}
