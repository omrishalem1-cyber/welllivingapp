package com.wellliving.attention

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App() }
            }
        }
    }
}

private enum class Tab { Train, Progress }

/** A session the user chose but has not started yet: the quote screen is showing. */
private data class PendingStart(val activityId: String, val targetMin: Int)

@Composable
private fun App() {
    val context = LocalContext.current
    val data by Store.data.collectAsState()
    val active by ActiveSessionState.current.collectAsState()
    val pendingRating by ActiveSessionState.pendingRating.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Train) }
    var openActivityId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingStart by remember { mutableStateOf<PendingStart?>(null) }

    val running = active
    val pending = pendingStart
    when {
        running != null -> SessionScreen(running, data)
        pending != null -> {
            BackHandler { pendingStart = null }
            QuoteScreen(
                activityName = data.activities.firstOrNull { it.id == pending.activityId }?.name.orEmpty(),
                targetMin = pending.targetMin,
                onStart = {
                    SessionService.start(context, pending.activityId, pending.targetMin)
                    pendingStart = null
                },
                onCancel = { pendingStart = null },
            )
        }
        else -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Train,
                        onClick = { tab = Tab.Train },
                        icon = { Icon(painterResource(android.R.drawable.ic_menu_recent_history), null) },
                        label = { Text("אימון") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Progress,
                        onClick = { tab = Tab.Progress; openActivityId = null },
                        icon = { Icon(painterResource(android.R.drawable.ic_menu_sort_by_size), null) },
                        label = { Text("התקדמות") },
                    )
                }
            },
        ) { padding ->
            val modifier = Modifier.fillMaxSize().padding(padding)
            when {
                tab == Tab.Progress -> ProgressScreen(data, modifier)
                openActivityId != null -> {
                    BackHandler { openActivityId = null }
                    ActivityScreen(
                        openActivityId!!, data, modifier,
                        onBack = { openActivityId = null },
                        onStart = { id, target -> pendingStart = PendingStart(id, target) },
                    )
                }
                else -> HomeScreen(data, modifier, onOpen = { openActivityId = it })
            }
        }
    }

    val ratingId = pendingRating
    if (running == null && ratingId != null) {
        RatingDialog(
            session = data.sessions.firstOrNull { it.id == ratingId },
            onRate = { rating -> Store.rateSession(ratingId, rating); ActiveSessionState.setPendingRating(null) },
            onSkip = { ActiveSessionState.setPendingRating(null) },
        )
    }
}
