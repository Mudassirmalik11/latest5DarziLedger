package com.example.features.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.WindowWidthClass
import com.example.features.customers.CustomerFormDialog
import com.example.features.customers.DuplicateCustomerDialog
import com.example.ui.theme.AtelierLandingDarkCanvas
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.OnAtelierOlive

@Composable
fun DashboardScreen(
  viewModel: DashboardViewModel,
  widthClass: WindowWidthClass,
  onCreateNewOrder: () -> Unit,
  onOpenOrderDetail: (orderId: String) -> Unit,
  onOpenCustomerDetail: (customerId: String) -> Unit,
  onNavigateToCustomers: () -> Unit,
  onNavigateToOrders: () -> Unit,
  onNavigateToLedger: () -> Unit,
  onOpenDrawer: () -> Unit = {},
  syncPill: @Composable () -> Unit = {},
  openPaymentsRequested: Boolean = false,
  onConsumeOpenPayments: () -> Unit = {},
  isOwner: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val customerFormError by viewModel.customerFormError.collectAsStateWithLifecycle()
  val duplicateCustomerPrompt by viewModel.duplicateCustomerPrompt.collectAsStateWithLifecycle()

  val isWide = widthClass != WindowWidthClass.Compact
  val isDarkCanvas = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val canvasColor = if (isDarkCanvas) AtelierLandingDarkCanvas else MaterialTheme.colorScheme.background

  var showPaymentsSheet by rememberSaveable { mutableStateOf(false) }
  var showAddCustomerDialog by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(openPaymentsRequested, isOwner) {
    if (openPaymentsRequested) {
      if (isOwner) {
        showPaymentsSheet = true
      }
      onConsumeOpenPayments()
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(canvasColor)
      .testTag("dashboard_screen"),
  ) {
    DashboardBrandHeader(
      onOpenDrawer = onOpenDrawer,
      syncPill = syncPill,
    )

    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = if (isWide) 24.dp else 16.dp, vertical = 20.dp),
      contentAlignment = Alignment.TopCenter,
    ) {
      LandingActionGrid(
        isOwner = isOwner,
        onCustomersClick = onNavigateToCustomers,
        onPaymentsClick = { if (isOwner) showPaymentsSheet = true },
        onOrdersClick = onNavigateToOrders,
        onLedgerClick = { if (isOwner) onNavigateToLedger() },
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 760.dp)
          .padding(top = 4.dp),
      )
    }

    PinnedBottomQuickActions(
      onAddCustomer = {
        viewModel.clearCustomerFormError()
        showAddCustomerDialog = true
      },
      onAddOrder = onCreateNewOrder,
      modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars),
    )
  }

  if (showPaymentsSheet && isOwner) {
    PaymentsOverviewSheet(
      todayCollectionPkr = uiState.todayCollectionPkr,
      outstandingPkr = uiState.outstandingPkr,
      unpaidOrders = uiState.unpaidOrders,
      paidOrders = uiState.paidOrders,
      onDismiss = { showPaymentsSheet = false },
      onOpenOrderDetail = onOpenOrderDetail,
    )
  }

  if (showAddCustomerDialog) {
    CustomerFormDialog(
      title = stringResource(R.string.customer_add_new),
      errorMessage = customerFormError,
      onDismiss = { showAddCustomerDialog = false },
      onSave = { name, phone, notes, customId ->
        viewModel.createCustomer(
          name = name,
          phone = phone,
          notes = notes,
          customId = customId,
          onCreated = { created ->
            showAddCustomerDialog = false
            onOpenCustomerDetail(created.customerId)
          },
        )
      },
    )
  }

  duplicateCustomerPrompt?.let { prompt ->
    DuplicateCustomerDialog(
      prompt = prompt,
      onOpenExisting = {
        val existingId = prompt.existingCustomer.customerId
        viewModel.dismissDuplicateCustomerPrompt()
        showAddCustomerDialog = false
        onOpenCustomerDetail(existingId)
      },
      onCreateSeparate = {
        viewModel.createCustomer(
          name = prompt.pendingName,
          phone = prompt.pendingPhone,
          notes = prompt.pendingNotes,
          customId = prompt.pendingCustomId,
          allowDuplicatePhone = true,
          onCreated = { created ->
            showAddCustomerDialog = false
            onOpenCustomerDetail(created.customerId)
          },
        )
      },
      onDismiss = viewModel::dismissDuplicateCustomerPrompt,
    )
  }
}

