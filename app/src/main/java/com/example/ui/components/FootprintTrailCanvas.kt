package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppThemeStyle
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FootprintTrailLandscape(
    steps: Int,
    dailyGoal: Int,
    themeStyle: AppThemeStyle,
    onGoalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Smooth animated step count
    val animatedSteps by animateFloatAsState(
        targetValue = steps.toFloat(),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "animated_steps"
    )

    // Load the user's custom transparent foot character icon
    val footBitmap = ImageBitmap.imageResource(id = R.drawable.ic_foot_foreground)

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = themeStyle.isDarkOled

    val isGoalMet = steps >= dailyGoal
    val progressPercent = if (dailyGoal > 0) ((animatedSteps / dailyGoal) * 100).toInt() else 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B),
                            Color(0xFF0D131F)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFEF9C3),
                            Color(0xFFF0FDF4),
                            Color(0xFFDCFCE7)
                        )
                    )
                }
            )
            .testTag("footprint_trail_landscape")
    ) {
        // Background Artwork displaying the mountain landscape with the light brown trail
        Image(
            painter = painterResource(id = R.drawable.img_mountain_trail_bg),
            contentDescription = "Mountain Trail Background",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Canvas for Interactive Dynamic Footprints & Summit Prayer Flags
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Build Outward Trail Path matching the light brown dirt path in the background image
            val outwardPath = Path().apply {
                moveTo(w * 0.48f, h * 0.96f)
                // Broad dirt road heading up-right
                cubicTo(
                    w * 0.52f, h * 0.93f,
                    w * 0.60f, h * 0.91f,
                    w * 0.60f, h * 0.87f
                )
                // Switchback curve left across lower slope
                cubicTo(
                    w * 0.60f, h * 0.81f,
                    w * 0.38f, h * 0.79f,
                    w * 0.33f, h * 0.74f
                )
                // Ascending rightward curve
                cubicTo(
                    w * 0.31f, h * 0.69f,
                    w * 0.54f, h * 0.66f,
                    w * 0.65f, h * 0.61f
                )
                // Switchback curve left across middle ridge
                cubicTo(
                    w * 0.68f, h * 0.58f,
                    w * 0.54f, h * 0.56f,
                    w * 0.43f, h * 0.54f
                )
                // Curve right along rocky path
                cubicTo(
                    w * 0.38f, h * 0.50f,
                    w * 0.55f, h * 0.47f,
                    w * 0.62f, h * 0.44f
                )
                // Upper ridge winding switchback
                cubicTo(
                    w * 0.68f, h * 0.38f,
                    w * 0.74f, h * 0.30f,
                    w * 0.75f, h * 0.24f
                )
                // Final ascent to the mountain peak
                cubicTo(
                    w * 0.74f, h * 0.17f,
                    w * 0.60f, h * 0.13f,
                    w * 0.50f, h * 0.09f
                )
            }

            // 2. Alternative return path down the mountain when daily goal is exceeded
            val returnPath = Path().apply {
                moveTo(w * 0.50f, h * 0.09f)
                cubicTo(
                    w * 0.42f, h * 0.13f,
                    w * 0.34f, h * 0.18f,
                    w * 0.28f, h * 0.26f
                )
                cubicTo(
                    w * 0.22f, h * 0.34f,
                    w * 0.22f, h * 0.44f,
                    w * 0.27f, h * 0.52f
                )
                cubicTo(
                    w * 0.30f, h * 0.60f,
                    w * 0.20f, h * 0.68f,
                    w * 0.23f, h * 0.76f
                )
                cubicTo(
                    w * 0.26f, h * 0.84f,
                    w * 0.38f, h * 0.90f,
                    w * 0.48f, h * 0.96f
                )
            }

            // 3. Draw Tibetan Prayer Flags & Sunburst ONLY when goal is reached!
            // When goal is not reached, NO flags are drawn at all as requested by the user.
            if (isGoalMet) {
                drawSummitPrayerFlags(w * 0.50f, h * 0.09f, w, h)
            }

            // 4. Calculate and Draw Dynamic Footprints using the user's foot icon
            val totalOutwardSteps = dailyGoal.coerceAtLeast(1).toFloat()
            val currentSteps = animatedSteps

            val outwardRatio = (currentSteps / totalOutwardSteps).coerceIn(0f, 1f)
            val returnRatio = if (currentSteps > totalOutwardSteps) {
                ((currentSteps - totalOutwardSteps) / totalOutwardSteps).coerceIn(0f, 1.2f)
            } else 0f

            // Outward Footprints: Non-overlapping sequence with perspective-scaled stride and gradual size
            val pmOutward = PathMeasure()
            pmOutward.setPath(outwardPath, false)
            val outwardLength = pmOutward.length

            val outwardNodes = mutableListOf<PathFootprintNode>()
            var distOutward = 10.dp.toPx()
            var isLeftOutward = false
            while (distOutward < outwardLength - 6.dp.toPx()) {
                val pos = pmOutward.getPosition(distOutward)
                val tangent = pmOutward.getTangent(distOutward)
                val angleRad = atan2(tangent.y, tangent.x)

                // Gradual perspective scaling: smaller as ascending towards summit (y=0.09h), larger at base (y=0.96h)
                val heightFraction = ((pos.y - h * 0.09f) / (h * 0.96f - h * 0.09f)).coerceIn(0f, 1f)
                val footScale = (0.24f + 0.52f * heightFraction).coerceIn(0.24f, 0.76f)

                // Alternating left/right step separation scaled with perspective to stay strictly on the trail
                val sideOffset = (if (isLeftOutward) -4.5f.dp.toPx() else 4.5f.dp.toPx()) * footScale
                val perpX = -tangent.y * sideOffset
                val perpY = tangent.x * sideOffset
                val footprintPos = Offset(pos.x + perpX, pos.y + perpY)

                outwardNodes.add(
                    PathFootprintNode(
                        position = footprintPos,
                        angleRad = angleRad,
                        scale = footScale,
                        isLeft = isLeftOutward
                    )
                )

                isLeftOutward = !isLeftOutward
                // Stride advance proportional to foot scale so footprints never bunch up or collide
                val advance = (28.dp.toPx() * footScale).coerceAtLeast(8.dp.toPx())
                distOutward += advance
            }

            val countOutwardToDraw = (outwardRatio * outwardNodes.size).toInt().coerceAtMost(outwardNodes.size)
            for (i in 0 until countOutwardToDraw) {
                val node = outwardNodes[i]
                drawIconFootprint(
                    image = footBitmap,
                    center = node.position,
                    angleRad = node.angleRad,
                    isLeftFoot = node.isLeft,
                    scale = node.scale,
                    alpha = 0.96f
                )
            }

            // Return Footprints along the alternative path when goal is exceeded
            if (returnRatio > 0f) {
                val pmReturn = PathMeasure()
                pmReturn.setPath(returnPath, false)
                val returnLength = pmReturn.length

                val returnNodes = mutableListOf<PathFootprintNode>()
                var distReturn = 10.dp.toPx()
                var isLeftReturn = false
                while (distReturn < returnLength - 6.dp.toPx()) {
                    val pos = pmReturn.getPosition(distReturn)
                    val tangent = pmReturn.getTangent(distReturn)
                    val angleRad = atan2(tangent.y, tangent.x)

                    val heightFraction = ((pos.y - h * 0.09f) / (h * 0.96f - h * 0.09f)).coerceIn(0f, 1f)
                    val footScale = (0.24f + 0.52f * heightFraction).coerceIn(0.24f, 0.76f)

                    val sideOffset = (if (isLeftReturn) -4.5f.dp.toPx() else 4.5f.dp.toPx()) * footScale
                    val perpX = -tangent.y * sideOffset
                    val perpY = tangent.x * sideOffset
                    val footprintPos = Offset(pos.x + perpX, pos.y + perpY)

                    returnNodes.add(
                        PathFootprintNode(
                            position = footprintPos,
                            angleRad = angleRad,
                            scale = footScale,
                            isLeft = isLeftReturn
                        )
                    )

                    isLeftReturn = !isLeftReturn
                    val advance = (28.dp.toPx() * footScale).coerceAtLeast(8.dp.toPx())
                    distReturn += advance
                }

                val countReturnToDraw = (returnRatio * returnNodes.size).toInt().coerceAtMost(returnNodes.size)
                for (i in 0 until countReturnToDraw) {
                    val node = returnNodes[i]
                    drawIconFootprint(
                        image = footBitmap,
                        center = node.position,
                        angleRad = node.angleRad,
                        isLeftFoot = node.isLeft,
                        scale = node.scale,
                        alpha = 1.0f,
                        isGoldenGlow = true
                    )
                }
            }
        }

        // Overlay: Step Count Infographic Card (Clickable to edit goal)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = surfaceColor.copy(alpha = if (isDark) 0.88f else 0.94f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGoalClick() }
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format("%,d", steps),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("today_steps_count_display")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "pasos",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isGoalMet) "¡Meta alcanzada! (" + String.format("%,d", dailyGoal) + ")" else "Pasos de hoy • Meta: " + String.format("%,d", dailyGoal),
                                fontSize = 12.sp,
                                color = if (isGoalMet) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar Meta",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Progress Percentage Pill Badge
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isGoalMet) Color(0xFFFFD700).copy(alpha = 0.25f) else primaryColor.copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isGoalMet) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = "$progressPercent%",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGoalMet) Color(0xFFFFB300) else primaryColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws the Tibetan Prayer Flags at the mountain summit.
 * Appears ONLY when the user reaches their daily step goal!
 */
