package com.example.features.customers

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.model.PhoneNumberUtils
import com.example.core.ui.DateFormatUtils
import com.example.core.ui.PkrFormatUtils
import com.example.data.local.daos.CustomerPaymentEntry
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.entities.MeasurementEntity
import com.example.features.orders.OrderSummaryCard
import com.example.ui.theme.AtelierLandingDarkCanvas
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.AtelierOliveDarkCard
import com.example.ui.theme.AtelierOliveDialogSurface
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OnAtelierOlive
import java.util.Locale

@Composable
fun CustomerDetailContent(
  uiState: CustomerDetailUiState,
  onEditCustomer: () -> Unit,
  onAddOrEditMeasurement: (sourceMeasurementId: String?) -> Unit,
  onCreateOrderForCustomer: () -> Unit = {},
  onOpenOrder: (orderId: String) -> Unit = {},
  modifier: Modifier = Modifier,
  onSaveMeasurementCurrent: (measurementId: String) -> Unit = {},
  onDeleteMeasurement: (measurementId: String) -> Unit = {},
  onPreviewMeasurement: (MeasurementEntity) -> Unit = {},
  onPrintMeasurement: (MeasurementEntity) -> Unit = {},
  onDismissMeasurementPreview: () -> Unit = {},
  previewMeasurementSlip: Pair<MeasurementEntity, String>? = null,
  isPrinting: Boolean = false,
  onShowMessage: (String) -> Unit = {},
) {
  val customer = uiState.customer ?: return
  val context = LocalContext.current
  val whatsappErrorMsg = stringResource(R.string.customer_whatsapp_error)

  var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
  var optionsTargetMeasurementId by rememberSaveable { mutableStateOf<String?>(null) }
  var deleteTargetMeasurementId by rememberSaveable { mutableStateOf<String?>(null) }

  val isDarkCanvas = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val canvasColor = if (isDarkCanvas) AtelierLandingDarkCanvas else MaterialTheme.colorScheme.background
  val cardSurface = if (isDarkCanvas) AtelierOliveDarkCard else MaterialTheme.colorScheme.surfaceContainerLow
  val dialogSurface = if (isDarkCanvas) AtelierOliveDialogSurface else MaterialTheme.colorScheme.surfaceContainerHigh
  val primaryText = if (isDarkCanvas) OnAtelierOlive else MaterialTheme.colorScheme.onSurface
  val mutedText = if (isDarkCanvas) OnAtelierOlive.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
  val dividerColor = if (isDarkCanvas) Color.White.copy(alpha = 0.09f) else MaterialTheme.colorScheme.outlineVariant

  val resolvedIdBadge = uiState.displayId ?: customer.customerId.take(4)
  val hasPhone = customer.phone.isNotBlank()

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
        .testTag("customer_detail_content"),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      // Customer Header Info + Quick Actions (Call / Whatsapp / + Order)
      item {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = customer.name,
              style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
              color = primaryText,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            )

            Surface(
              shape = RoundedCornerShape(999.dp),
              color = cardSurface,
              border = BorderStroke(1.dp, dividerColor),
              modifier = Modifier.testTag("customer_detail_id_pill"),
            ) {
              Text(
                text = stringResource(R.string.customers_id_badge, resolvedIdBadge),
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

          if (customer.notes.isNotBlank()) {
            CustomerMetaRow(
              icon = Icons.Outlined.LocationOn,
              text = customer.notes,
              textColor = mutedText,
            )
          }

          if (hasPhone) {
            CustomerMetaRow(
              icon = Icons.Outlined.Phone,
              text = customer.phone,
              textColor = mutedText,
            )
          }

          CustomerMetaRow(
            icon = Icons.Outlined.CalendarToday,
            text = stringResource(
              R.string.customer_since_label,
              DateFormatUtils.formatFullDate(customer.createdAt),
            ),
            textColor = mutedText,
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Button(
              onClick = {
                val dialNumber = PhoneNumberUtils.normalizePakistaniPhone(customer.phone)
                  .ifEmpty { customer.phone.trim() }
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber"))
                runCatching { context.startActivity(dialIntent) }
              },
              enabled = hasPhone,
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = AtelierOlive,
                contentColor = OnAtelierOlive,
                disabledContainerColor = AtelierOlive.copy(alpha = 0.45f),
                disabledContentColor = OnAtelierOlive.copy(alpha = 0.6f),
              ),
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("customer_call_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Phone,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.customer_action_call),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
              )
            }

            Button(
              onClick = {
                val waDigits = toWhatsAppDigits(customer.phone)
                if (waDigits.isNotEmpty()) {
                  val waIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/$waDigits"),
                  )
                  runCatching { context.startActivity(waIntent) }
                    .onFailure { onShowMessage(whatsappErrorMsg) }
                }
              },
              enabled = hasPhone,
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = AtelierOlive,
                contentColor = OnAtelierOlive,
                disabledContainerColor = AtelierOlive.copy(alpha = 0.45f),
                disabledContentColor = OnAtelierOlive.copy(alpha = 0.6f),
              ),
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("customer_whatsapp_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.customer_action_whatsapp),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
              )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
              onClick = onCreateOrderForCustomer,
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = AtelierOlive,
                contentColor = OnAtelierOlive,
              ),
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("customer_new_order_button"),
            ) {
              Text(
                text = stringResource(R.string.dashboard_action_add_order),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
              )
            }
          }
        }
      }

      // 3-Tab Switcher: MEASUREMENTS | PAYMENTS | ORDERS
      item {
        val tabs = listOf(
          stringResource(R.string.customer_tab_measurements) to "customer_tab_measurements",
          stringResource(R.string.customer_tab_payments) to "customer_tab_payments",
          stringResource(R.string.customer_tab_orders) to "customer_tab_orders",
        )
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, (label, tag) ->
              val isSelected = selectedTabIndex == index
              Column(
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .clickable { selectedTabIndex = index }
                  .testTag(tag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  ),
                  color = if (isSelected) primaryText else mutedText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                )
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isSelected) 2.5.dp else 1.dp)
                    .background(if (isSelected) primaryText else dividerColor),
                )
              }
            }
          }
        }
      }

      when (selectedTabIndex) {
        0 -> {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(R.string.customer_measurements_section),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = primaryText,
              )

              Surface(
                onClick = { onAddOrEditMeasurement(null) },
                shape = RoundedCornerShape(10.dp),
                color = AtelierOlive,
                contentColor = OnAtelierOlive,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("add_measurement_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.customer_add_measurements),
                    modifier = Modifier.size(22.dp),
                  )
                }
              }
            }
          }

          if (uiState.measurementHistory.isEmpty()) {
            item {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardSurface,
                border = BorderStroke(1.dp, dividerColor),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Text(
                    text = stringResource(R.string.customer_no_measurements),
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedText,
                  )
                  Button(
                    onClick = { onAddOrEditMeasurement(null) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                      containerColor = AtelierOlive,
                      contentColor = OnAtelierOlive,
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .testTag("add_measurement_empty_button"),
                  ) {
                    Text(stringResource(R.string.customer_add_measurements))
                  }
                }
              }
            }
          } else {
            items(uiState.measurementHistory, key = { it.measurementId }) { entry ->
              MeasurementProfileCard(
                entry = entry,
                showSavedIndicator = entry.isCurrent && uiState.measurementHistory.size > 1,
                cardSurface = cardSurface,
                dividerColor = dividerColor,
                primaryText = primaryText,
                mutedText = mutedText,
                onOpenOptions = { optionsTargetMeasurementId = entry.measurementId },
              )
            }
          }
        }

        1 -> {
          if (uiState.payments.isEmpty()) {
            item {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardSurface,
                border = BorderStroke(1.dp, dividerColor),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = stringResource(R.string.customer_payments_empty),
                  style = MaterialTheme.typography.bodyMedium,
                  color = mutedText,
                  modifier = Modifier.padding(18.dp),
                )
              }
            }
          } else {
            items(uiState.payments, key = { it.paymentId }) { payment ->
              CustomerPaymentCard(
                payment = payment,
                onClick = { onOpenOrder(payment.orderId) },
              )
            }
          }
        }

        else -> {
          item {
            Text(
              text = stringResource(R.string.customer_order_history),
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              color = primaryText,
            )
          }
          if (uiState.orders.isEmpty()) {
            item {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardSurface,
                border = BorderStroke(1.dp, dividerColor),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = stringResource(R.string.customer_no_orders),
                  style = MaterialTheme.typography.bodyMedium,
                  color = mutedText,
                  modifier = Modifier.padding(18.dp),
                )
              }
            }
          } else {
            items(uiState.orders, key = { it.orderId }) { order ->
              OrderSummaryCard(
                item = OrderWithCustomer(
                  order = order,
                  customerName = customer.name,
                  customerPhone = customer.phone,
                ),
                customerDisplayId = resolvedIdBadge,
                onClick = { onOpenOrder(order.orderId) },
              )
            }
          }
        }
      }
    }
  }

  // Three-dots Options Dialog (customer.dots.jpg)
  val optionsTarget = optionsTargetMeasurementId?.let { id ->
    uiState.measurementHistory.firstOrNull { it.measurementId == id }
  }
  if (optionsTarget != null) {
    MeasurementOptionsDialog(
      dialogSurface = dialogSurface,
      primaryText = primaryText,
      borderColor = dividerColor,
      isPrinting = isPrinting,
      onDismiss = { optionsTargetMeasurementId = null },
      onEdit = {
        val targetId = optionsTarget.measurementId
        optionsTargetMeasurementId = null
        onAddOrEditMeasurement(targetId)
      },
      onSave = {
        val targetId = optionsTarget.measurementId
        optionsTargetMeasurementId = null
        onSaveMeasurementCurrent(targetId)
      },
      onPrint = {
        optionsTargetMeasurementId = null
        onPreviewMeasurement(optionsTarget)
      },
      onDelete = {
        val targetId = optionsTarget.measurementId
        optionsTargetMeasurementId = null
        deleteTargetMeasurementId = targetId
      },
    )
  }

  // Delete Measurement Confirmation Dialog
  val deleteTarget = deleteTargetMeasurementId?.let { id ->
    uiState.measurementHistory.firstOrNull { it.measurementId == id }
  }
  if (deleteTarget != null) {
    AlertDialog(
      onDismissRequest = { deleteTargetMeasurementId = null },
      modifier = Modifier.testTag("measurement_delete_confirm_dialog"),
      title = { Text(stringResource(R.string.measurement_delete_confirm_title)) },
      text = { Text(stringResource(R.string.measurement_delete_confirm_desc)) },
      confirmButton = {
        Button(
          onClick = {
            val targetId = deleteTarget.measurementId
            deleteTargetMeasurementId = null
            onDeleteMeasurement(targetId)
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("measurement_delete_confirm_button"),
        ) {
          Text(stringResource(R.string.measurement_delete_confirm_button))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { deleteTargetMeasurementId = null },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }

  // Printable Measurement Slip Preview Dialog
  previewMeasurementSlip?.let { (measurement, previewText) ->
    MeasurementSlipPreviewDialog(
      previewText = previewText,
      onDismiss = onDismissMeasurementPreview,
      onPrintNow = {
        onDismissMeasurementPreview()
        onPrintMeasurement(measurement)
      },
    )
  }
}

@Composable
private fun CustomerMetaRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  textColor: Color,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = textColor,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = textColor,
    )
  }
}

