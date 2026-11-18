package com.simulens.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simulens.app.domain.rules.intervention.Trajectory
import com.simulens.app.ml.RiskPrediction
import com.simulens.app.ui.components.PillBadge
import com.simulens.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Roles, so the three cards complement each other instead of repeating each other:
 *
 *   MLClassifierCard   -> "What does the model make of my last 28 days?"  (the alert)
 *   Since you started  -> "How have I changed since my baseline?"          (the change)
 *   WeeklyUsageCard    -> "What are the real numbers?"                     (the evidence)
 *
 * Shared vocabulary: green = typical / lower, amber = worth a look, muted = no change.
 * No red anywhere. Nothing here predicts the future.
 */

private val RiskTypical = IceBlue

// =====================================================================
// 1. Behavioural risk check
// =====================================================================

@Composable
fun MLClassifierCard(
    prediction: RiskPrediction?,
    trajectory: Trajectory? = null,
    onNavigateToRag: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    // ---------- Not enough data yet ----------
    if (prediction == null) {
        InsightCard {
            CardHeader(
                Icons.Rounded.Insights, colors.onSurfaceVariant,
                "Behavioural risk check", "Looks at your last 28 days"
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Still learning your pattern",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Keep using your phone as usual. The check appears once there's enough tracked usage to look at.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
            )
        }
        return
    }

    val elevated = prediction.riskLevel == "ELEVATED"
    val prob = prediction.probability.coerceIn(0.0, 1.0).toFloat()
    val tone = if (elevated) InkAmber else RiskTypical
    val surface = colors.surfaceContainerLow

    InsightCard(
        container = if (elevated) InkAmber.copy(alpha = 0.08f).compositeOver(surface) else surface,
        border = if (elevated) InkAmber.copy(alpha = 0.40f) else colors.outlineVariant
    ) {
        CardHeader(
            Icons.Rounded.Insights, tone,
            "Behavioural risk check", "Looks at your last 28 days",
            trailing = {
                PillBadge(
                    text = if (elevated) "WORTH A LOOK" else "TYPICAL",
                    containerColor = tone.copy(alpha = 0.16f),
                    contentColor = tone
                )
            }
        )

        Spacer(Modifier.height(18.dp))

        Text(
            if (elevated) "Your pattern shows signs of heavier use" else "Your pattern looks typical",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (elevated)
                "Late-night visits, opens during class and short gaps between sessions over the last 28 days " +
                        "resemble heavier-use patterns. It's an estimate to reflect on, not a diagnosis."
            else
                "Your last 28 days sit within the typical range. Keeping your sleep and class boundaries as they are will help.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
        )

        // One neutral line that ties this card to "Since you started" without repeating it.
        val context = when (trajectory) {
            Trajectory.SLIPPING -> "Your daily screen time is also higher than when you started."
            Trajectory.IMPROVING -> "Your daily screen time is lower than when you started."
            else -> null
        }
        if (context != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(colors.onSurfaceVariant, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(context, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(18.dp))
        RiskMeter(prob = prob, marker = tone)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Typical", style = MaterialTheme.typography.labelSmall, color = RiskTypical)
            Text("Elevated", style = MaterialTheme.typography.labelSmall, color = InkAmber)
        }

        if (elevated) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onNavigateToRag,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InkAmber, contentColor = colors.background)
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Talk it through with Buddy", fontWeight = FontWeight.Bold)
            }
        }

        // ---------- Explainer ----------
        var showExplainer by remember { mutableStateOf(false) }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { showExplainer = !showExplainer }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Icon(
                if (showExplainer) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (showExplainer) "Hide details" else "What's behind this?",
                color = colors.primary, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }

        AnimatedVisibility(visible = showExplainer) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainer, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text(
                    "A model on your device looks at your last 28 days of tracked usage and compares the pattern " +
                            "with ones it has learned. These are the kinds of signals it considers:",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                ExplainerItem(Icons.Rounded.Bedtime, "Late-night use", "Opens and time spent in your bedtime window.")
                ExplainerItem(Icons.Rounded.School, "Opens during class", "Using monitored apps while you're scheduled in lectures.")
                ExplainerItem(Icons.Rounded.Timer, "Unusually long visits", "Sessions much longer than your typical one.")
                ExplainerItem(Icons.Rounded.Repeat, "Short gaps between sessions", "Reopening an app soon after leaving it.")
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = colors.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Model score: ${(prob * 100).toInt()}%. Scores of 50% or more are shown as elevated. " +
                            "This is an estimate, not a measurement, and it can be wrong.",
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
                )
            }
        }
    }
}

