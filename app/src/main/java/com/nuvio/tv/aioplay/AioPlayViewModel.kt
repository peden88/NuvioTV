package com.nuvio.tv.aioplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.AioPlaySessionStore
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AioPlaySection(val label: String) {
    LIVE("Live"),
    VOD("VOD"),
    CONTINUE("Continue Watching")
}

data class AioPlayUiState(
    val checkingSession: Boolean = true,
    val signedIn: Boolean = false,
    val loginBusy: Boolean = false,
    val user: AioPlayUser? = null,
    val capabilities: AioPlayCapabilities? = null,
    val selectedSection: AioPlaySection = AioPlaySection.LIVE,
    val catalogs: List<AioPlayCatalog> = emptyList(),
    val selectedCatalogId: String? = null,
    val items: List<AioPlayItem> = emptyList(),
    val loadingCatalog: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AioPlayViewModel @Inject constructor(
    private val api: AioPlayApiClient,
    private val sessionStore: AioPlaySessionStore,
    private val watchProgressRepository: WatchProgressRepository,
    private val playerSettingsDataStore: PlayerSettingsDataStore
) : ViewModel() {
    private val _state = MutableStateFlow(AioPlayUiState())
    val state: StateFlow<AioPlayUiState> = _state.asStateFlow()

    private var token: String? = null
    private var liveCatalogs: List<AioPlayCatalog> = emptyList()
    private var vodCatalogs: List<AioPlayCatalog> = emptyList()
    private var continueWatchingItems: List<AioPlayItem> = emptyList()
    private var localProgressByKey: Map<String, WatchProgress> = emptyMap()
    private val sharedProgressByKey = mutableMapOf<String, WatchProgress>()
    private val pushedProgressFingerprints = mutableMapOf<String, String>()

    private val continueWatchingCatalog = AioPlayCatalog(
        id = "continue_watching",
        name = "Continue Watching",
        type = "continue"
    )

    init {
        viewModelScope.launch {
            playerSettingsDataStore.applyAioPlayDefaultsIfUnset()
            restoreSession()
        }
        viewModelScope.launch {
            watchProgressRepository.continueWatching.collectLatest {
                rebuildContinueWatching()
            }
        }
        viewModelScope.launch {
            watchProgressRepository.allProgress.collect { rows ->
                localProgressByKey = rows.associateBy(::progressKey)
                rows.forEach(::mergeSharedProgress)
                rebuildContinueWatching()
                val activeToken = token ?: return@collect
                val changed = rows
                    .sortedByDescending { it.lastWatched }
                    .take(250)
                    .filter { progress ->
                        pushedProgressFingerprints[progressKey(progress)] != progressFingerprint(progress)
                    }
                if (changed.isEmpty()) return@collect

                runCatching { api.pushProgress(activeToken, changed) }
                    .onSuccess {
                        changed.forEach { progress ->
                            pushedProgressFingerprints[progressKey(progress)] = progressFingerprint(progress)
                        }
                    }
            }
        }
    }

    private fun isSeriesProgress(progress: WatchProgress): Boolean =
        progress.contentType.equals("series", ignoreCase = true) ||
            progress.contentType.equals("tv", ignoreCase = true) ||
            progress.contentType.equals("episode", ignoreCase = true)

    private fun progressKey(progress: WatchProgress): String = buildString {
        val isSeries = isSeriesProgress(progress)
        append(if (isSeries) "series" else "movie")
        append('|')
        append(progress.contentId)
        if (isSeries) {
            append('|')
            if (progress.season != null && progress.episode != null) {
                append(progress.season)
                append('|')
                append(progress.episode)
            } else {
                append("video|")
                append(progress.videoId)
            }
        }
    }

    private fun mergeSharedProgress(progress: WatchProgress) {
        val key = progressKey(progress)
        val current = sharedProgressByKey[key]
        if (current == null || progress.lastWatched >= current.lastWatched) {
            sharedProgressByKey[key] = progress
        }
    }

    private fun rebuildContinueWatching() {
        continueWatchingItems = sharedProgressByKey.values
            .asSequence()
            .filter(WatchProgress::isInProgress)
            .sortedByDescending(WatchProgress::lastWatched)
            .map { progress ->
                val isSeries = isSeriesProgress(progress)
                val episodeLabel = if (
                    isSeries && progress.season != null && progress.episode != null
                ) {
                    "S" + progress.season.toString().padStart(2, '0') +
                        "E" + progress.episode.toString().padStart(2, '0') +
                        progress.episodeTitle
                            ?.takeIf { it.isNotBlank() }
                            ?.let { " · $it" }
                            .orEmpty()
                } else {
                    null
                }
                AioPlayItem(
                    id = if (isSeries) progress.videoId else progress.contentId,
                    type = if (isSeries) "episode" else "movie",
                    name = progress.name,
                    description = episodeLabel,
                    poster = progress.poster,
                    background = progress.backdrop,
                    logo = progress.logo,
                    parentId = if (isSeries) progress.contentId else null,
                    parentName = if (isSeries) progress.name else null,
                    season = progress.season,
                    episode = progress.episode,
                    episodeTitle = progress.episodeTitle,
                    resumePositionMs = progress.position.takeIf { it > 0L },
                    resumeDurationMs = progress.duration.takeIf { it > 0L }
                )
            }
            .toList()

        if (_state.value.selectedSection == AioPlaySection.CONTINUE) {
            _state.value = _state.value.copy(
                items = continueWatchingItems,
                loadingCatalog = false,
                error = null
            )
        }
    }

    fun withSharedResume(item: AioPlayItem, contentType: String): AioPlayItem {
        val type = contentType.lowercase()
        if (type != "movie" && type != "episode") return item

        val progress = if (type == "movie") {
            sharedProgressByKey.values
                .filter { !isSeriesProgress(it) && it.contentId == item.id }
                .maxByOrNull(WatchProgress::lastWatched)
        } else {
            val parentId = item.parentId
            sharedProgressByKey.values
                .filter { candidate ->
                    isSeriesProgress(candidate) &&
                        (parentId == null || candidate.contentId == parentId) &&
                        when {
                            item.season != null && item.episode != null ->
                                candidate.season == item.season && candidate.episode == item.episode
                            else -> candidate.videoId == item.id
                        }
                }
                .maxByOrNull(WatchProgress::lastWatched)
        }

        return if (progress?.isInProgress() == true) {
            item.copy(
                resumePositionMs = progress.position.takeIf { it > 0L },
                resumeDurationMs = progress.duration.takeIf { it > 0L }
            )
        } else {
            item
        }
    }

    private fun progressFingerprint(progress: WatchProgress): String =
        listOf(
            progress.position,
            progress.duration,
            progress.lastWatched,
            progress.progressPercent ?: -1f
        ).joinToString("|")

    private suspend fun pullSharedProgress(activeToken: String) {
        val remote = api.progress(activeToken)
        if (remote.isEmpty()) return

        val local = if (localProgressByKey.isNotEmpty()) {
            localProgressByKey
        } else {
            watchProgressRepository.allProgress.first().associateBy(::progressKey)
        }

        remote.forEach { incoming ->
            mergeSharedProgress(incoming)
            val current = local[progressKey(incoming)]
            if (current == null || incoming.lastWatched > current.lastWatched) {
                watchProgressRepository.saveProgress(incoming, syncRemote = false)
            }
        }
        rebuildContinueWatching()
    }

    private suspend fun syncSharedProgress(activeToken: String) {
        val local = watchProgressRepository.allProgress.first()
        localProgressByKey = local.associateBy(::progressKey)
        local.forEach(::mergeSharedProgress)
        rebuildContinueWatching()

        runCatching { pullSharedProgress(activeToken) }

        val newestLocal = watchProgressRepository.allProgress.first()
            .sortedByDescending { it.lastWatched }
            .take(250)
        if (newestLocal.isNotEmpty()) {
            runCatching { api.pushProgress(activeToken, newestLocal) }
                .onSuccess {
                    newestLocal.forEach { progress ->
                        pushedProgressFingerprints[progressKey(progress)] = progressFingerprint(progress)
                    }
                }
        }
    }

    private suspend fun restoreSession() {
        if (!api.isConfigured()) {
            _state.value = AioPlayUiState(
                checkingSession = false,
                error = "AIOPlay server URL is not configured in this APK."
            )
            return
        }

        val stored = sessionStore.session.first()
        if (stored == null) {
            _state.value = AioPlayUiState(checkingSession = false)
            return
        }

        token = stored.token
        runCatching {
            val user = api.account(stored.token)
            enterSignedInState(user)
        }.onFailure {
            token = null
            sessionStore.clear()
            _state.value = AioPlayUiState(checkingSession = false)
        }
    }

    fun signIn(username: String, password: String) {
        if (_state.value.loginBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loginBusy = true, error = null)
            runCatching {
                api.login(username, password)
            }.onSuccess { login ->
                token = login.token
                sessionStore.save(
                    token = login.token,
                    username = login.user.username,
                    displayName = login.user.displayName,
                    role = login.user.role
                )
                enterSignedInState(login.user)
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    checkingSession = false,
                    signedIn = false,
                    loginBusy = false,
                    error = error.message ?: "Sign-in failed."
                )
            }
        }
    }

    private suspend fun enterSignedInState(user: AioPlayUser) {
        val activeToken = token ?: return
        val capabilities = api.capabilities(activeToken)
        runCatching { syncSharedProgress(activeToken) }

        liveCatalogs = if (capabilities.sportsEnabled) {
            api.catalogs(activeToken)
                .filter { it.type.equals("tv", ignoreCase = true) }
        } else {
            emptyList()
        }

        vodCatalogs = if (capabilities.vodEnabled) {
            runCatching { api.vodCatalogs(activeToken) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val initialSection = when {
            liveCatalogs.isNotEmpty() -> AioPlaySection.LIVE
            vodCatalogs.isNotEmpty() -> AioPlaySection.VOD
            else -> AioPlaySection.LIVE
        }
        val initialCatalogs = catalogsFor(initialSection)
        val preferred = if (initialSection == AioPlaySection.LIVE) {
            initialCatalogs.firstOrNull { it.id == "nuvio_sports_live" }
                ?: initialCatalogs.firstOrNull()
        } else {
            initialCatalogs.firstOrNull()
        }

        _state.value = AioPlayUiState(
            checkingSession = false,
            signedIn = true,
            user = user,
            capabilities = capabilities,
            selectedSection = initialSection,
            catalogs = initialCatalogs,
            selectedCatalogId = preferred?.selectionKey,
            loadingCatalog = preferred != null
        )

        if (preferred != null) {
            loadCatalogInternal(preferred)
        }
    }

    private fun catalogsFor(section: AioPlaySection): List<AioPlayCatalog> = when (section) {
        AioPlaySection.LIVE -> liveCatalogs
        AioPlaySection.VOD -> vodCatalogs
        AioPlaySection.CONTINUE -> listOf(continueWatchingCatalog)
    }

    fun selectSection(section: AioPlaySection) {
        if (_state.value.selectedSection == section) return
        viewModelScope.launch {
            val catalogs = catalogsFor(section)
            val preferred = if (section == AioPlaySection.LIVE) {
                catalogs.firstOrNull { it.id == "nuvio_sports_live" } ?: catalogs.firstOrNull()
            } else {
                catalogs.firstOrNull()
            }

            if (section == AioPlaySection.CONTINUE) {
                token?.let { activeToken -> runCatching { pullSharedProgress(activeToken) } }
                _state.value = _state.value.copy(
                    selectedSection = section,
                    catalogs = catalogs,
                    selectedCatalogId = preferred?.selectionKey,
                    items = continueWatchingItems,
                    loadingCatalog = false,
                    error = null
                )
                return@launch
            }

            _state.value = _state.value.copy(
                selectedSection = section,
                catalogs = catalogs,
                selectedCatalogId = preferred?.selectionKey,
                items = emptyList(),
                loadingCatalog = preferred != null,
                error = if (preferred == null) {
                    "No ${section.label.lowercase()} catalogs are available."
                } else {
                    null
                }
            )

            if (preferred != null) loadCatalogInternal(preferred)
        }
    }

    fun selectCatalog(catalog: AioPlayCatalog) {
        if (_state.value.selectedCatalogId == catalog.selectionKey && _state.value.items.isNotEmpty()) return
        viewModelScope.launch { loadCatalogInternal(catalog) }
    }

    fun refreshCurrentCatalog() {
        if (_state.value.selectedSection == AioPlaySection.CONTINUE) {
            viewModelScope.launch {
                token?.let { activeToken -> runCatching { pullSharedProgress(activeToken) } }
                _state.value = _state.value.copy(items = continueWatchingItems, error = null)
            }
            return
        }
        val selected = _state.value.catalogs.firstOrNull {
            it.selectionKey == _state.value.selectedCatalogId
        } ?: return
        viewModelScope.launch { loadCatalogInternal(selected) }
    }

    private suspend fun loadCatalogInternal(catalog: AioPlayCatalog) {
        val activeToken = token ?: return
        _state.value = _state.value.copy(
            selectedCatalogId = catalog.selectionKey,
            loadingCatalog = true,
            error = null
        )

        if (catalog.type == "continue") {
            _state.value = _state.value.copy(
                items = continueWatchingItems,
                loadingCatalog = false,
                error = null
            )
            return
        }

        val request = when (catalog.type.lowercase()) {
            "movie", "series" -> runCatching { api.vodCatalog(activeToken, catalog) }
            else -> runCatching { api.catalog(activeToken, catalog) }
        }

        request
            .onSuccess { items ->
                _state.value = _state.value.copy(
                    items = items,
                    loadingCatalog = false
                )
            }
            .onFailure { error ->
                if ((error as? AioPlayApiException)?.statusCode == 403) {
                    token = null
                    sessionStore.clear()
                    _state.value = AioPlayUiState(checkingSession = false)
                } else {
                    _state.value = _state.value.copy(
                        items = emptyList(),
                        loadingCatalog = false,
                        error = error.message ?: "Catalog could not be loaded."
                    )
                }
            }
    }

    suspend fun loadVodMeta(type: String, id: String): Result<AioPlayMetaDetails> {
        val activeToken = token ?: return Result.failure(
            AioPlayApiException("Your session has expired.", 403)
        )
        return runCatching { api.vodMeta(activeToken, type, id) }
    }

    fun signOut() {
        viewModelScope.launch {
            val oldToken = token
            token = null
            liveCatalogs = emptyList()
            vodCatalogs = emptyList()
            continueWatchingItems = emptyList()
            localProgressByKey = emptyMap()
            sharedProgressByKey.clear()
            pushedProgressFingerprints.clear()
            if (!oldToken.isNullOrBlank()) {
                runCatching { api.logout(oldToken) }
            }
            sessionStore.clear()
            _state.value = AioPlayUiState(checkingSession = false)
        }
    }

    suspend fun startPlayback(
        item: AioPlayItem,
        contentType: String = "sport_event"
    ): Result<AioPlayPlayback> {
        val activeToken = token ?: return Result.failure(
            AioPlayApiException("Your session has expired.", 403)
        )
        return runCatching {
            api.startPlayback(
                token = activeToken,
                item = item,
                contentType = contentType
            )
        }
    }

    suspend fun nextPlayback(sessionId: String): Result<AioPlayPlayback> {
        val activeToken = token ?: return Result.failure(
            AioPlayApiException("Your session has expired.", 403)
        )
        return runCatching { api.nextPlayback(activeToken, sessionId) }
    }

    fun finishPlayback(sessionId: String) {
        val activeToken = token ?: return
        viewModelScope.launch {
            runCatching { api.finishPlayback(activeToken, sessionId) }
        }
    }
}
