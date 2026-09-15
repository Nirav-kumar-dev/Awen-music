package com.music.vivi.ai

import android.util.Log
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.vivi.db.MusicDatabase
import com.music.vivi.db.entities.ArtistEntity
import com.music.vivi.db.entities.EventWithSong
import com.music.vivi.db.entities.Song
import com.music.vivi.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

enum class DaypartingVibe(val label: String, val searchKeywords: List<String>) {
    MORNING("Morning Uplift", listOf("acoustic morning", "uplifting chill", "fresh acoustic")),
    AFTERNOON("Afternoon Energy", listOf("upbeat electronic", "indie pop", "rhythm flow")),
    EVENING("Evening Unwind", listOf("melodic chillout", "r&b soul", "sunset vibes")),
    NOCTURNAL("Nocturnal Flow", listOf("synthwave night drive", "ambient lofi", "deep midnight"))
}

@Singleton
class UserAlgorithmEngine @Inject constructor(
    private val database: MusicDatabase
) {
    companion object {
        private const val TAG = "UserAlgorithmEngine"
    }

    /**
     * Determines current dayparting temporal vibe based on device local time.
     */
    fun getCurrentDaypartingVibe(): DaypartingVibe {
        val hour = LocalTime.now().hour
        return when (hour) {
            in 5..11 -> DaypartingVibe.MORNING
            in 12..17 -> DaypartingVibe.AFTERNOON
            in 18..22 -> DaypartingVibe.EVENING
            else -> DaypartingVibe.NOCTURNAL
        }
    }

    /**
     * 1. OPTIMIZED PREVIOUS ALGORITHM (Enhanced Local Affinity Engine)
     * Resurfaces familiar user favorites, heavy rotations, and forgotten gems
     * with advanced affinity scoring and recency decay.
     */
    suspend fun computeOptimizedPreviousAlgo(
        hideVideoSongs: Boolean = false,
        limit: Int = 25
    ): List<Song> = withContext(Dispatchers.IO) {
        try {
            val now = LocalDateTime.now()
            val events: List<EventWithSong> = database.events().first()
            val likedSongs = database.likedSongsByCreateDateAsc().first()
            val forgotten = database.forgottenFavorites().first()
            val from14Days = System.currentTimeMillis() - 86400000L * 14
            val mostPlayed = database.mostPlayedSongs(from14Days, limit = 20).first()

            // Database related songs (may be empty if offline cache is sparse)
            val dbRelated = try {
                database.quickPicks().first()
            } catch (e: Exception) {
                emptyList()
            }

            // Candidate pool
            val candidateMap = mutableMapOf<String, Song>()
            events.forEach { candidateMap[it.song.id] = it.song }
            likedSongs.forEach { candidateMap[it.id] = it }
            forgotten.forEach { candidateMap[it.id] = it }
            mostPlayed.forEach { candidateMap[it.id] = it }
            dbRelated.forEach { candidateMap[it.id] = it }

            if (candidateMap.isEmpty()) {
                return@withContext emptyList()
            }

            // Calculate Affinity Scores
            val scoredSongs = candidateMap.values.map { song ->
                var affinityScore = 0.0

                // Total play time contribution (1.5 pts per minute)
                val playMinutes = (song.song.totalPlayTime / 60000.0).coerceAtLeast(0.0)
                affinityScore += playMinutes * 1.5

                // Liked song bonus
                if (song.song.liked) {
                    affinityScore += 35.0
                }

                // In-library bonus
                if (song.song.inLibrary != null) {
                    affinityScore += 15.0
                }

                // Recency decay bonus based on latest playback event
                val latestEvent = events.firstOrNull { it.song.id == song.id }
                if (latestEvent != null) {
                    val hoursAgo = Duration.between(latestEvent.event.timestamp, now).toHours().coerceAtLeast(0)
                    // Exponential decay curve: recent listens get up to 50 points
                    val recencyBonus = 50.0 / (1.0 + (hoursAgo / 24.0))
                    affinityScore += recencyBonus
                }

                // Forgotten favorites resurfacing bonus
                if (forgotten.any { it.id == song.id }) {
                    affinityScore += 20.0
                }

                song to affinityScore
            }

            val result = scoredSongs
                .filter { !hideVideoSongs || !it.first.song.isVideo }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(limit)

            Log.d(TAG, "Optimized Previous Algo produced ${result.size} scored familiar songs")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in computeOptimizedPreviousAlgo", e)
            emptyList()
        }
    }

    /**
     * 2. NEW ADAPTIVE ALGORITHM (Dynamic Contextual & Collaborative Intelligence)
     * Context-aware dayparting, multi-seed collaborative discovery from YouTube Innertube,
     * diversity constraints, and novel track discovery.
     */
    suspend fun computeNewAdaptiveAlgo(
        hideVideoSongs: Boolean = false,
        limit: Int = 25
    ): List<Song> = withContext(Dispatchers.IO) {
        try {
            val vibe = getCurrentDaypartingVibe()
            val events = database.events().first()
            val likedSongs = database.likedSongsByCreateDateAsc().first()

            // Pick up to 5 diverse seed songs across different artists
            val seeds = mutableListOf<Song>()
            val seenArtists = mutableSetOf<String>()

            val priorityPool = (events.map { it.song } + likedSongs).distinctBy { it.id }
            for (song in priorityPool) {
                val artistId = song.artists.firstOrNull()?.id ?: song.artists.firstOrNull()?.name.orEmpty()
                if (artistId.isNotBlank() && seenArtists.add(artistId)) {
                    seeds.add(song)
                    if (seeds.size >= 5) break
                }
            }

            val discoveredSongs = java.util.Collections.synchronizedList(mutableListOf<Song>())

            coroutineScope {
                // Query collaborative related tracks for each seed concurrently
                val seedDeferreds = seeds.map { seed ->
                    async(Dispatchers.IO) {
                        try {
                            val nextResult = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()
                            val endpoint = nextResult?.relatedEndpoint
                            val relatedSongs = mutableListOf<SongItem>()

                            if (endpoint != null) {
                                val relatedPage = YouTube.related(endpoint).getOrNull()
                                relatedPage?.songs?.let { relatedSongs.addAll(it) }
                            }

                            if (relatedSongs.isEmpty() && nextResult != null) {
                                relatedSongs.addAll(nextResult.items.filterIsInstance<SongItem>())
                            }

                            relatedSongs
                                .filter { !hideVideoSongs || !it.isVideoSong }
                                .filter { !it.explicit }
                                .filter { it.id != seed.id }
                                .take(8)
                                .forEach { ytItem ->
                                    val converted = convertYtSongItemToSong(ytItem)
                                    discoveredSongs.add(converted)
                                }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed collaborative discovery for seed ${seed.id}: ${e.message}")
                        }
                    }
                }
                seedDeferreds.awaitAll()
            }

            // Fallback: If seeds were empty or network returned few items, query search summary for dayparting vibe
            if (discoveredSongs.size < 10) {
                val query = vibe.searchKeywords.random()
                YouTube.searchSummary(query).getOrNull()?.let { result ->
                    result.summaries.flatMap { it.items }.filterIsInstance<SongItem>()
                        .filter { !hideVideoSongs || !it.isVideoSong }
                        .filter { !it.explicit }
                        .take(15)
                        .forEach { ytItem ->
                            discoveredSongs.add(convertYtSongItemToSong(ytItem))
                        }
                }
            }

            // Apply Diversity Constraints: Max 2 tracks per artist
            val artistCounts = mutableMapOf<String, Int>()
            val diverseResult = mutableListOf<Song>()

            for (song in discoveredSongs.distinctBy { it.id }.shuffled()) {
                val artistKey = song.artists.firstOrNull()?.name ?: "Unknown"
                val count = artistCounts.getOrDefault(artistKey, 0)
                if (count < 2) {
                    artistCounts[artistKey] = count + 1
                    diverseResult.add(song)
                    if (diverseResult.size >= limit) break
                }
            }

            Log.d(TAG, "New Adaptive Algo (${vibe.label}) produced ${diverseResult.size} discovery songs")
            diverseResult
        } catch (e: Exception) {
            Log.e(TAG, "Error in computeNewAdaptiveAlgo", e)
            emptyList()
        }
    }

    /**
     * 3. THE HYBRID MIXER
     * Interleaves 50% familiar high-affinity songs with 50% fresh discoveries.
     * Produces a balanced, diverse flow.
     */
    fun mixAlgos(
        previousList: List<Song>,
        newList: List<Song>,
        targetSize: Int = 30
    ): List<Song> {
        val mixed = mutableListOf<Song>()
        val seenIds = mutableSetOf<String>()

        val maxIter = maxOf(previousList.size, newList.size)
        for (i in 0 until maxIter) {
            // Pick from Previous (Familiar)
            if (i < previousList.size) {
                val prevSong = previousList[i]
                if (seenIds.add(prevSong.id)) {
                    mixed.add(prevSong)
                }
            }
            // Pick from New (Discovery)
            if (i < newList.size) {
                val newSong = newList[i]
                if (seenIds.add(newSong.id)) {
                    mixed.add(newSong)
                }
            }
            if (mixed.size >= targetSize) break
        }

        // Fill remaining if needed
        if (mixed.size < targetSize) {
            for (song in previousList + newList) {
                if (seenIds.add(song.id)) {
                    mixed.add(song)
                    if (mixed.size >= targetSize) break
                }
            }
        }

        Log.d(TAG, "Hybrid Mixer blended ${previousList.size} familiar + ${newList.size} discovery => ${mixed.size} total")
        return mixed
    }

    /**
     * Converts an Innertube SongItem into a full Song object so it can be queued
     * and played by the player seamlessly even before being stored permanently in Room.
     */
    private fun convertYtSongItemToSong(item: SongItem): Song {
        return Song(
            song = SongEntity(
                id = item.id,
                title = item.title,
                duration = item.duration ?: -1,
                thumbnailUrl = item.thumbnail,
                albumId = item.album?.id,
                albumName = item.album?.name,
                explicit = item.explicit,
                isVideo = item.isVideoSong
            ),
            artists = item.artists.map { ytArtist ->
                ArtistEntity(
                    id = ytArtist.id ?: "artist_${ytArtist.name.hashCode()}",
                    name = ytArtist.name
                )
            }
        )
    }
}
