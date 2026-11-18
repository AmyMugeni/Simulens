package com.simulens.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simulens.app.domain.rules.intervention.DeviationLevel
import com.simulens.app.ui.theme.IceBlue
import com.simulens.app.ui.theme.InkAmber
import kotlin.math.roundToInt

/*
 * Shared vocabulary for every insight card:
 *   green/blue = typical / lower, amber = worth a look, muted = no change. No red.
 * One card shape, one header, one duration formatter, one level palette.
 */

internal val InsightGreen = Color(0xFF5FD39A)
internal val InsightAmberStrong = Color(0xFFFF9F43)

internal enum class Tone { GOOD, WATCH, FLAT, BAD }

@Composable
internal fun Tone.color(): Color = when (this) {
    Tone.GOOD -> IceBlue
    Tone.WATCH, Tone.BAD -> InkAmber
    Tone.FLAT -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Replaces the old five-argument levelColor. Using less than usual is good news, never amber. */
internal fun levelColor(l: DeviationLevel): Color = when (l) {
    DeviationLevel.WITHIN -> IceBlue
    DeviationLevel.BELOW -> IceBlue
    DeviationLevel.ABOVE -> InkAmber
    DeviationLevel.WELL_ABOVE -> InsightAmberStrong
}

internal fun fmtMins(m: Double): String = when {
    m < 1.0 -> "${(m * 60).roundToInt()}s"
    m < 60.0 -> "${m.roundToInt()} min"
    else -> "${(m / 60).toInt()}h ${(m % 60).roundToInt().toString().padStart(2, '0')}m"
}

@Composable
internal fun InsightCard(
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, border),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
internal fun CardHeader(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).background(tint.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Kept so existing call sites (title + content) still work. */
@Composable
internal fun BaselineCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    InsightCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onSurface)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
internal fun InsightLine(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Text(
            text, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun LegendItem(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dashed) {
            Canvas(Modifier.size(width = 14.dp, height = 2.dp)) {
                drawLine(
                    color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
                )
            }
        } else {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        }
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}