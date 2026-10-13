@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wellliving.attention

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellliving.trainer.Analytics
import com.wellliving.trainer.Bucket
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private enum class Period(val days: Int, val label: String) { Week(7, "שבוע"), Month(30, "חודש") }

private val dayMonth = DateTimeFormatter.ofPattern("d.M")
private val hebrewDays = listOf("ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳", "א׳") // Monday..Sunday

@Composable
fun ProgressScreen(data: Data, modifier: Modifier) {
    var period by rememberSaveable { mutableStateOf(Period.Week) }
    var filterId by rememberSaveable { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val activities = data.activities.filter { !it.archived || data.sessions.any { s -> s.activityId == it.id } }
    val sessions = data.sessions.filter { filterId == null || it.activityId == filterId }

    val from = today.minusDays(period.days - 1L)
    val current = Analytics.summary(Analytics.between(sessions, from, today, zone))
    val previous = Analytics.summary(
        Analytics.between(sessions, from.minusDays(period.days.toLong()), from.minusDays(1), zone),
    )

    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("התקדמות", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Period.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = period == p,
                        onClick = { period = p },
                        shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                    ) { Text(p.label) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterId == null, onClick = { filterId = null }, label = { Text("הכל") })
                activities.forEach { a ->
                    FilterChip(
                        selected = filterId == a.id,
                        onClick = { filterId = a.id },
                        label = { Text(a.name) },
                        leadingIcon = { Dot(activityColor(data.activities, a.id)) },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("${current.minutes}", "דקות ב${period.label}", Modifier.weight(1f))
                Tile(changeText(current.minutes, previous.minutes), "לעומת ה${period.label} הקודם", Modifier.weight(1f))
                Tile(
                    current.completionRate?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    "${current.completed}/${current.sessions} פרקים הושלמו",
                    Modifier.weight(1f),
                )
            }
        }
        item {
            val days = Analytics.daily(sessions, today, period.days, zone)
            val ids = if (filterId == null) activities.map { it.id } else listOf(filterId!!)
            ChartCard("דקות ריכוז לפי יום", "הקש על עמודה לפירוט") {
                StackedBars(
                    buckets = days,
                    ids = ids,
                    colors = ids.associateWith { activityColor(data.activities, it) },
                    label = { b, i ->
                        if (period == Period.Week) hebrewDays[b.start.dayOfWeek.value - 1]
                        else if (i % 5 == 4 || i == days.lastIndex) b.start.format(dayMonth) else ""
                    },
                    describe = { b -> describeBucket(b, b.start.format(dayMonth), data, ids) },
                )
                if (filterId == null) Legend(days, data)
            }
        }
        item {
            val weeks = Analytics.weekly(sessions, today, 8, zone)
            val ids = if (filterId == null) activities.map { it.id } else listOf(filterId!!)
            ChartCard("מגמה שבועית", "סך הדקות בכל אחד משמונת השבועות האחרונים") {
                StackedBars(
                    buckets = weeks,
                    ids = ids,
                    colors = ids.associateWith { activityColor(data.activities, it) },
                    label = { b, i -> if (i % 2 == 1 || i == weeks.lastIndex) b.start.format(dayMonth) else "" },
                    describe = { b -> describeBucket(b, "שבוע מ-${b.start.format(dayMonth)}", data, ids) },
                )
            }
        }
        item {
            val id = filterId
            if (id == null) {
                val avg = Analytics.weeklyAverageCompletedMin(sessions, today, 8, zone)
                ChartCard("אורך פרק מושלם, ממוצע שבועי", "קו עולה פירושו שהקשב מתארך") {
                    LineChart(
                        points = avg.map { it.second?.toFloat() },
                        dots = emptyList(),
                        lineColor = MaterialTheme.colorScheme.primary,
                        labels = avg.mapIndexed { i, p -> if (i % 2 == 1 || i == avg.lastIndex) p.first.format(dayMonth) else "" },
                        describe = { i -> avg[i].second?.let { "שבוע מ-${avg[i].first.format(dayMonth)}: ${"%.0f".format(it)} דק' בממוצע" } },
                    )
                }
            } else {
                val recent = sessions.sortedBy { it.startMs }.takeLast(20)
                ChartCard("התפתחות היעד בפעילות", "הקו: יעד הפרק · נקודה מלאה: הושלם · נקודה ריקה: הופסק") {
                    if (recent.isEmpty()) EmptyNote() else LineChart(
                        points = recent.map { it.targetMin.toFloat() },
                        dots = recent.map { Mark(it.actualSec / 60f, it.completed) },
                        lineColor = activityColor(data.activities, id),
                        labels = recent.mapIndexed { i, s ->
                            if (i == 0 || i == recent.lastIndex) Analytics.day(s, zone).format(dayMonth) else ""
                        },
                        describe = { i ->
                            val s = recent[i]
                            "${Analytics.day(s, zone).format(dayMonth)}: ${s.actualSec / 60} מתוך ${s.targetMin} דק'" +
                                if (s.completed) " · הושלם" else " · הופסק"
                        },
                    )
                }
            }
        }
        if (filterId == null) {
            item {
                val inPeriod = Analytics.between(data.sessions, from, today, zone)
                val byActivity = activities.map { a -> a to inPeriod.filter { it.activityId == a.id }.sumOf { it.actualSec } / 60 }
                    .filter { it.second > 0 }.sortedByDescending { it.second }
                ChartCard("לפי סוג פעילות", "דקות ב${period.label} הנוכחי") {
                    if (byActivity.isEmpty()) EmptyNote()
                    val top = byActivity.maxOfOrNull { it.second } ?: 1
                    byActivity.forEach { (a, minutes) ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Dot(activityColor(data.activities, a.id))
                                Spacer(Modifier.width(8.dp))
                                Text(a.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("$minutes דק'", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(
                                    Modifier.fillMaxWidth(minutes.toFloat() / top).height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)).background(activityColor(data.activities, a.id)),
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun changeText(now: Int, before: Int): String = when {
    before == 0 && now == 0 -> "—"
    before == 0 -> "חדש"
    else -> {
        val pct = ((now - before) * 100.0 / before).roundToInt()
        (if (pct > 0) "+" else "") + "$pct%"
    }
}

private fun describeBucket(b: Bucket, title: String, data: Data, ids: List<String>): String {
    val parts = ids.mapNotNull { id ->
        val m = b.minutesFor(id)
        if (m > 0 && ids.size > 1) "${data.activities.firstOrNull { it.id == id }?.name}: $m" else null
    }
    return "$title · ${b.minutes} דק'" + if (parts.isEmpty()) "" else " (${parts.joinToString(", ")})"
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun EmptyNote() {
    Text("עדיין אין נתונים לתקופה זו.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Legend(buckets: List<Bucket>, data: Data) {
    val present = data.activities.filter { a -> buckets.any { it.minutesFor(a.id) > 0 } }
    if (present.size < 2) return
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        present.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(activityColor(data.activities, a.id))
                Spacer(Modifier.width(4.dp))
                Text(a.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A round axis maximum and its step, so gridlines land on whole numbers. */
private fun niceScale(max: Float): Pair<Float, Float> {
    if (max <= 0f) return 30f to 10f
    val step = listOf(5f, 10f, 15f, 30f, 60f, 120f, 300f, 600f).firstOrNull { max / it <= 4 } ?: ceil(max / 4f)
    return ceil(max / step) * step to step
}

private class ChartInk(val grid: Color, val axisText: Color, val surface: Color)

@Composable
private fun chartInk() = ChartInk(
    grid = MaterialTheme.colorScheme.outlineVariant,
    axisText = MaterialTheme.colorScheme.onSurfaceVariant,
    surface = MaterialTheme.colorScheme.surfaceContainer,
)

private val axisStyleSize = 11.sp
private const val AXIS_LEFT_DP = 30f
private const val AXIS_BOTTOM_DP = 20f

private fun DrawScope.drawGrid(max: Float, step: Float, ink: ChartInk, measurer: TextMeasurer, left: Float, plotHeight: Float) {
    var v = 0f
    while (v <= max + 0.01f) {
        val y = plotHeight - v / max * plotHeight
        drawLine(ink.grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1f)
        val text = measurer.measure("${v.roundToInt()}", TextStyle(fontSize = axisStyleSize, color = ink.axisText))
        drawText(text, topLeft = Offset(0f, y - text.size.height / 2f))
        v += step
    }
}

@Composable
private fun SelectionLine(text: String?) {
    Text(
        text ?: " ",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun StackedBars(
    buckets: List<Bucket>,
    ids: List<String>,
    colors: Map<String, Color>,
    label: (Bucket, Int) -> String,
    describe: (Bucket) -> String,
) {
    var selected by remember(buckets.size, buckets.firstOrNull()?.start) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val ink = chartInk()
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val (max, step) = niceScale(buckets.maxOfOrNull { b -> ids.sumOf { b.minutesFor(it) }.toFloat() } ?: 0f)

    SelectionLine(selected?.let { describe(buckets[it]) })
    Canvas(
        Modifier.fillMaxWidth().height(180.dp).pointerInput(buckets) {
            detectTapGestures { pos ->
                val left = AXIS_LEFT_DP.dp.toPx()
                val slot = (size.width - left) / buckets.size
                val i = ((pos.x - left) / slot).toInt()
                selected = if (i in buckets.indices && i != selected) i else null
            }
        },
    ) {
        val left = AXIS_LEFT_DP.dp.toPx()
        val plotHeight = size.height - AXIS_BOTTOM_DP.dp.toPx()
        drawGrid(max, step, ink, measurer, left, plotHeight)
        val slot = (size.width - left) / buckets.size
        val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        val gap = 2.dp.toPx()
        buckets.forEachIndexed { i, b ->
            val x = left + slot * i + (slot - barWidth) / 2
            if (i == selected) drawRect(highlight, Offset(left + slot * i, 0f), Size(slot, plotHeight))
            var y = plotHeight
            val segments = ids.map { it to b.minutesFor(it) }.filter { it.second > 0 }
            segments.forEachIndexed { s, (id, minutes) ->
                val h = minutes / max * plotHeight
                val top = y - h
                val isTop = s == segments.lastIndex
                val radius = if (isTop) CornerRadius(4.dp.toPx()) else CornerRadius.Zero
                drawRoundRect(colors[id] ?: Color.Gray, Offset(x, top), Size(barWidth, (h - if (isTop) 0f else gap).coerceAtLeast(1f)), radius)
                y = top
            }
            val text = label(b, i)
            if (text.isNotEmpty()) {
                val m = measurer.measure(text, TextStyle(fontSize = axisStyleSize, color = ink.axisText))
                drawText(m, topLeft = Offset(left + slot * i + slot / 2 - m.size.width / 2f, plotHeight + 4.dp.toPx()))
            }
        }
    }
}

private data class Mark(val value: Float, val filled: Boolean)

@Composable
private fun LineChart(
    points: List<Float?>,
    dots: List<Mark>,
    lineColor: Color,
    labels: List<String>,
    describe: (Int) -> String?,
) {
    var selected by remember(points.size) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val ink = chartInk()
    val (max, step) = niceScale(max(points.maxOfOrNull { it ?: 0f } ?: 0f, dots.maxOfOrNull { it.value } ?: 0f))
    if (points.all { it == null }) {
        EmptyNote()
        return
    }

    SelectionLine(selected?.let(describe))
    Canvas(
        Modifier.fillMaxWidth().height(180.dp).pointerInput(points) {
            detectTapGestures { pos ->
                val left = AXIS_LEFT_DP.dp.toPx()
                val slot = (size.width - left) / points.size
                val i = ((pos.x - left) / slot).toInt()
                selected = if (i in points.indices && i != selected) i else null
            }
        },
    ) {
        val left = AXIS_LEFT_DP.dp.toPx()
        val plotHeight = size.height - AXIS_BOTTOM_DP.dp.toPx()
        drawGrid(max, step, ink, measurer, left, plotHeight)
        val slot = (size.width - left) / points.size
        fun x(i: Int) = left + slot * i + slot / 2
        fun y(v: Float) = plotHeight - v / max * plotHeight

        selected?.let { drawLine(ink.grid, Offset(x(it), 0f), Offset(x(it), plotHeight), strokeWidth = 2f) }

        val path = Path()
        var started = false
        points.forEachIndexed { i, v ->
            if (v == null) { started = false; return@forEachIndexed }
            if (!started) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
            started = true
        }
        drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { i, v ->
            if (v != null && dots.isEmpty()) {
                drawCircle(ink.surface, 6.dp.toPx(), Offset(x(i), y(v)))
                drawCircle(lineColor, 4.dp.toPx(), Offset(x(i), y(v)))
            }
        }
        dots.forEachIndexed { i, d ->
            val c = Offset(x(i), y(d.value))
            drawCircle(ink.surface, 6.dp.toPx(), c)
            if (d.filled) drawCircle(lineColor, 4.dp.toPx(), c)
            else drawCircle(lineColor, 3.5.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        }
        labels.forEachIndexed { i, text ->
            if (text.isNotEmpty()) {
                val m = measurer.measure(text, TextStyle(fontSize = axisStyleSize, color = ink.axisText))
                drawText(m, topLeft = Offset(x(i) - m.size.width / 2f, plotHeight + 4.dp.toPx()))
            }
        }
    }
}
