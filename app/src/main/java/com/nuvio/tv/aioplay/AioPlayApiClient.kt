package com.nuvio.tv.aioplay

import android.os.Build
import com.nuvio.tv.BuildConfig
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class AioPlayApiException(
    message: String,
    val statusCode: Int? = null
) : Exception(message)

@Singleton
class AioPlayApiClient @Inject constructor(
    @param:Named("customServerAuth") private val http: OkHttpClient
) {
    private val baseUrl: String
        get() = BuildConfig.AIOPLAY_API_BASE_URL.trim().trimEnd('/')

    fun isConfigured(): Boolean {
        if (baseUrl.isBlank()) return false
        return runCatching {
            val uri = java.net.URI(baseUrl)
            (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun requireBaseUrl(): String {
        if (!isConfigured()) {
            throw AioPlayApiException(
                "AIOPlay server URL is not configured in this APK."
            )
        }
        return baseUrl
    }

    private suspend fun requestJson(
        path: String,
        method: String = "GET",
        token: String? = null,
        body: JSONObject? = null,
        allowEmpty: Boolean = false
    ): JSONObject = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(requireBaseUrl() + path)
            .header("Accept", "application/json")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        when (method) {
            "POST", "PATCH", "PUT" -> {
                val raw = (body ?: JSONObject()).toString()
                requestBuilder.method(method, raw.toRequestBody(JSON_MEDIA_TYPE))
            }
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        http.newCall(requestBuilder.build()).execute().use { httpResponse ->
            val raw = httpResponse.body?.string().orEmpty()
            if (!httpResponse.isSuccessful) {
                val message = runCatching {
                    JSONObject(raw).optString("error").takeIf { it.isNotBlank() }
                }.getOrNull() ?: ("Server returned HTTP " + httpResponse.code + ".")
                throw AioPlayApiException(message, httpResponse.code)
            }
            if (allowEmpty && raw.isBlank()) return@use JSONObject()
            if (raw.isBlank()) return@use JSONObject()
            return@use JSONObject(raw)
        }
    }

    suspend fun login(username: String, password: String): AioPlayLoginResult {
        val payload = JSONObject()
            .put("username", username.trim())
            .put("password", password)
            .put("clientType", "android_tv")
            .put(
                "deviceName",
                listOf(Build.MANUFACTURER, Build.MODEL)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Android TV" }
            )

        val json = requestJson("/api/v1/auth/login", method = "POST", body = payload)
        val token = json.optString("accessToken")
        val user = json.optJSONObject("user")
        if (token.isBlank() || user == null) {
            throw AioPlayApiException("The server did not return an app session.")
        }
        return AioPlayLoginResult(
            token = token,
            user = parseUser(user)
        )
    }

    suspend fun account(token: String): AioPlayUser {
        val json = requestJson("/api/v1/account", token = token)
        val user = json.optJSONObject("user")
            ?: throw AioPlayApiException("The server returned no account.")
        return parseUser(user)
    }

    suspend fun logout(token: String) {
        requestJson("/api/v1/auth/logout", method = "POST", token = token, allowEmpty = true)
    }

    suspend fun capabilities(token: String): AioPlayCapabilities {
        val json = requestJson("/api/v1/bootstrap", token = token)
        val services = json.optJSONObject("services")
        val types = buildSet {
            val array = json.optJSONArray("contentTypes")
            if (array != null) {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        return AioPlayCapabilities(
            sportsEnabled = services?.optJSONObject("sports")?.optBoolean("enabled", false) == true,
            vodEnabled = services?.optJSONObject("vod")?.optBoolean("enabled", false) == true,
            contentTypes = types
        )
    }

    suspend fun catalogs(token: String): List<AioPlayCatalog> {
        val json = requestJson("/api/v1/sports/catalogs", token = token)
        val array = json.optJSONArray("catalogs") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val name = row.optString("name")
                val type = row.optString("type")
                if (id.isNotBlank() && name.isNotBlank() && type.isNotBlank()) {
                    add(AioPlayCatalog(id = id, name = name, type = type))
                }
            }
        }
    }

    suspend fun catalog(token: String, catalog: AioPlayCatalog): List<AioPlayItem> {
        val id = encodePath(catalog.id)
        val json = requestJson("/api/v1/sports/catalog/$id", token = token)
        return parseItems(json, catalog.type)
    }

    suspend fun vodCatalogs(token: String): List<AioPlayCatalog> {
        val json = requestJson("/api/v1/vod/catalogs", token = token)
        val array = json.optJSONArray("catalogs") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val name = row.optString("name").ifBlank { id }
                val type = row.optString("type")
                if (id.isBlank() || name.isBlank() || type.isBlank()) continue

                val requiredExtras = buildList {
                    val required = row.optJSONArray("requiredExtras")
                    if (required != null) {
                        for (j in 0 until required.length()) {
                            required.optString(j)
                                .takeIf { it.isNotBlank() }
                                ?.let(::add)
                        }
                    }
                }

                // Preserve AIOMetadata exactly as it presents the manifest:
                // one side-menu entry per catalog, in manifest order.
                add(
                    AioPlayCatalog(
                        id = id,
                        name = name,
                        type = type,
                        requiredExtras = requiredExtras
                    )
                )
            }
        }
    }

    suspend fun vodCatalog(token: String, catalog: AioPlayCatalog): List<AioPlayItem> {
        val type = requireVodType(catalog.type)
        val json = requestJson(
            "/api/v1/vod/catalog/" + encodePath(type) + "/" + encodePath(catalog.id),
            token = token
        )
        return parseItems(json, type)
    }

    suspend fun searchVod(
        token: String,
        query: String,
        type: String? = null
    ): List<AioPlayItem> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()
        val suffix = buildString {
            append("?q=")
            append(URLEncoder.encode(cleanQuery, "UTF-8"))
            if (!type.isNullOrBlank()) {
                append("&type=")
                append(URLEncoder.encode(requireVodType(type), "UTF-8"))
            }
        }
        val json = requestJson("/api/v1/vod/search$suffix", token = token)
        return parseItems(json, type.orEmpty())
    }

    suspend fun vodMeta(
        token: String,
        type: String,
        id: String
    ): AioPlayMetaDetails {
        val safeType = requireVodType(type)
        val json = requestJson(
            "/api/v1/vod/meta/" + encodePath(safeType) + "/" + encodePath(id),
            token = token
        )
        val meta = json.optJSONObject("meta")
            ?: throw AioPlayApiException("The server returned no metadata.")
        val item = parseItem(meta, safeType)
            ?: throw AioPlayApiException("The server returned incomplete metadata.")

        val videos = buildList {
            val rows = meta.optJSONArray("videos")
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val video = rows.optJSONObject(i) ?: continue
                    val videoId = video.optString("id")
                    if (videoId.isBlank()) continue
                    val season = video.optInt("season").takeIf { video.has("season") }
                    val episode = video.optInt("episode").takeIf { video.has("episode") }
                    val title = video.optString("title")
                        .ifBlank { video.optString("name") }
                        .ifBlank {
                            if (episode != null && episode > 0) "Episode $episode" else "Episode"
                        }
                    add(
                        AioPlayVideo(
                            id = videoId,
                            title = title,
                            season = season,
                            episode = episode
                        )
                    )
                }
            }
        }.sortedWith(
            compareBy<AioPlayVideo> { it.season ?: 0 }
                .thenBy { it.episode ?: 0 }
        )
        return AioPlayMetaDetails(item = item, videos = videos)
    }

    suspend fun startPlayback(
        token: String,
        item: AioPlayItem,
        contentType: String = "sport_event",
        stremioType: String? = null
    ): AioPlayPlayback {
        val body = JSONObject()
            .put("contentType", contentType)
            .put("id", item.id)
        if (!stremioType.isNullOrBlank()) body.put("stremioType", stremioType)
        val json = requestJson("/api/v1/play", method = "POST", token = token, body = body)
        return parsePlayback(json)
    }

    suspend fun nextPlayback(token: String, sessionId: String): AioPlayPlayback {
        val encoded = URLEncoder.encode(sessionId, "UTF-8").replace("+", "%20")
        val json = requestJson(
            "/api/v1/playback/$encoded/next",
            method = "POST",
            token = token
        )
        return parsePlayback(json)
    }

    suspend fun finishPlayback(token: String, sessionId: String) {
        val encoded = URLEncoder.encode(sessionId, "UTF-8").replace("+", "%20")
        requestJson(
            "/api/v1/playback/$encoded",
            method = "DELETE",
            token = token,
            allowEmpty = true
        )
    }

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun requireVodType(type: String): String {
        val value = type.trim().lowercase()
        if (value != "movie" && value != "series") {
            throw AioPlayApiException("VOD type must be movie or series.")
        }
        return value
    }

    private fun parseItems(json: JSONObject, fallbackType: String): List<AioPlayItem> {
        val array = json.optJSONArray("metas")
            ?: json.optJSONArray("metasDetailed")
            ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                parseItem(array.optJSONObject(i), fallbackType)?.let(::add)
            }
        }
    }

    private fun parseItem(json: JSONObject?, fallbackType: String): AioPlayItem? {
        if (json == null) return null
        val id = json.optString("id")
        val name = json.optString("name").ifBlank { json.optString("title") }
        if (id.isBlank() || name.isBlank()) return null
        return AioPlayItem(
            id = id,
            type = json.optString("type").ifBlank { fallbackType },
            name = name,
            description = json.optString("description").takeIf { it.isNotBlank() },
            poster = json.optString("poster").takeIf { it.isNotBlank() },
            background = json.optString("background").takeIf { it.isNotBlank() },
            logo = json.optString("logo").takeIf { it.isNotBlank() }
        )
    }

    private fun parseUser(json: JSONObject): AioPlayUser = AioPlayUser(
        id = json.optString("id"),
        username = json.optString("username"),
        displayName = json.optString("displayName").ifBlank { json.optString("username") },
        role = json.optString("role").ifBlank { "user" }
    )

    private fun parsePlayback(json: JSONObject): AioPlayPlayback {
        val sessionId = json.optString("sessionId")
        val playback = json.optJSONObject("playback")
            ?: throw AioPlayApiException(
                if (json.optBoolean("exhausted", false)) {
                    "No playable source is available."
                } else {
                    "The server returned no playable target."
                }
            )
        val url = playback.optString("url")
        if (sessionId.isBlank() || url.isBlank()) {
            throw AioPlayApiException("The server returned an incomplete playback target.")
        }
        val headers = buildMap {
            val objectHeaders = playback.optJSONObject("requestHeaders")
            if (objectHeaders != null) {
                val keys = objectHeaders.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    objectHeaders.optString(key).takeIf { it.isNotBlank() }?.let { put(key, it) }
                }
            }
        }
        return AioPlayPlayback(
            sessionId = sessionId,
            target = AioPlayPlaybackTarget(
                kind = playback.optString("kind").ifBlank { "direct" },
                url = url,
                requestHeaders = headers
            )
        )
    }
}
