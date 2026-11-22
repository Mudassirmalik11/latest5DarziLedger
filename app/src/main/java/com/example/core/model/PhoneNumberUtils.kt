package com.example.core.model

object PhoneNumberUtils {

  /**
   * Normalizes Pakistani mobile/landline numbers into canonical searchable digits.
   * Examples:
   *  - "+92 300 1234567" -> "03001234567"
   *  - "0092-300-1234567" -> "03001234567"
   *  - "923001234567" -> "03001234567"
   *  - "3001234567" -> "03001234567"
   *  - "0300-1234567" -> "03001234567"
   */
  fun normalizePakistaniPhone(raw: String): String {
    val digitsOnly = raw.filter { it.isDigit() }
    if (digitsOnly.isEmpty()) return ""

    return when {
      digitsOnly.startsWith("0092") && digitsOnly.length >= 13 -> "0" + digitsOnly.drop(4)
      digitsOnly.startsWith("92") && digitsOnly.length >= 12 -> "0" + digitsOnly.drop(2)
      digitsOnly.startsWith("3") && digitsOnly.length == 10 -> "0$digitsOnly"
      else -> digitsOnly
    }
  }

  /**
   * Formats normalized Pakistani mobile numbers as "03XX-XXXXXXX" when 11 digits starting with 03.
   */
  fun formatPakistaniPhone(raw: String): String {
    val normalized = normalizePakistaniPhone(raw)
    return if (normalized.length == 11 && normalized.startsWith("03")) {
      "${normalized.substring(0, 4)}-${normalized.substring(4)}"
    } else {
      raw.trim()
    }
  }

  fun isValidPakistaniPhone(raw: String): Boolean {
    val normalized = normalizePakistaniPhone(raw)
    if (normalized.length < 7 || normalized.length > 15) return false
    if (normalized.startsWith("03")) {
      return normalized.length == 11
    }
    return true
  }
}
