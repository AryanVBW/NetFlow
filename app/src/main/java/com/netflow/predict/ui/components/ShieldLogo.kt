package com.netflow.predict.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

/**
 * Animated shield logo drawn with Canvas.
 * Draws a shield outline, then pings three arcs outward.
 */
@Composable
fun ShieldLogoAnimated(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_ping")

    val pingAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.8f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ping_alpha"
    )
    val pingRadius by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ping_radius"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r  = size.minDimension / 2

        // Ping circles
        for (i in 0..2) {
            val fraction = ((pingRadius + i * 0.25f) % 1f)
            val circleR  = r * 0.6f + fraction * r * 0.9f
            val alpha    = pingAlpha * (1f - fraction)
            drawCircle(
                color  = Color(0xFF4FC3F7).copy(alpha = alpha * 0.4f),
                radius = circleR,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style  = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Shield shape (simplified path)
        val shieldPath = Path().apply {
            val w = size.width * 0.65f
            val h = size.height * 0.75f
            val sx = cx - w / 2
            val sy = cy - h / 2
            moveTo(cx, sy)
            lineTo(sx + w, sy + h * 0.18f)
            lineTo(sx + w, sy + h * 0.55f)
            quadraticBezierTo(sx + w, sy + h * 0.85f, cx, sy + h)
            quadraticBezierTo(sx, sy + h * 0.85f, sx, sy + h * 0.55f)
            lineTo(sx, sy + h * 0.18f)
            close()
        }
        drawPath(
            path  = shieldPath,
            color = Color(0xFF4FC3F7),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // "NF" monogram — simple two strokes
        val textPaint = android.graphics.Paint().apply {
            color     = android.graphics.Color.parseColor("#4FC3F7")
            textSize  = size.minDimension * 0.28f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText("NF", cx, cy + textPaint.textSize * 0.35f, textPaint)
    }
}