@Composable
private fun DashboardBrandHeader(
  onOpenDrawer: () -> Unit,
  syncPill: @Composable () -> Unit,
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("dashboard_brand_header"),
    color = AtelierOlive,
    contentColor = OnAtelierOlive,
    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 22.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onOpenDrawer,
        modifier = Modifier
          .size(48.dp)
          .testTag("dashboard_menu_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Menu,
          contentDescription = stringResource(R.string.dashboard_menu_open),
          tint = OnAtelierOlive,
          modifier = Modifier.size(28.dp),
        )
      }

      Row(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_tailor_shears),
          contentDescription = stringResource(R.string.app_name),
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
          ),
          color = OnAtelierOlive,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("dashboard_brand_title"),
        )
      }

      Box(contentAlignment = Alignment.CenterEnd) {
        syncPill()
      }
    }
  }
}

@Composable
private fun LandingActionGrid(
  isOwner: Boolean,
  onCustomersClick: () -> Unit,
  onPaymentsClick: () -> Unit,
  onOrdersClick: () -> Unit,
  onLedgerClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.testTag("dashboard_action_grid"),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      LandingGridTile(
        label = stringResource(R.string.dashboard_tile_customers),
        icon = Icons.Outlined.Group,
        onClick = onCustomersClick,
        enabled = true,
        modifier = Modifier
          .weight(1f)
          .testTag("dashboard_tile_customers"),
      )
      LandingGridTile(
        label = stringResource(R.string.dashboard_tile_payments),
        icon = Icons.Outlined.AccountBalanceWallet,
        onClick = onPaymentsClick,
        enabled = isOwner,
        modifier = Modifier
          .weight(1f)
          .testTag("dashboard_tile_payments"),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      LandingGridTile(
        label = stringResource(R.string.dashboard_tile_orders),
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        onClick = onOrdersClick,
        enabled = true,
        modifier = Modifier
          .weight(1f)
          .testTag("dashboard_tile_orders"),
      )
      LandingGridTile(
        label = stringResource(R.string.dashboard_tile_ledger),
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        onClick = onLedgerClick,
        enabled = isOwner,
        modifier = Modifier
          .weight(1f)
          .testTag("dashboard_tile_ledger"),
      )
    }
  }
}

@Composable
private fun LandingGridTile(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val isDarkCanvas = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val containerColor = if (enabled) {
    AtelierOlive
  } else if (isDarkCanvas) {
    AtelierOlive.copy(alpha = 0.24f)
  } else {
    AtelierOlive.copy(alpha = 0.18f)
  }
  val contentColor = if (enabled) {
    OnAtelierOlive
  } else if (isDarkCanvas) {
    OnAtelierOlive.copy(alpha = 0.36f)
  } else {
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
  }
  val tileBorder = if (enabled) {
    null
  } else {
    BorderStroke(
      width = 1.dp,
      color = if (isDarkCanvas) Color.White.copy(alpha = 0.09f) else AtelierOlive.copy(alpha = 0.22f),
    )
  }

  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.heightIn(min = 128.dp),
    shape = RoundedCornerShape(16.dp),
    color = containerColor,
    contentColor = contentColor,
    border = tileBorder,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 22.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = contentColor,
        modifier = Modifier.size(40.dp),
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
        color = contentColor,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun PinnedBottomQuickActions(
  onAddCustomer: () -> Unit,
  onAddOrder: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 760.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      LandingBottomButton(
        label = stringResource(R.string.dashboard_action_add_customer),
        icon = Icons.Outlined.PersonAdd,
        onClick = onAddCustomer,
        modifier = Modifier
          .weight(1f)
          .testTag("dashboard_add_customer_button"),
      )
      LandingBottomButton(
        label = stringResource(R.string.dashboard_action_add_order),
        icon = Icons.Outlined.AddShoppingCart,
        onClick = onAddOrder,
        modifier = Modifier
          .weight(1f)
          .testTag("dashboard_new_order_button"),
      )
    }
  }
}

@Composable
private fun LandingBottomButton(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.heightIn(min = 56.dp),
    shape = RoundedCornerShape(14.dp),
    color = AtelierOlive,
    contentColor = OnAtelierOlive,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = OnAtelierOlive,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.size(8.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
        color = OnAtelierOlive,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
