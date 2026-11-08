package com.simulens.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simulens.app.ml.RiskPrediction
import com.simulens.app.ui.components.PillBadge
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun MLClassifierCard(
    prediction: RiskPrediction?,
    onNavigateToRag: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    if (prediction == null) return

    val isElevated = prediction.riskLevel == "ELEVATED"
    val prob = prediction.probability.coerceIn(0.0, 1.0)
    val pct = (prob * 100).roundToInt()
    val accent = if (isElevated) colors.error else colors.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isElevated) colors.errorContainer.copy(alpha = 0.25f) else colors.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, if (isElevated) colors.error.copy(alpha = 0.5f) else colors.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isElevated) colors.errorContainer else colors.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isElevated) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isElevated) colors.onErrorContainer else colors.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ML Behavioral Risk Classifier",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Random Forest ONNX Model",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                PillBadge(
                    text = if (isElevated) "ELEVATED ($pct%)" else "NORMAL ($pct%)",
                    containerColor = if (isElevated) colors.errorContainer else colors.primaryContainer,
                    contentColor = if (isElevated) colors.onErrorContainer else colors.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Probability Spectrum Meter / Classification Gauge
            Text(
                text = "Model Classification Spectrum:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Normal Zone (0-49%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.primary)
                    Text("Threshold (50%)", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text("Elevated Risk (50-100%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.error)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.surfaceContainerHigh)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.5f).fillMaxHeight().background(colors.primaryContainer))
                        Box(modifier = Modifier.weight(0.5f).fillMaxHeight().background(colors.errorContainer))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = prob.toFloat().coerceIn(0.06f, 0.98f))
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Predicted Class: ${prediction.riskLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(
                        text = "Risk Probability: $pct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isElevated) {
                    "Your 28-day behavioral feature vector (session gaps, late-night visits, schedule conflicts) has been classified as ELEVATED risk by the Random Forest model ($pct% probability)."
                } else {
                    "Your 28-day behavioral feature vector is classified within the NORMAL range ($pct% risk probability). Usage parameters and session gaps remain balanced."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )

            if (isElevated) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateToRag,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.error)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ask AI for a Personalised Action Plan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Real weekly totals (from the sessions in Room) with the user's usual range behind them, and the
 * model's classification shown as an outlook, not as an invented minutes bar. The model returns a
 * class and a score, never a number of minutes.
 */
@Composable
fun UsageOutlookCard(
    weeks: List<WeekTotal>,
    prediction: RiskPrediction?,
    onNavigateToRag: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shown = weeks.takeLast(6)                         // six slots stay readable on a phone
    val done = shown.filter { !it.partial }
    val labelFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = colors.secondaryContainer, modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = colors.secondary,
                            modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Usage trend and outlook", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Text("Weekly time in your monitored apps", style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (done.size < 2) {
                Text(
                    "Not enough weeks yet. The trend appears after two full weeks of tracking.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                )
            } else {
                TrendChart(shown, done, labelFmt)
            }

            if (prediction != null) {
                Spacer(Modifier.height(16.dp))
                OutlookPanel(prediction, onNavigateToRag)
            }
        }
    }
}

@Composable
private fun TrendChart(shown: List<WeekTotal>, done: List<WeekTotal>, labelFmt: DateTimeFormatter) {
    val colors = MaterialTheme.colorScheme

    val sorted = done.map { it.minutes }.sorted()
    val median = percentile(sorted, 0.5)
    val low = if (sorted.size >= 4) percentile(sorted, 0.25) else median * 0.75
    val high = if (sorted.size >= 4) percentile(sorted, 0.75) else median * 1.25
    val maxV = (shown.map { it.minutes } + high).max().coerceAtLeast(30.0) * 1.15

    val barColor = colors.secondary.copy(alpha = 0.65f)
    val partialColor = colors.primary.copy(alpha = 0.55f)
    val bandColor = colors.primary.copy(alpha = 0.14f)
    val medianColor = colors.onSurfaceVariant

    // The plot has a fixed height of its own, so labels can never squeeze the bars.
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val h = size.height
        val slot = size.width / shown.size
        fun y(v: Double) = h - (v / maxV).toFloat() * h

        drawRect(bandColor, Offset(0f, y(high)), Size(size.width, y(low) - y(high)))
        shown.forEachIndexed { i, w ->
            val bw = slot * 0.5f
            val top = y(w.minutes)
            drawRoundRect(
                color = if (w.partial) partialColor else barColor,
                topLeft = Offset(i * slot + (slot - bw) / 2, top),
                size = Size(bw, (h - top).coerceAtLeast(2f)),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
        drawLine(
            medianColor, Offset(0f, y(median)), Offset(size.width, y(median)),
            strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
    }

    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        shown.forEach { w ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatDuration(w.minutes), style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (w.partial) FontWeight.Bold else FontWeight.Normal,
                    color = if (w.partial) colors.primary else colors.onSurface,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, textAlign = TextAlign.Center
                )
                Text(
                    if (w.partial) "So far" else w.weekStart.format(labelFmt),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, textAlign = TextAlign.Center
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        "Shaded band: your usual weekly range. Dashed line: your typical week. " +
                "The lighter bar is the current week so far.",
        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
    )
}

@Composable
private fun OutlookPanel(prediction: RiskPrediction, onNavigateToRag: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val elevated = prediction.riskLevel == "ELEVATED"
    val score = prediction.probability.coerceIn(0.0, 1.0)
    val accent = if (elevated) colors.tertiary else colors.primary

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillBadge(
                    text = if (elevated) "MAY RUN HIGHER" else "IN LINE WITH USUAL",
                    containerColor = if (elevated) colors.tertiaryContainer else colors.primaryContainer,
                    contentColor = if (elevated) colors.onTertiaryContainer else colors.onPrimaryContainer
                )
                Spacer(Modifier.width(10.dp))
                Text("Next week outlook", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = colors.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (elevated) "Based on your recent pattern, next week may run above your usual range."
                else "Based on your recent pattern, next week looks in line with your usual range.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurface
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { score.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = accent, trackColor = colors.surfaceContainerHigh
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Model score ${(score * 100).roundToInt()}%. An estimate from a random forest model, " +
                        "not a measurement, and it can be wrong. Use it as a prompt to reflect.",
                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
            )
            if (elevated) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onNavigateToRag, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Talk through next week", fontWeight = FontWeight.Bold)
                }
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
