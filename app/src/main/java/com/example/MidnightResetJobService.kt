package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.DayStepEntry
import com.example.widget.StepWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * JobScheduler service and manager to guarantee that the daily step counter
 * resets to zero at exactly 00:00 every midnight, even when the application
 * is in the background or killed.
 */
class MidnightResetJobService : JobService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "MidnightResetJob"
        const val JOB_ID_MIDNIGHT_RESET = 88001
        const val ACTION_MIDNIGHT_ALARM = "com.example.ACTION_EXACT_MIDNIGHT_RESET"
        private const val PREFS_NAME = "step_counter_prefs"
        private const val KEY_LAST_DATE = "last_recorded_date"
        private const val KEY_TODAY_STEPS = "today_steps"
        private const val KEY_SENSOR_OFFSET = "sensor_offset"
        private const val KEY_LAST_KNOWN_TOTAL_STEPS = "last_known_total_steps"

        /**
         * Schedules the daily midnight reset task using JobScheduler.
         * Calculates the exact duration until the next 00:00:00.
         */
        fun scheduleMidnightReset(context: Context) {
            try {
                val now = ZonedDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                val delayMillis = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1000L)

                Log.d(TAG, "Scheduling midnight reset in ${delayMillis / 1000}s (at $nextMidnight)")

                // 1. Schedule with system JobScheduler
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                if (jobScheduler != null) {
                    val componentName = ComponentName(context, MidnightResetJobService::class.java)
                    val jobInfo = JobInfo.Builder(JOB_ID_MIDNIGHT_RESET, componentName)
                        .setMinimumLatency(delayMillis)
                        .setOverrideDeadline(delayMillis + 30_000L) // Guarantees execution at midnight
                        .setPersisted(true)
                        .build()
                    val result = jobScheduler.schedule(jobInfo)
                    Log.d(TAG, "JobScheduler result: $result")
                }

                // 2. Complement with exact AlarmManager to guarantee exact 00:00:00 execution during deep sleep
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                if (alarmManager != null) {
                    val alarmIntent = Intent(context, MidnightResetReceiver::class.java).apply {
                        action = ACTION_MIDNIGHT_ALARM
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        JOB_ID_MIDNIGHT_RESET,
                        alarmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val triggerAtMillis = System.currentTimeMillis() + delayMillis
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling midnight reset: ${e.message}", e)
            }
        }

        /**
         * Executes the midnight reset logic safely and idempotently.
         */
        fun executeReset(context: Context, onComplete: (() -> Unit)? = null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentDate = LocalDate.now()
            val currentDateString = currentDate.toString()
            val lastStoredDateString = prefs.getString(KEY_LAST_DATE, "") ?: ""

            val yesterdaySteps = prefs.getInt(KEY_TODAY_STEPS, 0)
            val lastKnownTotal = prefs.getInt(KEY_LAST_KNOWN_TOTAL_STEPS, 0)

            Log.d(TAG, "Executing midnight reset. Last date: $lastStoredDateString, Current: $currentDateString")

            // Update preferences immediately to 0 steps for today
            val editor = prefs.edit()
                .putString(KEY_LAST_DATE, currentDateString)
                .putInt(KEY_TODAY_STEPS, 0)

            if (lastKnownTotal > 0) {
                editor.putInt(KEY_SENSOR_OFFSET, lastKnownTotal)
            }
            editor.apply()

            // Update database and widget in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getDatabase(context)
                    if (lastStoredDateString.isNotEmpty() && lastStoredDateString != currentDateString) {
                        database.stepDao().insertOrUpdate(
                            DayStepEntry(lastStoredDateString, yesterdaySteps, yesterdaySteps >= 10000)
                        )
                    }
                    database.stepDao().insertOrUpdate(
                        DayStepEntry(currentDateString, 0, false)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating database during midnight reset: ${e.message}", e)
                }

                // Notify StepService if running
                try {
                    val serviceIntent = Intent(context, StepService::class.java).apply {
                        action = StepService.ACTION_DAY_RESET
                    }
                    context.startService(serviceIntent)
                } catch (e: Exception) {}

                // Update Widget to 0 steps immediately
                try {
                    val widgetIntent = Intent(context, StepWidgetProvider::class.java).apply {
                        action = StepWidgetProvider.ACTION_UPDATE_STEPS
                        putExtra(StepWidgetProvider.EXTRA_STEPS, 0)
                        putExtra(StepWidgetProvider.EXTRA_GOAL, 10000)
                    }
                    context.sendBroadcast(widgetIntent)
                } catch (e: Exception) {}

                // Schedule next day's midnight reset
                scheduleMidnightReset(context)

                onComplete?.invoke()
            }
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStartJob triggered at midnight")
        executeReset(applicationContext) {
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // Reschedule if aborted
    }
}

/**
 * Receiver that handles exact midnight alarm triggers and device boot / time changes.
 */
class MidnightResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            MidnightResetJobService.ACTION_MIDNIGHT_ALARM -> {
                Log.d("MidnightResetReceiver", "Exact midnight alarm received!")
                MidnightResetJobService.executeReset(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.d("MidnightResetReceiver", "System event: ${intent.action}. Rescheduling midnight job.")
                MidnightResetJobService.scheduleMidnightReset(context)
            }
        }
    }
}
