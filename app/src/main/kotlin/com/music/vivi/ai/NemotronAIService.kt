package com.music.vivi.ai

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

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
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun queryMusicAI(
        userPrompt: String,
        userContext: NemotronUserContext? = null
    ): Result<NemotronAIResult> = withContext(Dispatchers.IO) {
        runCatching {
            val systemPrompt = buildString {
                appendLine("You are TideFlow AI, an ultra-intelligent, deeply personalized music curator powered by NVIDIA Nemotron 3.5 Lightning 30B.")
                appendLine("You have full direct access to the TideFlow music app and the user's authentic account profile data captured during registration and login.")
                appendLine()
                if (userContext != null) {
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
                    if (userContext.playlists.isNotEmpty()) {
                        appendLine("• User's Created Playlists:")
                        userContext.playlists.forEach { appendLine("   - $it") }
                    }
                    appendLine("=================================================================")
                    appendLine()
                }
                appendLine("Curator Instructions:")
                appendLine("1. Greet or personalize responses naturally referencing the user's name or handle when appropriate.")
                appendLine("2. Deeply factor in the user's age/birthdate, listening history, and audio settings (e.g. Dolby/Equalizer) when curating playlists.")
                appendLine("3. The user will ask to find songs for their mood, ask music questions, or request playlists.")
                appendLine("4. Always output valid raw JSON with keys:")
                appendLine("   - 'message': Insightful 1-2 sentence warm response explaining your curation tailored to their profile and mood.")
                appendLine("   - 'title': Catchy playlist or soundtrack title.")
                appendLine("   - 'vibe': Concise description of the mood / sound.")
                appendLine("   - 'songs': Array of 5 to 8 real song objects, each with 'title' and 'artist'.")
                appendLine("Return ONLY valid JSON, do not include markdown blocks or reasoning.")
            }

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }

            val chatTemplateKwargs = JSONObject().apply {
                put("enable_thinking", false)
            }

            val payload = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("chat_template_kwargs", chatTemplateKwargs)
                put("max_tokens", 2048)
                put("temperature", 0.7)
                put("top_p", 0.95)
                put("stream", false)
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                error("NVIDIA AI error (${response.code}): $err")
            }

            val responseBody = response.body?.string() ?: error("Empty AI response")
            val rootJson = JSONObject(responseBody)
            val choices = rootJson.getJSONArray("choices")
            val content = choices.getJSONObject(0).getJSONObject("message").getString("content")

            val cleanContent = content
                .replace("```json", "")
                .replace("```", "")
                .trim()

            var message = "Here are handpicked tracks tailored to your mood."
            var title = "Curated AI Soundtrack"
            var vibe = "Handcrafted for your vibe"
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

            // Resolve real playable SongItems on YouTube Music
            resolvePlayableTracks(songs)

            NemotronAIResult(
                message = message,
                playlistTitle = title,
                vibe = vibe,
                songs = songs
            )
        }
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

    private suspend fun resolvePlayableTracks(songs: List<NemotronSong>) = coroutineScope {
        val jobs = songs.map { song ->
            async {
                try {
                    val searchQuery = if (song.artist.isNotEmpty()) {
                        "${song.title} ${song.artist}"
                    } else {
                        song.title
                    }
                    val result = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val matchedSong = result?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                    if (matchedSong != null) {
                        song.songItem = matchedSong
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        jobs.awaitAll()
    }
}
