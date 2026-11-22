package com.example.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.core.model.ShopUserRole
import com.example.core.model.SyncState
import com.example.core.preferences.ShopPreferences
import com.example.core.ui.StatusPill
import com.example.core.ui.WindowWidthClass
import com.example.core.ui.widthClassFor
import com.example.data.remote.AuthUser
import com.example.features.auth.AuthScreen
import com.example.features.auth.DeviceAuthorizationViewModel
import com.example.features.auth.DeviceGateState
import com.example.features.auth.DeviceSetupScaffold
import com.example.features.customers.CustomerDetailScreen
import com.example.features.customers.CustomerDetailViewModel
import com.example.features.customers.CustomersScreen
import com.example.features.customers.CustomersViewModel
import com.example.features.dashboard.DashboardScreen
import com.example.features.dashboard.DashboardViewModel
import com.example.features.dashboard.LedgerScreen
import com.example.features.measurements.MeasurementEditorScreen
import com.example.features.measurements.MeasurementEditorViewModel
import com.example.features.orders.CreateOrderScreen
import com.example.features.orders.CreateOrderViewModel
import com.example.features.orders.OrderDetailScreen
import com.example.features.orders.OrderDetailViewModel
import com.example.features.orders.OrdersScreen
import com.example.features.orders.OrdersViewModel
import com.example.features.settings.SettingsScreen
import com.example.features.settings.SettingsViewModel
import com.example.features.sync.SyncStatusScreen
import com.example.features.sync.SyncStatusViewModel
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.OnAtelierOlive
import kotlinx.coroutines.launch

