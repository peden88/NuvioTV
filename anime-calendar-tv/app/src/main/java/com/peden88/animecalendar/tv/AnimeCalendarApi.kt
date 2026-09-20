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
    baseUrl: String,
    private val token: String
) {
    private val root = baseUrl.trim().trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun bootstrap(): AnimeCalendarSnapshot = withContext(Dispatchers.IO) {
        val rootObject = getObject("/api/tv/anime-calendar/bootstrap")
        val watchlist = parseAnimeNode(rootObject.opt("watchlist"))
        val watchlistIds = watchlist.mapTo(mutableSetOf()) { it.anilistId }
        fun mark(items: List<AnimeItem>) = items.map { item ->
            item.copy(selected = item.selected || item.anilistId in watchlistIds)
        }

        AnimeCalendarSnapshot(
            watchlist = mark(watchlist).sortedBy { it.displayTitle.lowercase() },
            calendar = mark(parseAnimeNode(rootObject.opt("calendar"))),
            upcoming = mark(parseAnimeNode(rootObject.opt("upcoming")))
                .sortedWith(
                    compareBy<AnimeItem> { startDateSortKey(it.startDateLabel) }
                        .thenBy { it.displayTitle.lowercase() }
                ),
            currentSeason = mark(parseAnimeNode(rootObject.opt("current"))),
            resolutions = parseResolutions(rootObject.optJSONObject("resolutions"))
        )
    }

    suspend fun toggleWatchlist(anilistId: Int): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("id", anilistId).toString()
        request(
            method = "POST",
            path = "/api/tv/anime-calendar/watchlist/toggle",
            body = body
        )
        true
    }

    suspend fun detail(anilistId: Int): Pair<AnimeItem?, NuvioResolution?> = withContext(Dispatchers.IO) {
        val payload = getObject("/api/tv/anime-calendar/anime/$anilistId")
        val parsed = parseAnimeNode(payload.opt("anime"))
        val item = parsed.firstOrNull { it.anilistId == anilistId } ?: parsed.firstOrNull()
        val resolution = payload.optJSONObject("nuvio")?.let(::parseResolution)
        item to resolution
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        getObject("/api/tv/anime-calendar/health").optBoolean("ok", false)
    }

    private fun getObject(path: String): JSONObject {
        val raw = request("GET", path, null)
        return JSONTokener(raw).nextValue() as? JSONObject
            ?: error("Unexpected Anime Calendar TV response")
    }

    private fun request(method: String, path: String, body: String?): String {
        val builder = Request.Builder()
            .url(root + path)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")

        when (method) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonType))
            else -> builder.get()
        }

        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("${response.code} ${response.message}: ${responseBody.take(280)}")
            }
            return responseBody
        }
    }
}

internal fun parseAnimeNode(node: Any?): List<AnimeItem> {
    val collected = mutableListOf<AnimeItem>()
    collectAnime(node, inheritedLabel = null, output = collected)
    return collected
        .filter { it.anilistId > 0 && it.displayTitle.isNotBlank() }
        .distinctBy { it.anilistId to (it.episodeNumber ?: -1) to (it.airingAtEpochSeconds ?: -1L) }
}

internal fun parseAnimePayload(raw: String): List<AnimeItem> {
    if (raw.isBlank()) return emptyList()
    val root = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: return emptyList()
    return parseAnimeNode(root)
}

private fun parseResolutions(source: JSONObject?): Map<Int, NuvioResolution> {
    if (source == null) return emptyMap()
    val result = linkedMapOf<Int, NuvioResolution>()
    val keys = source.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val id = key.toIntOrNull() ?: continue
        val resolution = source.optJSONObject(key)?.let(::parseResolution) ?: continue
        result[id] = resolution
    }
    return result
}

private fun parseResolution(source: JSONObject): NuvioResolution? {
    val contentId = source.optString("contentId").clean() ?: return null
    val contentType = source.optString("contentType").clean() ?: return null
    return NuvioResolution(
        contentId = contentId,
        contentType = contentType,
        status = source.optString("status").clean(),
        title = source.optString("title").clean()
    )
}

private fun collectAnime(node: Any?, inheritedLabel: String?, output: MutableList<AnimeItem>) {
    when (node) {
        is JSONArray -> for (index in 0 until node.length()) {
            collectAnime(node.opt(index), inheritedLabel, output)
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
    val backdrop = firstString(source, "bannerImage", "banner_image", "backdrop", "backdrop_url")

    val nextAiring = source.optJSONObject("nextAiringEpisode") ?: wrapper.optJSONObject("nextAiringEpisode")
    val airingAt = firstLong(wrapper, "airing_at", "airingAt", "airing_epoch", "timestamp")
        ?: firstLong(nextAiring, "airingAt", "airing_at")
    val episode = firstInt(wrapper, "episode", "episode_number", "episodeNumber")
        ?: firstInt(nextAiring, "episode")
    val explicitDate = firstString(wrapper, "air_date", "airDate", "date", "datetime", "airing_time")
    val derivedLabel = explicitDate ?: airingAt?.let(::formatAiringLabel) ?: inheritedLabel

    val startDateObject = source.optJSONObject("startDate")
        ?: source.optJSONObject("start_date")
    val startDateLabel = firstString(
        source,
        "start_date",
        "startDate",
        "premiere_date",
        "premiereDate"
    ) ?: startDateObject?.let(::formatStartDateObject)

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
        startDateLabel = startDateLabel,
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
        when (val value = obj.opt(name)) {
            is Number -> return value.toInt()
            is String -> value.trim().toIntOrNull()?.let { return it }
        }
    }
    return null
}

private fun firstLong(obj: JSONObject?, vararg names: String): Long? {
    if (obj == null) return null
    for (name in names) {
        when (val value = obj.opt(name)) {
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
        listOf(
            "monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday", "today", "tomorrow"
        ).any(lower::contains)
}

private fun startDateSortKey(raw: String?): String {
    if (raw.isNullOrBlank()) return "9999-12-31"

    val value = raw.trim()

    return when {
        value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> value
        value.matches(Regex("\\d{4}-\\d{2}")) -> "$value-01"
        value.matches(Regex("\\d{4}")) -> "$value-01-01"
        value.length >= 10 && value.substring(0, 10)
            .matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> value.substring(0, 10)
        else -> "9999-12-31"
    }
}

private fun formatStartDateObject(source: JSONObject): String? {
    val year = firstInt(source, "year") ?: return null
    val month = firstInt(source, "month") ?: return year.toString()
    val day = firstInt(source, "day")
    return if (day != null) {
        "%04d-%02d-%02d".format(year, month, day)
    } else {
        "%04d-%02d".format(year, month)
    }
}

private fun formatAiringLabel(epochSeconds: Long): String {
    return runCatching {
        val instant = Instant.ofEpochSecond(epochSeconds)
        val formatter = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    }.getOrDefault("")
}
