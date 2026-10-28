package com.simulens.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Colour roles. Calm to alarming reads primary -> tertiary -> error.
// ---------------------------------------------------------------------------------------------

@Composable
@ReadOnlyComposable
fun RiskLevel.accent(): Color {
    val c = MaterialTheme.colorScheme
    return when (this) {
        RiskLevel.LOW -> c.primary
        RiskLevel.MODERATE -> c.tertiary
        RiskLevel.HIGH, RiskLevel.VERY_HIGH -> c.error
    }
}

@Composable
@ReadOnlyComposable
fun FrequencyBand.accent(): Color {
    val c = MaterialTheme.colorScheme
    return when (this) {
        FrequencyBand.NORMAL -> c.primary
        FrequencyBand.HIGH -> c.tertiary
        FrequencyBand.VERY_HIGH -> c.error
    }
}

@Composable
@ReadOnlyComposable
private fun ReasonTone?.dotColor(): Color {
    val c = MaterialTheme.colorScheme
    return when (this) {
        ReasonTone.POSITIVE -> c.primary
        ReasonTone.NEGATIVE -> c.tertiary
        null -> c.outline
    }
}

// ---------------------------------------------------------------------------------------------
// The whole redesigned dashboard body, in the order it should appear.
// ---------------------------------------------------------------------------------------------

