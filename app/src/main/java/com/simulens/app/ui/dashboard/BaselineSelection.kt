package com.simulens.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Insights
import com.simulens.app.ml.RiskPrediction
import com.simulens.app.ui.dashboard.WeekTotal
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import com.simulens.app.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.format.DateTimeFormatter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.abs
import kotlin.math.roundToInt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_PAST_DAYS = 3

private enum class UsualView(val label: String, val metric: BaselineMetric?) {
    TOTAL("Total time", BaselineMetric.TOTAL_TIME),
    SESSIONS("Sessions", BaselineMetric.SESSION_COUNT),
    TIME_OF_DAY("Time of day", null)
}



@Composable
fun BaselineSection(baselines: Map<DayOfWeek, WeekdayBaseline>, spanDays: Int = 28, onOpenNudgeSettings: (() -> Unit)? = null) {
    UsualSection(baselines = baselines, onOpenNudgeSettings = onOpenNudgeSettings)
}

@Composable
fun UsualSection(
    baselines: Map<DayOfWeek, WeekdayBaseline>,
    onOpenNudgeSettings: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val today = remember { LocalDate.now().dayOfWeek }
    var dayIdx by rememberSaveable { mutableIntStateOf(today.value - 1) }
    var viewIdx by rememberSaveable { mutableIntStateOf(0) }

    val day = DayOfWeek.of(dayIdx + 1)
    val view = UsualView.entries[viewIdx]
    val metric = view.metric
    val chartMetric = metric ?: BaselineMetric.TOTAL_TIME
    val dayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val wb = baselines[day]

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---------- Day chips ----------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                val mv = baselines[d]?.metrics?.get(chartMetric)
                val dev = mv?.takeIf { !it.isPartialDay }?.deviation
                val hasData = mv?.baseline != null
                val selected = d == day
                Surface(
                    onClick = { dayIdx = d.value - 1 },
                    modifier = Modifier.weight(1f).alpha(if (hasData || selected) 1f else 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) colors.primary else colors.surfaceContainerHigh,
                    border = if (d == today && !selected) BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)) else null
                ) {
                    Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (d == today) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.onPrimary else colors.onSurface
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier.size(8.dp)
                                .background(if (dev == null) Color.Transparent else levelColor(dev.level), CircleShape)
                                .then(if (selected && dev != null) Modifier.border(1.5.dp, colors.onPrimary, CircleShape) else Modifier)
                        )
                    }
                }
            }
        }

        // ---------- Metric chips ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsualView.entries.forEach { v ->
                FilterChip(
                    selected = v == view,
                    onClick = { viewIdx = v.ordinal },
                    label = { Text(v.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = colors.primary,
                        selectedContainerColor = colors.primary,
                        selectedLabelColor = colors.onPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = v == view,
                        borderColor = colors.primary.copy(alpha = 0.6f), selectedBorderColor = Color.Transparent
                    )
                )
            }
        }

        if (metric != null) DayDetail(dayName, metric, wb?.metrics?.get(metric))
        else TimeOfDay(dayName, wb, onOpenNudgeSettings)

        Spacer(Modifier.height(4.dp))
        WeekComparisonCard(baselines, chartMetric, day) { dayIdx = it.value - 1 }
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

    val bandColor = IceBlue.copy(alpha = 0.18f)
    val selColor = IceBlue.copy(alpha = 0.08f)
    val medColor = colors.onSurfaceVariant
    val lineColor = IceBlue
    val dotColors = views.map { v ->
        v?.deviation?.let { levelColor(it.level) } ?: lineColor
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

@Composable
private fun DayDetail(dayName: String, metric: BaselineMetric, view: WeekdayMetricView?) {
    val colors = MaterialTheme.colorScheme
    val baseline = view?.baseline

    InsightCard {
        Text(
            "$dayName vs your usual", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = colors.onSurface
        )

        if (view == null || baseline == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Still learning your ${dayName}s. A few more are needed before comparing.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
            )
            return@InsightCard
        }

        val enough = baseline.samples.size >= MIN_PAST_DAYS
        val samples = baseline.samples.takeLast(5)
        val current = view.current
        val (text, tone) = dayInsight(dayName, metric, view, enough)

        Spacer(Modifier.height(10.dp))
        InsightLine(text, tone.color())
        Spacer(Modifier.height(14.dp))

        val values = samples.map { it.value } + listOfNotNull(current?.value)
        val labelFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH) }
        val labels = samples.map { it.date.format(labelFmt) } + (if (current != null) listOf("Now") else emptyList())
        val maxV = (values + baseline.high).max().coerceAtLeast(1.0) * 1.1
        val lastIdx = values.lastIndex
        val hasCurrent = current != null

        val pastColor = IceBlue.copy(alpha = 0.80f)
        val bandColor = IceBlue.copy(alpha = 0.14f)
        val medianColor = colors.onSurfaceVariant
        val description = labels.zip(values).joinToString(". ") { (l, v) -> "$l: ${fmt(metric, v)}" }

        Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
            val h = size.height
            val slot = size.width / values.size
            fun y(v: Double) = h - (v / maxV).toFloat() * h

            if (enough) {
                drawRoundRect(
                    bandColor, Offset(0f, y(baseline.high)), Size(size.width, y(baseline.low) - y(baseline.high)),
                    CornerRadius(6.dp.toPx())
                )
            }
            values.forEachIndexed { i, v ->
                val bw = slot * 0.5f
                val top = y(v)
                drawRoundRect(
                    color = if (hasCurrent && i == lastIdx) InkAmber else pastColor,
                    topLeft = Offset(i * slot + (slot - bw) / 2, top),
                    size = Size(bw, (h - top).coerceAtLeast(2.dp.toPx())),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
            }
            if (enough) {
                drawLine(
                    medianColor, Offset(0f, y(baseline.median)), Offset(size.width, y(baseline.median)),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEach {
                Text(
                    it, Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (enough) {
                LegendItem(IceBlue.copy(alpha = 0.25f), "Usual range")
                LegendItem(colors.onSurfaceVariant, "Typical", dashed = true)
            }
            if (hasCurrent) LegendItem(InkAmber, "This $dayName")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Based on ${baseline.samples.size} previous ${dayName}s" +
                    if (enough) "" else ". Needs $MIN_PAST_DAYS before it can say what's usual.",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
        )
    }
}

private fun dayInsight(dayName: String, metric: BaselineMetric, view: WeekdayMetricView, enough: Boolean): Pair<String, Tone> {
    val b = view.baseline ?: return "Still learning your ${dayName}s." to Tone.FLAT
    val cur = view.current
    if (!enough) {
        return if (cur == null) "Still learning your ${dayName}s." to Tone.FLAT
        else "Still learning your ${dayName}s. So far this one is ${fmt(metric, cur.value)}." to Tone.FLAT
    }
    val usual = when (metric) {
        BaselineMetric.SESSION_COUNT -> "about ${fmt(metric, b.median)} sessions"
        BaselineMetric.TOTAL_TIME -> "${fmt(metric, b.median)} of screen time"
        else -> fmt(metric, b.median)
    }
    if (cur == null) return "Your ${dayName}s usually have $usual. This $dayName hasn't happened yet." to Tone.FLAT
    val value = if (metric == BaselineMetric.SESSION_COUNT) "${fmt(metric, cur.value)} sessions" else fmt(metric, cur.value)

    if (view.isPartialDay) return "So far today: $value. A full $dayName usually has $usual." to Tone.FLAT

    val dev = view.deviation
    val pct = dev?.pctVsMedian?.let { abs(it).roundToInt() }
    return when (dev?.level) {
        DeviationLevel.WELL_ABOVE, DeviationLevel.ABOVE ->
            "This $dayName: $value, above your usual" + (pct?.let { " ($it% over)" } ?: "") + ". Usually $usual." to Tone.WATCH
        DeviationLevel.BELOW ->
            "This $dayName: $value, below your usual" + (pct?.let { " ($it% under)" } ?: "") + ". Usually $usual." to Tone.GOOD
        else -> "This $dayName: $value, within your usual. Usually $usual." to Tone.GOOD
    }
}

@Composable
private fun TimeOfDay(dayName: String, wb: WeekdayBaseline?, onOpenNudgeSettings: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val base = wb?.baselineMinsByBucket.orEmpty()

    InsightCard {
        Text(
            "When you use your phone on ${dayName}s", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = colors.onSurface
        )
        Spacer(Modifier.height(10.dp))

        if (base.values.sum() <= 0.0) {
            Text(
                "Not enough ${dayName}s yet to show a time-of-day pattern.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
            )
            return@InsightCard
        }

        val cur = wb?.currentMinsByBucket
        val maxV = (base.values + (cur?.values ?: emptyList())).max().coerceAtLeast(1.0)
        val peak = base.maxByOrNull { it.value }?.key
        val total = base.values.sum()
        val lateShare = if (total > 0) (base[TimeBucket.LATE_NIGHT] ?: 0.0) / total * 100 else 0.0

        InsightLine(
            "Your ${dayName}s peak in the ${peak?.let { bucketLabel(it).substringBefore(" (").lowercase() }}" +
                    (if (lateShare >= 15) ", and ${lateShare.roundToInt()}% of your usual time is late at night." else "."),
            IceBlue
        )
        if (lateShare >= 15 && onOpenNudgeSettings != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenNudgeSettings,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, InkAmber.copy(alpha = 0.5f))
            ) {
                Text("Set up sleep-hours nudges", fontWeight = FontWeight.Bold, color = InkAmber)
            }
        }

        Spacer(Modifier.height(12.dp))
        TimeBucket.entries.forEach { b ->
            val bv = base[b] ?: 0.0
            val cv = cur?.get(b)
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(bucketLabel(b), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        color = colors.onSurface, modifier = Modifier.weight(1f))
                    Text(
                        fmtMins(bv) + (cv?.let { " · now ${fmtMins(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                SoftBar((bv / maxV).toFloat(), IceBlue.copy(alpha = 0.8f))
                if (cv != null) {
                    Spacer(Modifier.height(3.dp))
                    SoftBar((cv / maxV).toFloat(), InkAmber)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem(IceBlue.copy(alpha = 0.8f), "Usual")
            if (cur != null) LegendItem(InkAmber, "This $dayName")
        }
    }
}

@Composable
private fun SoftBar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().background(color, CircleShape))
    }
}

// ----------------------------- BSMAS vs behavior --------------------------------------------

private enum class Signal { ELEVATED, TYPICAL, UNKNOWN }

private fun signalFor(baselines: Map<DayOfWeek, WeekdayBaseline>, metric: BaselineMetric): Signal {
    val devs = baselines.values.mapNotNull { it.metrics[metric]?.deviation }
    if (devs.isEmpty()) return Signal.TYPICAL
    val above = devs.count { it.level == DeviationLevel.ABOVE || it.level == DeviationLevel.WELL_ABOVE }
    return if (above >= 1) Signal.ELEVATED else Signal.TYPICAL
}

private fun lateNightSignal(baselines: Map<DayOfWeek, WeekdayBaseline>): Signal {
    val days = baselines.values.filter {
        it.currentMinsByBucket != null || it.metrics[BaselineMetric.TOTAL_TIME]?.baseline != null
    }
    if (days.isEmpty()) return Signal.TYPICAL
    val cur = days.sumOf { it.currentMinsByBucket?.get(TimeBucket.LATE_NIGHT) ?: 0.0 }
    val base = days.sumOf { it.baselineMinsByBucket[TimeBucket.LATE_NIGHT] ?: 0.0 }
    return if (cur > base * 1.25 && cur - base >= 10.0) Signal.ELEVATED else Signal.TYPICAL
}

private enum class Mood { AGREE, NEUTRAL, NOTICE }

private fun summaryFor(band: BsmasBand, phoneElevated: Boolean?): Pair<String, Mood> = when {
    phoneElevated == null ->
        "Your phone check is still learning. Your answers are saved and will be compared once it's ready." to Mood.NEUTRAL

    band == BsmasBand.LOWER && !phoneElevated ->
        "What you feel and what your phone shows agree: nothing stands out." to Mood.AGREE
    band == BsmasBand.LOWER ->
        "You feel fine, but your phone activity shows signs of heavier use. Worth a quick look." to Mood.NOTICE

    band == BsmasBand.MODERATE && !phoneElevated ->
        "You report some concern, and your phone activity looks typical." to Mood.NEUTRAL
    band == BsmasBand.MODERATE ->
        "You report some concern, and your phone activity shows signs of heavier use." to Mood.NOTICE

    !phoneElevated ->
        "Your phone activity looks typical, but you feel affected. How you feel counts, so your nudges are set to start earlier." to Mood.NOTICE
    else ->
        "Both point the same way. This could be a good moment to talk it through." to Mood.NOTICE
}

@Composable
fun BsmasBaselineCard(
    result: BsmasResult?,
    prediction: RiskPrediction? = null,
    baselines: Map<DayOfWeek, WeekdayBaseline>,
    onRetake: () -> Unit,
    onTalk: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    var showItems by rememberSaveable { mutableStateOf(false) }

    BaselineCard(title = "Psychological Sensitivity Alignment") {
        if (result == null) {
            Text(
                "Answer the short check-in to see how your self-reported psychological feelings line up with hardware telemetry.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRetake,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InkAmber, contentColor = Color(0xFF0B1220))
            ) {
                Text("Take Check-in Assessment", fontWeight = FontWeight.Bold)
            }
            return@BaselineCard
        }

        val phoneElevated = prediction?.let { it.riskLevel == "ELEVATED" }
        val (summary, mood) = summaryFor(result.band, phoneElevated)

        // ---------- How you feel ----------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Self-Reported Score", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(
                    "${result.total} / 30", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = InkAmber
                )
            }
            val (text, bg, fg) = when (result.band) {
                BsmasBand.LOWER -> Triple("LOWER CONCERN", IceBlue, Color(0xFF0B1220))
                BsmasBand.MODERATE -> Triple("SOME CONCERN", IceBlueLight, Color(0xFF0B1220))
                BsmasBand.ELEVATED -> Triple("HIGHER CONCERN", InkAmber, Color(0xFF0B1220))
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bg
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- Diagnostic Alignment Takeaway Box ----------
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainer,
            border = BorderStroke(1.dp, if (mood == Mood.NOTICE) InkAmber.copy(alpha = 0.6f) else colors.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                modifier = Modifier.padding(14.dp)
            )
        }

        if (onTalk != null && mood == Mood.NOTICE && phoneElevated == true) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onTalk,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InkAmber, contentColor = Color(0xFF0B1220)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color(0xFF0B1220), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Talk it through with Buddy", fontWeight = FontWeight.Bold)
            }
        }

        // ---------- How Answers Tie to Interventions Box ----------
        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainer,
            border = BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = InkAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "How Your Answers Tie to Interventions",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (result.band) {
                        BsmasBand.ELEVATED -> "Your self-assessment indicates higher concern. To protect your focus, real-time nudges activate earlier: as soon as your usage reaches 50% above your usual baseline."
                        BsmasBand.MODERATE -> "Your self-assessment indicates moderate concern. Real-time nudges activate when your usage reaches 75% above your usual baseline."
                        BsmasBand.LOWER -> "Your self-assessment indicates lower concern. Real-time nudges activate when your usage reaches 100% above your usual baseline (double your normal)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        // ---------- Optional detail: how each answer lines up with a habit ----------
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showItems = !showItems }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Icon(
                if (showItems) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (showItems) "Hide details" else "How each answer lines up",
                color = colors.primary, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }

        AnimatedVisibility(visible = showItems) {
            val links: Map<BsmasItem, Pair<String, Signal>> = mapOf(
                BsmasItem.SALIENCE to ("Unlocks and sessions" to signalFor(baselines, BaselineMetric.SESSION_COUNT)),
                BsmasItem.TOLERANCE to ("Visit length" to signalFor(baselines, BaselineMetric.SESSION_LENGTH)),
                BsmasItem.MOOD to ("Late-night use" to lateNightSignal(baselines)),
                BsmasItem.RELAPSE to ("Usage spikes" to signalFor(baselines, BaselineMetric.FREQUENCY))
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "These are loose links. A habit can sit above your usual for many reasons, and your answers don't depend on it.",
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                BsmasItem.entries.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                    ItemLine(item.label, result.score(item), links[item])
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "A self-reflection aid, not a diagnosis. Your answers stay on this device.",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
        )
        TextButton(onClick = onRetake, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Retake assessment check-in", color = InkAmber, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Two lines, no fixed widths, so it survives large font sizes. */
@Composable
private fun ItemLine(label: String, score: Int, link: Pair<String, Signal>?) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text("$score/5", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = InkAmber)
        }
        Spacer(Modifier.height(3.dp))
        if (link == null) {
            Text("Only your answers can show this", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        } else {
            val (habit, signal) = link
            val (text, bg, fg) = when (signal) {
                Signal.ELEVATED -> Triple("above your usual", InkAmber, Color(0xFF0B1220))
                Signal.TYPICAL -> Triple("typical for you", IceBlue, Color(0xFF0B1220))
                Signal.UNKNOWN -> Triple("learning", colors.surfaceContainerHigh, colors.onSurfaceVariant)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = bg
            ) {
                Text(
                    text = "$habit: $text",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 2,
                    softWrap = true
                )
            }
        }
    }
}

