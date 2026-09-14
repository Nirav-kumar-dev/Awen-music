package com.music.vivi.ui.component

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.innertube.models.WatchEndpoint
import com.music.vivi.LocalDatabase
import com.music.vivi.LocalPlayerConnection
import com.music.vivi.R
import com.music.vivi.ai.NemotronAIResult
import com.music.vivi.ai.NemotronAIService
import com.music.vivi.ai.NemotronSong
import com.music.vivi.ai.NemotronUserContext
import com.music.vivi.ai.storage.AiAgentStorage
import com.music.vivi.db.entities.PlaylistEntity
import com.music.vivi.db.entities.PlaylistSongMap
import com.music.vivi.db.entities.SongEntity
import com.music.vivi.models.toMediaMetadata
import com.music.vivi.playback.queues.AiPlaylistQueue
import com.music.vivi.playback.queues.YouTubeQueue
import com.music.vivi.ui.screens.PremiumTheme
import com.music.vivi.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

sealed interface NemotronSheetState {
    data object Idle : NemotronSheetState
    data class Thinking(val query: String) : NemotronSheetState
    data class Result(val data: NemotronAIResult) : NemotronSheetState
    data class Error(val message: String) : NemotronSheetState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NemotronAIBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current

    var uiState by remember { mutableStateOf<NemotronSheetState>(NemotronSheetState.Idle) }
    var inputText by remember { mutableStateOf("") }
    var isSavedToLibrary by remember { mutableStateOf(false) }
    var userContext by remember { mutableStateOf<NemotronUserContext?>(null) }

    LaunchedEffect(Unit) {
        userContext = NemotronUserContext.load(context, database)
        AiAgentStorage.syncUserProfile(context, database)
    }

    val quickMoods = remember {
        listOf(
            "Fresh 2026 & 2025 Bangers",
            "Hits & New Releases Mix",
            "Late Night Drive",
            "Deep Focus & Code",
            "Workout Beast Mode",
            "Cyberpunk Synthwave",
            "Chill Acoustic Morning",
            "Energetic Pop Hits"
        )
    }

