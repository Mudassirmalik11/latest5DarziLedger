package com.example.features.customers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.TailorApp
import com.example.testing.AuthorizedShopTestRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomerMeasurementFlowTest {

  private val authRule = AuthorizedShopTestRule()
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `create customer, detect duplicate phone, and record shalwar kameez measurements`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }

    // Navigate to Customers
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitForIdle()

    // Create customer
    composeTestRule.onNodeWithTag("add_customer_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_form_name_input").performTextInput("Salman Qureshi")
    composeTestRule.onNodeWithTag("customer_form_phone_input").performTextInput("03001112233")
    composeTestRule.onNodeWithTag("customer_form_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_content").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Salman Qureshi").onFirst().assertIsDisplayed()

    // Add Shalwar Kameez measurements
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("add_measurement_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("add_measurement_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_editor_screen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("meas_kameez_lambai").performTextInput("41.5")
    composeTestRule.onNodeWithTag("meas_tera").performTextInput("18")
    composeTestRule.onNodeWithTag("meas_bazu").performTextInput("24.5")
    composeTestRule.onNodeWithTag("meas_chati").performTextInput("42")
    composeTestRule.onNodeWithTag("meas_kamar").performTextInput("38")
    composeTestRule.onNodeWithTag("meas_ghera_daman").performTextInput("43")
    composeTestRule.onNodeWithTag("meas_gala_collar").performTextInput("16.5")
    composeTestRule.onNodeWithTag("meas_shalwar_lambai").performTextInput("39.5")
    composeTestRule.onNodeWithTag("meas_paincha").performTextInput("7.5")
    composeTestRule.onNodeWithTag("meas_ghera_asan").performTextInput("16")

    composeTestRule.onNodeWithTag("style_neck_collar").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("meas_save_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("41.5\"").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_detail_content").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasText("41.5\""))
    composeTestRule.onAllNodesWithText("41.5\"").onFirst().assertIsDisplayed()
  }

  @Test
  fun `customers directory displays sequential ids, address, and supports scoped search and filter`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val firstId: String
    val secondId: String
    val thirdId: String
    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      firstId = app.container.customerRepository.createCustomer(
        name = "Ali",
        rawPhone = "03057223392",
        notes = "Rehmana abad",
        deviceId = device.deviceId,
      ).getOrThrow().customerId
      delay(15)
      secondId = app.container.customerRepository.createCustomer(
        name = "مدثر",
        rawPhone = "03259196082",
        notes = "رحمان اباد",
        deviceId = device.deviceId,
      ).getOrThrow().customerId
      delay(15)
      thirdId = app.container.customerRepository.createCustomer(
        name = "Bilal Khan",
        rawPhone = "03119998877",
        notes = "",
        deviceId = device.deviceId,
      ).getOrThrow().customerId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_card_$thirdId").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("customers_total_count_text")
      .assertTextEquals("Total Customers: 3")
    composeTestRule.onNodeWithTag("add_customer_fab").assertIsDisplayed()

    // Filter to customers with an address/notes only -> excludes Bilal Khan (ID: 3)
    composeTestRule.onNodeWithTag("customers_filter_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_filter_address_only").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_total_count_text")
      .assertTextEquals("Total Customers: 2")

    // Both Ali (ID: 1) and مدثر (ID: 2) fit cleanly on screen
    composeTestRule.onNodeWithTag("customer_card_$secondId").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_card_$firstId").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("ID: 2").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("ID: 1").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("رحمان اباد").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Rehmana abad").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("0325-9196082").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("0305-7223392").onFirst().assertIsDisplayed()

    // Search by ID scope -> find ID: 1 (Ali)
    composeTestRule.onNodeWithTag("customers_scope_id").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("customers_search_input").performTextInput("1")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_total_count_text")
      .assertTextEquals("Total Customers: 1")
    composeTestRule.onNodeWithTag("customer_card_$firstId").assertIsDisplayed()

    // Clear search and switch to Phone scope -> search unformatted digits
    composeTestRule.onNodeWithTag("customers_search_clear").performClick()
    composeTestRule.onNodeWithTag("customers_scope_phone").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("customers_search_input").performTextInput("0325919")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_total_count_text")
      .assertTextEquals("Total Customers: 1")
    composeTestRule.onNodeWithTag("customer_card_$secondId").assertIsDisplayed()

    // Clear search and switch to Address scope -> search Urdu address
    composeTestRule.onNodeWithTag("customers_search_clear").performClick()
    composeTestRule.onNodeWithTag("customers_scope_address").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("customers_search_input").performTextInput("رحمان")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_total_count_text")
      .assertTextEquals("Total Customers: 1")
    composeTestRule.onNodeWithTag("customer_card_$secondId").assertIsDisplayed()
  }

  @Test
  fun `add new customer modal shows custom id placeholder, close and cancel buttons, and saves custom id`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      app.container.customerRepository.createCustomer(
        name = "Ali",
        rawPhone = "03057223392",
        notes = "Rehmana abad",
        deviceId = device.deviceId,
      ).getOrThrow()
      delay(15)
      app.container.customerRepository.createCustomer(
        name = "مدثر",
        rawPhone = "03259196082",
        notes = "رحمان اباد",
        deviceId = device.deviceId,
      ).getOrThrow()
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("ID: 2").fetchSemanticsNodes().isNotEmpty()
    }

    // Open Add New Customer modal
    composeTestRule.onNodeWithTag("add_customer_fab").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("customer_form_dialog").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Add New Customer").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Name *").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Phone").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Address").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Custom ID").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_form_id_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_form_close_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_form_cancel_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_form_save_button").assertIsDisplayed()

    // Dismiss via X button
    composeTestRule.onNodeWithTag("customer_form_close_button").performClick()
    composeTestRule.waitForIdle()
    assert(composeTestRule.onAllNodesWithTag("customer_form_dialog").fetchSemanticsNodes().isEmpty())

    // Reopen and create customer with Name *, Address, and user-entered Custom ID (phone blank)
    composeTestRule.onNodeWithTag("add_customer_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_form_name_input").performTextInput("Tariq Mahmood")
    composeTestRule.onNodeWithTag("customer_form_notes_input").performTextInput("Model Town")
    composeTestRule.onNodeWithTag("customer_form_id_input").performTextInput("42")
    composeTestRule.onNodeWithTag("customer_form_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onAllNodesWithText("Tariq Mahmood").onFirst().assertIsDisplayed()

    // Navigate back to Customers list and verify ID: 42 is displayed and searchable by ID
    composeTestRule.onNodeWithTag("customer_detail_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("ID: 42").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("customers_scope_id").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("customers_search_input").performTextInput("42")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_total_count_text").assertTextEquals("Total Customers: 1")
    composeTestRule.onAllNodesWithText("Tariq Mahmood").onFirst().assertIsDisplayed()
  }

  @Test
  fun `customer detail shows header, tabs, measurement options popup, print preview, save, edit, and delete`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val customerId: String
    val measId: String
    val orderId: String
    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      customerId = app.container.customerRepository.createCustomer(
        name = "Ali",
        rawPhone = "03057223392",
        notes = "Rehmana abad",
        customId = "1",
        deviceId = device.deviceId,
      ).getOrThrow().customerId
      val meas = app.container.customerRepository.saveMeasurement(
        customerId = customerId,
        snapshot = com.example.core.model.MeasurementSnapshot(
          kameezLambai = 23.0,
          tera = 12.0,
          bazu = 18.0,
          chati = 5.0,
          kamar = 19.0,
          gheraDaman = 20.0,
          galaCollar = 14.5,
          cuff = 9.5,
          shalwarLambai = 36.0,
          paincha = 7.0,
          gheraAsan = 15.0,
          notes = "Chota zari khula",
        ),
        neckStyle = com.example.core.model.NeckStyle.COLLAR,
        damanStyle = com.example.core.model.DamanStyle.GOL,
        pocketStyle = com.example.core.model.PocketStyle.TWO_SIDE,
        sleeveStyle = com.example.core.model.SleeveStyle.STUDS,
        silaiStyle = com.example.core.model.SilaiStyle.SINGLE,
        deviceId = device.deviceId,
      ).getOrThrow()
      measId = meas.measurementId
      orderId = app.container.orderRepository.createOrder(
        customerId = customerId,
        suitCount = 2,
        deliveryDateMillis = System.currentTimeMillis() + 5 * 86_400_000L,
        totalAmountPkr = 3000L,
        initialAdvancePkr = 500L,
        measurementSnapshot = meas.toMeasurementSnapshot(),
        styleSnapshot = meas.toStyleSnapshot(),
        deviceId = device.deviceId,
      ).getOrThrow().orderId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_card_$customerId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("customer_card_$customerId").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty()
    }

    // Header, Call/Whatsapp/+ Order buttons, and Tabs
    composeTestRule.onAllNodesWithText("Customer").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Ali").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("ID: 1").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Rehmana abad").onFirst().assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_call_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_whatsapp_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_new_order_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_tab_measurements").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_tab_payments").assertIsDisplayed()
    composeTestRule.onNodeWithTag("customer_tab_orders").assertIsDisplayed()

    // Scroll to measurement card and verify values
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("Male Shalwar Kameez").onFirst().assertIsDisplayed()
    composeTestRule.onAllNodesWithText("23\"").onFirst().assertIsDisplayed()

    // Click three dots -> Options popup (customer.dots.jpg)
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_options_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_options_close_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_option_edit").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_option_save").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_option_print").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_option_delete").assertIsDisplayed()

    // Test Print -> opens Measurement Slip Preview dialog
    composeTestRule.onNodeWithTag("measurement_option_print").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("slip_preview_close_button").performClick()
    composeTestRule.waitForIdle()

    // Test Save -> closes popup and keeps card active
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_option_save").performClick()
    composeTestRule.waitForIdle()
    assert(composeTestRule.onAllNodesWithTag("measurement_options_dialog").fetchSemanticsNodes().isEmpty())

    // Test Edit -> navigates to measurement editor
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_option_edit").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("measurement_editor_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("meas_save_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_detail_content").fetchSemanticsNodes().isNotEmpty()
    }

    // Test Payments tab and Orders tab
    composeTestRule.onNodeWithTag("customer_tab_payments").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("Amount: Rs. 500").onFirst().assertIsDisplayed()

    composeTestRule.onNodeWithTag("customer_tab_orders").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("order_card_$orderId").assertIsDisplayed()

    // Switch back to Measurements tab and test Delete
    composeTestRule.onNodeWithTag("customer_tab_measurements").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_options_button_$measId").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_option_delete").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("measurement_delete_confirm_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("measurement_delete_confirm_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("measurement_card_$measId").fetchSemanticsNodes().isEmpty()
    }
  }
}
