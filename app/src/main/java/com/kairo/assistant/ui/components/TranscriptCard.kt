package com.kairo.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kairo.assistant.ui.theme.KairoAccent
import com.kairo.assistant.ui.theme.KairoOnSurface
import com.kairo.assistant.ui.theme.KairoOnSurfaceVariant
import com.kairo.assistant.ui.theme.KairoPrimary
import com.kairo.assistant.ui.theme.KairoSurface
import com.kairo.assistant.ui.theme.KairoSurfaceVariant

@Composable
fun TranscriptCard(
    userText: String,
    responseText: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(KairoSurface.copy(alpha = 0.7f))
                .border(
                    width = 1.dp,
                    color = KairoSurfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            if (userText.isNotEmpty()) {
                Text(
                    text = "You said:",
                    style = MaterialTheme.typography.labelMedium,
                    color = KairoPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KairoOnSurface
                )
            }

            if (userText.isNotEmpty() && responseText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (responseText.isNotEmpty()) {
                Text(
                    text = "Kairo:",
                    style = MaterialTheme.typography.labelMedium,
                    color = KairoAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = responseText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KairoOnSurface.copy(alpha = 0.9f)
                )
            }
        }
    }
}
