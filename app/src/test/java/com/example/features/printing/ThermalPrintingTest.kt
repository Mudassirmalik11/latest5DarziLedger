package com.example.features.printing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.TailorApp
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PaymentStatus
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.SnapshotJson
import com.example.core.model.StyleSnapshot
import com.example.core.preferences.AppLanguage
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.entities.OrderEntity
import com.example.data.local.entities.PaymentEntity
import com.example.testing.AuthorizedShopTestRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class RecordingThermalPrinterClient(
  var pairedDevices: List<DiscoveredPrinter> = listOf(
    DiscoveredPrinter("RPP02N-58mm", "00:11:22:33:44:55", isBonded = true),
  ),
  var shouldFailPrint: Boolean = false,
) : ThermalPrinterClient {
  val sentPayloads = mutableListOf<Pair<String, ByteArray>>()

  override fun isBluetoothSupported(): Boolean = true
  override fun isBluetoothEnabled(): Boolean = true
  override fun hasConnectPermission(): Boolean = true
  override fun hasScanPermission(): Boolean = true

  override suspend fun getPairedPrinters(): Result<List<DiscoveredPrinter>> =
    Result.success(pairedDevices)

  override fun discoverNearbyPrinters(): Flow<DiscoveredPrinter> = emptyFlow()

  override suspend fun printBytes(printerAddress: String, bytes: ByteArray): Result<Unit> {
    if (shouldFailPrint) {
      return Result.failure(IllegalStateException("Printer offline"))
    }
    sentPayloads.add(printerAddress to bytes)
    return Result.success(Unit)
  }
}

class ThermalReceiptFormatterTest {

  private fun sampleOrder(
    orderNumber: Int? = 1007,
    customerName: String = "Muhammad Tariq Mahmood Qureshi Al-Siddiqui",
  ): Pair<OrderWithCustomer, MeasurementSnapshot> {
    val meas = MeasurementSnapshot(
      kameezLambai = 41.5,
      tera = 18.25,
      bazu = 24.0,
      chati = 42.0,
      kamar = 38.0,
      gheraDaman = 43.0,
      galaCollar = 16.5,
      cuff = 9.5,
      shalwarLambai = 39.5,
      paincha = 7.75,
      gheraAsan = 16.0,
      notes = "Keep collar stiff with double buckram",
    )
    val style = StyleSnapshot(
      neckStyle = NeckStyle.BAN,
      damanStyle = DamanStyle.CHORASI,
      pocketStyle = PocketStyle.TWO_SIDE,
      sleeveStyle = SleeveStyle.SINGLE_CUFF,
      silaiStyle = SilaiStyle.DOUBLE,
    )
    val order = OrderEntity(
      orderId = "ord-1",
      shopId = "shop-1",
      customerId = "cust-1",
      orderNumber = orderNumber,
      suitCount = 2,
      deliveryDate = 1_764_000_000_000L,
      totalAmountPkr = 8000L,
      paidAmountPkr = 3000L,
      remainingAmountPkr = 5000L,
      orderStatus = OrderStatus.PENDING,
      paymentStatus = PaymentStatus.PARTIAL,
      measurementSnapshotJson = SnapshotJson.encodeMeasurement(meas),
      styleSnapshotJson = SnapshotJson.encodeStyle(style),
      notes = "Urgent Eid delivery before Jumma",
      createdAt = 1_763_500_000_000L,
      updatedAt = 1_763_500_000_000L,
      version = 1L,
      lastModifiedByDevice = "phone-1",
    )
    return OrderWithCustomer(
      order = order,
      customerName = customerName,
      customerPhone = "0300-5551234",
    ) to meas
  }

