package com.music.vivi.ui.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.music.innertube.models.WatchEndpoint
import com.music.vivi.LocalDatabase
import com.music.vivi.LocalPlayerConnection
import com.music.vivi.R
import com.music.vivi.ai.VisionPlaylist
import com.music.vivi.ai.VisionPlaylistService
import com.music.vivi.db.entities.PlaylistEntity
import com.music.vivi.db.entities.PlaylistSongMap
import com.music.vivi.db.entities.SongEntity
import com.music.vivi.models.toMediaMetadata
import com.music.vivi.playback.queues.YouTubeQueue
import com.music.vivi.ui.screens.PremiumTheme
import com.music.vivi.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

sealed interface VisionSheetState {
    data object Idle : VisionSheetState
    data class Analyzing(val thumbnail: Bitmap?) : VisionSheetState
    data class Success(val playlist: VisionPlaylist) : VisionSheetState
    data class Error(val message: String) : VisionSheetState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionPlaylistBottomSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()

    var currentState by remember { mutableStateOf<VisionSheetState>(VisionSheetState.Idle) }
    var isSavedToLibrary by remember { mutableStateOf(false) }

    // Temp file for camera capture
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val analyzeImageBitmap: (Bitmap) -> Unit = { bitmap ->
        currentState = VisionSheetState.Analyzing(bitmap)
        isSavedToLibrary = false
        scope.launch {
            val result = VisionPlaylistService.analyzeImageAndGeneratePlaylist(bitmap)
            result.onSuccess { playlist ->
                currentState = VisionSheetState.Success(playlist)
            }.onFailure { err ->
                currentState = VisionSheetState.Error(err.message ?: "Failed to analyze image")
            }
        }
    }

    val analyzeImageUri: (Uri) -> Unit = { uri ->
        val bmp = VisionPlaylistService.loadScaledBitmap(context, uri)
        if (bmp != null) {
            analyzeImageBitmap(bmp)
        } else {
            Toast.makeText(context, "Could not load image", Toast.LENGTH_SHORT).show()
        }
    }

