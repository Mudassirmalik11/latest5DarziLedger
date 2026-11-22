package com.example.core.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormatUtils {
  fun formatDate(millis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("dd MMM yyyy", locale).format(Date(millis))

  fun formatFullDate(millis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("d MMMM yyyy", locale).format(Date(millis))

  fun formatShortDate(millis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("dd MMM", locale).format(Date(millis))

  fun formatDateTime(millis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(Date(millis))

  fun formatFullDateTime(millis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("MMMM d, yyyy h:mm a", locale).format(Date(millis))

  fun formatInches(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) {
      "${rounded.toInt()}\""
    } else {
      val text = rounded.toString().trimEnd('0').trimEnd('.')
      "$text\""
    }
  }
}
