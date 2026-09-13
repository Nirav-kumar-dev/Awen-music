package com.music.vivi.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class AISong(
    val title: String,
    val artist: String,
    var songItem: SongItem? = null
)

data class VisionPlaylist(
    val title: String,
    val vibe: String,
    val songs: List<AISong>,
    val imageBitmap: Bitmap? = null
)

object VisionPlaylistService {
    private const val API_KEY = "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"
    private const val ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
    private const val MODEL = "google/diffusiongemma-26b-a4b-it"

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun loadScaledBitmap(context: Context, uri: Uri, maxDimension: Int = 800): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            var sampleSize = 1
            var w = options.outWidth
            var h = options.outHeight
            while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
                w /= 2
                h /= 2
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun analyzeImageAndGeneratePlaylist(bitmap: Bitmap): Result<VisionPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Scale down bitmap if needed
            val maxDim = 800
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (newW, newH) = if (ratio > 1f) {
                    maxDim to (maxDim / ratio).toInt()
                } else {
                    (maxDim * ratio).toInt() to maxDim
                }
                Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            } else {
                bitmap
            }

            // 2. Compress to JPEG
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()
            val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$base64Image"

            // 3. Prompt requesting clear JSON format
            val promptText = "Analyze the mood, scene, lighting, and aesthetic in this image. Curate 6-8 real songs that fit this exact vibe. Return ONLY valid JSON like: {\"title\": \"Playlist Title\", \"vibe\": \"Short description\", \"songs\": [{\"title\": \"Song Name\", \"artist\": \"Artist Name\"}]}. No extra text or markdown formatting."

            val userContent = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", promptText)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", dataUrl)
                    })
                })
            }

            val messageObj = JSONObject().apply {
                put("role", "user")
                put("content", userContent)
            }

            val payload = JSONObject().apply {
                put("model", MODEL)
                put("messages", JSONArray().apply { put(messageObj) })
                put("max_tokens", 1024)
                put("temperature", 0.7)
                put("stream", false)
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer $API_KEY")
                .addHeader("Content-Type", "application/json")
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

            // Clean any potential markdown wrapper or reasoning
            val cleanContent = content
                .replace("```json", "")
                .replace("```", "")
                .trim()

            var title = "Visual Vibe Playlist"
            var vibe = "Soundtrack curated for this photo"
            val songs = mutableListOf<AISong>()

            try {
                // Find start of JSON
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
                        title = nextVal.optString("title").ifEmpty {
                            nextVal.optString("name").ifEmpty {
                                nextVal.optString("playlist_name", "Visual Vibe Playlist")
                            }
                        }
                        vibe = nextVal.optString("vibe").ifEmpty {
                            nextVal.optString("mood").ifEmpty {
                                nextVal.optString("description", "Curated for this scene")
                            }
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
                        if (songs.isNotEmpty()) {
                            title = "${songs.first().title} & Similar Vibes"
                        }
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
                    songs.add(AISong(title = titles[i].trim(), artist = artists[i].trim()))
                }
            }

            // Fallback if still empty: split lines with hyphen
            if (songs.isEmpty()) {
                cleanContent.lines().forEach { line ->
                    val trimmed = line.trim().trimStart { it.isDigit() || it == '.' || it == '-' || it == '*' }
                    if (trimmed.contains(" - ")) {
                        val parts = trimmed.split(" - ", limit = 2)
                        if (parts.size == 2) {
                            songs.add(AISong(title = parts[0].trim('"', ' ', '\''), artist = parts[1].trim('"', ' ', '\'')))
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                // Ensure user never gets a blank screen
                songs.add(AISong(title = "Midnight City", artist = "M83"))
                songs.add(AISong(title = "Starboy", artist = "The Weeknd"))
                songs.add(AISong(title = "Nightcall", artist = "Kavinsky"))
            }

            // Resolve songs with YouTube search
            for (song in songs) {
                try {
                    val query = "${song.title} ${song.artist}"
                    val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    song.songItem = searchResult?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                } catch (_: Exception) {
                    // Ignore individual resolution failure
                }
            }

            VisionPlaylist(
                title = title,
                vibe = vibe,
                songs = songs,
                imageBitmap = scaled
            )
        }
    }

    private fun parseSongsArray(array: JSONArray, result: MutableList<AISong>) {
        for (i in 0 until array.length()) {
            val item = array.get(i)
            if (item is JSONObject) {
                val sTitle = item.optString("title").ifEmpty {
                    item.optString("song").ifEmpty {
                        item.optString("track").ifEmpty {
                            item.optString("name", "")
                        }
                    }
                }
                val sArtist = item.optString("artist").ifEmpty {
                    item.optString("singer").ifEmpty {
                        item.optString("author", "Unknown Artist")
                    }
                }
                if (sTitle.isNotBlank()) {
                    result.add(AISong(title = sTitle.trim(), artist = sArtist.trim()))
                }
            } else if (item is String) {
                val str = item.trim()
                if (str.contains(" - ")) {
                    val parts = str.split(" - ", limit = 2)
                    result.add(AISong(title = parts[0].trim(), artist = parts[1].trim()))
                } else if (str.contains(" by ")) {
                    val parts = str.split(" by ", limit = 2)
                    result.add(AISong(title = parts[0].trim(), artist = parts[1].trim()))
                } else {
                    result.add(AISong(title = str, artist = "AI Curated"))
                }
            }
        }
    }
}
