package com.example.features.sync

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.model.SyncState
import com.example.core.ui.AtelierCard
import com.example.core.ui.DateFormatUtils
import com.example.core.ui.StatusPill
import com.example.data.local.entities.OrderNumberReservationEntity
import com.example.data.local.entities.SyncConflictEntity
import com.example.data.sync.ConflictResolutionChoice
import com.example.data.sync.ConflictResolver
import com.example.ui.theme.MonoMetricStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
  viewModel: SyncStatusViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
  val syncState by viewModel.syncState.collectAsStateWithLifecycle()
  val lastSyncError by viewModel.lastSyncError.collectAsStateWithLifecycle()
  val pendingCount by viewModel.pendingOutboxCount.collectAsStateWithLifecycle()
  val conflicts by viewModel.unresolvedConflicts.collectAsStateWithLifecycle()
  val metadata by viewModel.syncMetadata.collectAsStateWithLifecycle()
  val reservations by viewModel.reservations.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(actionMessage) {
    val msg = actionMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearMessage()
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("sync_status_screen"),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.sync_screen_title),
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("sync_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .testTag("sync_status_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // Overview & Actions Card
          AtelierCard {
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
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = when {
                      !isOnline -> Icons.Filled.CloudOff
                      syncState == SyncState.SYNCING -> Icons.Filled.CloudSync
                      conflicts.isNotEmpty() || syncState == SyncState.ERROR -> Icons.Filled.WarningAmber
                      else -> Icons.Filled.CloudDone
                    },
                    contentDescription = null,
                    tint = if (!isOnline || conflicts.isNotEmpty()) {
                      MaterialTheme.colorScheme.error
                    } else {
                      MaterialTheme.colorScheme.primary
                    },
                  )
                  Text(
                    text = stringResource(
                      if (isOnline) R.string.sync_network_online else R.string.sync_network_offline,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                  )
                }

                val statusLabel = when {
                  conflicts.isNotEmpty() -> stringResource(R.string.sync_status_conflict, conflicts.size)
                  !isOnline -> stringResource(R.string.sync_status_offline)
                  syncState == SyncState.SYNCING -> stringResource(R.string.sync_status_syncing)
                  pendingCount > 0 -> stringResource(R.string.sync_status_pending, pendingCount)
                  else -> stringResource(R.string.sync_status_synced)
                }
                StatusPill(
                  label = statusLabel,
                  containerColor = if (conflicts.isNotEmpty()) {
                    MaterialTheme.colorScheme.errorContainer
                  } else {
                    MaterialTheme.colorScheme.primaryContainer
                  },
                  contentColor = if (conflicts.isNotEmpty()) {
                    MaterialTheme.colorScheme.onErrorContainer
                  } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  },
                  modifier = Modifier.testTag("sync_overview_pill"),
                )
              }

              Text(
                text = stringResource(R.string.sync_pending_local_changes, pendingCount),
                style = MaterialTheme.typography.bodyMedium,
              )

              val lastSyncText = metadata?.lastSuccessfulSyncAt?.let {
                stringResource(R.string.sync_last_synced_at, DateFormatUtils.formatDateTime(it))
              } ?: stringResource(R.string.sync_last_synced_never)
              Text(
                text = lastSyncText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              val errorText = lastSyncError ?: metadata?.lastError
              if (!errorText.isNullOrBlank()) {
                Text(
                  text = errorText,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = viewModel::syncNow,
                  enabled = !isBusy && isOnline,
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("sync_now_button"),
                ) {
                  if (isBusy) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(18.dp),
                      strokeWidth = 2.dp,
                      color = MaterialTheme.colorScheme.onPrimary,
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Filled.Sync,
                      contentDescription = null,
                      modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(stringResource(R.string.sync_now_button))
                  }
                }

                OutlinedButton(
                  onClick = viewModel::reserveBlock,
                  enabled = !isBusy && isOnline,
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("reserve_block_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.ConfirmationNumber,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                  )
                  Text(stringResource(R.string.sync_reserve_block_button))
                }
              }
            }
          }

          // Order Number Reservation Blocks Card
          AtelierCard {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = stringResource(R.string.sync_reservations_section),
                style = MaterialTheme.typography.titleMedium,
              )
              if (reservations.isEmpty()) {
                Text(
                  text = stringResource(R.string.sync_reservations_empty),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                reservations.forEach { block ->
                  ReservationBlockRow(block = block)
                }
              }
            }
          }

          // Conflicts Header
          Text(
            text = stringResource(R.string.sync_conflicts_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp),
          )
        }
      }

      if (conflicts.isEmpty()) {
        item {
          AtelierCard(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 720.dp),
          ) {
            Text(
              text = stringResource(R.string.sync_conflicts_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .padding(16.dp)
                .testTag("sync_no_conflicts_text"),
            )
          }
        }
      } else {
        items(conflicts, key = { it.conflictId }) { conflict ->
          ConflictCard(
            conflict = conflict,
            isBusy = isBusy,
            onKeepLocal = {
              viewModel.resolveConflict(conflict.conflictId, ConflictResolutionChoice.KEEP_LOCAL)
            },
            onUseCloud = {
              viewModel.resolveConflict(conflict.conflictId, ConflictResolutionChoice.USE_CLOUD)
            },
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 720.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ReservationBlockRow(block: OrderNumberReservationEntity) {
  val remaining = if (block.isExhausted) 0 else (block.endNumber - block.nextAvailableNumber + 1).coerceAtLeast(0)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column {
      Text(
        text = "#${block.startNumber} – #${block.endNumber}",
        style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
      )
      Text(
        text = if (block.isExhausted) {
          stringResource(R.string.sync_block_exhausted)
        } else {
          stringResource(R.string.sync_block_next_available, block.nextAvailableNumber)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    StatusPill(
      label = stringResource(R.string.sync_block_remaining, remaining),
      containerColor = if (block.isExhausted) {
        MaterialTheme.colorScheme.surfaceVariant
      } else {
        MaterialTheme.colorScheme.secondaryContainer
      },
      contentColor = if (block.isExhausted) {
        MaterialTheme.colorScheme.onSurfaceVariant
      } else {
        MaterialTheme.colorScheme.onSecondaryContainer
      },
    )
  }
}

@Composable
private fun ConflictCard(
  conflict: SyncConflictEntity,
  isBusy: Boolean,
  onKeepLocal: () -> Unit,
  onUseCloud: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (localSummary, cloudSummary) = remember(conflict) {
    ConflictResolver.describeConflict(conflict)
  }

  AtelierCard(
    modifier = modifier.testTag("conflict_card_${conflict.conflictId}"),
    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val entityLabel = stringResource(
          when (conflict.entityType) {
            com.example.core.model.SyncEntityType.SHOP -> R.string.sync_entity_shop
            com.example.core.model.SyncEntityType.CUSTOMER -> R.string.sync_entity_customer
            com.example.core.model.SyncEntityType.MEASUREMENT -> R.string.sync_entity_measurement
            com.example.core.model.SyncEntityType.ORDER -> R.string.sync_entity_order
            com.example.core.model.SyncEntityType.PAYMENT -> R.string.sync_entity_payment
            com.example.core.model.SyncEntityType.DEVICE_CONFIG -> R.string.sync_entity_device
            com.example.core.model.SyncEntityType.ORDER_RESERVATION -> R.string.sync_entity_reservation
          },
        )
        Text(
          text = stringResource(R.string.sync_conflict_title, entityLabel),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.error,
        )
        Text(
          text = DateFormatUtils.formatDateTime(conflict.detectedAt),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(text = localSummary, style = MaterialTheme.typography.bodyMedium)
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Text(text = cloudSummary, style = MaterialTheme.typography.bodyMedium)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onKeepLocal,
          enabled = !isBusy,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("conflict_keep_local_${conflict.conflictId}"),
        ) {
          Text(stringResource(R.string.sync_keep_local))
        }
        OutlinedButton(
          onClick = onUseCloud,
          enabled = !isBusy,
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("conflict_use_cloud_${conflict.conflictId}"),
        ) {
          Text(stringResource(R.string.sync_use_cloud))
        }
      }
    }
  }
}
