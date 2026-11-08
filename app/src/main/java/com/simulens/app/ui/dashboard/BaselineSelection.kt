package com.simulens.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simulens.app.data.local.entity.BaselineAnchorEntity
import com.simulens.app.domain.bsmas.BsmasBand
import com.simulens.app.domain.bsmas.BsmasItem
import com.simulens.app.domain.bsmas.BsmasResult
import com.simulens.app.domain.rules.intervention.*
import com.simulens.app.ui.components.PillBadge
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class BaselineView(val label: String, val metric: BaselineMetric?) {
    SESSIONS("Sessions", BaselineMetric.SESSION_COUNT),
    LENGTH("Length", BaselineMetric.SESSION_LENGTH),
    FREQUENCY("Frequency", BaselineMetric.FREQUENCY),
    TOTAL("Total time", BaselineMetric.TOTAL_TIME),
    TIME_OF_DAY("Time of day", null)
}

@Composable
fun BaselineSection(baselines: Map<DayOfWeek, WeekdayBaseline>, spanDays: Int) {
    val colors = MaterialTheme.colorScheme
    val todayDow = remember { LocalDate.now().dayOfWeek }
    var dayIdx by rememberSaveable { mutableIntStateOf(todayDow.value - 1) }
    var viewIdx by rememberSaveable { mutableIntStateOf(0) }

    val day = DayOfWeek.of(dayIdx + 1)
    val view = BaselineView.entries[viewIdx]
    val viewMetric = view.metric
    // The combined chart can't plot buckets, so "Time of day" falls back to total time there.
    val chartMetric = viewMetric ?: BaselineMetric.TOTAL_TIME

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Day chips
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                val dev = baselines[d]?.metrics?.get(chartMetric)?.deviation
                val selected = d == day
                Surface(
                    onClick = { dayIdx = d.value - 1 },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) colors.primary else colors.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (d == todayDow) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.onPrimary else colors.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.size(6.dp).background(
                                if (dev == null) Color.Transparent
                                else levelColor(dev.level, colors.primary, colors.tertiary, colors.error, colors.secondary),
                                CircleShape
                            )
                        )
                    }
                }
            }
        }

        // Metric switcher
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BaselineView.entries.forEach { v ->
                FilterChip(
                    selected = v == view,
                    onClick = { viewIdx = v.ordinal },
                    label = { Text(v.label) }
                )
            }
        }

        val dayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val wb = baselines[day]

        if (viewMetric != null) {
            DayDetailCard(dayName, viewMetric, wb?.metrics?.get(viewMetric))
        } else {
            TimeOfDayCard(dayName, wb)
        }

        WeekComparisonCard(baselines, chartMetric, day) { dayIdx = it.value - 1 }
    }
}

