package com.example.data.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import com.example.data.local.database.DarziDatabase
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.OrderRepository
import com.example.data.local.repository.ShopRepository
import com.example.testing.AuthorizedShopTestRule
import com.example.testing.InMemoryShopPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class ShopBackupRepositoryTest {

  private lateinit var database: DarziDatabase
  private lateinit var shopRepo: ShopRepository
  private lateinit var customerRepo: CustomerRepository
  private lateinit var orderRepo: OrderRepository
  private lateinit var prefsRepo: InMemoryShopPreferencesRepository
  private lateinit var backupRepo: ShopBackupRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    database = Room.inMemoryDatabaseBuilder(context, DarziDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    shopRepo = ShopRepository(database)
    customerRepo = CustomerRepository(database, shopRepo)
    orderRepo = OrderRepository(database, shopRepo)
    prefsRepo = InMemoryShopPreferencesRepository()
    backupRepo = ShopBackupRepository(database, shopRepo, prefsRepo)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `exportToJson and restoreFromJson round-trip all shop entities without data loss`() = runBlocking {
    prefsRepo.updateShopProfile("Royal Stitch Lahore", "0300-1122334")
    prefsRepo.updatePaperWidth(80)
    shopRepo.updateShopProfile("Royal Stitch Lahore", "0300-1122334", "royal@darzi.pk")

    val customer = customerRepo.createCustomer("Usman Tariq", "0301-9988776", deviceId = "phone-1").getOrThrow()
    val measSnapshot = MeasurementSnapshot(
      kameezLambai = 41.0,
      tera = 18.5,
      bazu = 24.5,
      chati = 42.0,
      kamar = 38.5,
      gheraDaman = 43.0,
      galaCollar = 16.5,
      cuff = 9.5,
      shalwarLambai = 39.5,
      paincha = 7.75,
      gheraAsan = 16.0,
    )
    val styleSnapshot = StyleSnapshot(
      neckStyle = NeckStyle.BAN,
      damanStyle = DamanStyle.GOL,
      pocketStyle = PocketStyle.TWO_SIDE,
      sleeveStyle = SleeveStyle.SINGLE_CUFF,
      silaiStyle = SilaiStyle.DOUBLE,
    )
    customerRepo.saveMeasurement(
      customerId = customer.customerId,
      snapshot = measSnapshot,
      neckStyle = styleSnapshot.neckStyle,
      damanStyle = styleSnapshot.damanStyle,
      pocketStyle = styleSnapshot.pocketStyle,
      sleeveStyle = styleSnapshot.sleeveStyle,
      silaiStyle = styleSnapshot.silaiStyle,
      label = "Eid Fit",
      deviceId = "phone-1",
    ).getOrThrow()

    val order = orderRepo.createOrder(
      customerId = customer.customerId,
      suitCount = 2,
      deliveryDateMillis = 1_765_000_000_000L,
      totalAmountPkr = 7500L,
      initialAdvancePkr = 2500L,
      measurementSnapshot = measSnapshot,
      styleSnapshot = styleSnapshot,
      deviceId = "phone-1",
    ).getOrThrow()

    val json = backupRepo.exportToJson(nowMillis = 1_764_000_000_000L).getOrThrow()
    assertTrue(json.contains("\"appId\": \"darzi_ledger\""))

    val summary = backupRepo.inspectBackupSummary(json).getOrThrow()
    assertEquals("Royal Stitch Lahore", summary.shopName)
    assertEquals(1, summary.customerCount)
    assertEquals(1, summary.measurementCount)
    assertEquals(1, summary.orderCount)
    assertEquals(1, summary.paymentCount)

    // Wipe database cleanly and restore from exported JSON
    database.clearAllTables()
    prefsRepo.updateShopProfile("Blank", "")
    prefsRepo.updatePaperWidth(58)

    val report = backupRepo.restoreFromJson(json, enqueueSync = true).getOrThrow()
    assertEquals(1, report.customersRestored)
    assertEquals(1, report.measurementsRestored)
    assertEquals(1, report.ordersRestored)
    assertEquals(1, report.paymentsRestored)

    val restoredCustomer = database.customerDao().getById(customer.customerId)
    assertEquals("Usman Tariq", restoredCustomer?.name)
    val restoredOrder = database.orderDao().getById(order.orderId)
    assertEquals(7500L, restoredOrder?.totalAmountPkr)
    assertEquals(5000L, restoredOrder?.remainingAmountPkr)
    assertEquals(1, database.paymentDao().getPaymentsForOrder(order.orderId).size)
    assertEquals(80, prefsRepo.preferencesFlow.first().paperWidthMm)
    assertTrue(database.syncOutboxDao().getAllEvents().isNotEmpty())
  }

  @Test
  fun `invalid or orphan backup fails cleanly without corrupting existing database records`() = runBlocking {
    val existing = customerRepo.createCustomer("Faisal Khan", "0321-4455667", deviceId = "phone-1").getOrThrow()

    // Malformed JSON
    val malformed = backupRepo.restoreFromJson("{ not valid json")
    assertTrue(malformed.isFailure)

    // Unsupported schema version
    val badSchema = """
      {"schemaVersion":99,"appId":"darzi_ledger","exportedAtMillis":1000,"customers":[]}
    """.trimIndent()
    assertTrue(backupRepo.restoreFromJson(badSchema).isFailure)

    // Orphan payment referencing nonexistent orderId must roll back cleanly
    val orphanPaymentJson = """
      {
        "schemaVersion": 1,
        "appId": "darzi_ledger",
        "exportedAtMillis": 1000,
        "customers": [{
          "customerId": "cust-new",
          "shopId": "default_shop",
          "name": "Temp Customer",
          "phone": "0300-0000001",
          "normalizedPhone": "03000000001",
          "createdAt": 1000,
          "updatedAt": 1000,
          "lastModifiedByDevice": "phone-1"
        }],
        "payments": [{
          "paymentId": "pay-orphan",
          "orderId": "missing-order-id",
          "amountPkr": 500,
          "paymentMethod": "CASH",
          "createdAt": 1000,
          "deviceId": "phone-1"
        }]
      }
    """.trimIndent()
    val orphanResult = backupRepo.restoreFromJson(orphanPaymentJson)
    assertTrue(orphanResult.isFailure)

    // Verify existing customer untouched and rollback discarded cust-new
    assertEquals("Faisal Khan", database.customerDao().getById(existing.customerId)?.name)
    assertEquals(null, database.customerDao().getById("cust-new"))
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsBackupUiRobolectricTest {

  private val authRule = AuthorizedShopTestRule(initialSignedIn = true, authorizeLocalDevice = true)
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `settings displays json backup export and restore actions`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_export_backup_button"))
    composeTestRule.onNodeWithTag("settings_export_backup_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_restore_backup_button").assertIsDisplayed()
  }
}
