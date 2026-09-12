package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.StepViewModel
import com.example.ui.components.FootprintTrailLandscape

@Composable
fun TodayScreen(viewModel: StepViewModel) {
    val context = LocalContext.current
    val todayEntry by viewModel.todayEntry.collectAsState()
    val dailyGoal by viewModel.dailyGoal.collectAsState()
    val themeStyle by viewModel.themeStyle.collectAsState()

    var showGoalDialog by remember { mutableStateOf(false) }

    val steps = todayEntry?.steps ?: 0

    // Calculate metrics
    val distanceKm = String.format("%.2f", steps * 0.00076) // ~0.76m per step
    val caloriesKcal = (steps * 0.04).toInt() // ~0.04 kcal per step
    val activeMinutes = (steps / 100) // ~100 steps per min average pace

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Live Sensor Status Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sensor activo • Conteo en vivo",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Fullscreen Barefoot Mountain Trail Graphic Progress Canvas (Expanding to fill screen)
        FootprintTrailLandscape(
            steps = steps,
            dailyGoal = dailyGoal,
            themeStyle = themeStyle,
            onGoalClick = { showGoalDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Metric Cards Grid (Distancia, Calorías, Tiempo - intact)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Distancia",
                value = "$distanceKm km",
                icon = Icons.Default.DirectionsRun,
                iconColor = primaryColor,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Calorías",
                value = "$caloriesKcal kcal",
                icon = Icons.Default.LocalFireDepartment,
                iconColor = Color(0xFFFF5722),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Tiempo",
                value = "$activeMinutes min",
                icon = Icons.Default.Schedule,
                iconColor = secondaryColor,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Goal Modification Dialog
    if (showGoalDialog) {
        var tempGoal by remember { mutableFloatStateOf(dailyGoal.toFloat()) }
        val presetGoals = listOf(5000, 8000, 10000, 12000, 15000)

        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Establecer Meta Diaria",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${String.format("%,d", tempGoal.toInt())} pasos",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = tempGoal,
                        onValueChange = { tempGoal = it },
                        valueRange = 2000f..30000f,
                        steps = 27,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Metas rápidas:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        presetGoals.forEach { preset ->
                            FilterChip(
                                selected = tempGoal.toInt() == preset,
                                onClick = { tempGoal = preset.toFloat() },
                                label = { Text("${preset / 1000}k") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setDailyGoal(tempGoal.toInt())
                        showGoalDialog = false
                    },
                    modifier = Modifier.testTag("save_goal_button")
                ) {
                    Text("Guardar Meta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
