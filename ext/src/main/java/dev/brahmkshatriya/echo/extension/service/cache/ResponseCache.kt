package dev.brahmkshatriya.echo.extension.service.cache

import dev.brahmkshatriya.echo.extension.clients.login.LoginClientImpl.Companion.getCurrentUser
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Briefly keeps parsed responses, so that the calls Echo makes to show one page (e.g. an album,
 * its tracks and whether it's liked) share a single request. Calls made while a request is
 * running wait for it instead of sending their own. Failures aren't kept.
 */
object ResponseCache {
    const val SHORT_TTL = 30_000L
    const val LONG_TTL = 10 * 60_000L

    private class Entry(val value: CompletableDeferred<Any?>, val expiresAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    suspend fun <T> cached(key: String, ttl: Long = SHORT_TTL, fetch: suspend () -> T): T {
        val user = getCurrentUser()
        val userKey = "${user.server?.url}|${user.username}|$key"
        val now = System.currentTimeMillis()
        val created = Entry(CompletableDeferred(), now + ttl)

        val entry = entries.compute(userKey) { _, old ->
            if (old != null && old.expiresAt > now) old else created
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
