/**
 * TideFlow Project (C) 2026
 * Liquid Glass UI Integration & Hardware-Accelerated Glassmorphism Utility
 */

package com.music.vivi.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.liquidGlassEffect(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    blurRadius: Dp = 16.dp,
    elevation: Dp = 4.dp,
    pureBlack: Boolean = false
): Modifier {
    if (!enabled) return this

    val isDark = isSystemInDarkTheme()
    val surfaceColor = when {
        pureBlack && isDark -> Color.Black.copy(alpha = 0.35f)
        isDark -> Color.Black.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.15f)
    }

    val specularBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.45f),
            Color.White.copy(alpha = 0.10f),
            Color.Transparent,
            Color.White.copy(alpha = 0.25f)
        )
    )

    val density = LocalDensity.current
    val blurPx = with(density) { blurRadius.toPx() }

    return this
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)
        .then(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0f) {
                Modifier.graphicsLayer {
                    renderEffect = android.graphics.RenderEffect
                        .createBlurEffect(25f, 25f, android.graphics.Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                    clip = false
                    this.shape = shape
                }
            } else {
                Modifier.blur(blurRadius)
            }
        )
        .background(surfaceColor, shape = shape)
        .border(width = 1.dp, brush = specularBrush, shape = shape)
}
