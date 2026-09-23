package com.nuvio.tv.aioplay

import android.os.Build
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaBehaviorHints
import com.nuvio.tv.domain.model.MetaCastMember
import com.nuvio.tv.domain.model.MetaCompany
import com.nuvio.tv.domain.model.MetaLink
import com.nuvio.tv.domain.model.MetaTrailer
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
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
import org.json.JSONArray
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
            .header("X-AIOPlay-Client", "android-tv")

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

    suspend fun progress(token: String): List<WatchProgress> {
        val json = requestJson("/api/v1/progress", token = token)
        val rows = json.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val contentId = row.optString("contentId").trim()
                val videoId = row.optString("videoId").trim().ifBlank { contentId }
                val name = row.optString("name").trim()
                if (contentId.isBlank() || videoId.isBlank() || name.isBlank()) continue

                val position = row.optLong("position", 0L).coerceAtLeast(0L)
                val duration = row.optLong("duration", 0L).coerceAtLeast(0L)
                val explicitPercent = row.optNullableDouble("progressPercent")
                    ?.toFloat()
                    ?.coerceIn(0f, 100f)

                add(
                    WatchProgress(
                        contentId = contentId,
                        contentType = when (row.optString("contentType").trim().lowercase()) {
                            "tv", "episode", "series" -> "series"
                            else -> "movie"
                        },
                        name = name,
                        poster = row.optNonBlankString("poster"),
                        backdrop = row.optNonBlankString("backdrop"),
                        logo = row.optNonBlankString("logo"),
                        videoId = videoId,
                        season = row.optNullableInt("season"),
                        episode = row.optNullableInt("episode"),
                        episodeTitle = row.optNonBlankString("episodeTitle"),
                        position = position,
                        duration = duration,
                        lastWatched = row.optLong("lastWatched", 0L).coerceAtLeast(0L),
                        progressPercent = explicitPercent,
                        source = row.optNonBlankString("source") ?: "aioplay_shared"
                    )
                )
            }
        }
    }

    suspend fun pushProgress(token: String, progress: List<WatchProgress>) {
        if (progress.isEmpty()) return
        val rows = JSONArray()
        progress.take(500).forEach { item ->
            rows.put(
                JSONObject()
                    .put("contentId", item.contentId)
                    .put("contentType", item.contentType)
                    .put("name", item.name)
                    .put("poster", item.poster ?: JSONObject.NULL)
                    .put("backdrop", item.backdrop ?: JSONObject.NULL)
                    .put("logo", item.logo ?: JSONObject.NULL)
                    .put("videoId", item.videoId)
                    .put("season", item.season ?: JSONObject.NULL)
                    .put("episode", item.episode ?: JSONObject.NULL)
                    .put("episodeTitle", item.episodeTitle ?: JSONObject.NULL)
                    .put("position", item.position.coerceAtLeast(0L))
                    .put("duration", item.duration.coerceAtLeast(0L))
                    .put("lastWatched", item.lastWatched.coerceAtLeast(0L))
                    .put("progressPercent", item.progressPercentage.coerceIn(0f, 1f) * 100f)
                    .put("source", "aioplay_tv")
            )
        }
        requestJson(
            "/api/v1/progress",
            method = "PUT",
            token = token,
            body = JSONObject().put("items", rows)
        )
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
        val metaJson = json.optJSONObject("meta")
            ?: throw AioPlayApiException("The server returned no metadata.")
        val item = parseItem(metaJson, safeType)
            ?: throw AioPlayApiException("The server returned incomplete metadata.")

        val domainVideos = buildList {
            val rows = metaJson.optJSONArray("videos")
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val video = rows.optJSONObject(i) ?: continue
                    val videoId = video.optString("id")
                    if (videoId.isBlank()) continue
                    val season = video.optNullableInt("season")
                    val episode = video.optNullableInt("episode")
                    val title = video.optString("title")
                        .ifBlank { video.optString("name") }
                        .ifBlank {
                            if (episode != null && episode > 0) "Episode $episode" else "Episode"
                        }
                    add(
                        Video(
                            id = videoId,
                            title = title,
                            released = video.optNonBlankString("released"),
                            thumbnail = video.optNonBlankString("thumbnail")
                                ?: video.optNonBlankString("poster"),
                            season = season,
                            episode = episode,
                            overview = video.optNonBlankString("overview")
                                ?: video.optNonBlankString("description"),
                            runtime = video.optNullableInt("runtime"),
                            rating = video.optNullableDouble("rating"),
                            available = video.optNullableBoolean("available")
                        )
                    )
                }
            }
        }.sortedWith(
            compareBy<Video> { it.season ?: 0 }
                .thenBy { it.episode ?: 0 }
        )

        val videos = domainVideos.map { video ->
            AioPlayVideo(
                id = video.id,
                title = video.title,
                season = video.season,
                episode = video.episode,
                released = video.released,
                thumbnail = video.thumbnail,
                overview = video.overview,
                runtime = video.runtime,
                rating = video.rating
            )
        }

        val richMeta = Meta(
            id = item.id,
            type = ContentType.fromString(safeType),
            rawType = safeType,
            name = item.name,
            poster = item.poster,
            posterShape = PosterShape.fromString(metaJson.optNonBlankString("posterShape")),
            background = item.background,
            logo = item.logo,
            description = item.description,
            releaseInfo = metaJson.optNonBlankString("releaseInfo")
                ?: metaJson.optNonBlankString("year"),
            status = metaJson.optNonBlankString("status"),
            imdbRating = metaJson.optNullableDouble("imdbRating")?.toFloat(),
            genres = metaJson.stringList("genres"),
            runtime = metaJson.optNonBlankString("runtime"),
            director = metaJson.stringList("director"),
            writer = metaJson.stringList("writer"),
            cast = metaJson.stringList("cast"),
            castMembers = metaJson.castMembers(),
            videos = domainVideos,
            productionCompanies = metaJson.companies("productionCompanies"),
            networks = metaJson.companies("networks"),
            ageRating = metaJson.optNonBlankString("ageRating"),
            country = metaJson.optNonBlankString("country"),
            awards = metaJson.optNonBlankString("awards"),
            language = metaJson.optNonBlankString("language"),
            links = metaJson.metaLinks(),
            trailerYtIds = metaJson.stringList("trailerYtIds"),
            imdbId = metaJson.optNonBlankString("imdbId"),
            slug = metaJson.optNonBlankString("slug"),
            released = metaJson.optNonBlankString("released"),
            landscapePoster = metaJson.optNonBlankString("landscapePoster"),
            rawPosterUrl = metaJson.optNonBlankString("rawPosterUrl"),
            behaviorHints = metaJson.behaviorHints(),
            trailers = metaJson.trailers(),
            hasPoster = metaJson.optNullableBoolean("hasPoster"),
            hasBackground = metaJson.optNullableBoolean("hasBackground"),
            hasLandscapePoster = metaJson.optNullableBoolean("hasLandscapePoster"),
            hasLogo = metaJson.optNullableBoolean("hasLogo"),
            hasLinks = metaJson.optNullableBoolean("hasLinks"),
            hasVideos = metaJson.optNullableBoolean("hasVideos")
        )

        return AioPlayMetaDetails(
            item = item,
            videos = videos,
            meta = richMeta
        )
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
            .put("title", item.name)
            .put("parentId", item.parentId ?: JSONObject.NULL)
            .put("parentName", item.parentName ?: JSONObject.NULL)
            .put("season", item.season ?: JSONObject.NULL)
            .put("episode", item.episode ?: JSONObject.NULL)
            .put("episodeTitle", item.episodeTitle ?: JSONObject.NULL)
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

    suspend fun heartbeatPlayback(token: String, sessionId: String) {
        val encoded = URLEncoder.encode(sessionId, "UTF-8").replace("+", "%20")
        requestJson(
            "/api/v1/playback/$encoded/heartbeat",
            method = "POST",
            token = token
        )
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

    private fun JSONObject.optNonBlankString(key: String): String? =
        optString(key).trim().takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)
        val parsed = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
        return parsed?.takeIf { it.isFinite() }
    }

    private fun JSONObject.optNullableBoolean(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val value = opt(key)
        return when (value) {
            is JSONArray -> buildList {
                for (i in 0 until value.length()) {
                    when (val entry = value.opt(i)) {
                        is String -> entry.trim().takeIf { it.isNotBlank() }?.let(::add)
                        is JSONObject -> entry.optNonBlankString("name")?.let(::add)
                    }
                }
            }
            is String -> value
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun JSONObject.castMembers(): List<MetaCastMember> {
        val value = optJSONArray("castMembers") ?: return emptyList()
        return buildList {
            for (i in 0 until value.length()) {
                val row = value.optJSONObject(i) ?: continue
                val name = row.optNonBlankString("name") ?: continue
                add(
                    MetaCastMember(
                        name = name,
                        character = row.optNonBlankString("character"),
                        photo = row.optNonBlankString("photo")
                            ?: row.optNonBlankString("profilePath"),
                        tmdbId = row.optNullableInt("tmdbId") ?: row.optNullableInt("id")
                    )
                )
            }
        }
    }

    private fun JSONObject.companies(key: String): List<MetaCompany> {
        val value = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until value.length()) {
                when (val row = value.opt(i)) {
                    is String -> row.trim().takeIf { it.isNotBlank() }?.let {
                        add(MetaCompany(name = it))
                    }
                    is JSONObject -> {
                        val name = row.optNonBlankString("name") ?: continue
                        add(
                            MetaCompany(
                                name = name,
                                logo = row.optNonBlankString("logo")
                                    ?: row.optNonBlankString("logoPath"),
                                tmdbId = row.optNullableInt("tmdbId") ?: row.optNullableInt("id")
                            )
                        )
                    }
                }
            }
        }
    }

    private fun JSONObject.metaLinks(): List<MetaLink> {
        val value = optJSONArray("links") ?: return emptyList()
        return buildList {
            for (i in 0 until value.length()) {
                val row = value.optJSONObject(i) ?: continue
                val url = row.optNonBlankString("url") ?: continue
                add(
                    MetaLink(
                        name = row.optNonBlankString("name").orEmpty(),
                        category = row.optNonBlankString("category").orEmpty(),
                        url = url
                    )
                )
            }
        }
    }

    private fun JSONObject.trailers(): List<MetaTrailer> {
        val value = optJSONArray("trailers") ?: return emptyList()
        return buildList {
            for (i in 0 until value.length()) {
                val row = value.optJSONObject(i) ?: continue
                add(
                    MetaTrailer(
                        source = row.optNonBlankString("source"),
                        type = row.optNonBlankString("type"),
                        name = row.optNonBlankString("name"),
                        ytId = row.optNonBlankString("ytId")
                            ?: row.optNonBlankString("youtubeId"),
                        lang = row.optNonBlankString("lang")
                    )
                )
            }
        }
    }

    private fun JSONObject.behaviorHints(): MetaBehaviorHints? {
        val row = optJSONObject("behaviorHints") ?: return null
        return MetaBehaviorHints(
            defaultVideoId = row.optNonBlankString("defaultVideoId"),
            hasScheduledVideos = row.optNullableBoolean("hasScheduledVideos")
        )
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
            logo = json.optString("logo").takeIf { it.isNotBlank() },
            releaseInfo = json.optNonBlankString("releaseInfo")
                ?: json.optNonBlankString("year"),
            imdbRating = json.optNullableDouble("imdbRating")
                ?: json.optNullableDouble("rating"),
            genres = json.stringList("genres"),
            runtime = json.optNonBlankString("runtime")
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
