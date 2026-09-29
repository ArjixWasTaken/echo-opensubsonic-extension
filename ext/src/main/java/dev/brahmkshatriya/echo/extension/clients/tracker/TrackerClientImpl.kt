package dev.brahmkshatriya.echo.extension.clients.tracker

import dev.brahmkshatriya.echo.common.models.TrackDetails
import dev.brahmkshatriya.echo.extension.dto.endpoints.ScrobbleDto
import dev.brahmkshatriya.echo.extension.service.request.RequestService.authenticatedRequest
import dev.brahmkshatriya.echo.extension.service.request.RequestService.parseAs
import dev.brahmkshatriya.echo.extension.service.request.RequestService.runRequest
import dev.brahmkshatriya.echo.extension.service.request.RequestService.throwOnError
import dev.brahmkshatriya.echo.extension.service.session.SettingsSession
import kotlin.math.min

/**
 * Implements TrackerMarkClient's methods without implementing TrackerMarkClient itself, see
 * OpenSubsonicExtension
 */
class TrackerClientImpl {
    // When the current track started playing, sent as the time of its scrobble
    @Volatile
    private var playStartedAt = 0L

    // Only called for tracks from this extension, with null once another extension's track plays
    suspend fun onTrackChanged(details: TrackDetails?) {
        details ?: return
        playStartedAt = System.currentTimeMillis()
        if (!SettingsSession.scrobble) return

        // Marks the track as now playing, without counting it as played
        scrobble(details.track.id, submission = false)
    }

    // Same rule as Last.fm: played for half its length or 4 minutes, whichever comes first
    suspend fun getMarkAsPlayedDuration(details: TrackDetails): Long? {
        if (!SettingsSession.scrobble) return null
        val duration = details.totalDuration ?: details.track.duration ?: return null
        return min(duration / 2, 4 * 60 * 1000L)
    }

    suspend fun onMarkAsPlayed(details: TrackDetails) {
        scrobble(details.track.id, submission = true, time = playStartedAt)
    }

    private suspend fun scrobble(id: String, submission: Boolean, time: Long? = null) {
        val scrobbleData = runRequest(
            authenticatedRequest(
                endpoint = "scrobble",
                parameters = buildList {
                    add("id" to id)
                    add("submission" to submission.toString())
                    time?.let { add("time" to it.toString()) }
                },
            ),
        ).parseAs<ScrobbleDto>().subsonicResponse
        if (scrobbleData.status != "ok") {
            throwOnError(scrobbleData.error)
        }
    }
}
