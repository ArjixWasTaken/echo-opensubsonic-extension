package dev.brahmkshatriya.echo.extension.clients.searchfeed

import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.extension.service.feed.FeedUtils.continuousPages
import dev.brahmkshatriya.echo.extension.service.genre.GenreService.createGenreFeed
import dev.brahmkshatriya.echo.extension.service.genre.GenreService.getGenres
import dev.brahmkshatriya.echo.extension.service.search.SearchService.search
import dev.brahmkshatriya.echo.extension.service.session.SettingsSession

class SearchFeedClientImpl : SearchFeedClient {
    // How many results to load at a time when browsing everything
    private val pageSize = 50

    // Each tab only searches for its own results, once it's opened
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        val query = if (query.isBlank()) "" else query

        return Feed(
            listOf("Tracks", "Albums", "Artists", "Genres").map { Tab(it, it) },
        ) { tab ->
            val pagedData: PagedData<Shelf> = when (tab?.id) {
                "Tracks" -> searchPages(query) { count, offset ->
                    search(query, count, 0, 0, trackOffset = offset).tracks?.map { it.toShelf() }
                }

                "Albums" -> searchPages(query) { count, offset ->
                    search(query, 0, count, 0, albumOffset = offset).albums?.map { it.toShelf() }
                }

                "Artists" -> searchPages(query) { count, offset ->
                    search(query, 0, 0, count, artistOffset = offset).artists?.map { it.toShelf() }
                }

                "Genres" -> PagedData.Single {
                    getGenres()
                        .filter { it.contains(query.trim(), ignoreCase = true) }
                        .let { if (query.isEmpty()) it else it.take(SettingsSession.searchResults) }
                        .map {
                            Shelf.Category(
                                id = it.lowercase().replace(" ", ""),
                                title = it,
                                feed = createGenreFeed(it),
                            )
                        }
                }

                else -> throw IllegalArgumentException("Unknown tab")
            }
            pagedData.toFeedData()
        }
    }

    // An empty query browses everything a page at a time, others show the top results
    private fun searchPages(
        query: String,
        search: suspend (count: Int, offset: Int) -> List<Shelf>?,
    ): PagedData<Shelf> {
        return if (query.isEmpty()) {
            continuousPages(pageSize) { offset -> search(pageSize, offset) ?: emptyList() }
        } else {
            PagedData.Single { search(SettingsSession.searchResults, 0) ?: emptyList() }
        }
    }
}
