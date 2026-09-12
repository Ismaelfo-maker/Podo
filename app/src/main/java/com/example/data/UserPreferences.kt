package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeStyle(
    val title: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val isDarkOled: Boolean = false
) {
    OCEAN_BLUE("Azul Deportivo", 0xFF2196F3, 0xFF00BCD4),
    EMERALD_GREEN("Verde Salud", 0xFF4CAF50, 0xFF8BC34A),
    SUNSET_ORANGE("Naranja Energía", 0xFFFF9800, 0xFFFF5722),
    NEON_PURPLE("Púrpura Nocturno", 0xFF9C27B0, 0xFFE91E63),
    AMOLED_DARK("Ultra-Ahorro Batería (AMOLED)", 0xFF00E676, 0xFF1DE9B6, isDarkOled = true)
}

enum class ChartStyle(val title: String) {
    BAR("Gráfico de Barras"),
    AREA_LINE("Gráfico de Área / Líneas"),
    DONUT("Gráfico Circular / Anular")
}

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_settings_prefs", Context.MODE_PRIVATE)

    private val _themeStyle = MutableStateFlow(getSavedThemeStyle())
    val themeStyle: StateFlow<AppThemeStyle> = _themeStyle.asStateFlow()

    private val _chartStyle = MutableStateFlow(getSavedChartStyle())
    val chartStyle: StateFlow<ChartStyle> = _chartStyle.asStateFlow()

    private val _dailyGoal = MutableStateFlow(getSavedDailyGoal())
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    private fun getSavedThemeStyle(): AppThemeStyle {
        val name = prefs.getString(KEY_THEME_STYLE, AppThemeStyle.OCEAN_BLUE.name)
        return try {
            AppThemeStyle.valueOf(name ?: AppThemeStyle.OCEAN_BLUE.name)
        } catch (e: Exception) {
            AppThemeStyle.OCEAN_BLUE
        }
    }

    fun setThemeStyle(style: AppThemeStyle) {
        prefs.edit().putString(KEY_THEME_STYLE, style.name).apply()
        _themeStyle.value = style
    }

    private fun getSavedChartStyle(): ChartStyle {
        val name = prefs.getString(KEY_CHART_STYLE, ChartStyle.BAR.name)
        return try {
            ChartStyle.valueOf(name ?: ChartStyle.BAR.name)
        } catch (e: Exception) {
            ChartStyle.BAR
        }
    }

    fun setChartStyle(style: ChartStyle) {
        prefs.edit().putString(KEY_CHART_STYLE, style.name).apply()
        _chartStyle.value = style
    }

    private fun getSavedDailyGoal(): Int {
        return prefs.getInt(KEY_DAILY_GOAL, 10000)
    }

    fun setDailyGoal(goal: Int) {
        prefs.edit().putInt(KEY_DAILY_GOAL, goal).apply()
        _dailyGoal.value = goal
    }

    fun hasCleanedFakeData(): Boolean {
        return prefs.getBoolean(KEY_HAS_CLEANED_FAKE_DATA, false)
    }

    fun setHasCleanedFakeData(cleaned: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_CLEANED_FAKE_DATA, cleaned).apply()
    }

    companion object {
        private const val KEY_THEME_STYLE = "theme_style"
        private const val KEY_CHART_STYLE = "chart_style"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_HAS_CLEANED_FAKE_DATA = "has_cleaned_fake_data_v2"
    }
}
