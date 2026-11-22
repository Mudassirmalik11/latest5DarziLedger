package com.example.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.core.preferences.AppLanguage
import java.util.Locale

private class LocalizedActivityContextWrapper(
  base: Context,
  private val localizedResources: Resources,
) : ContextWrapper(base) {
  override fun getResources(): Resources = localizedResources

  override fun getAssets(): AssetManager = localizedResources.assets
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

@Composable
fun ProvideAppLocale(
  language: AppLanguage,
  content: @Composable () -> Unit,
) {
  val baseContext = LocalContext.current
  val baseConfiguration = LocalConfiguration.current

  val locale = remember(language) {
    when (language) {
      AppLanguage.ENGLISH -> Locale.ENGLISH
      AppLanguage.URDU -> Locale.forLanguageTag("ur")
    }
  }

  val localizedConfig = remember(baseConfiguration, locale) {
    Configuration(baseConfiguration).apply {
      setLocale(locale)
      setLayoutDirection(Locale.ENGLISH)
    }
  }

  // Keep host Activity resources & JVM default Locale synced so separate Windows
  // (Dialog, AlertDialog, DatePickerDialog, ModalBottomSheet, DropdownMenu) resolve
  // stringResource() in the active language without recreating the Activity,
  // while keeping UI layout direction strictly Left-to-Right.
  if (Locale.getDefault().language != locale.language) {
    Locale.setDefault(locale)
  }
  val hostResources = remember(baseContext) {
    baseContext.findActivity()?.resources ?: baseContext.resources
  }
  val hostLocale = hostResources.configuration.locales.let { list ->
    if (list.isEmpty) null else list[0]
  }
  if (hostLocale?.language != locale.language ||
    hostResources.configuration.layoutDirection != View.LAYOUT_DIRECTION_LTR
  ) {
    @Suppress("DEPRECATION")
    hostResources.updateConfiguration(localizedConfig, hostResources.displayMetrics)
  }

  val localizedContext = remember(baseContext, localizedConfig) {
    val configContext = baseContext.createConfigurationContext(localizedConfig)
    LocalizedActivityContextWrapper(
      base = baseContext,
      localizedResources = configContext.resources,
    )
  }

  CompositionLocalProvider(
    LocalContext provides localizedContext,
    LocalConfiguration provides localizedConfig,
    LocalLayoutDirection provides LayoutDirection.Ltr,
    content = content,
  )
}

fun localizedAppText(english: String, urdu: String): String =
  if (Locale.getDefault().language == "ur") urdu else english
