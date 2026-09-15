/**
 * TideFlow Project (C) 2026
 * Licensed under GPL-3.0 | Ultra-Premium Dark Theme (Proton-Inspired & Settings-Unified)
 */

package com.music.vivi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.innertube.YouTube
import com.music.innertube.models.Artist as YtArtist
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.utils.completed
import com.music.vivi.LocalDatabase
import com.music.vivi.LocalPlayerAwareWindowInsets
import com.music.vivi.LocalPlayerConnection
import com.music.vivi.R
import com.music.vivi.constants.PlaylistSortType
import com.music.vivi.db.entities.Album
import com.music.vivi.db.entities.Artist as DbArtist
import com.music.vivi.db.entities.LocalItem
import com.music.vivi.db.entities.Playlist
import com.music.vivi.db.entities.PlaylistEntity
import com.music.vivi.db.entities.Song
import com.music.vivi.extensions.toMediaItem
import com.music.vivi.models.toMediaMetadata
import com.music.vivi.playback.queues.ListQueue
import com.music.vivi.playback.queues.LocalAlbumRadio
import com.music.vivi.playback.queues.YouTubeAlbumRadio
import com.music.vivi.playback.queues.YouTubeQueue
import com.music.vivi.ui.component.HideOnScrollFAB
import com.music.vivi.ui.component.SpeedDialGridItem
import com.music.vivi.ui.component.VisionPlaylistBottomSheet
import com.music.vivi.ui.component.NemotronAIBottomSheet
import com.music.vivi.ui.component.LocalMenuState
import com.music.vivi.ui.menu.AlbumMenu
import com.music.vivi.ui.menu.ArtistMenu
import com.music.vivi.ui.menu.SongMenu
import com.music.vivi.ui.menu.YouTubeAlbumMenu
import com.music.vivi.ui.menu.YouTubeArtistMenu
import com.music.vivi.ui.menu.YouTubePlaylistMenu
import com.music.vivi.ui.menu.YouTubeSongMenu
import com.music.vivi.ui.theme.ObsidianTheme
import com.music.vivi.ui.utils.resize
import com.music.vivi.viewmodels.CommunityPlaylistItem
import com.music.vivi.viewmodels.DailyDiscoverItem
import com.music.vivi.viewmodels.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ============================================================================
// ULTRA-PREMIUM THEME TOKENS (UNIFIED WITH SETTINGS & PROTON AESTHETIC)
// ============================================================================
object PremiumTheme {
    val DeepBackground = Color(0xFF07070A)
    val SurfaceDark = Color(0xFF101116)
    val SurfaceCard = Color(0xFF14151C)
    val SurfaceCardElevated = Color(0xFF1C1D26)
    val CardBorder = Color(0x1AFFFFFF)
    val SpecularBorder = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.20f),
            Color.White.copy(alpha = 0.05f),
            Color.Transparent,
            Color.White.copy(alpha = 0.10f)
        )
    )

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8F91A0)
    val TextMuted = Color(0xFF5E6070)

    val AccentWhite = Color(0xFFFFFFFF)
    val AccentDark = Color(0xFF08090C)
}

