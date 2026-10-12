package com.wellliving.app.protection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wellliving.app.WellLivingApp
import com.wellliving.app.ui.AppTheme
import com.wellliving.app.ui.PinField

/** Covers the system screen that would switch off uninstall protection, until the PIN is entered. */
class PinGuardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as WellLivingApp).settings
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            AppTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("ההגנה מפני הסרה פעילה", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text("כדי לבטל אותה יש להזין את קוד ה-PIN.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    PinField(
                        confirmLabel = "אישור",
                        onSubmit = { pin ->
                            val ok = settings.checkPin(pin)
                            if (ok) {
                                settings.grantSettingsPass(System.currentTimeMillis())
                                finish()
                            }
                            ok
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = ::goHome, modifier = Modifier.fillMaxWidth()) { Text("חזרה") }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, PinGuardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
