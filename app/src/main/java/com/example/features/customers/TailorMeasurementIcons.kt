package com.example.features.customers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.DamanStyle
import com.example.core.model.NeckStyle
import com.example.core.model.PocketStyle
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.ui.theme.JetBrainsMonoFamily

enum class TailorBadgeGlyph {
  KAMEEZ_LENGTH,
  SHOULDER,
  CHEST,
  SLEEVES,
  WAIST,
  GHERA,
  COLLAR,
  CUFF,
  SHALWAR,
  PAINCHA,
  ASAN,
  POCKET,
  SILAI,
  DAMAN,
}

@Composable
fun TailorMeasurementBadge(
  glyph: TailorBadgeGlyph,
  badgeBackground: Color = Color(0xFFECEEE9),
  glyphColor: Color = Color(0xFF141612),
  accentColor: Color = Color(0xFF657254),
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = badgeBackground,
    modifier = modifier.size(44.dp),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(6.dp),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        when (glyph) {
          TailorBadgeGlyph.KAMEEZ_LENGTH -> drawKameezWithVerticalMeasure(glyphColor, accentColor)
          TailorBadgeGlyph.SHOULDER -> drawShoulderMeasure(glyphColor, accentColor)
          TailorBadgeGlyph.CHEST -> drawChestMeasure(glyphColor, accentColor)
          TailorBadgeGlyph.SLEEVES -> drawSleeveMeasure(glyphColor, accentColor)
          TailorBadgeGlyph.WAIST -> drawWaistMeasure(glyphColor, accentColor)
          TailorBadgeGlyph.GHERA -> drawGheraMeasure(glyphColor, accentColor)
          TailorBadgeGlyph.COLLAR -> drawCollarGlyph(glyphColor, accentColor)
          TailorBadgeGlyph.CUFF -> drawCuffGlyph(glyphColor, accentColor)
          TailorBadgeGlyph.SHALWAR -> drawShalwarGlyph(glyphColor, accentColor)
          TailorBadgeGlyph.PAINCHA -> drawPainchaGlyph(glyphColor, accentColor)
          TailorBadgeGlyph.ASAN -> drawAsanGlyph(glyphColor, accentColor)
          TailorBadgeGlyph.POCKET -> drawPocketGlyph(glyphColor, accentColor)
          TailorBadgeGlyph.SILAI -> drawSilaiButtonGlyph(glyphColor, accentColor)
          TailorBadgeGlyph.DAMAN -> drawDamanGlyph(glyphColor, accentColor)
        }
      }
    }
  }
}

private fun DrawScope.drawShirtBody(color: Color, leftOffset: Float = 0f, scale: Float = 1f) {
  val w = size.width * scale
  val h = size.height * scale
  val ox = leftOffset
  val oy = (size.height - h) / 2f

  val path = Path().apply {
    moveTo(ox + w * 0.28f, oy + h * 0.14f)
    lineTo(ox + w * 0.12f, oy + h * 0.28f)
    lineTo(ox + w * 0.06f, oy + h * 0.62f)
    lineTo(ox + w * 0.20f, oy + h * 0.62f)
    lineTo(ox + w * 0.23f, oy + h * 0.36f)
    lineTo(ox + w * 0.23f, oy + h * 0.90f)
    lineTo(ox + w * 0.69f, oy + h * 0.90f)
    lineTo(ox + w * 0.69f, oy + h * 0.36f)
    lineTo(ox + w * 0.72f, oy + h * 0.62f)
    lineTo(ox + w * 0.86f, oy + h * 0.62f)
    lineTo(ox + w * 0.80f, oy + h * 0.28f)
    lineTo(ox + w * 0.64f, oy + h * 0.14f)
    lineTo(ox + w * 0.54f, oy + h * 0.20f)
    lineTo(ox + w * 0.38f, oy + h * 0.20f)
    close()
  }
  drawPath(path, color = color)
}

