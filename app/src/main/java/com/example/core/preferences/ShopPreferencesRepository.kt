package com.example.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.shopPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "darzi_shop_preferences"
)

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}

enum class AppLanguage(val code: String) {
  ENGLISH("en"),
  URDU("ur"),
}

data class ShopPreferences(
  val shopName: String = "Darzi Ledger",
  val shopContact: String = "",
  val paperWidthMm: Int = 58,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val language: AppLanguage = AppLanguage.URDU,
)

interface ShopPreferencesRepository {
  val preferencesFlow: Flow<ShopPreferences>

  suspend fun updateShopProfile(shopName: String, shopContact: String)

  suspend fun updatePaperWidth(paperWidthMm: Int)

  suspend fun updateThemeMode(themeMode: ThemeMode)

  suspend fun updateLanguage(language: AppLanguage)
}

class DataStoreShopPreferencesRepository(
  private val context: Context,
) : ShopPreferencesRepository {

  private object Keys {
    val SHOP_NAME = stringPreferencesKey("shop_name")
    val SHOP_CONTACT = stringPreferencesKey("shop_contact")
    val PAPER_WIDTH_MM = intPreferencesKey("paper_width_mm")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val LANGUAGE = stringPreferencesKey("app_language")
  }

  override val preferencesFlow: Flow<ShopPreferences> =
    context.shopPrefsDataStore.data.map { prefs ->
      val savedTheme = prefs[Keys.THEME_MODE]
      val themeMode =
        ThemeMode.entries.firstOrNull { it.name == savedTheme } ?: ThemeMode.SYSTEM
      val savedLanguage = prefs[Keys.LANGUAGE]
      val language =
        AppLanguage.entries.firstOrNull { it.name == savedLanguage || it.code == savedLanguage }
          ?: AppLanguage.URDU
      val paperWidth = prefs[Keys.PAPER_WIDTH_MM]?.takeIf { it == 58 || it == 80 } ?: 58
      ShopPreferences(
        shopName = prefs[Keys.SHOP_NAME]?.takeIf { it.isNotBlank() } ?: "Darzi Ledger",
        shopContact = prefs[Keys.SHOP_CONTACT] ?: "",
        paperWidthMm = paperWidth,
        themeMode = themeMode,
        language = language,
      )
    }

  override suspend fun updateShopProfile(shopName: String, shopContact: String) {
    val trimmedName = shopName.trim().ifEmpty { "Darzi Ledger" }
    val trimmedContact = shopContact.trim()
    context.shopPrefsDataStore.edit { prefs ->
      prefs[Keys.SHOP_NAME] = trimmedName
      prefs[Keys.SHOP_CONTACT] = trimmedContact
    }
  }

  override suspend fun updatePaperWidth(paperWidthMm: Int) {
    val normalized = if (paperWidthMm == 80) 80 else 58
    context.shopPrefsDataStore.edit { prefs ->
      prefs[Keys.PAPER_WIDTH_MM] = normalized
    }
  }

  override suspend fun updateThemeMode(themeMode: ThemeMode) {
    context.shopPrefsDataStore.edit { prefs ->
      prefs[Keys.THEME_MODE] = themeMode.name
    }
  }

  override suspend fun updateLanguage(language: AppLanguage) {
    context.shopPrefsDataStore.edit { prefs ->
      prefs[Keys.LANGUAGE] = language.name
    }
  }
}
