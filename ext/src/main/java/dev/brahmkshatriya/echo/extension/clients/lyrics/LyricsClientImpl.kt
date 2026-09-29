package dev.brahmkshatriya.echo.extension.clients.lyrics

import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.clients.login.LoginClientImpl.Companion.getCurrentUser
import dev.brahmkshatriya.echo.extension.dto.endpoints.GetLyricsBySongIdDto
import dev.brahmkshatriya.echo.extension.dto.endpoints.GetLyricsDto
import dev.brahmkshatriya.echo.extension.models.ServerData
import dev.brahmkshatriya.echo.extension.service.request.RequestService.authenticatedRequest
import dev.brahmkshatriya.echo.extension.service.request.RequestService.parseAs
import dev.brahmkshatriya.echo.extension.service.request.RequestService.runRequest
import dev.brahmkshatriya.echo.extension.service.request.RequestService.throwOnError

/**
 * Implements LyricsClient's methods without implementing LyricsClient itself, see OpenSubsonicExtension
 */
class LyricsClientImpl {
    suspend fun searchTrackLyrics(track: Track): Feed<Lyrics> {
        val supportsSongLyrics = getCurrentUser().server?.extensions
            ?.contains(ServerData.Extension.SongLyrics) ?: false

        val lyrics = if (supportsSongLyrics) {
            getStructuredLyrics(track)
        } else {
            listOfNotNull(getLegacyLyrics(track))
        }

        return lyrics.toFeed()
    }

    // Lyrics are fully loaded when searched
    suspend fun loadLyrics(lyrics: Lyrics): Lyrics {
        return lyrics
    }

    private suspend fun getStructuredLyrics(track: Track): List<Lyrics> {
        val lyricsData = runRequest(
            authenticatedRequest(
                endpoint = "getLyricsBySongId",
                parameters = listOf(
                    "id" to track.id,
                ),
            ),
        ).parseAs<GetLyricsBySongIdDto>().subsonicResponse
        if (lyricsData.status != "ok") {
            throwOnError(lyricsData.error)
        }

        // Echo picks the first lyrics by default, so prefer synced ones
        return (lyricsData.lyricsList?.structuredLyrics ?: emptyList())
            .filter { it.line.isNotEmpty() }
            .sortedByDescending { it.synced }
            .mapIndexed { i, it ->
                it.toLyrics(
                    id = "${track.id}-$i",
                    fallbackTitle = track.title,
                    trackDuration = track.duration,
                )
            }
    }

    // For servers without the songLyrics extension, which only serve plain text lyrics
    private suspend fun getLegacyLyrics(track: Track): Lyrics? {
        val artist = track.artists.firstOrNull()?.name ?: return null
        val lyricsData = runRequest(
            authenticatedRequest(
                endpoint = "getLyrics",
                parameters = listOf(
                    "artist" to artist,
                    "title" to track.title,
                ),
            ),
        ).parseAs<GetLyricsDto>().subsonicResponse
        if (lyricsData.status != "ok") {
            throwOnError(lyricsData.error)
        }

        val text = lyricsData.lyrics?.value?.takeIf { it.isNotBlank() } ?: return null
        return Lyrics(
            id = track.id,
            title = lyricsData.lyrics.title ?: track.title,
            subtitle = lyricsData.lyrics.artist ?: artist,
            lyrics = Lyrics.Simple(text),
        )
    }
}
