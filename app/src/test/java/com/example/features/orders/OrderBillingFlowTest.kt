package com.example.features.orders

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.TailorApp
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import com.example.testing.AuthorizedShopTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrderBillingFlowTest {

  private val authRule = AuthorizedShopTestRule()
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `create order from customer, record partial and full payments, and transition status`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }

    // Create a customer
    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("add_customer_fab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_form_name_input").performTextInput("Imran Siddiqui")
    composeTestRule.onNodeWithTag("customer_form_phone_input").performTextInput("03214445566")
    composeTestRule.onNodeWithTag("customer_form_save_button").performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("customer_new_order_button").fetchSemanticsNodes().isNotEmpty()
    }

    // Launch New Order from Customer Detail
    composeTestRule.onNodeWithTag("customer_new_order_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("create_order_screen").fetchSemanticsNodes().isNotEmpty()
    }

    // Fill billing and measurements
    composeTestRule.onNodeWithTag("order_total_bill_input").performScrollTo().performTextInput("5000")
    composeTestRule.onNodeWithTag("order_advance_input").performScrollTo().performTextReplacement("1500")
    composeTestRule.onNodeWithTag("order_meas_lambai").performScrollTo().performTextInput("42")
    composeTestRule.onNodeWithTag("order_meas_tera").performScrollTo().performTextInput("18.5")
    composeTestRule.onNodeWithTag("order_meas_bazu").performScrollTo().performTextInput("24")
    composeTestRule.onNodeWithTag("order_meas_chati").performScrollTo().performTextInput("43")
    composeTestRule.onNodeWithTag("order_meas_kamar").performScrollTo().performTextInput("39")
    composeTestRule.onNodeWithTag("order_meas_ghera").performScrollTo().performTextInput("44")
    composeTestRule.onNodeWithTag("order_meas_gala").performScrollTo().performTextInput("16.5")
    composeTestRule.onNodeWithTag("order_meas_shalwar").performScrollTo().performTextInput("40")
    composeTestRule.onNodeWithTag("order_meas_paincha").performScrollTo().performTextInput("7.5")
    composeTestRule.onNodeWithTag("order_meas_asan").performScrollTo().performTextInput("16")

    composeTestRule.onNodeWithTag("order_submit_button").performScrollTo().performClick()

    // Verify Order Detail opened and scroll to Billing card showing Rs. 3,500 remaining
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_billing_card"))
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Rs. 3,500").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onAllNodesWithText("Rs. 3,500").onFirst().assertIsDisplayed()

    // Record partial payment Rs. 1,000 -> Remaining Rs. 2,500
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_record_payment_button"))
    composeTestRule.onNodeWithTag("order_record_payment_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("payment_amount_input").performTextInput("1000")
    composeTestRule.onNodeWithTag("payment_confirm_button").performClick()

    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_billing_card"))
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Rs. 2,500").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onAllNodesWithText("Rs. 2,500").onFirst().assertIsDisplayed()

    // One-tap Settle Full Remaining -> Paid
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_settle_remaining_button"))
    composeTestRule.onNodeWithTag("order_settle_remaining_button").performClick()
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_billing_card"))
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Paid").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onAllNodesWithText("Paid").onFirst().assertIsDisplayed()

    // Transition status Pending -> Ready -> Delivered
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_mark_ready_button"))
    composeTestRule.onNodeWithTag("order_mark_ready_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_mark_delivered_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_mark_delivered_button"))
    composeTestRule.onNodeWithTag("order_mark_delivered_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_mark_delivered_button").fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_billing_card"))
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Delivered").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onAllNodesWithText("Delivered").onFirst().assertIsDisplayed()
  }

  @Test
  fun `orders directory shows scoped search, metrics cards, detail tabs, and deletes order`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val orderId = runBlocking {
      val cust = app.container.customerRepository.createCustomer(
        name = "Ali Hassan",
        rawPhone = "0305-7223392",
        customId = "77",
        deviceId = "phone-1",
      ).getOrThrow()
      app.container.orderRepository.createOrder(
        customerId = cust.customerId,
        suitCount = 6,
        deliveryDateMillis = System.currentTimeMillis() + 7L * 86_400_000L,
        totalAmountPkr = 7200L,
        initialAdvancePkr = 200L,
        measurementSnapshot = MeasurementSnapshot(
          kameezLambai = 41.0,
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
          silaiStyle = SilaiStyle.DOUBLE,
        ),
        notes = "Prepare before Eid",
        deviceId = "phone-1",
      ).getOrThrow().orderId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_orders").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_card_$orderId").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("orders_total_count_text").assertIsDisplayed()
    composeTestRule.onNodeWithText("Total orders: 1").assertIsDisplayed()
    composeTestRule.onNodeWithText("CUSTOMER ID: 77").assertIsDisplayed()
    composeTestRule.onNodeWithText("Quantity").assertIsDisplayed()
    composeTestRule.onNodeWithText("Pending Amount").assertIsDisplayed()
    composeTestRule.onNodeWithTag("orders_scope_id").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("orders_search_input").performTextInput("77")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("order_card_$orderId").assertIsDisplayed()
    composeTestRule.onNodeWithTag("orders_search_clear").performClick()

    // Open Order Detail and verify Summary, Actions, Tabs (Payments / Measurement / Media)
    composeTestRule.onNodeWithTag("order_card_$orderId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Date Created").assertIsDisplayed()
    composeTestRule.onNodeWithText("Delivery Date").assertIsDisplayed()
    composeTestRule.onNodeWithText("Total Cost").assertIsDisplayed()

    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_settle_remaining_button"))
    composeTestRule.onNodeWithText("Actions").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mark as Paid").assertIsDisplayed()
    composeTestRule.onNodeWithText("Partial Payment").assertIsDisplayed()
    composeTestRule.onNodeWithText("Delete").assertIsDisplayed()
    composeTestRule.onNodeWithText("View Receipt").assertIsDisplayed()

    // Payments tab shows initial Rs. 200 payment
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_tab_payments"))
    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(androidx.compose.ui.test.hasText("Amount: Rs. 200"))
    composeTestRule.onNodeWithText("Amount: Rs. 200").assertIsDisplayed()

    // Switch to Measurement tab
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_tab_measurement"))
    composeTestRule.onNodeWithTag("order_tab_measurement").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_measurement_tab_card").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_measurement_tab_card"))
    composeTestRule.onNodeWithText("41\"").assertIsDisplayed()

    // Switch to Media tab
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_tab_media"))
    composeTestRule.onNodeWithTag("order_tab_media").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_media_add_button").fetchSemanticsNodes().isNotEmpty()
    }

    // Delete order and verify removal from Orders list
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_cancel_button"))
    composeTestRule.onNodeWithTag("order_cancel_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("order_confirm_delete_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("orders_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("Total orders: 0").fetchSemanticsNodes().isNotEmpty()
    }
    assertTrue(
      composeTestRule.onAllNodesWithTag("order_card_$orderId").fetchSemanticsNodes().isEmpty(),
    )
  }

  @Test
  fun `create order hides measurement selector for single measurement and shows selectable measurements for multiple`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val (singleCustId, multiCustId, kurtaId, pantId) = runBlocking {
      val singleCust = app.container.customerRepository.createCustomer(
        name = "Single Suit Customer",
        rawPhone = "03001112233",
        deviceId = "phone-1",
      ).getOrThrow()
      app.container.customerRepository.saveMeasurement(
        customerId = singleCust.customerId,
        snapshot = MeasurementSnapshot(
          kameezLambai = 39.0,
          tera = 17.5,
          bazu = 23.0,
          chati = 40.0,
          kamar = 36.0,
          gheraDaman = 41.0,
          galaCollar = 15.5,
          cuff = 9.0,
          shalwarLambai = 38.0,
          paincha = 7.0,
          gheraAsan = 15.0,
        ),
        neckStyle = NeckStyle.BAN,
        damanStyle = DamanStyle.GOL,
        pocketStyle = PocketStyle.TWO_SIDE,
        sleeveStyle = SleeveStyle.PLAIN,
        silaiStyle = SilaiStyle.SINGLE,
        label = "Kurta",
        deviceId = "phone-1",
      ).getOrThrow()

      val multiCust = app.container.customerRepository.createCustomer(
        name = "Multi Suit Customer",
        rawPhone = "03019998877",
        deviceId = "phone-1",
      ).getOrThrow()
      val kurta = app.container.customerRepository.saveMeasurement(
        customerId = multiCust.customerId,
        snapshot = MeasurementSnapshot(
          kameezLambai = 42.0,
          tera = 18.0,
          bazu = 24.5,
          chati = 44.0,
          kamar = 40.0,
          gheraDaman = 45.0,
          galaCollar = 16.5,
          cuff = 9.5,
          shalwarLambai = 41.0,
          paincha = 8.0,
          gheraAsan = 16.5,
        ),
        neckStyle = NeckStyle.BAN,
        damanStyle = DamanStyle.CHORASI,
        pocketStyle = PocketStyle.FRONT,
        sleeveStyle = SleeveStyle.SINGLE_CUFF,
        silaiStyle = SilaiStyle.DOUBLE,
        label = "Kurta",
        deviceId = "phone-1",
        nowMillis = 1_000L,
      ).getOrThrow()
      val pant = app.container.customerRepository.saveMeasurement(
        customerId = multiCust.customerId,
        snapshot = MeasurementSnapshot(
          kameezLambai = 31.0,
          tera = 16.0,
          bazu = 22.0,
          chati = 37.0,
          kamar = 32.0,
          gheraDaman = 38.0,
          galaCollar = 15.0,
          cuff = 8.5,
          shalwarLambai = 39.5,
          paincha = 7.25,
          gheraAsan = 14.5,
        ),
        neckStyle = NeckStyle.COLLAR,
        damanStyle = DamanStyle.GOL,
        pocketStyle = PocketStyle.ONE_SIDE,
        sleeveStyle = SleeveStyle.PLAIN,
        silaiStyle = SilaiStyle.SINGLE,
        label = "Pant",
        deviceId = "phone-1",
        nowMillis = 2_000L,
      ).getOrThrow()
      app.container.customerRepository.saveMeasurement(
        customerId = multiCust.customerId,
        snapshot = MeasurementSnapshot(
          kameezLambai = 40.0,
          tera = 17.0,
          bazu = 23.5,
          chati = 41.0,
          kamar = 37.0,
          gheraDaman = 42.0,
          galaCollar = 16.0,
          cuff = 9.0,
          shalwarLambai = 40.0,
          paincha = 7.5,
          gheraAsan = 15.5,
        ),
        neckStyle = NeckStyle.HALF_BAN,
        damanStyle = DamanStyle.GOL,
        pocketStyle = PocketStyle.TWO_SIDE,
        sleeveStyle = SleeveStyle.STUDS,
        silaiStyle = SilaiStyle.CHAMAK_DHAGA,
        label = "Shalwar Kameez",
        deviceId = "phone-1",
        nowMillis = 3_000L,
      ).getOrThrow()
      listOf(singleCust.customerId, multiCust.customerId, kurta.measurementId, pant.measurementId)
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_orders").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("orders_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("add_order_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("create_order_screen").fetchSemanticsNodes().isNotEmpty()
    }

    // 1. Select customer with a SINGLE measurement -> selector must NOT be visible; 39" auto-loaded
    composeTestRule.onNodeWithTag("order_pick_customer_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pick_customer_$singleCustId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pick_customer_$singleCustId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("39").fetchSemanticsNodes().isNotEmpty()
    }
    assertTrue(
      composeTestRule.onAllNodesWithTag("order_measurement_selector_card").fetchSemanticsNodes().isEmpty(),
    )

    // 2. Switch to customer with MULTIPLE measurements -> selector MUST be visible with Kurta, Pant, Shalwar Kameez
    composeTestRule.onNodeWithTag("order_pick_customer_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pick_customer_$multiCustId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pick_customer_$multiCustId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_measurement_selector_card").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_select_measurement_$kurtaId").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("order_select_measurement_$pantId").performScrollTo().assertIsDisplayed()

    // Select Pant measurement -> measurement values update to 31 / 39.5 and label header shows Pant
    composeTestRule.onNodeWithTag("order_select_measurement_$pantId").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("31").fetchSemanticsNodes().isNotEmpty()
    }

    // Enter bill & submit -> verify Order Detail Measurement tab shows Pant pill
    composeTestRule.onNodeWithTag("order_total_bill_input").performScrollTo().performTextInput("4200")
    composeTestRule.onNodeWithTag("order_submit_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_tab_measurement"))
    composeTestRule.onNodeWithTag("order_tab_measurement").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_measurement_label_pill").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_measurement_label_pill"))
    composeTestRule.onNodeWithText("Pant").assertIsDisplayed()
  }

  @Test
  fun `create order uses style chips with no default pocket and allows multi-select pockets`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val custId = runBlocking {
      app.container.customerRepository.createCustomer(
        name = "Pocket Test Customer",
        rawPhone = "03025556677",
        deviceId = "phone-1",
      ).getOrThrow().customerId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_tile_orders").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("orders_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("add_order_fab").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("create_order_screen").fetchSemanticsNodes().isNotEmpty()
    }

    // Select customer without saved measurements
    composeTestRule.onNodeWithTag("order_pick_customer_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("pick_customer_$custId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("pick_customer_$custId").performClick()

    // Verify all pocket chips are unselected by default (no default pocket value)
    composeTestRule.onNodeWithTag("style_preferences_card").performScrollTo()
    composeTestRule.onNodeWithTag("style_pocket_front").performScrollTo().assertIsNotSelected()
    composeTestRule.onNodeWithTag("style_pocket_one_side").performScrollTo().assertIsNotSelected()
    composeTestRule.onNodeWithTag("style_pocket_two_side").performScrollTo().assertIsNotSelected()
    composeTestRule.onNodeWithTag("style_pocket_shalwar_pocket").performScrollTo().assertIsNotSelected()

    // Select BOTH 2 Side and Shalwar Pocket
    composeTestRule.onNodeWithTag("style_pocket_two_side").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("style_pocket_shalwar_pocket").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("style_pocket_two_side").assertIsSelected()
    composeTestRule.onNodeWithTag("style_pocket_shalwar_pocket").assertIsSelected()

    // Fill minimal billing & measurements and submit
    composeTestRule.onNodeWithTag("order_total_bill_input").performScrollTo().performTextInput("3500")
    composeTestRule.onNodeWithTag("order_meas_lambai").performScrollTo().performTextInput("40")
    composeTestRule.onNodeWithTag("order_meas_tera").performScrollTo().performTextInput("18")
    composeTestRule.onNodeWithTag("order_meas_bazu").performScrollTo().performTextInput("24")
    composeTestRule.onNodeWithTag("order_meas_chati").performScrollTo().performTextInput("42")
    composeTestRule.onNodeWithTag("order_meas_kamar").performScrollTo().performTextInput("38")
    composeTestRule.onNodeWithTag("order_meas_ghera").performScrollTo().performTextInput("43")
    composeTestRule.onNodeWithTag("order_meas_gala").performScrollTo().performTextInput("16")
    composeTestRule.onNodeWithTag("order_meas_shalwar").performScrollTo().performTextInput("39")
    composeTestRule.onNodeWithTag("order_meas_paincha").performScrollTo().performTextInput("7.5")
    composeTestRule.onNodeWithTag("order_meas_asan").performScrollTo().performTextInput("16")

    composeTestRule.onNodeWithTag("order_submit_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_detail_content").performScrollToNode(hasTestTag("order_tab_measurement"))
    composeTestRule.onNodeWithTag("order_tab_measurement").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("order_measurement_tab_card").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_measurement_tab_card"))
    composeTestRule.onNodeWithText("Double Side Pocket, Shalwar Pocket")
      .performScrollTo()
      .assertIsDisplayed()
  }
}
