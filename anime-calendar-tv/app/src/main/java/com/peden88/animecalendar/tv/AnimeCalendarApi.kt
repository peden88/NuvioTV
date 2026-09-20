package com.peden88.animecalendar.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class AnimeCalendarApi(
    baseUrl: String = BuildConfig.ANIME_CALENDAR_BASE_URL
) {
    private val root = baseUrl.trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun watchlist(): List<AnimeItem> = getItems("/api/watchlist")
    suspend fun calendar(): List<AnimeItem> = getItems("/api/calendar")
    suspend fun upcoming(): List<AnimeItem> = getItems("/api/seasons/upcoming")
    suspend fun currentSeason(): List<AnimeItem> = getItems("/api/seasons/current")

    suspend fun setWatchlisted(anilistId: Int, selected: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (selected) {
            attempt("PATCH", "/api/watchlist/$anilistId", JSONObject().put("selected", true).toString()) ||
                attempt(
                    "POST",
                    "/api/watchlist",
                    JSONObject().put("anilist_id", anilistId).put("selected", true).toString()
                ) ||
                attempt(
                    "POST",
                    "/api/watchlist/bulk",
                    JSONObject()
                        .put("anilist_ids", JSONArray().put(anilistId))
                        .put("selected", true)
                        .toString()
                )
        } else {
            attempt("DELETE", "/api/watchlist/$anilistId", null) ||
                attempt("PATCH", "/api/watchlist/$anilistId", JSONObject().put("selected", false).toString()) ||
                attempt(
                    "POST",
                    "/api/watchlist",
                    JSONObject().put("anilist_id", anilistId).put("selected", false).toString()
                )
        }
    }

    private suspend fun getItems(path: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(root + path)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("${response.code} ${response.message}: ${body.take(240)}")
            }
            parseAnimePayload(body)
        }
    }

    private fun attempt(method: String, path: String, body: String?): Boolean {
        return runCatching {
            val builder = Request.Builder()
                .url(root + path)
                .header("Accept", "application/json")
            when (method) {
                "POST" -> builder.post((body ?: "{}").toRequestBody(jsonType))
                "PATCH" -> builder.patch((body ?: "{}").toRequestBody(jsonType))
                "DELETE" -> builder.delete()
                else -> return false
            }
            client.newCall(builder.build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}

internal fun parseAnimePayload(raw: String): List<AnimeItem> {
    if (raw.isBlank()) return emptyList()
    val root = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: return emptyList()
    val collected = mutableListOf<AnimeItem>()
    collectAnime(root, inheritedLabel = null, output = collected)
    return collected
        .filter { it.anilistId > 0 && it.displayTitle.isNotBlank() }
        .distinctBy { it.anilistId to (it.episodeNumber ?: -1) to (it.airingAtEpochSeconds ?: -1L) }
}

private fun collectAnime(node: Any?, inheritedLabel: String?, output: MutableList<AnimeItem>) {
    when (node) {
        is JSONArray -> {
            for (index in 0 until node.length()) {
                collectAnime(node.opt(index), inheritedLabel, output)
            }
        }
        is JSONObject -> {
            parseAnimeObject(node, inheritedLabel)?.let {
                output += it
                return
            }
            val keys = node.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val child = node.opt(key)
                if (child is JSONArray || child is JSONObject) {
                    val label = if (looksLikeCalendarLabel(key)) key else inheritedLabel
                    collectAnime(child, label, output)
                }
            }
        }
    }
}

private fun parseAnimeObject(wrapper: JSONObject, inheritedLabel: String?): AnimeItem? {
    val source = wrapper.optJSONObject("anime")
        ?: wrapper.optJSONObject("media")
        ?: wrapper.optJSONObject("title_data")
        ?: wrapper

    val id = firstInt(source, "anilist_id", "anilistId", "aniListId", "id")
        ?: firstInt(wrapper, "anilist_id", "anilistId", "aniListId", "id")
        ?: return null

    val titleObject = source.optJSONObject("title")
    val english = titleObject?.optString("english").clean()
        ?: firstString(source, "english_title", "title_english", "english")
    val romaji = titleObject?.optString("romaji").clean()
        ?: firstString(source, "romaji_title", "title_romaji", "romaji")
    val native = titleObject?.optString("native").clean()
        ?: firstString(source, "native_title", "title_native")
    val simpleTitle = when (val rawTitle = source.opt("title")) {
        is String -> rawTitle.clean()
        else -> null
    } ?: firstString(source, "name", "display_title")

    val title = english ?: simpleTitle ?: romaji ?: native ?: return null

    val cover = source.optJSONObject("coverImage")
    val poster = cover?.optString("extraLarge").clean()
        ?: cover?.optString("large").clean()
        ?: cover?.optString("medium").clean()
        ?: firstString(source, "poster", "poster_url", "image", "image_url", "cover", "cover_image")
    val backdrop = source.optJSONObject("bannerImage")?.optString("large").clean()
        ?: firstString(source, "bannerImage", "banner_image", "backdrop", "backdrop_url")

    val nextAiring = source.optJSONObject("nextAiringEpisode") ?: wrapper.optJSONObject("nextAiringEpisode")
    val airingAt = firstLong(wrapper, "airing_at", "airingAt", "airing_epoch", "timestamp")
        ?: firstLong(nextAiring, "airingAt", "airing_at")
    val episode = firstInt(wrapper, "episode", "episode_number", "episodeNumber")
        ?: firstInt(nextAiring, "episode")
    val explicitDate = firstString(wrapper, "air_date", "airDate", "date", "datetime", "airing_time")
    val derivedLabel = explicitDate
        ?: airingAt?.let(::formatAiringLabel)
        ?: inheritedLabel

    return AnimeItem(
        anilistId = id,
        title = title,
        romajiTitle = romaji,
        nativeTitle = native,
        posterUrl = poster,
        backdropUrl = backdrop,
        description = firstString(source, "description", "synopsis", "overview")?.stripHtml(),
        format = firstString(source, "format", "type"),
        status = firstString(source, "status", "airing_status"),
        year = firstInt(source, "year", "seasonYear", "season_year"),
        totalEpisodes = firstInt(source, "episodes", "episode_count", "total_episodes"),
        episodeNumber = episode,
        airingAtEpochSeconds = airingAt,
        scheduleLabel = derivedLabel,
        selected = firstBoolean(wrapper, "selected", "watchlisted", "in_watchlist")
            ?: firstBoolean(source, "selected", "watchlisted", "in_watchlist")
            ?: false
    )
}

private fun firstString(obj: JSONObject?, vararg names: String): String? {
    if (obj == null) return null
    for (name in names) {
        val value = obj.opt(name)
        if (value is String) value.clean()?.let { return it }
        if (value is Number) return value.toString()
    }
    return null
}

private fun firstInt(obj: JSONObject?, vararg names: String): Int? {
    if (obj == null) return null
    for (name in names) {
        val value = obj.opt(name)
        when (value) {
            is Number -> return value.toInt()
            is String -> value.trim().toIntOrNull()?.let { return it }
        }
    }
    return null
}

private fun firstLong(obj: JSONObject?, vararg names: String): Long? {
    if (obj == null) return null
    for (name in names) {
        val value = obj.opt(name)
        when (value) {
            is Number -> return value.toLong()
            is String -> value.trim().toLongOrNull()?.let { return it }
        }
    }
    return null
}

private fun firstBoolean(obj: JSONObject?, vararg names: String): Boolean? {
    if (obj == null) return null
    for (name in names) {
        when (val value = obj.opt(name)) {
            is Boolean -> return value
            is Number -> return value.toInt() != 0
            is String -> when (value.trim().lowercase()) {
                "true", "yes", "1", "selected" -> return true
                "false", "no", "0", "unselected" -> return false
            }
        }
    }
    return null
}

private fun String?.clean(): String? = this
    ?.trim()
    ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

private fun String.stripHtml(): String = this
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .trim()

private fun looksLikeCalendarLabel(value: String): Boolean {
    val lower = value.lowercase()
    return lower.matches(Regex("\\d{4}-\\d{1,2}-\\d{1,2}")) ||
        listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "today", "tomorrow").any(lower::contains)
}

private fun formatAiringLabel(epochSeconds: Long): String {
    return runCatching {
        val instant = Instant.ofEpochSecond(epochSeconds)
        val formatter = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    }.getOrDefault("")
}
