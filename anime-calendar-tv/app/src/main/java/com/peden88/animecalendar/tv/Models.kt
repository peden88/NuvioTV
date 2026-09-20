package com.peden88.animecalendar.tv

data class AnimeItem(
    val anilistId: Int,
    val title: String,
    val romajiTitle: String? = null,
    val nativeTitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val description: String? = null,
    val format: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val totalEpisodes: Int? = null,
    val episodeNumber: Int? = null,
    val airingAtEpochSeconds: Long? = null,
    val scheduleLabel: String? = null,
    val selected: Boolean = false
) {
    val displayTitle: String
        get() = title.ifBlank { romajiTitle ?: nativeTitle ?: "Untitled" }
}

data class NuvioResolution(
    val contentId: String,
    val contentType: String,
    val status: String? = null,
    val title: String? = null
)

data class AnimeCalendarSnapshot(
    val watchlist: List<AnimeItem>,
    val calendar: List<AnimeItem>,
    val upcoming: List<AnimeItem>,
    val currentSeason: List<AnimeItem>,
    val resolutions: Map<Int, NuvioResolution>
)

enum class CalendarTab(val label: String) {
    WATCHLIST("Watching"),
    CALENDAR("Calendar"),
    UPCOMING("Upcoming"),
    BROWSE("Browse")
}

data class AnimeCalendarUiState(
    val watchlist: List<AnimeItem> = emptyList(),
    val calendar: List<AnimeItem> = emptyList(),
    val upcoming: List<AnimeItem> = emptyList(),
    val currentSeason: List<AnimeItem> = emptyList(),
    val watchlistIds: Set<Int> = emptySet(),
    val resolutions: Map<Int, NuvioResolution> = emptyMap(),
    val mutatingIds: Set<Int> = emptySet(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val needsConfiguration: Boolean = false,
    val apiBaseUrl: String = BuildConfig.ANIME_CALENDAR_TV_API_BASE_URL,
    val error: String? = null
)
