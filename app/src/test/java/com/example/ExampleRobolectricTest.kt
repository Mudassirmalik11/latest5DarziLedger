package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.testing.AuthorizedShopTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private val authRule = AuthorizedShopTestRule()
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(authRule).around(composeTestRule)

  @Test
  fun `app name matches Darzi Ledger`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Darzi Ledger", appName)
  }

  @Test
  fun `landing grid, drawer, and back navigation switches between customers, orders, ledger, and settings without bottom bar`() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithTag("dashboard_screen").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithTag("navigation_bar").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithTag("nav_tab_customers").fetchSemanticsNodes().size)

    composeTestRule.onNodeWithTag("dashboard_tile_customers").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("customers_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("topbar_back_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("dashboard_tile_orders").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("orders_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("topbar_back_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("dashboard_tile_ledger").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("ledger_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("topbar_back_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("dashboard_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drawer_item_settings").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
  }
}
