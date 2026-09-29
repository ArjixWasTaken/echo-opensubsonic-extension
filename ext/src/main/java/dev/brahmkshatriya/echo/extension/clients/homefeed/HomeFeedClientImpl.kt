package dev.brahmkshatriya.echo.extension.clients.homefeed

import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.clients.album.AlbumClientImpl.Companion.AlbumListType
import dev.brahmkshatriya.echo.extension.clients.album.AlbumClientImpl.Companion.getAlbumList
import dev.brahmkshatriya.echo.extension.clients.artist.ArtistClientImpl.Companion.getArtists
import dev.brahmkshatriya.echo.extension.clients.playlist.PlaylistCombinedClientImpl.Companion.getPlaylists
import dev.brahmkshatriya.echo.extension.service.feed.FeedUtils.concurrentFeed
import dev.brahmkshatriya.echo.extension.service.feed.FeedUtils.continuousFeed
import dev.brahmkshatriya.echo.extension.service.search.SearchService.search

class HomeFeedClientImpl : HomeFeedClient {
    private val pageSize = 20
    private val listSize = 10

    // Echo shows track shelves in columns of three
    private val trackListSize = 9

    override suspend fun loadHomeFeed(): Feed<Shelf> {
        return concurrentFeed(
            {
                val albumList = getAlbumList(AlbumListType.Newest, listSize)
                val albumListFull = continuousFeed(pageSize) { offset ->
                    getAlbumList(
                        type = AlbumListType.Newest,
                        count = pageSize,
                        offset = offset,
                    ).map { it.toShelf() }
                }

                Shelf.Lists.Items(
                    id = "recentlyAdded",
                    title = "Recently Added",
                    list = albumList,
                    more = albumListFull,
                    type = Shelf.Lists.Type.Linear,
                )
            },
            {
                val trackList = getAllTracks(trackListSize)
                val trackListFull = continuousFeed(pageSize) { offset ->
                    getAllTracks(
                        count = pageSize,
                        offset = offset,
                    ).map { it.toShelf() }
                }

                Shelf.Lists.Tracks(
                    id = "allTracks",
                    title = "All Tracks",
                    list = trackList,
                    more = trackListFull,
                    type = Shelf.Lists.Type.Linear,
                )
            },
            {
                val playlistListFull = getPlaylists()
                val playlistList = playlistListFull.take(listSize)
                val playlistFeed: Feed<Shelf> = playlistListFull.map { it.toShelf() }.toFeed()

                Shelf.Lists.Items(
                    id = "playlists",
                    title = "Playlists",
                    list = playlistList,
                    more = playlistFeed,
                    type = Shelf.Lists.Type.Linear,
                )
            },
            {
                val artistListFull = getArtists()
                val artistList = artistListFull.shuffled().take(listSize)
                val artistFeed: Feed<Shelf> = artistListFull.map { it.toShelf() }.toFeed()

                Shelf.Lists.Items(
                    id = "artists",
                    title = "Artists",
                    list = artistList,
                    more = artistFeed,
                    type = Shelf.Lists.Type.Linear,
                )
            },
        )
    }

    // OpenSubsonic servers return every track for an empty search query
    private suspend fun getAllTracks(count: Int, offset: Int = 0): List<Track> {
        return search(
            query = "",
            trackCount = count,
            albumCount = 0,
            artistCount = 0,
            trackOffset = offset,
        ).tracks ?: emptyList()
    }
}
