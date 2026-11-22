package com.example.features.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.TailorApp
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.PaymentMethod
import com.example.core.model.PocketStyle
import com.example.core.model.ShopUserRole
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithText
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.DataStoreShopPreferencesRepository
import com.example.core.preferences.ShopPreferences
import com.example.data.remote.AuthUser
import com.example.features.orders.OrderDetailViewModel
import com.example.features.settings.SettingsViewModel
import com.example.testing.AuthorizedShopTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StaffRoleAuthorizationUiTest {

  private val authRule = AuthorizedShopTestRule(initialUserRole = ShopUserRole.STAFF)
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `staff user sees Payments and Ledger disabled on dashboard and drawer and cannot open them`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("dashboard_tile_customers").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithTag("dashboard_tile_orders").assertIsDisplayed().assertIsEnabled()

    composeTestRule.onNodeWithTag("dashboard_tile_payments").assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithTag("dashboard_tile_ledger").assertIsDisplayed().assertIsNotEnabled()

    // Clicking disabled tiles does not navigate or open sheets
    composeTestRule.onNodeWithTag("dashboard_tile_payments").performClick()
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithTag("payments_sheet").fetchSemanticsNodes().isEmpty())

    composeTestRule.onNodeWithTag("dashboard_tile_ledger").performClick()
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithTag("ledger_screen").fetchSemanticsNodes().isEmpty())

    // Navigation drawer keeps Payments and Ledger disabled as well
    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_customers").performScrollTo().assertIsEnabled()
    composeTestRule.onNodeWithTag("drawer_item_orders").performScrollTo().assertIsEnabled()
    composeTestRule.onNodeWithTag("drawer_item_payments").performScrollTo().assertIsNotEnabled()
    composeTestRule.onNodeWithTag("drawer_item_ledger").performScrollTo().assertIsNotEnabled()

    composeTestRule.onNodeWithTag("drawer_item_payments").performClick()
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithTag("payments_sheet").fetchSemanticsNodes().isEmpty())

    composeTestRule.onNodeWithTag("drawer_item_ledger").performClick()
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithTag("ledger_screen").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun `staff user on Order Details cannot use Mark as Paid, Partial Payment, or Delete, while View Receipt works`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val orderId = seedSampleOrder(app)

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("dashboard_tile_orders").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_card_$orderId").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("order_card_$orderId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_detail_content").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_settle_remaining_button"))

    composeTestRule.onNodeWithTag("order_settle_remaining_button").assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithTag("order_record_payment_button").assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithTag("order_cancel_button").assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithTag("order_preview_customer_button").assertIsDisplayed().assertIsEnabled()

    // View Receipt stays functional for Staff
    composeTestRule.onNodeWithTag("order_preview_customer_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("slip_preview_close_button").performClick()
    composeTestRule.waitForIdle()

    // Order balance remains unchanged and order is still active
    val orderAfter = runBlocking { app.container.orderRepository.observeOrderDetail(orderId).first() }
    assertNotNull(orderAfter)
    assertEquals(1800L, orderAfter!!.order.remainingAmountPkr)
  }

  @Test
  fun `OrderDetailViewModel blocks staff from settling, recording payments, or deleting order`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val orderId = seedSampleOrder(app)

    val staffVm = OrderDetailViewModel(
      orderId = orderId,
      orderRepository = app.container.orderRepository,
      deviceSyncRepository = app.container.deviceSyncRepository,
      thermalPrintManager = app.container.thermalPrintManager,
      orderMediaStore = app.container.orderMediaStore,
      customerRepository = app.container.customerRepository,
      userRole = ShopUserRole.STAFF,
    )

    staffVm.settleFullRemaining(PaymentMethod.CASH)
    assertNotNull(staffVm.actionError.value)

    staffVm.clearError()
    var paymentSucceeded = false
    staffVm.recordPayment("500", PaymentMethod.CASH, "") { paymentSucceeded = true }
    assertTrue(!paymentSucceeded)
    assertNotNull(staffVm.actionError.value)

    staffVm.clearError()
    var deleteSucceeded = false
    staffVm.deleteOrder { deleteSucceeded = true }
    assertTrue(!deleteSucceeded)
    assertNotNull(staffVm.actionError.value)

    val persisted = runBlocking { app.container.orderRepository.observeOrderDetail(orderId).first() }
    assertNotNull(persisted)
    assertEquals(1800L, persisted!!.order.remainingAmountPkr)
  }

  @Test
  fun `ensureLocalDeviceConfigForUser preserves persisted STAFF role when called without role override`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val reloaded = runBlocking {
      app.container.deviceSyncRepository.ensureLocalDeviceConfigForUser(userUid = "test_staff_uid")
    }
    assertEquals(ShopUserRole.STAFF.name, reloaded.userRole)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OwnerRoleAuthorizationUiTest {

  private val authRule = AuthorizedShopTestRule(initialUserRole = ShopUserRole.OWNER)
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `owner user has Payments, Ledger, and all Order Detail actions enabled`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val orderId = seedSampleOrder(app)

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("dashboard_tile_customers").assertIsEnabled()
    composeTestRule.onNodeWithTag("dashboard_tile_payments").assertIsEnabled()
    composeTestRule.onNodeWithTag("dashboard_tile_orders").assertIsEnabled()
    composeTestRule.onNodeWithTag("dashboard_tile_ledger").assertIsEnabled()

    composeTestRule.onNodeWithTag("dashboard_tile_orders").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_card_$orderId").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("order_card_$orderId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_detail_content").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_settle_remaining_button"))

    composeTestRule.onNodeWithTag("order_settle_remaining_button").assertIsEnabled()
    composeTestRule.onNodeWithTag("order_record_payment_button").assertIsEnabled()
    composeTestRule.onNodeWithTag("order_cancel_button").assertIsEnabled()
    composeTestRule.onNodeWithTag("order_preview_customer_button").assertIsEnabled()
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StaffDefaultUrduAndLanguageLockUiTest {

  private val authRule = AuthorizedShopTestRule(
    initialUserRole = ShopUserRole.STAFF,
    initialLanguage = null,
  )
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `default ShopPreferences and DataStore default to Urdu`() = runBlocking {
    assertEquals(AppLanguage.URDU, ShopPreferences().language)
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val dataStoreRepo = DataStoreShopPreferencesRepository(app)
    assertEquals(AppLanguage.URDU, dataStoreRepo.preferencesFlow.first().language)
  }

  @Test
  fun `staff user lands in Urdu by default and cannot change language in Settings UI or ViewModel`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }

    // Default post-sign-in UI is Urdu
    composeTestRule.onNodeWithTag("dashboard_brand_title").assertTextContains("درزی لیجر")
    composeTestRule.onNodeWithTag("dashboard_tile_customers").assertTextContains("گاہک")
    composeTestRule.onNodeWithTag("dashboard_tile_orders").assertTextContains("آرڈرز")

    // Navigate to Settings -> Language controls are disabled and owner-only hint is shown
    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_language_hint_text"))

    composeTestRule.onNodeWithText("ایپ کی زبان").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_language_english").assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithTag("settings_language_urdu").assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithTag("settings_language_hint_text")
      .assertTextContains("ایپ کی زبان صرف دکان کا مالک تبدیل کر سکتا ہے۔")

    // Clicking disabled English option has no effect
    composeTestRule.onNodeWithTag("settings_language_english").performClick()
    composeTestRule.waitForIdle()
    val persistedLang = runBlocking { app.container.shopPreferencesRepository.preferencesFlow.first().language }
    assertEquals(AppLanguage.URDU, persistedLang)

    // ViewModel guard also rejects direct calls from Staff
    val staffVm = SettingsViewModel(
      currentUserId = "test_staff_uid",
      currentUser = AuthUser("test_staff_uid", "Ali Staff", "staff@alnoor.pk"),
      repository = app.container.shopPreferencesRepository,
      shopRepository = app.container.shopRepository,
      deviceSyncRepository = app.container.deviceSyncRepository,
      shopBackupRepository = app.container.shopBackupRepository,
      shopCloudRepository = app.container.shopCloudRepository,
      authSessionProvider = app.container.authSessionProvider,
      thermalPrinterClient = app.container.thermalPrinterClient,
      thermalPrintManager = app.container.thermalPrintManager,
      shopId = "test_shop_uid",
      userRole = ShopUserRole.STAFF,
    )
    staffVm.setLanguage(AppLanguage.ENGLISH)
    val afterVmCall = runBlocking { app.container.shopPreferencesRepository.preferencesFlow.first().language }
    assertEquals(AppLanguage.URDU, afterVmCall)
    assertNotNull(staffVm.deviceActionMessage.value)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StaffOverridesSavedEnglishToUrduUiTest {

  private val authRule = AuthorizedShopTestRule(
    initialUserRole = ShopUserRole.STAFF,
    initialLanguage = AppLanguage.ENGLISH,
  )
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `staff signed in after owner set English still sees Urdu UI and locked language buttons`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("dashboard_brand_title").assertTextContains("درزی لیجر")

    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_language_hint_text"))

    composeTestRule.onNodeWithText("ایپ کی زبان").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_language_english").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("settings_language_urdu").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("settings_language_hint_text")
      .assertTextContains("ایپ کی زبان صرف دکان کا مالک تبدیل کر سکتا ہے۔")
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OwnerDefaultUrduAndLanguageSwitchUiTest {

  private val authRule = AuthorizedShopTestRule(
    initialUserRole = ShopUserRole.OWNER,
    initialLanguage = null,
  )
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `owner lands in Urdu by default after sign-in and can switch between Urdu and English`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("dashboard_brand_title").assertTextContains("درزی لیجر")

    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_language_english"))

    composeTestRule.onNodeWithTag("settings_language_english").assertIsEnabled().performClick()
    composeTestRule.waitForIdle()

    val switchedToEnglish = runBlocking { app.container.shopPreferencesRepository.preferencesFlow.first().language }
    assertEquals(AppLanguage.ENGLISH, switchedToEnglish)
    composeTestRule.onNodeWithText("App Language").assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_language_urdu").assertIsEnabled().performClick()
    composeTestRule.waitForIdle()

    val switchedBackToUrdu = runBlocking { app.container.shopPreferencesRepository.preferencesFlow.first().language }
    assertEquals(AppLanguage.URDU, switchedBackToUrdu)
    composeTestRule.onNodeWithText("ایپ کی زبان").assertIsDisplayed()
  }
}

