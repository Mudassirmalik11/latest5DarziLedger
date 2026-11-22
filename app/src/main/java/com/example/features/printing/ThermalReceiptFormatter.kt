package com.example.features.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.OrderStatus
import com.example.core.model.PaymentMethod
import com.example.core.model.PaymentStatus
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import com.example.core.preferences.AppLanguage
import com.example.core.ui.DateFormatUtils
import com.example.core.ui.PkrFormatUtils
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.entities.PaymentEntity
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

enum class PaperWidth(val widthMm: Int, val columns: Int) {
  MM_58(widthMm = 58, columns = 32),
  MM_80(widthMm = 80, columns = 48);

  companion object {
    fun fromMm(widthMm: Int): PaperWidth = if (widthMm == 80) MM_80 else MM_58
  }
}

enum class PrintSlipType {
  CUSTOMER_RECEIPT,
  KARIGAR_SLIP,
  MEASUREMENT_SLIP,
  TEST_PAGE,
}

object EscPosCommands {
  val INIT: ByteArray = byteArrayOf(0x1B, 0x40)
  val ALIGN_LEFT: ByteArray = byteArrayOf(0x1B, 0x61, 0x00)
  val ALIGN_CENTER: ByteArray = byteArrayOf(0x1B, 0x61, 0x01)
  val ALIGN_RIGHT: ByteArray = byteArrayOf(0x1B, 0x61, 0x02)
  val BOLD_ON: ByteArray = byteArrayOf(0x1B, 0x45, 0x01)
  val BOLD_OFF: ByteArray = byteArrayOf(0x1B, 0x45, 0x00)
  val TEXT_NORMAL: ByteArray = byteArrayOf(0x1D, 0x21, 0x00)
  val TEXT_DOUBLE_HEIGHT: ByteArray = byteArrayOf(0x1D, 0x21, 0x01)
  val TEXT_DOUBLE_SIZE: ByteArray = byteArrayOf(0x1D, 0x21, 0x11)
  val FEED_AND_CUT: ByteArray = byteArrayOf(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x42, 0x00)
}

object ThermalReceiptFormatter {

  private val commaNumberFormat: NumberFormat
    get() = NumberFormat.getIntegerInstance(Locale.US)

  fun wrapText(
    text: String,
    maxWidth: Int,
    preserveUnicode: Boolean = true,
  ): List<String> {
    if (maxWidth <= 0) return emptyList()
    val clean = sanitize(text, preserveUnicode).trim()
    if (clean.isEmpty()) return emptyList()

    val lines = mutableListOf<String>()
    for (paragraph in clean.split('\n')) {
      val trimmedPara = paragraph.trim()
      if (trimmedPara.isEmpty()) {
        lines.add("")
        continue
      }
      var current = StringBuilder()
      for (word in trimmedPara.split(Regex("\\s+"))) {
        if (word.length > maxWidth) {
          if (current.isNotEmpty()) {
            lines.add(current.toString())
            current = StringBuilder()
          }
          var idx = 0
          while (idx < word.length) {
            val end = (idx + maxWidth).coerceAtMost(word.length)
            lines.add(word.substring(idx, end))
            idx = end
          }
        } else if (current.isEmpty()) {
          current.append(word)
        } else if (current.length + 1 + word.length <= maxWidth) {
          current.append(' ').append(word)
        } else {
          lines.add(current.toString())
          current = StringBuilder(word)
        }
      }
      if (current.isNotEmpty()) {
        lines.add(current.toString())
      }
    }
    return lines
  }

  fun formatKeyValue(
    left: String,
    right: String,
    columns: Int,
    preserveUnicode: Boolean = true,
  ): List<String> {
    val cleanLeft = sanitizeSingleLine(left, preserveUnicode)
    val cleanRight = sanitizeSingleLine(right, preserveUnicode).ifBlank { "-" }
    val minGap = 1
    if (cleanLeft.length + minGap + cleanRight.length <= columns) {
      val spaces = (columns - cleanLeft.length - cleanRight.length).coerceAtLeast(1)
      return listOf(cleanLeft + " ".repeat(spaces) + cleanRight)
    }
    val wrappedLeft = wrapText(cleanLeft, columns, preserveUnicode).toMutableList()
    if (wrappedLeft.isEmpty()) {
      val wrappedRight = wrapText(cleanRight, columns, preserveUnicode)
      return if (wrappedRight.size == 1) {
        listOf(wrappedRight.first().padStart(columns))
      } else {
        wrappedRight
      }
    }
    val lastLeft = wrappedLeft.last()
    return if (lastLeft.length + minGap + cleanRight.length <= columns) {
      val spaces = columns - lastLeft.length - cleanRight.length
      wrappedLeft[wrappedLeft.lastIndex] = lastLeft + " ".repeat(spaces) + cleanRight
      wrappedLeft
    } else {
      val wrappedRight = wrapText(cleanRight, columns, preserveUnicode)
      if (wrappedRight.size == 1) {
        wrappedLeft.add(wrappedRight.first().padStart(columns))
      } else {
        wrappedLeft.addAll(wrappedRight)
      }
      wrappedLeft
    }
  }

  fun formatInlinePair(
    leftEntry: String,
    rightEntry: String,
    columns: Int,
    preserveUnicode: Boolean = true,
    alignMonospaceGrid: Boolean = true,
  ): List<String> {
    val left = sanitizeSingleLine(leftEntry, preserveUnicode)
    val right = sanitizeSingleLine(rightEntry, preserveUnicode)
    if (left.isEmpty() && right.isEmpty()) return emptyList()
    if (right.isEmpty()) return wrapText(left, columns, preserveUnicode)
    if (left.isEmpty()) return wrapText(right, columns, preserveUnicode)

    val halfCol = (columns - 2) / 2
    if (alignMonospaceGrid && left.length <= halfCol && right.length <= columns - halfCol - 2) {
      return listOf("${left.padEnd(halfCol)}| $right")
    }
    if (!alignMonospaceGrid && left.length + 5 + right.length <= columns) {
      return listOf("$left  |  $right")
    }
    if (left.length + 3 + right.length <= columns) {
      return listOf("$left | $right")
    }
    return buildList {
      addAll(wrapText(left, columns, preserveUnicode))
      addAll(wrapText(right, columns, preserveUnicode))
    }
  }

