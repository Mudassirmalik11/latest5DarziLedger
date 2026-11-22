package com.example.core.ui

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

object PkrFormatUtils {
  private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

  fun formatPkr(amountPkr: Long): String = "Rs. ${numberFormat.format(amountPkr)}"

  fun todayBoundsMillis(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 1)
    val end = cal.timeInMillis
    return start to end
  }

  fun weekBoundsMillis(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      firstDayOfWeek = Calendar.MONDAY
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.WEEK_OF_YEAR, 1)
    val end = cal.timeInMillis
    return start to end
  }

  fun monthBoundsMillis(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      set(Calendar.DAY_OF_MONTH, 1)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    val end = cal.timeInMillis
    return start to end
  }

  fun yearBoundsMillis(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      set(Calendar.MONTH, Calendar.JANUARY)
      set(Calendar.DAY_OF_MONTH, 1)
    }
    val start = cal.timeInMillis
    cal.add(Calendar.YEAR, 1)
    val end = cal.timeInMillis
    return start to end
  }

  fun startOfDayPlusDays(daysToAdd: Int, nowMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 18)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      add(Calendar.DAY_OF_YEAR, daysToAdd)
    }
    return cal.timeInMillis
  }
}
