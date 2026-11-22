package com.example.features.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ui.AtelierCard
import com.example.core.ui.PkrFormatUtils
import com.example.core.ui.StatusPill
import com.example.ui.theme.BrassSecondary
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.PinePrimary
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

@Composable
fun DashboardAnalyticsCard(
  analytics: PeriodAnalytics,
  isWide: Boolean,
  onSelectPeriod: (AnalyticsPeriod) -> Unit,
  modifier: Modifier = Modifier,
) {
  val periodBadgeRes = when (analytics.period) {
    AnalyticsPeriod.DAILY -> R.string.analytics_period_today
    AnalyticsPeriod.WEEKLY -> R.string.analytics_period_week
    AnalyticsPeriod.MONTHLY -> R.string.analytics_period_month
    AnalyticsPeriod.YEARLY -> R.string.analytics_period_year
  }

  AtelierCard(
    modifier = modifier
      .fillMaxWidth()
      .testTag("dashboard_analytics_card"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
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
            imageVector = Icons.Filled.BarChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = stringResource(R.string.analytics_section_title),
            style = MaterialTheme.typography.titleMedium,
          )
        }

        StatusPill(
          label = stringResource(periodBadgeRes),
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      val periodOptions = listOf(
        AnalyticsPeriod.DAILY to R.string.analytics_daily,
        AnalyticsPeriod.WEEKLY to R.string.analytics_weekly,
        AnalyticsPeriod.MONTHLY to R.string.analytics_monthly,
        AnalyticsPeriod.YEARLY to R.string.analytics_yearly,
      )

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        periodOptions.forEachIndexed { index, (period, labelRes) ->
          SegmentedButton(
            selected = analytics.period == period,
            onClick = { onSelectPeriod(period) },
            shape = SegmentedButtonDefaults.itemShape(
              index = index,
              count = periodOptions.size,
            ),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("analytics_tab_${period.name.lowercase()}"),
          ) {
            Text(
              text = stringResource(labelRes),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      if (isWide) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          AnalyticsStatBox(
            label = stringResource(R.string.analytics_collected_label),
            value = PkrFormatUtils.formatPkr(analytics.collectedPkr),
            accentColor = PinePrimary,
            modifier = Modifier
              .weight(1f)
              .testTag("analytics_collected_value"),
          )
          AnalyticsStatBox(
            label = stringResource(R.string.analytics_booked_label),
            value = PkrFormatUtils.formatPkr(analytics.bookedSalesPkr),
            accentColor = BrassSecondary,
            modifier = Modifier
              .weight(1f)
              .testTag("analytics_booked_value"),
          )
          AnalyticsStatBox(
            label = stringResource(R.string.analytics_orders_label),
            value = "${analytics.ordersCount}",
            subtitle = stringResource(R.string.analytics_delivered_count, analytics.deliveredCount),
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
              .weight(1f)
              .testTag("analytics_orders_value"),
          )
          AnalyticsStatBox(
            label = stringResource(R.string.analytics_suits_label),
            value = "${analytics.suitsCount}",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .weight(1f)
              .testTag("analytics_suits_value"),
          )
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          AnalyticsStatBox(
            label = stringResource(R.string.analytics_collected_label),
            value = PkrFormatUtils.formatPkr(analytics.collectedPkr),
            accentColor = PinePrimary,
            modifier = Modifier
              .weight(1f)
              .testTag("analytics_collected_value"),
          )
          AnalyticsStatBox(
            label = stringResource(R.string.analytics_booked_label),
            value = PkrFormatUtils.formatPkr(analytics.bookedSalesPkr),
            accentColor = BrassSecondary,
            modifier = Modifier
              .weight(1f)
              .testTag("analytics_booked_value"),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          AnalyticsStatBox(
            label = stringResource(R.string.analytics_orders_label),
            value = "${analytics.ordersCount}",
            subtitle = stringResource(R.string.analytics_delivered_count, analytics.deliveredCount),
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
              .weight(1f)
              .testTag("analytics_orders_value"),
          )
          AnalyticsStatBox(
            label = stringResource(R.string.analytics_suits_label),
            value = "${analytics.suitsCount}",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .weight(1f)
              .testTag("analytics_suits_value"),
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.analytics_trend_title),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LegendDot(color = PinePrimary, label = stringResource(R.string.analytics_collected_label))
          LegendDot(color = BrassSecondary.copy(alpha = 0.7f), label = stringResource(R.string.analytics_booked_label))
        }
      }

      val hasActivity = analytics.buckets.any { it.collectedPkr > 0L || it.bookedSalesPkr > 0L }
      if (!hasActivity) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          Text(
            text = stringResource(R.string.analytics_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
          )
        }
      } else {
        AnalyticsBarChart(
          buckets = analytics.buckets,
          compactLabels = !isWide && analytics.period == AnalyticsPeriod.YEARLY,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_chart"),
        )
      }
    }
  }
}

