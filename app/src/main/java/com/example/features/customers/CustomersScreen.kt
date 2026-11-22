package com.example.features.customers

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Smartphone
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
import com.example.core.ui.DateFormatUtils
import com.example.core.ui.EmptyWorkspaceCard
import com.example.core.ui.WindowWidthClass
import com.example.ui.theme.AtelierLandingDarkCanvas
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.OnAtelierOlive

@Composable
fun CustomersScreen(
  viewModel: CustomersViewModel,
  appContainer: AppContainer,
  widthClass: WindowWidthClass,
  onOpenCustomerDetail: (customerId: String) -> Unit,
  onOpenMeasurementEditor: (customerId: String, sourceMeasurementId: String?) -> Unit,
  onCreateOrderForCustomer: (customerId: String) -> Unit = {},
  onOpenOrderDetail: (orderId: String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val searchScope by viewModel.searchScope.collectAsStateWithLifecycle()
  val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
  val onlyWithAddress by viewModel.onlyWithAddress.collectAsStateWithLifecycle()
  val customerItems by viewModel.customerItems.collectAsStateWithLifecycle()
  val selectedCustomerId by viewModel.selectedCustomerId.collectAsStateWithLifecycle()
  val duplicatePrompt by viewModel.duplicatePrompt.collectAsStateWithLifecycle()
  val formError by viewModel.formError.collectAsStateWithLifecycle()

  var showCreateDialog by rememberSaveable { mutableStateOf(false) }
  var editingCustomer by rememberSaveable { mutableStateOf<String?>(null) }

  val isSplitPane = widthClass != WindowWidthClass.Compact
  val isDarkCanvas = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val canvasColor = if (isDarkCanvas) AtelierLandingDarkCanvas else MaterialTheme.colorScheme.background

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(canvasColor)
      .testTag("customers_screen"),
  ) {
    if (isSplitPane) {
      Row(modifier = Modifier.fillMaxSize()) {
        CustomerListPane(
          searchQuery = searchQuery,
          searchScope = searchScope,
          sortOrder = sortOrder,
          onlyWithAddress = onlyWithAddress,
          customerItems = customerItems,
          selectedCustomerId = selectedCustomerId,
          onSearchChange = viewModel::updateSearchQuery,
          onScopeChange = viewModel::updateSearchScope,
          onSortChange = viewModel::updateSortOrder,
          onToggleAddressOnly = viewModel::toggleOnlyWithAddress,
          onCustomerClick = { item -> viewModel.selectCustomer(item.customer.customerId) },
          modifier = Modifier
            .weight(0.45f)
            .fillMaxHeight(),
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
          modifier = Modifier
            .weight(0.55f)
            .fillMaxHeight(),
        ) {
          val selectedId = selectedCustomerId ?: customerItems.firstOrNull()?.customer?.customerId
          if (selectedId == null) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              EmptyWorkspaceCard(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.customer_profile_title),
                description = stringResource(R.string.customer_select_prompt),
              )
            }
          } else {
            val detailVm: CustomerDetailViewModel = viewModel(
              key = "detail_$selectedId",
              factory = CustomerDetailViewModel.Factory(
                customerId = selectedId,
                customerRepository = appContainer.customerRepository,
                orderRepository = appContainer.orderRepository,
                deviceSyncRepository = appContainer.deviceSyncRepository,
                thermalPrintManager = appContainer.thermalPrintManager,
              ),
            )
            val detailState by detailVm.uiState.collectAsStateWithLifecycle()
            val previewMeasurementSlip by detailVm.previewMeasurementSlip.collectAsStateWithLifecycle()
            val isPrinting by detailVm.isPrinting.collectAsStateWithLifecycle()
            val savedActiveMsg = stringResource(R.string.measurement_saved_active_msg)
            CustomerDetailContent(
              uiState = detailState,
              onEditCustomer = { editingCustomer = selectedId },
              onAddOrEditMeasurement = { measId -> onOpenMeasurementEditor(selectedId, measId) },
              onCreateOrderForCustomer = { onCreateOrderForCustomer(selectedId) },
              onOpenOrder = onOpenOrderDetail,
              onSaveMeasurementCurrent = { measId ->
                detailVm.setCurrentMeasurement(measId, savedActiveMsg)
              },
              onDeleteMeasurement = detailVm::deleteMeasurement,
              onPreviewMeasurement = detailVm::openMeasurementPreview,
              onPrintMeasurement = detailVm::printMeasurementSlip,
              onDismissMeasurementPreview = detailVm::dismissMeasurementPreview,
              previewMeasurementSlip = previewMeasurementSlip,
              isPrinting = isPrinting,
              onShowMessage = detailVm::showMessage,
            )
          }
        }
      }
    } else {
      CustomerListPane(
        searchQuery = searchQuery,
        searchScope = searchScope,
        sortOrder = sortOrder,
        onlyWithAddress = onlyWithAddress,
        customerItems = customerItems,
        selectedCustomerId = null,
        onSearchChange = viewModel::updateSearchQuery,
        onScopeChange = viewModel::updateSearchScope,
        onSortChange = viewModel::updateSortOrder,
        onToggleAddressOnly = viewModel::toggleOnlyWithAddress,
        onCustomerClick = { item -> onOpenCustomerDetail(item.customer.customerId) },
        modifier = Modifier.fillMaxSize(),
      )
    }

    FloatingActionButton(
      onClick = {
        viewModel.clearFormError()
        showCreateDialog = true
      },
      shape = CircleShape,
      containerColor = AtelierOlive,
      contentColor = OnAtelierOlive,
      elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(20.dp)
        .size(60.dp)
        .testTag("add_customer_fab"),
    ) {
      Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = stringResource(R.string.customer_add_new),
        modifier = Modifier.size(28.dp),
      )
    }
  }

  if (showCreateDialog) {
    CustomerFormDialog(
      title = stringResource(R.string.customer_add_new),
      errorMessage = formError,
      onDismiss = { showCreateDialog = false },
      onSave = { name, phone, notes, customId ->
        viewModel.createCustomer(
          name = name,
          phone = phone,
          notes = notes,
          customId = customId,
          onCreated = { created ->
            showCreateDialog = false
            if (!isSplitPane) {
              onOpenCustomerDetail(created.customerId)
            }
          },
        )
      },
    )
  }

  editingCustomer?.let { targetId ->
    val targetItem = customerItems.firstOrNull { it.customer.customerId == targetId }
    val target = targetItem?.customer
    if (target != null) {
      CustomerFormDialog(
        title = stringResource(R.string.customer_edit),
        initialName = target.name,
        initialPhone = target.phone,
        initialNotes = target.notes,
        initialCustomId = target.customId,
        errorMessage = formError,
        onDismiss = { editingCustomer = null },
        onSave = { name, phone, notes, customId ->
          viewModel.updateCustomer(
            customerId = target.customerId,
            name = name,
            phone = phone,
            notes = notes,
            customId = customId,
            onUpdated = { editingCustomer = null },
          )
        },
      )
    }
  }

  duplicatePrompt?.let { prompt ->
    DuplicateCustomerDialog(
      prompt = prompt,
      onOpenExisting = {
        val existingId = prompt.existingCustomer.customerId
        viewModel.dismissDuplicatePrompt()
        showCreateDialog = false
        viewModel.selectCustomer(existingId)
        if (!isSplitPane) {
          onOpenCustomerDetail(existingId)
        }
      },
      onCreateSeparate = {
        viewModel.createCustomer(
          name = prompt.pendingName,
          phone = prompt.pendingPhone,
          notes = prompt.pendingNotes,
          customId = prompt.pendingCustomId,
          allowDuplicatePhone = true,
          onCreated = { created ->
            showCreateDialog = false
            if (!isSplitPane) {
              onOpenCustomerDetail(created.customerId)
            }
          },
        )
      },
      onDismiss = viewModel::dismissDuplicatePrompt,
    )
  }
}

