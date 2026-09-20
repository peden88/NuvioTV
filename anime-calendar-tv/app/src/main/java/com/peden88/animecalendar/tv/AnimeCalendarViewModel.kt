package com.peden88.animecalendar.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnimeCalendarViewModel : ViewModel() {
    private val api = AnimeCalendarApi()
    private val _state = MutableStateFlow(AnimeCalendarUiState())
    val state: StateFlow<AnimeCalendarUiState> = _state.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = initial && it.watchlist.isEmpty() && it.calendar.isEmpty(),
                    refreshing = !initial,
                    error = null
                )
            }

            val results = listOf(
                async { runCatching { api.watchlist() } },
                async { runCatching { api.calendar() } },
                async { runCatching { api.upcoming() } },
                async { runCatching { api.currentSeason() } }
            ).awaitAll()

            val watchlistResult = results[0]
            val calendarResult = results[1]
            val upcomingResult = results[2]
            val currentResult = results[3]
            val successful = results.count { it.isSuccess }

            if (successful == 0) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = results.firstNotNullOfOrNull { result -> result.exceptionOrNull()?.message }
                            ?: "Could not reach Anime Calendar"
                    )
                }
                return@launch
            }

            val watchlist = watchlistResult.getOrElse { emptyList() }
            val watchlistIds = watchlist.mapTo(mutableSetOf()) { it.anilistId }
            fun mark(items: List<AnimeItem>) = items.map { item ->
                item.copy(selected = item.anilistId in watchlistIds || item.selected)
            }

            _state.update {
                it.copy(
                    watchlist = mark(watchlist).sortedBy { item -> item.displayTitle.lowercase() },
                    calendar = mark(calendarResult.getOrElse { emptyList() }),
                    upcoming = mark(upcomingResult.getOrElse { emptyList() }),
                    currentSeason = mark(currentResult.getOrElse { emptyList() }),
                    watchlistIds = watchlistIds,
                    loading = false,
                    refreshing = false,
                    error = if (successful < results.size) "Some sections could not be refreshed" else null
                )
            }
        }
    }

    fun toggleWatchlist(item: AnimeItem) {
        val id = item.anilistId
        val adding = id !in _state.value.watchlistIds
        if (id in _state.value.mutatingIds) return

        viewModelScope.launch {
            _state.update { it.copy(mutatingIds = it.mutatingIds + id, error = null) }
            val success = runCatching { api.setWatchlisted(id, adding) }.getOrDefault(false)
            if (!success) {
                _state.update {
                    it.copy(
                        mutatingIds = it.mutatingIds - id,
                        error = if (adding) "Could not add ${item.displayTitle}" else "Could not remove ${item.displayTitle}"
                    )
                }
                return@launch
            }

            val selectedIds = if (adding) _state.value.watchlistIds + id else _state.value.watchlistIds - id
            fun mark(items: List<AnimeItem>) = items.map { candidate ->
                if (candidate.anilistId == id) candidate.copy(selected = adding) else candidate
            }
            _state.update {
                val nextWatchlist = if (adding) {
                    (it.watchlist + item.copy(selected = true)).distinctBy(AnimeItem::anilistId)
                } else {
                    it.watchlist.filterNot { candidate -> candidate.anilistId == id }
                }
                it.copy(
                    watchlist = nextWatchlist.sortedBy { candidate -> candidate.displayTitle.lowercase() },
                    calendar = mark(it.calendar),
                    upcoming = mark(it.upcoming),
                    currentSeason = mark(it.currentSeason),
                    watchlistIds = selectedIds,
                    mutatingIds = it.mutatingIds - id
                )
            }
        }
    }
}