@Composable
private fun BaselineHeaderCard(baselines: Map<DayOfWeek, WeekdayBaseline>, spanDays: Int) {
    val colors = MaterialTheme.colorScheme
    val established = baselines.values.any { wb ->
        wb.metrics.values.any { it.baseline?.confidence == Confidence.ESTABLISHED }
    }
    val judged = baselines.values.mapNotNull { it.metrics[BaselineMetric.TOTAL_TIME]?.deviation }
    val flagged = judged.count { it.level == DeviationLevel.ABOVE || it.level == DeviationLevel.WELL_ABOVE }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).background(colors.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Insights, null, tint = colors.primary, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Your Baseline", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text("$spanDays ${if (spanDays == 1) "day" else "days"} of data",
                            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
                PillBadge(
                    text = if (established) "ESTABLISHED" else "LEARNING",
                    containerColor = if (established) colors.primaryContainer else colors.surfaceContainerHigh,
                    contentColor = if (established) colors.onPrimaryContainer else colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    judged.isEmpty() -> "Still learning your normal. Each weekday needs at least 2 past occurrences before it can be compared."
                    flagged == 0 -> "This week is within your normal range so far."
                    else -> "$flagged of ${judged.size} days this week ran above your usual screen time."
                },
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayDetailCard(dayName: String, metric: BaselineMetric, view: WeekdayMetricView?) {
    val colors = MaterialTheme.colorScheme
    val baseline = view?.baseline
    val current = view?.current

    BaselineCard(title = "$dayName vs your previous ${dayName}s") {
        if (view == null || baseline == null) {
            Text(
                "Still learning your ${dayName}s. At least 2 past ${dayName}s with data are needed.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
            )
            return@BaselineCard
        }

        val dev = view.deviation
        val curColor = dev?.let { levelColor(it.level, colors.primary, colors.tertiary, colors.error, colors.secondary) }
            ?: colors.primary
        val values = baseline.samples.map { it.value } + listOfNotNull(current?.value)
        val labels = baseline.samples.map { it.date.dayOfMonth.toString() } +
                (if (current != null) listOf("Now") else emptyList())
        val maxV = (values + baseline.high).max().coerceAtLeast(1.0) * 1.1
        val barColor = colors.secondary.copy(alpha = 0.55f)
        val bandColor = colors.primary.copy(alpha = 0.14f)
        val medianColor = colors.onSurfaceVariant
        val lastIdx = values.lastIndex
        val hasCurrent = current != null

        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val h = size.height
            fun y(v: Double) = h - (v / maxV).toFloat() * h
            drawRect(bandColor, Offset(0f, y(baseline.high)), Size(size.width, y(baseline.low) - y(baseline.high)))
            val slot = size.width / values.size
            values.forEachIndexed { i, v ->
                val bw = slot * 0.5f
                val top = y(v)
                drawRoundRect(
                    color = if (hasCurrent && i == lastIdx) curColor else barColor,
                    topLeft = Offset(i * slot + (slot - bw) / 2, top),
                    size = Size(bw, h - top),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
            drawLine(
                medianColor, Offset(0f, y(baseline.median)), Offset(size.width, y(baseline.median)),
                strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Shaded area = your normal range (${fmt(metric, baseline.low)} to ${fmt(metric, baseline.high)}). " +
                    "Dashed line = typical ${fmt(metric, baseline.median)}.",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        InsightLine(insight(dayName, metric, view), curColor)
    }
}

@Composable
private fun TimeOfDayCard(dayName: String, wb: WeekdayBaseline?) {
    val colors = MaterialTheme.colorScheme
    BaselineCard(title = "When you use your phone on ${dayName}s") {
        val base = wb?.baselineMinsByBucket.orEmpty()
        if (base.values.sum() <= 0.0) {
            Text("Not enough ${dayName}s yet to show a time-of-day pattern.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            return@BaselineCard
        }
        val cur = wb?.currentMinsByBucket
        val maxV = (base.values + (cur?.values ?: emptyList())).max().coerceAtLeast(1.0)

        TimeBucket.entries.forEach { b ->
            val bv = base[b] ?: 0.0
            val cv = cur?.get(b)
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(bucketLabel(b), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, color = colors.onSurface)
                    Text(
                        "usual ${fmtMins(bv)}" + (cv?.let { " • this week ${fmtMins(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (bv / maxV).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = colors.secondary.copy(alpha = 0.7f), trackColor = colors.surfaceContainerHigh
                )
                if (cv != null) {
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { (cv / maxV).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = colors.primary, trackColor = colors.surfaceContainerHigh
                    )
                }
            }
        }
        val peak = base.maxByOrNull { it.value }?.key
        val total = base.values.sum()
        val lateShare = if (total > 0) (base[TimeBucket.LATE_NIGHT] ?: 0.0) / total * 100 else 0.0
        Spacer(Modifier.height(8.dp))
        InsightLine(
            "Your ${dayName}s peak in the ${peak?.let { bucketLabel(it).substringBefore(" (").lowercase() }}" +
                    (if (lateShare >= 15) ", and ${lateShare.roundToInt()}% of your usual time is late at night." else "."),
            colors.primary
        )
        Text("Lighter bar = usual (median), darker bar = this week.",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun WeekComparisonCard(
    baselines: Map<DayOfWeek, WeekdayBaseline>,
    metric: BaselineMetric,
    selected: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val days = DayOfWeek.entries
    val views = days.map { baselines[it]?.metrics?.get(metric) }
    val maxV = views.flatMap { v ->
        listOfNotNull(v?.baseline?.high, v?.baseline?.q3, v?.current?.value)
    }.maxOrNull()?.coerceAtLeast(1.0)?.times(1.15) ?: 1.0

    val bandColor = colors.primary.copy(alpha = 0.18f)
    val selColor = colors.primary.copy(alpha = 0.07f)
    val medColor = colors.onSurfaceVariant
    val lineColor = colors.primary
    val dotColors = views.map { v ->
        v?.deviation?.let { levelColor(it.level, colors.primary, colors.tertiary, colors.error, colors.secondary) }
            ?: lineColor
    }

    BaselineCard(title = "This week vs your baseline") {
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).pointerInput(Unit) {
                detectTapGestures { p -> onSelect(days[(p.x / (size.width / 7f)).toInt().coerceIn(0, 6)]) }
            }
        ) {
            val h = size.height
            val slot = size.width / 7f
            fun y(v: Double) = h - (v / maxV).toFloat() * h
            fun cx(i: Int) = i * slot + slot / 2

            drawRect(selColor, Offset((selected.value - 1) * slot, 0f), Size(slot, h))

            views.forEachIndexed { i, v ->
                val b = v?.baseline ?: return@forEachIndexed
                drawLine(bandColor, Offset(cx(i), y(b.high)), Offset(cx(i), y(b.low)),
                    strokeWidth = slot * 0.5f, cap = StrokeCap.Round)
                drawLine(medColor, Offset(cx(i) - slot * 0.22f, y(b.median)),
                    Offset(cx(i) + slot * 0.22f, y(b.median)), strokeWidth = 4f)
            }
            for (i in 0 until 6) {
                val a = views[i]?.current?.value ?: continue
                val b = views[i + 1]?.current?.value ?: continue
                drawLine(lineColor.copy(alpha = 0.5f), Offset(cx(i), y(a)), Offset(cx(i + 1), y(b)), strokeWidth = 4f)
            }
            views.forEachIndexed { i, v ->
                val c = v?.current?.value ?: return@forEachIndexed
                drawCircle(dotColors[i], radius = 9f, center = Offset(cx(i), y(c)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEach {
                Text(it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3), Modifier.weight(1f),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                    color = if (it == selected) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (it == selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Shaded bar = normal range, tick = typical, dot = this week (colored when off-norm). " +
                    "Showing ${metricName(metric)}. Tap a day to inspect it.",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
        )
    }
}

// ----------------------------- BSMAS vs behavior --------------------------------------------

private enum class Signal { ELEVATED, TYPICAL, UNKNOWN }

private fun signalFor(baselines: Map<DayOfWeek, WeekdayBaseline>, metric: BaselineMetric): Signal {
    val devs = baselines.values.mapNotNull { it.metrics[metric]?.deviation }
    if (devs.size < 2) return Signal.UNKNOWN
    val above = devs.count { it.level == DeviationLevel.ABOVE || it.level == DeviationLevel.WELL_ABOVE }
    return if (above >= 2) Signal.ELEVATED else Signal.TYPICAL
}

private fun lateNightSignal(baselines: Map<DayOfWeek, WeekdayBaseline>): Signal {
    val days = baselines.values.filter {
        it.currentMinsByBucket != null && it.metrics[BaselineMetric.TOTAL_TIME]?.baseline != null
    }
    if (days.size < 2) return Signal.UNKNOWN
    val cur = days.sumOf { it.currentMinsByBucket?.get(TimeBucket.LATE_NIGHT) ?: 0.0 }
    val base = days.sumOf { it.baselineMinsByBucket[TimeBucket.LATE_NIGHT] ?: 0.0 }
    return if (cur > base * 1.25 && cur - base >= 10.0) Signal.ELEVATED else Signal.TYPICAL
}

@Composable
fun BsmasBaselineCard(
    result: BsmasResult?,
    baselines: Map<DayOfWeek, WeekdayBaseline>,
    onRetake: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    BaselineCard(title = "Baseline vs how you feel") {
        if (result == null) {
            Text("Complete the BSMAS questionnaire to compare your habits with how you feel about them.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            TextButton(onClick = onRetake) { Text("Take questionnaire") }
            return@BaselineCard
        }

        val signals = mapOf(
            BsmasItem.SALIENCE to signalFor(baselines, BaselineMetric.SESSION_COUNT),
            BsmasItem.TOLERANCE to signalFor(baselines, BaselineMetric.SESSION_LENGTH),
            BsmasItem.MOOD to lateNightSignal(baselines),
            BsmasItem.RELAPSE to signalFor(baselines, BaselineMetric.FREQUENCY)
        )
        val anyElevated = signals.values.any { it == Signal.ELEVATED }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("${result.total} / 30", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = colors.onSurface)
            val elevated = result.band == BsmasBand.ELEVATED
            PillBadge(
                text = when (result.band) {
                    BsmasBand.LOWER -> "LOWER"
                    BsmasBand.MODERATE -> "MODERATE"
                    BsmasBand.ELEVATED -> "ELEVATED"
                },
                containerColor = if (elevated) colors.errorContainer else colors.primaryContainer,
                contentColor = if (elevated) colors.onErrorContainer else colors.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(12.dp))

        BsmasItem.entries.forEach { item ->
            val sig = signals[item]
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                    modifier = Modifier.weight(1f))
                Text("${result.score(item)}/5", style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold, color = colors.onSurface)
                Spacer(Modifier.width(10.dp))
                Text(
                    when (sig) {
                        null -> "self-report only"
                        Signal.ELEVATED -> "above your norm"
                        Signal.TYPICAL -> "typical for you"
                        Signal.UNKNOWN -> "learning"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sig == Signal.ELEVATED) colors.error else colors.onSurfaceVariant,
                    modifier = Modifier.width(96.dp), textAlign = TextAlign.End
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        InsightLine(
            when {
                result.band == BsmasBand.ELEVATED && !anyElevated ->
                    "Your usage is steady against your own norm, but your answers suggest it is affecting you. A stable pattern isn't always a healthy one."
                result.band == BsmasBand.ELEVATED ->
                    "Your answers and this week's behavior both point the same way. Nudges are set to fire earlier."
                result.band == BsmasBand.LOWER && anyElevated ->
                    "You rate your use as low-concern, but parts of this week ran above your norm. Worth a quick check-in with yourself."
                else -> "Your answers and your recent habits look broadly consistent."
            },
            if (result.band == BsmasBand.ELEVATED) colors.error else colors.primary
        )
        Spacer(Modifier.height(6.dp))
        Text("A self-reflection aid, not a diagnosis. Your answers stay on this device.",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        TextButton(onClick = onRetake) { Text("Retake questionnaire") }
    }
}

// ----------------------------- shared helpers ------------------------------------------------

@Composable
private fun BaselineCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = colors.onSurface)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
@Composable
fun AnchorCard(
    anchor: BaselineAnchorEntity?,
    recent: PeriodStats?,
    bsmasNow: Int?,
    spanDays: Int,
    onReset: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }

    BaselineCard(title = "Since you started") {
        if (anchor == null || recent == null) {
            val left = (AnchorCalculator.WINDOW_DAYS - spanDays).coerceAtLeast(0)
            Text(
                if (left > 0) "Your starting point is still being recorded. It freezes in about $left days, and every change after that is measured against it."
                else "Waiting for enough usage days to freeze your starting point.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
            )
            return@BaselineCard
        }

        val start = PeriodStats(AnchorCalculator.WINDOW_DAYS, anchor.activeDays, anchor.medianDailyMins,
            anchor.medianDailySessions, anchor.medianSessionMins, anchor.lateNightShare)
        val c = AnchorCalculator.compare(start, recent, anchor.bsmasTotal, bsmasNow)

        val (label, accent) = when (c.trajectory) {
            Trajectory.IMPROVING -> "Improving" to colors.primary
            Trajectory.STEADY -> "Holding steady" to colors.onSurfaceVariant
            Trajectory.SLIPPING -> "Slipping" to colors.error
        }
        Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
        Text("Last 7 days vs your first ${AnchorCalculator.WINDOW_DAYS} days",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        AnchorRow("Daily screen time", fmtMins(start.medianDailyMins), fmtMins(recent.medianDailyMins), c.minsPct)
        AnchorRow("Sessions per day", start.medianDailySessions.roundToInt().toString(),
            recent.medianDailySessions.roundToInt().toString(), c.sessionsPct)
        AnchorRow("Typical visit", fmtMins(start.medianSessionMins), fmtMins(recent.medianSessionMins), c.lengthPct)
        AnchorRow("Late-night share", "${(start.lateNightShare * 100).roundToInt()}%",
            "${(recent.lateNightShare * 100).roundToInt()}%", null, c.lateShareDelta)
        if (anchor.bsmasTotal != null && bsmasNow != null) {
            AnchorRow("BSMAS score", "${anchor.bsmasTotal}", "$bsmasNow", null, c.bsmasDelta?.toDouble())
        } else {
            Text("Take the BSMAS questionnaire to add how you feel to this comparison.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text("Your starting point is frozen. It doesn't move as your habits change. A self-reflection aid, not a diagnosis.",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        TextButton(onClick = { confirm = true }) { Text("Reset starting point") }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Reset your starting point?") },
        text = { Text("A new 28-day starting point will be recorded from today. Use this after a big change, like a new term or a long trip.") },
        confirmButton = { Button(onClick = { confirm = false; onReset() }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
    )
}

@Composable
private fun AnchorRow(label: String, from: String, to: String, pct: Double?, delta: Double? = null) {
    val colors = MaterialTheme.colorScheme
    val change = pct ?: delta
    val tint = when {
        change == null || kotlin.math.abs(change) < 1.0 -> colors.onSurfaceVariant
        change < 0 -> colors.primary       // lower is better on every row
        else -> colors.error
    }
    val txt = when {
        pct != null -> "${if (pct > 0) "+" else ""}${pct.roundToInt()}%"
        delta != null -> "${if (delta > 0) "+" else ""}${delta.roundToInt()}"
        else -> ""
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        Text("$from → $to", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(txt, Modifier.width(48.dp), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
    }
}
@Composable
private fun InsightLine(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Text(text, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun levelColor(l: DeviationLevel, ok: Color, above: Color, wellAbove: Color, below: Color) = when (l) {
    DeviationLevel.WITHIN -> ok
    DeviationLevel.ABOVE -> above
    DeviationLevel.WELL_ABOVE -> wellAbove
    DeviationLevel.BELOW -> below
}

private fun metricName(m: BaselineMetric) = when (m) {
    BaselineMetric.SESSION_COUNT -> "session count"
    BaselineMetric.SESSION_LENGTH -> "session length"
    BaselineMetric.FREQUENCY -> "session frequency"
    BaselineMetric.TOTAL_TIME -> "total time"
}

private fun bucketLabel(b: TimeBucket) = when (b) {
    TimeBucket.MORNING -> "Morning (5 AM - 12 PM)"
    TimeBucket.AFTERNOON -> "Afternoon (12 - 5 PM)"
    TimeBucket.EVENING -> "Evening (5 - 10 PM)"
    TimeBucket.LATE_NIGHT -> "Late night (10 PM - 5 AM)"
}

private fun fmtMins(m: Double): String = when {
    m < 1.0 -> "${(m * 60).roundToInt()}s"
    m < 60.0 -> "${m.roundToInt()} min"
    else -> "${(m / 60).toInt()}h ${(m % 60).roundToInt().toString().padStart(2, '0')}m"
}

private fun fmt(m: BaselineMetric, v: Double): String = when (m) {
    BaselineMetric.SESSION_COUNT -> v.roundToInt().toString()
    BaselineMetric.FREQUENCY -> String.format(Locale.US, "%.1f/hr", v)
    BaselineMetric.SESSION_LENGTH, BaselineMetric.TOTAL_TIME -> fmtMins(v)
}

private fun insight(dayName: String, metric: BaselineMetric, view: WeekdayMetricView): String {
    val b = view.baseline ?: return "Still learning your ${dayName}s."
    val usual = when (metric) {
        BaselineMetric.SESSION_COUNT -> "about ${fmt(metric, b.median)} sessions"
        BaselineMetric.SESSION_LENGTH -> "a typical session of ${fmt(metric, b.median)}"
        BaselineMetric.FREQUENCY -> "${fmt(metric, b.median)} sessions per active hour"
        BaselineMetric.TOTAL_TIME -> "${fmt(metric, b.median)} of screen time"
    }
    val cur = view.current
        ?: return "Your ${dayName}s usually have $usual. This $dayName hasn't happened yet."
    val value = when (metric) {
        BaselineMetric.SESSION_COUNT -> "${fmt(metric, cur.value)} sessions"
        BaselineMetric.FREQUENCY -> "${fmt(metric, cur.value)} sessions per active hour"
        else -> fmt(metric, cur.value)
    }
    val whenText = if (view.isPartialDay) "So far today" else "This $dayName"
    val dev = view.deviation
    val pct = dev?.pctVsMedian?.let { abs(it).roundToInt() }
    val verdict = when (dev?.level) {
        DeviationLevel.WELL_ABOVE -> "well above your norm" + (pct?.let { " ($it% over)" } ?: "")
        DeviationLevel.ABOVE -> "above your norm" + (pct?.let { " ($it% over)" } ?: "")
        DeviationLevel.BELOW -> "below your norm" + (pct?.let { " ($it% under)" } ?: "")
        else -> "within your normal range"
    }
    return "Your ${dayName}s usually have $usual. $whenText: $value, $verdict."
}