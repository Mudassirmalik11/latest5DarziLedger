package com.example.features.customers

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.AtelierLandingDarkCanvas
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.OnAtelierOlive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
  viewModel: CustomerDetailViewModel,
  onNavigateBack: () -> Unit,
  onOpenMeasurementEditor: (sourceMeasurementId: String?) -> Unit,
  onCreateOrder: () -> Unit = {},
  onOpenOrder: (orderId: String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val editError by viewModel.editError.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
  val isPrinting by viewModel.isPrinting.collectAsStateWithLifecycle()
  val previewMeasurementSlip by viewModel.previewMeasurementSlip.collectAsStateWithLifecycle()

  var showEditDialog by rememberSaveable { mutableStateOf(false) }
  var showOverflowMenu by rememberSaveable { mutableStateOf(false) }
  var showDeleteCustomerDialog by rememberSaveable { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val savedActiveMessage = stringResource(R.string.measurement_saved_active_msg)

  LaunchedEffect(statusMessage) {
    val msg = statusMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  val isDarkCanvas = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val canvasColor = if (isDarkCanvas) AtelierLandingDarkCanvas else MaterialTheme.colorScheme.background

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = canvasColor,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.customer_detail_topbar_title),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("customer_detail_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.customer_form_close_cd),
            )
          }
        },
        actions = {
          if (uiState.customer != null) {
            IconButton(
              onClick = { showOverflowMenu = true },
              modifier = Modifier.testTag("customer_detail_menu_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.customer_more_options_cd),
              )
            }
            DropdownMenu(
              expanded = showOverflowMenu,
              onDismissRequest = { showOverflowMenu = false },
            ) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.customer_edit)) },
                leadingIcon = {
                  Icon(Icons.Filled.Edit, contentDescription = null)
                },
                onClick = {
                  showOverflowMenu = false
                  viewModel.clearEditError()
                  showEditDialog = true
                },
                modifier = Modifier.testTag("edit_customer_button"),
              )
              DropdownMenuItem(
                text = {
                  Text(
                    text = stringResource(R.string.customer_delete_action),
                    color = MaterialTheme.colorScheme.error,
                  )
                },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                  )
                },
                onClick = {
                  showOverflowMenu = false
                  showDeleteCustomerDialog = true
                },
                modifier = Modifier.testTag("customer_menu_delete"),
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = AtelierOlive,
          titleContentColor = OnAtelierOlive,
          navigationIconContentColor = OnAtelierOlive,
          actionIconContentColor = OnAtelierOlive,
        ),
      )
    },
  ) { innerPadding ->
    CustomerDetailContent(
      uiState = uiState,
      onEditCustomer = {
        viewModel.clearEditError()
        showEditDialog = true
      },
      onAddOrEditMeasurement = onOpenMeasurementEditor,
      onCreateOrderForCustomer = onCreateOrder,
      onOpenOrder = onOpenOrder,
      onSaveMeasurementCurrent = { measurementId ->
        viewModel.setCurrentMeasurement(measurementId, savedActiveMessage)
      },
      onDeleteMeasurement = viewModel::deleteMeasurement,
      onPreviewMeasurement = viewModel::openMeasurementPreview,
      onPrintMeasurement = viewModel::printMeasurementSlip,
      onDismissMeasurementPreview = viewModel::dismissMeasurementPreview,
      previewMeasurementSlip = previewMeasurementSlip,
      isPrinting = isPrinting,
      onShowMessage = viewModel::showMessage,
      modifier = Modifier.padding(innerPadding),
    )
  }

  val customer = uiState.customer
  if (showEditDialog && customer != null) {
    CustomerFormDialog(
      title = stringResource(R.string.customer_edit),
      initialName = customer.name,
      initialPhone = customer.phone,
      initialNotes = customer.notes,
      initialCustomId = customer.customId,
      errorMessage = editError,
      onDismiss = { showEditDialog = false },
      onSave = { name, phone, notes, customId ->
        viewModel.updateCustomer(
          name = name,
          phone = phone,
          notes = notes,
          customId = customId,
          onSuccess = { showEditDialog = false },
        )
      },
    )
  }

  if (showDeleteCustomerDialog && customer != null) {
    AlertDialog(
      onDismissRequest = { showDeleteCustomerDialog = false },
      modifier = Modifier.testTag("customer_delete_confirm_dialog"),
      title = { Text(stringResource(R.string.customer_delete_confirm_title)) },
      text = { Text(stringResource(R.string.customer_delete_confirm_desc)) },
      confirmButton = {
        Button(
          onClick = {
            showDeleteCustomerDialog = false
            viewModel.deleteCustomer(onDeleted = onNavigateBack)
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("customer_delete_confirm_button"),
        ) {
          Text(stringResource(R.string.measurement_delete_confirm_button))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDeleteCustomerDialog = false },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }
}
