package com.music.vivi.ai

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.vivi.ai.storage.AiAgentStorage
import com.music.vivi.constants.FirebaseAiSyncKey
import com.music.vivi.constants.FirebaseBirthdateKey
import com.music.vivi.constants.FirebaseCustomPlaylistKey
import com.music.vivi.constants.FirebaseDolbyKey
import com.music.vivi.constants.FirebaseEmailKey
import com.music.vivi.constants.FirebaseEqualizerKey
import com.music.vivi.constants.FirebaseIsLoggedInKey
import com.music.vivi.constants.FirebaseNameKey
import com.music.vivi.constants.FirebaseUsernameKey
import com.music.vivi.constants.FirebaseYoutubeSyncKey
import com.music.vivi.constants.PlaylistSortType
import com.music.vivi.db.MusicDatabase
import com.music.vivi.utils.dataStore
import com.music.vivi.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

data class NemotronUserContext(
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val birthdate: String = "",
    val isLoggedIn: Boolean = false,
    val aiSync: Boolean = true,
    val dolby: Boolean = true,
    val customPlaylist: Boolean = true,
    val equalizer: Boolean = true,
    val youtubeSync: Boolean = false,
    val recentSongs: List<String> = emptyList(),
    val likedSongs: List<String> = emptyList(),
    val playlists: List<String> = emptyList()
) {
    companion object {
        suspend fun load(context: Context, database: MusicDatabase? = null): NemotronUserContext {
            val name = context.dataStore.get(FirebaseNameKey, "")
            val username = context.dataStore.get(FirebaseUsernameKey, "")
            val email = context.dataStore.get(FirebaseEmailKey, "")
            val birthdate = context.dataStore.get(FirebaseBirthdateKey, "")
            val isLoggedIn = context.dataStore.get(FirebaseIsLoggedInKey, false)
            val aiSync = context.dataStore.get(FirebaseAiSyncKey, true)
            val dolby = context.dataStore.get(FirebaseDolbyKey, true)
            val customPlaylist = context.dataStore.get(FirebaseCustomPlaylistKey, true)
            val equalizer = context.dataStore.get(FirebaseEqualizerKey, true)
            val youtubeSync = context.dataStore.get(FirebaseYoutubeSyncKey, false)

            var recentSongsList: List<String> = emptyList()
            var likedSongsList: List<String> = emptyList()
            var playlistsList: List<String> = emptyList()

            try {
                if (database != null) {
                    recentSongsList = database.events().first().take(10).map {
                        "${it.song.title} - ${it.song.artists.joinToString { a -> a.name }}"
                    }
                    likedSongsList = database.likedSongsByCreateDateAsc().first().take(10).map {
                        "${it.title} - ${it.artists.joinToString { a -> a.name }}"
                    }
                    playlistsList = database.playlists(PlaylistSortType.CREATE_DATE, true).first().take(5).map {
                        it.playlist.name
                    }
                }
            } catch (_: Exception) {}

            return NemotronUserContext(
                name = name,
                username = username,
                email = email,
                birthdate = birthdate,
                isLoggedIn = isLoggedIn,
                aiSync = aiSync,
                dolby = dolby,
                customPlaylist = customPlaylist,
                equalizer = equalizer,
                youtubeSync = youtubeSync,
                recentSongs = recentSongsList,
                likedSongs = likedSongsList,
                playlists = playlistsList
            )
        }
    }
}

data class NemotronSong(
    val title: String,
    val artist: String,
    var songItem: SongItem? = null
)

data class NemotronAIResult(
    val message: String,
    val playlistTitle: String,
    val vibe: String,
    val songs: List<NemotronSong>,
    val userGreeting: String = ""
)

