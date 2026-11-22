package com.example.features.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.ThemeMode
import com.example.core.ui.AtelierCard
import com.example.core.ui.DateFormatUtils
import com.example.core.ui.StatusPill
import com.example.data.remote.ShopCloudRepository
import com.example.data.remote.ShopStaffUserDoc
import com.example.features.auth.DeviceRow
import com.example.features.printing.AndroidBluetoothPrinterClient
import com.example.features.printing.DiscoveredPrinter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
  onOpenSyncStatus: () -> Unit = {},
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val prefs by viewModel.preferences.collectAsStateWithLifecycle()
  val defaultPrinter by viewModel.defaultPrinter.collectAsStateWithLifecycle()
  val availablePrinters by viewModel.availablePrinters.collectAsStateWithLifecycle()
  val isScanningPrinters by viewModel.isScanningPrinters.collectAsStateWithLifecycle()
  val isPrintBusy by viewModel.isPrintBusy.collectAsStateWithLifecycle()
  val printerError by viewModel.printerError.collectAsStateWithLifecycle()
  val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
  val backupError by viewModel.backupError.collectAsStateWithLifecycle()
  val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
  val localDevice by viewModel.localDevice.collectAsStateWithLifecycle()
  val devicesState by viewModel.registeredDevicesState.collectAsStateWithLifecycle()
  val staffUsersState by viewModel.staffUsersState.collectAsStateWithLifecycle()
  val isCreatingStaffUser by viewModel.isCreatingStaffUser.collectAsStateWithLifecycle()
  val staffActionError by viewModel.staffActionError.collectAsStateWithLifecycle()
  val createdStaffCredentials by viewModel.createdStaffCredentials.collectAsStateWithLifecycle()
  val savedCount by viewModel.savedMessageCounter.collectAsStateWithLifecycle()
  val deviceActionMessage by viewModel.deviceActionMessage.collectAsStateWithLifecycle()
  val savedText = stringResource(R.string.settings_saved_feedback)
  val permissionMissingText = stringResource(R.string.printer_permission_required)

  var shopNameInput by rememberSaveable(prefs.shopName) { mutableStateOf(prefs.shopName) }
  var shopContactInput by rememberSaveable(prefs.shopContact) { mutableStateOf(prefs.shopContact) }
  var showPrinterPicker by rememberSaveable { mutableStateOf(false) }
  var showAddStaffDialog by rememberSaveable { mutableStateOf(false) }

  val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { results ->
    val connectGranted = results[android.Manifest.permission.BLUETOOTH_CONNECT]
      ?: viewModel.hasBluetoothConnectPermission()
    if (connectGranted) {
      viewModel.loadPairedPrinters()
      showPrinterPicker = true
    } else {
      onShowMessage(permissionMissingText)
    }
  }

  val exportBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json"),
  ) { uri ->
    if (uri != null) {
      viewModel.exportBackupToUri(context, uri)
    }
  }

  val restoreBackupLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      viewModel.inspectBackupFromUri(context, uri)
    }
  }

  LaunchedEffect(savedCount) {
    if (savedCount > 0) {
      onShowMessage(savedText)
    }
  }

  LaunchedEffect(createdStaffCredentials) {
    if (createdStaffCredentials != null) {
      showAddStaffDialog = false
    }
  }

  LaunchedEffect(deviceActionMessage) {
    val msg = deviceActionMessage
    if (!msg.isNullOrBlank()) {
      onShowMessage(msg)
      viewModel.clearDeviceMessage()
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("settings_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item {
      // Shop Account & 2-Device Authorization Card
      AtelierCard(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 720.dp),
      ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.settings_account_section),
                style = MaterialTheme.typography.titleMedium,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = viewModel.signedInUser.displayName,
                  style = MaterialTheme.typography.titleSmall,
                )
                if (viewModel.signedInUser.email.isNotBlank()) {
                  Text(
                    text = viewModel.signedInUser.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                StatusPill(
                  label = stringResource(
                    if (viewModel.isOwner) R.string.role_owner_badge else R.string.role_staff_badge,
                  ),
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.testTag("settings_user_role_pill"),
                )
                val deviceStatusRes = when (localDevice?.registrationStatus) {
                  com.example.core.model.DeviceRegistrationStatus.UNREGISTERED -> R.string.settings_device_status_unregistered
                  com.example.core.model.DeviceRegistrationStatus.PENDING -> R.string.settings_device_status_pending
                  com.example.core.model.DeviceRegistrationStatus.AUTHORIZED -> R.string.settings_device_status_authorized
                  com.example.core.model.DeviceRegistrationStatus.REVOKED -> R.string.settings_device_status_revoked
                  null -> R.string.settings_device_status_checking
                }
                StatusPill(
                  label = stringResource(deviceStatusRes),
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }

            Text(
              text = stringResource(R.string.settings_single_device_policy),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when (val state = devicesState) {
              is DevicesUiState.Loading -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                  Text(stringResource(R.string.settings_devices_loading), style = MaterialTheme.typography.bodySmall)
                }
              }

              is DevicesUiState.Error -> {
                Text(
                  text = state.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              is DevicesUiState.Success -> {
                if (state.devices.isEmpty()) {
                  Text(
                    text = stringResource(R.string.settings_devices_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                } else {
                  state.devices.forEach { device ->
                    DeviceRow(
                      device = device,
                      isCurrentDevice = device.id == localDevice?.deviceId,
                      isBusy = false,
                      onRevoke = { viewModel.revokeDevice(device.id) },
                      onReauthorize = { viewModel.reauthorizeDevice(device.id) },
                    )
                  }
                }
              }
            }

            OutlinedButton(
              onClick = { viewModel.signOut(context) },
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("settings_sign_out_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
              )
              Text(stringResource(R.string.settings_sign_out))
            }
          }
        }
      }

      if (viewModel.isOwner) {
        item {
          val devicesList = (devicesState as? DevicesUiState.Success)?.devices.orEmpty()
          ShopUsersManagementCard(
            staffUsersState = staffUsersState,
            devices = devicesList,
            onAddUserClick = {
              viewModel.clearStaffActionError()
              showAddStaffDialog = true
            },
            onDisableUser = viewModel::revokeStaffUser,
            onReenableUser = viewModel::reactivateStaffUser,
            onRevokeUserDevice = viewModel::revokeDevice,
          )
        }
      }

      item {
        // Cloud Sync & Conflicts Shortcut Card
        AtelierCard(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.settings_sync_center_title),
                style = MaterialTheme.typography.titleMedium,
              )
            }
            Text(
              text = stringResource(R.string.settings_sync_center_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = onOpenSyncStatus,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("settings_open_sync_button"),
            ) {
              Text(stringResource(R.string.settings_open_sync_center))
            }
          }
        }
      }

      item {
        // Shop Profile Card
        AtelierCard(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.settings_shop_section),
                style = MaterialTheme.typography.titleMedium,
              )
            }

            OutlinedTextField(
              value = shopNameInput,
              onValueChange = { shopNameInput = it },
              label = { Text(stringResource(R.string.settings_shop_name_label)) },
              enabled = viewModel.isOwner,
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_shop_name_input"),
            )

            OutlinedTextField(
              value = shopContactInput,
              onValueChange = { shopContactInput = it },
              label = { Text(stringResource(R.string.settings_shop_contact_label)) },
              placeholder = { Text("0300-1234567") },
              enabled = viewModel.isOwner,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_shop_contact_input"),
            )

            if (viewModel.isOwner) {
              Button(
                onClick = { viewModel.saveShopProfile(shopNameInput, shopContactInput) },
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("settings_save_shop_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Check,
                  contentDescription = null,
                  modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.settings_save_shop))
              }
            } else {
              Text(
                text = stringResource(R.string.settings_shop_owner_only_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      item {
        // Printer Preferences Card
        AtelierCard(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Print,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.settings_printer_section),
                style = MaterialTheme.typography.titleMedium,
              )
            }

            Text(
              text = defaultPrinter?.let {
                stringResource(R.string.printer_configured_label, it.printerName, it.printerAddress)
              } ?: stringResource(R.string.printer_none_configured),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("settings_configured_printer_text"),
            )

            if (!printerError.isNullOrBlank()) {
              Text(
                text = printerError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("settings_printer_error_text"),
              )
            }

            Text(
              text = stringResource(R.string.settings_paper_width_label),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              FilterChip(
                selected = prefs.paperWidthMm == 58,
                onClick = { viewModel.setPaperWidth(58) },
                label = { Text(stringResource(R.string.settings_paper_58mm)) },
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("settings_paper_58mm"),
              )
              FilterChip(
                selected = prefs.paperWidthMm == 80,
                onClick = { viewModel.setPaperWidth(80) },
                label = { Text(stringResource(R.string.settings_paper_80mm)) },
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("settings_paper_80mm"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = {
                  viewModel.clearPrinterError()
                  val required = AndroidBluetoothPrinterClient.requiredRuntimePermissions()
                  if (required.isEmpty() || viewModel.hasBluetoothConnectPermission()) {
                    viewModel.loadPairedPrinters()
                    showPrinterPicker = true
                  } else {
                    bluetoothPermissionLauncher.launch(required)
                  }
                },
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("settings_select_printer_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Bluetooth,
                  contentDescription = null,
                  modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp),
                )
                Text(stringResource(R.string.printer_select_button))
              }

              OutlinedButton(
                onClick = viewModel::printTestSlip,
                enabled = defaultPrinter != null && !isPrintBusy,
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("settings_test_print_button"),
              ) {
                if (isPrintBusy) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                  )
                } else {
                  Text(stringResource(R.string.printer_test_print_button))
                }
              }
            }
          }
        }
      }

      item {
        // Appearance & Language Card
        AtelierCard(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.settings_appearance_section),
                style = MaterialTheme.typography.titleMedium,
              )
            }

            val themeOptions = listOf(
              ThemeMode.SYSTEM to R.string.settings_theme_system,
              ThemeMode.LIGHT to R.string.settings_theme_light,
              ThemeMode.DARK to R.string.settings_theme_dark,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              themeOptions.forEachIndexed { index, (mode, labelRes) ->
                SegmentedButton(
                  selected = prefs.themeMode == mode,
                  onClick = { viewModel.setThemeMode(mode) },
                  shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = themeOptions.size,
                  ),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("settings_theme_${mode.name.lowercase()}"),
                ) {
                  Text(stringResource(labelRes))
                }
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
              text = stringResource(R.string.settings_language_label),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val languageOptions = listOf(
              AppLanguage.ENGLISH to R.string.settings_language_english,
              AppLanguage.URDU to R.string.settings_language_urdu,
            )
            val selectedLanguage = if (viewModel.isOwner) prefs.language else AppLanguage.URDU

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              languageOptions.forEachIndexed { index, (lang, labelRes) ->
                SegmentedButton(
                  selected = selectedLanguage == lang,
                  enabled = viewModel.isOwner,
                  onClick = {
                    if (viewModel.isOwner) {
                      viewModel.setLanguage(lang)
                    }
                  },
                  shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = languageOptions.size,
                  ),
                  colors = SegmentedButtonDefaults.colors(
                    disabledActiveContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    disabledActiveContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                    disabledActiveBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    disabledInactiveContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    disabledInactiveContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                    disabledInactiveBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                  ),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("settings_language_${lang.name.lowercase()}"),
                ) {
                  Text(stringResource(labelRes))
                }
              }
            }

            Text(
              text = stringResource(
                if (viewModel.isOwner) {
                  R.string.settings_language_hint
                } else {
                  R.string.settings_language_owner_only_hint
                },
              ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("settings_language_hint_text"),
            )

            Text(
              text = stringResource(R.string.settings_currency_unit),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item {
        // Local JSON Backup & Restore Card (Zero-permission Storage Access Framework)
        AtelierCard(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Backup,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.settings_backup_section),
                style = MaterialTheme.typography.titleMedium,
              )
            }

            Text(
              text = stringResource(R.string.settings_backup_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!backupError.isNullOrBlank()) {
              Text(
                text = backupError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("settings_backup_error_text"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = {
                  viewModel.clearBackupError()
                  val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                  exportBackupLauncher.launch("darzi_ledger_backup_$stamp.json")
                },
                enabled = !isBackupBusy,
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("settings_export_backup_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Download,
                  contentDescription = null,
                  modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp),
                )
                Text(stringResource(R.string.settings_export_backup))
              }

              OutlinedButton(
                onClick = {
                  viewModel.clearBackupError()
                  restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                enabled = !isBackupBusy,
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("settings_restore_backup_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Restore,
                  contentDescription = null,
                  modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp),
                )
                Text(stringResource(R.string.settings_restore_backup))
              }
            }
          }
        }
      }
    }

  val activeRestore = pendingRestore
  if (activeRestore != null) {
    val summary = activeRestore.summary
    AlertDialog(
      onDismissRequest = viewModel::dismissPendingRestore,
      modifier = Modifier.testTag("restore_confirm_dialog"),
      title = { Text(stringResource(R.string.backup_restore_dialog_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.backup_restore_shop_name, summary.shopName),
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = stringResource(
              R.string.backup_restore_exported_at,
              DateFormatUtils.formatDateTime(summary.exportedAtMillis),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Text(
            text = stringResource(
              R.string.backup_restore_counts,
              summary.customerCount,
              summary.measurementCount,
              summary.orderCount,
              summary.paymentCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            text = stringResource(R.string.backup_restore_safety_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = viewModel::confirmPendingRestore,
          enabled = !isBackupBusy,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("restore_confirm_button"),
        ) {
          Text(stringResource(R.string.backup_restore_confirm_button))
        }
      },
      dismissButton = {
        TextButton(
          onClick = viewModel::dismissPendingRestore,
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }

  if (showPrinterPicker) {
    PrinterPickerDialog(
      printers = availablePrinters,
      selectedAddress = defaultPrinter?.printerAddress,
      isScanning = isScanningPrinters,
      errorMessage = printerError,
      onScanNearby = viewModel::startNearbyScan,
      onOpenBluetoothSettings = {
        runCatching {
          context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
      },
      onSelectPrinter = { printer ->
        viewModel.stopNearbyScan()
        viewModel.selectPrinter(printer)
        showPrinterPicker = false
      },
      onDismiss = {
        viewModel.stopNearbyScan()
        showPrinterPicker = false
      },
    )
  }

  if (showAddStaffDialog) {
    AddStaffUserDialog(
      isBusy = isCreatingStaffUser,
      errorMessage = staffActionError,
      onDismiss = {
        if (!isCreatingStaffUser) {
          viewModel.clearStaffActionError()
          showAddStaffDialog = false
        }
      },
      onCreate = { name, email, password, confirmPassword ->
        viewModel.createStaffUser(name, email, password, confirmPassword)
      },
    )
  }

  val credentials = createdStaffCredentials
  if (credentials != null) {
    val copyText = stringResource(
      R.string.staff_credentials_copy_payload,
      credentials.displayName,
      credentials.email,
      credentials.password,
    )
    val copiedToast = stringResource(R.string.staff_credentials_copied)
    AlertDialog(
      onDismissRequest = viewModel::dismissCreatedCredentials,
      modifier = Modifier.testTag("staff_credentials_dialog"),
      title = { Text(stringResource(R.string.staff_credentials_dialog_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.staff_credentials_dialog_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = credentials.displayName,
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = stringResource(R.string.staff_credentials_email, credentials.email),
                style = MaterialTheme.typography.bodyMedium,
              )
              Text(
                text = stringResource(R.string.staff_credentials_password, credentials.password),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = viewModel::dismissCreatedCredentials,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("staff_credentials_done_button"),
        ) {
          Text(stringResource(R.string.action_close))
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = {
            clipboardManager.setText(AnnotatedString(copyText))
            onShowMessage(copiedToast)
          },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("staff_credentials_copy_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier
              .padding(end = 6.dp)
              .size(16.dp),
          )
          Text(stringResource(R.string.staff_credentials_copy_button))
        }
      },
    )
  }
}

@Composable
private fun ShopUsersManagementCard(
  staffUsersState: StaffUsersUiState,
  devices: List<com.example.data.remote.DeviceRegistrationDoc>,
  onAddUserClick: () -> Unit,
  onDisableUser: (String) -> Unit,
  onReenableUser: (String) -> Unit,
  onRevokeUserDevice: (String) -> Unit,
) {
  AtelierCard(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 720.dp)
      .testTag("settings_shop_users_card"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Filled.Group,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = stringResource(R.string.settings_shop_users_section),
            style = MaterialTheme.typography.titleMedium,
          )
        }
        val activeCount = (staffUsersState as? StaffUsersUiState.Success)?.users?.count {
          it.status == ShopCloudRepository.STATUS_STAFF_ACTIVE
        } ?: 0
        StatusPill(
          label = stringResource(R.string.settings_shop_users_count_pill, activeCount, ShopCloudRepository.MAX_STAFF_USERS),
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.testTag("settings_shop_users_count_pill"),
        )
      }

      Text(
        text = stringResource(R.string.settings_shop_users_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      when (staffUsersState) {
        is StaffUsersUiState.Loading -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
              text = stringResource(R.string.settings_shop_users_loading),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }

        is StaffUsersUiState.Error -> {
          Text(
            text = staffUsersState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        is StaffUsersUiState.Success -> {
          val users = staffUsersState.users
          val activeCount = users.count { it.status == ShopCloudRepository.STATUS_STAFF_ACTIVE }
          if (users.isEmpty()) {
            Text(
              text = stringResource(R.string.settings_shop_users_empty),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            users.forEach { staff ->
              val activeDevice = devices.firstOrNull {
                it.userUid == staff.staffUid && it.status == ShopCloudRepository.STATUS_AUTHORIZED
              }
              StaffUserRow(
                staff = staff,
                activeDeviceName = activeDevice?.deviceName,
                canReenable = activeCount < ShopCloudRepository.MAX_STAFF_USERS,
                onDisable = { onDisableUser(staff.staffUid) },
                onReenable = { onReenableUser(staff.staffUid) },
                onRevokeDevice = {
                  if (activeDevice != null) onRevokeUserDevice(activeDevice.id)
                },
              )
            }
          }

          Button(
            onClick = onAddUserClick,
            enabled = activeCount < ShopCloudRepository.MAX_STAFF_USERS,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("settings_add_staff_user_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.PersonAdd,
              contentDescription = null,
              modifier = Modifier
                .padding(end = 8.dp)
                .size(18.dp),
            )
            Text(
              stringResource(
                if (activeCount >= ShopCloudRepository.MAX_STAFF_USERS) {
                  R.string.settings_add_staff_user_limit_reached
                } else {
                  R.string.settings_add_staff_user
                },
              ),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StaffUserRow(
  staff: ShopStaffUserDoc,
  activeDeviceName: String?,
  canReenable: Boolean,
  onDisable: () -> Unit,
  onReenable: () -> Unit,
  onRevokeDevice: () -> Unit,
) {
  val isActive = staff.status == ShopCloudRepository.STATUS_STAFF_ACTIVE
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("staff_user_row_${staff.staffUid}"),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = staff.displayName,
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = staff.email,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusPill(
          label = stringResource(
            if (isActive) R.string.staff_status_active else R.string.staff_status_disabled,
          ),
          containerColor = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.errorContainer
          },
          contentColor = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onErrorContainer
          },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (!activeDeviceName.isNullOrBlank()) {
            stringResource(R.string.staff_active_device_label, activeDeviceName)
          } else {
            stringResource(R.string.staff_no_active_device)
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (!activeDeviceName.isNullOrBlank()) {
            TextButton(
              onClick = onRevokeDevice,
              modifier = Modifier.heightIn(min = 48.dp),
            ) {
              Text(stringResource(R.string.device_revoke_action))
            }
          }
          if (isActive) {
            TextButton(
              onClick = onDisable,
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("staff_disable_button_${staff.staffUid}"),
            ) {
              Text(stringResource(R.string.staff_action_disable))
            }
          } else {
            TextButton(
              onClick = onReenable,
              enabled = canReenable,
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("staff_reenable_button_${staff.staffUid}"),
            ) {
              Text(stringResource(R.string.staff_action_reenable))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AddStaffUserDialog(
  isBusy: Boolean,
  errorMessage: String?,
  onDismiss: () -> Unit,
  onCreate: (name: String, email: String, password: String, confirmPassword: String) -> Unit,
) {
  var name by rememberSaveable { mutableStateOf("") }
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var confirmPassword by rememberSaveable { mutableStateOf("") }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("add_staff_user_dialog"),
    title = { Text(stringResource(R.string.staff_add_dialog_title)) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = stringResource(R.string.staff_add_dialog_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.staff_name_label)) },
          singleLine = true,
          enabled = !isBusy,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_name_input"),
        )

        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          label = { Text(stringResource(R.string.auth_email_label)) },
          singleLine = true,
          enabled = !isBusy,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_email_input"),
        )

        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text(stringResource(R.string.auth_password_label)) },
          singleLine = true,
          enabled = !isBusy,
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(
              onClick = { passwordVisible = !passwordVisible },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = stringResource(
                  if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                ),
              )
            }
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_password_input"),
        )

        OutlinedTextField(
          value = confirmPassword,
          onValueChange = { confirmPassword = it },
          label = { Text(stringResource(R.string.auth_confirm_password_label)) },
          singleLine = true,
          enabled = !isBusy,
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_confirm_password_input"),
        )

        if (!errorMessage.isNullOrBlank()) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
          ) {
            Text(
              text = errorMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier
                .padding(10.dp)
                .testTag("staff_dialog_error"),
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onCreate(name, email, password, confirmPassword) },
        enabled = !isBusy,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("staff_create_confirm_button"),
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Text(stringResource(R.string.staff_create_action))
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !isBusy,
        modifier = Modifier.heightIn(min = 48.dp),
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}

@Composable
private fun PrinterPickerDialog(
  printers: List<DiscoveredPrinter>,
  selectedAddress: String?,
  isScanning: Boolean,
  errorMessage: String?,
  onScanNearby: () -> Unit,
  onOpenBluetoothSettings: () -> Unit,
  onSelectPrinter: (DiscoveredPrinter) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("printer_picker_dialog"),
    title = { Text(stringResource(R.string.printer_picker_title)) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (!errorMessage.isNullOrBlank()) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }

        if (printers.isEmpty()) {
          Text(
            text = stringResource(R.string.printer_none_found_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          printers.forEach { printer ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { onSelectPrinter(printer) }
                .padding(vertical = 8.dp)
                .testTag("printer_item_${printer.address}"),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = printer.name,
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = printer.address,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              StatusPill(
                label = stringResource(
                  when {
                    printer.address == selectedAddress -> R.string.printer_pill_selected
                    printer.isBonded -> R.string.printer_pill_paired
                    else -> R.string.printer_pill_nearby
                  },
                ),
                containerColor = if (printer.address == selectedAddress) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (printer.address == selectedAddress) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onSecondaryContainer
                },
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onScanNearby,
            enabled = !isScanning,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp),
          ) {
            if (isScanning) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier
                  .padding(end = 4.dp)
                  .size(16.dp),
              )
              Text(stringResource(R.string.printer_scan_nearby))
            }
          }

          TextButton(
            onClick = onOpenBluetoothSettings,
            modifier = Modifier.heightIn(min = 48.dp),
          ) {
            Text(stringResource(R.string.printer_open_bt_settings))
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp),
      ) {
        Text(stringResource(R.string.action_close))
      }
    },
  )
}