private fun DrawScope.drawSummitPrayerFlags(
    x: Float,
    y: Float,
    w: Float,
    h: Float
) {
    // 1. Golden Sun Rays behind summit
    val rayCount = 14
    val rayLength = 65.dp.toPx()
    for (r in 0 until rayCount) {
        val rayAngle = (r.toFloat() / rayCount) * (2 * Math.PI).toFloat()
        val rayEndX = x + cos(rayAngle.toDouble()).toFloat() * rayLength
        val rayEndY = y + sin(rayAngle.toDouble()).toFloat() * rayLength

        drawLine(
            color = Color(0xFFFFD700).copy(alpha = 0.40f),
            start = Offset(x, y),
            end = Offset(rayEndX, rayEndY),
            strokeWidth = 2.5f.dp.toPx()
        )
    }

    // 2. Central Prayer Flag Pole
    val poleTop = Offset(x, y - 28.dp.toPx())
    val poleBottom = Offset(x, y + 12.dp.toPx())
    drawLine(
        color = Color(0xFF422006),
        start = poleBottom,
        end = poleTop,
        strokeWidth = 3.5f.dp.toPx()
    )

    // 3. Multi-colored Tibetan Prayer Flag Lines (Left String & Right String)
    // Colors of Tibetan Prayer Flags: Blue, Yellow, Red, Green, White
    val flagColors = listOf(
        Color(0xFF1E88E5), // Blue
        Color(0xFFFFD54F), // Yellow
        Color(0xFFE53935), // Red
        Color(0xFF43A047), // Green
        Color(0xFFFFFFFF)  // White
    )

    // Left Flag String Path
    val leftStringEnd = Offset(x - w * 0.22f, y + 24.dp.toPx())
    val leftStringMid = Offset(x - w * 0.11f, y)

    val leftStringPath = Path().apply {
        moveTo(poleTop.x, poleTop.y)
        quadraticTo(leftStringMid.x, leftStringMid.y, leftStringEnd.x, leftStringEnd.y)
    }
    drawPath(
        path = leftStringPath,
        color = Color.White.copy(alpha = 0.9f),
        style = Stroke(width = 2.dp.toPx())
    )

    // Draw Left Flags
    val leftPm = PathMeasure()
    leftPm.setPath(leftStringPath, false)
    val leftLen = leftPm.length
    val flagCount = 7

    for (f in 0 until flagCount) {
        val dist = (f.toFloat() + 0.5f) / flagCount * leftLen
        val pos = leftPm.getPosition(dist)
        val flagColor = flagColors[f % flagColors.size]

        val flagPath = Path().apply {
            moveTo(pos.x, pos.y)
            lineTo(pos.x - 7.dp.toPx(), pos.y + 12.dp.toPx())
            lineTo(pos.x + 3.dp.toPx(), pos.y + 12.dp.toPx())
            lineTo(pos.x + 4.dp.toPx(), pos.y)
            close()
        }
        drawPath(flagPath, color = flagColor)
    }

    // Right Flag String Path
    val rightStringEnd = Offset(x + w * 0.22f, y + 24.dp.toPx())
    val rightStringMid = Offset(x + w * 0.11f, y)

    val rightStringPath = Path().apply {
        moveTo(poleTop.x, poleTop.y)
        quadraticTo(rightStringMid.x, rightStringMid.y, rightStringEnd.x, rightStringEnd.y)
    }
    drawPath(
        path = rightStringPath,
        color = Color.White.copy(alpha = 0.9f),
        style = Stroke(width = 2.dp.toPx())
    )

    // Draw Right Flags
    val rightPm = PathMeasure()
    rightPm.setPath(rightStringPath, false)
    val rightLen = rightPm.length

    for (f in 0 until flagCount) {
        val dist = (f.toFloat() + 0.5f) / flagCount * rightLen
        val pos = rightPm.getPosition(dist)
        val flagColor = flagColors[f % flagColors.size]

        val flagPath = Path().apply {
            moveTo(pos.x, pos.y)
            lineTo(pos.x - 3.dp.toPx(), pos.y + 12.dp.toPx())
            lineTo(pos.x + 7.dp.toPx(), pos.y + 12.dp.toPx())
            lineTo(pos.x + 4.dp.toPx(), pos.y)
            close()
        }
        drawPath(flagPath, color = flagColor)
    }
}

