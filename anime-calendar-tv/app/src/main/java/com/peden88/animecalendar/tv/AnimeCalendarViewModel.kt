package com.peden88.animecalendar.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnimeCalendarViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(
        "anime_calendar_tv",
        Application.MODE_PRIVATE
    )

    private val _state = MutableStateFlow(
        AnimeCalendarUiState(
            needsConfiguration = savedToken().isBlank(),
            apiBaseUrl = savedBaseUrl()
        )
    )
    val state: StateFlow<AnimeCalendarUiState> = _state.asStateFlow()

    init {
        if (!_state.value.needsConfiguration) {
            refresh(initial = true)
        }
    }

    private fun savedBaseUrl(): String =
        preferences.getString("api_base_url", BuildConfig.ANIME_CALENDAR_TV_API_BASE_URL)
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.ANIME_CALENDAR_TV_API_BASE_URL

    private fun savedToken(): String =
        preferences.getString("api_token", "").orEmpty().trim()

    private fun api(): AnimeCalendarApi =
        AnimeCalendarApi(savedBaseUrl(), savedToken())

    fun saveConnection(baseUrl: String, token: String) {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val normalizedToken = token.trim()

        preferences.edit()
            .putString("api_base_url", normalizedUrl)
            .putString("api_token", normalizedToken)
            .apply()

        _state.update {
            it.copy(
                needsConfiguration = normalizedUrl.isBlank() || normalizedToken.isBlank(),
                apiBaseUrl = normalizedUrl.ifBlank { BuildConfig.ANIME_CALENDAR_TV_API_BASE_URL },
                error = null
            )
        }

        if (!_state.value.needsConfiguration) {
            refresh(initial = true)
        }
    }

    fun requestConfiguration() {
        _state.update { it.copy(needsConfiguration = true) }
    }

    fun refresh(initial: Boolean = false) {
        if (savedToken().isBlank()) {
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    needsConfiguration = true
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = initial && it.watchlist.isEmpty() && it.calendar.isEmpty(),
                    refreshing = !initial,
                    error = null,
                    needsConfiguration = false
                )
            }

            runCatching { api().bootstrap() }
                .onSuccess { snapshot ->
                    val watchlistIds = snapshot.watchlist.mapTo(mutableSetOf()) { it.anilistId }
                    _state.update {
                        it.copy(
                            watchlist = snapshot.watchlist,
                            calendar = snapshot.calendar,
                            upcoming = snapshot.upcoming,
                            currentSeason = snapshot.currentSeason,
                            watchlistIds = watchlistIds,
                            resolutions = snapshot.resolutions,
                            loading = false,
                            refreshing = false,
                            needsConfiguration = false,
                            apiBaseUrl = savedBaseUrl(),
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = error.message ?: "Could not reach Anime Calendar TV API"
                        )
                    }
                }
        }
    }

    fun toggleWatchlist(item: AnimeItem) {
        val id = item.anilistId
        if (id in _state.value.mutatingIds) return

        viewModelScope.launch {
            _state.update { it.copy(mutatingIds = it.mutatingIds + id, error = null) }
            val success = runCatching { api().toggleWatchlist(id) }.getOrDefault(false)

            if (!success) {
                _state.update {
                    it.copy(
                        mutatingIds = it.mutatingIds - id,
                        error = "Could not update ${item.displayTitle}"
                    )
                }
                return@launch
            }

            _state.update { it.copy(mutatingIds = it.mutatingIds - id) }
            refresh()
        }
    }
}
