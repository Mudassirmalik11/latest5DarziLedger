package com.example.features.orders

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.AtelierLandingDarkCanvas
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.OnAtelierOlive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
  viewModel: OrderDetailViewModel,
  onNavigateBack: () -> Unit,
  onOpenCustomer: (customerId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val actionError by viewModel.actionError.collectAsStateWithLifecycle()
  val printMessage by viewModel.printStatusMessage.collectAsStateWithLifecycle()
  val isPrinting by viewModel.isPrinting.collectAsStateWithLifecycle()
  val previewSlip by viewModel.previewSlip.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(printMessage) {
    val msg = printMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearPrintMessage()
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
            text = stringResource(R.string.order_detail_title),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("order_detail_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.customer_form_close_cd),
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = AtelierOlive,
          titleContentColor = OnAtelierOlive,
          navigationIconContentColor = OnAtelierOlive,
        ),
      )
    },
  ) { innerPadding ->
    OrderDetailContent(
      uiState = uiState,
      actionError = actionError,
      onClearError = viewModel::clearError,
      onUpdateStatus = { status -> viewModel.updateStatus(status) },
      onDeleteOrder = { viewModel.deleteOrder(onDeleted = onNavigateBack) },
      onRecordPayment = { amount, method, note, onSuccess ->
        viewModel.recordPayment(amount, method, note, onSuccess)
      },
      onSettleRemaining = { method -> viewModel.settleFullRemaining(method) },
      onOpenCustomer = onOpenCustomer,
      isPrinting = isPrinting,
      previewSlip = previewSlip,
      onPrintCustomerReceipt = viewModel::printCustomerReceipt,
      onPrintKarigarSlip = viewModel::printKarigarSlip,
      onOpenPreview = viewModel::openPreview,
      onDismissPreview = viewModel::dismissPreview,
      onAddMediaUris = viewModel::addMediaUris,
      onRemoveMediaUri = viewModel::removeMediaUri,
      isOwner = viewModel.isOwner,
      modifier = Modifier.padding(innerPadding),
    )
  }
}