@Composable
private fun CustomerListPane(
  searchQuery: String,
  searchScope: CustomerSearchScope,
  sortOrder: CustomerSortOrder,
  onlyWithAddress: Boolean,
  customerItems: List<CustomerListItem>,
  selectedCustomerId: String?,
  onSearchChange: (String) -> Unit,
  onScopeChange: (CustomerSearchScope) -> Unit,
  onSortChange: (CustomerSortOrder) -> Unit,
  onToggleAddressOnly: () -> Unit,
  onCustomerClick: (CustomerListItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showFilterMenu by rememberSaveable { mutableStateOf(false) }

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
            .testTag("customers_search_input"),
          placeholder = {
            Text(
              text = stringResource(R.string.customers_search_simple_hint),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { onSearchChange("") },
                modifier = Modifier.testTag("customers_search_clear"),
              ) {
                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.dashboard_clear_search))
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
            onClick = { showFilterMenu = true },
            shape = RoundedCornerShape(10.dp),
            color = if (onlyWithAddress || sortOrder != CustomerSortOrder.NEWEST) {
              AtelierOlive
            } else {
              MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (onlyWithAddress || sortOrder != CustomerSortOrder.NEWEST) {
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
              .testTag("customers_filter_button"),
          ) {
            Box(
              modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = stringResource(R.string.customers_filter_button),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
              )
            }
          }

          DropdownMenu(
            expanded = showFilterMenu,
            onDismissRequest = { showFilterMenu = false },
            modifier = Modifier.testTag("customers_filter_menu"),
          ) {
            CustomerSortOptionRow(
              labelRes = R.string.customers_sort_newest,
              selected = sortOrder == CustomerSortOrder.NEWEST,
              onClick = {
                onSortChange(CustomerSortOrder.NEWEST)
                showFilterMenu = false
              },
              testTag = "customers_sort_newest",
            )
            CustomerSortOptionRow(
              labelRes = R.string.customers_sort_oldest,
              selected = sortOrder == CustomerSortOrder.OLDEST,
              onClick = {
                onSortChange(CustomerSortOrder.OLDEST)
                showFilterMenu = false
              },
              testTag = "customers_sort_oldest",
            )
            CustomerSortOptionRow(
              labelRes = R.string.customers_sort_name,
              selected = sortOrder == CustomerSortOrder.NAME_AZ,
              onClick = {
                onSortChange(CustomerSortOrder.NAME_AZ)
                showFilterMenu = false
              },
              testTag = "customers_sort_name",
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            CustomerSortOptionRow(
              labelRes = R.string.customers_filter_with_address,
              selected = onlyWithAddress,
              onClick = {
                onToggleAddressOnly()
                showFilterMenu = false
              },
              testTag = "customers_filter_address_only",
            )
          }
        }
      }

      SearchByScopeBar(
        selectedScope = searchScope,
        onSelectScope = onScopeChange,
      )

      Text(
        text = stringResource(R.string.customers_total_count, customerItems.size),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag("customers_total_count_text"),
      )
    }

    if (customerItems.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 820.dp)
          .padding(16.dp),
      ) {
        EmptyWorkspaceCard(
          icon = Icons.Filled.People,
          title = stringResource(R.string.customers_empty_title),
          description = stringResource(R.string.customers_empty_desc),
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
        items(customerItems, key = { it.customer.customerId }) { item ->
          CustomerDirectoryCard(
            item = item,
            isSelected = selectedCustomerId == item.customer.customerId,
            onClick = { onCustomerClick(item) },
          )
        }
      }
    }
  }
}

