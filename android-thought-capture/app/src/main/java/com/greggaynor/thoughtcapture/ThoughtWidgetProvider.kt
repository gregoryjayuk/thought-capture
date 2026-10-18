package com.greggaynor.thoughtcapture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class ThoughtWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_CAPTURE -> {
                val serviceAction = if (CaptureState.isListening(context)) {
                    VoiceCaptureService.ACTION_STOP
                } else {
                    VoiceCaptureService.ACTION_START
                }
                val serviceIntent = Intent(context, VoiceCaptureService::class.java).apply {
                    action = serviceAction
                }
                context.startForegroundService(serviceIntent)
                updateAllWidgets(context)
            }

            ACTION_REFRESH_WIDGET -> updateAllWidgets(context)
        }
    }

    private fun buildRemoteViews(context: Context): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_thought_capture)
        val listening = CaptureState.isListening(context)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            Intent(context, ThoughtWidgetProvider::class.java).apply { action = ACTION_TOGGLE_CAPTURE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        remoteViews.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
        remoteViews.setInt(R.id.widget_icon, "setAlpha", if (listening) 255 else 190)

        return remoteViews
    }

    companion object {
        const val ACTION_TOGGLE_CAPTURE = "com.greggaynor.thoughtcapture.TOGGLE_CAPTURE"
        const val ACTION_REFRESH_WIDGET = "com.greggaynor.thoughtcapture.REFRESH_WIDGET"

        fun updateAllWidgets(context: Context) {
            val widgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ThoughtWidgetProvider::class.java)
            val ids = widgetManager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val provider = ThoughtWidgetProvider()
                provider.onUpdate(context, widgetManager, ids)
            }
        }
    }
}
