package com.example

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.data.DayStepEntry
import com.example.widget.StepWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

class StepService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var database: AppDatabase

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var currentDailyGoal = 10000
    private var dateChangeReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "StepService"
        private const val CHANNEL_ID = "step_counter_channel_silent"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "step_counter_prefs"
        private const val KEY_LAST_DATE = "last_date"
        private const val KEY_SENSOR_OFFSET = "sensor_offset"
        private const val KEY_TODAY_STEPS = "today_steps"
        private const val KEY_LAST_KNOWN_TOTAL_STEPS = "last_known_total_steps"

        const val ACTION_ADD_MANUAL_STEPS = "com.example.ADD_MANUAL_STEPS"
        const val EXTRA_STEPS_TO_ADD = "extra_steps_to_add"
        const val ACTION_DAY_RESET = "com.example.ACTION_DAY_RESET"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        database = AppDatabase.getDatabase(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Register hardware step sensor with batching
        stepCounterSensor?.let { sensor ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 5_000_000)
                } else {
                    sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering step sensor listener: ${e.message}", e)
            }
        }

        createNotificationChannel()
        startInForeground()

        // Register system broadcast receiver for midnight date/time changes
        registerDateChangeReceiver()

        // Background coroutine checking for midnight date change every 20 seconds
        serviceScope.launch {
            while (isActive) {
                checkAndHandleDateChange()
                delay(20_000)
            }
        }
    }

    private fun registerDateChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        dateChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                checkAndHandleDateChange()
            }
        }
        registerReceiver(dateChangeReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        checkAndHandleDateChange()
        if (intent?.action == ACTION_ADD_MANUAL_STEPS) {
            val stepsToAdd = intent.getIntExtra(EXTRA_STEPS_TO_ADD, 100)
            val currentSteps = prefs.getInt(KEY_TODAY_STEPS, 0)
            val newSteps = currentSteps + stepsToAdd
            val currentDateString = LocalDate.now().toString()
            prefs.edit().putInt(KEY_TODAY_STEPS, newSteps).apply()
            updateDatabaseAndWidget(currentDateString, newSteps)
        } else {
            startInForeground()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure service automatically restarts if the user clears all tasks from recent apps
        val restartServiceIntent = Intent(applicationContext, StepService::class.java).apply {
            setPackage(packageName)
        }
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext,
            1005,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    private fun startInForeground() {
        val notification = buildSilentNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground service with type health: ${e.message}", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback startForeground failed: ${e2.message}", e2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
        }
    }

    /**
     * Automatic midnight reset: Checks if the calendar date has advanced past lastStoredDate.
     * If so, archives yesterday's steps and resets today's steps to 0 automatically.
     */
    private fun checkAndHandleDateChange() {
        val currentDate = LocalDate.now()
        val currentDateString = currentDate.toString()
        val lastStoredDateString = prefs.getString(KEY_LAST_DATE, "") ?: ""

        if (lastStoredDateString.isEmpty()) {
            prefs.edit()
                .putString(KEY_LAST_DATE, currentDateString)
                .putInt(KEY_TODAY_STEPS, 0)
                .apply()
            serviceScope.launch {
                database.stepDao().insertOrUpdate(DayStepEntry(currentDateString, 0, false))
            }
            updateWidget(0)
            return
        }

        val lastStoredDate = try { LocalDate.parse(lastStoredDateString) } catch (e: Exception) { currentDate }

        if (currentDate.isAfter(lastStoredDate)) {
            // Midnight transition detected!
            val yesterdaySteps = prefs.getInt(KEY_TODAY_STEPS, 0)
            val lastKnownTotal = prefs.getInt(KEY_LAST_KNOWN_TOTAL_STEPS, 0)

            // Save yesterday's steps
            serviceScope.launch {
                database.stepDao().insertOrUpdate(
                    DayStepEntry(lastStoredDateString, yesterdaySteps, yesterdaySteps >= currentDailyGoal)
                )
                // Initialize today's database entry with 0 steps
                database.stepDao().insertOrUpdate(
                    DayStepEntry(currentDateString, 0, false)
                )
            }

            // Update sensor offset and reset today steps to 0
            val editor = prefs.edit()
                .putString(KEY_LAST_DATE, currentDateString)
                .putInt(KEY_TODAY_STEPS, 0)
            if (lastKnownTotal > 0) {
                editor.putInt(KEY_SENSOR_OFFSET, lastKnownTotal)
            }
            editor.apply()

            // Update Home Screen Widget to 0 steps immediately
            updateWidget(0)

            // Notify application
            val resetIntent = Intent(ACTION_DAY_RESET)
            sendBroadcast(resetIntent)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val totalStepsSinceBoot = event.values[0].toInt()

        // Cache last total reading from hardware sensor
        prefs.edit().putInt(KEY_LAST_KNOWN_TOTAL_STEPS, totalStepsSinceBoot).apply()

        val currentDate = LocalDate.now()
        val currentDateString = currentDate.toString()
        val lastStoredDateString = prefs.getString(KEY_LAST_DATE, "") ?: ""

        if (lastStoredDateString.isEmpty()) {
            prefs.edit()
                .putString(KEY_LAST_DATE, currentDateString)
                .putInt(KEY_SENSOR_OFFSET, totalStepsSinceBoot)
                .putInt(KEY_TODAY_STEPS, 0)
                .apply()
            updateDatabaseAndWidget(currentDateString, 0)
            return
        }

        val lastStoredDate = try { LocalDate.parse(lastStoredDateString) } catch (e: Exception) { currentDate }

        if (currentDate.isAfter(lastStoredDate)) {
            // New day: archive yesterday and reset offset
            val offset = prefs.getInt(KEY_SENSOR_OFFSET, totalStepsSinceBoot)
            val finalYesterdaySteps = (totalStepsSinceBoot - offset).coerceAtLeast(0)

            serviceScope.launch {
                database.stepDao().insertOrUpdate(
                    DayStepEntry(lastStoredDateString, finalYesterdaySteps, finalYesterdaySteps >= currentDailyGoal)
                )
                database.stepDao().insertOrUpdate(
                    DayStepEntry(currentDateString, 0, false)
                )
            }
            prefs.edit()
                .putString(KEY_LAST_DATE, currentDateString)
                .putInt(KEY_SENSOR_OFFSET, totalStepsSinceBoot)
                .putInt(KEY_TODAY_STEPS, 0)
                .apply()
            updateDatabaseAndWidget(currentDateString, 0)
        } else {
            val offset = prefs.getInt(KEY_SENSOR_OFFSET, totalStepsSinceBoot)
            var currentTodaySteps = totalStepsSinceBoot - offset
            if (currentTodaySteps < 0) {
                prefs.edit().putInt(KEY_SENSOR_OFFSET, totalStepsSinceBoot).apply()
                currentTodaySteps = 0
            }
            prefs.edit().putInt(KEY_TODAY_STEPS, currentTodaySteps).apply()
            updateDatabaseAndWidget(currentDateString, currentTodaySteps)
        }
    }

    private fun updateDatabaseAndWidget(dateString: String, steps: Int) {
        serviceScope.launch {
            database.stepDao().insertOrUpdate(DayStepEntry(dateString, steps, steps >= currentDailyGoal))
        }
        updateWidget(steps)
    }

    private fun updateWidget(steps: Int) {
        val widgetIntent = Intent(this, StepWidgetProvider::class.java).apply {
            action = StepWidgetProvider.ACTION_UPDATE_STEPS
            putExtra(StepWidgetProvider.EXTRA_STEPS, steps)
            putExtra(StepWidgetProvider.EXTRA_GOAL, currentDailyGoal)
        }
        sendBroadcast(widgetIntent)
    }

    /**
     * Builds a completely silent notification that does not appear in the status bar
     * (uses IMPORTANCE_MIN, PRIORITY_MIN, and transparent icon).
     */
    private fun buildSilentNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transparent)
            .setContentTitle(null)
            .setContentText(null)
            .setShowWhen(false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Pasos en Segundo Plano",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Servicio silencioso de conteo de pasos sin icono en la barra de estado"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        dateChangeReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) {}
        }
        sensorManager.unregisterListener(this)
        serviceJob.cancel()
    }
}

