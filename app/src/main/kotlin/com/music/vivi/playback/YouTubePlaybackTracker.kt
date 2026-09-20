package com.music.vivi.playback

import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.vivi.constants.InnerTubeCookieKey
import com.music.innertube.utils.parseCookieString
import com.music.vivi.utils.dataStore
import com.music.vivi.utils.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.random.Random

/**
 * Tracks active song playback in the background directly against YouTube's servers using the
 * user's logged-in YouTube account.
 *
 * When a song starts playing, this tracker:
 * 1. Fetches a fresh authenticated player response from YouTube for the song.
 * 2. Immediately registers the playback start ping (`videostatsPlaybackUrl`) and 0-second watchtime ping.
 * 3. Sends periodic active watchtime pings every 10 seconds while the song is playing.
 * 4. Pings on pause / resume / track change.
 * 5. Emits [MusicService.playbackRegistered] as soon as the song passes 5 seconds, triggering
 *    the app's History screen to refresh and show the song in YouTube Cloud History.
 *
 * If the user is NOT logged into YouTube (no SAPISID cookie), this tracker is completely inactive
 * and the app behaves 100% like before.
 */
class YouTubePlaybackTracker(
    private val service: MusicService,
) {
    companion object {
        private const val TAG = "YouTubePlaybackSync"
        private const val WATCHTIME_INTERVAL_MS = 10_000L // 10 seconds
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentTrackingJob: Job? = null
    private var currentVideoId: String? = null
    private var currentCpn: String? = null
    private var playbackUrl: String? = null
    private var watchtimeUrl: String? = null
    private var atrUrl: String? = null
    private var songDurationSeconds: Int? = null
    private var lastReportedPositionSeconds: Float = 0f
    private var hasEmittedPlaybackRegistered = false

    private fun isLoggedIn(): Boolean {
        val cookie = service.dataStore.get(InnerTubeCookieKey, "")
        val cookieMap = parseCookieString(cookie)
        return "SAPISID" in cookieMap || "__Secure-3PAPISID" in cookieMap || "__Secure-1PAPISID" in cookieMap
    }

    fun onSongStarted(videoId: String) {
        if (!isLoggedIn()) {
            Timber.tag(TAG).d("User not logged into YouTube. Skipping cloud playback sync for %s", videoId)
            return
        }

        if (currentVideoId == videoId && currentTrackingJob?.isActive == true) {
            // Already actively tracking this video
            return
        }

        stopTracking(sendFinalPing = true)

        currentVideoId = videoId
        lastReportedPositionSeconds = 0f
        hasEmittedPlaybackRegistered = false

        currentTrackingJob = scope.launch {
            try {
                Timber.tag(TAG).i("Starting hidden YouTube cloud playback session for videoId: %s", videoId)

                // 1. Fetch fresh authenticated player response
                val playerResponse = YouTube.player(
                    videoId = videoId,
                    playlistId = null,
                    client = YouTubeClient.WEB_REMIX,
                ).getOrNull()

                if (playerResponse == null) {
                    Timber.tag(TAG).w("Failed to fetch player response for videoId: %s", videoId)
                    return@launch
                }

                val tracking = playerResponse.playbackTracking
                playbackUrl = tracking?.videostatsPlaybackUrl?.baseUrl
                watchtimeUrl = tracking?.videostatsWatchtimeUrl?.baseUrl
                atrUrl = tracking?.atrUrl?.baseUrl
                songDurationSeconds = playerResponse.videoDetails?.lengthSeconds?.toIntOrNull()

                // Generate a unique 16-character client playback nonce (cpn)
                val cpn = (1..16).map {
                    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[Random.Default.nextInt(0, 64)]
                }.joinToString("")
                currentCpn = cpn

                Timber.tag(TAG).i("Got playback tracking URLs for %s (cpn=%s)", videoId, cpn)

                // 2. Send immediate playback start ping
                playbackUrl?.let { url ->
                    YouTube.registerPlayback(
                        playbackUrl = url,
                        watchtimeUrl = null,
                        playlistId = null,
                        contentLengthSeconds = songDurationSeconds,
                        playbackPositionSeconds = 0f,
                        cpn = cpn,
                    ).onSuccess {
                        Timber.tag(TAG).i("Playback start ping registered successfully for %s", videoId)
                    }.onFailure {
                        Timber.tag(TAG).e(it, "Playback start ping failed for %s", videoId)
                    }
                }

                // 3. Send initial watchtime ping (0 seconds)
                watchtimeUrl?.let { url ->
                    YouTube.registerWatchtime(
                        url = url,
                        cpn = cpn,
                        playlistId = null,
                        contentLengthSeconds = songDurationSeconds,
                        playbackPositionSeconds = 0f,
                        startTimeSeconds = 0f,
                        endTimeSeconds = 0f,
                        state = "playing",
                    ).onSuccess {
                        Timber.tag(TAG).i("Initial watchtime ping (0s) registered successfully for %s", videoId)
                    }.onFailure {
                        Timber.tag(TAG).e(it, "Initial watchtime ping failed for %s", videoId)
                    }
                }

                // 4. Send atr ping if available
                atrUrl?.let { url ->
                    YouTube.registerAtr(url, cpn)
                }

                // 5. Active watchtime loop while playing
                while (isActive && currentVideoId == videoId) {
                    delay(WATCHTIME_INTERVAL_MS)

                    if (!service.player.isPlaying) {
                        continue
                    }

                    val currentPos = (service.player.currentPosition.coerceAtLeast(0) / 1000f)
                    val startPos = lastReportedPositionSeconds
                    val endPos = currentPos
                    lastReportedPositionSeconds = currentPos

                    watchtimeUrl?.let { url ->
                        YouTube.registerWatchtime(
                            url = url,
                            cpn = cpn,
                            playlistId = null,
                            contentLengthSeconds = songDurationSeconds,
                            playbackPositionSeconds = currentPos,
                            startTimeSeconds = startPos,
                            endTimeSeconds = endPos,
                            state = "playing",
                        ).onSuccess {
                            Timber.tag(TAG).d("Watchtime ping (%.1fs - %.1fs) registered for %s", startPos, endPos, videoId)
                            if (!hasEmittedPlaybackRegistered && currentPos >= 5f) {
                                hasEmittedPlaybackRegistered = true
                                service.playbackRegistered.tryEmit(videoId)
                                Timber.tag(TAG).i("Song %s reached 5s — emitted playbackRegistered to refresh History", videoId)
                            }
                        }.onFailure {
                            Timber.tag(TAG).e(it, "Watchtime ping failed for %s", videoId)
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Expected when song changes or stops
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error in YouTube playback tracking for %s", videoId)
            }
        }
    }

    fun onPlayPauseChanged(isPlaying: Boolean) {
        if (!isLoggedIn() || currentVideoId == null || watchtimeUrl == null || currentCpn == null) return

        scope.launch {
            val currentPos = (service.player.currentPosition.coerceAtLeast(0) / 1000f)
            val startPos = lastReportedPositionSeconds
            lastReportedPositionSeconds = currentPos
            val state = if (isPlaying) "playing" else "paused"

            watchtimeUrl?.let { url ->
                YouTube.registerWatchtime(
                    url = url,
                    cpn = currentCpn!!,
                    playlistId = null,
                    contentLengthSeconds = songDurationSeconds,
                    playbackPositionSeconds = currentPos,
                    startTimeSeconds = startPos,
                    endTimeSeconds = currentPos,
                    state = state,
                )
            }
        }
    }

    fun stopTracking(sendFinalPing: Boolean = true) {
        val oldJob = currentTrackingJob
        val oldVideoId = currentVideoId
        val oldWatchtimeUrl = watchtimeUrl
        val oldCpn = currentCpn
        val oldDuration = songDurationSeconds
        val lastPos = lastReportedPositionSeconds

        currentTrackingJob = null
        currentVideoId = null
        playbackUrl = null
        watchtimeUrl = null
        atrUrl = null
        currentCpn = null

        oldJob?.cancel()

        if (sendFinalPing && oldWatchtimeUrl != null && oldCpn != null && oldVideoId != null) {
            scope.launch {
                val currentPos = (service.player.currentPosition.coerceAtLeast(0) / 1000f)
                YouTube.registerWatchtime(
                    url = oldWatchtimeUrl,
                    cpn = oldCpn,
                    playlistId = null,
                    contentLengthSeconds = oldDuration,
                    playbackPositionSeconds = currentPos,
                    startTimeSeconds = lastPos,
                    endTimeSeconds = currentPos,
                    state = "paused",
                )
            }
        }
    }

    fun release() {
        stopTracking(sendFinalPing = false)
        scope.cancel()
    }
}
