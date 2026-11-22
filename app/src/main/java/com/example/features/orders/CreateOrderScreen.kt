package com.example.features.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.model.PaymentMethod
import com.example.core.ui.AtelierCard
import com.example.core.ui.DateFormatUtils
import com.example.core.ui.PkrFormatUtils
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.features.customers.TailorBadgeGlyph
import com.example.features.customers.TailorMeasurementBadge
import com.example.features.customers.localizedPaymentMethod
import com.example.features.measurements.StylePreferencesCard
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.AtelierOliveDarkCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OnAtelierOlive

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateOrderScreen(
  viewModel: CreateOrderViewModel,
  onNavigateBack: () -> Unit,
  onOrderCreated: (orderId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.formState.collectAsStateWithLifecycle()
  val customers by viewModel.allCustomers.collectAsStateWithLifecycle()

  var showCustomerPicker by rememberSaveable { mutableStateOf(false) }
  var showDatePicker by rememberSaveable { mutableStateOf(false) }

  val isDarkCanvas = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val cardSurface = if (isDarkCanvas) AtelierOliveDarkCard else MaterialTheme.colorScheme.surfaceContainerLow
  val primaryText = if (isDarkCanvas) OnAtelierOlive else MaterialTheme.colorScheme.onSurface
  val mutedText = if (isDarkCanvas) OnAtelierOlive.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
  val dividerColor = if (isDarkCanvas) Color.White.copy(alpha = 0.09f) else MaterialTheme.colorScheme.outlineVariant

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("create_order_screen"),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.order_create_title),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // 1. Customer Selection Card
          AtelierCard {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                  text = state.selectedCustomer?.name ?: stringResource(R.string.order_select_customer),
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              if (state.selectedCustomer != null) {
                val measSummary = state.selectedMeasurement?.let { meas ->
                  val label = resolvedMeasurementTitle(meas.label)
                  stringResource(R.string.order_loaded_label_meas, label)
                } ?: if (state.hasSavedMeasurement) {
                  stringResource(R.string.order_loaded_saved_meas)
                } else {
                  stringResource(R.string.order_loaded_no_meas)
                }
                Text(
                  text = state.selectedCustomer!!.phone + measSummary,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              OutlinedButton(
                onClick = { showCustomerPicker = true },
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("order_pick_customer_button"),
              ) {
                Text(
                  if (state.selectedCustomer == null) stringResource(R.string.order_select_customer)
                  else stringResource(R.string.order_change_customer),
                )
              }
            }
          }

          // 1B. Measurement Selector (only shown when selected customer has > 1 measurement)
          if (state.selectedCustomer != null && state.availableMeasurements.size > 1) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = cardSurface,
              border = BorderStroke(1.dp, dividerColor),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("order_measurement_selector_card"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Straighten,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = stringResource(R.string.order_select_measurement),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = primaryText,
                  )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  state.availableMeasurements.forEach { measurement ->
                    val isSelected = state.selectedMeasurement?.measurementId == measurement.measurementId
                    val itemTitle = resolvedMeasurementTitle(measurement.label)
                    val selectedFill = if (isDarkCanvas) {
                      AtelierOlive.copy(alpha = 0.24f)
                    } else {
                      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    }
                    Surface(
                      onClick = { viewModel.selectMeasurement(measurement) },
                      shape = RoundedCornerShape(12.dp),
                      color = if (isSelected) selectedFill else Color.Transparent,
                      border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) AtelierOlive else dividerColor,
                      ),
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("order_select_measurement_${measurement.measurementId}"),
                    ) {
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Row(
                          modifier = Modifier.weight(1f),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                          Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) AtelierOlive else mutedText,
                            modifier = Modifier.size(20.dp),
                          )
                          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                              Text(
                                text = itemTitle,
                                style = MaterialTheme.typography.titleSmall.copy(
                                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                              )
                              if (measurement.isCurrent) {
                                Surface(
                                  shape = RoundedCornerShape(999.dp),
                                  color = AtelierOlive.copy(alpha = 0.25f),
                                ) {
                                  Text(
                                    text = stringResource(R.string.order_measurement_active_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = primaryText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                  )
                                }
                              }
                            }
                            Text(
                              text = "${DateFormatUtils.formatInches(measurement.kameezLambai)} • ${DateFormatUtils.formatInches(measurement.chati)} • ${DateFormatUtils.formatInches(measurement.shalwarLambai)}",
                              style = MaterialTheme.typography.bodySmall,
                              color = mutedText,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis,
                            )
                          }
                        }

                        Text(
                          text = DateFormatUtils.formatDate(measurement.createdAt),
                          style = MaterialTheme.typography.labelMedium,
                          color = mutedText,
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // 2. Order Details & Billing Card
          AtelierCard {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Text(
                text = stringResource(R.string.order_billing_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
              )

              // Suit Count Stepper
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(stringResource(R.string.order_suit_count), style = MaterialTheme.typography.bodyLarge)
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  IconButton(
                    onClick = {
                      if (state.suitCount > 1) {
                        viewModel.updateForm { it.copy(suitCount = it.suitCount - 1) }
                      }
                    },
                    modifier = Modifier.testTag("order_suit_minus"),
                  ) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.order_decrease_suits_cd))
                  }
                  Text(
                    text = "${state.suitCount}",
                    style = TextStyle(
                      fontFamily = JetBrainsMonoFamily,
                      fontSize = 18.sp,
                      fontWeight = FontWeight.Bold,
                    ),
                  )
                  IconButton(
                    onClick = {
                      viewModel.updateForm { it.copy(suitCount = it.suitCount + 1) }
                    },
                    modifier = Modifier.testTag("order_suit_plus"),
                  ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.order_increase_suits_cd))
                  }
                }
              }

              // Delivery Date Quick Presets + Picker
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = "${stringResource(R.string.order_delivery_date)}: ${DateFormatUtils.formatDate(state.deliveryDateMillis)}",
                  style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  listOf(
                    stringResource(R.string.order_preset_tomorrow) to 1,
                    stringResource(R.string.order_preset_plus_3_days) to 3,
                    stringResource(R.string.order_preset_plus_7_days) to 7,
                  ).forEach { (label, days) ->
                    FilterChip(
                      selected = false,
                      onClick = {
                        viewModel.updateForm {
                          it.copy(deliveryDateMillis = PkrFormatUtils.startOfDayPlusDays(days))
                        }
                      },
                      label = { Text(label) },
                      modifier = Modifier.heightIn(min = 48.dp),
                    )
                  }
                  OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.CalendarMonth,
                      contentDescription = null,
                      modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(stringResource(R.string.order_calendar_button))
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                OutlinedTextField(
                  value = state.totalBillPkrText,
                  onValueChange = { raw ->
                    viewModel.updateForm { it.copy(totalBillPkrText = raw.filter { c -> c.isDigit() }) }
                  },
                  label = { Text(stringResource(R.string.order_total_bill_label)) },
                  prefix = { Text(stringResource(R.string.currency_pkr_prefix)) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("order_total_bill_input"),
                )

                OutlinedTextField(
                  value = state.advancePkrText,
                  onValueChange = { raw ->
                    viewModel.updateForm { it.copy(advancePkrText = raw.filter { c -> c.isDigit() }) }
                  },
                  label = { Text(stringResource(R.string.order_advance_label)) },
                  prefix = { Text(stringResource(R.string.currency_pkr_prefix)) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("order_advance_input"),
                )
              }

              val remaining = state.remainingPreviewPkr
              if (remaining != null) {
                Text(
                  text = "${stringResource(R.string.order_remaining_balance)}: ${PkrFormatUtils.formatPkr(remaining)}",
                  style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                  ),
                  color = if (remaining < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
              }

              if ((state.advancePkr ?: 0L) > 0L) {
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  PaymentMethod.entries.forEach { method ->
                    FilterChip(
                      selected = state.advancePaymentMethod == method,
                      onClick = { viewModel.updateForm { it.copy(advancePaymentMethod = method) } },
                      label = { Text(localizedPaymentMethod(method)) },
                      modifier = Modifier.heightIn(min = 48.dp),
                    )
                  }
                }
              }
            }
          }

          // 3. Measurement & Style Snapshot Card (styled to match Customer Detail screenshot_5)
          val selectedMeas = state.selectedMeasurement
          val measTitle = if (selectedMeas != null) {
            resolvedMeasurementTitle(selectedMeas.label)
          } else {
            stringResource(R.string.customer_measurements_section)
          }

          val editableMetrics = listOf(
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.KAMEEZ_LENGTH,
              label = stringResource(R.string.measurement_label_kameez_length),
              value = state.kameezLambai,
              tag = "order_meas_lambai",
              onValueChange = { v -> viewModel.updateForm { it.copy(kameezLambai = v) } },
            ),
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.SHOULDER,
              label = stringResource(R.string.measurement_label_shoulder),
              value = state.tera,
              tag = "order_meas_tera",
              onValueChange = { v -> viewModel.updateForm { it.copy(tera = v) } },
            ),
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.CHEST,
              label = stringResource(R.string.measurement_label_chest),
              value = state.chati,
              tag = "order_meas_chati",
              onValueChange = { v -> viewModel.updateForm { it.copy(chati = v) } },
            ),
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.SLEEVES,
              label = stringResource(R.string.measurement_label_sleeves),
              value = state.bazu,
              tag = "order_meas_bazu",
              onValueChange = { v -> viewModel.updateForm { it.copy(bazu = v) } },
            ),
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.WAIST,
              label = stringResource(R.string.measurement_label_waist),
              value = state.kamar,
              tag = "order_meas_kamar",
              onValueChange = { v -> viewModel.updateForm { it.copy(kamar = v) } },
            ),
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.GHERA,
              label = stringResource(R.string.measurement_label_ghera_daman),
              value = state.gheraDaman,
              tag = "order_meas_ghera",
              onValueChange = { v -> viewModel.updateForm { it.copy(gheraDaman = v) } },
            ),
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.COLLAR,
              label = stringResource(R.string.measurement_label_collar_gala),
              value = state.galaCollar,
              tag = "order_meas_gala",
              onValueChange = { v -> viewModel.updateForm { it.copy(galaCollar = v) } },
            ),
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.CUFF,
              label = stringResource(R.string.measurement_label_cuff),
              value = state.cuff,
              tag = "order_meas_cuff",
              onValueChange = { v -> viewModel.updateForm { it.copy(cuff = v) } },
            ),
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.SHALWAR,
              label = stringResource(R.string.measurement_label_shalwar),
              value = state.shalwarLambai,
              tag = "order_meas_shalwar",
              onValueChange = { v -> viewModel.updateForm { it.copy(shalwarLambai = v) } },
            ),
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.PAINCHA,
              label = stringResource(R.string.measurement_label_paincha),
              value = state.paincha,
              tag = "order_meas_paincha",
              onValueChange = { v -> viewModel.updateForm { it.copy(paincha = v) } },
            ),
            EditableMetricSpec(
              glyph = TailorBadgeGlyph.ASAN,
              label = stringResource(R.string.measurement_label_asan),
              value = state.gheraAsan,
              tag = "order_meas_asan",
              onValueChange = { v -> viewModel.updateForm { it.copy(gheraAsan = v) } },
            ),
          )

          Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardSurface,
            border = BorderStroke(1.dp, dividerColor),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("order_create_measurements_card"),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              // Card Header matching screenshot_5 (Measurement label + check + date)
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
                    text = measTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  if (selectedMeas?.isCurrent == true) {
                    Icon(
                      imageVector = Icons.Filled.CheckCircle,
                      contentDescription = null,
                      tint = AtelierOlive,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }

                Text(
                  text = selectedMeas?.let { DateFormatUtils.formatDate(it.createdAt) }
                    ?: stringResource(R.string.unit_inches),
                  style = MaterialTheme.typography.bodyMedium,
                  color = mutedText,
                  maxLines = 1,
                )
              }

              HorizontalDivider(color = dividerColor)

              // 2-Column Editable Measurement Metrics Grid with TailorMeasurementBadge
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                editableMetrics.chunked(2).forEach { pair ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    pair.forEach { metric ->
                      EditableMeasurementMetricTile(
                        glyph = metric.glyph,
                        label = metric.label,
                        value = metric.value,
                        onValueChange = metric.onValueChange,
                        inputTag = metric.tag,
                        labelColor = mutedText,
                        valueColor = primaryText,
                        dividerColor = dividerColor,
                        modifier = Modifier.weight(1f),
                      )
                    }
                    if (pair.size == 1) {
                      Spacer(modifier = Modifier.weight(1f))
                    }
                  }
                }
              }
            }
          }

          // 4. Style Preferences Card (chip selectors matching MeasurementEditorScreen / screenshot_7)
          StylePreferencesCard(
            neckStyle = state.neckStyle,
            onSelectNeckStyle = { style -> viewModel.updateForm { it.copy(neckStyle = style) } },
            damanStyle = state.damanStyle,
            onSelectDamanStyle = { style -> viewModel.updateForm { it.copy(damanStyle = style) } },
            pocketStyles = state.pocketStyles,
            onTogglePocketStyle = { style ->
              viewModel.updateForm { current ->
                val updated = if (style in current.pocketStyles) {
                  current.pocketStyles - style
                } else {
                  current.pocketStyles + style
                }
                current.copy(pocketStyles = updated)
              }
            },
            sleeveStyle = state.sleeveStyle,
            onSelectSleeveStyle = { style -> viewModel.updateForm { it.copy(sleeveStyle = style) } },
            silaiStyle = state.silaiStyle,
            onSelectSilaiStyle = { style -> viewModel.updateForm { it.copy(silaiStyle = style) } },
            footerContent = {
              HorizontalDivider(color = dividerColor)
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = state.alsoUpdateCustomerMeasurements,
                  onCheckedChange = { checked ->
                    viewModel.updateForm { it.copy(alsoUpdateCustomerMeasurements = checked) }
                  },
                )
                Text(
                  text = stringResource(R.string.order_update_customer_meas_toggle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
          )

          OutlinedTextField(
            value = state.notes,
            onValueChange = { v -> viewModel.updateForm { it.copy(notes = v) } },
            label = { Text(stringResource(R.string.customer_notes_label)) },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
          )

          if (!state.errorMessage.isNullOrBlank()) {
            Text(
              text = state.errorMessage!!,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("order_create_error"),
            )
          }

          Button(
            onClick = { viewModel.submitOrder(onOrderCreated) },
            enabled = !state.isSaving,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("order_submit_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(R.string.order_save_button))
          }
        }
      }
    }
  }

  if (showCustomerPicker) {
    CustomerPickerDialog(
      customers = customers,
      onSelect = { customer ->
        viewModel.selectCustomer(customer)
        showCustomerPicker = false
      },
      onDismiss = { showCustomerPicker = false },
    )
  }

  if (showDatePicker) {
    val dateState = rememberDatePickerState(initialSelectedDateMillis = state.deliveryDateMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            dateState.selectedDateMillis?.let { chosen ->
              viewModel.updateForm { it.copy(deliveryDateMillis = chosen) }
            }
            showDatePicker = false
          },
        ) {
          Text(stringResource(R.string.action_ok))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    ) {
      DatePicker(state = dateState)
    }
  }
}

