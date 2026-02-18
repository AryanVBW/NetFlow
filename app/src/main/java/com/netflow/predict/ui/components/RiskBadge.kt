package com.netflow.predict.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.netflow.predict.data.model.RiskLevel
import com.netflow.predict.ui.theme.*

/**
 * Colored pill badge for risk levels.
 */
@Composable
fun RiskBadge(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    val (bg, text, label) = when (riskLevel) {
        RiskLevel.LOW     -> Triple(Tertiary.copy(alpha = 0.2f),     Tertiary,  "Low")
        RiskLevel.MEDIUM  -> Triple(Warning.copy(alpha = 0.2f),      Warning,   "Medium")
        RiskLevel.HIGH    -> Triple(ErrorColor.copy(alpha = 0.2f),   ErrorColor,"High")
        RiskLevel.UNKNOWN -> Triple(Color.Gray.copy(alpha = 0.15f),  Color.Gray,"Unknown")
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = text
        )
    }
}

/** Color-only helper for charts / borders. */
fun riskColor(level: RiskLevel): Color = when (level) {
    RiskLevel.LOW     -> Tertiary
    RiskLevel.MEDIUM  -> Warning
    RiskLevel.HIGH    -> ErrorColor
    RiskLevel.UNKNOWN -> Color.Gray
}