/** Two-zone track, a tick at the 50% cutoff, and a marker at the real position (no clamping). */
@Composable
private fun RiskMeter(prob: Float, marker: Color) {
    val colors = MaterialTheme.colorScheme
    val low = RiskTypical.copy(alpha = 0.35f)
    val high = InkAmber.copy(alpha = 0.35f)
    val tick = colors.onSurfaceVariant
    val ring = colors.surfaceContainerLow

    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val trackH = 10.dp.toPx()
        val r = 9.dp.toPx()
        val top = (size.height - trackH) / 2f
        val cy = size.height / 2f

        val track = Path().apply {
            addRoundRect(RoundRect(0f, top, size.width, top + trackH, CornerRadius(trackH / 2f)))
        }
        clipPath(track) {
            drawRect(low, Offset(0f, top), Size(size.width / 2f, trackH))
            drawRect(high, Offset(size.width / 2f, top), Size(size.width / 2f, trackH))
        }

        drawLine(
            tick, Offset(size.width / 2f, top - 3.dp.toPx()), Offset(size.width / 2f, top + trackH + 3.dp.toPx()),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
        )

        val cx = r + prob * (size.width - 2 * r)
        drawCircle(marker, r, Offset(cx, cy))
        drawCircle(ring, r, Offset(cx, cy), style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun ExplainerItem(icon: ImageVector, title: String, desc: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).background(colors.primary.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

// =====================================================================
// 2. Weekly usage
// =====================================================================

private enum class Pace { ABOVE, BELOW, ON }

private data class UsualRange(val low: Double, val median: Double, val high: Double, val estimated: Boolean)

private fun usualRange(done: List<WeekTotal>): UsualRange {
    val s = done.map { it.minutes }.sorted()
    val med = percentile(s, 0.5)
    return if (s.size >= 4) UsualRange(percentile(s, 0.25), med, percentile(s, 0.75), false)
    else UsualRange(med * 0.75, med, med * 1.25, true)
}

/** Compares the current week to the usual range scaled by how much of the week has passed. */
private fun paceFor(current: WeekTotal, r: UsualRange): Pace? {
    val days = (ChronoUnit.DAYS.between(current.weekStart, LocalDate.now()) + 1).coerceIn(1L, 7L)
    if (days < 2) return null
    val f = days / 7.0
    return when {
        current.minutes > r.high * f -> Pace.ABOVE
        current.minutes < r.low * f -> Pace.BELOW
        else -> Pace.ON
    }
}

@Composable
fun WeeklyUsageCard(weeks: List<WeekTotal>) {
    val colors = MaterialTheme.colorScheme
    val shown = weeks.takeLast(6)
    val done = shown.filter { !it.partial }
    val current = shown.lastOrNull { it.partial }
    val labelFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH) }

    InsightCard {
        CardHeader(Icons.Rounded.BarChart, IceBlue, "Weekly usage", "Time in your monitored apps")
        Spacer(Modifier.height(16.dp))

        if (done.size < 2) {
            Text(
                "Your weekly trend appears after two full weeks of tracking.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).background(colors.onSurface.copy(alpha = 0.08f), CircleShape)) {
                Box(
                    Modifier.fillMaxWidth((done.size / 2f).coerceIn(0.03f, 1f)).fillMaxHeight()
                        .background(IceBlue, CircleShape)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("${done.size} of 2 full weeks", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            return@InsightCard
        }

        val range = remember(done) { usualRange(done) }
        val headlineWeek = current ?: done.last()
        val pace = current?.let { paceFor(it, range) }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (current != null) "This week so far" else "Last full week",
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant
                )
                Text(
                    formatDuration(headlineWeek.minutes),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
            }
            if (pace != null) {
                val (text, color) = when (pace) {
                    Pace.ABOVE -> "ABOVE USUAL PACE" to InkAmber
                    Pace.BELOW -> "BELOW USUAL PACE" to RiskTypical
                    Pace.ON -> "ON USUAL PACE" to colors.onSurfaceVariant
                }
                PillBadge(text = text, containerColor = color.copy(alpha = 0.16f), contentColor = color)
            }
        }

        Spacer(Modifier.height(16.dp))
        TrendChart(shown, range, labelFmt)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(IceBlue.copy(alpha = 0.25f), "Usual range")
            LegendItem(colors.onSurfaceVariant, "Typical week", dashed = true)
            if (current != null) LegendItem(InkAmber, "So far")
        }
        if (range.estimated) {
            Spacer(Modifier.height(6.dp))
            Text(
                "The usual range is an estimate until you have 4 full weeks.",
                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrendChart(shown: List<WeekTotal>, range: UsualRange, labelFmt: DateTimeFormatter) {
    val colors = MaterialTheme.colorScheme
    val maxV = (shown.map { it.minutes } + range.high).max().coerceAtLeast(30.0) * 1.15

    val barColor = IceBlue.copy(alpha = 0.80f)
    val currentColor = InkAmber
    val bandColor = IceBlue.copy(alpha = 0.14f)
    val medianColor = colors.onSurfaceVariant

    val description = shown.joinToString(". ") { w ->
        val name = if (w.partial) "This week so far" else "Week of ${w.weekStart.format(labelFmt)}"
        "$name: ${formatDuration(w.minutes)}"
    }

    Canvas(
        Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }
    ) {
        val h = size.height
        val slot = size.width / shown.size
        fun y(v: Double) = h - (v / maxV).toFloat() * h

        drawRoundRect(
            bandColor, Offset(0f, y(range.high)), Size(size.width, y(range.low) - y(range.high)),
            CornerRadius(6.dp.toPx())
        )
        shown.forEachIndexed { i, w ->
            val bw = slot * 0.5f
            val top = y(w.minutes)
            drawRoundRect(
                color = if (w.partial) currentColor else barColor,
                topLeft = Offset(i * slot + (slot - bw) / 2, top),
                size = Size(bw, (h - top).coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
        drawLine(
            medianColor, Offset(0f, y(range.median)), Offset(size.width, y(range.median)),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        )
    }

    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        shown.forEach { w ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatDuration(w.minutes), style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (w.partial) FontWeight.Bold else FontWeight.Normal,
                    color = if (w.partial) InkAmber else colors.onSurface,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, textAlign = TextAlign.Center
                )
                Text(
                    if (w.partial) "This wk" else w.weekStart.format(labelFmt),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun percentile(sorted: List<Double>, p: Double): Double {
    if (sorted.size == 1) return sorted[0]
    val idx = p * (sorted.size - 1)
    val lo = floor(idx).toInt()
    val hi = ceil(idx).toInt()
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo)
}