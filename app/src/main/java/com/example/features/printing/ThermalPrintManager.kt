package com.example.features.printing

import com.example.core.model.ShopUserRole
import com.example.core.model.SnapshotJson
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.ShopPreferences
import com.example.core.preferences.ShopPreferencesRepository
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.entities.PaymentEntity
import com.example.data.local.entities.PrinterConfigEntity
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.ShopRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ThermalPrintManager(
  private val printerClient: ThermalPrinterClient,
  private val deviceSyncRepository: DeviceSyncRepository,
  private val shopRepository: ShopRepository,
  private val shopPreferencesRepository: ShopPreferencesRepository,
) {

  fun observeDefaultPrinter(): Flow<PrinterConfigEntity?> =
    deviceSyncRepository.observeDefaultPrinter()

  private suspend fun safePreferences(): ShopPreferences {
    val basePrefs = runCatching { shopPreferencesRepository.preferencesFlow.first() }
      .getOrElse { e ->
        if (e is CancellationException) throw e
        ShopPreferences()
      }
    val activeRole = runCatching { deviceSyncRepository.observeDeviceConfig().first()?.userRole }
      .getOrNull()
    return if (activeRole == ShopUserRole.STAFF.name) {
      basePrefs.copy(language = AppLanguage.URDU)
    } else {
      basePrefs
    }
  }

  private fun textFor(language: AppLanguage, english: String, urdu: String): String =
    if (language == AppLanguage.URDU) urdu else english

  suspend fun buildPreviewText(
    orderDetail: OrderWithCustomer,
    payments: List<PaymentEntity>,
    slipType: PrintSlipType,
  ): String {
    val prefs = safePreferences()
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val shopContact = shop.contactNumber.takeIf { it.isNotBlank() } ?: prefs.shopContact
    val paperWidth = PaperWidth.fromMm(prefs.paperWidthMm)

    return when (slipType) {
      PrintSlipType.CUSTOMER_RECEIPT ->
        ThermalReceiptFormatter.formatCustomerReceiptLines(
          shopName = shopName,
          shopContact = shopContact,
          orderDetail = orderDetail,
          payments = payments,
          paperWidth = paperWidth,
          preserveUnicode = true,
          language = prefs.language,
        ).joinToString("\n")

      PrintSlipType.KARIGAR_SLIP -> {
        val measurement = runCatching {
          SnapshotJson.decodeMeasurement(orderDetail.order.measurementSnapshotJson)
        }.getOrElse {
          return textFor(
            prefs.language,
            "Order measurement snapshot is unavailable.",
            "آرڈر کے ناپ کا ریکارڈ دستیاب نہیں ہے۔",
          )
        }
        val style = runCatching {
          SnapshotJson.decodeStyle(orderDetail.order.styleSnapshotJson)
        }.getOrElse {
          return textFor(
            prefs.language,
            "Order style snapshot is unavailable.",
            "آرڈر کے ڈیزائن کا ریکارڈ دستیاب نہیں ہے۔",
          )
        }
        ThermalReceiptFormatter.formatKarigarSlipLines(
          shopName = shopName,
          orderDetail = orderDetail,
          measurement = measurement,
          style = style,
          paperWidth = paperWidth,
          preserveUnicode = true,
          language = prefs.language,
        ).joinToString("\n")
      }

      PrintSlipType.MEASUREMENT_SLIP -> {
        val measurement = runCatching {
          SnapshotJson.decodeMeasurement(orderDetail.order.measurementSnapshotJson)
        }.getOrElse {
          return textFor(
            prefs.language,
            "Order measurement snapshot is unavailable.",
            "آرڈر کے ناپ کا ریکارڈ دستیاب نہیں ہے۔",
          )
        }
        val style = runCatching {
          SnapshotJson.decodeStyle(orderDetail.order.styleSnapshotJson)
        }.getOrElse {
          return textFor(
            prefs.language,
            "Order style snapshot is unavailable.",
            "آرڈر کے ڈیزائن کا ریکارڈ دستیاب نہیں ہے۔",
          )
        }
        ThermalReceiptFormatter.formatMeasurementSlipLines(
          shopName = shopName,
          customerName = orderDetail.customerName,
          customerPhone = orderDetail.customerPhone,
          customerDisplayId = null,
          measurementLabel = "Standard",
          measurement = measurement,
          style = style,
          recordedAtMillis = orderDetail.order.createdAt,
          paperWidth = paperWidth,
          preserveUnicode = true,
          language = prefs.language,
        ).joinToString("\n")
      }

      PrintSlipType.TEST_PAGE -> {
        val printerName = deviceSyncRepository.observeDefaultPrinter().first()?.printerName
          ?: textFor(prefs.language, "Thermal Printer", "تھرمل پرنٹر")
        ThermalReceiptFormatter.formatTestPageLines(
          shopName = shopName,
          printerName = printerName,
          paperWidth = paperWidth,
          preserveUnicode = true,
          language = prefs.language,
        ).joinToString("\n")
      }
    }
  }

  suspend fun buildMeasurementPreviewText(
    customer: CustomerEntity,
    customerDisplayId: String?,
    measurement: MeasurementEntity,
  ): String {
    val prefs = safePreferences()
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val paperWidth = PaperWidth.fromMm(prefs.paperWidthMm)

    return ThermalReceiptFormatter.formatMeasurementSlipLines(
      shopName = shopName,
      customerName = customer.name,
      customerPhone = customer.phone,
      customerDisplayId = customerDisplayId,
      measurementLabel = measurement.label,
      measurement = measurement.toMeasurementSnapshot(),
      style = measurement.toStyleSnapshot(),
      recordedAtMillis = measurement.createdAt,
      paperWidth = paperWidth,
      preserveUnicode = true,
      language = prefs.language,
    ).joinToString("\n")
  }

  suspend fun printCustomerReceipt(
    orderDetail: OrderWithCustomer,
    payments: List<PaymentEntity>,
  ): Result<String> {
    val prefs = safePreferences()
    val printer = deviceSyncRepository.observeDefaultPrinter().first()
      ?: return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Select a Bluetooth thermal printer in Settings first.",
            "پہلے ترتیبات میں بلوٹوتھ تھرمل پرنٹر منتخب کریں۔",
          ),
        ),
      )
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val shopContact = shop.contactNumber.takeIf { it.isNotBlank() } ?: prefs.shopContact
    val width = PaperWidth.fromMm(prefs.paperWidthMm)

    val bytes = ThermalReceiptFormatter.formatCustomerReceiptBytes(
      shopName = shopName,
      shopContact = shopContact,
      orderDetail = orderDetail,
      payments = payments,
      paperWidth = width,
      language = prefs.language,
    )
    return printerClient.printBytes(printer.printerAddress, bytes).map {
      textFor(
        prefs.language,
        "Customer receipt sent to ${printer.printerName}",
        "گاہک کی رسید ${printer.printerName} پر بھیج دی گئی",
      )
    }
  }

  suspend fun printKarigarSlip(orderDetail: OrderWithCustomer): Result<String> {
    val prefs = safePreferences()
    val printer = deviceSyncRepository.observeDefaultPrinter().first()
      ?: return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Select a Bluetooth thermal printer in Settings first.",
            "پہلے ترتیبات میں بلوٹوتھ تھرمل پرنٹر منتخب کریں۔",
          ),
        ),
      )
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val width = PaperWidth.fromMm(prefs.paperWidthMm)

    val measurement = runCatching {
      SnapshotJson.decodeMeasurement(orderDetail.order.measurementSnapshotJson)
    }.getOrElse {
      return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Order measurement snapshot is corrupted or missing.",
            "آرڈر کے ناپ کا ریکارڈ خراب یا غائب ہے۔",
          ),
        ),
      )
    }
    val style = runCatching {
      SnapshotJson.decodeStyle(orderDetail.order.styleSnapshotJson)
    }.getOrElse {
      return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Order style snapshot is corrupted or missing.",
            "آرڈر کے ڈیزائن کا ریکارڈ خراب یا غائب ہے۔",
          ),
        ),
      )
    }

    val bytes = ThermalReceiptFormatter.formatKarigarSlipBytes(
      shopName = shopName,
      orderDetail = orderDetail,
      measurement = measurement,
      style = style,
      paperWidth = width,
      language = prefs.language,
    )
    return printerClient.printBytes(printer.printerAddress, bytes).map {
      textFor(
        prefs.language,
        "Karigar workshop slip sent to ${printer.printerName}",
        "کاریگر ورکشاپ پرچی ${printer.printerName} پر بھیج دی گئی",
      )
    }
  }

  suspend fun printMeasurementSlip(
    customer: CustomerEntity,
    customerDisplayId: String?,
    measurement: MeasurementEntity,
  ): Result<String> {
    val prefs = safePreferences()
    val printer = deviceSyncRepository.observeDefaultPrinter().first()
      ?: return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Select a Bluetooth thermal printer in Settings first.",
            "پہلے ترتیبات میں بلوٹوتھ تھرمل پرنٹر منتخب کریں۔",
          ),
        ),
      )
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val width = PaperWidth.fromMm(prefs.paperWidthMm)

    val bytes = ThermalReceiptFormatter.formatMeasurementSlipBytes(
      shopName = shopName,
      customerName = customer.name,
      customerPhone = customer.phone,
      customerDisplayId = customerDisplayId,
      measurementLabel = measurement.label,
      measurement = measurement.toMeasurementSnapshot(),
      style = measurement.toStyleSnapshot(),
      recordedAtMillis = measurement.createdAt,
      paperWidth = width,
      language = prefs.language,
    )
    return printerClient.printBytes(printer.printerAddress, bytes).map {
      textFor(
        prefs.language,
        "Measurement slip sent to ${printer.printerName}",
        "ناپ کی پرچی ${printer.printerName} پر بھیج دی گئی",
      )
    }
  }

  suspend fun printTestPage(): Result<String> {
    val prefs = safePreferences()
    val printer = deviceSyncRepository.observeDefaultPrinter().first()
      ?: return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Select a Bluetooth thermal printer first.",
            "پہلے بلوٹوتھ تھرمل پرنٹر منتخب کریں۔",
          ),
        ),
      )
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val width = PaperWidth.fromMm(prefs.paperWidthMm)

    val bytes = ThermalReceiptFormatter.formatTestPageBytes(
      shopName = shopName,
      printerName = printer.printerName,
      paperWidth = width,
      language = prefs.language,
    )
    return printerClient.printBytes(printer.printerAddress, bytes).map {
      textFor(
        prefs.language,
        "Test slip sent to ${printer.printerName}",
        "ٹیسٹ پرچی ${printer.printerName} پر بھیج دی گئی",
      )
    }
  }
}
