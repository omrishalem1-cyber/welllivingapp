package com.wellliving.attention

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellliving.trainer.ActivityStats
import com.wellliving.trainer.ProgressionPolicy
import com.wellliving.trainer.Session
import com.wellliving.trainer.Stats
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val policy = ProgressionPolicy()

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun HomeScreen(data: Data, onOpen: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("מאמן קשב", style = MaterialTheme.typography.headlineMedium)
            Text(
                "היום: ${Stats.minutesSince(data.sessions, startOfToday())} דקות ריכוז · " +
                    "7 ימים: ${Stats.minutesSince(data.sessions, now - 7 * 86_400_000L)} דקות",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(data.activities.filter { !it.archived }, key = { it.id }) { activity ->
            val stats = Stats.forActivity(data.sessions.filter { it.activityId == activity.id }, now, policy)
            Card(Modifier.fillMaxWidth().clickable { onOpen(activity.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(activity.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${stats.progress.targetMin} דק'", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(summaryLine(stats), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("הוספת סוג פעילות") }
        }
    }
    if (adding) AddActivityDialog(onDismiss = { adding = false })
}

private fun summaryLine(stats: ActivityStats): String =
    if (stats.totalSessions == 0) "טרם בוצעו פרקים"
    else "${stats.completedSessions}/${stats.totalSessions} פרקים הושלמו · ${stats.minutesLast7Days} דק' השבוע"

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
fun ActivityScreen(activityId: String, data: Data, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = data.activities.firstOrNull { it.id == activityId } ?: return onBack()
    val sessions = data.sessions.filter { it.activityId == activityId }.sortedByDescending { it.startMs }
    val stats = Stats.forActivity(sessions, System.currentTimeMillis(), policy)
    val progress = stats.progress
    var confirmArchive by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("→ חזרה") }
            Text(activity.name, style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("היעד הנוכחי", style = MaterialTheme.typography.labelLarge)
                    Text("${progress.targetMin} דקות", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    val toRaise = progress.successesToRaise(policy)
                    Text(
                        when {
                            toRaise == null -> "הגעת ליעד המרבי (${policy.maxMin} דקות)"
                            toRaise == 1 -> "עוד פרק אחד שיושלם יעלה את היעד ל-${progress.targetMin + policy.stepMin} דקות"
                            else -> "$toRaise פרקים מושלמים ברצף יעלו את היעד ל-${progress.targetMin + policy.stepMin} דקות"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { SessionService.start(context, activityId, progress.targetMin) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("התחלת פרק של ${progress.targetMin} דקות") }
                }
            }
        }
        item { StatsBlock(stats) }
        if (sessions.isNotEmpty()) {
            item { Text("פרקים אחרונים", style = MaterialTheme.typography.titleMedium) }
            items(sessions.take(30), key = { it.id }) { SessionRow(it) }
        }
        item {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { confirmArchive = true }) { Text("הסרת סוג הפעילות מהרשימה") }
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

@Composable
private fun StatsBlock(stats: ActivityStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

private val dateFormat = SimpleDateFormat("d.M · HH:mm", Locale.getDefault())

@Composable
private fun SessionRow(session: Session) {
    Column {
        Row {
            Text(dateFormat.format(Date(session.startMs)), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (session.completed) "✓ ${session.targetMin} דק'" else "${session.actualSec / 60} מתוך ${session.targetMin} דק'",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "הסחות: ${session.distractions}" + (session.focusRating?.let { " · ריכוז: $it/5" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(Modifier.padding(top = 6.dp))
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

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        val minutes = remainingMs / 60_000
        val seconds = (remainingMs / 1000) % 60
        Text(String.format(Locale.US, "%d:%02d", minutes, seconds), fontSize = 72.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { 1f - remainingMs.toFloat() / totalMs }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
        Text(
            "כשתשים לב שהקשב נדד, הקש \"הוסחתי\" וחזור למשימה. אפשר גם מההתראה.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { SessionService.send(context, SessionService.ACTION_DISTRACTION) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Text("הוסחתי (${session.distractions})", fontSize = 18.sp) }
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = { confirmStop = true }) { Text("סיום מוקדם") }
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