@Composable
private fun CustomerSortOptionRow(
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
private fun SearchByScopeBar(
  selectedScope: CustomerSearchScope,
  onSelectScope: (CustomerSearchScope) -> Unit,
) {
  val scopeItems = listOf(
    CustomerSearchScope.ALL to R.string.customers_scope_all,
    CustomerSearchScope.NAME to R.string.customers_scope_name,
    CustomerSearchScope.PHONE to R.string.customers_scope_phone,
    CustomerSearchScope.ID to R.string.customers_scope_id,
    CustomerSearchScope.ADDRESS to R.string.customers_scope_address,
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
              .testTag("customers_scope_${scope.name.lowercase()}"),
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

@Composable
private fun CustomerDirectoryCard(
  item: CustomerListItem,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val customer = item.customer
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = AtelierOlive,
    contentColor = OnAtelierOlive,
    border = if (isSelected) {
      BorderStroke(2.dp, OnAtelierOlive)
    } else {
      null
    },
    modifier = Modifier
      .fillMaxWidth()
      .testTag("customer_card_${customer.customerId}"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(end = 10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = customer.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = OnAtelierOlive,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (customer.notes.isNotBlank()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = OnAtelierOlive.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = customer.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = OnAtelierOlive.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color.Black.copy(alpha = 0.24f),
          contentColor = OnAtelierOlive,
        ) {
          Text(
            text = stringResource(R.string.customers_id_badge, item.displayId),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      HorizontalDivider(color = OnAtelierOlive.copy(alpha = 0.22f))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.CalendarToday,
            contentDescription = null,
            tint = OnAtelierOlive.copy(alpha = 0.85f),
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = DateFormatUtils.formatFullDateTime(customer.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = OnAtelierOlive.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (customer.phone.isNotBlank()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Smartphone,
              contentDescription = null,
              tint = OnAtelierOlive.copy(alpha = 0.85f),
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = customer.phone,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
              color = OnAtelierOlive,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}