    fun submitQuery(prompt: String) {
        if (prompt.isBlank()) return
        keyboardController?.hide()
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        uiState = NemotronSheetState.Thinking(prompt)
        isSavedToLibrary = false

        scope.launch {
            val ctx = userContext ?: NemotronUserContext.load(context, database)
            val result = NemotronAIService.queryMusicAI(prompt, ctx, context)
            result.onSuccess { aiResult ->
                uiState = NemotronSheetState.Result(aiResult)
            }.onFailure { err ->
                uiState = NemotronSheetState.Error(err.localizedMessage ?: "AI generation failed")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PremiumTheme.DeepBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(PremiumTheme.DeepBackground)
        ) {
            // Radial spotlight glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.85f
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                // 1. Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AI",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF08090C),
                                fontFamily = FontFamily.SansSerif
                            )
                        }

                        Column {
                            Text(
                                text = "TideFlow AI Assistant",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            val userLabel = userContext?.name?.takeIf { it.isNotBlank() }
                                ?: userContext?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                            Text(
                                text = if (userLabel != null) "Nemotron 3.5 • Tuned for $userLabel" else "Nemotron 3.5 Lightning 30B",
                                fontSize = 11.sp,
                                color = Color(0xFF4ADE80),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PremiumTheme.SurfaceCard)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Interactive Text Input Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(PremiumTheme.SurfaceCard)
                        .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = "Ask for mood, vibe, genre, or playlist...",
                                    fontSize = 14.sp,
                                    color = PremiumTheme.TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submitQuery(inputText) }),
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (inputText.isNotBlank()) Color.White else Color.White.copy(alpha = 0.1f))
                                .clickable(enabled = inputText.isNotBlank()) {
                                    submitQuery(inputText)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_forward),
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Color(0xFF08090C) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Quick Mood Pills
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickMoods.forEach { mood ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF161720))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                                .clickable {
                                    inputText = mood
                                    submitQuery(mood)
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = mood,
                                color = PremiumTheme.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Content Area based on State
                when (val state = uiState) {
                    is NemotronSheetState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(PremiumTheme.SurfaceCard)
                                        .border(1.dp, PremiumTheme.CardBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "AI",
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Tell AI What You Want to Hear",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Search by feeling, activity, or sound aesthetic.\nNemotron 3.5 curates and builds instant playlists.",
                                    fontSize = 13.sp,
                                    color = PremiumTheme.TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    is NemotronSheetState.Thinking -> {
                        val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "orb_rot"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .rotate(rotation)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.sweepGradient(
                                                listOf(
                                                    Color(0xFF4ADE80),
                                                    Color(0xFF38BDF8),
                                                    Color(0xFFA855F7),
                                                    Color(0xFF4ADE80)
                                                )
                                            )
                                        )
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(PremiumTheme.DeepBackground),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "AI",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "Consulting Nemotron 3.5...",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "\"${state.query}\"",
                                    fontSize = 13.sp,
                                    color = PremiumTheme.TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    is NemotronSheetState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Generation Error",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    fontSize = 13.sp,
                                    color = PremiumTheme.TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { uiState = NemotronSheetState.Idle },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }

                    is NemotronSheetState.Result -> {
                        val result = state.data

                        val playTrackAt: (NemotronSong, Int) -> Unit = { targetSong, indexHint ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                var resolvedItem = targetSong.songItem
                                if (resolvedItem == null) {
                                    resolvedItem = NemotronAIService.resolveSingleTrack(targetSong)
                                }
                                val playable = result.songs.mapNotNull { it.songItem }
                                if (playable.isNotEmpty()) {
                                    val startIdx = if (resolvedItem != null) {
                                        playable.indexOfFirst { it.id == resolvedItem.id }.takeIf { it >= 0 }
                                            ?: indexHint.coerceIn(0, playable.size - 1)
                                    } else {
                                        indexHint.coerceIn(0, playable.size - 1)
                                    }
                                    AiAgentStorage.updateSessionFeedback(context, result.playlistTitle, wasPlayed = true)
                                    playerConnection?.playQueue(
                                        AiPlaylistQueue(
                                            title = result.playlistTitle,
                                            initialSongs = playable,
                                            startIndex = startIdx
                                        )
                                    )
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "No playable tracks found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // AI Message Bubble
                            item(key = "ai_msg") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(PremiumTheme.SurfaceCard)
                                        .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(20.dp))
                                        .padding(16.dp)
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF4ADE80)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "✓",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                            }
                                            Text(
                                                text = "AI RESPONSE",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PremiumTheme.TextMuted,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = result.message,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                            }

                            // Playlist Overview Card
                            item(key = "ai_playlist_overview") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF171822),
                                                    Color(0xFF0D0E14)
                                                )
                                            )
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                                        .padding(18.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = result.playlistTitle,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${result.songs.size} tracks • ${result.vibe}",
                                            fontSize = 13.sp,
                                            color = PremiumTheme.TextSecondary
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Action Buttons: Play All + Save to Library
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Play All Button
                                            Button(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val playable = result.songs.mapNotNull { it.songItem }
                                                    if (playable.isNotEmpty()) {
                                                        scope.launch {
                                                            AiAgentStorage.updateSessionFeedback(context, result.playlistTitle, wasPlayed = true)
                                                        }
                                                        playerConnection?.playQueue(
                                                            AiPlaylistQueue(
                                                                title = result.playlistTitle,
                                                                initialSongs = playable,
                                                                startIndex = 0
                                                            )
                                                        )
                                                        onDismiss()
                                                    } else {
                                                        Toast.makeText(context, "No playable tracks found", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = Color(0xFF08090C)
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(46.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.play),
                                                    contentDescription = "Play",
                                                    tint = Color(0xFF08090C),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Play All",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }

                                            // Save to Library Button
                                            Button(
                                                onClick = {
                                                    if (!isSavedToLibrary) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        scope.launch(Dispatchers.IO) {
                                                            AiAgentStorage.updateSessionFeedback(context, result.playlistTitle, wasSaved = true)
                                                            val playlistEntity = PlaylistEntity(
                                                                name = result.playlistTitle,
                                                                bookmarkedAt = LocalDateTime.now(),
                                                                isEditable = true
                                                            )
                                                            database.query {
                                                                insert(playlistEntity)
                                                                result.songs.forEachIndexed { idx, s ->
                                                                    val resolved = s.songItem
                                                                    val songId = resolved?.id ?: "nemotron_${System.currentTimeMillis()}_$idx"
                                                                    insert(
                                                                        SongEntity(
                                                                            id = songId,
                                                                            title = s.title,
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
                                                                Toast.makeText(context, "Saved to your library!", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSavedToLibrary) Color(0xFF1B2A1E) else PremiumTheme.SurfaceCard,
                                                    contentColor = if (isSavedToLibrary) Color(0xFF4ADE80) else Color.White
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isSavedToLibrary) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.15f)
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(46.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(if (isSavedToLibrary) R.drawable.check else R.drawable.library_add),
                                                    contentDescription = "Save",
                                                    tint = if (isSavedToLibrary) Color(0xFF4ADE80) else Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isSavedToLibrary) "Saved" else "Save Playlist",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Song List Items
                            itemsIndexed(result.songs) { index, song ->
                                val songItem = song.songItem

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(PremiumTheme.SurfaceCard)
                                        .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(16.dp))
                                        .clickable {
                                            playTrackAt(song, index)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Track Number
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PremiumTheme.TextMuted,
                                            modifier = Modifier.width(22.dp)
                                        )

                                        // Artwork
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF222430)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (songItem?.thumbnail != null) {
                                                AsyncImage(
                                                    model = songItem.thumbnail.resize(100, 100),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(R.drawable.music_note),
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Track Title & Artist
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = song.artist.ifEmpty { "Curated Track" },
                                                fontSize = 12.sp,
                                                color = PremiumTheme.TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Play Icon
                                        IconButton(
                                            onClick = {
                                                playTrackAt(song, index)
                                            },
                                            modifier = Modifier.size(32.dp)
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

                            item(key = "bottom_pad") {
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
