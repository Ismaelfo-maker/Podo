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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
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
import java.util.Locale

class StepService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var database: AppDatabase

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var currentDailyGoal = 10000
    private var dateChangeReceiver: BroadcastReceiver? = null
    private var lastAccelStepTimeMs = 0L

    companion object {
        private const val TAG = "StepService"
        private const val CHANNEL_ID = "step_counter_channel_v4"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "step_counter_prefs"
        private const val KEY_LAST_DATE = "last_date"
        private const val KEY_SENSOR_OFFSET = "sensor_offset"
        private const val KEY_TODAY_STEPS = "today_steps"
        private const val KEY_LAST_KNOWN_TOTAL_STEPS = "last_known_total_steps"

        const val ACTION_ADD_MANUAL_STEPS = "com.example.ADD_MANUAL_STEPS"
        const val EXTRA_STEPS_TO_ADD = "extra_steps_to_add"
        const val ACTION_DAY_RESET = "com.example.ACTION_DAY_RESET"
        const val ACTION_RESET_ALL_STEPS = "com.example.ACTION_RESET_ALL_STEPS"
        const val ACTION_RESET_TODAY_STEPS = "com.example.ACTION_RESET_TODAY_STEPS"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        database = AppDatabase.getDatabase(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Discover available sensors
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Register appropriate sensor for step detection
        registerSensors()

        createNotificationChannel()
        startInForeground()

        // Schedule JobScheduler task for guaranteed midnight reset
        MidnightResetJobService.scheduleMidnightReset(this)

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

    private fun registerSensors() {
        try {
            if (stepCounterSensor != null) {
                // Hardware step counter with immediate UI delivery (no delayed batching)
                sensorManager.registerListener(
                    this,
                    stepCounterSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                Log.d(TAG, "Registered TYPE_STEP_COUNTER")
            } else if (stepDetectorSensor != null) {
                // Hardware step detector
                sensorManager.registerListener(
                    this,
                    stepDetectorSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                Log.d(TAG, "Registered TYPE_STEP_DETECTOR")
            } else if (accelerometerSensor != null) {
                // Accelerometer fallback for devices/emulators without dedicated pedometer
                sensorManager.registerListener(
                    this,
                    accelerometerSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
                Log.d(TAG, "Registered TYPE_ACCELEROMETER fallback")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering step sensor listener: ${e.message}", e)
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
        val currentDateString = LocalDate.now().toString()

        when (intent?.action) {
            ACTION_ADD_MANUAL_STEPS -> {
                val stepsToAdd = intent.getIntExtra(EXTRA_STEPS_TO_ADD, 100)
                val currentSteps = prefs.getInt(KEY_TODAY_STEPS, 0)
                val newSteps = currentSteps + stepsToAdd
                val lastKnownTotal = prefs.getInt(KEY_LAST_KNOWN_TOTAL_STEPS, 0)

                val editor = prefs.edit().putInt(KEY_TODAY_STEPS, newSteps)
                if (prefs.contains(KEY_SENSOR_OFFSET)) {
                    val currentOffset = prefs.getInt(KEY_SENSOR_OFFSET, 0)
                    editor.putInt(KEY_SENSOR_OFFSET, (currentOffset - stepsToAdd).coerceAtLeast(0))
                } else if (lastKnownTotal > 0) {
                    editor.putInt(KEY_SENSOR_OFFSET, (lastKnownTotal - newSteps).coerceAtLeast(0))
                }
                editor.apply()
                updateDatabaseAndWidget(currentDateString, newSteps)
            }
            ACTION_DAY_RESET, ACTION_RESET_ALL_STEPS, ACTION_RESET_TODAY_STEPS -> {
                val lastKnownTotal = prefs.getInt(KEY_LAST_KNOWN_TOTAL_STEPS, 0)
                val editor = prefs.edit()
                    .putInt(KEY_TODAY_STEPS, 0)
                    .putString(KEY_LAST_DATE, currentDateString)
                if (lastKnownTotal > 0) {
                    editor.putInt(KEY_SENSOR_OFFSET, lastKnownTotal)
                }
                editor.apply()
                updateDatabaseAndWidget(currentDateString, 0)
            }
            else -> {
                startInForeground()
            }
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
        val currentSteps = prefs.getInt(KEY_TODAY_STEPS, 0)
        val notification = buildStepsNotification(currentSteps)
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
        if (event == null) return
        val sensorType = event.sensor.type

        when (sensorType) {
            Sensor.TYPE_STEP_COUNTER -> {
                val totalStepsSinceBoot = event.values[0].toInt()
                prefs.edit().putInt(KEY_LAST_KNOWN_TOTAL_STEPS, totalStepsSinceBoot).apply()

                val currentDate = LocalDate.now()
                val currentDateString = currentDate.toString()
                val lastStoredDateString = prefs.getString(KEY_LAST_DATE, "") ?: ""

                if (lastStoredDateString.isEmpty() || currentDateString != lastStoredDateString) {
                    val yesterdaySteps = prefs.getInt(KEY_TODAY_STEPS, 0)
                    if (lastStoredDateString.isNotEmpty()) {
                        serviceScope.launch {
                            database.stepDao().insertOrUpdate(
                                DayStepEntry(lastStoredDateString, yesterdaySteps, yesterdaySteps >= currentDailyGoal)
                            )
                        }
                    }
                    prefs.edit()
                        .putString(KEY_LAST_DATE, currentDateString)
                        .putInt(KEY_SENSOR_OFFSET, totalStepsSinceBoot)
                        .putInt(KEY_TODAY_STEPS, 0)
                        .apply()
                    updateDatabaseAndWidget(currentDateString, 0)
                    return
                }

                // Same day: initialize offset if not present
                if (!prefs.contains(KEY_SENSOR_OFFSET)) {
                    val currentToday = prefs.getInt(KEY_TODAY_STEPS, 0)
                    val initialOffset = (totalStepsSinceBoot - currentToday).coerceAtLeast(0)
                    prefs.edit().putInt(KEY_SENSOR_OFFSET, initialOffset).apply()
                }

                val offset = prefs.getInt(KEY_SENSOR_OFFSET, totalStepsSinceBoot)
                var currentTodaySteps = totalStepsSinceBoot - offset

                if (currentTodaySteps < 0) {
                    // Device rebooted: sensor reset to zero
                    val prevToday = prefs.getInt(KEY_TODAY_STEPS, 0)
                    val newOffset = (totalStepsSinceBoot - prevToday).coerceAtLeast(0)
                    prefs.edit().putInt(KEY_SENSOR_OFFSET, newOffset).apply()
                    currentTodaySteps = (totalStepsSinceBoot - newOffset).coerceAtLeast(0)
                }

                prefs.edit().putInt(KEY_TODAY_STEPS, currentTodaySteps).apply()
                updateDatabaseAndWidget(currentDateString, currentTodaySteps)
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                // Each detector event represents a physical step taken
                checkAndHandleDateChange()
                val currentDateString = LocalDate.now().toString()
                val currentTodaySteps = prefs.getInt(KEY_TODAY_STEPS, 0) + 1
                prefs.edit().putInt(KEY_TODAY_STEPS, currentTodaySteps).apply()
                updateDatabaseAndWidget(currentDateString, currentTodaySteps)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // If neither hardware step counter nor detector is available, detect steps via accelerometer peaks
                if (stepCounterSensor == null && stepDetectorSensor == null) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    val now = System.currentTimeMillis()

                    // Walking produces dynamic acceleration spikes above normal 9.8 m/s²
                    if (magnitude > 11.8f && (now - lastAccelStepTimeMs) > 300) {
                        lastAccelStepTimeMs = now
                        checkAndHandleDateChange()
                        val currentDateString = LocalDate.now().toString()
                        val currentTodaySteps = prefs.getInt(KEY_TODAY_STEPS, 0) + 1
                        prefs.edit().putInt(KEY_TODAY_STEPS, currentTodaySteps).apply()
                        updateDatabaseAndWidget(currentDateString, currentTodaySteps)
                    }
                }
            }
        }
    }

    private fun updateDatabaseAndWidget(dateString: String, steps: Int) {
        serviceScope.launch {
            database.stepDao().insertOrUpdate(DayStepEntry(dateString, steps, steps >= currentDailyGoal))
        }
        updateWidget(steps)
        updateNotification(steps)
    }

    private fun updateWidget(steps: Int) {
        val widgetIntent = Intent(this, StepWidgetProvider::class.java).apply {
            action = StepWidgetProvider.ACTION_UPDATE_STEPS
            putExtra(StepWidgetProvider.EXTRA_STEPS, steps)
            putExtra(StepWidgetProvider.EXTRA_GOAL, currentDailyGoal)
        }
        sendBroadcast(widgetIntent)
    }

    private fun updateNotification(steps: Int) {
        try {
            val notification = buildStepsNotification(steps)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification: ${e.message}", e)
        }
    }

    /**
     * Generates a clean dynamic small icon rendering the step number for the notification shade.
     */
    private fun createStepNumberIcon(steps: Int): IconCompat {
        return try {
            val density = resources.displayMetrics.density
            val sizePx = (24 * density).toInt().coerceAtLeast(48)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val text = when {
                steps < 1000 -> steps.toString()
                steps < 10000 -> String.format(Locale.US, "%.1fk", steps / 1000.0)
                else -> "${steps / 1000}k"
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = when (text.length) {
                    1, 2 -> sizePx * 0.70f
                    3 -> sizePx * 0.54f
                    else -> sizePx * 0.42f
                }
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val textBounds = Rect()
            paint.getTextBounds(text, 0, text.length, textBounds)
            val y = (sizePx / 2f) + (textBounds.height() / 2f) - textBounds.bottom
            canvas.drawText(text, sizePx / 2f, y, paint)

            IconCompat.createWithBitmap(bitmap)
        } catch (e: Exception) {
            IconCompat.createWithResource(this, R.drawable.ic_footprint_stat)
        }
    }

    /**
     * Builds the notification that displays the step count and small step-number icon.
     * Uses IMPORTANCE_MIN so that it is completely hidden from the status bar when closed
     * and only visible when the user pulls down the notification shade.
     */
    private fun buildStepsNotification(steps: Int): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smallIcon = createStepNumberIcon(steps)
        val formattedSteps = String.format(Locale.US, "%,d", steps)
        val formattedGoal = String.format(Locale.US, "%,d", currentDailyGoal)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("$formattedSteps pasos")
            .setContentText("Meta diaria: $formattedGoal pasos")
            .setNumber(steps)
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
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                notificationManager.deleteNotificationChannel("step_counter_channel")
                notificationManager.deleteNotificationChannel("step_counter_channel_silent")
            } catch (e: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Pasos en Segundo Plano",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Muestra el número de pasos en la barra de notificaciones sin icono en la barra oculta"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
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