@Composable
private fun MeasurementProfileCard(
  entry: MeasurementEntity,
  showSavedIndicator: Boolean,
  cardSurface: Color,
  dividerColor: Color,
  primaryText: Color,
  mutedText: Color,
  onOpenOptions: () -> Unit,
) {
  val titleLabel = if (entry.label.isBlank() || entry.label.equals("Standard", ignoreCase = true)) {
    stringResource(R.string.measurement_type_male_shalwar_kameez)
  } else {
    entry.label
  }

  val measurementPairs = listOf(
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.KAMEEZ_LENGTH,
      label = stringResource(R.string.measurement_label_kameez_length),
      value = DateFormatUtils.formatInches(entry.kameezLambai),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.SHOULDER,
      label = stringResource(R.string.measurement_label_shoulder),
      value = DateFormatUtils.formatInches(entry.tera),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.CHEST,
      label = stringResource(R.string.measurement_label_chest),
      value = DateFormatUtils.formatInches(entry.chati),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.SLEEVES,
      label = stringResource(R.string.measurement_label_sleeves),
      value = DateFormatUtils.formatInches(entry.bazu),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.WAIST,
      label = stringResource(R.string.measurement_label_waist),
      value = DateFormatUtils.formatInches(entry.kamar),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.GHERA,
      label = stringResource(R.string.measurement_label_ghera_daman),
      value = DateFormatUtils.formatInches(entry.gheraDaman),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.COLLAR,
      label = stringResource(R.string.measurement_label_collar_gala),
      value = DateFormatUtils.formatInches(entry.galaCollar),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.CUFF,
      label = stringResource(R.string.measurement_label_cuff),
      value = DateFormatUtils.formatInches(entry.cuff),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.SHALWAR,
      label = stringResource(R.string.measurement_label_shalwar),
      value = DateFormatUtils.formatInches(entry.shalwarLambai),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.PAINCHA,
      label = stringResource(R.string.measurement_label_paincha),
      value = DateFormatUtils.formatInches(entry.paincha),
    ),
    MeasurementMetricSpec(
      glyph = TailorBadgeGlyph.ASAN,
      label = stringResource(R.string.measurement_label_asan),
      value = DateFormatUtils.formatInches(entry.gheraAsan),
    ),
  )

  val styleItems = listOf(
    TailorBadgeGlyph.COLLAR to localizedNeckStyle(entry.neckStyle),
    TailorBadgeGlyph.POCKET to localizedPocketStyles(entry.pocketStyles),
    TailorBadgeGlyph.CUFF to localizedSleeveStyle(entry.sleeveStyle),
    TailorBadgeGlyph.SILAI to localizedSilaiStyle(entry.silaiStyle),
    TailorBadgeGlyph.DAMAN to localizedDamanStyle(entry.damanStyle),
  )

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = cardSurface,
    border = BorderStroke(1.dp, dividerColor),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("measurement_card_${entry.measurementId}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Card Header: Garment Type + Date + Three-dots Options Trigger
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = titleLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (showSavedIndicator) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = null,
              tint = AtelierOlive,
              modifier = Modifier
                .size(16.dp)
                .testTag("measurement_active_badge_${entry.measurementId}"),
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = DateFormatUtils.formatDate(entry.createdAt),
            style = MaterialTheme.typography.bodyMedium,
            color = mutedText,
            maxLines = 1,
          )
          IconButton(
            onClick = onOpenOptions,
            modifier = Modifier
              .size(48.dp)
              .testTag("measurement_options_button_${entry.measurementId}"),
          ) {
            Icon(
              imageVector = Icons.Filled.MoreVert,
              contentDescription = stringResource(R.string.measurement_options_title),
              tint = primaryText,
            )
          }
        }
      }

      HorizontalDivider(
        color = dividerColor,
        modifier = Modifier.padding(end = 8.dp),
      )

      // 2-Column Measurement Metrics Grid
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(end = 8.dp),
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

      HorizontalDivider(
        color = dividerColor,
        modifier = Modifier.padding(end = 8.dp),
      )

      // 2-Column Stitching Styles Grid
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(end = 8.dp),
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

      if (entry.notes.isNotBlank()) {
        HorizontalDivider(
          color = dividerColor,
          modifier = Modifier.padding(end = 8.dp),
        )
        Column(
          modifier = Modifier.padding(end = 8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = stringResource(R.string.order_detail_additional_note),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = primaryText,
          )
          Text(
            text = entry.notes,
            style = MaterialTheme.typography.bodyMedium,
            color = mutedText,
          )
        }
      }
    }
  }
}