private fun seedSampleOrder(app: TailorApp): String = runBlocking {
  val customer = app.container.customerRepository.createCustomer(
    name = "Tariq Mahmood",
    rawPhone = "0300-1212123",
    deviceId = "test-device",
  ).getOrThrow()

  app.container.orderRepository.createOrder(
    customerId = customer.customerId,
    suitCount = 1,
    deliveryDateMillis = System.currentTimeMillis() + 3L * 86_400_000L,
    totalAmountPkr = 2000L,
    initialAdvancePkr = 200L,
    initialPaymentMethod = PaymentMethod.CASH,
    measurementSnapshot = MeasurementSnapshot(
      kameezLambai = 40.0,
      tera = 18.0,
      bazu = 24.0,
      chati = 42.0,
      kamar = 38.0,
      gheraDaman = 43.0,
      galaCollar = 16.0,
      cuff = 9.0,
      shalwarLambai = 40.0,
      paincha = 7.5,
      gheraAsan = 16.0,
    ),
    styleSnapshot = StyleSnapshot(
      neckStyle = NeckStyle.BAN,
      damanStyle = DamanStyle.GOL,
      pocketStyle = PocketStyle.TWO_SIDE,
      sleeveStyle = SleeveStyle.PLAIN,
      silaiStyle = SilaiStyle.SINGLE,
    ),
    deviceId = "test-device",
  ).getOrThrow().orderId
}
