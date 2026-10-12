package com.wellliving.app.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wellliving.app.WellLivingApp
import com.wellliving.app.core.AppCatalog
import com.wellliving.app.ui.AppTheme
import com.wellliving.rules.Decision
import com.wellliving.rules.GateReason
import kotlinx.coroutines.delay
import java.time.LocalDate

/** The screen shown instead of a blocked app: the 60-second gate, the night lock, or a hard block. */
class BlockActivity : ComponentActivity() {

    private val app get() = application as WellLivingApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        render(intent)
    }

    /** Leaving the gate half-way resets it: the 60 seconds must be spent here. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    private fun render(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        val label = AppCatalog.label(this, pkg)
        val mode = intent.getStringExtra(EXTRA_MODE)
        setContent {
            AppTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    when (mode) {
                        MODE_GATE -> GateScreen(
                            appLabel = label,
                            quotaExceeded = intent.getStringExtra(EXTRA_GATE_REASON) == GateReason.QUOTA_EXCEEDED.name,
                            passMinutes = app.settings.blockingConfig().gatePassMinutes,
                            onContinue = {
                                app.settings.grantGatePass(pkg, System.currentTimeMillis())
                                openApp(pkg)
                            },
                            onGiveUp = ::goHome,
                        )
                        MODE_NIGHT -> NightScreen(
                            appLabel = label,
                            canDeclareMorning = intent.getBooleanExtra(EXTRA_CAN_DECLARE_MORNING, false),
                            urgentMinutes = app.settings.nightConfig().urgentUnlockMinutes,
                            onUrgentUnlock = {
                                app.settings.grantUrgentUnlock(System.currentTimeMillis())
                                openApp(pkg)
                            },
                            onDeclareMorning = {
                                app.settings.declareMorning(LocalDate.now())
                                openApp(pkg)
                            },
                            onBack = ::goHome,
                        )
                        else -> HardBlockScreen(appLabel = label, onBack = ::goHome)
                    }
                }
            }
        }
    }

    private fun openApp(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_GATE_REASON = "gate_reason"
        private const val EXTRA_CAN_DECLARE_MORNING = "can_declare_morning"
        private const val MODE_GATE = "gate"
        private const val MODE_NIGHT = "night"
        private const val MODE_HARD = "hard"

        fun intent(context: Context, pkg: String, decision: Decision): Intent =
            Intent(context, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_PACKAGE, pkg)
                .apply {
                    when (decision) {
                        is Decision.Gate -> putExtra(EXTRA_MODE, MODE_GATE).putExtra(EXTRA_GATE_REASON, decision.reason.name)
                        is Decision.NightLock -> putExtra(EXTRA_MODE, MODE_NIGHT)
                            .putExtra(EXTRA_CAN_DECLARE_MORNING, decision.canDeclareMorning)
                        is Decision.HardBlock -> putExtra(EXTRA_MODE, MODE_HARD)
                        Decision.Allow -> Unit
                    }
                }
    }
}

/** Fixed line for now; the motivation module will choose a line by context. */
private const val GATE_LINE = "לפני שאתה ממשיך: האם זה באמת מה שאתה רוצה לעשות עכשיו?"
private const val GATE_SECONDS = 60
private const val INHALE_SECONDS = 4
private const val EXHALE_SECONDS = 6

@Composable
private fun GateScreen(
    appLabel: String,
    quotaExceeded: Boolean,
    passMinutes: Int,
    onContinue: () -> Unit,
    onGiveUp: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(GATE_SECONDS) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }
    val elapsed = GATE_SECONDS - remaining
    val inhaling = elapsed % (INHALE_SECONDS + EXHALE_SECONDS) < INHALE_SECONDS
    val scale by animateFloatAsState(
        targetValue = if (inhaling) 1f else 0.55f,
        animationSpec = tween(durationMillis = (if (inhaling) INHALE_SECONDS else EXHALE_SECONDS) * 1_000),
        label = "breath",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (quotaExceeded) "המכסה היומית של $appLabel הסתיימה" else "$appLabel ברשימת החסימה",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            Box(
                Modifier.size(220.dp).scale(scale).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            )
            Text(
                text = if (remaining > 0) (if (inhaling) "שאיפה" else "נשיפה") else "",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(GATE_LINE, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGiveUp, modifier = Modifier.fillMaxWidth()) { Text("לוותר ולחזור") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onContinue, enabled = remaining == 0, modifier = Modifier.fillMaxWidth()) {
            Text(if (remaining > 0) "אפשר להמשיך בעוד $remaining שניות" else "להמשיך ל-$appLabel ל-$passMinutes דקות")
        }
    }
}

@Composable
private fun NightScreen(
    appLabel: String,
    canDeclareMorning: Boolean,
    urgentMinutes: Int,
    onUrgentUnlock: () -> Unit,
    onDeclareMorning: () -> Unit,
    onBack: () -> Unit,
) {
    var step by remember { mutableStateOf(NightStep.LOCKED) }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            NightStep.LOCKED -> {
                Text("נעילת לילה", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text(
                    "$appLabel נעולה עד 08:00. שיחות, שעון מעורר, וואטסאפ ו-Waze זמינים.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("חזרה") }
                Spacer(Modifier.height(8.dp))
                if (canDeclareMorning) {
                    OutlinedButton(onClick = { step = NightStep.MORNING }, modifier = Modifier.fillMaxWidth()) {
                        Text("היום שלי מתחיל עכשיו")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = { step = NightStep.URGENT_QUESTION }, modifier = Modifier.fillMaxWidth()) {
                    Text("פתיחה דחופה")
                }
            }
            NightStep.URGENT_QUESTION -> {
                Text("האם זה באמת מה שאני רוצה?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(
                    "פתיחה דחופה פותחת את הטלפון ל-$urgentMinutes דקות. יוטיוב נשאר חסום.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("לא, אני חוזר לישון") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onUrgentUnlock, modifier = Modifier.fillMaxWidth()) { Text("כן, זה דחוף") }
            }
            NightStep.MORNING -> {
                Text("הצהרת בוקר", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text(
                    "אני קם עכשיו ומתחיל את היום לפני 08:00. נעילת הלילה תסתיים כבר עכשיו.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = onDeclareMorning, modifier = Modifier.fillMaxWidth()) { Text("אני מצהיר") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { step = NightStep.LOCKED }, modifier = Modifier.fillMaxWidth()) { Text("ביטול") }
            }
        }
    }
}

private enum class NightStep { LOCKED, URGENT_QUESTION, MORNING }

@Composable
private fun HardBlockScreen(appLabel: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$appLabel חסומה בלילה", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("גם פתיחה דחופה אינה פותחת אותה. היא תיפתח בבוקר.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("חזרה") }
    }
}
