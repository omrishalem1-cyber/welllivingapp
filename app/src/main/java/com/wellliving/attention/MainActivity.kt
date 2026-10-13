package com.wellliving.attention

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface { App() }
                }
            }
        }
    }
}

@Composable
private fun App() {
    val data by Store.data.collectAsState()
    val active by ActiveSessionState.current.collectAsState()
    val pendingRating by ActiveSessionState.pendingRating.collectAsState()
    var openActivityId by remember { mutableStateOf<String?>(null) }

    val running = active
    when {
        running != null -> SessionScreen(running, data)
        openActivityId != null -> {
            BackHandler { openActivityId = null }
            ActivityScreen(openActivityId!!, data, onBack = { openActivityId = null })
        }
        else -> HomeScreen(data, onOpen = { openActivityId = it })
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

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
