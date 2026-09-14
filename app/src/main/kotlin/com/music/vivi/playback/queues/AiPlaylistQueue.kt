/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.vivi.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.RadioChip
import com.music.vivi.extensions.toMediaItem
import com.music.vivi.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AiPlaylistQueue(
    val title: String? = null,
    val initialSongs: List<SongItem>,
    val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private val _radioChips = MutableStateFlow<List<RadioChip>>(emptyList())
    override val radioChips: StateFlow<List<RadioChip>> = _radioChips.asStateFlow()

    private var continuation: String? = null
    private var hasFetchedRadio = false

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        val safeIndex = startIndex.coerceIn(0, (initialSongs.size - 1).coerceAtLeast(0))

        // Preload radio chips in background for player UI
        try {
            val seed = initialSongs.getOrNull(safeIndex) ?: initialSongs.firstOrNull()
            if (seed != null) {
                val endpoint = seed.endpoint ?: WatchEndpoint(
                    videoId = seed.id,
                    playlistId = "RDAMVM${seed.id}"
                )
                val res = YouTube.next(endpoint).getOrNull()
                if (res != null && res.radioChips.isNotEmpty()) {
                    _radioChips.value = res.radioChips
                }
            }
        } catch (e: Exception) {
            // Non-critical fallback
        }

        Queue.Status(
            title = title,
            items = initialSongs.map { it.toMediaItem() },
            mediaItemIndex = safeIndex,
        )
    }

    override fun hasNextPage(): Boolean = !hasFetchedRadio || continuation != null

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        try {
            val seedSong = initialSongs.getOrNull(startIndex) ?: initialSongs.lastOrNull()
            if (seedSong != null) {
                val endpoint = seedSong.endpoint ?: WatchEndpoint(
                    videoId = seedSong.id,
                    playlistId = "RDAMVM${seedSong.id}"
                )
                val nextResult = YouTube.next(endpoint, continuation).getOrNull()
                if (nextResult != null) {
                    continuation = nextResult.continuation
                    hasFetchedRadio = true
                    if (nextResult.radioChips.isNotEmpty()) {
                        _radioChips.value = nextResult.radioChips
                    }
                    val existingIds = initialSongs.map { it.id }.toSet()
                    return@withContext nextResult.items
                        .filterIsInstance<SongItem>()
                        .filter { it.id !in existingIds }
                        .map { it.toMediaItem() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }
}
