package com.nuvio.tv.aioplay

import com.nuvio.tv.domain.model.Meta

data class AioPlayUser(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String
)

data class AioPlayLoginResult(
    val token: String,
    val user: AioPlayUser
)

data class AioPlayCapabilities(
    val sportsEnabled: Boolean,
    val vodEnabled: Boolean,
    val contentTypes: Set<String>
)

data class AioPlayCatalog(
    val id: String,
    val name: String,
    val type: String,
    val requiredExtras: List<String> = emptyList(),
    val extras: Map<String, String> = emptyMap()
) {
    val selectionKey: String
        get() = buildString {
            append(type)
            append(':')
            append(id)
            extras.toSortedMap().forEach { (key, value) ->
                append('|')
                append(key)
                append('=')
                append(value)
            }
        }
}

data class AioPlayItem(
    val id: String,
    val type: String,
    val name: String,
    val description: String?,
    val poster: String?,
    val background: String?,
    val logo: String?
)

data class AioPlayPlaybackTarget(
    val kind: String,
    val url: String,
    val requestHeaders: Map<String, String>
)

data class AioPlayPlayback(
    val sessionId: String,
    val target: AioPlayPlaybackTarget
)

data class AioPlayVideo(
    val id: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val released: String? = null,
    val thumbnail: String? = null,
    val overview: String? = null,
    val runtime: Int? = null,
    val rating: Double? = null
)

data class AioPlayMetaDetails(
    val item: AioPlayItem,
    val videos: List<AioPlayVideo>,
    val meta: Meta
)
