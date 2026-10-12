package com.wellliving.app.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wellliving.app.WellLivingApp
import com.wellliving.app.core.AppCatalog
import com.wellliving.app.core.InstalledApp
import com.wellliving.app.core.Permissions
import com.wellliving.app.protection.AdminReceiver
import com.wellliving.rules.NightConfig
import com.wellliving.rules.NightLockRule
import java.time.LocalTime

class MainActivity : ComponentActivity() {

    private val app get() = application as WellLivingApp

    /** Bumped on every resume so that screens re-read permissions and settings. */
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf(Screen.HOME) }
                    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                    // Reading refresh makes the screens recompose after returning from system settings.
                    val tick = refresh
                    when (screen) {
                        Screen.HOME -> HomeScreen(tick, onOpen = { screen = it })
                        Screen.PERMISSIONS -> PermissionsScreen(tick)
                        Screen.APPS -> AppsScreen()
                        Screen.NIGHT -> NightScreen()
                        Screen.PROTECTION -> ProtectionScreen(tick)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    // --- Home ----------------------------------------------------------------------------------

    @Composable
    private fun HomeScreen(tick: Int, onOpen: (Screen) -> Unit) {
        val settings = app.settings
        val allGranted = Permissions.accessibilityEnabled(this) && Permissions.usageAccessGranted(this)
        val quotas = settings.quotas()
        val usage = if (Permissions.usageAccessGranted(this)) app.usage.todayMillis() else emptyMap()
        val nightNow = NightLockRule.isNight(LocalTime.now(), settings.nightConfig())

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("חיים מיטיבים", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            if (!allGranted) {
                Card(Modifier.fillMaxWidth().clickable { onOpen(Screen.PERMISSIONS) }) {
                    Text(
                        "החסימה עדיין לא פעילה: חסרות הרשאות. לחץ כאן כדי להשלים.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("נעילת לילה", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !settings.nightLockEnabled -> "כבויה"
                            nightNow -> "פעילה עכשיו, עד 08:00"
                            else -> "תופעל ב-22:30"
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("שימוש היום", style = MaterialTheme.typography.titleMedium)
                    if (quotas.isEmpty()) Text("עדיין לא נבחרו אפליקציות להגבלה.")
                    quotas.entries.sortedBy { AppCatalog.label(this@MainActivity, it.key) }.forEach { (pkg, minutes) ->
                        val used = (usage[pkg] ?: 0L) / 60_000L
                        val limit = if (minutes == 0) "חסומה, מחסום בכל פתיחה" else "$used מתוך $minutes דקות"
                        Text("${AppCatalog.label(this@MainActivity, pkg)}: $limit")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { onOpen(Screen.APPS) }, modifier = Modifier.fillMaxWidth()) { Text("אפליקציות ומכסות") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onOpen(Screen.NIGHT) }, modifier = Modifier.fillMaxWidth()) { Text("נעילת לילה ו-Shorts") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onOpen(Screen.PROTECTION) }, modifier = Modifier.fillMaxWidth()) { Text("הגנה מפני הסרה") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onOpen(Screen.PERMISSIONS) }, modifier = Modifier.fillMaxWidth()) { Text("הרשאות") }
        }
    }

    // --- Permissions ---------------------------------------------------------------------------

    @SuppressLint("BatteryLife")
    @Composable
    private fun PermissionsScreen(tick: Int) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("הרשאות", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "באפליקציה שהותקנה ישירות, Android 13 ומעלה חוסם בתחילה את שירות הנגישות. אם ההפעלה נדחית: " +
                    "הגדרות ← אפליקציות ← חיים מיטיבים ← תפריט שלוש הנקודות ← \"התרת הגדרות מוגבלות\", ואז לנסות שוב.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            PermissionRow(
                title = "שירות נגישות",
                explanation = "מזהה איזו אפליקציה פתוחה ואוכף את החסימות, כולל Shorts.",
                granted = Permissions.accessibilityEnabled(this@MainActivity),
            ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            PermissionRow(
                title = "גישה לנתוני שימוש",
                explanation = "מודדת כמה זמן הייתה כל אפליקציה פתוחה היום, לצורך המכסות.",
                granted = Permissions.usageAccessGranted(this@MainActivity),
            ) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            PermissionRow(
                title = "פטור מחיסכון בסוללה",
                explanation = "מונע מהמערכת לסגור את החסימה ברקע.",
                granted = Permissions.ignoringBatteryOptimizations(this@MainActivity),
            ) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                )
            }
            PermissionRow(
                title = "הגנה מפני הסרה",
                explanation = "מנהל מכשיר: מונע הסרת האפליקציה בלי קוד PIN.",
                granted = Permissions.adminActive(this@MainActivity),
            ) { requestAdmin() }
        }
    }

    @Composable
    private fun PermissionRow(title: String, explanation: String, granted: Boolean, onGrant: () -> Unit) {
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(explanation, style = MaterialTheme.typography.bodySmall)
                }
                if (granted) Text("פעיל ✓") else TextButton(onClick = onGrant) { Text("הפעלה") }
            }
        }
    }

    private fun requestAdmin() {
        startActivity(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(this))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "מונע הסרה של האפליקציה בלי קוד PIN."),
        )
    }

    // --- Apps and quotas -----------------------------------------------------------------------

    @Composable
    private fun AppsScreen() {
        val settings = app.settings
        val apps = remember { AppCatalog.launchableApps(this) }
        var quotas by remember { mutableStateOf(settings.quotas()) }
        val pin = rememberPinGate()

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("אפליקציות ומכסות", style = MaterialTheme.typography.headlineSmall)
            Text(
                "מכסה יומית בדקות. \"חסומה\" פירושה מחסום של 60 שניות בכל פתיחה. " +
                    "הקלה בהגבלה דורשת את קוד ה-PIN, אם הוגדר.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps, key = InstalledApp::packageName) { installed ->
                    val current = quotas[installed.packageName]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(installed.label, style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                QUOTA_OPTIONS.forEach { (label, minutes) ->
                                    FilterChip(
                                        selected = current == minutes,
                                        onClick = {
                                            val apply = {
                                                settings.setQuota(installed.packageName, minutes)
                                                quotas = settings.quotas()
                                            }
                                            if (isLoosening(current, minutes)) pin.require(apply) else apply()
                                        },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        pin.Dialog()
    }

    /** No limit is looser than any quota; a quota of 0 (gate on every opening) is the strictest. */
    private fun isLoosening(from: Int?, to: Int?): Boolean = when {
        from == null -> false
        to == null -> true
        else -> to > from
    }

    // --- Night lock and Shorts -----------------------------------------------------------------

    @Composable
    private fun NightScreen() {
        val settings = app.settings
        var enabled by remember { mutableStateOf(settings.nightLockEnabled) }
        var shorts by remember { mutableStateOf(settings.shortsBlockingEnabled) }
        var extraAllowed by remember { mutableStateOf(settings.extraNightAllowed()) }
        var picking by remember { mutableStateOf(false) }
        val pin = rememberPinGate()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("נעילת לילה", style = MaterialTheme.typography.headlineSmall)
            Text(
                "22:30–08:00. בלילה ניתן לפתוח בפתיחה דחופה, ויוטיוב נשאר חסום גם אז. " +
                    "מ-05:00 ניתן להצהיר שהיום התחיל. שיחות, שעון מעורר, וואטסאפ ו-Waze זמינים תמיד.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow("נעילת לילה פעילה", enabled) { on ->
                val apply = { settings.nightLockEnabled = on; enabled = on }
                if (!on) pin.require(apply) else apply()
            }
            SwitchRow("חסימת Shorts ביוטיוב", shorts) { on ->
                val apply = { settings.shortsBlockingEnabled = on; shorts = on }
                if (!on) pin.require(apply) else apply()
            }
            Spacer(Modifier.height(16.dp))
            Text("אפליקציות נוספות שמותרות בלילה", style = MaterialTheme.typography.titleMedium)
            if (extraAllowed.isEmpty()) Text("אין.")
            extraAllowed.forEach { pkg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(AppCatalog.label(this@MainActivity, pkg), Modifier.weight(1f))
                    TextButton(onClick = {
                        settings.setNightAllowed(pkg, false)
                        extraAllowed = settings.extraNightAllowed()
                    }) { Text("הסרה") }
                }
            }
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("הוספת אפליקציה") }
        }

        if (picking) {
            val candidates = remember {
                AppCatalog.launchableApps(this).filter {
                    it.packageName !in NightConfig.DEFAULT_NIGHT_ALLOWED && it.packageName != NightConfig.YOUTUBE
                }
            }
            AlertDialog(
                onDismissRequest = { picking = false },
                confirmButton = { TextButton(onClick = { picking = false }) { Text("סגירה") } },
                title = { Text("מותרת בלילה") },
                text = {
                    LazyColumn {
                        items(candidates, key = InstalledApp::packageName) { candidate ->
                            Text(
                                candidate.label,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    picking = false
                                    pin.require {
                                        settings.setNightAllowed(candidate.packageName, true)
                                        extraAllowed = settings.extraNightAllowed()
                                    }
                                }.padding(vertical = 10.dp),
                            )
                        }
                    }
                },
            )
        }
        pin.Dialog()
    }

    @Composable
    private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    // --- Uninstall protection ------------------------------------------------------------------

    @Composable
    private fun ProtectionScreen(tick: Int) {
        val settings = app.settings
        var hasPin by remember { mutableStateOf(settings.hasPin()) }
        var changing by remember { mutableStateOf(false) }
        val pin = rememberPinGate()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("הגנה מפני הסרה", style = MaterialTheme.typography.headlineSmall)
            Text(
                "כשההגנה פעילה, Android אינו מאפשר להסיר את האפליקציה. ביטול ההגנה, וכל הקלה בהגדרות, " +
                    "דורשים את קוד ה-PIN. מומלץ שחבר יבחר את הקוד וישמור אותו אצלו. " +
                    "בגרסה מאוחרת יותר השחרור יעבור לאישור חבר מהקבוצה.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Text(if (Permissions.adminActive(this@MainActivity)) "ההגנה פעילה ✓" else "ההגנה כבויה")
            if (!Permissions.adminActive(this@MainActivity)) {
                Button(onClick = ::requestAdmin, modifier = Modifier.fillMaxWidth()) { Text("הפעלת ההגנה") }
            }
            Spacer(Modifier.height(16.dp))
            if (!hasPin || changing) {
                Text(if (hasPin) "קוד PIN חדש" else "הגדרת קוד PIN (4–8 ספרות)", style = MaterialTheme.typography.titleMedium)
                PinField(confirmLabel = "שמירה") { newPin ->
                    settings.setPin(newPin)
                    hasPin = true
                    changing = false
                    true
                }
            } else {
                Text("קוד PIN מוגדר ✓")
                OutlinedButton(onClick = { pin.require { changing = true } }, modifier = Modifier.fillMaxWidth()) {
                    Text("החלפת הקוד")
                }
            }
        }
        pin.Dialog()
    }

    // --- PIN gate for loosening changes --------------------------------------------------------

    private inner class PinGate {
        var pending by mutableStateOf<(() -> Unit)?>(null)

        fun require(action: () -> Unit) {
            if (app.settings.hasPin()) pending = action else action()
        }

        @Composable
        fun Dialog() {
            val action = pending ?: return
            AlertDialog(
                onDismissRequest = { pending = null },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { pending = null }) { Text("ביטול") } },
                title = { Text("נדרש קוד PIN") },
                text = {
                    PinField(confirmLabel = "אישור") { entered ->
                        val ok = app.settings.checkPin(entered)
                        if (ok) {
                            pending = null
                            action()
                        }
                        ok
                    }
                },
            )
        }
    }

    @Composable
    private fun rememberPinGate(): PinGate = remember { PinGate() }

    private enum class Screen { HOME, PERMISSIONS, APPS, NIGHT, PROTECTION }

    private companion object {
        val QUOTA_OPTIONS: List<Pair<String, Int?>> = listOf(
            "ללא" to null,
            "חסומה" to 0,
            "15" to 15,
            "30" to 30,
            "60" to 60,
        )
    }
}