  @Test
  fun `58mm and 80mm customer receipt lines respect column widths and include PKR balances`() {
    val (detail, _) = sampleOrder(orderNumber = 1007)
    val payments = listOf(
      PaymentEntity("p1", "ord-1", 3000L, PaymentMethod.CASH, "Advance", 1_763_500_000_000L, "phone-1"),
    )

    val lines58 = ThermalReceiptFormatter.formatCustomerReceiptLines(
      shopName = "Al-Noor Bespoke Tailors Lahore",
      shopContact = "0300-1234567",
      orderDetail = detail,
      payments = payments,
      paperWidth = PaperWidth.MM_58,
    )
    assertTrue(lines58.all { it.length <= 32 })
    val joined58 = lines58.joinToString("\n")
    assertTrue(joined58.contains("ORDER #1007"))
    assertTrue(joined58.contains("Rs. 8,000"))
    assertTrue(joined58.contains("Rs. 3,000"))
    assertTrue(joined58.contains("Rs. 5,000"))

    val lines80 = ThermalReceiptFormatter.formatCustomerReceiptLines(
      shopName = "Al-Noor Bespoke Tailors Lahore",
      shopContact = "0300-1234567",
      orderDetail = detail,
      payments = payments,
      paperWidth = PaperWidth.MM_80,
    )
    assertTrue(lines80.all { it.length <= 48 })
  }

  @Test
  fun `offline pending order formats as NUMBER PENDING and karigar slip includes all 11 measurements`() {
    val (detail, meas) = sampleOrder(orderNumber = null, customerName = "Bilal Ahmed")
    val style = SnapshotJson.decodeStyle(detail.order.styleSnapshotJson)

    val karigar58 = ThermalReceiptFormatter.formatKarigarSlipLines(
      shopName = "Al-Noor Tailors",
      orderDetail = detail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_58,
    )
    assertTrue(karigar58.all { it.length <= 32 })
    val text = karigar58.joinToString("\n")
    assertTrue(text.contains("ORDER: NUMBER PENDING"))
    assertTrue(
      "Lambai and Tera should be paired on the same line to reduce paper length",
      karigar58.any { it.contains("Lambai: 41.5\"") && it.contains("Tera: 18.25\"") },
    )
    assertTrue(
      "Shalwar and Paincha should be paired on the same line",
      karigar58.any { it.contains("Shalwar: 39.5\"") && it.contains("Paincha: 7.75\"") },
    )
    assertTrue(text.contains("Ban"))
    assertTrue(text.contains("Chorasi"))
    assertTrue(text.contains("Double"))

    val karigar80 = ThermalReceiptFormatter.formatKarigarSlipLines(
      shopName = "Al-Noor Tailors",
      orderDetail = detail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_80,
    )
    assertTrue(karigar80.all { it.length <= 48 })
    assertTrue(karigar80.any { it.contains("Lambai: 41.5\"") && it.contains("Tera: 18.25\"") })
  }

  @Test
  fun `ESC POS byte encoding starts with INIT and ends with FEED_AND_CUT`() {
    val (detail, meas) = sampleOrder(orderNumber = 1012)
    val style = SnapshotJson.decodeStyle(detail.order.styleSnapshotJson)
    val bytes = ThermalReceiptFormatter.formatKarigarSlipBytes(
      shopName = "Al-Noor",
      orderDetail = detail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_58,
    )
    assertEquals(EscPosCommands.INIT[0], bytes[0])
    assertEquals(EscPosCommands.INIT[1], bytes[1])
    val tail = bytes.copyOfRange(bytes.size - EscPosCommands.FEED_AND_CUT.size, bytes.size)
    assertTrue(tail.contentEquals(EscPosCommands.FEED_AND_CUT))
  }

  @Test
  fun `long customer name wraps cleanly without truncation on 58mm receipt`() {
    val (detail, _) = sampleOrder(
      orderNumber = 1015,
      customerName = "Muhammad Tariq Mahmood Qureshi Al-Siddiqui",
    )
    val lines58 = ThermalReceiptFormatter.formatCustomerReceiptLines(
      shopName = "Al-Noor Tailors",
      shopContact = "0300-1234567",
      orderDetail = detail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
    )
    assertTrue(lines58.all { it.length <= 32 })
    val joined = lines58.joinToString("\n")
    assertTrue(joined.contains("Muhammad Tariq Mahmood Qureshi"))
    assertTrue(joined.contains("Al-Siddiqui"))
  }

