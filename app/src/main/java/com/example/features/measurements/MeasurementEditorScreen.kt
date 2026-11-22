package com.example.features.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ui.AtelierCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeasurementEditorScreen(
  viewModel: MeasurementEditorViewModel,
  onNavigateBack: () -> Unit,
  onSaved: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.formState.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("measurement_editor_screen"),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = stringResource(R.string.measurement_editor_title),
              style = MaterialTheme.typography.titleLarge,
            )
            if (state.customerName.isNotBlank()) {
              Text(
                text = state.customerName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 820.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          OutlinedTextField(
            value = state.label,
            onValueChange = { value -> viewModel.updateField { it.copy(label = value) } },
            label = { Text(stringResource(R.string.measurement_label_hint)) },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("meas_label_input"),
          )

          // Kameez Measurements (Inches)
          AtelierCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = stringResource(R.string.measurement_kameez_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
              )

              MeasurementTwoColumnRow(
                leftLabel = stringResource(R.string.meas_kameez_lambai),
                leftValue = state.kameezLambai,
                leftTag = "meas_kameez_lambai",
                onLeftChange = { v -> viewModel.updateField { it.copy(kameezLambai = v) } },
                rightLabel = stringResource(R.string.meas_tera),
                rightValue = state.tera,
                rightTag = "meas_tera",
                onRightChange = { v -> viewModel.updateField { it.copy(tera = v) } },
              )
              MeasurementTwoColumnRow(
                leftLabel = stringResource(R.string.meas_bazu),
                leftValue = state.bazu,
                leftTag = "meas_bazu",
                onLeftChange = { v -> viewModel.updateField { it.copy(bazu = v) } },
                rightLabel = stringResource(R.string.meas_chati),
                rightValue = state.chati,
                rightTag = "meas_chati",
                onRightChange = { v -> viewModel.updateField { it.copy(chati = v) } },
              )
              MeasurementTwoColumnRow(
                leftLabel = stringResource(R.string.meas_kamar),
                leftValue = state.kamar,
                leftTag = "meas_kamar",
                onLeftChange = { v -> viewModel.updateField { it.copy(kamar = v) } },
                rightLabel = stringResource(R.string.meas_ghera_daman),
                rightValue = state.gheraDaman,
                rightTag = "meas_ghera_daman",
                onRightChange = { v -> viewModel.updateField { it.copy(gheraDaman = v) } },
              )
              MeasurementTwoColumnRow(
                leftLabel = stringResource(R.string.meas_gala_collar),
                leftValue = state.galaCollar,
                leftTag = "meas_gala_collar",
                onLeftChange = { v -> viewModel.updateField { it.copy(galaCollar = v) } },
                rightLabel = stringResource(R.string.meas_cuff),
                rightValue = state.cuff,
                rightTag = "meas_cuff",
                onRightChange = { v -> viewModel.updateField { it.copy(cuff = v) } },
              )
            }
          }

          // Shalwar Measurements (Inches)
          AtelierCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = stringResource(R.string.measurement_shalwar_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
              )

              MeasurementTwoColumnRow(
                leftLabel = stringResource(R.string.meas_shalwar_lambai),
                leftValue = state.shalwarLambai,
                leftTag = "meas_shalwar_lambai",
                onLeftChange = { v -> viewModel.updateField { it.copy(shalwarLambai = v) } },
                rightLabel = stringResource(R.string.meas_paincha),
                rightValue = state.paincha,
                rightTag = "meas_paincha",
                onRightChange = { v -> viewModel.updateField { it.copy(paincha = v) } },
              )

              InchInputField(
                label = stringResource(R.string.meas_ghera_asan),
                value = state.gheraAsan,
                onValueChange = { v -> viewModel.updateField { it.copy(gheraAsan = v) } },
                modifier = Modifier
                  .fillMaxWidth(0.5f)
                  .testTag("meas_ghera_asan"),
              )
            }
          }

          // Style Selectors
          StylePreferencesCard(
            neckStyle = state.neckStyle,
            onSelectNeckStyle = { style -> viewModel.updateField { it.copy(neckStyle = style) } },
            damanStyle = state.damanStyle,
            onSelectDamanStyle = { style -> viewModel.updateField { it.copy(damanStyle = style) } },
            pocketStyles = state.pocketStyles,
            onTogglePocketStyle = { style ->
              viewModel.updateField { current ->
                val updated = if (style in current.pocketStyles) {
                  current.pocketStyles - style
                } else {
                  current.pocketStyles + style
                }
                current.copy(pocketStyles = updated)
              }
            },
            sleeveStyle = state.sleeveStyle,
            onSelectSleeveStyle = { style -> viewModel.updateField { it.copy(sleeveStyle = style) } },
            silaiStyle = state.silaiStyle,
            onSelectSilaiStyle = { style -> viewModel.updateField { it.copy(silaiStyle = style) } },
          )

          OutlinedTextField(
            value = state.notes,
            onValueChange = { v -> viewModel.updateField { it.copy(notes = v) } },
            label = { Text(stringResource(R.string.customer_notes_label)) },
            maxLines = 3,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("meas_notes_input"),
          )

          if (!state.errorMessage.isNullOrBlank()) {
            Text(
              text = state.errorMessage!!,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("meas_error_text"),
            )
          }

          Button(
            onClick = { viewModel.save(onSaved = onSaved) },
            enabled = !state.isSaving,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("meas_save_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(R.string.measurement_save))
          }
        }
      }
    }
  }
}

@Composable
private fun MeasurementTwoColumnRow(
  leftLabel: String,
  leftValue: String,
  leftTag: String,
  onLeftChange: (String) -> Unit,
  rightLabel: String,
  rightValue: String,
  rightTag: String,
  onRightChange: (String) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    InchInputField(
      label = leftLabel,
      value = leftValue,
      onValueChange = onLeftChange,
      modifier = Modifier
        .weight(1f)
        .testTag(leftTag),
    )
    InchInputField(
      label = rightLabel,
      value = rightValue,
      onValueChange = onRightChange,
      modifier = Modifier
        .weight(1f)
        .testTag(rightTag),
    )
  }
}

@Composable
private fun InchInputField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value = value,
    onValueChange = { raw ->
      val filtered = raw.filter { it.isDigit() || it == '.' }
      if (filtered.count { it == '.' } <= 1) {
        onValueChange(filtered)
      }
    },
    label = { Text(label) },
    suffix = { Text(stringResource(R.string.unit_in_short)) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = modifier,
  )
}