    // Media Picker launcher (Gallery)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            analyzeImageUri(uri)
        }
    }

    // Camera Capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            analyzeImageUri(tempCameraUri!!)
        }
    }

    // Camera Permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val tempFile = File.createTempFile("camera_vibe_", ".jpg", context.cacheDir).apply {
                    createNewFile()
                    deleteOnExit()
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission is required to snap photos", Toast.LENGTH_SHORT).show()
        }
    }

    val openCamera: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                val tempFile = File.createTempFile("camera_vibe_", ".jpg", context.cacheDir).apply {
                    createNewFile()
                    deleteOnExit()
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val openGallery: () -> Unit = {
        mediaPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PremiumTheme.DeepBackground,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PremiumTheme.DeepBackground)
        ) {
            // Studio spotlight diffused glow matching HomeScreen
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.75f
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                // Top Centered Drag Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Unified App Header (matches PremiumAppHeader)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(PremiumTheme.SurfaceCard)
                                .border(1.dp, PremiumTheme.CardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.camera),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Visual Soundtrack",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (-0.4).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "AI SCENE RECOGNITION",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PremiumTheme.TextMuted,
                                letterSpacing = 1.3.sp
                            )
                        }
                    }

                    // Circular Close Button (matches Settings/History buttons on Home)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PremiumTheme.SurfaceCard)
                            .border(1.dp, PremiumTheme.CardBorder, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                when (val state = currentState) {
                    is VisionSheetState.Idle -> {
                        // Section description
                        Text(
                            text = "Snap a moment or select a photo. DiffusionGemma AI analyzes the colors, lighting, and aesthetic to curate a seamless playlist for the vibe.",
                            color = PremiumTheme.TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(bottom = 22.dp)
                        )

                        // 2 Cards: Take Photo & Pick Image (matches PremiumFeatureCard & Obsidian aesthetics)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Take Photo Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(PremiumTheme.SurfaceCard)
                                    .border(1.dp, PremiumTheme.SpecularBorder, RoundedCornerShape(24.dp))
                                    .clickable { openCamera() }
                                    .padding(vertical = 26.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.camera),
                                            contentDescription = "Take Photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Take Photo",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Snap your moment",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 12.sp,
                                        color = PremiumTheme.TextMuted
                                    )
                                }
                            }

                            // Choose Photo Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(PremiumTheme.SurfaceCard)
                                    .border(1.dp, PremiumTheme.SpecularBorder, RoundedCornerShape(24.dp))
                                    .clickable { openGallery() }
                                    .padding(vertical = 26.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.image),
                                            contentDescription = "Pick Image",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Choose Image",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "From your gallery",
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 12.sp,
                                        color = PremiumTheme.TextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    is VisionSheetState.Analyzing -> {
                        // Analyzing / Shimmer loading state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            state.thumbnail?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .border(1.dp, PremiumTheme.SpecularBorder, RoundedCornerShape(22.dp))
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Scene Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f))
                                                )
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(34.dp)
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "Analyzing Visual Scene...",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "DiffusionGemma 26B reading colors, mood & atmosphere",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                color = PremiumTheme.TextSecondary
                            )
                        }
                    }

                    is VisionSheetState.Success -> {
                        val playlist = state.playlist

                        // Hero Header Card (matches PremiumFeatureCard)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(PremiumTheme.SurfaceCard)
                                .border(1.dp, PremiumTheme.SpecularBorder, RoundedCornerShape(24.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                playlist.imageBitmap?.let { bmp ->
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(18.dp))
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    // Badge matching "FEATURED SELECTION"
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "VISUAL VIBE ENGINE",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = playlist.title,
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = playlist.vibe,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 12.sp,
                                        color = PremiumTheme.TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Section Title: CURATED SOUNDTRACK
                        Text(
                            text = "CURATED SOUNDTRACK",
                            color = PremiumTheme.TextMuted,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.3.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                        )

                        // Song list with items matching Obsidian Theme
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(playlist.songs) { index, song ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(PremiumTheme.SurfaceCard)
                                        .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(16.dp))
                                        .clickable {
                                            song.songItem?.let { songItem ->
                                                playerConnection?.playQueue(
                                                    YouTubeQueue(
                                                        songItem.endpoint ?: WatchEndpoint(videoId = songItem.id),
                                                        songItem.toMediaMetadata()
                                                    )
                                                )
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Number index
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PremiumTheme.TextMuted,
                                        modifier = Modifier.width(26.dp)
                                    )

                                    // Thumbnail if resolved from YouTube
                                    val thumb = song.songItem?.thumbnail
                                    if (!thumb.isNullOrBlank()) {
                                        AsyncImage(
                                            model = thumb.resize(100, 100),
                                            contentDescription = song.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(10.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = song.artist,
                                            fontFamily = FontFamily.SansSerif,
                                            fontSize = 12.sp,
                                            color = PremiumTheme.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Play icon capsule
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.play),
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Actions Row (Play All & Save to Library & Snap Another)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary Play All Button (Matching Hero play button: White pill)
                            Button(
                                onClick = {
                                    val resolvedSongs = playlist.songs.mapNotNull { it.songItem }
                                    if (resolvedSongs.isNotEmpty()) {
                                        val first = resolvedSongs.first()
                                        playerConnection?.playQueue(
                                            YouTubeQueue(
                                                first.endpoint ?: WatchEndpoint(videoId = first.id),
                                                first.toMediaMetadata()
                                            )
                                        )
                                        Toast.makeText(context, "Playing ${playlist.title}", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Resolving songs...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF07070A)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    tint = Color(0xFF07070A),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Play Playlist",
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            // Secondary Save to Library Button (Matching obsidianCard pill)
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PremiumTheme.SurfaceCard)
                                    .border(
                                        1.dp,
                                        if (isSavedToLibrary) Color.White else PremiumTheme.CardBorder,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        if (!isSavedToLibrary) {
                                            scope.launch(Dispatchers.IO) {
                                                val playlistEntity = PlaylistEntity(
                                                    name = playlist.title,
                                                    bookmarkedAt = LocalDateTime.now(),
                                                    isEditable = true
                                                )
                                                database.query {
                                                    insert(playlistEntity)
                                                    playlist.songs.forEachIndexed { idx, song ->
                                                        val resolved = song.songItem
                                                        val songId = resolved?.id ?: "ai_${System.currentTimeMillis()}_$idx"
                                                        insert(
                                                            SongEntity(
                                                                id = songId,
                                                                title = song.title,
                                                                duration = resolved?.duration ?: 0,
                                                                thumbnailUrl = resolved?.thumbnail
                                                            )
                                                        )
                                                        insert(
                                                            PlaylistSongMap(
                                                                playlistId = playlistEntity.id,
                                                                songId = songId,
                                                                position = idx
                                                            )
                                                        )
                                                    }
                                                }
                                                withContext(Dispatchers.Main) {
                                                    isSavedToLibrary = true
                                                    Toast.makeText(context, "Saved to library!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(if (isSavedToLibrary) R.drawable.check else R.drawable.playlist_add),
                                        contentDescription = null,
                                        tint = if (isSavedToLibrary) Color(0xFF34D399) else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSavedToLibrary) "Saved" else "Save",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSavedToLibrary) Color(0xFF34D399) else Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Retake / Another Photo Button
                            IconButton(
                                onClick = { currentState = VisionSheetState.Idle },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PremiumTheme.SurfaceCard)
                                    .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(16.dp))
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.camera),
                                    contentDescription = "Another photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    is VisionSheetState.Error -> {
                        // Error Card matching Obsidian Theme
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(PremiumTheme.SurfaceCard)
                                .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(24.dp))
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Analysis Failed",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 13.sp,
                                    color = PremiumTheme.TextSecondary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { currentState = VisionSheetState.Idle },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFF07070A)
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = "Try Again",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