@Composable
private fun CustomerPaymentCard(
  payment: CustomerPaymentEntry,
  onClick: () -> Unit,
) {
  val orderLabel = payment.orderNumber?.let {
    stringResource(R.string.customer_payment_order_pill, it.toString())
  } ?: localizedPaymentMethod(payment.paymentMethod)

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = AtelierOlive,
    contentColor = OnAtelierOlive,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("customer_payment_card_${payment.paymentId}"),
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
        color = Color.Black.copy(alpha = 0.24f),
        contentColor = OnAtelierOlive,
      ) {
        Text(
          text = orderLabel,
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }
    }
  }
}

@Composable
private fun MeasurementOptionsDialog(
  dialogSurface: Color,
  primaryText: Color,
  borderColor: Color,
  isPrinting: Boolean,
  onDismiss: () -> Unit,
  onEdit: () -> Unit,
  onSave: () -> Unit,
  onPrint: () -> Unit,
  onDelete: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = dialogSurface,
      contentColor = primaryText,
      border = BorderStroke(1.dp, borderColor),
      tonalElevation = 6.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .widthIn(max = 420.dp)
        .testTag("measurement_options_dialog"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.measurement_options_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = primaryText,
          )
          Surface(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            color = AtelierOlive,
            contentColor = OnAtelierOlive,
            modifier = Modifier
              .size(48.dp)
              .testTag("measurement_options_close_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(2.dp))

        OptionOliveButton(
          label = stringResource(R.string.measurement_option_edit),
          onClick = onEdit,
          testTag = "measurement_option_edit",
        )
        OptionOliveButton(
          label = stringResource(R.string.measurement_option_save),
          onClick = onSave,
          testTag = "measurement_option_save",
        )
        OptionOliveButton(
          label = stringResource(R.string.measurement_option_print),
          enabled = !isPrinting,
          onClick = onPrint,
          testTag = "measurement_option_print",
        )
        OptionOliveButton(
          label = stringResource(R.string.measurement_option_delete),
          onClick = onDelete,
          testTag = "measurement_option_delete",
        )
      }
    }
  }
}

