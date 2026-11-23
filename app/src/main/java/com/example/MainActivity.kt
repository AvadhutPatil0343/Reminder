package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ai.configureAppCheck
import com.example.notifications.NotificationHelper
import com.example.ui.RemindlyRootApp
import com.example.widget.RemindlyWidgetProvider

class MainActivity : ComponentActivity() {
    private var highlightedReminderId by mutableStateOf<String?>(null)
    private var openQuickAddToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        NotificationHelper.ensureNotificationChannels(this)
        consumeLaunchIntent(intent)

        setContent {
            RemindlyRootApp(
                highlightedReminderId = highlightedReminderId,
                openQuickAddToken = openQuickAddToken
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        highlightedReminderId = intent?.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID)
        if (intent?.getBooleanExtra(RemindlyWidgetProvider.EXTRA_OPEN_QUICK_ADD, false) == true) {
            openQuickAddToken += 1
            intent.removeExtra(RemindlyWidgetProvider.EXTRA_OPEN_QUICK_ADD)
        }
    }
}
