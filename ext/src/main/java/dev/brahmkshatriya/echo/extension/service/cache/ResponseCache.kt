package dev.brahmkshatriya.echo.extension.service.cache

import dev.brahmkshatriya.echo.extension.clients.login.LoginClientImpl.Companion.getCurrentUser
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Briefly keeps parsed responses, so that the calls Echo makes to show one page share a single
 * request. Echo first loads the page's item (e.g. the album), then its tracks, feed and whether
 * it's liked. The first call passes `fresh` to always send a request, including when the page is
 * refreshed, and the rest reuse its response. Calls made while a request is running wait for it
 * instead of sending their own. Failures aren't kept.
 */
object ResponseCache {
    private const val TTL = 30_000L

    private class Entry(val value: CompletableDeferred<Any?>, val expiresAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    suspend fun <T> cached(key: String, fresh: Boolean = false, fetch: suspend () -> T): T {
        val user = getCurrentUser()
        val userKey = "${user.server?.url}|${user.username}|$key"
        val now = System.currentTimeMillis()
        val created = Entry(CompletableDeferred(), now + TTL)

        val entry = entries.compute(userKey) { _, old ->
            if (!fresh && old != null && old.expiresAt > now) old else created
        }!!
        if (entry === created) {
            entries.values.removeIf { it.expiresAt <= now }
            try {
                created.value.complete(fetch())
            } catch (e: Throwable) {
                entries.remove(userKey, created)
                created.value.completeExceptionally(e)
            }
        }

        @Suppress("UNCHECKED_CAST")
        return entry.value.await() as T
    }

    // For changes made on the server, which cached responses wouldn't show
    fun clear() {
        entries.clear()
    }
}
