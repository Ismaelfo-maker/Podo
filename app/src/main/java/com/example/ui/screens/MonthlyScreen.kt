package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
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

@Composable
fun MonthlyScreen(viewModel: StepViewModel) {
    val monthlyEntries by viewModel.monthlyEntries.collectAsState()
    val dailyGoal by viewModel.dailyGoal.collectAsState()
    val chartStyle by viewModel.chartStyle.collectAsState()

    val totalMonthlySteps = monthlyEntries.sumOf { it.steps }
    val averageMonthlySteps = if (monthlyEntries.isNotEmpty()) totalMonthlySteps / monthlyEntries.size else 0
    val daysGoalMet = monthlyEntries.count { it.steps >= dailyGoal }
    val totalDays = monthlyEntries.size.coerceAtLeast(1)
    val complianceRate = (daysGoalMet.toFloat() / totalDays) * 100f
    val totalKm = String.format("%.1f", totalMonthlySteps * 0.00076)

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Resumen Mensual (Últimos 30 días)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Custom Interactive Chart
        StepChartContainer(
            entries = monthlyEntries,
            dailyGoal = dailyGoal,
            chartStyle = chartStyle,
            modifier = Modifier.testTag("monthly_chart_container")
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Métricas del Mes",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(
                title = "Total Mes",
                value = String.format("%,d", totalMonthlySteps),
                subtitle = "pasos caminados",
                modifier = Modifier.weight(1f)
            )
            StatBox(
                title = "Promedio Diario",
                value = String.format("%,d", averageMonthlySteps),
                subtitle = "pasos por día",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(
                title = "Distancia Mes",
                value = "$totalKm km",
                subtitle = "recorridos",
                modifier = Modifier.weight(1f)
            )
            StatBox(
                title = "Cumplimiento Meta",
                value = "${complianceRate.toInt()}%",
                subtitle = "$daysGoalMet de $totalDays días",
                highlight = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
