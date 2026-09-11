package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChartStyle
import com.example.data.DayStepEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StepChartContainer(
    entries: List<DayStepEntry>,
    dailyGoal: Int,
    chartStyle: ChartStyle,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selectedEntry = selectedIndex?.let { entries.getOrNull(it) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("step_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Selected tooltip header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chartStyle.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (selectedEntry != null) {
                    val dateFormatted = try {
                        val parsed = LocalDate.parse(selectedEntry.date)
                        parsed.format(DateTimeFormatter.ofPattern("dd MMM", Locale("es", "ES")))
                    } catch (e: Exception) { selectedEntry.date }

                    Text(
                        text = "$dateFormatted: ${String.format("%,d", selectedEntry.steps)} pasos",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedEntry.steps >= dailyGoal) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "Toca un punto para detalles",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (chartStyle) {
                ChartStyle.BAR -> BarStepChart(entries, dailyGoal, selectedIndex) { selectedIndex = it }
                ChartStyle.AREA_LINE -> AreaLineStepChart(entries, dailyGoal, selectedIndex) { selectedIndex = it }
                ChartStyle.DONUT -> DonutStepChart(entries, dailyGoal)
            }
        }
    }
}

@Composable
fun BarStepChart(
    entries: List<DayStepEntry>,
    dailyGoal: Int,
    selectedIndex: Int?,
    onSelectIndex: (Int) -> Unit
) {
    if (entries.isEmpty()) return

    val maxSteps = (entries.maxOfOrNull { it.steps } ?: dailyGoal).coerceAtLeast(dailyGoal + 2000)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val goalLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .testTag("bar_chart_canvas")
            .pointerInput(entries) {
                detectTapGestures { offset ->
                    val barWidthSpace = size.width / entries.size
                    val clickedIndex = (offset.x / barWidthSpace).toInt().coerceIn(0, entries.size - 1)
                    onSelectIndex(clickedIndex)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val barCount = entries.size
        val barSpacing = width / barCount
        val barWidth = (barSpacing * 0.65f).coerceAtMost(28.dp.toPx())

        // Draw goal reference line
        val goalY = height - ((dailyGoal.toFloat() / maxSteps) * height)
        drawLine(
            color = goalLineColor,
            start = Offset(0f, goalY),
            end = Offset(width, goalY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )

        entries.forEachIndexed { index, entry ->
            val barHeight = ((entry.steps.toFloat() / maxSteps) * height).coerceAtLeast(4.dp.toPx())
            val x = index * barSpacing + (barSpacing - barWidth) / 2f
            val y = height - barHeight

            val isSelected = selectedIndex == index
            val color = when {
                isSelected -> Color.White
                entry.steps >= dailyGoal -> secondaryColor
                else -> primaryColor
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
        }
    }
}

@Composable
fun AreaLineStepChart(
    entries: List<DayStepEntry>,
    dailyGoal: Int,
    selectedIndex: Int?,
    onSelectIndex: (Int) -> Unit
) {
    if (entries.isEmpty()) return

    val maxSteps = (entries.maxOfOrNull { it.steps } ?: dailyGoal).coerceAtLeast(dailyGoal + 2000)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val goalLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .testTag("area_line_chart_canvas")
            .pointerInput(entries) {
                detectTapGestures { offset ->
                    val pointSpacing = size.width / (entries.size - 1).coerceAtLeast(1)
                    val clickedIndex = (offset.x / pointSpacing).toInt().coerceIn(0, entries.size - 1)
                    onSelectIndex(clickedIndex)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val pointSpacing = if (entries.size > 1) width / (entries.size - 1) else width

        // Draw goal reference line
        val goalY = height - ((dailyGoal.toFloat() / maxSteps) * height)
        drawLine(
            color = goalLineColor,
            start = Offset(0f, goalY),
            end = Offset(width, goalY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )

        val points = entries.mapIndexed { index, entry ->
            val x = index * pointSpacing
            val y = height - ((entry.steps.toFloat() / maxSteps) * height)
            Offset(x, y)
        }

        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val control1 = Offset((p1.x + p2.x) / 2, p1.y)
                    val control2 = Offset((p1.x + p2.x) / 2, p2.y)
                    cubicTo(control1.x, control1.y, control2.x, control2.y, p2.x, p2.y)
                }
            }

            // Fill area gradient
            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent)
                )
            )

            // Draw line
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Draw points
        points.forEachIndexed { index, point ->
            val isSelected = selectedIndex == index
            val entry = entries[index]
            val radius = if (isSelected) 8.dp.toPx() else 4.dp.toPx()
            val color = if (entry.steps >= dailyGoal) secondaryColor else primaryColor

            drawCircle(
                color = color,
                center = point,
                radius = radius
            )
            if (isSelected) {
                drawCircle(
                    color = Color.White,
                    center = point,
                    radius = radius / 2
                )
            }
        }
    }
}

@Composable
fun DonutStepChart(
    entries: List<DayStepEntry>,
    dailyGoal: Int
) {
    if (entries.isEmpty()) return

    val totalSteps = entries.sumOf { it.steps }
    val daysWithGoal = entries.count { it.steps >= dailyGoal }
    val totalDays = entries.size
    val complianceRate = (daysWithGoal.toFloat() / totalDays.coerceAtLeast(1)) * 100f

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(170.dp)) {
            val strokeWidth = 20.dp.toPx()
            val diameter = size.width - strokeWidth

            // Track background
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(diameter, diameter),
                style = Stroke(strokeWidth)
            )

            // Goal compliance arc
            val sweepAngle = (complianceRate / 100f) * 360f
            drawArc(
                brush = Brush.sweepGradient(listOf(primaryColor, secondaryColor, primaryColor)),
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(diameter, diameter),
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${complianceRate.toInt()}%",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$daysWithGoal de $totalDays días meta",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
