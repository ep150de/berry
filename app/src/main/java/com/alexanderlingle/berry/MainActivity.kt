package com.alexanderlingle.berry

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alexanderlingle.berry.notify.BerryNotifier
import com.alexanderlingle.berry.notify.NotifyPrefs
import com.alexanderlingle.berry.ui.navigation.BerryNavGraph
import com.alexanderlingle.berry.ui.navigation.Routes
import com.alexanderlingle.berry.ui.theme.BerryTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Tab to open when launched from a predictive notification; dashboard otherwise. */
    private var startRoute by mutableStateOf(Routes.DASHBOARD)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* one-shot ask; the worker stays silent until granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startRoute = intent?.getStringExtra(BerryNotifier.EXTRA_START_ROUTE) ?: Routes.DASHBOARD
        maybeAskNotificationPermission()
        setContent {
            BerryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BerryNavGraph(startRoute = startRoute)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(BerryNotifier.EXTRA_START_ROUTE)?.let { startRoute = it }
    }

    /**
     * Berry's first runtime permission (approved 2026-10-17). Asked once, on
     * first launch; never nagged again if declined.
     */
    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            val prefs = NotifyPrefs(this@MainActivity)
            if (prefs.wasPermissionAsked()) return@launch
            prefs.setPermissionAsked()
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
