/**
 * Awen Project (C) 2026
 * Liquid Glass UI Integration & Hardware-Accelerated Glassmorphism Utility
 */

package com.music.vivi.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.music.vivi.models.MediaMetadata

/**
 * Applies a frosted glassmorphism container surface with a specular highlight rim.
 * Note: Container blur is deliberately NOT applied to the outer Modifier so child
 * composables (e.g. text, icons, buttons) remain 100% crisp and readable.
 */
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
        pureBlack && isDark -> Color.Black.copy(alpha = 0.72f)
        isDark -> Color(0xFF0F111E).copy(alpha = 0.68f)
        else -> Color.White.copy(alpha = 0.72f)
    }

    val specularBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.40f),
            Color.White.copy(alpha = 0.10f),
            Color.Transparent,
            Color.White.copy(alpha = 0.20f)
        )
    )

    return this
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)
        .background(surfaceColor, shape = shape)
        .border(width = 1.dp, brush = specularBrush, shape = shape)
}

/**
 * Dedicated frosted liquid glass background layer for the mini player.
 * Renders blurred album art underneath the player controls, preserving full
 * sharpness for the foreground text, album thumbnail, and playback actions.
 */
@Composable
fun LiquidGlassPlayerBackground(
    mediaMetadata: MediaMetadata?,
    gradientColors: List<Color>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnailUrl = mediaMetadata?.thumbnailUrl

    Box(modifier = modifier.fillMaxSize()) {
        if (!thumbnailUrl.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .size(128, 128)
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(30.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (pureBlack) Color.Black.copy(alpha = 0.65f)
                        else Color(0xFF0A0B14).copy(alpha = 0.55f)
                    )
            )
        } else if (gradientColors.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                gradientColors.first().copy(alpha = 0.40f),
                                if (gradientColors.size > 1) gradientColors[1].copy(alpha = 0.30f) else Color.Transparent
                            )
                        )
                    )
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1E1C38).copy(alpha = 0.70f),
                                Color(0xFF0F1122).copy(alpha = 0.80f)
                            )
                        )
                    )
            )
        }

        // Specular top highlight sheen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Ultra-premium dark theme design tokens inspired by Proton / Settings aesthetics.
 */
object ObsidianTheme {
    val Background = Color(0xFF07070A)
    val SurfaceDark = Color(0xFF101116)
    val SurfaceContainer = Color(0xFF16171E)
    val SurfaceContainerHigh = Color(0xFF1E202A)
    val CardBorder = Color(0x1AFFFFFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8F91A0)
    val TextMuted = Color(0xFF5E6070)
    val PillActive = Color(0xFFFFFFFF)
    val PillActiveText = Color(0xFF08090C)
    val PillInactive = Color(0xFF14151C)
    val PillInactiveText = Color(0xFF8F91A0)
}

/**
 * Reusable card modifier matching the sleek, dark, bevel-bordered container aesthetic.
 */
fun Modifier.obsidianCard(
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = Color(0xFF14151C),
    borderColor: Color = Color(0x1AFFFFFF),
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .background(containerColor, shape)
    .border(borderWidth, borderColor, shape)