/**
 * Draws a footprint using the user's custom foot icon image.
 * Flips horizontally for alternating left/right feet,
 * rotates with the path tangent, and adjusts size with perspective.
 */
private fun DrawScope.drawIconFootprint(
    image: ImageBitmap,
    center: Offset,
    angleRad: Float,
    isLeftFoot: Boolean,
    scale: Float,
    alpha: Float = 0.96f,
    isGoldenGlow: Boolean = false
) {
    val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
    val baseFootWidth = 28.dp.toPx()
    val baseFootHeight = 28.dp.toPx()
    val drawW = baseFootWidth * scale
    val drawH = baseFootHeight * scale

    rotate(degrees = angleDeg + 90f, pivot = center) {
        scale(
            scaleX = if (isLeftFoot) -1f else 1f,
            scaleY = 1f,
            pivot = center
        ) {
            if (isGoldenGlow) {
                // Golden victory aura for return journey when exceeding step goal
                drawCircle(
                    color = Color(0xFFFFD700).copy(alpha = 0.45f),
                    center = center,
                    radius = (baseFootWidth * 0.55f) * scale
                )
                drawCircle(
                    color = Color(0xFFFFF9C4).copy(alpha = 0.65f),
                    center = center,
                    radius = (baseFootWidth * 0.35f) * scale
                )
            } else {
                // Soft shadow on the dirt path under the foot
                drawCircle(
                    color = Color(0x3B4A2810),
                    center = Offset(center.x, center.y + 1.5f.dp.toPx() * scale),
                    radius = (baseFootWidth * 0.36f) * scale
                )
            }

            drawImage(
                image = image,
                dstOffset = IntOffset(
                    x = (center.x - drawW / 2).toInt(),
                    y = (center.y - drawH / 2).toInt()
                ),
                dstSize = IntSize(drawW.toInt(), drawH.toInt()),
                alpha = alpha
            )
        }
    }
}

private data class PathFootprintNode(
    val position: Offset,
    val angleRad: Float,
    val scale: Float,
    val isLeft: Boolean
)

