package com.example.features.customers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.AtelierOliveDialogSurface
import com.example.ui.theme.OnAtelierOlive

@Composable
fun CustomerFormDialog(
  title: String,
  initialName: String = "",
  initialPhone: String = "",
  initialNotes: String = "",
  initialCustomId: String = "",
  errorMessage: String? = null,
  onDismiss: () -> Unit,
  onSave: (name: String, phone: String, notes: String, customId: String) -> Unit,
) {
  var name by rememberSaveable { mutableStateOf(initialName) }
  var phone by rememberSaveable { mutableStateOf(initialPhone) }
  var notes by rememberSaveable { mutableStateOf(initialNotes) }
  var customId by rememberSaveable { mutableStateOf(initialCustomId) }

  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val cardContainer = if (isDark) AtelierOliveDialogSurface else MaterialTheme.colorScheme.surfaceContainerHigh
  val cardContent = if (isDark) OnAtelierOlive else MaterialTheme.colorScheme.onSurface
  val fieldBorderColor = if (isDark) {
    OnAtelierOlive.copy(alpha = 0.28f)
  } else {
    MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
  }
  val placeholderColor = if (isDark) {
    OnAtelierOlive.copy(alpha = 0.68f)
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = cardContent,
    unfocusedTextColor = cardContent,
    focusedBorderColor = AtelierOlive,
    unfocusedBorderColor = fieldBorderColor,
    focusedPlaceholderColor = placeholderColor,
    unfocusedPlaceholderColor = placeholderColor,
    cursorColor = AtelierOlive,
  )

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = cardContainer,
      contentColor = cardContent,
      border = BorderStroke(
        width = 1.dp,
        color = if (isDark) OnAtelierOlive.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant,
      ),
      tonalElevation = 6.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .widthIn(max = 460.dp)
        .testTag("customer_form_dialog"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = cardContent,
            modifier = Modifier
              .weight(1f)
              .padding(end = 12.dp),
          )
          Surface(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            color = AtelierOlive,
            contentColor = OnAtelierOlive,
            modifier = Modifier
              .size(48.dp)
              .testTag("customer_form_close_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.customer_form_close_cd),
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          placeholder = { Text(stringResource(R.string.customer_form_name_hint)) },
          singleLine = true,
          shape = RoundedCornerShape(6.dp),
          colors = fieldColors,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_form_name_input"),
        )

        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          placeholder = { Text(stringResource(R.string.customer_form_phone_hint)) },
          singleLine = true,
          shape = RoundedCornerShape(6.dp),
          colors = fieldColors,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_form_phone_input"),
        )

        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          placeholder = { Text(stringResource(R.string.customer_form_address_hint)) },
          maxLines = 3,
          shape = RoundedCornerShape(6.dp),
          colors = fieldColors,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_form_notes_input"),
        )

        OutlinedTextField(
          value = customId,
          onValueChange = { customId = it },
          placeholder = { Text(stringResource(R.string.customer_form_custom_id_hint)) },
          singleLine = true,
          shape = RoundedCornerShape(6.dp),
          colors = fieldColors,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("customer_form_id_input"),
        )

        if (!errorMessage.isNullOrBlank()) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("customer_form_error"),
          )
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = AtelierOlive,
              contentColor = OnAtelierOlive,
            ),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("customer_form_cancel_button"),
          ) {
            Text(
              text = stringResource(R.string.action_cancel),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            )
          }

          Button(
            onClick = { onSave(name, phone, notes, customId) },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = AtelierOlive,
              contentColor = OnAtelierOlive,
            ),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("customer_form_save_button"),
          ) {
            Text(
              text = stringResource(R.string.customer_form_save),
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            )
          }
        }
      }
    }
  }
}

@Composable
fun DuplicateCustomerDialog(
  prompt: DuplicateCustomerPrompt,
  onOpenExisting: () -> Unit,
  onCreateSeparate: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.customer_duplicate_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(
            R.string.customer_duplicate_desc_primary,
            prompt.existingCustomer.name,
            prompt.existingCustomer.phone,
          ),
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          text = stringResource(R.string.customer_duplicate_desc_secondary),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onOpenExisting,
        colors = ButtonDefaults.buttonColors(
          containerColor = AtelierOlive,
          contentColor = OnAtelierOlive,
        ),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("duplicate_reuse_existing_button"),
      ) {
        Text(stringResource(R.string.customer_duplicate_reuse))
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onCreateSeparate,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("duplicate_create_separate_button"),
      ) {
        Text(stringResource(R.string.customer_duplicate_create_anyway))
      }
    },
  )
}