  @Test
  fun `urdu customer name shop name and notes render intact in preview and transliterate without question marks in bytes`() {
    val (baseDetail, meas) = sampleOrder(
      orderNumber = 1020,
      customerName = "محمد عاصم ملک",
    )
    val urduDetail = baseDetail.copy(
      order = baseDetail.order.copy(notes = "عید سے پہلے تیار کریں"),
    )
    val style = SnapshotJson.decodeStyle(urduDetail.order.styleSnapshotJson)

    val customerPreviewLines = ThermalReceiptFormatter.formatCustomerReceiptLines(
      shopName = "مدینہ ٹیلرز",
      shopContact = "0300-1234567",
      orderDetail = urduDetail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
    )
    val customerPreview = customerPreviewLines.joinToString("\n")
    assertTrue(customerPreview.contains("محمد عاصم ملک"))
    assertTrue(customerPreview.contains("مدینہ ٹیلرز"))
    assertTrue(customerPreview.contains("عید سے پہلے تیار کریں"))
    assertFalse("Preview must never replace Urdu characters with '?'", customerPreview.contains("?"))

    val karigarPreviewLines = ThermalReceiptFormatter.formatKarigarSlipLines(
      shopName = "مدینہ ٹیلرز",
      orderDetail = urduDetail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
    )
    val karigarPreview = karigarPreviewLines.joinToString("\n")
    assertTrue(karigarPreview.contains("محمد عاصم ملک"))
    assertTrue(karigarPreview.contains("مدینہ ٹیلرز"))
    assertFalse("Karigar preview must never replace Urdu characters with '?'", karigarPreview.contains("?"))

    val escPosBytes = ThermalReceiptFormatter.formatCustomerReceiptBytes(
      shopName = "مدینہ ٹیلرز",
      shopContact = "0300-1234567",
      orderDetail = urduDetail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
    )
    val asciiText = escPosBytes.toString(Charsets.US_ASCII)
    assertFalse("ESC/POS output must not emit '?' placeholders", asciiText.contains("?"))
    assertTrue(asciiText.contains("Customer:"))
    assertTrue(asciiText.contains("mhmd aasm mlk"))
  }

