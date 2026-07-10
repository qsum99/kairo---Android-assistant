package com.kairo.assistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoGradientEnd
import com.kairo.assistant.ui.theme.KairoGradientStart
import com.kairo.assistant.ui.theme.KairoPrimary
import com.kairo.assistant.ui.theme.KairoSurfaceVariant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    isActive: Boolean,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 40
    val halfCount = barCount / 2
    val bars = remember {
        List(halfCount) { Animatable(0.1f) }
    }

    LaunchedEffect(isActive, isProcessing) {
        if (isActive) {
            while (true) {
                bars.forEachIndexed { index, bar ->
                    launch {
                        val centerBias = (index.toFloat() / halfCount).coerceIn(0.15f, 1.0f)
                        val targetHeight = (Random.nextFloat() * 0.75f + 0.15f) * centerBias
                        bar.animateTo(
                            targetValue = targetHeight,
                            animationSpec = tween(
                                durationMillis = Random.nextInt(120, 280),
                            )
                        )
                    }
                }
                delay(90)
            }
        } else if (isProcessing) {
            var step = 0
            while (true) {
                bars.forEachIndexed { index, bar ->
                    launch {
                        val centerBias = (index.toFloat() / halfCount).coerceIn(0.15f, 1.0f)
                        val angle = (index * 0.35f) + (step * 0.2f)
                        val targetHeight = (Math.sin(angle.toDouble()).toFloat() * 0.25f + 0.35f) * centerBias
                        bar.animateTo(
                            targetValue = targetHeight,
                            animationSpec = tween(
                                durationMillis = 150,
                             )
                        )
                    }
                }
                step++
                delay(120)
            }
        } else {
            bars.forEach { bar ->
                launch {
                    bar.animateTo(
                        targetValue = 0.05f,
                        animationSpec = tween(durationMillis = 400)
                    )
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val totalWidth = size.width
        val barWidth = totalWidth / (barCount * 1.8f)
        val spacing = barWidth * 0.8f
        val maxHeight = size.height

        val heights = FloatArray(barCount)
        for (i in 0 until halfCount) {
            heights[i] = bars[i].value
            heights[barCount - 1 - i] = bars[i].value
        }

        for (index in 0 until barCount) {
            val rawHeight = heights[index]
            val barHeight = maxHeight * rawHeight
            val x = index * (barWidth + spacing) + (totalWidth - (barCount * (barWidth + spacing) - spacing)) / 2f

            val barBrush = Brush.verticalGradient(
                colors = listOf(
                    KairoGradientEnd.copy(alpha = 0.95f),
                    KairoGradientStart.copy(alpha = 0.75f)
                )
            )

            drawRoundRect(
                brush = if (isActive) barBrush else Brush.verticalGradient(
                    colors = listOf(
                        KairoSurfaceVariant.copy(alpha = 0.5f),
                        KairoSurfaceVariant.copy(alpha = 0.3f)
                    )
                ),
                topLeft = Offset(x, (maxHeight - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
