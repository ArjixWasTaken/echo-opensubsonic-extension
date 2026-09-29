package dev.brahmkshatriya.echo.extension.clients.follow

import dev.brahmkshatriya.echo.common.clients.FollowClient
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.extension.clients.artist.ArtistClientImpl.Companion.getArtist
import dev.brahmkshatriya.echo.extension.service.request.RequestService.authenticatedRequest
import dev.brahmkshatriya.echo.extension.service.request.RequestService.parseAs
import dev.brahmkshatriya.echo.extension.service.request.RequestService.runRequest
import dev.brahmkshatriya.echo.extension.service.request.RequestService.throwOnError

class FollowClientImpl : FollowClient {
    override suspend fun followItem(
        item: EchoMediaItem,
        shouldFollow: Boolean,
    ) {
        if (item !is Artist) {
            return
        }

        runRequest(
            authenticatedRequest(
                endpoint = if (shouldFollow) "star" else "unstar",
                parameters = listOf(
                    "artistId" to item.id,
                ),
            ),
        )
    }

    override suspend fun isFollowing(item: EchoMediaItem): Boolean {
        if (item !is Artist) {
            return false
        }

        return getArtist(item.id).starred != null
    }

    /*
     * Not implemented by OpenSubsonic.
     */
    override suspend fun getFollowersCount(item: EchoMediaItem): Long? {
        return null
    }
}