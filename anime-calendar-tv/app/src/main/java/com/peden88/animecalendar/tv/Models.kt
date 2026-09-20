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
    val mutatingIds: Set<Int> = emptySet(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null
)
