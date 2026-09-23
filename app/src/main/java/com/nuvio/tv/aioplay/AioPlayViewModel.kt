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
    CONTINUE("Continue Watching"),
    LIBRARY("Library")
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
    private var libraryItems: List<AioPlayItem> = emptyList()
    private var localProgressByKey: Map<String, WatchProgress> = emptyMap()
    private val sharedProgressByKey = mutableMapOf<String, WatchProgress>()
    private val pushedProgressFingerprints = mutableMapOf<String, String>()
    private val vodMetaCache = object : LinkedHashMap<String, AioPlayMetaDetails>(48, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, AioPlayMetaDetails>?
        ): Boolean = size > 48
    }
    private val vodMetaPrefetching = mutableSetOf<String>()
    private data class CachedSearch(
        val storedAtMs: Long,
        val items: List<AioPlayItem>
    )
    private val searchCache = object : LinkedHashMap<String, CachedSearch>(24, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, CachedSearch>?
        ): Boolean = size > 24
    }

    private val libraryCatalog = AioPlayCatalog(
        id = "library",
        name = "Library",
        type = "library"
    )

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
            .filter { it.isInProgress() }
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
        // Server-backed library items are wired in the next API pass. Keeping the
        // section live now lets navigation/focus behaviour ship independently.
        AioPlaySection.LIBRARY -> listOf(libraryCatalog)
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

            if (section == AioPlaySection.LIBRARY) {
                val activeToken = token
                if (activeToken != null) {
                    runCatching { api.library(activeToken) }.onSuccess { libraryItems = it }
                }
                _state.value = _state.value.copy(
                    selectedSection = section,
                    catalogs = catalogs,
                    selectedCatalogId = preferred?.selectionKey,
                    items = libraryItems,
                    loadingCatalog = false,
                    error = if (libraryItems.isEmpty()) "Your library is empty." else null
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

    fun isInLibrary(item: AioPlayItem): Boolean =
        libraryItems.any { it.id == item.id && it.type.equals(item.type, ignoreCase = true) }

    fun addToLibrary(item: AioPlayItem) {
        val activeToken = token ?: return
        viewModelScope.launch {
            runCatching { api.addToLibrary(activeToken, item) }
                .onSuccess { saved ->
                    libraryItems = listOf(saved) + libraryItems.filterNot {
                        it.id == saved.id && it.type.equals(saved.type, ignoreCase = true)
                    }
                    if (_state.value.selectedSection == AioPlaySection.LIBRARY) {
                        _state.value = _state.value.copy(items = libraryItems, error = null)
                    }
                }
        }
    }

    fun removeFromLibrary(item: AioPlayItem) {
        val activeToken = token ?: return
        viewModelScope.launch {
            runCatching { api.removeFromLibrary(activeToken, item) }
                .onSuccess {
                    libraryItems = libraryItems.filterNot {
                        it.id == item.id && it.type.equals(item.type, ignoreCase = true)
                    }
                    if (_state.value.selectedSection == AioPlaySection.LIBRARY) {
                        _state.value = _state.value.copy(
                            items = libraryItems,
                            error = if (libraryItems.isEmpty()) "Your library is empty." else null
                        )
                    }
                }
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

    private fun vodMetaKey(type: String, id: String): String =
        type.lowercase() + "|" + id

    suspend fun searchVod(
        query: String,
        type: String? = null
    ): Result<List<AioPlayItem>> {
        val activeToken = token ?: return Result.failure(
            AioPlayApiException("Your session has expired.", 403)
        )
        val cleanQuery = query.trim()
        val normalizedType = type
            ?.lowercase()
            ?.takeIf { it == "movie" || it == "series" }
        val cacheKey = cleanQuery.lowercase() + "|" + normalizedType.orEmpty()
        val now = System.currentTimeMillis()

        synchronized(searchCache) {
            searchCache[cacheKey]
                ?.takeIf { now - it.storedAtMs < 5 * 60 * 1000L }
                ?.let { return Result.success(it.items) }
        }

        return runCatching {
            api.searchVod(
                token = activeToken,
                query = cleanQuery,
                type = normalizedType
            )
        }.onSuccess { items ->
            synchronized(searchCache) {
                searchCache[cacheKey] = CachedSearch(
                    storedAtMs = System.currentTimeMillis(),
                    items = items
                )
            }
        }
    }

    suspend fun loadVodMeta(type: String, id: String): Result<AioPlayMetaDetails> {
        val normalizedType = when (type.lowercase()) {
            "tv", "episode" -> "series"
            else -> type.lowercase()
        }
        val key = vodMetaKey(normalizedType, id)
        synchronized(vodMetaCache) {
            vodMetaCache[key]
        }?.let { return Result.success(it) }

        val activeToken = token ?: return Result.failure(
            AioPlayApiException("Your session has expired.", 403)
        )
        return runCatching { api.vodMeta(activeToken, normalizedType, id) }
            .onSuccess { details ->
                synchronized(vodMetaCache) {
                    vodMetaCache[key] = details
                }
            }
    }

    fun prefetchVodMeta(item: AioPlayItem) {
        val type: String
        val id: String
        when (item.type.lowercase()) {
            "episode" -> {
                type = "series"
                id = item.parentId ?: return
            }
            "series", "tv" -> {
                type = "series"
                id = item.id
            }
            "movie" -> {
                type = "movie"
                id = item.id
            }
            else -> return
        }

        val key = vodMetaKey(type, id)
        synchronized(vodMetaCache) {
            if (vodMetaCache.containsKey(key) || key in vodMetaPrefetching) return
            vodMetaPrefetching += key
        }

        viewModelScope.launch {
            try {
                loadVodMeta(type, id)
            } finally {
                synchronized(vodMetaCache) {
                    vodMetaPrefetching -= key
                }
            }
        }
    }

    suspend fun resolveNextEpisode(
        current: AioPlayItem,
        nextVideoId: String?,
        nextSeason: Int?,
        nextEpisode: Int?
    ): AioPlayItem? {
        val seriesId = current.parentId ?: return null
        val details = loadVodMeta("series", seriesId).getOrNull()

        if (details == null) {
            val fallbackId = nextVideoId?.takeIf { it.isNotBlank() } ?: return null
            val code = if (nextSeason != null && nextEpisode != null) {
                "S" + nextSeason.toString().padStart(2, '0') +
                    "E" + nextEpisode.toString().padStart(2, '0')
            } else {
                "Next episode"
            }
            return current.copy(
                id = fallbackId,
                type = "episode",
                name = code,
                description = null,
                season = nextSeason,
                episode = nextEpisode,
                episodeTitle = null,
                resumePositionMs = null,
                resumeDurationMs = null
            )
        }

        val ordered = details.videos.sortedWith(
            compareBy<AioPlayVideo>(
                { it.season ?: Int.MAX_VALUE },
                { it.episode ?: Int.MAX_VALUE }
            )
        )

        val target = when {
            !nextVideoId.isNullOrBlank() ->
                ordered.firstOrNull { it.id == nextVideoId }
            nextSeason != null && nextEpisode != null ->
                ordered.firstOrNull {
                    it.season == nextSeason && it.episode == nextEpisode
                }
            else -> {
                val currentIndex = ordered.indexOfFirst { video ->
                    video.id == current.id ||
                        (
                            current.season != null &&
                            current.episode != null &&
                            video.season == current.season &&
                            video.episode == current.episode
                        )
                }
                ordered.getOrNull(currentIndex + 1)
            }
        }

        if (target == null) {
            val fallbackId = nextVideoId?.takeIf { it.isNotBlank() } ?: return null
            return current.copy(
                id = fallbackId,
                type = "episode",
                name = if (nextSeason != null && nextEpisode != null) {
                    "S" + nextSeason.toString().padStart(2, '0') +
                        "E" + nextEpisode.toString().padStart(2, '0')
                } else {
                    "Next episode"
                },
                description = null,
                season = nextSeason,
                episode = nextEpisode,
                episodeTitle = null,
                resumePositionMs = null,
                resumeDurationMs = null
            )
        }

        return AioPlayItem(
            id = target.id,
            type = "episode",
            name = target.title,
            description = target.overview,
            poster = details.item.poster,
            background = details.item.background,
            logo = details.item.logo,
            parentId = details.item.id,
            parentName = details.item.name,
            season = target.season,
            episode = target.episode,
            episodeTitle = target.title
        )
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
            synchronized(vodMetaCache) {
                vodMetaCache.clear()
                vodMetaPrefetching.clear()
            }
            synchronized(searchCache) {
                searchCache.clear()
            }
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

    suspend fun heartbeatPlayback(sessionId: String): Result<Unit> {
        val activeToken = token ?: return Result.failure(
            AioPlayApiException("Your session has expired.", 403)
        )
        return runCatching { api.heartbeatPlayback(activeToken, sessionId) }
    }

    suspend fun finishPlaybackAndWait(sessionId: String): Result<Unit> {
        val activeToken = token ?: return Result.failure(
            AioPlayApiException("Your session has expired.", 403)
        )
        return runCatching { api.finishPlayback(activeToken, sessionId) }
    }

    fun finishPlayback(sessionId: String) {
        val activeToken = token ?: return
        viewModelScope.launch {
            runCatching { api.finishPlayback(activeToken, sessionId) }
        }
    }
}
