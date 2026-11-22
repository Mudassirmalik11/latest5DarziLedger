package com.example.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ui.AtelierCard
import com.example.core.ui.StatusPill
import com.example.core.ui.WindowWidthClass
import com.example.data.remote.DeviceRegistrationDoc
import com.example.data.remote.ShopCloudRepository

@Composable
fun DeviceSetupScaffold(
  state: DeviceGateState,
  widthClass: WindowWidthClass,
  isBusy: Boolean,
  actionError: String?,
  onRegisterDevice: (name: String, role: String) -> Unit,
  onRevokeDevice: (deviceId: String) -> Unit,
  onReauthorizeCurrent: (deviceId: String) -> Unit,
  onRetry: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("device_setup_screen"),
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(20.dp),
      contentAlignment = Alignment.Center,
    ) {
      when (state) {
        is DeviceGateState.Checking -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator()
            Text(
              text = stringResource(R.string.device_checking_auth),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        is DeviceGateState.NeedsRegistration -> {
          val defaultRole = if (widthClass == WindowWidthClass.Compact) "PHONE" else "TABLET"
          var deviceName by rememberSaveable(state.defaultName) { mutableStateOf(state.defaultName) }
          var selectedRole by rememberSaveable(defaultRole) { mutableStateOf(defaultRole) }

          AtelierCard(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 520.dp),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
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
                  text = stringResource(R.string.device_setup_title),
                  style = MaterialTheme.typography.titleLarge,
                )
              }

              Text(
                text = stringResource(R.string.device_setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text(stringResource(R.string.device_name_label)) },
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("device_name_input"),
              )

              Text(
                text = stringResource(R.string.device_role_label),
                style = MaterialTheme.typography.labelLarge,
              )

              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                  selected = selectedRole == "PHONE",
                  onClick = { selectedRole = "PHONE" },
                  label = { Text(stringResource(R.string.device_role_phone)) },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Filled.PhoneAndroid,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                  selected = selectedRole == "TABLET",
                  onClick = { selectedRole = "TABLET" },
                  label = { Text(stringResource(R.string.device_role_tablet)) },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Filled.TabletAndroid,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  modifier = Modifier.heightIn(min = 48.dp),
                )
              }

              if (!actionError.isNullOrBlank()) {
                ErrorBanner(actionError)
              }

              Button(
                onClick = { onRegisterDevice(deviceName, selectedRole) },
                enabled = !isBusy && deviceName.isNotBlank(),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 50.dp)
                  .testTag("authorize_device_button"),
              ) {
                if (isBusy) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                } else {
                  Text(stringResource(R.string.device_authorize_button))
                }
              }

              TextButton(
                onClick = onSignOut,
                modifier = Modifier
                  .align(Alignment.End)
                  .heightIn(min = 48.dp),
              ) {
                Text(stringResource(R.string.settings_sign_out))
              }
            }
          }
        }

        is DeviceGateState.DeviceLimitReached -> {
          AtelierCard(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 560.dp),
          ) {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              item {
                Text(
                  text = stringResource(R.string.device_limit_title),
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              item {
                Text(
                  text = stringResource(R.string.device_limit_desc),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (!actionError.isNullOrBlank()) {
                item { ErrorBanner(actionError) }
              }
              items(state.registeredDevices.size) { index ->
                val device = state.registeredDevices[index]
                DeviceRow(
                  device = device,
                  isCurrentDevice = device.id == state.localDevice.deviceId,
                  isBusy = isBusy,
                  onRevoke = { onRevokeDevice(device.id) },
                  onReauthorize = {},
                )
              }
              item {
                OutlinedButton(
                  onClick = onSignOut,
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                ) {
                  Text(stringResource(R.string.settings_sign_out))
                }
              }
            }
          }
        }

        is DeviceGateState.Revoked -> {
          AtelierCard(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 520.dp),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = stringResource(R.string.device_revoked_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
              )
              Text(
                text = stringResource(R.string.device_revoked_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (!actionError.isNullOrBlank()) {
                ErrorBanner(actionError)
              }
              val activeCount = state.registeredDevices.count {
                it.status == ShopCloudRepository.STATUS_AUTHORIZED
              }
              if (activeCount < ShopCloudRepository.MAX_ACTIVE_DEVICES_PER_USER) {
                Button(
                  onClick = { onReauthorizeCurrent(state.localDevice.deviceId) },
                  enabled = !isBusy,
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                ) {
                  Text(stringResource(R.string.device_reauthorize_action))
                }
              }
              OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp),
              ) {
                Text(stringResource(R.string.settings_sign_out))
              }
            }
          }
        }

        is DeviceGateState.StaffAccessRevoked -> {
          AtelierCard(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 520.dp)
              .testTag("staff_revoked_card"),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.ErrorOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                )
                Text(
                  text = stringResource(R.string.staff_revoked_title),
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              Text(
                text = stringResource(R.string.staff_revoked_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (state.email.isNotBlank()) {
                Text(
                  text = state.email,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
              OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("staff_revoked_sign_out_button"),
              ) {
                Text(stringResource(R.string.settings_sign_out))
              }
            }
          }
        }

        is DeviceGateState.Error -> {
          AtelierCard(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 480.dp),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.ErrorOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                )
                Text(
                  text = stringResource(R.string.device_verification_failed),
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedButton(
                  onClick = onSignOut,
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                ) {
                  Text(stringResource(R.string.settings_sign_out))
                }
                Button(
                  onClick = onRetry,
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                ) {
                  Text(stringResource(R.string.action_retry))
                }
              }
            }
          }
        }

        is DeviceGateState.Authorized -> Unit
      }
    }
  }
}

