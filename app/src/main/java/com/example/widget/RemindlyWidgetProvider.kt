package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.notifications.LocalAlarmStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RemindlyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        const val EXTRA_OPEN_QUICK_ADD = "extra_open_quick_add"

        fun requestUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RemindlyWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            widgetIds.forEach { id ->
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_remindly)
            val alarms = LocalAlarmStore(context).getStoredAlarms()
            val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

            views.setTextViewText(
                R.id.widget_summary,
                "${alarms.size} upcoming dose${if (alarms.size == 1) "" else "s"}"
            )

            val itemIds = listOf(R.id.widget_item_1, R.id.widget_item_2, R.id.widget_item_3)
            if (alarms.isEmpty()) {
                views.setTextViewText(R.id.widget_item_1, "All doses taken! Tap + Add to schedule.")
                views.setTextViewText(R.id.widget_item_2, "")
                views.setTextViewText(R.id.widget_item_3, "")
            } else {
                itemIds.forEachIndexed { idx, viewId ->
                    val rec = alarms.getOrNull(idx)
                    if (rec != null) {
                        val dose = rec.dosage.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
                        views.setTextViewText(
                            viewId,
                            "• ${rec.title}$dose (${timeFmt.format(Date(rec.triggerAtMillis))})"
                        )
                    } else {
                        views.setTextViewText(viewId, "")
                    }
                }
            }

            val openAppPi = PendingIntent.getActivity(
                context,
                1001,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPi)

            val quickAddPi = PendingIntent.getActivity(
                context,
                1002,
                Intent(context, MainActivity::class.java).apply {
                    putExtra(EXTRA_OPEN_QUICK_ADD, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_add_button, quickAddPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
