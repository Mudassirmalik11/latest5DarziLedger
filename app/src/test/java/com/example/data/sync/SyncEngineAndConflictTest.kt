package com.example.data.sync

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.TailorApp
import com.example.core.model.DamanStyle
import com.example.core.model.DeviceRegistrationStatus
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import com.example.core.model.SyncEntityType
import com.example.core.model.SyncStatus
import com.example.data.local.database.DarziDatabase
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.OrderRepository
import com.example.data.local.repository.ShopRepository
import com.example.testing.AuthorizedShopTestRule
import com.example.testing.FakeAuthSessionProvider
import com.example.testing.FakeConnectivityMonitor
import com.example.data.remote.AuthUser
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncEngineAndConflictTest {

  private lateinit var context: Context
  private lateinit var db: DarziDatabase
  private lateinit var shopRepo: ShopRepository
  private lateinit var customerRepo: CustomerRepository
  private lateinit var orderRepo: OrderRepository
  private lateinit var deviceSyncRepo: DeviceSyncRepository
  private lateinit var connectivity: FakeConnectivityMonitor
  private lateinit var authProvider: FakeAuthSessionProvider
  private lateinit var syncEngine: SyncEngine

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
          .setProjectId("demo-no-project")
          .setApplicationId("1:1234567890:android:test")
          .setApiKey("test-api-key")
          .build(),
      )
    }
    db = Room.inMemoryDatabaseBuilder(context, DarziDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    shopRepo = ShopRepository(db)
    customerRepo = CustomerRepository(db, shopRepo)
    orderRepo = OrderRepository(db, shopRepo)
    deviceSyncRepo = DeviceSyncRepository(db)
    connectivity = FakeConnectivityMonitor(initialOnline = false)
    authProvider = FakeAuthSessionProvider(AuthUser("shop_uid", "Owner", "owner@darzi.pk"))
    syncEngine = SyncEngine(
      database = db,
      db = FirebaseFirestore.getInstance(),
      authSessionProvider = authProvider,
      connectivityMonitor = connectivity,
      deviceSyncRepository = deviceSyncRepo,
      shopRepository = shopRepo,
      orderRepository = orderRepo,
    )
  }

  @After
  fun tearDown() {
    syncEngine.stopRealtimeSync()
    db.close()
  }

  @Test
  fun `exponential backoff scales cleanly and caps at 15 minutes`() {
    assertEquals(5_000L, ConflictResolver.computeBackoffMillis(0))
    assertEquals(15_000L, ConflictResolver.computeBackoffMillis(1))
    assertEquals(45_000L, ConflictResolver.computeBackoffMillis(2))
    assertEquals(135_000L, ConflictResolver.computeBackoffMillis(3))
    assertEquals(405_000L, ConflictResolver.computeBackoffMillis(4))
    assertEquals(900_000L, ConflictResolver.computeBackoffMillis(5))
    assertEquals(900_000L, ConflictResolver.computeBackoffMillis(10))
  }

  @Test
  fun `concurrent multi-device edit detection triggers only on differing device with newer base`() {
    // Same device retrying or sequential update -> no conflict
    assertFalse(
      ConflictResolver.hasConcurrentUpdateConflict(
        localVersion = 3L,
        localDeviceId = "phone-1",
        remoteVersion = 3L,
        remoteDeviceId = "phone-1",
      ),
    )

    // Tablet edited from v1 -> v2 cleanly while cloud still on v1 -> no conflict
    assertFalse(
      ConflictResolver.hasConcurrentUpdateConflict(
        localVersion = 2L,
        localDeviceId = "tablet-1",
        remoteVersion = 1L,
        remoteDeviceId = "phone-1",
      ),
    )

    // Tablet edited from v1 -> v2 offline, while phone already pushed v2 -> conflict!
    assertTrue(
      ConflictResolver.hasConcurrentUpdateConflict(
        localVersion = 2L,
        localDeviceId = "tablet-1",
        remoteVersion = 2L,
        remoteDeviceId = "phone-1",
      ),
    )
  }

  @Test
  fun `SyncMappers omit DocumentId field and maintain Firestore rule parity including cuff zero`() = runBlocking {
    val customer = customerRepo.createCustomer(
      name = "Imran Siddiqui",
      rawPhone = "0300-1122334",
      notes = "Regular fit",
      deviceId = "phone-1",
    ).getOrThrow()

    val customerMap = SyncMappers.customerToCreateMap("shop_uid", customer)
    assertFalse(customerMap.containsKey("id"))
    assertEquals("shop_uid", customerMap["ownerUid"])
    assertEquals(false, customerMap["deleted"])

    val meas = customerRepo.saveMeasurement(
      customerId = customer.customerId,
      snapshot = MeasurementSnapshot(
        kameezLambai = 40.0,
        tera = 18.0,
        bazu = 24.0,
        chati = 41.0,
        kamar = 37.0,
        gheraDaman = 42.0,
        galaCollar = 16.0,
        cuff = 0.0,
        shalwarLambai = 39.0,
        paincha = 7.5,
        gheraAsan = 15.5,
      ),
      neckStyle = NeckStyle.ROUND,
      damanStyle = DamanStyle.GOL,
      pocketStyle = PocketStyle.TWO_SIDE,
      sleeveStyle = SleeveStyle.PLAIN,
      silaiStyle = SilaiStyle.SINGLE,
      deviceId = "phone-1",
    ).getOrThrow()

    val measMap = SyncMappers.measurementToCreateMap("shop_uid", meas)
    assertFalse(measMap.containsKey("id"))
    assertEquals(0.0, measMap["cuff"])
    assertEquals(true, measMap["currentFlag"])

    val order = orderRepo.createOrder(
      customerId = customer.customerId,
      suitCount = 1,
      deliveryDateMillis = 123_456_789L,
      totalAmountPkr = 4500L,
      initialAdvancePkr = 1500L,
      initialPaymentMethod = PaymentMethod.CASH,
      measurementSnapshot = meas.toMeasurementSnapshot(),
      styleSnapshot = meas.toStyleSnapshot(),
      deviceId = "phone-1",
    ).getOrThrow()

    val orderCreateMap = SyncMappers.orderToCreateMap("shop_uid", order)
    assertFalse(orderCreateMap.containsKey("id"))
    assertFalse(orderCreateMap.containsKey("orderNumber")) // null orderNumber omitted cleanly
    assertEquals(1500L, orderCreateMap["paidAmountPkr"])
    assertEquals(3000L, orderCreateMap["remainingAmountPkr"])

    val detailUpdateMap = SyncMappers.orderDetailsUpdateMap(
      entity = order.copy(orderNumber = 1005, orderStatus = OrderStatus.READY),
      includeOrderNumber = true,
    )
    assertEquals(1005L, detailUpdateMap["orderNumber"])
    assertEquals("READY", detailUpdateMap["orderStatus"])
    assertFalse(detailUpdateMap.containsKey("paidAmountPkr"))
  }

  @Test
  fun `resolveConflict KEEP_LOCAL bumps version and re-queues outbox, USE_CLOUD applies remote cleanly`(): Unit = runBlocking {
    val device = deviceSyncRepo.ensureLocalDeviceConfig()
    deviceSyncRepo.updateDeviceAuthorization(
      deviceId = device.deviceId,
      shopId = "shop_uid",
      deviceName = "Counter Tablet",
      deviceRole = "TABLET",
      status = DeviceRegistrationStatus.AUTHORIZED,
      registeredAtMillis = 1000L,
    )

    // Case 1: KEEP_LOCAL
    val cust1 = customerRepo.createCustomer("Local Name", "0300-4445566", deviceId = device.deviceId).getOrThrow()
    val updatedLocal = customerRepo.updateCustomer(
      customerId = cust1.customerId,
      name = "Updated Local Name",
      rawPhone = "0300-4445566",
      notes = "Keep local note",
      deviceId = device.deviceId,
    ).getOrThrow()
    val remoteVersion = updatedLocal.copy(
      name = "Cloud Name",
      version = 3L,
      lastModifiedByDevice = "other-device",
    )
    val conflict1 = ConflictResolver.createCustomerConflict(updatedLocal, remoteVersion)
    db.deviceSyncDao().upsertConflict(conflict1)

    val outboxEvents1 = db.syncOutboxDao().getAllEvents().filter { it.entityId == cust1.customerId }
    db.syncOutboxDao().markConflict(outboxEvents1.last().eventId, "Concurrent edit")

    syncEngine.resolveConflict(conflict1.conflictId, ConflictResolutionChoice.KEEP_LOCAL).getOrThrow()

    val savedAfterKeepLocal = db.customerDao().getById(cust1.customerId)!!
    assertEquals("Updated Local Name", savedAfterKeepLocal.name)
    assertEquals(4L, savedAfterKeepLocal.version) // max(2, 3) + 1
    assertTrue(db.deviceSyncDao().getConflictById(conflict1.conflictId)?.resolved == true)
    val resetEvent = db.syncOutboxDao().getAllEvents().first { it.eventId == outboxEvents1.last().eventId }
    assertEquals(SyncStatus.PENDING, resetEvent.syncStatus)

    // Case 2: USE_CLOUD
    val cust2 = customerRepo.createCustomer("Phone Draft", "0321-9988771", deviceId = device.deviceId).getOrThrow()
    val remoteWins = cust2.copy(
      name = "Cloud Authoritative Name",
      notes = "Edited on Phone",
      version = 5L,
      lastModifiedByDevice = "other-phone",
    )
    val conflict2 = ConflictResolver.createCustomerConflict(cust2, remoteWins)
    db.deviceSyncDao().upsertConflict(conflict2)
    val outbox2 = db.syncOutboxDao().getAllEvents().last { it.entityId == cust2.customerId }
    db.syncOutboxDao().markConflict(outbox2.eventId, "Concurrent edit")

    syncEngine.resolveConflict(conflict2.conflictId, ConflictResolutionChoice.USE_CLOUD).getOrThrow()

    val savedAfterCloud = db.customerDao().getById(cust2.customerId)!!
    assertEquals("Cloud Authoritative Name", savedAfterCloud.name)
    assertEquals(5L, savedAfterCloud.version)
    val syncedEvent = db.syncOutboxDao().getAllEvents().first { it.eventId == outbox2.eventId }
    assertEquals(SyncStatus.SYNCED, syncedEvent.syncStatus)
    assertTrue(db.deviceSyncDao().observeUnresolvedConflicts().first().isEmpty())
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncCenterUiRobolectricTest {

  private val authRule = AuthorizedShopTestRule(initialSignedIn = true, authorizeLocalDevice = true, initialOnline = false)
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `topbar sync pill displays offline state and navigates to Sync Center with conflict resolution`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val conflictId = runBlocking {
      val cust = app.container.customerRepository.createCustomer(
        name = "Tariq Mehmood",
        rawPhone = "0300-7778899",
        deviceId = "phone-test",
      ).getOrThrow()
      val conflict = ConflictResolver.createCustomerConflict(
        local = cust,
        remote = cust.copy(name = "Tariq Khan (Cloud)", version = 2L, lastModifiedByDevice = "tablet-2"),
      )
      app.container.database.deviceSyncDao().upsertConflict(conflict)
      conflict.conflictId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("topbar_sync_pill").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("topbar_sync_pill").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sync_status_screen").assertIsDisplayed()
    val keepLocalTag = "conflict_keep_local_$conflictId"
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("sync_no_conflicts_text").fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag("sync_status_list").performScrollToNode(hasTestTag(keepLocalTag))
    composeTestRule.onNodeWithTag(keepLocalTag).assertIsDisplayed().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("sync_no_conflicts_text").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("sync_status_list").performScrollToNode(hasTestTag("sync_no_conflicts_text"))
    composeTestRule.onNodeWithTag("sync_no_conflicts_text").assertIsDisplayed()
  }
}
