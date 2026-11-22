package com.example.features.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.app.TailorApp
import com.example.core.model.DeviceRegistrationStatus
import com.example.data.remote.AuthUser
import com.example.testing.AuthorizedShopTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthAndDeviceGateTest {

  private val authRule = AuthorizedShopTestRule(initialSignedIn = false, authorizeLocalDevice = false)
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `unauthenticated launch shows google sign in gate and sign in enters authorized shop`() {
    composeTestRule.waitForIdle()

    // Unauthenticated cold start: AuthScreen visible, feature UI NOT composed
    composeTestRule.onNodeWithTag("auth_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithTag("nav_tab_customers").fetchSemanticsNodes().size)

    // Pre-authorize local device and perform Google Sign-In
    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val testUser = AuthUser("test_shop_uid", "Al-Noor Owner", "owner@alnoor.pk")
    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      app.container.deviceSyncRepository.updateDeviceAuthorization(
        deviceId = device.deviceId,
        shopId = testUser.uid,
        deviceName = "Counter Phone",
        deviceRole = "PHONE",
        status = DeviceRegistrationStatus.AUTHORIZED,
        registeredAtMillis = System.currentTimeMillis(),
      )
    }

    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()

    // Open Settings via navigation drawer and verify signed-in account and Sign Out flow
    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("Al-Noor Owner").onFirst().assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_sign_out_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("auth_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("auth_screen").assertIsDisplayed()
  }

  @Test
  fun `email password sign up validates mismatch, creates account, signs out, and signs back in`() {
    composeTestRule.waitForIdle()

    val app = ApplicationProvider.getApplicationContext<TailorApp>()
    val testUser = AuthUser("test_shop_uid", "Al-Noor Master", "master@alnoor.pk")
    runBlocking {
      val device = app.container.deviceSyncRepository.ensureLocalDeviceConfig()
      app.container.deviceSyncRepository.updateDeviceAuthorization(
        deviceId = device.deviceId,
        shopId = testUser.uid,
        deviceName = "Shop Tablet",
        deviceRole = "TABLET",
        status = DeviceRegistrationStatus.AUTHORIZED,
        registeredAtMillis = System.currentTimeMillis(),
      )
    }

    // Switch to Sign Up tab
    composeTestRule.onNodeWithTag("auth_mode_sign_up").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("auth_name_input").performScrollTo().performTextInput("Al-Noor Master")
    composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("master@alnoor.pk")
    composeTestRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("secret123")
    composeTestRule.onNodeWithTag("auth_confirm_password_input").performScrollTo().performTextInput("mismatch")
    composeTestRule.onNodeWithTag("auth_email_submit_button").performScrollTo().performClick()

    composeTestRule.onNodeWithTag("auth_error_banner")
      .performScrollTo()
      .assertIsDisplayed()
      .assertTextContains("Passwords do not match", substring = true)

    // Fix confirm password and submit Sign Up
    composeTestRule.onNodeWithTag("auth_confirm_password_input").performScrollTo().performTextClearance()
    composeTestRule.onNodeWithTag("auth_confirm_password_input").performTextInput("secret123")
    composeTestRule.onNodeWithTag("auth_email_submit_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()

    // Sign out from Settings via navigation drawer
    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("Al-Noor Master").onFirst().assertIsDisplayed()

    composeTestRule.onNodeWithTag("settings_sign_out_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("auth_screen").fetchSemanticsNodes().isNotEmpty()
    }

    // Sign back in with the newly registered Email + Password
    composeTestRule.onNodeWithTag("auth_mode_sign_in").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("master@alnoor.pk")
    composeTestRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("secret123")
    composeTestRule.onNodeWithTag("auth_email_submit_button").performScrollTo().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
  }
}
