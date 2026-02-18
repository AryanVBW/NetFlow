package com.netflow.predict.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.netflow.predict.ui.theme.Primary

/**
 * Small sparkline (5 bars) showing bytes/sec history.
 */
@Composable
fun Sparkline(
    data: List<Long>,
    modifier: Modifier = Modifier,
    color: Color = Primary
) {
    val safeData = data.ifEmpty { List(5) { 0L } }
    val maxVal = safeData.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f

    Canvas(modifier = modifier.size(width = 36.dp, height = 18.dp)) {
        val barWidth = size.width / (safeData.size * 2 - 1)
        safeData.forEachIndexed { i, v ->
            val barHeight = (v.toFloat() / maxVal) * size.height
            val x = i * barWidth * 2
            drawRect(
                color    = color.copy(alpha = 0.85f),
                topLeft  = Offset(x, size.height - barHeight),
                size     = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )
        }
    }
}

/**
 * Area chart for 24-hour traffic data (hourly points).
 */
@Composable
fun AreaChart(
    dataPoints: List<Long>,
    modifier: Modifier = Modifier,
    lineColor: Color = Primary,
    fillColor: Color = Primary.copy(alpha = 0.3f)
) {
    val safeData = if (dataPoints.isEmpty()) List(24) { 0L } else dataPoints
    val maxVal = safeData.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f

    Canvas(modifier = modifier) {
        val stepX = size.width / (safeData.size - 1).coerceAtLeast(1)

        val points = safeData.mapIndexed { i, v ->
            Offset(
                x = i * stepX,
                y = size.height - (v.toFloat() / maxVal) * size.height * 0.85f
            )
        }

        // Fill path
        val fillPath = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(
            path  = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = 0f,
                endY   = size.height
            )
        )

        // Stroke line
        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path      = strokePath,
            color     = lineColor,
            style     = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Weekly risk bar chart (7 bars).
 */
@Composable
fun WeeklyRiskChart(
    riskLevels: List<com.netflow.predict.data.model.RiskLevel>,
    todayIndex: Int = java.time.LocalDate.now().dayOfWeek.value - 1,
    modifier: Modifier = Modifier
) {
    val safeData = if (riskLevels.isEmpty()) List(7) { com.netflow.predict.data.model.RiskLevel.UNKNOWN }
                  else riskLevels

    Canvas(modifier = modifier) {
        val barWidth = (size.width / safeData.size) * 0.55f
        val gap      = (size.width / safeData.size) * 0.45f

        safeData.forEachIndexed { i, risk ->
            val barH = when (risk) {
                com.netflow.predict.data.model.RiskLevel.HIGH    -> size.height * 0.9f
                com.netflow.predict.data.model.RiskLevel.MEDIUM  -> size.height * 0.55f
                com.netflow.predict.data.model.RiskLevel.LOW     -> size.height * 0.25f
                com.netflow.predict.data.model.RiskLevel.UNKNOWN -> size.height * 0.1f
            }
            val isFuture = i > todayIndex
            val alpha    = if (isFuture) 0.4f else 1f
            val color    = riskColor(risk).copy(alpha = alpha)
            val x = i * (barWidth + gap) + gap / 2

            drawRect(
                color   = color,
                topLeft = Offset(x, size.height - barH),
                size    = androidx.compose.ui.geometry.Size(barWidth, barH)
            )
        }
    }
}