object NemotronAIService {
    private const val API_KEY = "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"
    private const val ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
    private const val MODEL = "nvidia/nemotron-3.5-lightning-30b-a3b"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun queryMusicAI(
        userPrompt: String,
        userContext: NemotronUserContext? = null,
        context: Context? = null
    ): Result<NemotronAIResult> = withContext(Dispatchers.IO) {
        runCatching {
            // Load Agent context from separate storage if available
            val agentSystemContext = if (context != null) {
                try {
                    val agentData = AiAgentStorage.load(context)
                    AiAgentStorage.getAgentContextSystemPrompt(agentData)
                } catch (e: Exception) {
                    null
                }
            } else null

            val systemPrompt = buildString {
                appendLine("You are TideFlow AI, an ultra-intelligent, deeply personalized music curator powered by NVIDIA Nemotron 3.5 Lightning 30B.")
                appendLine("You have direct access to the TideFlow music engine and the user's authentic account profile and listening data.")
                appendLine()

                if (agentSystemContext != null) {
                    appendLine(agentSystemContext)
                    appendLine()
                } else if (userContext != null) {
                    appendLine("=== REGISTERED USER ACCOUNT PROFILE (FROM LOGIN / ONBOARDING) ===")
                    if (userContext.name.isNotBlank()) appendLine("• User Full Name: ${userContext.name}")
                    if (userContext.username.isNotBlank()) appendLine("• Registered Handle: @${userContext.username}")
                    if (userContext.email.isNotBlank()) appendLine("• Account Email: ${userContext.email}")
                    if (userContext.birthdate.isNotBlank()) appendLine("• Date of Birth / Generation: ${userContext.birthdate}")
                    appendLine("• Account Status: ${if (userContext.isLoggedIn) "Verified Logged In" else "Local / Guest Profile"}")
                    appendLine("• User Feature & Audio Preferences:")
                    appendLine("   - AI Synchronization: ${if (userContext.aiSync) "Enabled" else "Disabled"}")
                    appendLine("   - Dolby Atmos / 3D Audio: ${if (userContext.dolby) "Enabled" else "Disabled"}")
                    appendLine("   - Custom Playlists: ${if (userContext.customPlaylist) "Enabled" else "Disabled"}")
                    appendLine("   - Equalizer Tuning: ${if (userContext.equalizer) "Enabled" else "Disabled"}")
                    appendLine("   - YouTube Music Sync: ${if (userContext.youtubeSync) "Connected" else "Disconnected"}")
                    if (userContext.likedSongs.isNotEmpty()) {
                        appendLine("• User's Favorite / Liked Tracks in Library:")
                        userContext.likedSongs.forEach { appendLine("   - $it") }
                    }
                    if (userContext.recentSongs.isNotEmpty()) {
                        appendLine("• Recent Playback History:")
                        userContext.recentSongs.forEach { appendLine("   - $it") }
                    }
                    appendLine("=================================================================")
                    appendLine()
                }

                appendLine("Curator Directives & Variety Mandates:")
                appendLine("1. FRESH & UNIQUE EVERY TIME: Every single request from the user MUST yield a completely NEW and DIFFERENT tracklist. NEVER repeat tracks that were previously suggested, recently played, or curated earlier.")
                appendLine("2. DYNAMIC MIX: Suggest good music featuring a dynamic, balanced mix of beloved iconic hits and brand-new fresh releases (2024–2026), rotating artists, eras, and styles creatively.")
                appendLine("3. REAL TRACKS: Ensure exact, real song titles and artist names for every track.")
                appendLine("4. Output strictly valid raw JSON with the following keys:")
                appendLine("   - 'message': 1-2 sentence warm, inspiring response explaining this unique curation tailored to their mood and modern taste.")
                appendLine("   - 'title': Catchy, evocative, distinct playlist title.")
                appendLine("   - 'vibe': Concise description of the mood / sound (e.g., 'Late-Night Melodic Chill • Fresh 2025/2026 & Classics').")
                appendLine("   - 'songs': Array of 6 to 8 real song objects, each with 'title' and 'artist'.")
                appendLine("Return ONLY valid JSON. Do not include markdown blocks (no ```json), no reasoning.")
            }

            val seed = (System.currentTimeMillis() % 1000000).toString()
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "$userPrompt\n(Note: Provide a fresh, unique tracklist with new song selections. Request Seed #$seed)")
                })
            }

            // Disable thinking to prevent long reasoning loops and guarantee fast response (~2-3s)
            val chatTemplateKwargs = JSONObject().apply {
                put("enable_thinking", false)
            }

            val payload = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("chat_template_kwargs", chatTemplateKwargs)
                put("max_tokens", 2048)
                put("temperature", 0.95)
                put("top_p", 0.98)
                put("stream", false)
            }

            var aiContent: String? = null
            try {
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrBlank()) {
                        val rootJson = JSONObject(responseBody)
                        val choices = rootJson.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            aiContent = choices.getJSONObject(0).getJSONObject("message").getString("content")
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // If API call succeeded, parse JSON. If failed, use Autonomous Agent Fallback so AI never fails!
            val parsedResult = if (!aiContent.isNullOrBlank()) {
                parseAiContent(aiContent)
            } else {
                autonomousFallbackCurate(userPrompt)
            }

            // Resolve real playable SongItems on YouTube Music
            resolvePlayableTracks(parsedResult.songs)

            // Save curation session into AI Agent Storage
            if (context != null) {
                try {
                    AiAgentStorage.recordCuration(context, userPrompt, parsedResult)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            parsedResult
        }
    }

    private suspend fun parseAiContent(content: String): NemotronAIResult {
        val cleanContent = content
            .replace("```json", "")
            .replace("```", "")
            .trim()

        var message = "Here are handpicked tracks blending top hits and fresh new releases."
        var title = "Curated AI Soundtrack"
        var vibe = "Dynamic Mix of Classics & Fresh 2024-2026 Hits"
        val songs = mutableListOf<NemotronSong>()

        try {
            val jsonStart = cleanContent.indexOfFirst { it == '{' || it == '[' }
            val jsonEnd = cleanContent.indexOfLast { it == '}' || it == ']' }
            val candidateJson = if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleanContent.substring(jsonStart, jsonEnd + 1)
            } else {
                cleanContent
            }

            val tokener = JSONTokener(candidateJson)
            when (val nextVal = tokener.nextValue()) {
                is JSONObject -> {
                    message = nextVal.optString("message").ifEmpty {
                        nextVal.optString("description", message)
                    }
                    title = nextVal.optString("title").ifEmpty {
                        nextVal.optString("name", "Curated AI Soundtrack")
                    }
                    vibe = nextVal.optString("vibe").ifEmpty {
                        nextVal.optString("mood", "Curated for your request")
                    }

                    val songsArr = nextVal.optJSONArray("songs")
                        ?: nextVal.optJSONArray("tracks")
                        ?: nextVal.optJSONArray("playlist")

                    if (songsArr != null) {
                        parseSongsArray(songsArr, songs)
                    }
                }

                is JSONArray -> {
                    parseSongsArray(nextVal, songs)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback regex if array parsing yielded empty list
        if (songs.isEmpty()) {
            val titleRegex = Regex("""["']?title["']?\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val artistRegex = Regex("""["']?artist["']?\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

            val titles = titleRegex.findAll(cleanContent).map { it.groupValues[1] }.toList()
            val artists = artistRegex.findAll(cleanContent).map { it.groupValues[1] }.toList()

            val count = minOf(titles.size, artists.size)
            for (i in 0 until count) {
                songs.add(NemotronSong(title = titles[i].trim(), artist = artists[i].trim()))
            }
        }

        // Fallback if still empty: split lines with hyphen
        if (songs.isEmpty()) {
            cleanContent.lines().forEach { line ->
                val trimmed = line.trim().trimStart { it.isDigit() || it == '.' || it == '-' || it == '*' }
                if (trimmed.contains(" - ")) {
                    val parts = trimmed.split(" - ", limit = 2)
                    if (parts.size == 2) {
                        songs.add(NemotronSong(title = parts[0].trim('"', ' ', '\''), artist = parts[1].trim('"', ' ', '\'')))
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            return autonomousFallbackCurate(content)
        }

        return NemotronAIResult(
            message = message,
            playlistTitle = title,
            vibe = vibe,
            songs = songs
        )
    }

    /**
     * Autonomous Fallback: Synthesizes a curated tracklist using YouTube search / charts
     * so that the user NEVER sees a failed AI screen even if network or API keys encounter limits.
     */
    private suspend fun autonomousFallbackCurate(userPrompt: String): NemotronAIResult {
        val query = userPrompt.trim()
        val songs = mutableListOf<NemotronSong>()

        try {
            val modifiers = listOf(
                "new releases 2026",
                "top hits 2025",
                "trending songs",
                "viral hits",
                "fresh bangers",
                "vibes mix",
                "chill mix",
                "essential tracks"
            ).shuffled()

            val searchQueries = listOf(
                "$query ${modifiers[0]}",
                "$query ${modifiers[1]}",
                "$query hits",
                query
            ).shuffled()

            for (sq in searchQueries) {
                if (songs.size >= 8) break
                val searchResult = withTimeoutOrNull(5000.milliseconds) {
                    YouTube.search(sq, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                }
                val items = searchResult?.items?.filterIsInstance<SongItem>().orEmpty().shuffled()
                for (item in items) {
                    if (songs.size >= 8) break
                    val artist = item.artists.firstOrNull()?.name.orEmpty()
                    if (songs.none { it.title.equals(item.title, ignoreCase = true) }) {
                        val nemotronSong = NemotronSong(title = item.title, artist = artist)
                        nemotronSong.songItem = item
                        songs.add(nemotronSong)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Expanded pool shuffled every time if offline or empty
        if (songs.isEmpty()) {
            val emergencyPool = listOf(
                NemotronSong("Espresso", "Sabrina Carpenter"),
                NemotronSong("Lose Control", "Teddy Swims"),
                NemotronSong("Stargazing", "Myles Smith"),
                NemotronSong("Birds of a Feather", "Billie Eilish"),
                NemotronSong("A Bar Song (Tipsy)", "Shaboozey"),
                NemotronSong("Midnight City", "M83"),
                NemotronSong("Die With A Smile", "Lady Gaga & Bruno Mars"),
                NemotronSong("Good Luck, Babe!", "Chappell Roan"),
                NemotronSong("Taste", "Sabrina Carpenter"),
                NemotronSong("Beautiful Things", "Benson Boone"),
                NemotronSong("Too Sweet", "Hozier"),
                NemotronSong("Saturn", "SZA"),
                NemotronSong("I Had Some Help", "Post Malone"),
                NemotronSong("Greedy", "Tate McRae"),
                NemotronSong("Starboy", "The Weeknd"),
                NemotronSong("Blinding Lights", "The Weeknd")
            ).shuffled().take(7)
            songs.addAll(emergencyPool)
        }

        return NemotronAIResult(
            message = "Here is an agent-curated selection blending iconic hits and fresh new releases tuned for \"$query\".",
            playlistTitle = "TideFlow • ${query.replaceFirstChar { it.uppercase() }} Mix",
            vibe = "Dynamic Blend • Timeless Hits & Fresh 2024-2026 Tracks",
            songs = songs
        )
    }

    private fun parseSongsArray(array: JSONArray, list: MutableList<NemotronSong>) {
        for (i in 0 until array.length()) {
            when (val item = array.get(i)) {
                is JSONObject -> {
                    val sTitle = item.optString("title").ifEmpty {
                        item.optString("name", "Unknown Track")
                    }
                    val sArtist = item.optString("artist").ifEmpty {
                        item.optString("creator", "Unknown Artist")
                    }
                    list.add(NemotronSong(title = sTitle.trim(), artist = sArtist.trim()))
                }

                is String -> {
                    if (item.contains(" - ")) {
                        val parts = item.split(" - ", limit = 2)
                        list.add(NemotronSong(title = parts[0].trim(), artist = parts[1].trim()))
                    } else {
                        list.add(NemotronSong(title = item.trim(), artist = ""))
                    }
                }
            }
        }
    }

    suspend fun resolveSingleTrack(song: NemotronSong): SongItem? = withContext(Dispatchers.IO) {
        if (song.songItem != null) return@withContext song.songItem
        try {
            val searchQuery = if (song.artist.isNotEmpty()) {
                "${song.title} ${song.artist}"
            } else {
                song.title
            }
            val result = withTimeoutOrNull(4000.milliseconds) {
                YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            }
            var matchedSong = result?.items?.filterIsInstance<SongItem>()?.firstOrNull()

            // Fallback to title only if title+artist had no results
            if (matchedSong == null && song.artist.isNotEmpty()) {
                val fallbackResult = withTimeoutOrNull(3000.milliseconds) {
                    YouTube.search(song.title, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                }
                matchedSong = fallbackResult?.items?.filterIsInstance<SongItem>()?.firstOrNull()
            }

            if (matchedSong != null) {
                song.songItem = matchedSong
            }
            matchedSong
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolvePlayableTracks(songs: List<NemotronSong>) = coroutineScope {
        val jobs = songs.map { song ->
            async {
                resolveSingleTrack(song)
            }
        }
        jobs.awaitAll()
    }
}
