package com.nuvio.tv.aiosport

data class AioSportUser(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String
)

data class AioSportLoginResult(
    val token: String,
    val user: AioSportUser
)

data class AioSportCapabilities(
    val sportsEnabled: Boolean,
    val vodEnabled: Boolean,
    val contentTypes: Set<String>
)

data class AioSportCatalog(
    val id: String,
    val name: String,
    val type: String
)

data class AioSportItem(
    val id: String,
    val type: String,
    val name: String,
    val description: String?,
    val poster: String?,
    val background: String?,
    val logo: String?
)

data class AioSportPlaybackTarget(
    val kind: String,
    val url: String,
    val requestHeaders: Map<String, String>
)

data class AioSportPlayback(
    val sessionId: String,
    val target: AioSportPlaybackTarget
)
