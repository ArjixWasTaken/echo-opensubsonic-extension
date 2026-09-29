package dev.brahmkshatriya.echo.extension.service.request

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.extension.clients.login.LoginClientImpl.Companion.checkAuth
import dev.brahmkshatriya.echo.extension.clients.login.LoginClientImpl.Companion.getCurrentUser
import dev.brahmkshatriya.echo.extension.dto.types.ErrorDto
import dev.brahmkshatriya.echo.extension.models.ServerData
import dev.brahmkshatriya.echo.extension.models.UserData
import dev.brahmkshatriya.echo.extension.service.cache.ResponseCache
import dev.brahmkshatriya.echo.extension.service.session.SettingsSession
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit.MINUTES
import kotlin.text.Charsets.UTF_8

object RequestService {
    private const val API_VERSION: String = "1.16"
    private const val CLIENT_NAME: String = "Echo nightly"
    private const val RESPONSE_FORMAT: String = "json"
    private val COMMON_PARAMETERS: List<Pair<String, String>> = listOf(
        "v" to API_VERSION,
        "c" to CLIENT_NAME,
        "f" to RESPONSE_FORMAT,
    )
    private val DEFAULT_CACHE_CONTROL = CacheControl.Builder().maxAge(10, MINUTES).build()
    private val DEFAULT_HEADERS = Headers.Builder().build()

    private val rng = SecureRandom()
    private val httpClient = OkHttpClient()
    val json = Json { ignoreUnknownKeys = true }

    // AUTHENTICATION

    private fun generateSalt(length: Int = 8): String {
        val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '-' + '_'

        return buildString(length) {
            repeat(length) {
                append(charPool[rng.nextInt(charPool.size)])
            }
        }
    }

    // The same for every request of an account, see appendAuthParameters
    private fun accountSalt(credentials: UserData): String {
        return MessageDigest.getInstance("SHA-256")
            .digest("${credentials.server?.url}|${credentials.username}".toByteArray(UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }

    private fun generateToken(password: String, salt: String): String {
        // MessageDigest isn't thread-safe and tokens are generated from concurrent requests
        return MessageDigest.getInstance("MD5").digest((password + salt).toByteArray(UTF_8))
            .joinToString("") {
                "%02x".format(it)
            }
    }

    /**
     * @param stableSalt whether the request needs the same URL every time, like cover art and
     * streams that Echo loads and caches by URL itself. Their salt is then derived from the
     * account instead of random, so the token and URL don't change between calls.
     */
    private fun appendAuthParameters(
        parameters: List<Pair<String, String>> = emptyList(),
        credentials: UserData = getCurrentUser(),
        stableSalt: Boolean = false,
    ): List<Pair<String, String>> {
        checkAuth(credentials)

        credentials.apiKey?.let {
            return parameters + listOf(
                "apiKey" to it,
            )
        }

        credentials.password!!.let {
            val salt = if (stableSalt) accountSalt(credentials) else generateSalt()
            val token = generateToken(it, salt)
            return parameters + listOf(
                "u" to credentials.username,
                "t" to token,
                "s" to salt,
            )
        }
    }

    // REQUESTS

    fun get(
        baseUrl: String,
        endpoint: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ): Request {
        return Request.Builder()
            .url(
                baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("rest")
                    addPathSegment(endpoint)

                    (COMMON_PARAMETERS + parameters).forEach {
                        addQueryParameter(
                            it.first,
                            it.second,
                        )
                    }
                }.build(),
            )
            .headers(DEFAULT_HEADERS)
            .cacheControl(DEFAULT_CACHE_CONTROL)
            .build()
    }

    fun post(
        baseUrl: String,
        endpoint: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ): Request {
        return Request.Builder()
            .url(
                baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("rest")
                    addPathSegment(endpoint)
                }.build(),
            )
            .post(
                FormBody.Builder().apply {
                    (COMMON_PARAMETERS + parameters).forEach { add(it.first, it.second) }
                }.build(),
            )
            .headers(DEFAULT_HEADERS)
            .cacheControl(DEFAULT_CACHE_CONTROL)
            .build()
    }

    fun authenticatedRequest(
        endpoint: String,
        parameters: List<Pair<String, String>> = emptyList(),
        needsGet: Boolean = false,
        credentials: UserData = getCurrentUser(),
    ): Request {
        // Requests that need GET are the ones handed to Echo to load itself
        val params: List<Pair<String, String>> =
            appendAuthParameters(parameters, credentials, stableSalt = needsGet)
        val server: ServerData = credentials.server!!
        val supportsPost: Boolean =
            server.extensions?.contains(ServerData.Extension.FormPost) ?: false

        return if (supportsPost && !needsGet && !SettingsSession.forceGetRequests) {
            post(baseUrl = server.url, endpoint = endpoint, parameters = params)
        } else {
            get(baseUrl = server.url, endpoint = endpoint, parameters = params)
        }
    }

    // Change data that cached responses include, e.g. whether an album is liked
    private val WRITE_ENDPOINTS = setOf(
        "star", "unstar", "createPlaylist", "updatePlaylist", "deletePlaylist",
    )

    suspend fun runRequest(
        request: Request,
    ): Response {
        val response = httpClient.newCall(request).await()
        if (request.url.pathSegments.last() in WRITE_ENDPOINTS) {
            ResponseCache.clear()
        }
        return response
    }

    // UTILS

    fun RequestBody.toByteArray(): ByteArray {
        val buffer = Buffer()
        this.writeTo(buffer)
        return buffer.readByteArray()
    }

    fun Request.toNetworkRequest(): NetworkRequest {
        return NetworkRequest(
            url = url.toString(),
            headers = buildMap {
                headers.forEach { put(it.first, it.second) }
            },
            method = NetworkRequest.Method.valueOf(method),
            body = body?.toByteArray(),
        )
    }

    fun throwOnError(error: ErrorDto?) {
        throw error?.let {
            Exception("Error " + it.code + ": " + (it.message ?: "Unknown error"))
        } ?: Exception("Unknown error")
    }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromStream(body.byteStream())
    }
}