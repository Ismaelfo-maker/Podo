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

class StepWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_STEPS = "com.example.UPDATE_STEPS"
        const val EXTRA_STEPS = "extra_steps"
        const val EXTRA_GOAL = "extra_goal"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, 0, 10000)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_STEPS) {
            val steps = intent.getIntExtra(EXTRA_STEPS, 0)
            val goal = intent.getIntExtra(EXTRA_GOAL, 10000)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, StepWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, steps, goal)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        steps: Int,
        goal: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.step_widget_layout)
        views.setTextViewText(R.id.widget_steps_count, String.format("%,d", steps))
        views.setTextViewText(R.id.widget_goal_text, "Meta: ${String.format("%,d", goal)}")

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_steps_count, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
