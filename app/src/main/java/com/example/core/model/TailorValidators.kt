package com.example.core.model

import java.util.Locale

object TailorValidators {

  private fun isUrdu(): Boolean = Locale.getDefault().language == "ur"

  fun validateCustomer(name: String, phone: String): String? {
    if (name.trim().length < 2) {
      return if (isUrdu()) {
        "گاہک کا نام کم از کم 2 حروف کا ہونا چاہیے۔"
      } else {
        "Customer name must be at least 2 characters."
      }
    }
    if (phone.isNotBlank() && !PhoneNumberUtils.isValidPakistaniPhone(phone)) {
      return if (isUrdu()) {
        "درست پاکستانی موبائل نمبر درج کریں (مثلاً 1234567-0300)۔"
      } else {
        "Enter a valid Pakistani phone number (e.g., 0300-1234567)."
      }
    }
    return null
  }

  fun validateMeasurement(snapshot: MeasurementSnapshot): String? {
    val urdu = isUrdu()
    val fields = listOf(
      (if (urdu) "قمیض لمبائی" else "Kameez Lambai") to (snapshot.kameezLambai to (20.0..60.0)),
      (if (urdu) "تیرا" else "Tera") to (snapshot.tera to (10.0..30.0)),
      (if (urdu) "بازو" else "Bazu") to (snapshot.bazu to (12.0..35.0)),
      (if (urdu) "چھاتی" else "Chati") to (snapshot.chati to (15.0..65.0)),
      (if (urdu) "کمر" else "Kamar") to (snapshot.kamar to (14.0..65.0)),
      (if (urdu) "گھیرا / دامن" else "Ghera / Daman") to (snapshot.gheraDaman to (15.0..65.0)),
      (if (urdu) "گلا / کالر" else "Gala / Collar") to (snapshot.galaCollar to (10.0..25.0)),
      (if (urdu) "کف" else "Cuff") to (snapshot.cuff to (0.0..15.0)),
      (if (urdu) "شلوار لمبائی" else "Shalwar Lambai") to (snapshot.shalwarLambai to (20.0..55.0)),
      (if (urdu) "پانچہ" else "Paincha") to (snapshot.paincha to (4.0..20.0)),
      (if (urdu) "گھیرا / آسن" else "Ghera / Asan") to (snapshot.gheraAsan to (10.0..35.0)),
    )
    for ((label, valueAndRange) in fields) {
      val (value, range) = valueAndRange
      if (value.isNaN() || value.isInfinite() || value !in range) {
        return if (urdu) {
          "$label ${range.start} سے ${range.endInclusive} انچ کے درمیان ہونا چاہیے۔"
        } else {
          "$label must be between ${range.start} and ${range.endInclusive} inches."
        }
      }
    }
    return null
  }

  fun validateNewOrder(
    suitCount: Int,
    totalAmountPkr: Long,
    initialAdvancePkr: Long,
  ): String? {
    val urdu = isUrdu()
    if (suitCount < 1 || suitCount > 100) {
      return if (urdu) {
        "سوٹ کی تعداد 1 سے 100 کے درمیان ہونی چاہیے۔"
      } else {
        "Suit count must be between 1 and 100."
      }
    }
    if (totalAmountPkr < 0L) {
      return if (urdu) "کل رقم منفی نہیں ہو سکتی۔" else "Total amount cannot be negative."
    }
    if (initialAdvancePkr < 0L) {
      return if (urdu) "پیشگی ادائیگی منفی نہیں ہو سکتی۔" else "Advance payment cannot be negative."
    }
    if (initialAdvancePkr > totalAmountPkr) {
      return if (urdu) {
        "پیشگی ادائیگی کل بل سے زیادہ نہیں ہو سکتی۔"
      } else {
        "Advance payment cannot exceed total bill."
      }
    }
    return null
  }

  fun validatePayment(
    amountPkr: Long,
    remainingBalancePkr: Long,
  ): String? {
    val urdu = isUrdu()
    if (amountPkr <= 0L) {
      return if (urdu) {
        "ادائیگی کی رقم 0 روپے سے زیادہ ہونی چاہیے۔"
      } else {
        "Payment amount must be greater than Rs. 0."
      }
    }
    if (amountPkr > remainingBalancePkr) {
      return if (urdu) {
        "ادائیگی بقایا رقم ($remainingBalancePkr روپے) سے زیادہ نہیں ہو سکتی۔"
      } else {
        "Payment cannot exceed remaining balance of Rs. $remainingBalancePkr."
      }
    }
    return null
  }
}