  private fun formatPairedEntries(
    entries: List<String>,
    columns: Int,
    preserveUnicode: Boolean,
    alignMonospaceGrid: Boolean,
  ): List<String> {
    val lines = mutableListOf<String>()
    var i = 0
    while (i < entries.size) {
      val left = entries[i]
      if (i + 1 < entries.size) {
        lines.addAll(
          formatInlinePair(
            leftEntry = left,
            rightEntry = entries[i + 1],
            columns = columns,
            preserveUnicode = preserveUnicode,
            alignMonospaceGrid = alignMonospaceGrid,
          ),
        )
      } else {
        lines.addAll(wrapText(left, columns, preserveUnicode))
      }
      i += 2
    }
    return lines
  }

  fun divider(columns: Int, ch: Char = '-'): String = ch.toString().repeat(columns)

  fun centerText(
    text: String,
    columns: Int,
    preserveUnicode: Boolean = true,
  ): List<String> =
    wrapText(text, columns, preserveUnicode).map { line ->
      val pad = ((columns - line.length) / 2).coerceAtLeast(0)
      " ".repeat(pad) + line
    }

  fun formatCustomerReceiptLines(
    shopName: String,
    shopContact: String,
    orderDetail: OrderWithCustomer,
    payments: List<PaymentEntity>,
    paperWidth: PaperWidth,
    preserveUnicode: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): List<String> {
    val cols = paperWidth.columns
    val order = orderDetail.order
    val isUrdu = language == AppLanguage.URDU && preserveUnicode
    val alignGrid = !isUrdu
    val customerName = orderDetail.customerName.ifBlank { if (isUrdu) "گاہک" else "Customer" }
    val customerPhone = orderDetail.customerPhone.ifBlank { "-" }
    val lines = mutableListOf<String>()

    val defaultShop = if (isUrdu) "درزی لیجر" else "Darzi Ledger"
    val resolvedShop = shopName.ifBlank { defaultShop }.let { name ->
      if (isUrdu && name == "Darzi Ledger") "درزی لیجر" else name
    }
    lines.addAll(centerText(resolvedShop, cols, preserveUnicode))
    if (shopContact.isNotBlank()) {
      val telPrefix = if (isUrdu) "فون:" else "Tel:"
      lines.addAll(centerText("$telPrefix $shopContact", cols, preserveUnicode))
    }
    lines.add(divider(cols, '='))

    val orderNumberLabel = if (isUrdu) {
      order.orderNumber?.let { "آرڈر #$it" } ?: "آرڈر: نمبر زیر التوا"
    } else {
      order.orderNumber?.let { "ORDER #$it" } ?: "ORDER: NUMBER PENDING"
    }
    lines.addAll(centerText(orderNumberLabel, cols, preserveUnicode))
    lines.addAll(centerText(if (isUrdu) "گاہک کی رسید" else "CUSTOMER RECEIPT", cols, preserveUnicode))
    lines.add(divider(cols))

    if (isUrdu) {
      lines.addAll(wrapText("گاہک: $customerName", cols, preserveUnicode))
      lines.addAll(wrapText("موبائل: $customerPhone", cols, preserveUnicode))
      lines.addAll(
        formatInlinePair(
          "سوٹ: ${order.suitCount}",
          "حالت: ${orderStatusLabel(order.orderStatus, true)}",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )
      val urduLocale = Locale.forLanguageTag("ur")
      val createdText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.createdAt, urduLocale)
      } else {
        DateFormatUtils.formatShortDate(order.createdAt, urduLocale)
      }
      val dueText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.deliveryDate, urduLocale)
      } else {
        DateFormatUtils.formatShortDate(order.deliveryDate, urduLocale)
      }
      lines.addAll(
        formatInlinePair(
          "بکنگ: $createdText",
          "واپسی: $dueText",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )

      lines.add(divider(cols))
      lines.addAll(
        formatInlinePair(
          "بل: ${formatCompactUrduPkr(order.totalAmountPkr)}",
          "ادا: ${formatUrduPkr(order.paidAmountPkr)}",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )
      lines.addAll(
        formatInlinePair(
          "بقایا: ${formatUrduPkr(order.remainingAmountPkr)}",
          paymentStatusLabel(order.paymentStatus, true),
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )

      if (payments.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("ادائیگیاں:")
        payments.forEach { p ->
          val dateStr = DateFormatUtils.formatShortDate(p.createdAt, urduLocale)
          val label = "$dateStr (${paymentMethodLabel(p.paymentMethod, true)})"
          lines.addAll(formatInlinePair(label, formatUrduPkr(p.amountPkr), cols, preserveUnicode, alignGrid))
        }
      }

      if (order.notes.isNotBlank()) {
        lines.add(divider(cols))
        lines.addAll(wrapText("نوٹ: ${order.notes}", cols, preserveUnicode))
      }

      lines.add(divider(cols, '='))
      lines.addAll(centerText("شکریہ! یہ پرچی ساتھ لائیں۔", cols, preserveUnicode))
    } else {
      lines.addAll(formatKeyValue("Customer:", customerName, cols, preserveUnicode))
      lines.addAll(formatKeyValue("Mobile:", customerPhone, cols, preserveUnicode))
      lines.addAll(
        formatInlinePair(
          "Suits: ${order.suitCount}",
          "Status: ${orderStatusLabel(order.orderStatus, false)}",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )
      val createdText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.createdAt, Locale.ENGLISH)
      } else {
        DateFormatUtils.formatShortDate(order.createdAt, Locale.ENGLISH)
      }
      val dueText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.deliveryDate, Locale.ENGLISH)
      } else {
        DateFormatUtils.formatShortDate(order.deliveryDate, Locale.ENGLISH)
      }
      lines.addAll(
        formatInlinePair(
          "In: $createdText",
          "Due: $dueText",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )

      lines.add(divider(cols))
      lines.addAll(
        formatInlinePair(
          "Total: ${PkrFormatUtils.formatPkr(order.totalAmountPkr)}",
          "Paid: ${PkrFormatUtils.formatPkr(order.paidAmountPkr)}",
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )
      lines.addAll(
        formatInlinePair(
          "REMAINING: ${PkrFormatUtils.formatPkr(order.remainingAmountPkr)}",
          paymentStatusLabel(order.paymentStatus, false),
          cols,
          preserveUnicode,
          alignGrid,
        ),
      )

      if (payments.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("Payments:")
        payments.forEach { p ->
          val dateStr = if (paperWidth == PaperWidth.MM_80) {
            DateFormatUtils.formatDate(p.createdAt, Locale.ENGLISH)
          } else {
            DateFormatUtils.formatShortDate(p.createdAt, Locale.ENGLISH)
          }
          val label = "$dateStr (${paymentMethodLabel(p.paymentMethod, false)})"
          lines.addAll(formatKeyValue(label, PkrFormatUtils.formatPkr(p.amountPkr), cols, preserveUnicode))
        }
      }

      if (order.notes.isNotBlank()) {
        lines.add(divider(cols))
        lines.addAll(wrapText("Note: ${order.notes}", cols, preserveUnicode))
      }

      lines.add(divider(cols, '='))
      lines.addAll(centerText("Shukriya! Please bring this slip.", cols, preserveUnicode))
    }
    return lines
  }

  fun formatKarigarSlipLines(
    shopName: String,
    orderDetail: OrderWithCustomer,
    measurement: MeasurementSnapshot,
    style: StyleSnapshot,
    paperWidth: PaperWidth,
    preserveUnicode: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): List<String> {
    val cols = paperWidth.columns
    val order = orderDetail.order
    val isUrdu = language == AppLanguage.URDU && preserveUnicode
    val alignGrid = !isUrdu
    val customerName = orderDetail.customerName.ifBlank { if (isUrdu) "گاہک" else "Customer" }
    val lines = mutableListOf<String>()

    val title = if (isUrdu) "کاریگر ورکشاپ پرچی" else "KARIGAR WORKSHOP SLIP"
    val defaultShop = if (isUrdu) "درزی لیجر" else "Darzi Ledger"
    val resolvedShop = shopName.ifBlank { defaultShop }.let { name ->
      if (isUrdu && name == "Darzi Ledger") "درزی لیجر" else name
    }
    val orderNumberLabel = if (isUrdu) {
      order.orderNumber?.let { "آرڈر #$it" } ?: "آرڈر: نمبر زیر التوا"
    } else {
      order.orderNumber?.let { "ORDER #$it" } ?: "ORDER: NUMBER PENDING"
    }

    lines.addAll(centerText(title, cols, preserveUnicode))
    lines.addAll(centerText(resolvedShop, cols, preserveUnicode))
    lines.add(divider(cols, '='))
    lines.addAll(centerText(orderNumberLabel, cols, preserveUnicode))

    if (isUrdu) {
      lines.addAll(wrapText("گاہک: $customerName", cols, preserveUnicode))
      val dueText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.deliveryDate, Locale.forLanguageTag("ur"))
      } else {
        DateFormatUtils.formatShortDate(order.deliveryDate, Locale.forLanguageTag("ur"))
      }
      lines.addAll(formatInlinePair("سوٹ: ${order.suitCount}", "واپسی: $dueText", cols, preserveUnicode, alignGrid))

      lines.add(divider(cols))
      lines.add("ناپ (انچ)")
      val measurementEntries = listOf(
        "لمبائی: ${formatUrduInches(measurement.kameezLambai)}",
        "تیرا: ${formatUrduInches(measurement.tera)}",
        "بازو: ${formatUrduInches(measurement.bazu)}",
        "چھاتی: ${formatUrduInches(measurement.chati)}",
        "کمر: ${formatUrduInches(measurement.kamar)}",
        "گھیرا: ${formatUrduInches(measurement.gheraDaman)}",
        "گلا: ${formatUrduInches(measurement.galaCollar)}",
        "کف: ${formatUrduInches(measurement.cuff)}",
        "شلوار: ${formatUrduInches(measurement.shalwarLambai)}",
        "پانچہ: ${formatUrduInches(measurement.paincha)}",
        "آسن: ${formatUrduInches(measurement.gheraAsan)}",
      )
      lines.addAll(formatPairedEntries(measurementEntries, cols, preserveUnicode, alignGrid))

      lines.add(divider(cols))
      lines.add("سلائی و ڈیزائن")
      val styleEntries = listOf(
        "گلا: ${neckStyleLabel(style.neckStyle, true)}",
        "دامن: ${damanStyleLabel(style.damanStyle, true)}",
        "جیب: ${pocketStylesLabel(style.effectivePocketStyles(), true)}",
        "آستین: ${sleeveStyleLabel(style.sleeveStyle, true)}",
        "سلائی: ${silaiStyleLabel(style.silaiStyle, true)}",
      )
      lines.addAll(formatPairedEntries(styleEntries, cols, preserveUnicode, alignGrid))

      val combinedNotes = buildList {
        if (measurement.notes.isNotBlank()) add("فٹنگ: ${measurement.notes}")
        if (order.notes.isNotBlank()) add("آرڈر: ${order.notes}")
      }
      if (combinedNotes.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("کاریگر نوٹس")
        combinedNotes.forEach { note ->
          lines.addAll(wrapText(note, cols, preserveUnicode))
        }
      }
    } else {
      lines.addAll(formatKeyValue("Customer:", customerName, cols, preserveUnicode))
      val dueText = if (paperWidth == PaperWidth.MM_80) {
        DateFormatUtils.formatDate(order.deliveryDate, Locale.ENGLISH)
      } else {
        DateFormatUtils.formatShortDate(order.deliveryDate, Locale.ENGLISH)
      }
      lines.addAll(formatInlinePair("Suits: ${order.suitCount}", "Due: $dueText", cols, preserveUnicode, alignGrid))

      lines.add(divider(cols))
      lines.add("MEASUREMENTS (Inches)")
      val measurementEntries = listOf(
        "Lambai: ${DateFormatUtils.formatInches(measurement.kameezLambai)}",
        "Tera: ${DateFormatUtils.formatInches(measurement.tera)}",
        "Bazu: ${DateFormatUtils.formatInches(measurement.bazu)}",
        "Chati: ${DateFormatUtils.formatInches(measurement.chati)}",
        "Kamar: ${DateFormatUtils.formatInches(measurement.kamar)}",
        "Ghera: ${DateFormatUtils.formatInches(measurement.gheraDaman)}",
        "Gala: ${DateFormatUtils.formatInches(measurement.galaCollar)}",
        "Cuff: ${DateFormatUtils.formatInches(measurement.cuff)}",
        "Shalwar: ${DateFormatUtils.formatInches(measurement.shalwarLambai)}",
        "Paincha: ${DateFormatUtils.formatInches(measurement.paincha)}",
        "Asan: ${DateFormatUtils.formatInches(measurement.gheraAsan)}",
      )
      lines.addAll(formatPairedEntries(measurementEntries, cols, preserveUnicode, alignGrid))

      lines.add(divider(cols))
      lines.add("STITCHING STYLES")
      val useCompact = paperWidth == PaperWidth.MM_58
      val styleEntries = listOf(
        "Neck: ${neckStyleLabel(style.neckStyle, false)}",
        "Daman: ${damanStyleLabel(style.damanStyle, false, useCompact)}",
        "${if (useCompact) "Pkts" else "Pockets"}: ${pocketStylesLabel(style.effectivePocketStyles(), false, useCompact)}",
        "${if (useCompact) "Slv" else "Sleeves"}: ${sleeveStyleLabel(style.sleeveStyle, false, useCompact)}",
        "Silai: ${silaiStyleLabel(style.silaiStyle, false, useCompact)}",
      )
      lines.addAll(formatPairedEntries(styleEntries, cols, preserveUnicode, alignGrid))

      val combinedNotes = buildList {
        if (measurement.notes.isNotBlank()) add("Fit: ${measurement.notes}")
        if (order.notes.isNotBlank()) add("Order: ${order.notes}")
      }
      if (combinedNotes.isNotEmpty()) {
        lines.add(divider(cols))
        lines.add("WORKSHOP NOTES")
        combinedNotes.forEach { note ->
          lines.addAll(wrapText(note, cols, preserveUnicode))
        }
      }
    }

    lines.add(divider(cols, '='))
    return lines
  }

  fun formatMeasurementSlipLines(
    shopName: String,
    customerName: String,
    customerPhone: String,
    customerDisplayId: String?,
    measurementLabel: String,
    measurement: MeasurementSnapshot,
    style: StyleSnapshot,
    recordedAtMillis: Long,
    paperWidth: PaperWidth,
    preserveUnicode: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): List<String> {
    val cols = paperWidth.columns
    val isUrdu = language == AppLanguage.URDU && preserveUnicode
    val alignGrid = !isUrdu
    val resolvedName = customerName.ifBlank { if (isUrdu) "گاہک" else "Customer" }
    val resolvedLabel = if (measurementLabel.isBlank() || measurementLabel.equals("Standard", ignoreCase = true)) {
      if (isUrdu) "مردانہ شلوار قمیض" else "Male Shalwar Kameez"
    } else {
      measurementLabel
    }
    val dateLocale = if (isUrdu) Locale.forLanguageTag("ur") else Locale.ENGLISH
    val dateText = if (paperWidth == PaperWidth.MM_80) {
      DateFormatUtils.formatDate(recordedAtMillis, dateLocale)
    } else {
      DateFormatUtils.formatShortDate(recordedAtMillis, dateLocale)
    }
    val title = if (isUrdu) "ناپ کی پرچی" else "MEASUREMENT SLIP"
    val defaultShop = if (isUrdu) "درزی لیجر" else "Darzi Ledger"
    val resolvedShop = shopName.ifBlank { defaultShop }.let { name ->
      if (isUrdu && name == "Darzi Ledger") "درزی لیجر" else name
    }

    val lines = mutableListOf<String>()
    lines.addAll(centerText(title, cols, preserveUnicode))
    lines.addAll(centerText(resolvedShop, cols, preserveUnicode))
    lines.add(divider(cols, '='))

    if (isUrdu) {
      val idPart = customerDisplayId?.let { "آئی ڈی: $it" } ?: ""
      if (idPart.isNotEmpty()) {
        lines.addAll(formatInlinePair("گاہک: $resolvedName", idPart, cols, preserveUnicode, alignGrid))
      } else {
        lines.addAll(wrapText("گاہک: $resolvedName", cols, preserveUnicode))
      }
      if (customerPhone.isNotBlank()) {
        lines.addAll(formatInlinePair("فون: $customerPhone", "تاریخ: $dateText", cols, preserveUnicode, alignGrid))
      } else {
        lines.addAll(wrapText("تاریخ: $dateText", cols, preserveUnicode))
      }
      lines.addAll(wrapText("قسم: $resolvedLabel", cols, preserveUnicode))
      lines.add(divider(cols))
      lines.add("ناپ (انچ)")
      val measurementEntries = listOf(
        "لمبائی: ${formatUrduInches(measurement.kameezLambai)}",
        "تیرا: ${formatUrduInches(measurement.tera)}",
        "بازو: ${formatUrduInches(measurement.bazu)}",
        "چھاتی: ${formatUrduInches(measurement.chati)}",
        "کمر: ${formatUrduInches(measurement.kamar)}",
        "گھیرا: ${formatUrduInches(measurement.gheraDaman)}",
        "گلا: ${formatUrduInches(measurement.galaCollar)}",
        "کف: ${formatUrduInches(measurement.cuff)}",
        "شلوار: ${formatUrduInches(measurement.shalwarLambai)}",
        "پانچہ: ${formatUrduInches(measurement.paincha)}",
        "آسن: ${formatUrduInches(measurement.gheraAsan)}",
      )
      lines.addAll(formatPairedEntries(measurementEntries, cols, preserveUnicode, alignGrid))

      lines.add(divider(cols))
      lines.add("سلائی و ڈیزائن")
      val styleEntries = listOf(
        "گلا: ${neckStyleLabel(style.neckStyle, true)}",
        "دامن: ${damanStyleLabel(style.damanStyle, true)}",
        "جیب: ${pocketStylesLabel(style.effectivePocketStyles(), true)}",
        "آستین: ${sleeveStyleLabel(style.sleeveStyle, true)}",
        "سلائی: ${silaiStyleLabel(style.silaiStyle, true)}",
      )
      lines.addAll(formatPairedEntries(styleEntries, cols, preserveUnicode, alignGrid))

      if (measurement.notes.isNotBlank()) {
        lines.add(divider(cols))
        lines.addAll(wrapText("نوٹ: ${measurement.notes}", cols, preserveUnicode))
      }
    } else {
      val idPart = customerDisplayId?.let { "ID: $it" } ?: ""
      if (idPart.isNotEmpty()) {
        lines.addAll(formatKeyValue("Customer: $resolvedName", idPart, cols, preserveUnicode))
      } else {
        lines.addAll(formatKeyValue("Customer:", resolvedName, cols, preserveUnicode))
      }
      if (customerPhone.isNotBlank()) {
        lines.addAll(formatInlinePair("Phone: $customerPhone", "Date: $dateText", cols, preserveUnicode, alignGrid))
      } else {
        lines.addAll(formatKeyValue("Date:", dateText, cols, preserveUnicode))
      }
      lines.addAll(wrapText("Garment: $resolvedLabel", cols, preserveUnicode))
      lines.add(divider(cols))
      lines.add("MEASUREMENTS (Inches)")
      val measurementEntries = listOf(
        "Lambai: ${DateFormatUtils.formatInches(measurement.kameezLambai)}",
        "Tera: ${DateFormatUtils.formatInches(measurement.tera)}",
        "Bazu: ${DateFormatUtils.formatInches(measurement.bazu)}",
        "Chati: ${DateFormatUtils.formatInches(measurement.chati)}",
        "Kamar: ${DateFormatUtils.formatInches(measurement.kamar)}",
        "Ghera: ${DateFormatUtils.formatInches(measurement.gheraDaman)}",
        "Gala: ${DateFormatUtils.formatInches(measurement.galaCollar)}",
        "Cuff: ${DateFormatUtils.formatInches(measurement.cuff)}",
        "Shalwar: ${DateFormatUtils.formatInches(measurement.shalwarLambai)}",
        "Paincha: ${DateFormatUtils.formatInches(measurement.paincha)}",
        "Asan: ${DateFormatUtils.formatInches(measurement.gheraAsan)}",
      )
      lines.addAll(formatPairedEntries(measurementEntries, cols, preserveUnicode, alignGrid))

      lines.add(divider(cols))
      lines.add("STITCHING STYLES")
      val useCompact = paperWidth == PaperWidth.MM_58
      val styleEntries = listOf(
        "Neck: ${neckStyleLabel(style.neckStyle, false)}",
        "Daman: ${damanStyleLabel(style.damanStyle, false, useCompact)}",
        "${if (useCompact) "Pkts" else "Pockets"}: ${pocketStylesLabel(style.effectivePocketStyles(), false, useCompact)}",
        "${if (useCompact) "Slv" else "Sleeves"}: ${sleeveStyleLabel(style.sleeveStyle, false, useCompact)}",
        "Silai: ${silaiStyleLabel(style.silaiStyle, false, useCompact)}",
      )
      lines.addAll(formatPairedEntries(styleEntries, cols, preserveUnicode, alignGrid))

      if (measurement.notes.isNotBlank()) {
        lines.add(divider(cols))
        lines.add("ADDITIONAL NOTE")
        lines.addAll(wrapText(measurement.notes, cols, preserveUnicode))
      }
    }

    lines.add(divider(cols, '='))
    return lines
  }

  fun formatTestPageLines(
    shopName: String,
    printerName: String,
    paperWidth: PaperWidth,
    preserveUnicode: Boolean = true,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): List<String> {
    val cols = paperWidth.columns
    val isUrdu = language == AppLanguage.URDU && preserveUnicode
    val defaultShop = if (isUrdu) "درزی لیجر" else "Darzi Ledger"
    val resolvedShop = shopName.ifBlank { defaultShop }.let { name ->
      if (isUrdu && name == "Darzi Ledger") "درزی لیجر" else name
    }
    val lines = mutableListOf<String>()
    lines.addAll(centerText(resolvedShop, cols, preserveUnicode))
    lines.addAll(centerText(if (isUrdu) "پرنٹر ٹیسٹ پرچی" else "PRINTER TEST SLIP", cols, preserveUnicode))
    lines.add(divider(cols, '='))
    if (isUrdu) {
      lines.addAll(wrapText("پرنٹر: $printerName", cols, preserveUnicode))
      lines.addAll(wrapText("کاغذ کا سائز: ${paperWidth.widthMm}mm ($cols)", cols, preserveUnicode))
      lines.addAll(wrapText("وقت: ${DateFormatUtils.formatDateTime(System.currentTimeMillis(), Locale.forLanguageTag("ur"))}", cols, preserveUnicode))
      lines.add(divider(cols))
      lines.addAll(wrapText("نمونہ: لمبائی 41.5 | تیرا 18.25", cols, preserveUnicode))
      lines.add(divider(cols, '='))
      lines.addAll(centerText("درزی لیجر پرنٹر تیار ہے", cols, preserveUnicode))
    } else {
      lines.addAll(formatKeyValue("Printer:", printerName, cols, preserveUnicode))
      lines.addAll(formatKeyValue("Paper Width:", "${paperWidth.widthMm}mm (${cols} cols)", cols, preserveUnicode))
      lines.addAll(formatKeyValue("Printed:", DateFormatUtils.formatDateTime(System.currentTimeMillis(), Locale.ENGLISH), cols, preserveUnicode))
      lines.add(divider(cols))
      lines.addAll(wrapText("Sample alignment: 1234567890 ABCDEFGHIJKLMNOPQRSTUVWXYZ", cols, preserveUnicode))
      lines.add(divider(cols, '='))
      lines.addAll(centerText("Darzi Ledger Ready", cols, preserveUnicode))
    }
    return lines
  }

  fun formatCustomerReceiptBytes(
    shopName: String,
    shopContact: String,
    orderDetail: OrderWithCustomer,
    payments: List<PaymentEntity>,
    paperWidth: PaperWidth,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): ByteArray {
    if (language == AppLanguage.URDU) {
      val unicodeLines = formatCustomerReceiptLines(
        shopName = shopName,
        shopContact = shopContact,
        orderDetail = orderDetail,
        payments = payments,
        paperWidth = paperWidth,
        preserveUnicode = true,
        language = AppLanguage.URDU,
      )
      encodeEscPosRasterDocument(
        lines = unicodeLines,
        paperWidth = paperWidth,
        isRtl = true,
      )?.let { return it }
    }
    return encodeEscPosDocument(
      headerTitle = shopName.ifBlank { "Darzi Ledger" },
      highlightLine = orderDetail.order.orderNumber?.let { "ORDER #$it" } ?: "NUMBER PENDING",
      bodyLines = formatCustomerReceiptLines(
        shopName = shopName,
        shopContact = shopContact,
        orderDetail = orderDetail,
        payments = payments,
        paperWidth = paperWidth,
        preserveUnicode = false,
        language = AppLanguage.ENGLISH,
      ),
    )
  }

  fun formatKarigarSlipBytes(
    shopName: String,
    orderDetail: OrderWithCustomer,
    measurement: MeasurementSnapshot,
    style: StyleSnapshot,
    paperWidth: PaperWidth,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): ByteArray {
    if (language == AppLanguage.URDU) {
      val unicodeLines = formatKarigarSlipLines(
        shopName = shopName,
        orderDetail = orderDetail,
        measurement = measurement,
        style = style,
        paperWidth = paperWidth,
        preserveUnicode = true,
        language = AppLanguage.URDU,
      )
      encodeEscPosRasterDocument(
        lines = unicodeLines,
        paperWidth = paperWidth,
        isRtl = true,
      )?.let { return it }
    }
    return encodeEscPosDocument(
      headerTitle = "KARIGAR SLIP",
      highlightLine = orderDetail.order.orderNumber?.let { "ORDER #$it" } ?: "NUMBER PENDING",
      bodyLines = formatKarigarSlipLines(
        shopName = shopName,
        orderDetail = orderDetail,
        measurement = measurement,
        style = style,
        paperWidth = paperWidth,
        preserveUnicode = false,
        language = AppLanguage.ENGLISH,
      ),
    )
  }

  fun formatMeasurementSlipBytes(
    shopName: String,
    customerName: String,
    customerPhone: String,
    customerDisplayId: String?,
    measurementLabel: String,
    measurement: MeasurementSnapshot,
    style: StyleSnapshot,
    recordedAtMillis: Long,
    paperWidth: PaperWidth,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): ByteArray {
    if (language == AppLanguage.URDU) {
      val unicodeLines = formatMeasurementSlipLines(
        shopName = shopName,
        customerName = customerName,
        customerPhone = customerPhone,
        customerDisplayId = customerDisplayId,
        measurementLabel = measurementLabel,
        measurement = measurement,
        style = style,
        recordedAtMillis = recordedAtMillis,
        paperWidth = paperWidth,
        preserveUnicode = true,
        language = AppLanguage.URDU,
      )
      encodeEscPosRasterDocument(
        lines = unicodeLines,
        paperWidth = paperWidth,
        isRtl = true,
      )?.let { return it }
    }
    return encodeEscPosDocument(
      headerTitle = "MEASUREMENT SLIP",
      highlightLine = customerDisplayId?.let { "$customerName (ID: $it)" } ?: customerName,
      bodyLines = formatMeasurementSlipLines(
        shopName = shopName,
        customerName = customerName,
        customerPhone = customerPhone,
        customerDisplayId = customerDisplayId,
        measurementLabel = measurementLabel,
        measurement = measurement,
        style = style,
        recordedAtMillis = recordedAtMillis,
        paperWidth = paperWidth,
        preserveUnicode = false,
        language = AppLanguage.ENGLISH,
      ),
    )
  }

  fun formatTestPageBytes(
    shopName: String,
    printerName: String,
    paperWidth: PaperWidth,
    language: AppLanguage = AppLanguage.ENGLISH,
  ): ByteArray {
    if (language == AppLanguage.URDU) {
      val unicodeLines = formatTestPageLines(
        shopName = shopName,
        printerName = printerName,
        paperWidth = paperWidth,
        preserveUnicode = true,
        language = AppLanguage.URDU,
      )
      encodeEscPosRasterDocument(
        lines = unicodeLines,
        paperWidth = paperWidth,
        isRtl = true,
      )?.let { return it }
    }
    return encodeEscPosDocument(
      headerTitle = shopName.ifBlank { "Darzi Ledger" },
      highlightLine = "TEST PRINT OK",
      bodyLines = formatTestPageLines(
        shopName = shopName,
        printerName = printerName,
        paperWidth = paperWidth,
        preserveUnicode = false,
        language = AppLanguage.ENGLISH,
      ),
    )
  }

  private fun encodeEscPosRasterDocument(
    lines: List<String>,
    paperWidth: PaperWidth,
    isRtl: Boolean,
  ): ByteArray? = runCatching {
    val widthPx = if (paperWidth == PaperWidth.MM_80) 576 else 384
    val horizontalPad = 8
    val contentWidth = widthPx - horizontalPad * 2
    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 24f else 22f
      typeface = Typeface.DEFAULT
    }
    val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      textSize = if (paperWidth == PaperWidth.MM_80) 26f else 24f
      typeface = Typeface.DEFAULT_BOLD
    }
    val direction = if (isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR

    val rows = mutableListOf<Any>() // Boolean (heavy divider) or StaticLayout
    var totalHeight = 8
    for ((index, rawLine) in lines.withIndex()) {
      val trimmed = rawLine.trim()
      if (trimmed.isNotEmpty() && trimmed.all { it == '=' || it == '-' }) {
        val heavy = trimmed.first() == '='
        rows.add(heavy)
        totalHeight += if (heavy) 12 else 10
        continue
      }
      val isCentered = rawLine.startsWith(" ") && trimmed.isNotEmpty()
      val isHeader = isCentered || index <= 4 || trimmed == "ناپ (انچ)" ||
        trimmed == "سلائی و ڈیزائن" || trimmed == "کاریگر نوٹس" ||
        trimmed == "MEASUREMENTS (Inches)" || trimmed == "STITCHING STYLES"
      val paint = if (isHeader) boldPaint else bodyPaint
      val text = if (isCentered) trimmed else rawLine
      val alignment = if (isCentered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
      val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
        .setAlignment(alignment)
        .setTextDirection(direction)
        .setLineSpacing(4f, 1.0f)
        .setIncludePad(true)
        .build()
      rows.add(layout)
      totalHeight += layout.height + 2
    }
    totalHeight = (totalHeight + 12).coerceAtLeast(32)

    val bitmap = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
    try {
      val canvas = Canvas(bitmap)
      canvas.drawColor(Color.WHITE)
      val linePaint = Paint().apply { color = Color.BLACK }
      var y = 6f
      for (row in rows) {
        when (row) {
          is Boolean -> {
            val stroke = if (row) 3f else 1.5f
            canvas.drawRect(horizontalPad.toFloat(), y + 4f, (widthPx - horizontalPad).toFloat(), y + 4f + stroke, linePaint)
            y += if (row) 12f else 10f
          }
          is StaticLayout -> {
            canvas.save()
            canvas.translate(horizontalPad.toFloat(), y)
            row.draw(canvas)
            canvas.restore()
            y += row.height + 2f
          }
        }
      }

      val out = ByteArrayOutputStream()
      out.write(EscPosCommands.INIT)
      val bytesPerRow = widthPx / 8
      val xL = (bytesPerRow and 0xFF).toByte()
      val xH = ((bytesPerRow shr 8) and 0xFF).toByte()
      val maxSliceHeight = 256
      var startY = 0
      val pixels = IntArray(widthPx)

      while (startY < totalHeight) {
        val sliceHeight = (totalHeight - startY).coerceAtMost(maxSliceHeight)
        val yL = (sliceHeight and 0xFF).toByte()
        val yH = ((sliceHeight shr 8) and 0xFF).toByte()
        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

        for (rowOffset in 0 until sliceHeight) {
          bitmap.getPixels(pixels, 0, widthPx, 0, startY + rowOffset, widthPx, 1)
          for (byteIdx in 0 until bytesPerRow) {
            var packed = 0
            val baseX = byteIdx * 8
            for (bit in 0 until 8) {
              val pixel = pixels[baseX + bit]
              val r = Color.red(pixel)
              val g = Color.green(pixel)
              val b = Color.blue(pixel)
              val luma = (r * 30 + g * 59 + b * 11) / 100
              if (luma < 160) {
                packed = packed or (1 shl (7 - bit))
              }
            }
            out.write(packed)
          }
        }
        startY += sliceHeight
      }

      out.write(EscPosCommands.FEED_AND_CUT)
      out.toByteArray()
    } finally {
      bitmap.recycle()
    }
  }.getOrNull()

  private fun encodeEscPosDocument(
    headerTitle: String,
    highlightLine: String,
    bodyLines: List<String>,
  ): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(EscPosCommands.INIT)

    out.write(EscPosCommands.ALIGN_CENTER)
    out.write(EscPosCommands.BOLD_ON)
    out.write(EscPosCommands.TEXT_DOUBLE_HEIGHT)
    out.write(lineBytes(sanitizeEscPosAscii(headerTitle).ifBlank { "Darzi Ledger" }))
    out.write(EscPosCommands.TEXT_NORMAL)
    out.write(lineBytes(sanitizeEscPosAscii(highlightLine)))
    out.write(EscPosCommands.BOLD_OFF)
    out.write(EscPosCommands.ALIGN_LEFT)

    bodyLines.forEach { line ->
      out.write(lineBytes(line))
    }

    out.write(EscPosCommands.FEED_AND_CUT)
    return out.toByteArray()
  }

  private fun lineBytes(line: String): ByteArray =
    (line + "\n").toByteArray(StandardCharsets.US_ASCII)

  private fun formatUrduPkr(amountPkr: Long): String = "${commaNumberFormat.format(amountPkr)} روپے"

  private fun formatCompactUrduPkr(amountPkr: Long): String = commaNumberFormat.format(amountPkr)

  private fun formatUrduInches(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) {
      rounded.toInt().toString()
    } else {
      rounded.toString().trimEnd('0').trimEnd('.')
    }
  }

  private fun orderStatusLabel(status: OrderStatus, isUrdu: Boolean): String =
    if (!isUrdu) status.displayName else when (status) {
      OrderStatus.PENDING -> "زیر التوا"
      OrderStatus.READY -> "تیار"
      OrderStatus.DELIVERED -> "حوالے"
      OrderStatus.CANCELLED -> "منسوخ"
    }

  private fun paymentStatusLabel(status: PaymentStatus, isUrdu: Boolean): String =
    if (!isUrdu) status.displayName else when (status) {
      PaymentStatus.UNPAID -> "بقایا"
      PaymentStatus.PARTIAL -> "جزوی"
      PaymentStatus.PAID -> "مکمل ادا"
    }

  private fun paymentMethodLabel(method: PaymentMethod, isUrdu: Boolean): String =
    if (!isUrdu) method.displayName else when (method) {
      PaymentMethod.CASH -> "نقد"
      PaymentMethod.JAZZCASH -> "جاز کیش"
      PaymentMethod.EASYPAISA -> "ایزی پیسہ"
      PaymentMethod.BANK_TRANSFER -> "بینک"
      PaymentMethod.OTHER -> "دیگر"
    }

  private fun neckStyleLabel(style: NeckStyle, isUrdu: Boolean): String =
    if (!isUrdu) style.displayName else when (style) {
      NeckStyle.COLLAR -> "کالر"
      NeckStyle.BAN -> "بین"
      NeckStyle.HALF_BAN -> "ہاف بین"
      NeckStyle.ROUND -> "گول"
    }

  private fun damanStyleLabel(style: DamanStyle, isUrdu: Boolean, compact: Boolean = false): String =
    if (!isUrdu) {
      if (!compact) style.displayName else when (style) {
        DamanStyle.GOL -> "Gol"
        DamanStyle.CHORASI -> "Chorasi"
      }
    } else when (style) {
      DamanStyle.GOL -> "گول"
      DamanStyle.CHORASI -> "چورس"
    }

  private fun pocketStylesLabel(styles: List<PocketStyle>, isUrdu: Boolean, compact: Boolean = false): String {
    if (styles.isEmpty()) return if (isUrdu) "کوئی نہیں" else "None"
    val separator = if (isUrdu) "، " else ", "
    return styles.joinToString(separator) { pocketStyleLabel(it, isUrdu, compact) }
  }

  private fun pocketStyleLabel(style: PocketStyle, isUrdu: Boolean, compact: Boolean = false): String =
    if (!isUrdu) {
      if (!compact) style.displayName else when (style) {
        PocketStyle.FRONT -> "Front"
        PocketStyle.ONE_SIDE -> "1 Side"
        PocketStyle.TWO_SIDE -> "2 Side"
        PocketStyle.SHALWAR_POCKET -> "Shalwar"
      }
    } else when (style) {
      PocketStyle.FRONT -> "سامنے"
      PocketStyle.ONE_SIDE -> "1 سائیڈ"
      PocketStyle.TWO_SIDE -> "2 سائیڈ"
      PocketStyle.SHALWAR_POCKET -> "شلوار"
    }

  private fun sleeveStyleLabel(style: SleeveStyle, isUrdu: Boolean, compact: Boolean = false): String =
    if (!isUrdu) {
      if (!compact) style.displayName else when (style) {
        SleeveStyle.PLAIN -> "Plain"
        SleeveStyle.SINGLE_CUFF -> "1-Cuff"
        SleeveStyle.DOUBLE_CUFF -> "2-Cuff"
        SleeveStyle.STUDS -> "Studs"
      }
    } else when (style) {
      SleeveStyle.PLAIN -> "سادہ"
      SleeveStyle.SINGLE_CUFF -> "سنگل کف"
      SleeveStyle.DOUBLE_CUFF -> "ڈبل کف"
      SleeveStyle.STUDS -> "اسٹڈ"
    }

  private fun silaiStyleLabel(style: SilaiStyle, isUrdu: Boolean, compact: Boolean = false): String =
    if (!isUrdu) {
      if (!compact) style.displayName else when (style) {
        SilaiStyle.SINGLE -> "Single"
        SilaiStyle.DOUBLE -> "Double"
        SilaiStyle.CHAMAK_DHAGA -> "Chamak"
      }
    } else when (style) {
      SilaiStyle.SINGLE -> "سنگل"
      SilaiStyle.DOUBLE -> "ڈبل"
      SilaiStyle.CHAMAK_DHAGA -> "چمک دھاگہ"
    }

  private fun sanitize(input: String, preserveUnicode: Boolean): String =
    if (preserveUnicode) sanitizePreview(input) else sanitizeEscPosAscii(input)

  private fun sanitizeSingleLine(input: String, preserveUnicode: Boolean): String =
    sanitize(input, preserveUnicode).replace(Regex("\\s+"), " ").trim()

  private fun sanitizePreview(input: String): String =
    buildString(input.length) {
      for (ch in input) {
        when {
          ch == '\r' -> Unit
          ch == '\u00A0' || ch == '\t' -> append(' ')
          ch == '\n' -> append('\n')
          ch.code < 0x20 || ch.code == 0x7F -> Unit
          else -> append(ch)
        }
      }
    }

  private fun sanitizeEscPosAscii(input: String): String {
    val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
    return buildString(normalized.length) {
      for (ch in normalized) {
        val type = Character.getType(ch)
        if (type == Character.NON_SPACING_MARK.toInt() ||
          type == Character.ENCLOSING_MARK.toInt() ||
          type == Character.COMBINING_SPACING_MARK.toInt()
        ) {
          continue
        }
        when (ch) {
          in '\u0020'..'\u007E', '\n' -> append(ch)
          '\r' -> Unit
          '\u00A0', '\t' -> append(' ')
          '—', '–' -> append('-')
          '•' -> append('*')
          '“', '”' -> append('"')
          '’', '‘' -> append('\'')
          '،' -> append(',')
          '؛' -> append(';')
          '؟' -> append('?')
          in '\u0660'..'\u0669' -> append(('0'.code + (ch.code - '\u0660'.code)).toChar())
          in '\u06F0'..'\u06F9' -> append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
          '\u0640', '\u200C', '\u200D', '\u200E', '\u200F', '\uFEFF' -> Unit
          else -> {
            val roman = urduCharToAscii(ch)
            if (roman != null) {
              append(roman)
            } else if (Character.isWhitespace(ch)) {
              append(' ')
            }
          }
        }
      }
    }.replace(Regex(" {2,}"), " ")
  }

  private fun urduCharToAscii(ch: Char): String? = when (ch) {
    'آ', 'ا', 'أ', 'إ', 'ٱ' -> "a"
    'ب' -> "b"
    'پ' -> "p"
    'ت', 'ٹ', 'ط' -> "t"
    'ث', 'س', 'ص' -> "s"
    'ج' -> "j"
    'چ' -> "ch"
    'ح', 'ہ', 'ھ', 'ۂ', 'ة' -> "h"
    'خ' -> "kh"
    'د', 'ڈ' -> "d"
    'ذ', 'ز', 'ض', 'ظ' -> "z"
    'ر', 'ڑ' -> "r"
    'ژ' -> "zh"
    'ش' -> "sh"
    'ع' -> "a"
    'غ' -> "gh"
    'ف' -> "f"
    'ق' -> "q"
    'ک', 'ك' -> "k"
    'گ' -> "g"
    'ل' -> "l"
    'م' -> "m"
    'ن', 'ں' -> "n"
    'و', 'ؤ' -> "o"
    'ی', 'ي', 'ئ', 'ے', 'ۓ' -> "y"
    'ء' -> "'"
    else -> null
  }
}