  @Test
  fun `urdu language preference generates full urdu customer receipt and compact paired karigar slip`() {
    val (baseDetail, meas) = sampleOrder(
      orderNumber = 1025,
      customerName = "محمد عاصم ملک",
    )
    val urduDetail = baseDetail.copy(
      order = baseDetail.order.copy(notes = "عید سے پہلے تیار کریں"),
    )
    val style = SnapshotJson.decodeStyle(urduDetail.order.styleSnapshotJson)

    val customerLines58 = ThermalReceiptFormatter.formatCustomerReceiptLines(
      shopName = "مدینہ ٹیلرز",
      shopContact = "0300-1234567",
      orderDetail = urduDetail,
      payments = emptyList(),
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
      language = AppLanguage.URDU,
    )
    assertTrue(customerLines58.all { it.length <= 32 })
    val customerText = customerLines58.joinToString("\n")
    assertTrue(customerText.contains("گاہک کی رسید"))
    assertTrue(customerText.contains("گاہک: محمد عاصم ملک"))
    assertTrue(customerLines58.any { it.contains("سوٹ:") && it.contains("حالت:") })
    assertTrue(customerLines58.any { it.contains("بل:") && it.contains("ادا:") })
    assertTrue(customerText.contains("شکریہ!"))
    assertFalse(customerText.contains("?"))

    val karigarLines58 = ThermalReceiptFormatter.formatKarigarSlipLines(
      shopName = "مدینہ ٹیلرز",
      orderDetail = urduDetail,
      measurement = meas,
      style = style,
      paperWidth = PaperWidth.MM_58,
      preserveUnicode = true,
      language = AppLanguage.URDU,
    )
    assertTrue(karigarLines58.all { it.length <= 32 })
    val karigarText = karigarLines58.joinToString("\n")
    assertTrue(karigarText.contains("کاریگر ورکشاپ پرچی"))
    assertTrue(karigarText.contains("ناپ (انچ)"))
    assertTrue(karigarText.contains("سلائی و ڈیزائن"))
    assertTrue(
      "Lambai and Tera must be on same line in Urdu karigar slip",
      karigarLines58.any { it.contains("لمبائی: 41.5") && it.contains("تیرا: 18.25") },
    )
    assertTrue(karigarLines58.any { it.contains("بازو: 24") && it.contains("چھاتی: 42") })
    assertTrue(karigarLines58.any { it.contains("شلوار: 39.5") && it.contains("پانچہ: 7.75") })
    assertTrue(karigarLines58.any { it.contains("گلا: بین") && it.contains("دامن: چورس") })
    assertFalse(karigarText.contains("?"))
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BluetoothPrintingUiRobolectricTest {

  private val recordingClient = RecordingThermalPrinterClient()
  private val authRule = AuthorizedShopTestRule(
    initialSignedIn = true,
    authorizeLocalDevice = true,
    initialOnline = false,
    thermalPrinterClientOverride = recordingClient,
  )
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `settings selects bluetooth printer, runs test print, and order detail previews karigar slip`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val orderId = runBlocking {
      val cust = app.container.customerRepository.createCustomer(
        name = "Salman Raza",
        rawPhone = "0300-4455667",
        deviceId = "phone-1",
      ).getOrThrow()
      app.container.orderRepository.createOrder(
        customerId = cust.customerId,
        suitCount = 1,
        deliveryDateMillis = System.currentTimeMillis() + 86_400_000L,
        totalAmountPkr = 3500L,
        initialAdvancePkr = 1000L,
        measurementSnapshot = MeasurementSnapshot(
          kameezLambai = 40.0,
          tera = 18.0,
          bazu = 24.0,
          chati = 41.0,
          kamar = 37.0,
          gheraDaman = 42.0,
          galaCollar = 16.0,
          cuff = 9.0,
          shalwarLambai = 39.0,
          paincha = 7.5,
          gheraAsan = 15.5,
        ),
        styleSnapshot = StyleSnapshot(
          neckStyle = NeckStyle.COLLAR,
          damanStyle = DamanStyle.GOL,
          pocketStyle = PocketStyle.TWO_SIDE,
          sleeveStyle = SleeveStyle.PLAIN,
          silaiStyle = SilaiStyle.SINGLE,
        ),
        deviceId = "phone-1",
      ).getOrThrow().orderId
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    // Select printer in Settings via drawer & run test print
    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_select_printer_button"))
    composeTestRule.onNodeWithTag("settings_select_printer_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("printer_picker_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("printer_item_00:11:22:33:44:55").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_configured_printer_text"))
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      runCatching {
        composeTestRule.onNodeWithTag("settings_configured_printer_text")
          .assertTextContains("RPP02N-58mm", substring = true)
      }.isSuccess
    }

    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      composeTestRule.onAllNodesWithText("Saved printer", substring = true)
        .fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag("settings_screen")
      .performScrollToNode(hasTestTag("settings_test_print_button"))
    composeTestRule.onNodeWithTag("settings_screen").performTouchInput {
      swipeUp(startY = centerY, endY = top + 40f)
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_test_print_button")
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      recordingClient.sentPayloads.isNotEmpty() ||
        composeTestRule.onAllNodesWithTag("settings_printer_error_text")
          .fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(
      "Unexpected printer error shown in UI",
      0,
      composeTestRule.onAllNodesWithTag("settings_printer_error_text").fetchSemanticsNodes().size,
    )
    assertEquals(1, recordingClient.sentPayloads.size)
    assertEquals("00:11:22:33:44:55", recordingClient.sentPayloads.first().first)

    // Navigate back to Dashboard -> Orders -> open order -> Preview Karigar Slip
    composeTestRule.onNodeWithTag("topbar_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("dashboard_tile_orders").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_card_$orderId").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_card_$orderId").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("order_detail_content").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_preview_karigar_button"))
    composeTestRule.onNodeWithTag("order_preview_karigar_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("slip_preview_text")
      .assertTextContains("KARIGAR WORKSHOP SLIP", substring = true)
  }

  @Test
  fun `order detail previews customer receipt and karigar slip with Urdu customer name without question marks`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val urduName = "محمد عاصم ملک"
    val orderId = runBlocking {
      val cust = app.container.customerRepository.createCustomer(
        name = urduName,
        rawPhone = "0300-9988776",
        deviceId = "phone-1",
      ).getOrThrow()
      app.container.orderRepository.createOrder(
        customerId = cust.customerId,
        suitCount = 1,
        deliveryDateMillis = System.currentTimeMillis() + 86_400_000L,
        totalAmountPkr = 4200L,
        initialAdvancePkr = 1200L,
        measurementSnapshot = MeasurementSnapshot(
          kameezLambai = 40.0,
          tera = 18.0,
          bazu = 24.0,
          chati = 41.0,
          kamar = 37.0,
          gheraDaman = 42.0,
          galaCollar = 16.0,
          cuff = 9.0,
          shalwarLambai = 39.0,
          paincha = 7.5,
          gheraAsan = 15.5,
        ),
        styleSnapshot = StyleSnapshot(
          neckStyle = NeckStyle.BAN,
          damanStyle = DamanStyle.GOL,
          pocketStyle = PocketStyle.TWO_SIDE,
          sleeveStyle = SleeveStyle.PLAIN,
          silaiStyle = SilaiStyle.SINGLE,
        ),
        notes = "عید آرڈر",
        deviceId = "phone-1",
      ).getOrThrow().orderId
    }

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
      .performScrollToNode(hasTestTag("order_preview_customer_button"))
    composeTestRule.onNodeWithTag("order_preview_customer_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_text")
      .assertTextContains(urduName, substring = true)
    assertTrue(
      composeTestRule.onAllNodesWithText("???", substring = true).fetchSemanticsNodes().isEmpty(),
    )

    composeTestRule.onNodeWithText("Close").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_preview_karigar_button"))
    composeTestRule.onNodeWithTag("order_preview_karigar_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_text")
      .assertTextContains(urduName, substring = true)
      .assertTextContains("KARIGAR WORKSHOP SLIP", substring = true)
    assertTrue(
      composeTestRule.onAllNodesWithText("???", substring = true).fetchSemanticsNodes().isEmpty(),
    )
  }

  @Test
  fun `urdu language preference previews slips in Urdu with paired measurements and prints Urdu raster bytes`() {
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val urduName = "محمد عاصم ملک"
    val orderId = runBlocking {
      app.container.shopPreferencesRepository.updateLanguage(AppLanguage.URDU)
      val cust = app.container.customerRepository.createCustomer(
        name = urduName,
        rawPhone = "0300-7766554",
        deviceId = "phone-1",
      ).getOrThrow()
      app.container.orderRepository.createOrder(
        customerId = cust.customerId,
        suitCount = 2,
        deliveryDateMillis = System.currentTimeMillis() + 86_400_000L,
        totalAmountPkr = 6000L,
        initialAdvancePkr = 2000L,
        measurementSnapshot = MeasurementSnapshot(
          kameezLambai = 40.0,
          tera = 18.0,
          bazu = 24.0,
          chati = 41.0,
          kamar = 37.0,
          gheraDaman = 42.0,
          galaCollar = 16.0,
          cuff = 9.0,
          shalwarLambai = 39.0,
          paincha = 7.5,
          gheraAsan = 15.5,
        ),
        styleSnapshot = StyleSnapshot(
          neckStyle = NeckStyle.BAN,
          damanStyle = DamanStyle.GOL,
          pocketStyle = PocketStyle.TWO_SIDE,
          sleeveStyle = SleeveStyle.PLAIN,
          silaiStyle = SilaiStyle.DOUBLE,
        ),
        deviceId = "phone-1",
      ).getOrThrow().orderId
    }

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

    // Preview customer receipt in Urdu
    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_preview_customer_button"))
    composeTestRule.onNodeWithTag("order_preview_customer_button")
      .assertTextContains("گاہک رسید دیکھیں", substring = true)
      .performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_text")
      .assertTextContains("گاہک کی رسید", substring = true)
      .assertTextContains(urduName, substring = true)
      .assertTextContains("بقایا: 4,000 روپے", substring = true)

    composeTestRule.onNodeWithTag("slip_preview_close_button")
      .assertTextContains("بند کریں", substring = true)
      .performClick()
    composeTestRule.waitForIdle()

    // Preview karigar slip in Urdu & verify Lambai + Tera appear on the same line
    composeTestRule.onNodeWithTag("order_detail_content")
      .performScrollToNode(hasTestTag("order_preview_karigar_button"))
    composeTestRule.onNodeWithTag("order_preview_karigar_button").performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("slip_preview_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("slip_preview_text")
      .assertTextContains("کاریگر ورکشاپ پرچی", substring = true)
      .assertTextContains("لمبائی: 40  |  تیرا: 18", substring = true)
      .assertTextContains("گلا: بین  |  دامن: گول", substring = true)

    // Verify Urdu ESC/POS raster bytes use GS v 0 (1D 76 30 00) and INIT/FEED_AND_CUT
    val previewedOrderDetail = runBlocking {
      app.container.orderRepository.observeOrderDetail(orderId).first()!!
    }
    val sampleBytes = ThermalReceiptFormatter.formatKarigarSlipBytes(
      shopName = "درزی لیجر",
      orderDetail = previewedOrderDetail,
      measurement = MeasurementSnapshot(
        kameezLambai = 40.0,
        tera = 18.0,
        bazu = 24.0,
        chati = 41.0,
        kamar = 37.0,
        gheraDaman = 42.0,
        galaCollar = 16.0,
        cuff = 9.0,
        shalwarLambai = 39.0,
        paincha = 7.5,
        gheraAsan = 15.5,
      ),
      style = StyleSnapshot(
        neckStyle = NeckStyle.BAN,
        damanStyle = DamanStyle.GOL,
        pocketStyle = PocketStyle.TWO_SIDE,
        sleeveStyle = SleeveStyle.PLAIN,
        silaiStyle = SilaiStyle.DOUBLE,
      ),
      paperWidth = PaperWidth.MM_58,
      language = AppLanguage.URDU,
    )
    assertEquals(EscPosCommands.INIT[0], sampleBytes[0])
    assertEquals(EscPosCommands.INIT[1], sampleBytes[1])
    // Starts with INIT followed by GS v 0 raster command (0x1D, 0x76, 0x30, 0x00)
    assertEquals(0x1D.toByte(), sampleBytes[2])
    assertEquals(0x76.toByte(), sampleBytes[3])
    assertEquals(0x30.toByte(), sampleBytes[4])
    assertEquals(0x00.toByte(), sampleBytes[5])
    val tail = sampleBytes.copyOfRange(sampleBytes.size - EscPosCommands.FEED_AND_CUT.size, sampleBytes.size)
    assertTrue(tail.contentEquals(EscPosCommands.FEED_AND_CUT))
  }
}
