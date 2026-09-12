package com.example

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppThemeStyle
import com.example.data.ChartStyle
import com.example.data.DayStepEntry
import com.example.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class StepViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val stepDao = database.stepDao()
    val userPreferences = UserPreferences(application)

    private val _currentDate = MutableStateFlow(LocalDate.now().toString())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()
    val todayDateString: String get() = _currentDate.value

    val themeStyle: StateFlow<AppThemeStyle> = userPreferences.themeStyle
    val chartStyle: StateFlow<ChartStyle> = userPreferences.chartStyle
    val dailyGoal: StateFlow<Int> = userPreferences.dailyGoal

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayEntry: StateFlow<DayStepEntry?> = _currentDate
        .flatMapLatest { date -> stepDao.getEntryFlowByDate(date) }
        .map { it ?: DayStepEntry(_currentDate.value, 0, false) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DayStepEntry(LocalDate.now().toString(), 0, false)
        )

    val historyEntries: StateFlow<List<DayStepEntry>> = stepDao.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Weekly entries (last 7 days including today)
    val weeklyEntries: StateFlow<List<DayStepEntry>> = stepDao.getAllEntries()
        .map { list ->
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(6)
            val dateMap = list.associateBy { it.date }
            
            val result = mutableListOf<DayStepEntry>()
            var curr = startDate
            while (!curr.isAfter(endDate)) {
                val dStr = curr.toString()
                val entry = dateMap[dStr] ?: DayStepEntry(dStr, 0, false)
                result.add(entry)
                curr = curr.plusDays(1)
            }
            result
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly entries (last 30 days including today)
    val monthlyEntries: StateFlow<List<DayStepEntry>> = stepDao.getAllEntries()
        .map { list ->
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(29)
            val dateMap = list.associateBy { it.date }

            val result = mutableListOf<DayStepEntry>()
            var curr = startDate
            while (!curr.isAfter(endDate)) {
                val dStr = curr.toString()
                val entry = dateMap[dStr] ?: DayStepEntry(dStr, 0, false)
                result.add(entry)
                curr = curr.plusDays(1)
            }
            result
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Guarantee clean start with ONLY real data: remove all fictitious / mock data
        viewModelScope.launch(Dispatchers.IO) {
            val todayStr = LocalDate.now().toString()
            if (!userPreferences.hasCleanedFakeData()) {
                // One-time complete purge of old fictitious entries
                stepDao.deleteAll()
                stepDao.insertOrUpdate(DayStepEntry(todayStr, 0, false))
                userPreferences.setHasCleanedFakeData(true)
                try {
                    val context = getApplication<Application>()
                    val intent = Intent(context, StepService::class.java).apply {
                        action = StepService.ACTION_RESET_ALL_STEPS
                    }
                    context.startService(intent)
                } catch (e: Exception) {}
            } else {
                val existing = stepDao.getEntryByDate(todayStr)
                if (existing == null) {
                    stepDao.insertOrUpdate(DayStepEntry(todayStr, 0, false))
                }
            }
        }
        // Continuous check to guarantee automatic midnight reset
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(15_000)
                val nowStr = LocalDate.now().toString()
                if (_currentDate.value != nowStr) {
                    _currentDate.value = nowStr
                }
            }
        }
    }

    fun checkDateTransition() {
        val nowStr = LocalDate.now().toString()
        if (_currentDate.value != nowStr) {
            _currentDate.value = nowStr
            viewModelScope.launch(Dispatchers.IO) {
                val existing = stepDao.getEntryByDate(nowStr)
                if (existing == null) {
                    stepDao.insertOrUpdate(DayStepEntry(nowStr, 0, false))
                }
            }
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            stepDao.deleteAll()
            val todayStr = LocalDate.now().toString()
            _currentDate.value = todayStr
            stepDao.insertOrUpdate(DayStepEntry(todayStr, 0, false))

            val context = getApplication<Application>()
            try {
                val intent = Intent(context, StepService::class.java).apply {
                    action = StepService.ACTION_RESET_ALL_STEPS
                }
                context.startService(intent)
            } catch (e: Exception) {
                // Service may not be running
            }
        }
    }

    fun deleteHistoryEntry(date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            stepDao.deleteByDate(date)
            val todayStr = LocalDate.now().toString()
            if (date == todayStr) {
                // Reset today to 0 steps
                stepDao.insertOrUpdate(DayStepEntry(todayStr, 0, false))
                val context = getApplication<Application>()
                try {
                    val intent = Intent(context, StepService::class.java).apply {
                        action = StepService.ACTION_RESET_TODAY_STEPS
                    }
                    context.startService(intent)
                } catch (e: Exception) {
                    // Service may not be running
                }
            }
        }
    }

    fun setThemeStyle(style: AppThemeStyle) {
        userPreferences.setThemeStyle(style)
    }

    fun setChartStyle(style: ChartStyle) {
        userPreferences.setChartStyle(style)
    }

    fun setDailyGoal(goal: Int) {
        userPreferences.setDailyGoal(goal)
    }

    fun addManualSteps(count: Int = 500) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateStr = LocalDate.now().toString()
            if (_currentDate.value != dateStr) {
                _currentDate.value = dateStr
            }
            val currentEntry = stepDao.getEntryByDate(dateStr)
            val newSteps = (currentEntry?.steps ?: 0) + count
            val goal = dailyGoal.value
            stepDao.insertOrUpdate(DayStepEntry(dateStr, newSteps, newSteps >= goal))
            
            // Send intent to service to sync notification and widget
            val context = getApplication<Application>()
            try {
                val intent = Intent(context, StepService::class.java).apply {
                    action = StepService.ACTION_ADD_MANUAL_STEPS
                    putExtra(StepService.EXTRA_STEPS_TO_ADD, count)
                }
                context.startService(intent)
            } catch (e: Exception) {
                // Service may not be running or background restricted
            }
        }
    }

    fun exportToCsv(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = historyEntries.value
            if (entries.isEmpty()) {
                onResult(false, "No hay datos para exportar")
                return@launch
            }
            val fileName = "pasos_${System.currentTimeMillis()}.csv"
            val csvContent = StringBuilder().apply {
                append("Fecha,Pasos,MetaAlcanzada\n")
                entries.forEach { append("${it.date},${it.steps},${it.goalMet}\n") }
            }.toString()
            try {
                val resolver = getApplication<Application>().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os -> os.write(csvContent.toByteArray()) }
                    onResult(true, "Guardado en Descargas: $fileName")
                } else onResult(false, "Error al crear archivo CSV")
            } catch (e: Exception) {
                onResult(false, "Error: ${e.localizedMessage}")
            }
        }
    }
}