@Composable
private fun resolvedMeasurementTitle(label: String): String {
  return if (label.isBlank() || label.equals("Standard", ignoreCase = true)) {
    stringResource(R.string.measurement_type_male_shalwar_kameez)
  } else {
    label
  }
}

private data class EditableMetricSpec(
  val glyph: TailorBadgeGlyph,
  val label: String,
  val value: String,
  val tag: String,
  val onValueChange: (String) -> Unit,
)

@Composable
private fun EditableMeasurementMetricTile(
  glyph: TailorBadgeGlyph,
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  inputTag: String,
  labelColor: Color,
  valueColor: Color,
  dividerColor: Color,
  modifier: Modifier = Modifier,
) {
  val focusRequester = remember { FocusRequester() }
  var isFocused by remember { mutableStateOf(false) }

  Row(
    modifier = modifier
      .heightIn(min = 48.dp)
      .clip(RoundedCornerShape(10.dp))
      .clickable { focusRequester.requestFocus() },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    TailorMeasurementBadge(glyph = glyph)
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = labelColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      BasicTextField(
        value = value,
        onValueChange = { raw ->
          val filtered = raw.filter { it.isDigit() || it == '.' }
          if (filtered.count { it == '.' } <= 1) {
            onValueChange(filtered)
          }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(
          fontFamily = JetBrainsMonoFamily,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = valueColor,
        ),
        cursorBrush = SolidColor(AtelierOlive),
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester)
          .onFocusChanged { isFocused = it.isFocused }
          .testTag(inputTag),
        decorationBox = { innerTextField ->
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(modifier = Modifier.weight(1f, fill = false)) {
                if (value.isEmpty()) {
                  Text(
                    text = "0.0",
                    style = TextStyle(
                      fontFamily = JetBrainsMonoFamily,
                      fontSize = 16.sp,
                      fontWeight = FontWeight.Bold,
                      color = labelColor.copy(alpha = 0.45f),
                    ),
                  )
                }
                innerTextField()
              }
              if (value.isNotEmpty()) {
                Text(
                  text = "\"",
                  style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                  ),
                )
              }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 1.5.dp else 1.dp)
                .background(if (isFocused) AtelierOlive else dividerColor),
            )
          }
        },
      )
    }
  }
}

@Composable
private fun CustomerPickerDialog(
  customers: List<CustomerEntity>,
  onSelect: (CustomerEntity) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  val filtered = customers.filter {
    query.isBlank() ||
      it.name.contains(query, ignoreCase = true) ||
      it.normalizedPhone.contains(query.filter(Char::isDigit))
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.order_select_customer)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text(stringResource(R.string.order_pick_customer_search_hint)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
          modifier = Modifier.heightIn(max = 320.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(filtered, key = { it.customerId }) { customer ->
            AtelierCard(
              onClick = { onSelect(customer) },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("pick_customer_${customer.customerId}"),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(customer.name, style = MaterialTheme.typography.titleSmall)
                Text(customer.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
    },
  )
}
