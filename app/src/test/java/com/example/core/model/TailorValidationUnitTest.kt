package com.example.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TailorValidationUnitTest {

  @Test
  fun `pakistani phone normalization handles +92, 0092, dashes, and spaces`() {
    assertEquals("03001234567", PhoneNumberUtils.normalizePakistaniPhone("+92 300 1234567"))
    assertEquals("03001234567", PhoneNumberUtils.normalizePakistaniPhone("0092-300-1234567"))
    assertEquals("03001234567", PhoneNumberUtils.normalizePakistaniPhone("923001234567"))
    assertEquals("03001234567", PhoneNumberUtils.normalizePakistaniPhone("0300-1234567"))
    assertEquals("03001234567", PhoneNumberUtils.normalizePakistaniPhone("3001234567"))
    assertEquals("0300-1234567", PhoneNumberUtils.formatPakistaniPhone("+923001234567"))
  }

  @Test
  fun `order status transitions enforce valid workflow`() {
    assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.READY))
    assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED))
    assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED))

    assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.DELIVERED))
    assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.CANCELLED))

    assertFalse(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.PENDING))
    assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.READY))
  }

  @Test
  fun `payment validation prevents negative or excess PKR payments`() {
    assertNotNull(TailorValidators.validatePayment(0L, 1500L))
    assertNotNull(TailorValidators.validatePayment(-200L, 1500L))
    assertNotNull(TailorValidators.validatePayment(1501L, 1500L))
    assertNull(TailorValidators.validatePayment(1500L, 1500L))
    assertNull(TailorValidators.validatePayment(500L, 1500L))
  }

  @Test
  fun `measurement validation checks realistic inch bounds`() {
    val valid = MeasurementSnapshot(
      kameezLambai = 41.5,
      tera = 18.0,
      bazu = 24.5,
      chati = 42.0,
      kamar = 38.5,
      gheraDaman = 43.0,
      galaCollar = 16.5,
      cuff = 9.5,
      shalwarLambai = 39.5,
      paincha = 7.5,
      gheraAsan = 16.0,
    )
    assertNull(TailorValidators.validateMeasurement(valid))
    assertNotNull(TailorValidators.validateMeasurement(valid.copy(kameezLambai = 5.0)))
    assertNotNull(TailorValidators.validateMeasurement(valid.copy(paincha = 30.0)))
  }

  @Test
  fun `customer validation requires name and validates phone only when provided`() {
    assertNotNull(TailorValidators.validateCustomer("A", "0300-1234567"))
    assertNull(TailorValidators.validateCustomer("Ali", ""))
    assertNull(TailorValidators.validateCustomer("Ali", "03001234567"))
    assertNotNull(TailorValidators.validateCustomer("Ali", "123"))
  }
}
