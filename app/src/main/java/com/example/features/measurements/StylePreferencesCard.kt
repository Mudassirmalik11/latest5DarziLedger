package com.example.features.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.DamanStyle
import com.example.core.model.NeckStyle
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.ui.AtelierCard
import com.example.features.customers.localizedDamanStyle
import com.example.features.customers.localizedNeckStyle
import com.example.features.customers.localizedPocketStyle
import com.example.features.customers.localizedSilaiStyle
import com.example.features.customers.localizedSleeveStyle

@Composable
fun StylePreferencesCard(
  neckStyle: NeckStyle,
  onSelectNeckStyle: (NeckStyle) -> Unit,
  damanStyle: DamanStyle,
  onSelectDamanStyle: (DamanStyle) -> Unit,
  pocketStyles: Set<PocketStyle>,
  onTogglePocketStyle: (PocketStyle) -> Unit,
  sleeveStyle: SleeveStyle,
  onSelectSleeveStyle: (SleeveStyle) -> Unit,
  silaiStyle: SilaiStyle,
  onSelectSilaiStyle: (SilaiStyle) -> Unit,
  modifier: Modifier = Modifier,
  footerContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
  AtelierCard(modifier = modifier.testTag("style_preferences_card")) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = stringResource(R.string.measurement_styles_section),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )

      SingleStyleChipGroup(
        title = stringResource(R.string.style_neck),
        options = NeckStyle.entries,
        selected = neckStyle,
        labelFor = { localizedNeckStyle(it) },
        tagPrefix = "style_neck",
        onSelect = onSelectNeckStyle,
      )

      SingleStyleChipGroup(
        title = stringResource(R.string.style_daman),
        options = DamanStyle.entries,
        selected = damanStyle,
        labelFor = { localizedDamanStyle(it) },
        tagPrefix = "style_daman",
        onSelect = onSelectDamanStyle,
      )

      MultiStyleChipGroup(
        title = stringResource(R.string.style_pockets),
        options = PocketStyle.entries,
        selected = pocketStyles,
        labelFor = { localizedPocketStyle(it) },
        tagPrefix = "style_pocket",
        onToggle = onTogglePocketStyle,
      )

      SingleStyleChipGroup(
        title = stringResource(R.string.style_sleeves),
        options = SleeveStyle.entries,
        selected = sleeveStyle,
        labelFor = { localizedSleeveStyle(it) },
        tagPrefix = "style_sleeve",
        onSelect = onSelectSleeveStyle,
      )

      SingleStyleChipGroup(
        title = stringResource(R.string.style_silai),
        options = SilaiStyle.entries,
        selected = silaiStyle,
        labelFor = { localizedSilaiStyle(it) },
        tagPrefix = "style_silai",
        onSelect = onSelectSilaiStyle,
      )

      if (footerContent != null) {
        footerContent()
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : Enum<T>> SingleStyleChipGroup(
  title: String,
  options: List<T>,
  selected: T,
  labelFor: @Composable (T) -> String,
  tagPrefix: String,
  onSelect: (T) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      options.forEach { option ->
        FilterChip(
          selected = option == selected,
          onClick = { onSelect(option) },
          label = { Text(labelFor(option)) },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("${tagPrefix}_${option.name.lowercase()}"),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : Enum<T>> MultiStyleChipGroup(
  title: String,
  options: List<T>,
  selected: Set<T>,
  labelFor: @Composable (T) -> String,
  tagPrefix: String,
  onToggle: (T) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      options.forEach { option ->
        FilterChip(
          selected = option in selected,
          onClick = { onToggle(option) },
          label = { Text(labelFor(option)) },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("${tagPrefix}_${option.name.lowercase()}"),
        )
      }
    }
  }
}