@Composable
private fun AnalyticsStatBox(
  label: String,
  value: String,
  accentColor: Color,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
) {
  Surface(
    modifier = modifier.semantics(mergeDescendants = true) {},
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .background(accentColor, CircleShape),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = value,
        style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun LegendDot(
  color: Color,
  label: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .background(color, CircleShape),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun AnalyticsBarChart(
  buckets: List<AnalyticsChartBucket>,
  compactLabels: Boolean,
  modifier: Modifier = Modifier,
) {
  val maxValue = remember(buckets) {
    buckets.maxOfOrNull { maxOf(it.collectedPkr, it.bookedSalesPkr) }?.coerceAtLeast(1L) ?: 1L
  }
  val baselineColor = MaterialTheme.colorScheme.outlineVariant
  val bookedColor = BrassSecondary.copy(alpha = 0.55f)
  val collectedColor = PinePrimary

  Column(modifier = modifier) {
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(116.dp),
    ) {
      val count = buckets.size.coerceAtLeast(1)
      val slotWidth = size.width / count
      val chartHeight = size.height - 6.dp.toPx()
      val barGroupWidth = (slotWidth * 0.68f).coerceAtMost(32.dp.toPx())
      val singleBarWidth = (barGroupWidth * 0.44f).coerceAtLeast(3.dp.toPx())
      val gap = (barGroupWidth - singleBarWidth * 2f).coerceAtLeast(1.dp.toPx())
      val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

      drawLine(
        color = baselineColor.copy(alpha = 0.45f),
        start = Offset(0f, chartHeight * 0.5f),
        end = Offset(size.width, chartHeight * 0.5f),
        strokeWidth = 1.dp.toPx(),
      )
      drawLine(
        color = baselineColor,
        start = Offset(0f, chartHeight),
        end = Offset(size.width, chartHeight),
        strokeWidth = 1.dp.toPx(),
      )

      buckets.forEachIndexed { index, bucket ->
        val slotStart = index * slotWidth
        val groupStart = slotStart + (slotWidth - barGroupWidth) / 2f

        val bookedRatio = (bucket.bookedSalesPkr.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
        val collectedRatio = (bucket.collectedPkr.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)

        val bookedHeight = if (bucket.bookedSalesPkr > 0L) (chartHeight * bookedRatio).coerceAtLeast(4.dp.toPx()) else 0f
        val collectedHeight = if (bucket.collectedPkr > 0L) (chartHeight * collectedRatio).coerceAtLeast(4.dp.toPx()) else 0f

        if (bookedHeight > 0f) {
          drawRoundRect(
            color = bookedColor,
            topLeft = Offset(groupStart, chartHeight - bookedHeight),
            size = Size(singleBarWidth, bookedHeight),
            cornerRadius = radius,
          )
        }
        if (collectedHeight > 0f) {
          drawRoundRect(
            color = collectedColor,
            topLeft = Offset(groupStart + singleBarWidth + gap, chartHeight - collectedHeight),
            size = Size(singleBarWidth, collectedHeight),
            cornerRadius = radius,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
      buckets.forEach { bucket ->
        Text(
          text = resolveBucketLabel(bucket.label, compactLabels),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun resolveBucketLabel(
  label: AnalyticsBucketLabel,
  compact: Boolean,
): String {
  val locale = LocalConfiguration.current.locales.let {
    if (it.isEmpty) Locale.ENGLISH else it[0]
  }
  val symbols = remember(locale) { DateFormatSymbols.getInstance(locale) }

  return when (label) {
    AnalyticsBucketLabel.Night -> stringResource(R.string.analytics_bucket_night)
    AnalyticsBucketLabel.Morning -> stringResource(R.string.analytics_bucket_morning)
    AnalyticsBucketLabel.Afternoon -> stringResource(R.string.analytics_bucket_afternoon)
    AnalyticsBucketLabel.Evening -> stringResource(R.string.analytics_bucket_evening)
    is AnalyticsBucketLabel.DayOfWeek -> {
      val name = symbols.shortWeekdays.getOrNull(label.calendarDay).orEmpty()
      if (name.isNotBlank()) name.take(3) else when (label.calendarDay) {
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        Calendar.SATURDAY -> "Sat"
        else -> "Sun"
      }
    }
    is AnalyticsBucketLabel.WeekOfMonth -> stringResource(R.string.analytics_week_prefix, label.weekNumber)
    is AnalyticsBucketLabel.MonthOfYear -> {
      val months = symbols.shortMonths
      val raw = months.getOrNull(label.monthIndex).orEmpty()
      if (compact) raw.take(1).ifEmpty { "${label.monthIndex + 1}" } else raw.take(3)
    }
  }
}
