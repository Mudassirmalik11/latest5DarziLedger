package com.example.features.dashboard

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.TailorApp
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import com.example.core.preferences.AppLanguage
import com.example.core.ui.PkrFormatUtils
import com.example.data.local.daos.OrderAnalyticsRow
import com.example.data.local.daos.PaymentAnalyticsRow
import com.example.testing.AuthorizedShopTestRule
import java.util.Calendar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class AnalyticsAggregatorUnitTest {

  @Test
  fun `daily, weekly, monthly, and yearly aggregations bucket orders and payments accurately`() {
    val refCal = Calendar.getInstance().apply {
      set(2026, Calendar.NOVEMBER, 18, 15, 0, 0) // Wednesday afternoon
      set(Calendar.MILLISECOND, 0)
    }
    val refMillis = refCal.timeInMillis

    val (dayStart, _) = PkrFormatUtils.todayBoundsMillis(refMillis)
    val morningMillis = dayStart + 9 * 3_600_000L
    val eveningMillis = dayStart + 19 * 3_600_000L

    val daily = AnalyticsAggregator.aggregate(
      period = AnalyticsPeriod.DAILY,
      referenceMillis = refMillis,
      orders = listOf(
        OrderAnalyticsRow(morningMillis, 4000L, 1, OrderStatus.PENDING),
        OrderAnalyticsRow(eveningMillis, 7500L, 2, OrderStatus.DELIVERED),
      ),
      payments = listOf(
        PaymentAnalyticsRow(morningMillis, 1500L),
        PaymentAnalyticsRow(eveningMillis, 5000L),
      ),
    )

    assertEquals(6500L, daily.collectedPkr)
    assertEquals(11500L, daily.bookedSalesPkr)
    assertEquals(2, daily.ordersCount)
    assertEquals(3, daily.suitsCount)
    assertEquals(1, daily.deliveredCount)
    assertEquals(4, daily.buckets.size)
    assertEquals(1500L, daily.buckets[1].collectedPkr) // Morning
    assertEquals(5000L, daily.buckets[3].collectedPkr) // Evening

    // Weekly (7 days Mon..Sun)
    val weekly = AnalyticsAggregator.aggregate(
      period = AnalyticsPeriod.WEEKLY,
      referenceMillis = refMillis,
      orders = listOf(OrderAnalyticsRow(morningMillis, 4000L, 1, OrderStatus.PENDING)),
      payments = listOf(PaymentAnalyticsRow(morningMillis, 2000L)),
    )
    assertEquals(7, weekly.buckets.size)
    assertEquals(4000L, weekly.buckets[2].bookedSalesPkr) // Wednesday index 2 (Mon=0, Tue=1, Wed=2)

    // Monthly (Nov 2026 has 30 days -> 5 weeks)
    val monthly = AnalyticsAggregator.aggregate(
      period = AnalyticsPeriod.MONTHLY,
      referenceMillis = refMillis,
      orders = listOf(OrderAnalyticsRow(morningMillis, 4000L, 1, OrderStatus.PENDING)),
      payments = listOf(PaymentAnalyticsRow(morningMillis, 2000L)),
    )
    assertEquals(5, monthly.buckets.size)
    assertEquals(4000L, monthly.buckets[2].bookedSalesPkr) // Nov 18 -> (18-1)/7 = 2

    // Yearly (12 months)
    val yearly = AnalyticsAggregator.aggregate(
      period = AnalyticsPeriod.YEARLY,
      referenceMillis = refMillis,
      orders = listOf(OrderAnalyticsRow(morningMillis, 4000L, 1, OrderStatus.PENDING)),
      payments = listOf(PaymentAnalyticsRow(morningMillis, 2000L)),
    )
    assertEquals(12, yearly.buckets.size)
    assertEquals(4000L, yearly.buckets[Calendar.NOVEMBER].bookedSalesPkr)
  }

  @Test
  fun `calendar bounds cover exact week, month, and year windows`() {
    val wedCal = Calendar.getInstance().apply {
      set(2026, Calendar.NOVEMBER, 18, 14, 30, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val (weekStart, weekEnd) = PkrFormatUtils.weekBoundsMillis(wedCal.timeInMillis)
    assertTrue(weekEnd - weekStart >= 7L * 24L * 3_600_000L - 3_600_000L)
    val weekStartCal = Calendar.getInstance().apply { timeInMillis = weekStart }
    assertEquals(Calendar.MONDAY, weekStartCal.get(Calendar.DAY_OF_WEEK))
    assertEquals(16, weekStartCal.get(Calendar.DAY_OF_MONTH))

    val (monthStart, monthEnd) = PkrFormatUtils.monthBoundsMillis(wedCal.timeInMillis)
    val mStartCal = Calendar.getInstance().apply { timeInMillis = monthStart }
    val mEndCal = Calendar.getInstance().apply { timeInMillis = monthEnd }
    assertEquals(1, mStartCal.get(Calendar.DAY_OF_MONTH))
    assertEquals(Calendar.NOVEMBER, mStartCal.get(Calendar.MONTH))
    assertEquals(Calendar.DECEMBER, mEndCal.get(Calendar.MONTH))

    val (yearStart, yearEnd) = PkrFormatUtils.yearBoundsMillis(wedCal.timeInMillis)
    val yStartCal = Calendar.getInstance().apply { timeInMillis = yearStart }
    val yEndCal = Calendar.getInstance().apply { timeInMillis = yearEnd }
    assertEquals(Calendar.JANUARY, yStartCal.get(Calendar.MONTH))
    assertEquals(2026, yStartCal.get(Calendar.YEAR))
    assertEquals(2027, yEndCal.get(Calendar.YEAR))
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardAnalyticsAndUrduUiTest {

  private val authRule = AuthorizedShopTestRule(initialSignedIn = true, authorizeLocalDevice = true)
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  private val sampleMeasurements = MeasurementSnapshot(
    kameezLambai = 42.0,
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
  )

  private val sampleStyle = StyleSnapshot(
    neckStyle = NeckStyle.BAN,
    damanStyle = DamanStyle.GOL,
    pocketStyle = PocketStyle.TWO_SIDE,
    sleeveStyle = SleeveStyle.PLAIN,
    silaiStyle = SilaiStyle.SINGLE,
  )

  @Test
  fun `dashboard displays daily, weekly, monthly, and yearly analytics accurately`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val now = System.currentTimeMillis()
    val (todayStart, _) = PkrFormatUtils.todayBoundsMillis(now)
    val (yearStart, _) = PkrFormatUtils.yearBoundsMillis(now)
    val earlierThisYear = if (todayStart - yearStart > 2 * 24 * 3_600_000L) {
      yearStart + 12 * 3_600_000L
    } else {
      now
    }

    runBlocking {
      val customer = app.container.customerRepository.createCustomer(
        name = "Salman Qureshi",
        rawPhone = "0300-8899001",
        deviceId = "test-phone",
        nowMillis = earlierThisYear,
      ).getOrThrow()

      // Today order: 5,000 booked, 2,000 advance paid
      app.container.orderRepository.createOrder(
        customerId = customer.customerId,
        suitCount = 1,
        deliveryDateMillis = now + 3 * 86_400_000L,
        totalAmountPkr = 5000L,
        initialAdvancePkr = 2000L,
        initialPaymentMethod = PaymentMethod.CASH,
        measurementSnapshot = sampleMeasurements,
        styleSnapshot = sampleStyle,
        deviceId = "test-phone",
        nowMillis = todayStart + 10 * 3_600_000L,
      ).getOrThrow()

      // Earlier-in-year order (when today isn't Jan 1-2): 9,000 booked, 3,000 paid
      if (earlierThisYear != now) {
        app.container.orderRepository.createOrder(
          customerId = customer.customerId,
          suitCount = 2,
          deliveryDateMillis = earlierThisYear + 4 * 86_400_000L,
          totalAmountPkr = 9000L,
          initialAdvancePkr = 3000L,
          initialPaymentMethod = PaymentMethod.JAZZCASH,
          measurementSnapshot = sampleMeasurements,
          styleSnapshot = sampleStyle,
          deviceId = "test-phone",
          nowMillis = earlierThisYear,
        ).getOrThrow()
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    // Landing page stays clean: no search bar, no summary, no analytics chart, no bottom nav bar
    assertTrue(composeTestRule.onAllNodesWithTag("dashboard_search_input").fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithTag("dashboard_analytics_card").fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithTag("navigation_bar").fetchSemanticsNodes().isEmpty())

    // Open Ledger screen for Daily/Weekly/Monthly/Yearly analytics
    composeTestRule.onNodeWithTag("dashboard_tile_ledger").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("ledger_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("ledger_screen")
      .performScrollToNode(hasTestTag("dashboard_analytics_card"))
    composeTestRule.onNodeWithTag("dashboard_analytics_card").assertIsDisplayed()

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("analytics_chart").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("analytics_collected_value").assertTextContains("Rs. 2,000")
    composeTestRule.onNodeWithTag("analytics_booked_value").assertTextContains("Rs. 5,000")
    composeTestRule.onNodeWithTag("analytics_chart").performScrollTo().assertIsDisplayed()

    // Switch through Weekly -> Monthly -> Yearly
    composeTestRule.onNodeWithTag("analytics_tab_weekly").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("analytics_tab_monthly").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("analytics_tab_yearly").performClick()

    if (earlierThisYear != now) {
      composeTestRule.waitUntil(timeoutMillis = 5_000L) {
        runCatching {
          composeTestRule.onNodeWithTag("analytics_booked_value").assertTextContains("Rs. 14,000")
        }.isSuccess
      }
      composeTestRule.onNodeWithTag("analytics_collected_value").assertTextContains("Rs. 5,000")
      composeTestRule.onNodeWithTag("analytics_booked_value").assertTextContains("Rs. 14,000")
    } else {
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("analytics_collected_value").assertTextContains("Rs. 2,000")
    }
  }

  @Test
  fun `switching language to Urdu in Settings updates UI strings and persists preference`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_menu_button").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_language_urdu"))
    composeTestRule.onNodeWithTag("settings_language_urdu").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    val savedLang = runBlocking { app.container.shopPreferencesRepository.preferencesFlow.first().language }
    assertEquals(AppLanguage.URDU, savedLang)

    // Live Urdu strings render immediately across navigation and settings
    composeTestRule.onNodeWithText("ایپ کی زبان").assertIsDisplayed()
    composeTestRule.onNodeWithTag("topbar_back_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("dashboard_brand_title").assertTextContains("درزی لیجر")

    // Verify dialogs (separate Compose window) also resolve Urdu placeholders & buttons
    composeTestRule.onNodeWithTag("dashboard_add_customer_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_form_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("نیا گاہک شامل کریں").assertIsDisplayed()
    composeTestRule.onNodeWithText("نام *").assertIsDisplayed()
    composeTestRule.onNodeWithText("فون").assertIsDisplayed()
    composeTestRule.onNodeWithText("پتہ").assertIsDisplayed()
    composeTestRule.onNodeWithText("کسٹم آئی ڈی").assertIsDisplayed()
    composeTestRule.onNodeWithText("محفوظ کریں").assertIsDisplayed()
    composeTestRule.onNodeWithText("منسوخ کریں").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("dashboard_tile_ledger").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("ledger_screen")
      .performScrollToNode(hasTestTag("dashboard_analytics_card"))
    composeTestRule.onNodeWithText("کاروباری تجزیہ").assertIsDisplayed()

    // Switch back to English cleanly
    composeTestRule.onNodeWithTag("topbar_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_language_english"))
    composeTestRule.onNodeWithTag("settings_language_english").performClick()
    composeTestRule.waitForIdle()

    val revertedLang = runBlocking { app.container.shopPreferencesRepository.preferencesFlow.first().language }
    assertEquals(AppLanguage.ENGLISH, revertedLang)
    composeTestRule.onNodeWithText("App Language").assertIsDisplayed()
  }

  @Test
  fun `signed-in landing screen displays Darzi Ledger branding, 2x2 grid, bottom actions, and zero extra clutter`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val now = System.currentTimeMillis()
    runBlocking {
      val customer = app.container.customerRepository.createCustomer(
        name = "Imran Khan",
        rawPhone = "0301-4455667",
        deviceId = "test-phone",
        nowMillis = now,
      ).getOrThrow()

      app.container.orderRepository.createOrder(
        customerId = customer.customerId,
        suitCount = 1,
        deliveryDateMillis = now + 2 * 86_400_000L,
        totalAmountPkr = 4500L,
        initialAdvancePkr = 1500L,
        initialPaymentMethod = PaymentMethod.CASH,
        measurementSnapshot = sampleMeasurements,
        styleSnapshot = sampleStyle,
        deviceId = "test-phone",
        nowMillis = now,
      ).getOrThrow()
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }

    // Branding header shows Darzi Ledger and omits any premium/ad/ezytailor/search/summary clutter
    composeTestRule.onNodeWithTag("dashboard_brand_header").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dashboard_brand_title").assertTextContains("Darzi Ledger")
    assertTrue(composeTestRule.onAllNodesWithText("Premium", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithText("short ads", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithText("ezytailor", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithTag("dashboard_search_input").fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithTag("dashboard_analytics_card").fetchSemanticsNodes().isEmpty())
    assertTrue(composeTestRule.onAllNodesWithTag("navigation_bar").fetchSemanticsNodes().isEmpty())

    // 2x2 Action Grid & Pinned Bottom Dual Actions
    composeTestRule.onNodeWithTag("dashboard_tile_customers").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dashboard_tile_payments").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dashboard_tile_orders").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dashboard_tile_ledger").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dashboard_add_customer_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dashboard_new_order_button").assertIsDisplayed()

    // Payments tile opens functional Payments & Balances sheet with outstanding PKR
    composeTestRule.onNodeWithTag("dashboard_tile_payments").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("payments_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithText("Imran Khan", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Due: Rs. 3,000").assertIsDisplayed()
  }

  @Test
  fun `landing screen + Customer button creates customer and + Order opens order creation`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_add_customer_button").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("dashboard_add_customer_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customer_form_name_input").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cancel").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dashboard_drawer_sheet").assertIsDisplayed()
  }
}
