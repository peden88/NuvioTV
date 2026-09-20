package com.nuvio.tv.aiosport

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

class AioSportApiException(
    message: String,
    val statusCode: Int? = null
) : Exception(message)

@Singleton
class AioSportApiClient @Inject constructor(
    @param:Named("customServerAuth") private val http: OkHttpClient
) {
    private val baseUrl: String
        get() = BuildConfig.AIOSPORT_API_BASE_URL.trim().trimEnd('/')

    fun isConfigured(): Boolean {
        if (baseUrl.isBlank()) return false
        return runCatching {
            val uri = java.net.URI(baseUrl)
            (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun requireBaseUrl(): String {
        if (!isConfigured()) {
            throw AioSportApiException(
                "AIOSport server URL is not configured in this APK."
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
                throw AioSportApiException(message, httpResponse.code)
            }
            if (allowEmpty && raw.isBlank()) return@use JSONObject()
            if (raw.isBlank()) return@use JSONObject()
            return@use JSONObject(raw)
        }
    }

    suspend fun login(username: String, password: String): AioSportLoginResult {
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

        val json = requestJson("/api/login", method = "POST", body = payload)
        val token = json.optString("token")
        val user = json.optJSONObject("user")
        if (token.isBlank() || user == null) {
            throw AioSportApiException("The server did not return an app session.")
        }
        return AioSportLoginResult(
            token = token,
            user = parseUser(user)
        )
    }

    suspend fun account(token: String): AioSportUser {
        val json = requestJson("/api/v1/account", token = token)
        val user = json.optJSONObject("user")
            ?: throw AioSportApiException("The server returned no account.")
        return parseUser(user)
    }

    suspend fun logout(token: String) {
        requestJson("/api/logout", method = "POST", token = token, allowEmpty = true)
    }

    suspend fun capabilities(token: String): AioSportCapabilities {
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
        return AioSportCapabilities(
            sportsEnabled = services?.optJSONObject("sports")?.optBoolean("enabled", false) == true,
            vodEnabled = services?.optJSONObject("vod")?.optBoolean("enabled", false) == true,
            contentTypes = types
        )
    }

    suspend fun catalogs(token: String): List<AioSportCatalog> {
        val json = requestJson("/manifest.json", token = token)
        val array = json.optJSONArray("catalogs") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val id = row.optString("id")
                val name = row.optString("name")
                val type = row.optString("type")
                if (id.isNotBlank() && name.isNotBlank() && type.isNotBlank()) {
                    add(AioSportCatalog(id = id, name = name, type = type))
                }
            }
        }
    }

    suspend fun catalog(token: String, catalog: AioSportCatalog): List<AioSportItem> {
        val id = URLEncoder.encode(catalog.id, "UTF-8").replace("+", "%20")
        val type = URLEncoder.encode(catalog.type, "UTF-8").replace("+", "%20")
        val json = requestJson("/catalog/$type/$id.json", token = token)
        val array = json.optJSONArray("metas") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val itemId = row.optString("id")
                val name = row.optString("name")
                if (itemId.isBlank() || name.isBlank()) continue
                add(
                    AioSportItem(
                        id = itemId,
                        type = row.optString("type").ifBlank { catalog.type },
                        name = name,
                        description = row.optString("description").takeIf { it.isNotBlank() },
                        poster = row.optString("poster").takeIf { it.isNotBlank() },
                        background = row.optString("background").takeIf { it.isNotBlank() },
                        logo = row.optString("logo").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    suspend fun startPlayback(
        token: String,
        item: AioSportItem,
        contentType: String = "sport_event",
        stremioType: String? = null
    ): AioSportPlayback {
        val body = JSONObject()
            .put("contentType", contentType)
            .put("id", item.id)
        if (!stremioType.isNullOrBlank()) body.put("stremioType", stremioType)
        val json = requestJson("/api/v1/play", method = "POST", token = token, body = body)
        return parsePlayback(json)
    }

    suspend fun nextPlayback(token: String, sessionId: String): AioSportPlayback {
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

    private fun parseUser(json: JSONObject): AioSportUser = AioSportUser(
        id = json.optString("id"),
        username = json.optString("username"),
        displayName = json.optString("displayName").ifBlank { json.optString("username") },
        role = json.optString("role").ifBlank { "user" }
    )

    private fun parsePlayback(json: JSONObject): AioSportPlayback {
        val sessionId = json.optString("sessionId")
        val playback = json.optJSONObject("playback")
            ?: throw AioSportApiException(
                if (json.optBoolean("exhausted", false)) {
                    "No playable source is available."
                } else {
                    "The server returned no playable target."
                }
            )
        val url = playback.optString("url")
        if (sessionId.isBlank() || url.isBlank()) {
            throw AioSportApiException("The server returned an incomplete playback target.")
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
        return AioSportPlayback(
            sessionId = sessionId,
            target = AioSportPlaybackTarget(
                kind = playback.optString("kind").ifBlank { "direct" },
                url = url,
                requestHeaders = headers
            )
        )
    }
}
