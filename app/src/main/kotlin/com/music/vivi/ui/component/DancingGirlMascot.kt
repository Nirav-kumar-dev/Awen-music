package com.music.vivi.ui.component

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-memory cache for the dancing mascot frames.
 * Keeps frames in RAM after first load for zero GC stutter and 60fps rendering.
 */
object MascotAssets {
    @Volatile
    var danceFrames: List<ImageBitmap>? = null
        private set

    @Volatile
    var upsetFrame: ImageBitmap? = null
        private set

    @Volatile
    private var isLoaded = false

    fun isReady(): Boolean = isLoaded && !danceFrames.isNullOrEmpty() && upsetFrame != null

    suspend fun load(context: Context) {
        if (isReady()) return
        withContext(Dispatchers.IO) {
            try {
                val frames = ArrayList<ImageBitmap>(60)
                for (i in 0 until 60) {
                    val path = String.format("mascot/dance_%02d.webp", i)
                    context.assets.open(path).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()?.let {
                            frames.add(it)
                        }
                    }
                }
                val upset = context.assets.open("mascot/upset.webp").use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }

                danceFrames = frames
                upsetFrame = upset
                isLoaded = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

/**
 * Dancing Girl Mascot for the Mini Player.
 * - Falls from top with bouncy spring when music starts playing.
 * - Dances (loops 60 frames @ 30fps) while music is playing.
 * - Paces horizontally back and forth across the mini player, facing the direction she walks.
 * - Becomes upset (pouts with cute anime tears) when music is paused.
 * - Tapping her causes a playful little jump.
 */
@Composable
fun DancingGirlMascot(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    walkDistanceDp: Float = 95f
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(MascotAssets.isReady()) }

    LaunchedEffect(Unit) {
        if (!MascotAssets.isReady()) {
            MascotAssets.load(context)
        }
        ready = MascotAssets.isReady()
    }

    if (!ready) return

    val coroutineScope = rememberCoroutineScope()

    // Falling entrance animation state (in dp)
    val fallAnim = remember { Animatable(if (isPlaying) 0f else -90f) }
    var hasEntered by remember { mutableStateOf(isPlaying) }

    // Tap jump animation
    val jumpAnim = remember { Animatable(0f) }

    // Dancing frame animation
    var currentFrameIndex by remember { mutableIntStateOf(0) }
    val totalFrames = MascotAssets.danceFrames?.size ?: 60

    // Horizontal walk/pacing animation (in dp)
    val walkAnim = remember { Animatable(0f) }
    var facingScale by remember { mutableFloatStateOf(1f) }

    // Entrance fall effect when playback begins
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            if (!hasEntered) {
                fallAnim.snapTo(-90f)
                fallAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                hasEntered = true
            }
        }
    }

    // Dance frame loop
    LaunchedEffect(isPlaying, hasEntered, totalFrames) {
        if (isPlaying && hasEntered && totalFrames > 0) {
            while (isActive) {
                delay(33L) // ~30 fps
                currentFrameIndex = (currentFrameIndex + 1) % totalFrames
            }
        }
    }

    // Horizontal pacing loop
    LaunchedEffect(isPlaying, hasEntered) {
        if (isPlaying && hasEntered) {
            while (isActive) {
                // Walk right
                facingScale = 1f
                walkAnim.animateTo(
                    targetValue = walkDistanceDp,
                    animationSpec = tween(
                        durationMillis = (walkDistanceDp * 35).toInt().coerceAtLeast(1500),
                        easing = LinearEasing
                    )
                )
                delay(200L) // Small pause at turning point

                // Walk left
                facingScale = -1f
                walkAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = (walkDistanceDp * 35).toInt().coerceAtLeast(1500),
                        easing = LinearEasing
                    )
                )
                delay(200L)
            }
        } else {
            // Face forward when upset / paused
            facingScale = 1f
        }
    }

    // Determine current frame bitmap
    val bitmapToDraw: ImageBitmap? = if (isPlaying && hasEntered) {
        MascotAssets.danceFrames?.getOrNull(currentFrameIndex) ?: MascotAssets.upsetFrame
    } else {
        MascotAssets.upsetFrame
    }

    bitmapToDraw?.let { imageBitmap ->
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Music Mascot",
                modifier = Modifier
                    .size(width = 28.dp, height = 52.dp)
                    .graphicsLayer {
                        translationY = (fallAnim.value + jumpAnim.value).dp.toPx()
                        translationX = walkAnim.value.dp.toPx()
                        scaleX = facingScale
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Playful jump on tap
                        coroutineScope.launch {
                            jumpAnim.animateTo(
                                targetValue = -18f,
                                animationSpec = tween(120, easing = FastOutLinearInEasing)
                            )
                            jumpAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                    }
            )
        }
    }
}
