/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.vivi.ai.storage

import android.content.Context
import com.music.vivi.ai.NemotronSong
import com.music.vivi.ai.NemotronAIResult
import com.music.vivi.constants.AccountEmailKey
import com.music.vivi.constants.AccountNameKey
import com.music.vivi.constants.FirebaseBirthdateKey
import com.music.vivi.constants.FirebaseCustomPlaylistKey
import com.music.vivi.constants.FirebaseDolbyKey
import com.music.vivi.constants.FirebaseEmailKey
import com.music.vivi.constants.FirebaseEqualizerKey
import com.music.vivi.constants.FirebaseIsLoggedInKey
import com.music.vivi.constants.FirebaseNameKey
import com.music.vivi.constants.FirebaseUsernameKey
import com.music.vivi.constants.FirebaseYoutubeSyncKey
import com.music.vivi.constants.InnerTubeCookieKey
import com.music.vivi.constants.YtmSyncKey
import com.music.vivi.db.MusicDatabase
import com.music.vivi.utils.dataStore
import com.music.vivi.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AgentUserProfile(
    var name: String = "",
    var username: String = "",
    var email: String = "",
    var birthdate: String = "",
    var isLoggedIn: Boolean = false,
    var isYouTubeSynced: Boolean = false,
    var isDolbyAtmos: Boolean = false,
    var isEqualizerEnabled: Boolean = false,
    var isCustomPlaylistEnabled: Boolean = true,
    var topFavoriteArtists: List<String> = emptyList(),
    var preferredGenres: List<String> = emptyList(),
    var recentlyPlayedSummary: List<String> = emptyList(),
    var likedSongsSummary: List<String> = emptyList()
)

