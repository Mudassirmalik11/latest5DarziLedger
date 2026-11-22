package com.example.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MonoMetricStyle

enum class WindowWidthClass {
  Compact,
  Medium,
  Expanded,
}

fun widthClassFor(maxWidth: Dp): WindowWidthClass =
  when {
    maxWidth < 600.dp -> WindowWidthClass.Compact
    maxWidth < 840.dp -> WindowWidthClass.Medium
    else -> WindowWidthClass.Expanded
  }

@Composable
fun AtelierCard(
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = MaterialTheme.shapes.medium
  val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
  val colors = CardDefaults.cardColors(containerColor = containerColor)

  if (onClick != null) {
    Card(
      onClick = onClick,
      modifier = modifier,
      shape = shape,
      colors = colors,
      border = border,
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      content = content,
    )
  } else {
    Card(
      modifier = modifier,
      shape = shape,
      colors = colors,
      border = border,
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
      content = content,
    )
  }
}

@Composable
fun StatusPill(
  label: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  onClick: (() -> Unit)? = null,
) {
  val content: @Composable () -> Unit = {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
      )
    }
  }
  if (onClick != null) {
    Surface(
      onClick = onClick,
      modifier = modifier,
      shape = RoundedCornerShape(999.dp),
      color = containerColor,
      contentColor = contentColor,
      content = content,
    )
  } else {
    Surface(
      modifier = modifier,
      shape = RoundedCornerShape(999.dp),
      color = containerColor,
      contentColor = contentColor,
      content = content,
    )
  }
}

@Composable
fun MetricTile(
  label: String,
  value: String,
  icon: ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  AtelierCard(modifier = modifier) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = accentColor.copy(alpha = 0.14f),
          contentColor = accentColor,
        ) {
          Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
      Text(
        text = value,
        style = MonoMetricStyle,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
fun EmptyWorkspaceCard(
  icon: ImageVector,
  title: String,
  description: String,
  modifier: Modifier = Modifier,
) {
  AtelierCard(
    modifier = modifier.fillMaxWidth(),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier
            .padding(14.dp)
            .size(30.dp),
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 420.dp),
      )
    }
  }
}