@Composable
fun DeviceRow(
  device: DeviceRegistrationDoc,
  isCurrentDevice: Boolean,
  isBusy: Boolean,
  onRevoke: () -> Unit,
  onReauthorize: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isAuthorized = device.status == ShopCloudRepository.STATUS_AUTHORIZED
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        val thisDeviceSuffix = if (isCurrentDevice) stringResource(R.string.device_this_device_suffix) else ""
        Text(
          text = device.deviceName + thisDeviceSuffix,
          style = MaterialTheme.typography.titleSmall,
        )
        val userRoleLabel = stringResource(
          if (device.userRole == "STAFF") R.string.device_user_role_staff else R.string.device_user_role_owner,
        )
        val deviceRoleLabel = when {
          device.deviceRole.equals("TABLET", ignoreCase = true) -> stringResource(R.string.device_role_tablet)
          device.deviceRole.equals("PHONE", ignoreCase = true) -> stringResource(R.string.device_role_phone)
          else -> device.deviceRole
        }
        val subtitle = buildString {
          if (device.userEmail.isNotBlank()) {
            append(device.userEmail)
            append(" • ")
          }
          append(userRoleLabel)
          append(" • ")
          append(deviceRoleLabel)
        }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        StatusPill(
          label = stringResource(
            if (isAuthorized) R.string.device_status_authorized else R.string.device_status_revoked,
          ),
          containerColor = if (isAuthorized) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.errorContainer
          },
          contentColor = if (isAuthorized) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onErrorContainer
          },
        )
        if (isAuthorized) {
          TextButton(
            onClick = onRevoke,
            enabled = !isBusy,
            modifier = Modifier.heightIn(min = 48.dp),
          ) {
            Text(stringResource(R.string.device_revoke_action))
          }
        } else {
          TextButton(
            onClick = onReauthorize,
            enabled = !isBusy,
            modifier = Modifier.heightIn(min = 48.dp),
          ) {
            Text(stringResource(R.string.device_reauthorize_action))
          }
        }
      }
    }
  }
}

@Composable
private fun ErrorBanner(message: String) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.errorContainer,
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onErrorContainer,
      modifier = Modifier.padding(12.dp),
    )
  }
}