data class AgentMemoryFact(
    val id: String = UUID.randomUUID().toString(),
    val category: String, // TASTE, NEW_RELEASES, ERA_PREFERENCE, ROUTINE, DISLIKES, MOOD
    val fact: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentCurationSession(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val playlistTitle: String,
    val vibe: String,
    val songCount: Int,
    val songs: List<NemotronSong>,
    val timestamp: Long = System.currentTimeMillis(),
    var wasPlayed: Boolean = false,
    var wasSaved: Boolean = false
)

data class AgentKnowledgeBase(
    var cachedNewReleases: MutableList<NemotronSong> = mutableListOf(),
    var cachedTrendingTracks: MutableList<NemotronSong> = mutableListOf(),
    var lastUpdatedTimestamp: Long = 0L
)

class AiAgentData {
    var version: Int = 1
    var userProfile: AgentUserProfile = AgentUserProfile()
    val memoryFacts: MutableList<AgentMemoryFact> = mutableListOf()
    val curationHistory: MutableList<AgentCurationSession> = mutableListOf()
    var knowledgeBase: AgentKnowledgeBase = AgentKnowledgeBase()
}

object AiAgentStorage {
    private const val FILE_NAME = "tideflow_ai_agent_store.json"
    private val lock = Any()
    private var cachedData: AiAgentData? = null

    /**
     * Loads agent data from persistent storage file (or creates initialized store if absent)
     */
    suspend fun load(context: Context): AiAgentData = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (cachedData != null) return@withContext cachedData!!
            
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) {
                val initial = createInitialAgentData()
                saveInternal(context, initial)
                cachedData = initial
                return@withContext initial
            }

            try {
                val jsonString = file.readText(Charsets.UTF_8)
                val root = JSONObject(jsonString)
                val data = parseAgentData(root)
                cachedData = data
                data
            } catch (e: Exception) {
                e.printStackTrace()
                val fallback = createInitialAgentData()
                cachedData = fallback
                fallback
            }
        }
    }

    /**
     * Persists agent data atomically to disk
     */
    suspend fun save(context: Context) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val data = cachedData ?: return@withContext
            saveInternal(context, data)
        }
    }

    private fun saveInternal(context: Context, data: AiAgentData) {
        try {
            val root = serializeAgentData(data)
            val file = File(context.filesDir, FILE_NAME)
            val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
            tempFile.writeText(root.toString(2), Charsets.UTF_8)
            if (tempFile.exists()) {
                tempFile.renameTo(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Dynamically synchronizes user account profile, settings, and listening stats into Agent Storage
     */
    suspend fun syncUserProfile(context: Context, database: MusicDatabase?) = withContext(Dispatchers.IO) {
        val data = load(context)

        // Read preferences
        val name = context.dataStore.get(FirebaseNameKey, "").ifEmpty {
            context.dataStore.get(AccountNameKey, "")
        }.trim()
        val username = context.dataStore.get(FirebaseUsernameKey, "").trim()
        val email = context.dataStore.get(FirebaseEmailKey, "").ifEmpty {
            context.dataStore.get(AccountEmailKey, "")
        }.trim()
        val birthdate = context.dataStore.get(FirebaseBirthdateKey, "").trim()
        val cookie = context.dataStore.get(InnerTubeCookieKey, "")
        val hasCookie = cookie.contains("SAPISID") || cookie.contains("__Secure-3PSID")
        val ytmSync = context.dataStore.get(FirebaseYoutubeSyncKey, true) && context.dataStore.get(YtmSyncKey, true)
        val dolby = context.dataStore.get(FirebaseDolbyKey, false)
        val eq = context.dataStore.get(FirebaseEqualizerKey, false)
        val customPlaylist = context.dataStore.get(FirebaseCustomPlaylistKey, true)

        // Query database for listening intelligence
        val likedTracks = mutableListOf<String>()
        val recentTracks = mutableListOf<String>()
        val topArtists = mutableListOf<String>()

        if (database != null) {
            try {
                val likedEntities = database.likedSongsByCreateDateAsc().firstOrNull().orEmpty().take(12)
                likedTracks.addAll(likedEntities.map { "${it.title} - ${it.artists.joinToString { a -> a.name }}" })

                val recentEntities = database.events().firstOrNull().orEmpty().take(10)
                recentTracks.addAll(recentEntities.map { "${it.song.title} - ${it.song.artists.joinToString { a -> a.name }}" })

                val artists = database.artistsBookmarkedByNameAsc().firstOrNull().orEmpty().take(8)
                topArtists.addAll(artists.map { it.artist.name })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        synchronized(lock) {
            data.userProfile.apply {
                this.name = name
                this.username = username
                this.email = email
                this.birthdate = birthdate
                this.isLoggedIn = hasCookie || name.isNotEmpty()
                this.isYouTubeSynced = hasCookie && ytmSync
                this.isDolbyAtmos = dolby
                this.isEqualizerEnabled = eq
                this.isCustomPlaylistEnabled = customPlaylist
                if (likedTracks.isNotEmpty()) this.likedSongsSummary = likedTracks
                if (recentTracks.isNotEmpty()) this.recentlyPlayedSummary = recentTracks
                if (topArtists.isNotEmpty()) this.topFavoriteArtists = topArtists
            }
            saveInternal(context, data)
        }
    }

    /**
     * Autonomous learning: Teaches the agent a new preference fact about the user
     */
    suspend fun learnFact(context: Context, category: String, factText: String) = withContext(Dispatchers.IO) {
        if (factText.isBlank()) return@withContext
        val data = load(context)
        synchronized(lock) {
            val exists = data.memoryFacts.any { it.fact.equals(factText, ignoreCase = true) }
            if (!exists) {
                data.memoryFacts.add(
                    AgentMemoryFact(
                        category = category,
                        fact = factText.trim()
                    )
                )
                // Cap memory facts to 30 most relevant
                if (data.memoryFacts.size > 30) {
                    data.memoryFacts.removeAt(0)
                }
                saveInternal(context, data)
            }
        }
    }

    /**
     * Logs an AI curation session for future retrieval and reinforcement learning
     */
    suspend fun recordCuration(
        context: Context,
        query: String,
        result: NemotronAIResult,
        wasPlayed: Boolean = false,
        wasSaved: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val data = load(context)
        synchronized(lock) {
            val session = AgentCurationSession(
                query = query,
                playlistTitle = result.playlistTitle,
                vibe = result.vibe,
                songCount = result.songs.size,
                songs = result.songs,
                wasPlayed = wasPlayed,
                wasSaved = wasSaved
            )
            data.curationHistory.add(0, session)
            // Keep last 25 sessions
            if (data.curationHistory.size > 25) {
                data.curationHistory.removeAt(data.curationHistory.lastIndex)
            }

            // Learn taste cues from query
            val q = query.lowercase()
            when {
                q.contains("new") || q.contains("fresh") || q.contains("2025") || q.contains("2026") -> {
                    learnFactInternal(data, "NEW_RELEASES", "User actively seeks out new releases and 2024-2026 music.")
                }
                q.contains("focus") || q.contains("code") || q.contains("study") -> {
                    learnFactInternal(data, "ROUTINE", "User listens to focus/ambient soundtrack during productivity and coding.")
                }
                q.contains("workout") || q.contains("gym") || q.contains("beast") -> {
                    learnFactInternal(data, "ROUTINE", "User listens to high-energy and intense music for workouts.")
                }
                q.contains("chill") || q.contains("relax") || q.contains("night") -> {
                    learnFactInternal(data, "MOOD", "User appreciates late-night and chill acoustics or smooth vibes.")
                }
            }

            saveInternal(context, data)
        }
    }

    private fun learnFactInternal(data: AiAgentData, category: String, fact: String) {
        if (!data.memoryFacts.any { it.fact.equals(fact, ignoreCase = true) }) {
            data.memoryFacts.add(AgentMemoryFact(category = category, fact = fact))
        }
    }

    /**
     * Updates playback or library bookmark confirmation for a curated session
     */
    suspend fun updateSessionFeedback(
        context: Context,
        playlistTitle: String,
        wasPlayed: Boolean? = null,
        wasSaved: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        val data = load(context)
        synchronized(lock) {
            val session = data.curationHistory.firstOrNull { it.playlistTitle.equals(playlistTitle, ignoreCase = true) }
            if (session != null) {
                if (wasPlayed != null) session.wasPlayed = wasPlayed
                if (wasSaved != null) session.wasSaved = wasSaved
                saveInternal(context, data)
            }
        }
    }

    /**
     * Generates a rich, structured prompt segment for the LLM based on stored agent knowledge
     */
    fun getAgentContextSystemPrompt(data: AiAgentData): String = buildString {
        appendLine("=== TIDEFLOW AI AGENT KNOWLEDGE STORE & PERSISTENT MEMORY ===")
        val p = data.userProfile
        if (p.name.isNotBlank()) appendLine("• User Name: ${p.name}")
        if (p.username.isNotBlank()) appendLine("• User Handle: @${p.username}")
        if (p.birthdate.isNotBlank()) appendLine("• Age / Generation Context: ${p.birthdate}")
        appendLine("• YouTube Music Sync: ${if (p.isYouTubeSynced) "Active & Synced" else "Local Mode"}")
        appendLine("• Audio Engine: ${if (p.isDolbyAtmos) "Dolby Atmos Enabled" else "Standard Hi-Res"}, Equalizer: ${if (p.isEqualizerEnabled) "Custom Tuned" else "Flat"}")

        if (p.topFavoriteArtists.isNotEmpty()) {
            appendLine("• Top Favorite Artists in Library: ${p.topFavoriteArtists.joinToString(", ")}")
        }
        if (p.likedSongsSummary.isNotEmpty()) {
            appendLine("• Recent Library Favorites:")
            p.likedSongsSummary.take(6).forEach { appendLine("   * $it") }
        }
        if (p.recentlyPlayedSummary.isNotEmpty()) {
            appendLine("• Recent Playback History:")
            p.recentlyPlayedSummary.take(5).forEach { appendLine("   * $it") }
        }

        if (data.memoryFacts.isNotEmpty()) {
            appendLine("• Learned Agent Memory & Taste Rules:")
            data.memoryFacts.take(10).forEach { appendLine("   [${it.category}] ${it.fact}") }
        }

        if (data.curationHistory.isNotEmpty()) {
            val recentPlaylists = data.curationHistory.take(3).map { "\"${it.playlistTitle}\" (${it.vibe})" }
            appendLine("• Past Successful Curations: ${recentPlaylists.joinToString(", ")}")
            val pastTracks = data.curationHistory.take(4).flatMap { it.songs }.map { "${it.title} by ${it.artist}" }.filter { it.isNotBlank() }.distinct()
            if (pastTracks.isNotEmpty()) {
                appendLine("• PREVIOUSLY SUGGESTED SONGS (CRITICAL: DO NOT REPEAT ANY OF THESE, SUGGEST BRAND NEW SONGS):")
                pastTracks.take(25).forEach { appendLine("   - Avoid: $it") }
            }
        }
        appendLine("=============================================================")
    }

    private fun createInitialAgentData(): AiAgentData {
        return AiAgentData().apply {
            memoryFacts.add(
                AgentMemoryFact(
                    category = "CURATION_POLICY",
                    fact = "User prefers a high-quality mix of all-time iconic hits and fresh new releases (2024-2026)."
                )
            )
            memoryFacts.add(
                AgentMemoryFact(
                    category = "SOUND_PROFILE",
                    fact = "Curate diverse soundscapes with strong melody, rhythmic immersion, and distinct production quality."
                )
            )
            memoryFacts.add(
                AgentMemoryFact(
                    category = "DISCOVERY",
                    fact = "Blend familiar beloved tracks with exciting new discoveries from top charts and breakthrough artists."
                )
            )
        }
    }

    private fun serializeAgentData(data: AiAgentData): JSONObject {
        val root = JSONObject()
        root.put("version", data.version)

        // Profile
        val p = data.userProfile
        val profileJson = JSONObject().apply {
            put("name", p.name)
            put("username", p.username)
            put("email", p.email)
            put("birthdate", p.birthdate)
            put("isLoggedIn", p.isLoggedIn)
            put("isYouTubeSynced", p.isYouTubeSynced)
            put("isDolbyAtmos", p.isDolbyAtmos)
            put("isEqualizerEnabled", p.isEqualizerEnabled)
            put("isCustomPlaylistEnabled", p.isCustomPlaylistEnabled)
            put("topFavoriteArtists", JSONArray(p.topFavoriteArtists))
            put("preferredGenres", JSONArray(p.preferredGenres))
            put("recentlyPlayedSummary", JSONArray(p.recentlyPlayedSummary))
            put("likedSongsSummary", JSONArray(p.likedSongsSummary))
        }
        root.put("userProfile", profileJson)

        // Memory Facts
        val factsArr = JSONArray()
        data.memoryFacts.forEach { f ->
            factsArr.put(JSONObject().apply {
                put("id", f.id)
                put("category", f.category)
                put("fact", f.fact)
                put("timestamp", f.timestamp)
            })
        }
        root.put("memoryFacts", factsArr)

        // Curation History
        val historyArr = JSONArray()
        data.curationHistory.forEach { s ->
            historyArr.put(JSONObject().apply {
                put("id", s.id)
                put("query", s.query)
                put("playlistTitle", s.playlistTitle)
                put("vibe", s.vibe)
                put("songCount", s.songCount)
                put("timestamp", s.timestamp)
                put("wasPlayed", s.wasPlayed)
                put("wasSaved", s.wasSaved)
                val songsArr = JSONArray()
                s.songs.forEach { song ->
                    songsArr.put(JSONObject().apply {
                        put("title", song.title)
                        put("artist", song.artist)
                    })
                }
                put("songs", songsArr)
            })
        }
        root.put("curationHistory", historyArr)

        return root
    }

    private fun parseAgentData(root: JSONObject): AiAgentData {
        val data = AiAgentData()
        data.version = root.optInt("version", 1)

        val pObj = root.optJSONObject("userProfile")
        if (pObj != null) {
            data.userProfile = AgentUserProfile(
                name = pObj.optString("name", ""),
                username = pObj.optString("username", ""),
                email = pObj.optString("email", ""),
                birthdate = pObj.optString("birthdate", ""),
                isLoggedIn = pObj.optBoolean("isLoggedIn", false),
                isYouTubeSynced = pObj.optBoolean("isYouTubeSynced", false),
                isDolbyAtmos = pObj.optBoolean("isDolbyAtmos", false),
                isEqualizerEnabled = pObj.optBoolean("isEqualizerEnabled", false),
                isCustomPlaylistEnabled = pObj.optBoolean("isCustomPlaylistEnabled", true),
                topFavoriteArtists = jsonArrayToStringList(pObj.optJSONArray("topFavoriteArtists")),
                preferredGenres = jsonArrayToStringList(pObj.optJSONArray("preferredGenres")),
                recentlyPlayedSummary = jsonArrayToStringList(pObj.optJSONArray("recentlyPlayedSummary")),
                likedSongsSummary = jsonArrayToStringList(pObj.optJSONArray("likedSongsSummary"))
            )
        }

        val factsArr = root.optJSONArray("memoryFacts")
        if (factsArr != null) {
            for (i in 0 until factsArr.length()) {
                val fObj = factsArr.optJSONObject(i) ?: continue
                data.memoryFacts.add(
                    AgentMemoryFact(
                        id = fObj.optString("id", UUID.randomUUID().toString()),
                        category = fObj.optString("category", "TASTE"),
                        fact = fObj.optString("fact", ""),
                        timestamp = fObj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }

        val historyArr = root.optJSONArray("curationHistory")
        if (historyArr != null) {
            for (i in 0 until historyArr.length()) {
                val sObj = historyArr.optJSONObject(i) ?: continue
                val songsList = mutableListOf<NemotronSong>()
                val sSongsArr = sObj.optJSONArray("songs")
                if (sSongsArr != null) {
                    for (j in 0 until sSongsArr.length()) {
                        val so = sSongsArr.optJSONObject(j) ?: continue
                        songsList.add(NemotronSong(title = so.optString("title"), artist = so.optString("artist")))
                    }
                }
                data.curationHistory.add(
                    AgentCurationSession(
                        id = sObj.optString("id", UUID.randomUUID().toString()),
                        query = sObj.optString("query", ""),
                        playlistTitle = sObj.optString("playlistTitle", ""),
                        vibe = sObj.optString("vibe", ""),
                        songCount = sObj.optInt("songCount", songsList.size),
                        songs = songsList,
                        timestamp = sObj.optLong("timestamp", System.currentTimeMillis()),
                        wasPlayed = sObj.optBoolean("wasPlayed", false),
                        wasSaved = sObj.optBoolean("wasSaved", false)
                    )
                )
            }
        }

        return data
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.optString(i))
        }
        return list
    }
}
