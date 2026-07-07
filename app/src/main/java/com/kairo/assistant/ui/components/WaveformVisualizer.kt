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
    modifier: Modifier = Modifier
) {
    val barCount = 40
    val bars = remember {
        List(barCount) { Animatable(0.1f) }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            while (true) {
                bars.forEachIndexed { index, bar ->
                    launch {
                        val targetHeight = Random.nextFloat() * 0.7f + 0.15f
                        bar.animateTo(
                            targetValue = targetHeight,
                            animationSpec = tween(
                                durationMillis = Random.nextInt(100, 300),
                            )
                        )
                    }
                }
                delay(100)
            }
        } else {
            bars.forEach { bar ->
                launch {
                    bar.animateTo(
                        targetValue = 0.1f,
                        animationSpec = tween(durationMillis = 400)
                    )
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val totalWidth = size.width
        val barWidth = totalWidth / (barCount * 2f)
        val spacing = barWidth
        val maxHeight = size.height

        bars.forEachIndexed { index, bar ->
            val barHeight = maxHeight * bar.value
            val x = index * (barWidth + spacing) + spacing / 2f

            val progress = index.toFloat() / barCount
            val barBrush = Brush.verticalGradient(
                colors = listOf(
                    KairoGradientEnd.copy(alpha = 0.9f),
                    KairoGradientStart.copy(alpha = 0.7f)
                )
            )

            // Draw bar centered vertically
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