// ============================================================================
// MAIN HOME CONTENT COMPONENT
// ============================================================================
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NocturnalHomeContent(
    viewModel: HomeViewModel,
    navController: NavController,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val isPlaying = playerConnection?.isEffectivelyPlaying?.collectAsState()?.value ?: false
    val currentMetadata = playerConnection?.mediaMetadata?.collectAsState()?.value

    // Data from local database storage and HomeViewModel
    val historyEvents by database.events().collectAsState(initial = emptyList())
    val localPlaylists by database.playlists(PlaylistSortType.CREATE_DATE, true).collectAsState(initial = emptyList())
    val quickPicks by viewModel.quickPicks.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val speedDialItems by viewModel.speedDialItems.collectAsState()
    val pinnedSpeedDialItems by viewModel.pinnedSpeedDialItems.collectAsState()

    // Real recently played songs extracted directly from DB playback events
    val recentHistorySongs = remember(historyEvents) {
        historyEvents.map { it.song }.distinctBy { it.id }
    }
    val recentlyPlayedToDisplay = remember(recentHistorySongs, quickPicks) {
        if (recentHistorySongs.isNotEmpty()) recentHistorySongs
        else quickPicks.orEmpty()
    }

    val isCategoryLoading by viewModel.isCategoryLoading.collectAsState()
    var selectedFilterPill by rememberSaveable { mutableStateOf("All") }
    val filterPills = remember {
        listOf("All", "Ambient", "Focus", "Chill", "Electronic", "Romance", "Energetic", "Workout", "Party")
    }

    BackHandler(enabled = selectedFilterPill != "All") {
        selectedFilterPill = "All"
        viewModel.selectCategory("All")
    }

    LaunchedEffect(selectedFilterPill) {
        listState.animateScrollToItem(0)
    }

    var showVisionSheet by remember { mutableStateOf(false) }
    var showNemotronSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PremiumTheme.DeepBackground)
    ) {
        // Refined Studio Spotlight Gradient (Subtle diffused glow from top center)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, 0f),
                    radius = size.width * 0.9f
                )
            )
        }

        // Main Scrollable Content
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 80.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. App Header (TideFlow brand + History + Settings + Avatar)
            item(key = "premium_header") {
                PremiumAppHeader(
                    accountImageUrl = accountImageUrl,
                    onHistoryClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate("history")
                    },
                    onSettingsClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate("settings")
                    },
                    onAvatarClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate("settings")
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            // 2. Filter Pills (All, Ambient, Focus, Chill, etc.)
            item(key = "premium_filter_pills") {
                PremiumPillFilterRow(
                    pills = filterPills,
                    selectedPill = selectedFilterPill,
                    onSelect = { pill ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (selectedFilterPill.equals(pill, ignoreCase = true) && !pill.equals("All", ignoreCase = true)) {
                            selectedFilterPill = "All"
                            viewModel.selectCategory("All")
                        } else {
                            selectedFilterPill = pill
                            viewModel.selectCategory(pill)
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
            }

            if (selectedFilterPill != "All") {
                // Category Banner Card
                item(key = "category_header_$selectedFilterPill") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF13141C),
                                        Color(0xFF090A0E)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Column {
                            Text(
                                text = "CURATED CATEGORY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PremiumTheme.TextMuted,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$selectedFilterPill Soundtrack",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Handcrafted playlists, albums & tracks tailored for $selectedFilterPill",
                                fontSize = 13.sp,
                                color = PremiumTheme.TextSecondary
                            )
                        }
                    }
                }

                if (isCategoryLoading) {
                    item(key = "category_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {
                    val sections = homePage?.sections.orEmpty()
                    if (sections.isEmpty()) {
                        item(key = "category_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 50.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No content found for $selectedFilterPill",
                                        color = PremiumTheme.TextSecondary,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            selectedFilterPill = "All"
                                            viewModel.selectCategory("All")
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("Return to All")
                                    }
                                }
                            }
                        }
                    } else {
                        sections.forEachIndexed { index, section ->
                            item(key = "category_section_${selectedFilterPill}_${section.title}_$index") {
                                PremiumSectionHeader(
                                    title = section.title,
                                    subtitle = selectedFilterPill.uppercase(),
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp)
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(section.items, key = { it.id }) { item ->
                                        PremiumYTItemCard(
                                            item = item,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                when (item) {
                                                    is SongItem -> playerConnection?.playQueue(
                                                        YouTubeQueue(
                                                            item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                            item.toMediaMetadata()
                                                        )
                                                    )
                                                    is AlbumItem -> navController.navigate("album/${item.id}")
                                                    is ArtistItem -> navController.navigate("artist/${item.id}")
                                                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
            // 2.5 Speed Dial (YouTube Music 3x3 Dynamic Grid with Paging & Pinning)
            if (speedDialItems.isNotEmpty()) {
                item(key = "premium_speed_dial") {
                    PremiumSpeedDialSection(
                        speedDialItems = speedDialItems,
                        pinnedSpeedDialItems = pinnedSpeedDialItems,
                        currentMetadataId = currentMetadata?.id,
                        isPlaying = isPlaying,
                        onItemClick = { item ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            when (item) {
                                is SongItem -> {
                                    if (item.id == currentMetadata?.id) {
                                        playerConnection?.togglePlayPause()
                                    } else {
                                        playerConnection?.playQueue(
                                            YouTubeQueue(
                                                item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                item.toMediaMetadata()
                                            )
                                        )
                                    }
                                }
                                is AlbumItem -> navController.navigate("album/${item.id}")
                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                            }
                        },
                        onItemLongClick = { item ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            when (item) {
                                is SongItem -> menuState.show {
                                    YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                                }
                                is AlbumItem -> menuState.show {
                                    YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                                }
                                is ArtistItem -> menuState.show {
                                    YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                                }
                                is PlaylistItem -> menuState.show {
                                    YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
                                }
                            }
                        }
                    )
                }
            }

            // 3. Featured Hero Card ("Tonight's Feature" / "Daily Discover")
            item(key = "premium_tonight_feature") {
                val featuredSong = dailyDiscover?.firstOrNull()?.recommendation as? SongItem
                    ?: quickPicks?.firstOrNull()?.let {
                        SongItem(
                            id = it.id,
                            title = it.title,
                            artists = listOf(YtArtist(it.artists.firstOrNull()?.name ?: "Featured Artist", null)),
                            album = null,
                            duration = null,
                            thumbnail = it.thumbnailUrl.orEmpty(),
                            explicit = false
                        )
                    }

                val featureTitle = featuredSong?.title ?: "Featured Selection"
                val featureSubtitle = (featuredSong?.artists?.joinToString(", ") { it.name })?.let { "Featuring $it" }
                    ?: "Handcrafted selection tailored to your taste"

                PremiumFeatureCard(
                    title = featureTitle,
                    subtitle = featureSubtitle,
                    artworkUrl = featuredSong?.thumbnail,
                    onPlayClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (featuredSong != null) {
                            playerConnection?.playQueue(
                                YouTubeQueue(
                                    featuredSong.endpoint ?: WatchEndpoint(videoId = featuredSong.id),
                                    featuredSong.toMediaMetadata()
                                )
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            // 4. Recently Played Carousel (From DB Storage)
            if (recentlyPlayedToDisplay.isNotEmpty()) {
                item(key = "premium_recently_played") {
                    PremiumSectionHeader(
                        title = "Recently Played",
                        subtitle = "YOUR LISTENING HISTORY",
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)
                    )

                    PremiumRecentlyPlayedCarousel(
                        songs = recentlyPlayedToDisplay,
                        currentPlayingId = currentMetadata?.id,
                        isPlaying = isPlaying,
                        onSongClick = { song ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (song.id == currentMetadata?.id) {
                                playerConnection?.togglePlayPause()
                            } else {
                                val songIndex = recentlyPlayedToDisplay.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = "Recently Played",
                                        items = recentlyPlayedToDisplay.map { it.toMediaItem() },
                                        startIndex = songIndex,
                                        isRadio = true
                                    )
                                )
                            }
                        },
                        onSongLongClick = { song ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
                }
            }

            // 5. Curated & Community Playlists (With 3-song previews)
            if (!communityPlaylists.isNullOrEmpty() || !localPlaylists.isNullOrEmpty()) {
                item(key = "premium_playlist_suggestions") {
                    PremiumSectionHeader(
                        title = "Curated Playlists",
                        subtitle = "YOUR PLAYLISTS & COMMUNITY PICKS",
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)
                    )

                    PremiumPlaylistSuggestionsSection(
                        communityPlaylists = communityPlaylists,
                        accountPlaylists = accountPlaylists,
                        localPlaylists = localPlaylists,
                        onOpenOnlinePlaylist = { browseId -> navController.navigate("online_playlist/$browseId") },
                        onOpenLocalPlaylist = { localId -> navController.navigate("local_playlist/$localId") },
                        onPlayOnlinePlaylist = { endpoint -> playerConnection?.playQueue(YouTubeQueue(endpoint)) },
                        onPlaySong = { songItem ->
                            playerConnection?.playQueue(
                                YouTubeQueue(songItem.endpoint ?: WatchEndpoint(videoId = songItem.id), songItem.toMediaMetadata())
                            )
                        }
                    )
                }
            }

            // 6. Made For You (Grouped rounded card list matching Settings style)
            item(key = "premium_made_for_you") {
                PremiumSectionHeader(
                    title = "Made For You",
                    subtitle = "PERSONALIZED CURATIONS",
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)
                )

                PremiumMadeForYouList(
                    dailyDiscover = dailyDiscover.orEmpty(),
                    quickPicks = quickPicks.orEmpty(),
                    onPlaySong = { songItem ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (songItem.id == currentMetadata?.id) {
                            playerConnection?.togglePlayPause()
                        } else {
                            playerConnection?.playQueue(
                                YouTubeQueue(
                                    songItem.endpoint ?: WatchEndpoint(videoId = songItem.id),
                                    songItem.toMediaMetadata()
                                )
                            )
                        }
                    },
                    onSongLongClick = { songItem ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            YouTubeSongMenu(
                                song = songItem,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            // 7. Keep Listening / Frequent Rotations (From App Storage)
            keepListening?.takeIf { it.isNotEmpty() }?.let { listeningItems ->
                item(key = "premium_keep_listening") {
                    PremiumSectionHeader(
                        title = "Keep Listening",
                        subtitle = "FREQUENT ROTATIONS",
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)
                    )

                    PremiumKeepListeningRow(
                        items = listeningItems,
                        onItemClick = { item ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            when (item) {
                                is Song -> {
                                    if (item.id == currentMetadata?.id) {
                                        playerConnection?.togglePlayPause()
                                    } else {
                                        playerConnection?.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                                    }
                                }
                                is Album -> navController.navigate("album/${item.id}")
                                is DbArtist -> navController.navigate("artist/${item.id}")
                                else -> {}
                            }
                        },
                        onItemLongClick = { item ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            when (item) {
                                is Song -> menuState.show {
                                    SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss)
                                }
                                is Album -> menuState.show {
                                    AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss)
                                }
                                is DbArtist -> menuState.show {
                                    ArtistMenu(originalArtist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
                                }
                                else -> {}
                            }
                        }
                    )
                }
            }

            // 8. Forgotten Favorites (Resurfaced from Storage)
            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { favSongs ->
                item(key = "premium_forgotten_favs") {
                    PremiumSectionHeader(
                        title = "Forgotten Favorites",
                        subtitle = "RESURFACED FOR YOU",
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(favSongs.take(15), key = { it.id }) { song ->
                            PremiumCompactSongCard(
                                song = song,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (song.id == currentMetadata?.id) {
                                        playerConnection?.togglePlayPause()
                                    } else {
                                        playerConnection?.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 9. Similar to Top Played Artists (Taste Recommendation Engine)
            similarRecommendations?.takeIf { it.isNotEmpty() }?.let { similarList ->
                items(similarList, key = { it.title.id }) { recommendation ->
                    PremiumSectionHeader(
                        title = recommendation.title.title,
                        subtitle = "SIMILAR TO YOUR TASTE",
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recommendation.items, key = { it.id }) { item ->
                            PremiumYTItemCard(
                                item = item,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when (item) {
                                        is SongItem -> playerConnection?.playQueue(
                                            YouTubeQueue(
                                                item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                item.toMediaMetadata()
                                            )
                                        )
                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                        is ArtistItem -> navController.navigate("artist/${item.id}")
                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 10. Curated Home Page Sections (Online / Cached)
            homePage?.sections?.forEachIndexed { index, section ->
                item(key = "premium_home_section_$index") {
                    PremiumSectionHeader(
                        title = section.title,
                        subtitle = "EXPLORE & DISCOVER",
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(section.items, key = { it.id }) { item ->
                            PremiumYTItemCard(
                                item = item,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when (item) {
                                        is SongItem -> playerConnection?.playQueue(
                                            YouTubeQueue(
                                                item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                item.toMediaMetadata()
                                            )
                                        )
                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                        is ArtistItem -> navController.navigate("artist/${item.id}")
                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 11. Moods & Genres Exploration
            explorePage?.moodAndGenres?.let { moodAndGenres ->
                item(key = "premium_moods_genres") {
                    PremiumSectionHeader(
                        title = stringResource(R.string.mood_and_genres),
                        subtitle = "CATEGORIES",
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(moodAndGenres) { mg ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PremiumTheme.SurfaceCard)
                                    .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(16.dp))
                                    .clickable {
                                        navController.navigate("youtube_browse/${mg.endpoint.browseId}?params=${mg.endpoint.params}")
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = mg.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
            }

            item(key = "premium_bottom_spacer") {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Quick Shuffle Floating Action Button
        HideOnScrollFAB(
            lazyListState = listState,
            icon = R.drawable.shuffle,
            onClick = {
                val songsToShuffle = allLocalItems.filterIsInstance<Song>()
                if (songsToShuffle.isNotEmpty()) {
                    val shuffled = songsToShuffle.shuffled()
                    playerConnection?.playQueue(ListQueue(title = "Shuffled Library", items = shuffled.map { it.toMediaItem() }))
                }
            },
            onRecognitionClick = {
                navController.navigate("recognition")
            },
            onCameraClick = {
                showVisionSheet = true
            },
            onAIClick = {
                showNemotronSheet = true
            }
        )

        if (showVisionSheet) {
            VisionPlaylistBottomSheet(
                onDismiss = { showVisionSheet = false }
            )
        }

        if (showNemotronSheet) {
            NemotronAIBottomSheet(
                onDismiss = { showNemotronSheet = false }
            )
        }
    }
}

// ============================================================================
// 1. APP HEADER (TideFlow + History + Settings + Avatar)
// ============================================================================
@Composable
private fun PremiumAppHeader(
    accountImageUrl: String?,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF14151C))
                    .border(1.dp, PremiumTheme.CardBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = "TideFlow",
                fontFamily = FontFamily.SansSerif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
        }

        // Actions: History + Settings + Avatar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // History Button
            IconButton(
                onClick = onHistoryClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PremiumTheme.SurfaceCard)
                    .border(1.dp, PremiumTheme.CardBorder, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_history),
                    contentDescription = "History",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Settings Button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PremiumTheme.SurfaceCard)
                    .border(1.dp, PremiumTheme.CardBorder, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            // User Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PremiumTheme.SurfaceCard)
                    .border(1.5.dp, Color(0x30FFFFFF), CircleShape)
                    .clickable { onAvatarClick() }
            ) {
                if (!accountImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = accountImageUrl.resize(100, 100),
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.person),
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. PILL FILTER ROW (Settings & Proton Inset Style)
// ============================================================================
@Composable
private fun PremiumPillFilterRow(
    pills: List<String>,
    selectedPill: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pills.forEach { pill ->
            val isSelected = pill.equals(selectedPill, ignoreCase = true)

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White
                        else PremiumTheme.SurfaceCard
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.White else PremiumTheme.CardBorder,
                        shape = CircleShape
                    )
                    .clickable { onSelect(pill) }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text = pill,
                    color = if (isSelected) Color(0xFF08090C) else PremiumTheme.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

// ============================================================================
// 3. HERO FEATURE CARD (Glossy Dark Stadium Banner)
// ============================================================================
@Composable
private fun PremiumFeatureCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumTheme.SurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .border(1.dp, PremiumTheme.SpecularBorder, RoundedCornerShape(26.dp))
            .clickable(onClick = onPlayClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl.resize(800, 800),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dark Studio Vignette Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x6007070A),
                                Color(0xCC07070A),
                                Color(0xF807070A)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Feature Badge
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "FEATURED SELECTION",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            color = PremiumTheme.TextSecondary,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Floating circular play button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .shadow(8.dp, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = "Play",
                            tint = Color(0xFF07070A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. SECTION HEADER (Clean Modern Sans-Serif)
// ============================================================================
@Composable
private fun PremiumSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = subtitle,
            color = PremiumTheme.TextMuted,
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.3.sp
        )
    }
}

// ============================================================================
// 5. RECENTLY PLAYED CAROUSEL
// ============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumRecentlyPlayedCarousel(
    songs: List<Song>,
    currentPlayingId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(songs.take(15), key = { it.id }) { song ->
            val isCurrentActive = song.id == currentPlayingId

            Column(
                modifier = Modifier
                    .width(145.dp)
                    .combinedClickable(
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongLongClick(song) }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(145.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(PremiumTheme.SurfaceCard)
                        .border(
                            width = 1.dp,
                            color = if (isCurrentActive) Color.White.copy(alpha = 0.6f) else PremiumTheme.CardBorder,
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    AsyncImage(
                        model = song.thumbnailUrl?.resize(400, 400),
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                                )
                            )
                    )

                    if (isCurrentActive && isPlaying) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.volume_up),
                                contentDescription = "Playing",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = song.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" },
                    color = PremiumTheme.TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================================
// 6. PLAYLIST SUGGESTIONS (Grouped Rounded Cards)
// ============================================================================
@Composable
private fun PremiumPlaylistSuggestionsSection(
    communityPlaylists: List<CommunityPlaylistItem>?,
    accountPlaylists: List<PlaylistItem>?,
    localPlaylists: List<Playlist>?,
    onOpenOnlinePlaylist: (String) -> Unit,
    onOpenLocalPlaylist: (String) -> Unit,
    onPlayOnlinePlaylist: (WatchEndpoint) -> Unit,
    onPlaySong: (SongItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        // Community Curated Playlists
        communityPlaylists?.forEach { item ->
            item(key = "comm_playlist_${item.playlist.id}") {
                val dbPlaylist by database.playlistByBrowseId(item.playlist.id).collectAsState(initial = null)
                val isBookmarked = dbPlaylist?.playlist?.bookmarkedAt != null

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = PremiumTheme.SurfaceCard),
                    modifier = Modifier
                        .width(310.dp)
                        .border(1.dp, PremiumTheme.SpecularBorder, RoundedCornerShape(24.dp))
                        .clickable { onOpenOnlinePlaylist(item.playlist.id) }
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF191A24))
                                    .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(14.dp))
                            ) {
                                AsyncImage(
                                    model = item.playlist.thumbnail?.resize(200, 200),
                                    contentDescription = item.playlist.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.playlist.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.playlist.author?.name ?: "Curated Playlist",
                                    color = PremiumTheme.TextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.playlist.songCountText ?: "Playlist",
                                    color = PremiumTheme.TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Preview 3 Tracks
                        if (item.songs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0F1017))
                                    .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                item.songs.take(3).forEachIndexed { idx, song ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onPlaySong(song) }
                                            .padding(vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            color = PremiumTheme.TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(18.dp)
                                        )
                                        Text(
                                            text = song.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = song.artists.joinToString(", ") { it.name },
                                            color = PremiumTheme.TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Action Buttons Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { item.playlist.playEndpoint?.let { onPlayOnlinePlaylist(it) } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF07070A)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            IconButton(
                                onClick = { item.playlist.radioEndpoint?.let { onPlayOnlinePlaylist(it) } },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color(0xFF1C1D26), RoundedCornerShape(14.dp))
                                    .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(14.dp))
                            ) {
                                Icon(painterResource(R.drawable.radio), null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        if (dbPlaylist?.playlist == null) {
                                            database.transaction {
                                                val playlistEntity = PlaylistEntity(
                                                    name = item.playlist.title,
                                                    browseId = item.playlist.id,
                                                    thumbnailUrl = item.playlist.thumbnail,
                                                    remoteSongCount = item.playlist.songCountText?.split(" ")?.firstOrNull()?.toIntOrNull(),
                                                    playEndpointParams = item.playlist.playEndpoint?.params,
                                                    shuffleEndpointParams = item.playlist.shuffleEndpoint?.params,
                                                    radioEndpointParams = item.playlist.radioEndpoint?.params
                                                ).toggleLike()
                                                insert(playlistEntity)
                                            }
                                        } else {
                                            database.transaction {
                                                val currentPlaylist = dbPlaylist!!.playlist
                                                update(currentPlaylist.toggleLike())
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color(0xFF1C1D26), RoundedCornerShape(14.dp))
                                    .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(14.dp))
                            ) {
                                Icon(
                                    painter = painterResource(if (isBookmarked) R.drawable.library_add_check else R.drawable.library_add),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Local Playlists
        localPlaylists?.forEach { playlist ->
            item(key = "local_playlist_${playlist.playlist.id}") {
                Column(
                    modifier = Modifier
                        .width(145.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onOpenLocalPlaylist(playlist.playlist.id) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(145.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(PremiumTheme.SurfaceCard)
                            .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(18.dp))
                    ) {
                        if (!playlist.playlist.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = playlist.playlist.thumbnailUrl?.resize(400, 400),
                                contentDescription = playlist.playlist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = playlist.playlist.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${playlist.songCount} songs",
                        color = PremiumTheme.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Account Playlists
        accountPlaylists?.forEach { playlistItem ->
            item(key = "acct_playlist_${playlistItem.id}") {
                Column(
                    modifier = Modifier
                        .width(145.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onOpenOnlinePlaylist(playlistItem.id) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(145.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(PremiumTheme.SurfaceCard)
                            .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(18.dp))
                    ) {
                        AsyncImage(
                            model = playlistItem.thumbnail?.resize(400, 400),
                            contentDescription = playlistItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = playlistItem.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = playlistItem.author?.name ?: "Playlist",
                        color = PremiumTheme.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ============================================================================
// 7. MADE FOR YOU (Grouped Card Rows Matching Settings Style)
// ============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumMadeForYouList(
    dailyDiscover: List<DailyDiscoverItem>,
    quickPicks: List<Song>,
    onPlaySong: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(dailyDiscover, quickPicks) {
        val list = mutableListOf<SongItem>()
        dailyDiscover.forEach { d ->
            (d.recommendation as? SongItem)?.let { list.add(it) }
        }
        if (list.size < 6) {
            quickPicks.forEach { q ->
                if (list.none { it.id == q.id }) {
                    list.add(
                        SongItem(
                            id = q.id,
                            title = q.title,
                            artists = q.artists.map { YtArtist(it.name, it.id) },
                            album = null,
                            duration = null,
                            thumbnail = q.thumbnailUrl.orEmpty(),
                            explicit = false
                        )
                    )
                }
            }
        }
        list.distinctBy { it.id }.take(7)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumTheme.SurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, PremiumTheme.SpecularBorder, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            items.forEachIndexed { index, songItem ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onPlaySong(songItem) },
                            onLongClick = { onSongLongClick(songItem) }
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Index Number
                    Text(
                        text = String.format("%02d", index + 1),
                        color = PremiumTheme.TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.width(30.dp)
                    )

                    // Thumbnail
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1B24))
                            .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = songItem.thumbnail.resize(120, 120),
                            contentDescription = songItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Title & Artist
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songItem.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = songItem.artists.joinToString(", ") { it.name }.ifBlank { "TideFlow Selection" },
                            color = PremiumTheme.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Play Button
                    IconButton(
                        onClick = { onPlaySong(songItem) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1D26))
                            .border(1.dp, PremiumTheme.CardBorder, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (index < items.size - 1) {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.04f),
                        modifier = Modifier.padding(start = 60.dp, end = 16.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// 8. KEEP LISTENING ROW
// ============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumKeepListeningRow(
    items: List<LocalItem>,
    onItemClick: (LocalItem) -> Unit,
    onItemLongClick: (LocalItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
    ) {
        items(items.take(15)) { item ->
            val title = when (item) {
                is Song -> item.title
                is Album -> item.title
                is DbArtist -> item.title
                else -> ""
            }
            val subtitle = when (item) {
                is Song -> item.artists.joinToString(", ") { it.name }
                is Album -> item.artists.joinToString(", ") { it.name }
                is DbArtist -> "Artist"
                else -> ""
            }
            val thumbUrl = when (item) {
                is Song -> item.thumbnailUrl
                is Album -> item.thumbnailUrl
                is DbArtist -> item.thumbnailUrl
                else -> null
            }
            val isCircle = item is DbArtist
            val shape = if (isCircle) CircleShape else RoundedCornerShape(18.dp)

            Column(
                modifier = Modifier
                    .width(135.dp)
                    .combinedClickable(
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(135.dp)
                        .clip(shape)
                        .background(PremiumTheme.SurfaceCard)
                        .border(1.dp, PremiumTheme.CardBorder, shape)
                ) {
                    AsyncImage(
                        model = thumbUrl?.resize(300, 300),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    color = PremiumTheme.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================================
// 9. COMPACT SONG CARD (For Forgotten Favorites)
// ============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumCompactSongCard(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(135.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(PremiumTheme.SurfaceCard)
                .border(1.dp, PremiumTheme.CardBorder, RoundedCornerShape(18.dp))
        ) {
            AsyncImage(
                model = song.thumbnailUrl?.resize(300, 300),
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = song.artists.joinToString(", ") { it.name },
            color = PremiumTheme.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================================
// 10. YT ITEM CARD (For Recommendations & Explore Sections)
// ============================================================================
@Composable
private fun PremiumYTItemCard(
    item: YTItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArtist = item is ArtistItem
    val shape = if (isArtist) CircleShape else RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .width(135.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(shape)
                .background(PremiumTheme.SurfaceCard)
                .border(1.dp, PremiumTheme.CardBorder, shape)
        ) {
            AsyncImage(
                model = item.thumbnail?.resize(300, 300),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val subtitle = when (item) {
            is SongItem -> item.artists.joinToString(", ") { it.name }
            is AlbumItem -> item.artists?.joinToString(", ") { it.name }.orEmpty()
            is ArtistItem -> "Artist"
            is PlaylistItem -> item.author?.name ?: "Playlist"
        }

        Text(
            text = subtitle,
            color = PremiumTheme.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================================
// SPEED DIAL SECTION (YOUTUBE MUSIC 3x3 DYNAMIC GRID WITH HORIZONTAL PAGING)
// ============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumSpeedDialSection(
    speedDialItems: List<YTItem>,
    pinnedSpeedDialItems: List<com.music.vivi.db.entities.SpeedDialItem>,
    currentMetadataId: String?,
    isPlaying: Boolean,
    onItemClick: (YTItem) -> Unit,
    onItemLongClick: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (speedDialItems.isEmpty()) return

    val totalItems = speedDialItems.take(27)
    val pageCount = ((totalItems.size + 8) / 9).coerceIn(1, 3)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = modifier.fillMaxWidth()) {
        PremiumSectionHeader(
            title = "Speed Dial",
            subtitle = "QUICK PICKS & PINNED • 3x3",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val pageItems = totalItems.drop(page * 9).take(9)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (col in 0 until 3) {
                            val itemIndex = row * 3 + col
                            val item = pageItems.getOrNull(itemIndex)
                            if (item != null) {
                                val isPinned = pinnedSpeedDialItems.any { it.id == item.id }
                                val isActive = currentMetadataId == item.id
                                Box(modifier = Modifier.weight(1f)) {
                                    SpeedDialGridItem(
                                        item = item,
                                        isPinned = isPinned,
                                        isActive = isActive,
                                        isPlaying = isPlaying && isActive,
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Horizontal Pager Page Indicators (Dots)
        if (pageCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.5.dp)
                            .size(if (isSelected) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.25f))
                    )
                }
            }
        }
    }
}

