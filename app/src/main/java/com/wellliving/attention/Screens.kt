package com.wellliving.attention

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellliving.trainer.ActivityStats
import com.wellliving.trainer.ProgressionPolicy
import com.wellliving.trainer.QUOTES
import com.wellliving.trainer.QuotePicker
import com.wellliving.trainer.Session
import com.wellliving.trainer.Stats
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

val policy = ProgressionPolicy()

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "בוקר טוב"
    in 12..16 -> "צהריים טובים"
    in 17..20 -> "ערב טוב"
    else -> "לילה שקט"
}

@Composable
fun HomeScreen(data: Data, modifier: Modifier, onOpen: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val today = Stats.minutesSince(data.sessions, startOfToday())
    val week = Stats.minutesSince(data.sessions, now - 7 * 86_400_000L)
    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brand.heroGradient).padding(24.dp),
            ) {
                Text(greeting(), color = Brand.OnNightMuted, style = MaterialTheme.typography.titleMedium)
                Text("על מה נתמקד עכשיו?", color = Brand.OnNight, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                Row {
                    HeroNumber("$today", "דקות ריכוז היום", Modifier.weight(1f))
                    HeroNumber("$week", "דקות ב-7 ימים", Modifier.weight(1f))
                }
            }
        }
        item {
            Text(
                "סוגי פעילות",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
        items(data.activities.filter { !it.archived }, key = { it.id }) { activity ->
            val stats = Stats.forActivity(data.sessions.filter { it.activityId == activity.id }, now, policy)
            ActivityCard(
                name = activity.name,
                color = activityColor(data.activities, activity.id),
                stats = stats,
                onClick = { onOpen(activity.id) },
            )
        }
        item {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("+ הוספת סוג פעילות")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (adding) AddActivityDialog(onDismiss = { adding = false })
}

@Composable
private fun HeroNumber(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = Brand.Amber, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Brand.OnNightMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActivityCard(name: String, color: Color, stats: ActivityStats, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (stats.totalSessions == 0) "טרם בוצעו פרקים"
                    else "${stats.completedSessions}/${stats.totalSessions} הושלמו · ${stats.minutesLast7Days} דק' השבוע",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TargetPill(stats.progress.targetMin)
        }
    }
}

@Composable
private fun TargetPill(minutes: Int) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text("$minutes דק'", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddActivityDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("סוג פעילות חדש") },
        text = {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("שם הפעילות") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { Store.addActivity(name); onDismiss() }, enabled = name.isNotBlank()) { Text("הוספה") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
fun ActivityScreen(
    activityId: String,
    data: Data,
    modifier: Modifier,
    onBack: () -> Unit,
    onStart: (String, Int) -> Unit,
) {
    val activity = data.activities.firstOrNull { it.id == activityId }
    if (activity == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val sessions = data.sessions.filter { it.activityId == activityId }.sortedByDescending { it.startMs }
    val stats = Stats.forActivity(sessions, System.currentTimeMillis(), policy)
    val progress = stats.progress
    var confirmArchive by remember { mutableStateOf(false) }

    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("→ חזרה") }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brand.heroGradient).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(activity.name, color = Brand.OnNight, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Box(contentAlignment = Alignment.Center) {
                    LadderRing(progress.targetMin, Modifier.size(170.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${progress.targetMin}", color = Brand.OnNight, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        Text("דקות", color = Brand.OnNightMuted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val toRaise = progress.successesToRaise(policy)
                Text(
                    when {
                        toRaise == null -> "הגעת ליעד המרבי של ${policy.maxMin} דקות"
                        toRaise == 1 -> "עוד פרק מושלם אחד, והיעד יעלה ל-${progress.targetMin + policy.stepMin} דקות"
                        else -> "$toRaise פרקים מושלמים ברצף יעלו את היעד ל-${progress.targetMin + policy.stepMin} דקות"
                    },
                    color = Brand.OnNightMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onStart(activityId, progress.targetMin) },
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Amber, contentColor = Color(0xFF2A1A00)),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("התחלת פרק של ${progress.targetMin} דקות", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        item { StatsBlock(stats) }
        if (sessions.isNotEmpty()) {
            item { Text("פרקים אחרונים", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp)) }
            items(sessions.take(30), key = { it.id }) { SessionRow(it) }
        }
        item {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { confirmArchive = true }) {
                Text("הסרת סוג הפעילות מהרשימה", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("להסיר את \"${activity.name}\"?") },
            text = { Text("הפעילות תוסתר מהרשימה. הפרקים שתועדו יישמרו.") },
            confirmButton = { TextButton(onClick = { Store.archiveActivity(activityId); onBack() }) { Text("הסרה") } },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("ביטול") } },
        )
    }
}

/** Where the current target sits on the 10–90 minute ladder. */
@Composable
private fun LadderRing(targetMin: Int, modifier: Modifier) {
    val fraction = (targetMin - policy.minMin).toFloat() / (policy.maxMin - policy.minMin)
    Canvas(modifier) {
        val stroke = 12.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(Brand.OnNight.copy(alpha = 0.15f), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(Brand.Amber, 135f, 270f * fraction.coerceIn(0.02f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun StatsBlock(stats: ActivityStats) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("מדדי התקדמות", style = MaterialTheme.typography.titleMedium)
            StatLine("פרקים שהושלמו", "${stats.completedSessions} מתוך ${stats.totalSessions}")
            StatLine("שיעור השלמה (10 אחרונים)", stats.recentSuccessRate?.let { "${(it * 100).roundToInt()}%" } ?: "—")
            StatLine("הפרק המושלם הארוך ביותר", "${stats.longestCompletedMin} דק'")
            StatLine("דקות ריכוז ב-7 הימים האחרונים", "${stats.minutesLast7Days}")
            StatLine(
                "הסחות לכל 10 דקות (10 אחרונים)",
                stats.recentDistractionsPer10Min?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
            )
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

private val dateFormat = SimpleDateFormat("d.M · HH:mm", Locale.getDefault())

@Composable
private fun SessionRow(session: Session) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row {
            Text(dateFormat.format(Date(session.startMs)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (session.completed) "✓ ${session.targetMin} דק'" else "${session.actualSec / 60} מתוך ${session.targetMin} דק'",
                style = MaterialTheme.typography.bodyMedium,
                color = if (session.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "הסחות: ${session.distractions}" + (session.focusRating?.let { " · ריכוז: $it/5" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private var lastQuote: Int? = null

/** A moment before the session: one verified quote, then the start button. */
@Composable
fun QuoteScreen(activityName: String, targetMin: Int, onStart: () -> Unit, onCancel: () -> Unit) {
    var index by remember { mutableIntStateOf(QuotePicker.next(lastQuote).also { lastQuote = it }) }
    val quote = QUOTES[index]
    Box(Modifier.fillMaxSize().background(Brand.nightGradient).systemBarsPadding().padding(24.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("רגע לפני", color = Brand.OnNightMuted, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            AnimatedContent(targetState = quote, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "quote") { q ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Brand.Amber))
                    Spacer(Modifier.height(24.dp))
                    Text(q.text, style = QuoteStyle, color = Brand.OnNight, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Text(q.author, color = Brand.Amber, fontWeight = FontWeight.SemiBold)
                    Text(
                        q.source + if (q.translated) " · בתרגום חופשי" else "",
                        color = Brand.OnNightMuted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("$activityName · $targetMin דקות", color = Brand.OnNightMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Amber, contentColor = Color(0xFF2A1A00)),
                modifier = Modifier.fillMaxWidth().height(60.dp),
            ) { Text("אני מוכן · התחלה", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            Row {
                TextButton(onClick = { index = QuotePicker.next(index).also { lastQuote = it } }) {
                    Text("משפט אחר", color = Brand.OnNightMuted)
                }
                TextButton(onClick = onCancel) { Text("ביטול", color = Brand.OnNightMuted) }
            }
        }
    }
}

@Composable
fun SessionScreen(session: ActiveSession, data: Data) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.startMs) {
        while (true) { now = System.currentTimeMillis(); delay(500) }
    }
    var confirmStop by remember { mutableStateOf(false) }
    val totalMs = session.targetMin * 60_000L
    val remainingMs = (session.endMs - now).coerceAtLeast(0)
    val name = data.activities.firstOrNull { it.id == session.activityId }?.name ?: ""

    Box(Modifier.fillMaxSize().background(Brand.nightGradient).systemBarsPadding().padding(24.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, color = Brand.OnNight, style = MaterialTheme.typography.titleLarge)
            Text("${session.targetMin} דקות של קשב אחד", color = Brand.OnNightMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(280.dp)) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(Brand.OnNight.copy(alpha = 0.12f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    val sweep = 360f * (1f - remainingMs.toFloat() / totalMs)
                    drawArc(Brand.Amber, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        String.format(Locale.US, "%d:%02d", remainingMs / 60_000, (remainingMs / 1000) % 60),
                        color = Brand.OnNight, fontSize = 64.sp, fontWeight = FontWeight.Light,
                    )
                    Text("נותרו", color = Brand.OnNightMuted)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "כשתבחין שהקשב נדד, הקש \"הוסחתי\" וחזור למשימה. אפשר גם מתוך ההתראה.",
                color = Brand.OnNightMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { SessionService.send(context, SessionService.ACTION_DISTRACTION) },
                colors = ButtonDefaults.buttonColors(containerColor = Brand.OnNight.copy(alpha = 0.14f), contentColor = Brand.OnNight),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("הוסחתי · ${session.distractions}", fontSize = 18.sp) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmStop = true }) { Text("סיום מוקדם", color = Brand.OnNightMuted) }
        }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("לסיים את הפרק עכשיו?") },
            text = { Text("הפרק ייחשב כלא הושלם. פרק קצר משתי דקות לא יתועד כלל.") },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; SessionService.send(context, SessionService.ACTION_STOP) }) { Text("סיום") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("להמשיך") } },
        )
    }
}

@Composable
fun RatingDialog(session: Session?, onRate: (Int) -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(if (session?.completed == true) "הפרק הושלם" else "הפרק הסתיים") },
        text = {
            Column {
                Text("עד כמה היית מרוכז בפרק? (1 = כלל לא, 5 = במלואו)")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { value ->
                        OutlinedButton(onClick = { onRate(value) }, modifier = Modifier.weight(1f)) { Text("$value") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSkip) { Text("דילוג") } },
    )
}
