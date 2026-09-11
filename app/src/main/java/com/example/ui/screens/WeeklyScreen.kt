package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.StepViewModel
import com.example.ui.components.StepChartContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeeklyScreen(viewModel: StepViewModel) {
    val weeklyEntries by viewModel.weeklyEntries.collectAsState()
    val dailyGoal by viewModel.dailyGoal.collectAsState()
    val chartStyle by viewModel.chartStyle.collectAsState()

    val totalWeeklySteps = weeklyEntries.sumOf { it.steps }
    val averageWeeklySteps = if (weeklyEntries.isNotEmpty()) totalWeeklySteps / weeklyEntries.size else 0
    val bestDayEntry = weeklyEntries.maxByOrNull { it.steps }
    val daysGoalMet = weeklyEntries.count { it.steps >= dailyGoal }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Section Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarViewWeek,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Resumen Semanal (Últimos 7 días)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Custom Chart Component
        StepChartContainer(
            entries = weeklyEntries,
            dailyGoal = dailyGoal,
            chartStyle = chartStyle,
            modifier = Modifier.testTag("weekly_chart_container")
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Estadísticas de la Semana",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Grid of Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(
                title = "Total Semana",
                value = String.format("%,d", totalWeeklySteps),
                subtitle = "pasos acumulados",
                modifier = Modifier.weight(1f)
            )
            StatBox(
                title = "Promedio Diario",
                value = String.format("%,d", averageWeeklySteps),
                subtitle = "pasos / día",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val bestDayFormatted = bestDayEntry?.let {
                try {
                    val p = LocalDate.parse(it.date)
                    p.format(DateTimeFormatter.ofPattern("EEEE", Locale("es", "ES"))).capitalize(Locale("es", "ES"))
                } catch (e: Exception) { "-" }
            } ?: "-"

            StatBox(
                title = "Mejor Día",
                value = bestDayFormatted,
                subtitle = "${String.format("%,d", bestDayEntry?.steps ?: 0)} pasos",
                highlight = true,
                modifier = Modifier.weight(1f)
            )
            StatBox(
                title = "Metas Cumplidas",
                value = "$daysGoalMet / ${weeklyEntries.size}",
                subtitle = "días superados",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    subtitle: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