@Composable
private fun OptionOliveButton(
  label: String,
  onClick: () -> Unit,
  testTag: String,
  enabled: Boolean = true,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(10.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = AtelierOlive,
      contentColor = OnAtelierOlive,
      disabledContainerColor = AtelierOlive.copy(alpha = 0.5f),
      disabledContentColor = OnAtelierOlive.copy(alpha = 0.65f),
    ),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 50.dp)
      .testTag(testTag),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
    )
  }
}

@Composable
private fun MeasurementSlipPreviewDialog(
  previewText: String,
  onDismiss: () -> Unit,
  onPrintNow: () -> Unit,
) {
  val context = LocalContext.current
  val hasUrduCharacters = remember(previewText) {
    previewText.any { ch -> Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.ARABIC }
  }
  val slipLocale = if (hasUrduCharacters) Locale("ur") else Locale.ENGLISH
  val slipDirection = if (hasUrduCharacters) LayoutDirection.Rtl else LayoutDirection.Ltr
  val baseConfig = LocalConfiguration.current
  val localizedConfig = remember(baseConfig, slipLocale) {
    Configuration(baseConfig).apply { setLocale(slipLocale) }
  }
  val localizedContext = remember(context, localizedConfig) {
    context.createConfigurationContext(localizedConfig)
  }
  val previewTitle = localizedContext.getString(R.string.measurement_preview_title)
  val printNowLabel = localizedContext.getString(R.string.action_print_now)
  val closeLabel = localizedContext.getString(R.string.action_close)
  val textDirection = if (slipDirection == LayoutDirection.Rtl) {
    TextDirection.Rtl
  } else {
    TextDirection.Ltr
  }

  AlertDialog(
    onDismissRequest = onDismiss,
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
            text = previewText,
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
          onClick = onPrintNow,
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
          onClick = onDismiss,
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

private fun toWhatsAppDigits(rawPhone: String): String {
  val normalized = PhoneNumberUtils.normalizePakistaniPhone(rawPhone)
  return when {
    normalized.startsWith("0") && normalized.length >= 10 -> "92" + normalized.drop(1)
    normalized.isNotEmpty() -> normalized
    else -> rawPhone.filter { it.isDigit() }
  }
}