// ----------------------------- shared helpers ------------------------------------------------




@Composable
fun AnchorCard(
    anchor: BaselineAnchorEntity?,
    recent: PeriodStats?,
    weeklyTotals: List<WeekTotal> = emptyList(),
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

        val start = PeriodStats(
            AnchorCalculator.WINDOW_DAYS, anchor.activeDays, anchor.medianDailyMins,
            anchor.medianDailySessions, anchor.medianSessionMins, anchor.lateNightShare
        )
        val c = AnchorCalculator.compare(start, recent, anchor.bsmasTotal, bsmasNow)

        val (label, accent, trendIcon) = when (c.trajectory) {
            Trajectory.IMPROVING -> Triple("Improving", colors.primary, Icons.Rounded.TrendingDown)
            Trajectory.STEADY -> Triple("Holding steady", colors.onSurfaceVariant, Icons.Rounded.TrendingFlat)
            Trajectory.SLIPPING -> Triple("Slipping", colors.error, Icons.Rounded.TrendingUp)
        }

        // ---------- Headline ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(trendIcon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    label, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = accent
                )
                Text(
                    "Last 7 days vs your first ${AnchorCalculator.WINDOW_DAYS} days",
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---------- Trajectory chart using REAL weekly tracking data ----------
        TrajectoryChart(
            startMins = start.medianDailyMins,
            recentMins = recent.medianDailyMins,
            weeklyTotals = weeklyTotals,
            accent = accent
        )

        Spacer(Modifier.height(16.dp))

        // ---------- Comparison rows ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp)
        ) {
            AnchorRow("Daily screen time", fmtMins(start.medianDailyMins), fmtMins(recent.medianDailyMins), c.minsPct)
            AnchorDivider()
            AnchorRow(
                "Sessions per day",
                start.medianDailySessions.roundToInt().toString(),
                recent.medianDailySessions.roundToInt().toString(), c.sessionsPct
            )
            AnchorDivider()
            AnchorRow("Typical visit", fmtMins(start.medianSessionMins), fmtMins(recent.medianSessionMins), c.lengthPct)
            AnchorDivider()
            AnchorRow(
                "Late-night share",
                "${(start.lateNightShare * 100).roundToInt()}%",
                "${(recent.lateNightShare * 100).roundToInt()}%",
                pct = null,
                delta = c.lateShareDelta?.times(100)
            )
            if (anchor.bsmasTotal != null && bsmasNow != null) {
                AnchorDivider()
                AnchorRow(
                    "BSMAS score", "${anchor.bsmasTotal}", "$bsmasNow",
                    pct = null, delta = c.bsmasDelta?.toDouble()
                )
            }
        }

        if (anchor.bsmasTotal == null || bsmasNow == null) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.primary.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Take the BSMAS questionnaire to add how you feel to this comparison.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurface
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Your starting point is frozen. It doesn't move as your habits change. A self-reflection aid, not a diagnosis.",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
        )
        TextButton(onClick = { confirm = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text("Reset starting point", color = colors.primary, fontWeight = FontWeight.SemiBold)
        }
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
fun AnchorRow(
    label: String,
    start: String,
    now: String,
    pct: Double? = null,
    delta: Double? = null,
    deltaUnit: String = " pts"
) {
    val colors = MaterialTheme.colorScheme
    val change = pct ?: delta
    val flatThreshold = if (pct != null) 3.0 else 1.0

    val tone = when {
        change == null || kotlin.math.abs(change) < flatThreshold -> Tone.FLAT
        change < 0 -> Tone.GOOD
        else -> Tone.BAD
    }
    val accent = when (tone) {
        Tone.GOOD -> colors.primary
        Tone.BAD, Tone.WATCH -> colors.error
        Tone.FLAT -> colors.onSurfaceVariant
    }
    val pillText = when {
        change == null || tone == Tone.FLAT -> "No change"
        pct != null -> "${abs(pct).roundToInt()}%"
        else -> "${abs(delta!!).roundToInt()}$deltaUnit"
    }
    val pillIcon = when {
        tone == Tone.FLAT -> Icons.Rounded.Remove
        change!! < 0 -> Icons.Rounded.ArrowDownward
        else -> Icons.Rounded.ArrowUpward
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        start, style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        "  →  ", style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        now, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = colors.onSurface
                    )
                }
            }
            ChangePill(text = pillText, icon = pillIcon, accent = accent)
        }

        if (pct != null) {
            Spacer(Modifier.height(8.dp))
            ChangeBar(
                ratio = (1f + (pct / 100.0).toFloat()),
                fill = accent,
                track = colors.onSurface.copy(alpha = 0.08f),
                tick = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChangePill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color) {
    Row(
        modifier = Modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun ChangeBar(ratio: Float, fill: Color, track: Color, tick: Color) {
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val maxRatio = 1.5f
        val barH = 6.dp.toPx()
        val top = (size.height - barH) / 2f
        val radius = CornerRadius(barH / 2f)

        drawRoundRect(track, Offset(0f, top), Size(size.width, barH), radius)

        val fillW = size.width * (ratio.coerceIn(0.03f, maxRatio) / maxRatio)
        drawRoundRect(fill, Offset(0f, top), Size(fillW, barH), radius)

        val tx = size.width / maxRatio
        drawLine(tick, Offset(tx, 0f), Offset(tx, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun AnchorDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun TrajectoryChart(
    startMins: Double,
    recentMins: Double,
    weeklyTotals: List<WeekTotal> = emptyList(),
    accent: Color
) {
    val colors = MaterialTheme.colorScheme

    val realWeeklyAverages = weeklyTotals.takeLast(6).map { week ->
        if (week.partial) recentMins else (week.minutes / 7.0).coerceAtLeast(0.0)
    }

    val points = if (realWeeklyAverages.size >= 2) {
        listOf(startMins) + realWeeklyAverages.drop(1)
    } else {
        listOf(startMins, (startMins + recentMins) / 2.0, recentMins)
    }
    val maxVal = (points.maxOrNull() ?: 180.0).coerceAtLeast(30.0) * 1.2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(
            "Daily screen time", style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold, color = colors.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 16.dp, height = 2.dp)) {
                drawLine(
                    colors.onSurfaceVariant, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "Your starting point (${fmtMins(startMins)})",
                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width
            val h = size.height
            val pad = 10.dp.toPx()
            fun y(v: Double) = pad + (h - 2 * pad) * (1f - (v / maxVal).toFloat().coerceIn(0f, 1f))

            repeat(3) { i ->
                val gy = pad + (h - 2 * pad) * (i / 2f)
                drawLine(
                    colors.onSurface.copy(alpha = 0.05f), Offset(0f, gy), Offset(w, gy),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val anchorY = y(startMins)
            drawLine(
                colors.onSurfaceVariant.copy(alpha = 0.6f), Offset(0f, anchorY), Offset(w, anchorY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            )

            val pts = points.mapIndexed { i, v -> Offset(i / (points.size - 1f) * w, y(v)) }

            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }
            val fill = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                lineTo(w, h); lineTo(0f, h); close()
            }

            drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(
                line, accent,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            drawCircle(colors.surfaceContainer, 5.dp.toPx(), pts.first())
            drawCircle(accent, 5.dp.toPx(), pts.first(), style = Stroke(2.dp.toPx()))

            val end = pts.last()
            drawCircle(colors.secondary.copy(alpha = 0.25f), 11.dp.toPx(), end)
            drawCircle(colors.secondary, 6.dp.toPx(), end)
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Start", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(
                "Now · ${fmtMins(recentMins)}", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = colors.secondary
            )
        }
    }
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