@Composable
fun DarziLedgerApp(
  appContainer: AppContainer,
  modifier: Modifier = Modifier,
) {
  val currentUser by appContainer.authSessionProvider.authStateFlow
    .collectAsStateWithLifecycle(initialValue = appContainer.authSessionProvider.currentUser)

  val user = currentUser
  if (user == null) {
    AuthScreen(
      authSessionProvider = appContainer.authSessionProvider,
      modifier = modifier,
    )
  } else {
    key(user.uid) {
      AuthenticatedShopShell(
        appContainer = appContainer,
        currentUser = user,
        modifier = modifier,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShopShell(
  appContainer: AppContainer,
  currentUser: AuthUser,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val deviceGateVm: DeviceAuthorizationViewModel = viewModel(
    key = "device_gate_${currentUser.uid}",
    factory = DeviceAuthorizationViewModel.Factory(
      uid = currentUser.uid,
      deviceSyncRepository = appContainer.deviceSyncRepository,
      shopCloudRepository = appContainer.shopCloudRepository,
      authSessionProvider = appContainer.authSessionProvider,
    ),
  )
  val gateState by deviceGateVm.gateState.collectAsStateWithLifecycle()
  val isBusy by deviceGateVm.isBusy.collectAsStateWithLifecycle()
  val actionError by deviceGateVm.actionError.collectAsStateWithLifecycle()

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val widthClass = widthClassFor(maxWidth)

    val authorizedGate = gateState as? DeviceGateState.Authorized
    if (authorizedGate == null) {
      DeviceSetupScaffold(
        state = gateState,
        widthClass = widthClass,
        isBusy = isBusy,
        actionError = actionError,
        onRegisterDevice = deviceGateVm::registerCurrentDevice,
        onRevokeDevice = deviceGateVm::revokeDevice,
        onReauthorizeCurrent = deviceGateVm::reauthorizeCurrentDevice,
        onRetry = deviceGateVm::evaluateDeviceAccess,
        onSignOut = { deviceGateVm.signOut(context) },
      )
      return@BoxWithConstraints
    }

    val activeShopId = authorizedGate.shopId.ifBlank {
      authorizedGate.device.shopId.ifBlank { currentUser.uid }
    }
    val activeUserRole = authorizedGate.userRole
    val isOwner = activeUserRole == ShopUserRole.OWNER

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val shopPreferences by appContainer.shopPreferencesRepository.preferencesFlow
      .collectAsStateWithLifecycle(initialValue = ShopPreferences())

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var openPaymentsRequested by remember { mutableStateOf(false) }

    val isOnline by appContainer.connectivityMonitor.isOnline.collectAsStateWithLifecycle()
    val syncState by appContainer.syncEngine.syncState.collectAsStateWithLifecycle()
    val pendingSyncCount by appContainer.deviceSyncRepository.observePendingOutboxCount()
      .collectAsStateWithLifecycle(initialValue = 0)
    val conflictCount by appContainer.deviceSyncRepository.observeUnresolvedConflictCount()
      .collectAsStateWithLifecycle(initialValue = 0)

    DisposableEffect(currentUser.uid, activeShopId) {
      appContainer.syncEngine.startRealtimeSync(activeShopId, scope)
      onDispose {
        appContainer.syncEngine.stopRealtimeSync()
      }
    }

    val isDashboardActive = currentDestination == null ||
      currentDestination.hierarchy.any { it.hasRoute(DashboardRoute::class) }
    val isCustomersActive = currentDestination?.hierarchy?.any { it.hasRoute(CustomersRoute::class) } == true
    val isOrdersActive = currentDestination?.hierarchy?.any { it.hasRoute(OrdersRoute::class) } == true
    val isLedgerActive = currentDestination?.hierarchy?.any { it.hasRoute(LedgerRoute::class) } == true
    val isSettingsActive = currentDestination?.hierarchy?.any { it.hasRoute(SettingsRoute::class) } == true

    val sectionTitleRes: Int? = when {
      isCustomersActive -> R.string.nav_customers
      isOrdersActive -> R.string.nav_orders
      isLedgerActive -> R.string.drawer_nav_analytics
      isSettingsActive -> R.string.nav_settings
      else -> null
    }

    val navigateFromDashboardOrDrawer: (Any?) -> Unit = { targetRoute ->
      navController.popBackStack(DashboardRoute, inclusive = false)
      if (targetRoute != null) {
        navController.navigate(targetRoute) {
          launchSingleTop = true
        }
      }
    }

    val renderSyncPill: @Composable (Modifier) -> Unit = { pillModifier ->
      val (syncLabel, syncIcon, syncColors) = when {
        conflictCount > 0 -> Triple(
          stringResource(R.string.sync_status_conflict, conflictCount),
          Icons.Filled.WarningAmber,
          MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer,
        )
        !isOnline -> Triple(
          stringResource(R.string.sync_status_offline),
          Icons.Filled.CloudOff,
          MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant,
        )
        syncState == SyncState.SYNCING -> Triple(
          stringResource(R.string.sync_status_syncing),
          Icons.Filled.CloudSync,
          MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        )
        pendingSyncCount > 0 -> Triple(
          stringResource(R.string.sync_status_pending, pendingSyncCount),
          Icons.Filled.CloudUpload,
          MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        )
        else -> Triple(
          stringResource(R.string.sync_status_synced),
          Icons.Filled.CloudDone,
          MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      StatusPill(
        label = syncLabel,
        containerColor = syncColors.first,
        contentColor = syncColors.second,
        icon = syncIcon,
        onClick = { navController.navigate(SyncStatusRoute) },
        modifier = pillModifier.testTag("topbar_sync_pill"),
      )
    }

    ModalNavigationDrawer(
      drawerState = drawerState,
      gesturesEnabled = isDashboardActive || sectionTitleRes != null,
      drawerContent = {
        ModalDrawerSheet(modifier = Modifier.testTag("dashboard_drawer_sheet")) {
          Surface(
            color = AtelierOlive,
            contentColor = OnAtelierOlive,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 18.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Image(
                  painter = painterResource(id = R.drawable.ic_tailor_shears),
                  contentDescription = stringResource(R.string.app_name),
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp)),
                )
                Column {
                  Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = OnAtelierOlive,
                  )
                  Text(
                    text = shopPreferences.shopName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnAtelierOlive.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (currentUser.email.isNotBlank()) {
                  Text(
                    text = currentUser.email,
                    style = MaterialTheme.typography.labelMedium,
                    color = OnAtelierOlive.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                  )
                }
                StatusPill(
                  label = stringResource(
                    if (activeUserRole == ShopUserRole.OWNER) R.string.role_owner_badge
                    else R.string.role_staff_badge,
                  ),
                  containerColor = OnAtelierOlive.copy(alpha = 0.18f),
                  contentColor = OnAtelierOlive,
                )
              }
            }
          }

          Column(
            modifier = Modifier
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            NavigationDrawerItem(
              label = { Text(stringResource(R.string.nav_dashboard)) },
              selected = isDashboardActive,
              onClick = {
                navigateFromDashboardOrDrawer(null)
                scope.launch { drawerState.close() }
              },
              icon = { Icon(Icons.Outlined.Dashboard, contentDescription = null) },
              modifier = Modifier.testTag("drawer_item_dashboard"),
            )
            NavigationDrawerItem(
              label = { Text(stringResource(R.string.nav_customers)) },
              selected = isCustomersActive,
              onClick = {
                navigateFromDashboardOrDrawer(CustomersRoute)
                scope.launch { drawerState.close() }
              },
              icon = { Icon(Icons.Outlined.People, contentDescription = null) },
              modifier = Modifier.testTag("drawer_item_customers"),
            )
            RoleAwareDrawerItem(
              label = stringResource(R.string.drawer_nav_payments),
              selected = false,
              enabled = isOwner,
              onClick = {
                if (isOwner) {
                  navigateFromDashboardOrDrawer(null)
                  openPaymentsRequested = true
                  scope.launch { drawerState.close() }
                }
              },
              icon = Icons.Outlined.AccountBalanceWallet,
              modifier = Modifier.testTag("drawer_item_payments"),
            )
            NavigationDrawerItem(
              label = { Text(stringResource(R.string.nav_orders)) },
              selected = isOrdersActive,
              onClick = {
                navigateFromDashboardOrDrawer(OrdersRoute)
                scope.launch { drawerState.close() }
              },
              icon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null) },
              modifier = Modifier.testTag("drawer_item_orders"),
            )
            RoleAwareDrawerItem(
              label = stringResource(R.string.drawer_nav_analytics),
              selected = isLedgerActive,
              enabled = isOwner,
              onClick = {
                if (isOwner) {
                  navigateFromDashboardOrDrawer(LedgerRoute)
                  scope.launch { drawerState.close() }
                }
              },
              icon = Icons.AutoMirrored.Outlined.MenuBook,
              modifier = Modifier.testTag("drawer_item_ledger"),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            NavigationDrawerItem(
              label = { Text(stringResource(R.string.drawer_nav_sync)) },
              selected = false,
              onClick = {
                navController.navigate(SyncStatusRoute)
                scope.launch { drawerState.close() }
              },
              icon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) },
              modifier = Modifier.testTag("drawer_item_sync"),
            )
            NavigationDrawerItem(
              label = { Text(stringResource(R.string.nav_settings)) },
              selected = isSettingsActive,
              onClick = {
                navigateFromDashboardOrDrawer(SettingsRoute)
                scope.launch { drawerState.close() }
              },
              icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
              modifier = Modifier.testTag("drawer_item_settings"),
            )
          }
        }
      },
    ) {
      Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = if (isDashboardActive) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
          if (sectionTitleRes != null) {
            TopAppBar(
              navigationIcon = {
                IconButton(
                  onClick = { navController.popBackStack() },
                  modifier = Modifier.testTag("topbar_back_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                  )
                }
              },
              title = {
                Text(
                  text = stringResource(sectionTitleRes),
                  style = MaterialTheme.typography.titleLarge,
                )
              },
              actions = {
                renderSyncPill(Modifier.padding(end = 12.dp))
              },
              colors = if (isCustomersActive || isOrdersActive) {
                TopAppBarDefaults.topAppBarColors(
                  containerColor = AtelierOlive,
                  titleContentColor = OnAtelierOlive,
                  navigationIconContentColor = OnAtelierOlive,
                )
              } else {
                TopAppBarDefaults.topAppBarColors(
                  containerColor = MaterialTheme.colorScheme.background,
                  titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
              },
            )
          }
        },
      ) { innerPadding ->
        DarziNavHost(
          navController = navController,
          appContainer = appContainer,
          currentUser = currentUser,
          shopId = activeShopId,
          userRole = activeUserRole,
          widthClass = widthClass,
          onOpenDrawer = { scope.launch { drawerState.open() } },
          renderSyncPill = { renderSyncPill(Modifier) },
          openPaymentsRequested = openPaymentsRequested,
          onConsumeOpenPayments = { openPaymentsRequested = false },
          onShowMessage = { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
          },
          modifier = if (sectionTitleRes != null) Modifier.padding(innerPadding) else Modifier,
        )
      }
    }
  }
}

