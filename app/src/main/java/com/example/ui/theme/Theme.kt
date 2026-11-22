package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
  primary = PinePrimary,
  onPrimary = OnPinePrimary,
  primaryContainer = PineContainer,
  onPrimaryContainer = OnPineContainer,
  secondary = BrassSecondary,
  onSecondary = OnBrassSecondary,
  secondaryContainer = BrassContainer,
  onSecondaryContainer = OnBrassContainer,
  tertiary = IndigoTertiary,
  onTertiary = OnIndigoTertiary,
  tertiaryContainer = IndigoContainer,
  onTertiaryContainer = OnIndigoTertiaryContainer,
  background = LinenBackground,
  onBackground = LinenOnBackground,
  surface = LinenSurface,
  onSurface = LinenOnSurface,
  surfaceVariant = LinenSurfaceVariant,
  onSurfaceVariant = LinenOnSurfaceVariant,
  surfaceContainerLowest = LinenSurfaceContainerLowest,
  surfaceContainerLow = LinenSurfaceContainerLow,
  surfaceContainer = LinenSurfaceContainer,
  surfaceContainerHigh = LinenSurfaceContainerHigh,
  outline = LinenOutline,
  outlineVariant = LinenOutlineVariant,
  error = TerracottaError,
  onError = OnTerracottaError,
  errorContainer = TerracottaErrorContainer,
  onErrorContainer = OnTerracottaErrorContainer,
)

private val DarkColorScheme = darkColorScheme(
  primary = PinePrimaryDark,
  onPrimary = OnPinePrimaryDark,
  primaryContainer = PineContainerDark,
  onPrimaryContainer = OnPineContainerDark,
  secondary = BrassSecondaryDark,
  onSecondary = OnBrassSecondaryDark,
  secondaryContainer = BrassContainerDark,
  onSecondaryContainer = OnBrassContainerDark,
  tertiary = IndigoTertiaryDark,
  onTertiary = OnIndigoTertiaryDark,
  tertiaryContainer = IndigoContainerDark,
  onTertiaryContainer = OnIndigoContainerDark,
  background = CharcoalBackgroundDark,
  onBackground = CharcoalOnBackgroundDark,
  surface = CharcoalSurfaceDark,
  onSurface = CharcoalOnSurfaceDark,
  surfaceVariant = CharcoalSurfaceVariantDark,
  onSurfaceVariant = CharcoalOnSurfaceVariantDark,
  surfaceContainerLow = CharcoalSurfaceContainerLowDark,
  surfaceContainer = CharcoalSurfaceContainerDark,
  surfaceContainerHigh = CharcoalSurfaceContainerHighDark,
  outline = CharcoalOutlineDark,
  outlineVariant = CharcoalOutlineVariantDark,
)

@Composable
fun DarziLedgerTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = DarziTypography,
    shapes = DarziShapes,
    content = content,
  )
}
