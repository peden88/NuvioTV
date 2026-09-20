package com.nuvio.tv.aiosport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.AioSportSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AioSportUiState(
    val checkingSession: Boolean = true,
    val signedIn: Boolean = false,
    val loginBusy: Boolean = false,
    val user: AioSportUser? = null,
    val capabilities: AioSportCapabilities? = null,
    val catalogs: List<AioSportCatalog> = emptyList(),
    val selectedCatalogId: String? = null,
    val items: List<AioSportItem> = emptyList(),
    val loadingCatalog: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AioSportViewModel @Inject constructor(
    private val api: AioSportApiClient,
    private val sessionStore: AioSportSessionStore
) : ViewModel() {
    private val _state = MutableStateFlow(AioSportUiState())
    val state: StateFlow<AioSportUiState> = _state.asStateFlow()

    private var token: String? = null

    init {
        viewModelScope.launch { restoreSession() }
    }

    private suspend fun restoreSession() {
        if (!api.isConfigured()) {
            _state.value = AioSportUiState(
                checkingSession = false,
                error = "AIOSport server URL is not configured in this APK."
            )
            return
        }

        val stored = sessionStore.session.first()
        if (stored == null) {
            _state.value = AioSportUiState(checkingSession = false)
            return
        }

        token = stored.token
        runCatching {
            val user = api.account(stored.token)
            enterSignedInState(user)
        }.onFailure {
            token = null
            sessionStore.clear()
            _state.value = AioSportUiState(checkingSession = false)
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

    private suspend fun enterSignedInState(user: AioSportUser) {
        val activeToken = token ?: return
        val capabilities = api.capabilities(activeToken)
        val catalogs = if (capabilities.sportsEnabled) {
            api.catalogs(activeToken)
                .filter { it.type.equals("tv", ignoreCase = true) }
        } else {
            emptyList()
        }

        val preferred = catalogs.firstOrNull { it.id == "nuvio_sports_live" }
            ?: catalogs.firstOrNull()

        _state.value = AioSportUiState(
            checkingSession = false,
            signedIn = true,
            user = user,
            capabilities = capabilities,
            catalogs = catalogs,
            selectedCatalogId = preferred?.id,
            loadingCatalog = preferred != null
        )

        if (preferred != null) {
            loadCatalogInternal(preferred)
        }
    }

    fun selectCatalog(catalog: AioSportCatalog) {
        if (_state.value.selectedCatalogId == catalog.id && _state.value.items.isNotEmpty()) return
        viewModelScope.launch { loadCatalogInternal(catalog) }
    }

    fun refreshCurrentCatalog() {
        val selected = _state.value.catalogs.firstOrNull {
            it.id == _state.value.selectedCatalogId
        } ?: return
        viewModelScope.launch { loadCatalogInternal(selected) }
    }

    private suspend fun loadCatalogInternal(catalog: AioSportCatalog) {
        val activeToken = token ?: return
        _state.value = _state.value.copy(
            selectedCatalogId = catalog.id,
            loadingCatalog = true,
            error = null
        )
        runCatching { api.catalog(activeToken, catalog) }
            .onSuccess { items ->
                _state.value = _state.value.copy(
                    items = items,
                    loadingCatalog = false
                )
            }
            .onFailure { error ->
                if ((error as? AioSportApiException)?.statusCode == 403) {
                    token = null
                    sessionStore.clear()
                    _state.value = AioSportUiState(checkingSession = false)
                } else {
                    _state.value = _state.value.copy(
                        loadingCatalog = false,
                        error = error.message ?: "Catalog could not be loaded."
                    )
                }
            }
    }

    fun signOut() {
        viewModelScope.launch {
            val oldToken = token
            token = null
            if (!oldToken.isNullOrBlank()) {
                runCatching { api.logout(oldToken) }
            }
            sessionStore.clear()
            _state.value = AioSportUiState(checkingSession = false)
        }
    }

    suspend fun startPlayback(
        item: AioSportItem,
        contentType: String = "sport_event"
    ): Result<AioSportPlayback> {
        val activeToken = token ?: return Result.failure(
            AioSportApiException("Your session has expired.", 403)
        )
        return runCatching {
            api.startPlayback(
                token = activeToken,
                item = item,
                contentType = contentType
            )
        }
    }

    suspend fun nextPlayback(sessionId: String): Result<AioSportPlayback> {
        val activeToken = token ?: return Result.failure(
            AioSportApiException("Your session has expired.", 403)
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