@Composable
private fun DarziNavHost(
  navController: NavHostController,
  appContainer: AppContainer,
  currentUser: AuthUser,
  shopId: String,
  userRole: ShopUserRole,
  widthClass: WindowWidthClass,
  onOpenDrawer: () -> Unit,
  renderSyncPill: @Composable () -> Unit,
  openPaymentsRequested: Boolean,
  onConsumeOpenPayments: () -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val measurementSavedMessage = stringResource(R.string.measurement_saved_feedback)
  val uid = currentUser.uid
  val isOwner = userRole == ShopUserRole.OWNER

  NavHost(
    navController = navController,
    startDestination = DashboardRoute,
    modifier = modifier.fillMaxSize(),
  ) {
    composable<DashboardRoute> {
      val dashboardViewModel: DashboardViewModel = viewModel(
        key = "dashboard_$uid",
        factory = DashboardViewModel.Factory(
          orderRepository = appContainer.orderRepository,
          customerRepository = appContainer.customerRepository,
          deviceSyncRepository = appContainer.deviceSyncRepository,
        ),
      )
      DashboardScreen(
        viewModel = dashboardViewModel,
        widthClass = widthClass,
        onCreateNewOrder = { navController.navigate(CreateOrderRoute()) },
        onOpenOrderDetail = { orderId -> navController.navigate(OrderDetailRoute(orderId)) },
        onOpenCustomerDetail = { customerId -> navController.navigate(CustomerDetailRoute(customerId)) },
        onNavigateToCustomers = { navController.navigate(CustomersRoute) { launchSingleTop = true } },
        onNavigateToOrders = { navController.navigate(OrdersRoute) { launchSingleTop = true } },
        onNavigateToLedger = {
          if (isOwner) {
            navController.navigate(LedgerRoute) { launchSingleTop = true }
          }
        },
        onOpenDrawer = onOpenDrawer,
        syncPill = renderSyncPill,
        openPaymentsRequested = openPaymentsRequested && isOwner,
        onConsumeOpenPayments = onConsumeOpenPayments,
        isOwner = isOwner,
      )
    }

    composable<LedgerRoute> {
      if (!isOwner) {
        LaunchedEffect(Unit) {
          navController.popBackStack()
        }
        return@composable
      }
      val dashboardViewModel: DashboardViewModel = viewModel(
        key = "ledger_$uid",
        factory = DashboardViewModel.Factory(
          orderRepository = appContainer.orderRepository,
          customerRepository = appContainer.customerRepository,
          deviceSyncRepository = appContainer.deviceSyncRepository,
        ),
      )
      LedgerScreen(
        viewModel = dashboardViewModel,
        widthClass = widthClass,
        onOpenOrderDetail = { orderId -> navController.navigate(OrderDetailRoute(orderId)) },
        onOpenCustomerDetail = { customerId -> navController.navigate(CustomerDetailRoute(customerId)) },
        onNavigateToOrders = { navController.navigate(OrdersRoute) { launchSingleTop = true } },
      )
    }

    composable<CustomersRoute> {
      val customersViewModel: CustomersViewModel = viewModel(
        key = "customers_$uid",
        factory = CustomersViewModel.Factory(
          customerRepository = appContainer.customerRepository,
          deviceSyncRepository = appContainer.deviceSyncRepository,
        ),
      )
      CustomersScreen(
        viewModel = customersViewModel,
        appContainer = appContainer,
        widthClass = widthClass,
        onOpenCustomerDetail = { customerId ->
          navController.navigate(CustomerDetailRoute(customerId))
        },
        onOpenMeasurementEditor = { customerId, sourceMeasurementId ->
          navController.navigate(MeasurementEditorRoute(customerId, sourceMeasurementId))
        },
        onCreateOrderForCustomer = { customerId ->
          navController.navigate(CreateOrderRoute(customerId))
        },
        onOpenOrderDetail = { orderId ->
          navController.navigate(OrderDetailRoute(orderId))
        },
      )
    }

    composable<CustomerDetailRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<CustomerDetailRoute>()
      val detailViewModel: CustomerDetailViewModel = viewModel(
        key = "customer_detail_${uid}_${route.customerId}",
        factory = CustomerDetailViewModel.Factory(
          customerId = route.customerId,
          customerRepository = appContainer.customerRepository,
          orderRepository = appContainer.orderRepository,
          deviceSyncRepository = appContainer.deviceSyncRepository,
          thermalPrintManager = appContainer.thermalPrintManager,
        ),
      )
      CustomerDetailScreen(
        viewModel = detailViewModel,
        onNavigateBack = { navController.popBackStack() },
        onOpenMeasurementEditor = { sourceMeasurementId ->
          navController.navigate(
            MeasurementEditorRoute(
              customerId = route.customerId,
              sourceMeasurementId = sourceMeasurementId,
            ),
          )
        },
        onCreateOrder = { navController.navigate(CreateOrderRoute(route.customerId)) },
        onOpenOrder = { orderId -> navController.navigate(OrderDetailRoute(orderId)) },
      )
    }

    composable<MeasurementEditorRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<MeasurementEditorRoute>()
      val editorViewModel: MeasurementEditorViewModel = viewModel(
        key = "meas_editor_${uid}_${route.customerId}_${route.sourceMeasurementId}",
        factory = MeasurementEditorViewModel.Factory(
          customerId = route.customerId,
          sourceMeasurementId = route.sourceMeasurementId,
          customerRepository = appContainer.customerRepository,
          deviceSyncRepository = appContainer.deviceSyncRepository,
        ),
      )
      MeasurementEditorScreen(
        viewModel = editorViewModel,
        onNavigateBack = { navController.popBackStack() },
        onSaved = {
          onShowMessage(measurementSavedMessage)
          navController.popBackStack()
        },
      )
    }

    composable<OrdersRoute> {
      val ordersViewModel: OrdersViewModel = viewModel(
        key = "orders_$uid",
        factory = OrdersViewModel.Factory(
          orderRepository = appContainer.orderRepository,
          customerRepository = appContainer.customerRepository,
        ),
      )
      OrdersScreen(
        viewModel = ordersViewModel,
        appContainer = appContainer,
        widthClass = widthClass,
        onCreateNewOrder = { navController.navigate(CreateOrderRoute()) },
        onOpenOrderDetail = { orderId -> navController.navigate(OrderDetailRoute(orderId)) },
        onOpenCustomerDetail = { customerId -> navController.navigate(CustomerDetailRoute(customerId)) },
        onShowMessage = onShowMessage,
        userRole = userRole,
      )
    }

    composable<CreateOrderRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<CreateOrderRoute>()
      val createVm: CreateOrderViewModel = viewModel(
        key = "create_order_${uid}_${route.customerId}",
        factory = CreateOrderViewModel.Factory(
          initialCustomerId = route.customerId,
          customerRepository = appContainer.customerRepository,
          orderRepository = appContainer.orderRepository,
          deviceSyncRepository = appContainer.deviceSyncRepository,
        ),
      )
      CreateOrderScreen(
        viewModel = createVm,
        onNavigateBack = { navController.popBackStack() },
        onOrderCreated = { orderId ->
          navController.popBackStack()
          navController.navigate(OrderDetailRoute(orderId))
        },
      )
    }

    composable<OrderDetailRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<OrderDetailRoute>()
      val orderDetailVm: OrderDetailViewModel = viewModel(
        key = "order_detail_${uid}_${userRole.name}_${route.orderId}",
        factory = OrderDetailViewModel.Factory(
          orderId = route.orderId,
          orderRepository = appContainer.orderRepository,
          deviceSyncRepository = appContainer.deviceSyncRepository,
          thermalPrintManager = appContainer.thermalPrintManager,
          orderMediaStore = appContainer.orderMediaStore,
          customerRepository = appContainer.customerRepository,
          userRole = userRole,
        ),
      )
      OrderDetailScreen(
        viewModel = orderDetailVm,
        onNavigateBack = { navController.popBackStack() },
        onOpenCustomer = { customerId -> navController.navigate(CustomerDetailRoute(customerId)) },
      )
    }

    composable<SettingsRoute> {
      val settingsViewModel: SettingsViewModel = viewModel(
        key = "settings_${uid}_${shopId}_${userRole.name}",
        factory = SettingsViewModel.Factory(
          currentUserId = uid,
          currentUser = currentUser,
          repository = appContainer.shopPreferencesRepository,
          shopRepository = appContainer.shopRepository,
          deviceSyncRepository = appContainer.deviceSyncRepository,
          shopBackupRepository = appContainer.shopBackupRepository,
          shopCloudRepository = appContainer.shopCloudRepository,
          authSessionProvider = appContainer.authSessionProvider,
          thermalPrinterClient = appContainer.thermalPrinterClient,
          thermalPrintManager = appContainer.thermalPrintManager,
          shopId = shopId,
          userRole = userRole,
        ),
      )
      SettingsScreen(
        viewModel = settingsViewModel,
        onShowMessage = onShowMessage,
        onOpenSyncStatus = { navController.navigate(SyncStatusRoute) },
      )
    }

    composable<SyncStatusRoute> {
      val syncVm: SyncStatusViewModel = viewModel(
        key = "sync_status_$uid",
        factory = SyncStatusViewModel.Factory(
          syncEngine = appContainer.syncEngine,
          deviceSyncRepository = appContainer.deviceSyncRepository,
          connectivityMonitor = appContainer.connectivityMonitor,
        ),
      )
      SyncStatusScreen(
        viewModel = syncVm,
        onNavigateBack = { navController.popBackStack() },
      )
    }
  }
}

@Composable
private fun RoleAwareDrawerItem(
  label: String,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  if (enabled) {
    NavigationDrawerItem(
      label = { Text(label) },
      selected = selected,
      onClick = onClick,
      icon = { Icon(icon, contentDescription = null) },
      modifier = modifier,
    )
  } else {
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f)
    Surface(
      onClick = {},
      enabled = false,
      shape = RoundedCornerShape(28.dp),
      color = Color.Transparent,
      modifier = modifier
        .fillMaxWidth()
        .height(56.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(start = 16.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = disabledColor,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color = disabledColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
