package dev.brahmkshatriya.echo.extension.clients.album

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.clients.artist.ArtistClientImpl.Companion.getArtist
import dev.brahmkshatriya.echo.extension.dto.endpoints.GetAlbumDto
import dev.brahmkshatriya.echo.extension.dto.endpoints.GetAlbumListDto
import dev.brahmkshatriya.echo.extension.dto.types.AlbumDto
import dev.brahmkshatriya.echo.extension.service.cache.ResponseCache.cached
import dev.brahmkshatriya.echo.extension.service.feed.FeedUtils.concurrentFeed
import dev.brahmkshatriya.echo.extension.service.request.RequestService.authenticatedRequest
import dev.brahmkshatriya.echo.extension.service.request.RequestService.parseAs
import dev.brahmkshatriya.echo.extension.service.request.RequestService.runRequest
import dev.brahmkshatriya.echo.extension.service.request.RequestService.throwOnError

class AlbumClientImpl : AlbumClient {
    override suspend fun loadAlbum(album: Album): Album {
        return getAlbum(album.id).toAlbum()
    }

    override suspend fun loadTracks(album: Album): Feed<Track>? {
        return getAlbum(album.id).song?.map { it.toTrack() }?.toFeed()
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? {
        val artist = album.artists.firstOrNull() ?: return null
        val otherAlbums = getArtist(artist.id).album?.map { it.toAlbum() } ?: return null

        return concurrentFeed(
            {
                Shelf.Lists.Items(
                    id = "otherAlbums",
                    title = "More from this artist",
                    list = otherAlbums,
                    type = Shelf.Lists.Type.Linear,
                )
            },
        )
    }

    companion object {
        // Cached, as Echo loads an album, its tracks and whether it's liked separately
        suspend fun getAlbum(id: String): AlbumDto = cached("getAlbum:$id") {
            val albumData = runRequest(
                authenticatedRequest(
                    endpoint = "getAlbum",
                    parameters = listOf(
                        "id" to id,
                    ),
                ),
            ).parseAs<GetAlbumDto>().subsonicResponse
            if (albumData.status != "ok") {
                throwOnError(albumData.error)
            }

            albumData.album!!
        }

        suspend fun getAlbumList(type: AlbumListType, count: Int, offset: Int = 0): List<Album> {
            val albumListData = runRequest(
                authenticatedRequest(
                    endpoint = "getAlbumList2",
                    parameters = listOf(
                        "type" to type.id,
                        "size" to count.toString(),
                        "offset" to offset.toString(),
                    ),
                ),
            ).parseAs<GetAlbumListDto>().subsonicResponse
            if (albumListData.status != "ok") {
                throwOnError(albumListData.error)
            }

            return albumListData.albumList2?.album?.map { it.toAlbum() } ?: emptyList()
        }

        @Suppress("unused")
        enum class AlbumListType(val id: String) {
            Random("random"),
            Newest("newest"),
            Highest("highest"),
            Frequent("frequent"),
            Recent("recent"),
            AlphabeticalByName("alphabeticalByName"),
            AlphabeticalByArtist("alphabeticalByArtist"),
            Starred("starred");
        }
    }
}