@Composable
fun WellnessDashboardSection(state: DashboardState, modifier: Modifier = Modifier) {
    val w = state.wellness
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WellnessStatusCard(result = w, timeline = state.scoreTimeline)

        TodayVsBaselineCard(result = w, hasClasses = state.dayStrip.classes.isNotEmpty())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenTimeMetricCard(
                minutes = w.minutes,
                baselineMinutes = w.baselineMinutes,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            SessionFrequencyMetricCard(
                sessions = w.sessions,
                band = w.sessionBand,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        DayStripCard(strip = state.dayStrip)
    }
}

@Composable
fun DayStripCard(
    strip: DayStrip,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )

                Text(
                    text = "00:00 - 24:00",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val classColor = colors.primary.copy(alpha = 0.4f)
            val windDownColor = colors.tertiary.copy(alpha = 0.3f)
            val appUseColor = colors.secondary
            val nowColor = colors.error

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceContainerHighest)
            ) {
                val w = size.width
                val h = size.height

                strip.classes.forEach { b ->
                    val startX = w * (b.startMin / 1440f)
                    val rectWidth = max(w * ((b.endMin - b.startMin) / 1440f), 4f)
                    drawRect(
                        color = classColor,
                        topLeft = Offset(startX, 0f),
                        size = androidx.compose.ui.geometry.Size(rectWidth, h)
                    )
                }

                strip.windDown.forEach { b ->
                    val startX = w * (b.startMin / 1440f)
                    val rectWidth = max(w * ((b.endMin - b.startMin) / 1440f), 4f)
                    drawRect(
                        color = windDownColor,
                        topLeft = Offset(startX, 0f),
                        size = androidx.compose.ui.geometry.Size(rectWidth, h)
                    )
                }

                strip.sessions.forEach { b ->
                    val startX = w * (b.startMin / 1440f)
                    val rectWidth = max(w * ((b.endMin - b.startMin) / 1440f), 4f)
                    drawRect(
                        color = appUseColor,
                        topLeft = Offset(startX, 0f),
                        size = androidx.compose.ui.geometry.Size(rectWidth, h)
                    )
                }

                val nowX = w * (strip.nowMin / 1440f)
                drawLine(
                    color = nowColor,
                    start = Offset(nowX, 0f),
                    end = Offset(nowX, h),
                    strokeWidth = 6f
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Classes",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.secondary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "App Use",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.tertiary.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Wind-down",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Wellness status: score, risk level, the main reason, and how the score moved through the day.
// ---------------------------------------------------------------------------------------------

@Composable
fun WellnessStatusCard(
    result: WellnessResult,
    timeline: List<ScorePoint>,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val accent = result.level.accent()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Wellness today",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant
                )
                RiskPill(text = result.level.label, accent = accent)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                Text(
                    text = result.score.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    text = "/100",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Top) {
                ToneDot(tone = result.headlineTone, topPadding = 8.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = result.headline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            ScoreCurve(timeline = timeline, accent = accent)

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("12a", "6a", "12p", "6p", "12a").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (result.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.reasons.forEach { reason ->
                        Row(verticalAlignment = Alignment.Top) {
                            ToneDot(tone = reason.tone, topPadding = 6.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = reason.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskPill(text: String, accent: Color) {
    Surface(shape = CircleShape, color = accent.copy(alpha = 0.16f)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ToneDot(tone: ReasonTone?, topPadding: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .padding(top = topPadding)
            .size(8.dp)
            .background(tone.dotColor(), CircleShape)
    )
}

/**
 * The score from midnight to now. Guides sit at 80, 60 and 40, the edges of the risk levels,
 * so you can see when the day crossed from one level into the next.
 */
@Composable
private fun ScoreCurve(timeline: List<ScorePoint>, accent: Color) {
    val colors = MaterialTheme.colorScheme
    val guide = colors.outlineVariant
    val ringColor = colors.surfaceContainerLow
    val description = remember(timeline) {
        val first = timeline.firstOrNull()?.score
        val last = timeline.lastOrNull()?.score
        if (first != null && last != null) {
            "Wellness score through the day, from $first to $last."
        } else {
            "Wellness score through the day."
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics { contentDescription = description }
    ) {
        val w = size.width
        val h = size.height
        val padV = 6.dp.toPx()
        fun x(min: Int): Float = min.coerceIn(0, 1440) / 1440f * w
        fun y(score: Int): Float = padV + (1f - score.coerceIn(0, 100) / 100f) * (h - 2 * padV)

        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
        listOf(80, 60, 40).forEach { s ->
            drawLine(
                color = guide,
                start = Offset(0f, y(s)),
                end = Offset(w, y(s)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
        }

        if (timeline.isEmpty()) return@Canvas

        val path = Path()
        timeline.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(p.minuteOfDay), y(p.score))
            else path.lineTo(x(p.minuteOfDay), y(p.score))
        }
        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val last = timeline.last()
        val center = Offset(x(last.minuteOfDay), y(last.score))
        drawCircle(color = ringColor, radius = 7.dp.toPx(), center = center)
        drawCircle(color = accent, radius = 5.dp.toPx(), center = center)
    }
}

// ---------------------------------------------------------------------------------------------
// Today against the user's own baseline, as paired bars.
// ---------------------------------------------------------------------------------------------

@Composable
fun TodayVsBaselineCard(
    result: WellnessResult,
    hasClasses: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val baselineMin = result.baselineMinutes
    val baselineSessions = result.baselineSessions

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Today vs your baseline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))

            if (baselineMin == null || baselineSessions == null) {
                Text(
                    text = "Your baseline appears after a few days of tracking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Compared with ${result.baselineLabel} by now",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                CompareRow(
                    title = "Screen time",
                    today = result.minutes,
                    baseline = baselineMin,
                    format = { formatDuration(it) },
                    delta = signedDuration(result.minutes - baselineMin)
                )

                Spacer(modifier = Modifier.height(16.dp))

                CompareRow(
                    title = "Sessions",
                    today = result.sessions.toDouble(),
                    baseline = baselineSessions,
                    format = { it.roundToInt().toString() },
                    delta = signedCount(result.sessions - baselineSessions)
                )

                if (hasClasses) {
                    val baseLecture = result.baselineLectureMinutes ?: 0.0
                    val lectureDelta = if (baseLecture >= 1.0) {
                        signedPercent((result.lectureMinutes - baseLecture) / baseLecture * 100.0)
                    } else {
                        signedDuration(result.lectureMinutes - baseLecture)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    CompareRow(
                        title = "During lectures",
                        today = result.lectureMinutes,
                        baseline = baseLecture,
                        format = { formatDuration(it) },
                        delta = lectureDelta
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareRow(
    title: String,
    today: Double,
    baseline: Double,
    format: (Double) -> String,
    delta: String
) {
    val colors = MaterialTheme.colorScheme
    val scale = max(max(today, baseline), 1.0)
    val above = today - baseline > max(1.0, baseline * 0.05)
    val below = baseline - today > max(1.0, baseline * 0.05)

    val todayColor = if (above) colors.tertiary else colors.primary
    val deltaColor = when {
        above -> colors.tertiary
        below -> colors.primary
        else -> colors.onSurfaceVariant
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface
            )
            Text(
                text = delta,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = deltaColor
            )
        }
        ValueBar(label = "Today", fraction = today / scale, color = todayColor, value = format(today))
        ValueBar(label = "Usual", fraction = baseline / scale, color = colors.outline, value = format(baseline))
    }
}

@Composable
private fun ValueBar(label: String, fraction: Double, color: Color, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.toFloat().coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(54.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Screen time and session frequency cards
// ---------------------------------------------------------------------------------------------

@Composable
fun ScreenTimeMetricCard(
    minutes: Double,
    baselineMinutes: Double?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val delta = baselineMinutes?.let { minutes - it }
    val (deltaText, deltaColor) = when {
        delta == null -> "Building your baseline" to colors.onSurfaceVariant
        abs(delta) < 3.0 -> "On par with usual" to colors.onSurfaceVariant
        delta > 0 -> "+${durationWords(delta.roundToInt())} vs usual" to colors.tertiary
        else -> "\u2212${durationWords((-delta).roundToInt())} vs usual" to colors.primary
    }

    MetricCard(modifier = modifier) {
        Text(
            text = "Screen time",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = formatDuration(minutes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = deltaText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = deltaColor
        )
    }
}

@Composable
fun SessionFrequencyMetricCard(
    sessions: Int,
    band: FrequencyBand,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val bandColor = band.accent()
    val hint = when (band) {
        FrequencyBand.NORMAL -> "Normal is up to ${FrequencyBand.HIGH_ABOVE} a day"
        FrequencyBand.HIGH -> "High is over ${FrequencyBand.HIGH_ABOVE} a day"
        FrequencyBand.VERY_HIGH -> "Very high is over ${FrequencyBand.VERY_HIGH_ABOVE} a day"
    }

    MetricCard(modifier = modifier) {
        Text(
            text = "Session frequency",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = sessions.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = band.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = bandColor
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FrequencyBand.entries.forEach { b ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(b.accent().copy(alpha = if (b == band) 1f else 0.25f))
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

// ---------------------------------------------------------------------------------------------
// Small formatting helpers
// ---------------------------------------------------------------------------------------------

/** 40 -> "40min", 90 -> "1hr 30min". */
private fun durationWords(minutes: Int): String = formatDuration(minutes.toDouble())

private fun signedDuration(deltaMin: Double): String {
    val rounded = deltaMin.roundToInt()
    return when {
        abs(rounded) < 3 -> "On par"
        rounded > 0 -> "+${durationWords(rounded)}"
        else -> "\u2212${durationWords(-rounded)}"
    }
}

private fun signedCount(delta: Double): String {
    val rounded = delta.roundToInt()
    return when {
        rounded == 0 -> "On par"
        rounded > 0 -> "+$rounded"
        else -> "\u2212${-rounded}"
    }
}

private fun signedPercent(pct: Double): String {
    val rounded = pct.roundToInt()
    return when {
        abs(rounded) < 5 -> "On par"
        rounded > 0 -> "+$rounded%"
        else -> "\u2212${-rounded}%"
    }
}

// ---------------------------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 380, heightDp = 1300)
@Composable
private fun WellnessPreview() {
    val timeline = listOf(
        ScorePoint(0, 100), ScorePoint(360, 100), ScorePoint(450, 97), ScorePoint(540, 94),
        ScorePoint(600, 82), ScorePoint(720, 79), ScorePoint(840, 74), ScorePoint(900, 68)
    )
    val result = WellnessResult(
        score = 68,
        level = RiskLevel.MODERATE,
        headline = "18% increased usage during lecture hours",
        headlineTone = ReasonTone.NEGATIVE,
        reasons = listOf(
            WellnessReason("High session frequency: 14 sessions today", ReasonTone.NEGATIVE, 4.0),
            WellnessReason("12% reduced usage compared with your usual Tuesday by now", ReasonTone.POSITIVE, 1.2)
        ),
        minutes = 128.0,
        baselineMinutes = 88.0,
        sessions = 14,
        baselineSessions = 9.0,
        sessionBand = FrequencyBand.HIGH,
        lectureMinutes = 26.0,
        baselineLectureMinutes = 22.0,
        baselineLabel = "your usual Tuesday"
    )
    MaterialTheme {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WellnessStatusCard(result = result, timeline = timeline)
            TodayVsBaselineCard(result = result, hasClasses = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ScreenTimeMetricCard(
                    minutes = result.minutes,
                    baselineMinutes = result.baselineMinutes,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                SessionFrequencyMetricCard(
                    sessions = result.sessions,
                    band = result.sessionBand,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}