private fun DrawScope.drawKameezWithVerticalMeasure(color: Color, accent: Color) {
  drawShirtBody(color, leftOffset = 0f, scale = 0.95f)
  // Vertical double arrow on right
  val x = size.width * 0.88f
  val top = size.height * 0.16f
  val bot = size.height * 0.88f
  val stroke = size.minDimension * 0.065f
  drawLine(accent, Offset(x, top), Offset(x, bot), strokeWidth = stroke, cap = StrokeCap.Round)
  drawLine(accent, Offset(x - size.width * 0.08f, bot - size.height * 0.10f), Offset(x, bot), strokeWidth = stroke, cap = StrokeCap.Round)
  drawLine(accent, Offset(x + size.width * 0.08f, bot - size.height * 0.10f), Offset(x, bot), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawShoulderMeasure(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.065f
  // Horizontal arrow across top
  val y = h * 0.18f
  drawLine(accent, Offset(w * 0.10f, y), Offset(w * 0.90f, y), strokeWidth = stroke, cap = StrokeCap.Round)
  drawLine(accent, Offset(w * 0.22f, y - h * 0.10f), Offset(w * 0.10f, y), strokeWidth = stroke, cap = StrokeCap.Round)
  drawLine(accent, Offset(w * 0.22f, y + h * 0.10f), Offset(w * 0.10f, y), strokeWidth = stroke, cap = StrokeCap.Round)
  drawLine(accent, Offset(w * 0.78f, y - h * 0.10f), Offset(w * 0.90f, y), strokeWidth = stroke, cap = StrokeCap.Round)
  drawLine(accent, Offset(w * 0.78f, y + h * 0.10f), Offset(w * 0.90f, y), strokeWidth = stroke, cap = StrokeCap.Round)

  // Upper torso silhouette
  val torso = Path().apply {
    moveTo(w * 0.34f, h * 0.34f)
    lineTo(w * 0.14f, h * 0.46f)
    lineTo(w * 0.08f, h * 0.88f)
    lineTo(w * 0.92f, h * 0.88f)
    lineTo(w * 0.86f, h * 0.46f)
    lineTo(w * 0.66f, h * 0.34f)
    lineTo(w * 0.56f, h * 0.42f)
    lineTo(w * 0.44f, h * 0.42f)
    close()
  }
  drawPath(torso, color = color)
  drawLine(Color.White, Offset(w * 0.50f, h * 0.42f), Offset(w * 0.50f, h * 0.88f), strokeWidth = stroke * 0.6f)
}

private fun DrawScope.drawChestMeasure(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.065f
  drawShirtBody(color, leftOffset = size.width * 0.05f, scale = 0.92f)
  drawArc(
    color = Color.White,
    startAngle = 15f,
    sweepAngle = 150f,
    useCenter = false,
    topLeft = Offset(w * 0.18f, h * 0.42f),
    size = Size(w * 0.64f, h * 0.24f),
    style = Stroke(width = stroke),
  )
  drawLine(accent, Offset(w * 0.16f, h * 0.54f), Offset(w * 0.84f, h * 0.54f), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
}

private fun DrawScope.drawSleeveMeasure(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.07f
  val path = Path().apply {
    moveTo(w * 0.18f, h * 0.18f)
    lineTo(w * 0.52f, h * 0.18f)
    lineTo(w * 0.84f, h * 0.72f)
    lineTo(w * 0.62f, h * 0.84f)
    lineTo(w * 0.26f, h * 0.42f)
    close()
  }
  drawPath(path, color = color)
  drawLine(accent, Offset(w * 0.46f, h * 0.12f), Offset(w * 0.86f, h * 0.64f), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawWaistMeasure(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.065f
  drawShirtBody(color, leftOffset = w * 0.05f, scale = 0.92f)
  drawLine(accent, Offset(w * 0.20f, h * 0.64f), Offset(w * 0.80f, h * 0.64f), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawGheraMeasure(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.065f
  drawShirtBody(color, leftOffset = w * 0.05f, scale = 0.90f)
  drawLine(accent, Offset(w * 0.18f, h * 0.86f), Offset(w * 0.82f, h * 0.86f), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawCollarGlyph(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.07f
  val band = Path().apply {
    moveTo(w * 0.22f, h * 0.28f)
    lineTo(w * 0.50f, h * 0.18f)
    lineTo(w * 0.78f, h * 0.28f)
    lineTo(w * 0.74f, h * 0.46f)
    lineTo(w * 0.50f, h * 0.58f)
    lineTo(w * 0.26f, h * 0.46f)
    close()
  }
  drawPath(band, color = color)
  val leftWing = Path().apply {
    moveTo(w * 0.22f, h * 0.42f)
    lineTo(w * 0.48f, h * 0.62f)
    lineTo(w * 0.28f, h * 0.84f)
    lineTo(w * 0.14f, h * 0.62f)
    close()
  }
  val rightWing = Path().apply {
    moveTo(w * 0.78f, h * 0.42f)
    lineTo(w * 0.52f, h * 0.62f)
    lineTo(w * 0.72f, h * 0.84f)
    lineTo(w * 0.86f, h * 0.62f)
    close()
  }
  drawPath(leftWing, color = color)
  drawPath(rightWing, color = color)
  drawLine(accent, Offset(w * 0.26f, h * 0.46f), Offset(w * 0.50f, h * 0.58f), strokeWidth = stroke * 0.8f)
  drawLine(accent, Offset(w * 0.74f, h * 0.46f), Offset(w * 0.50f, h * 0.58f), strokeWidth = stroke * 0.8f)
}

private fun DrawScope.drawCuffGlyph(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.065f
  drawRoundRect(
    color = color,
    topLeft = Offset(w * 0.14f, h * 0.30f),
    size = Size(w * 0.72f, h * 0.42f),
    cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
  )
  drawLine(Color.White, Offset(w * 0.56f, h * 0.30f), Offset(w * 0.56f, h * 0.72f), strokeWidth = stroke)
  drawCircle(accent, radius = w * 0.06f, center = Offset(w * 0.70f, h * 0.51f))
}

private fun DrawScope.drawShalwarGlyph(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.065f
  val path = Path().apply {
    moveTo(w * 0.24f, h * 0.12f)
    lineTo(w * 0.76f, h * 0.12f)
    lineTo(w * 0.80f, h * 0.45f)
    lineTo(w * 0.64f, h * 0.88f)
    lineTo(w * 0.52f, h * 0.88f)
    lineTo(w * 0.50f, h * 0.48f)
    lineTo(w * 0.48f, h * 0.88f)
    lineTo(w * 0.36f, h * 0.88f)
    lineTo(w * 0.20f, h * 0.45f)
    close()
  }
  drawPath(path, color = color)
  drawLine(accent, Offset(w * 0.24f, h * 0.20f), Offset(w * 0.76f, h * 0.20f), strokeWidth = stroke)
}

private fun DrawScope.drawPainchaGlyph(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.07f
  val leg = Path().apply {
    moveTo(w * 0.28f, h * 0.14f)
    lineTo(w * 0.72f, h * 0.14f)
    lineTo(w * 0.64f, h * 0.80f)
    lineTo(w * 0.36f, h * 0.80f)
    close()
  }
  drawPath(leg, color = color)
  drawLine(accent, Offset(w * 0.32f, h * 0.86f), Offset(w * 0.68f, h * 0.86f), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawAsanGlyph(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.07f
  val path = Path().apply {
    moveTo(w * 0.22f, h * 0.16f)
    lineTo(w * 0.78f, h * 0.16f)
    lineTo(w * 0.78f, h * 0.62f)
    lineTo(w * 0.56f, h * 0.62f)
    lineTo(w * 0.50f, h * 0.44f)
    lineTo(w * 0.44f, h * 0.62f)
    lineTo(w * 0.22f, h * 0.62f)
    close()
  }
  drawPath(path, color = color)
  drawLine(accent, Offset(w * 0.50f, h * 0.18f), Offset(w * 0.50f, h * 0.46f), strokeWidth = stroke, cap = StrokeCap.Round)
}

private fun DrawScope.drawPocketGlyph(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  drawShirtBody(color, leftOffset = w * 0.05f, scale = 0.92f)
  // Two crisp green/olive side pocket highlights like screenshot
  drawRoundRect(
    color = accent,
    topLeft = Offset(w * 0.20f, h * 0.58f),
    size = Size(w * 0.12f, h * 0.18f),
    cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
  )
  drawRoundRect(
    color = accent,
    topLeft = Offset(w * 0.66f, h * 0.58f),
    size = Size(w * 0.12f, h * 0.18f),
    cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
  )
}

private fun DrawScope.drawSilaiButtonGlyph(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.07f
  drawCircle(color = color, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f))
  drawCircle(color = Color.White, radius = w * 0.28f, center = Offset(w * 0.50f, h * 0.50f), style = Stroke(width = stroke * 0.7f))
  val holeR = w * 0.045f
  drawCircle(Color.White, holeR, Offset(w * 0.42f, h * 0.42f))
  drawCircle(Color.White, holeR, Offset(w * 0.58f, h * 0.42f))
  drawCircle(Color.White, holeR, Offset(w * 0.42f, h * 0.58f))
  drawCircle(Color.White, holeR, Offset(w * 0.58f, h * 0.58f))
  drawLine(accent, Offset(w * 0.42f, h * 0.42f), Offset(w * 0.58f, h * 0.58f), strokeWidth = stroke * 0.65f)
  drawLine(accent, Offset(w * 0.58f, h * 0.42f), Offset(w * 0.42f, h * 0.58f), strokeWidth = stroke * 0.65f)
}

private fun DrawScope.drawDamanGlyph(color: Color, accent: Color) {
  val w = size.width
  val h = size.height
  val stroke = size.minDimension * 0.07f
  val hem = Path().apply {
    moveTo(w * 0.22f, h * 0.18f)
    lineTo(w * 0.78f, h * 0.18f)
    lineTo(w * 0.78f, h * 0.72f)
    quadraticTo(w * 0.50f, h * 0.88f, w * 0.22f, h * 0.72f)
    close()
  }
  drawPath(hem, color = color)
  drawPath(
    path = Path().apply {
      moveTo(w * 0.24f, h * 0.72f)
      quadraticTo(w * 0.50f, h * 0.86f, w * 0.76f, h * 0.72f)
    },
    color = accent,
    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

data class MeasurementMetricSpec(
  val glyph: TailorBadgeGlyph,
  val label: String,
  val value: String,
)

@Composable
fun MeasurementMetricTile(
  glyph: TailorBadgeGlyph,
  label: String,
  value: String,
  labelColor: Color,
  valueColor: Color,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    TailorMeasurementBadge(glyph = glyph)
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = labelColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        style = TextStyle(
          fontFamily = JetBrainsMonoFamily,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = valueColor,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun StyleChoiceTile(
  glyph: TailorBadgeGlyph,
  label: String,
  textColor: Color,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    TailorMeasurementBadge(glyph = glyph)
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = textColor,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
fun localizedNeckStyle(style: NeckStyle): String = when (style) {
  NeckStyle.COLLAR -> stringResource(R.string.style_opt_collar)
  NeckStyle.BAN -> stringResource(R.string.style_opt_ban)
  NeckStyle.HALF_BAN -> stringResource(R.string.style_opt_half_ban)
  NeckStyle.ROUND -> stringResource(R.string.style_opt_round)
}

@Composable
fun localizedDamanStyle(style: DamanStyle): String = when (style) {
  DamanStyle.GOL -> stringResource(R.string.style_opt_daman_gol)
  DamanStyle.CHORASI -> stringResource(R.string.style_opt_daman_chorasi)
}

@Composable
fun localizedPocketStyle(style: PocketStyle): String = when (style) {
  PocketStyle.FRONT -> stringResource(R.string.style_opt_pocket_front)
  PocketStyle.ONE_SIDE -> stringResource(R.string.style_opt_pocket_one_side)
  PocketStyle.TWO_SIDE -> stringResource(R.string.style_opt_pocket_two_side)
  PocketStyle.SHALWAR_POCKET -> stringResource(R.string.style_opt_pocket_shalwar)
}

@Composable
fun localizedPocketStyles(styles: Collection<PocketStyle>): String {
  val ordered = com.example.core.model.PocketStyleCodec.ordered(styles)
  if (ordered.isEmpty()) return stringResource(R.string.style_opt_pocket_none)
  return ordered.map { localizedPocketStyle(it) }.joinToString(", ")
}

@Composable
fun localizedSleeveStyle(style: SleeveStyle): String = when (style) {
  SleeveStyle.PLAIN -> stringResource(R.string.style_opt_sleeve_plain)
  SleeveStyle.SINGLE_CUFF -> stringResource(R.string.style_opt_sleeve_single_cuff)
  SleeveStyle.DOUBLE_CUFF -> stringResource(R.string.style_opt_sleeve_double_cuff)
  SleeveStyle.STUDS -> stringResource(R.string.style_opt_sleeve_studs)
}

@Composable
fun localizedSilaiStyle(style: SilaiStyle): String = when (style) {
  SilaiStyle.SINGLE -> stringResource(R.string.style_opt_silai_single)
  SilaiStyle.DOUBLE -> stringResource(R.string.style_opt_silai_double)
  SilaiStyle.CHAMAK_DHAGA -> stringResource(R.string.style_opt_silai_chamak)
}

@Composable
fun localizedPaymentMethod(method: com.example.core.model.PaymentMethod): String = when (method) {
  com.example.core.model.PaymentMethod.CASH -> stringResource(R.string.payment_method_cash)
  com.example.core.model.PaymentMethod.JAZZCASH -> stringResource(R.string.payment_method_jazzcash)
  com.example.core.model.PaymentMethod.EASYPAISA -> stringResource(R.string.payment_method_easypaisa)
  com.example.core.model.PaymentMethod.BANK_TRANSFER -> stringResource(R.string.payment_method_bank_transfer)
  com.example.core.model.PaymentMethod.OTHER -> stringResource(R.string.payment_method_other)
}
