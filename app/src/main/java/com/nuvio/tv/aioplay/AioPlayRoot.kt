@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.aioplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.CardDepthSurface
import com.nuvio.tv.ui.components.FocusMarqueeText
import com.nuvio.tv.ui.components.LocalCardDepthStyle
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.nuvioCardDepth
import com.nuvio.tv.ui.screens.account.InputField
import com.nuvio.tv.ui.screens.player.PlayerScreen
import com.nuvio.tv.ui.screens.settings.PlaybackSettingsScreen
import com.nuvio.tv.ui.theme.NuvioTheme
import java.net.URLEncoder
import org.json.JSONObject

private const val HOME_ROUTE = "aioplay_home"

private enum class AioPlayHomeFocusZone {
    TOP,
    NAV,
    CONTENT
}
private const val SETTINGS_ROUTE = "aioplay_settings"
private const val ACCOUNT_ROUTE = "aioplay_account"
private const val DETAIL_ROUTE =
    "aioplay_detail?itemId={itemId}&itemType={itemType}&title={title}&poster={poster}&backdrop={backdrop}&logo={logo}"
private const val SERIES_ROUTE =
    "aioplay_series?itemId={itemId}&title={title}&poster={poster}&backdrop={backdrop}&logo={logo}"
private const val LOADING_ROUTE =
    "aioplay_loading?itemId={itemId}&title={title}&poster={poster}&backdrop={backdrop}&logo={logo}&contentType={contentType}&sessionId={sessionId}"
private const val PLAYER_ROUTE =
    "aioplay_player?streamUrl={streamUrl}&title={title}&headers={headers}&contentId={contentId}&contentType={contentType}&contentName={contentName}&poster={poster}&backdrop={backdrop}&logo={logo}&videoId={videoId}&aioplaySessionId={aioplaySessionId}&aioplayContentType={aioplayContentType}"

private fun encode(value: String?): String =
    URLEncoder.encode(value.orEmpty(), "UTF-8").replace("+", "%20")

private fun loadingRoute(
    item: AioPlayItem,
    contentType: String,
    sessionId: String = ""
): String =
    "aioplay_loading" +
        "?itemId=" + encode(item.id) +
        "&title=" + encode(item.name) +
        "&poster=" + encode(item.poster) +
        "&backdrop=" + encode(item.background) +
        "&logo=" + encode(item.logo) +
        "&contentType=" + encode(contentType) +
        "&sessionId=" + encode(sessionId)

private fun detailRoute(item: AioPlayItem): String =
    "aioplay_detail" +
        "?itemId=" + encode(item.id) +
        "&itemType=" + encode(item.type) +
        "&title=" + encode(item.name) +
        "&poster=" + encode(item.poster) +
        "&backdrop=" + encode(item.background) +
        "&logo=" + encode(item.logo)

private fun seriesRoute(item: AioPlayItem): String =
    "aioplay_series" +
        "?itemId=" + encode(item.id) +
        "&title=" + encode(item.name) +
        "&poster=" + encode(item.poster) +
        "&backdrop=" + encode(item.background) +
        "&logo=" + encode(item.logo)

private fun playerRoute(
    item: AioPlayItem,
    contentType: String,
    playback: AioPlayPlayback
): String {
    val headers = JSONObject(playback.target.requestHeaders).toString()
    return "aioplay_player" +
        "?streamUrl=" + encode(playback.target.url) +
        "&title=" + encode(item.name) +
        "&headers=" + encode(headers) +
        "&contentId=" + encode(item.id) +
        "&contentType=tv" +
        "&contentName=" + encode(item.name) +
        "&poster=" + encode(item.poster) +
        "&backdrop=" + encode(item.background) +
        "&logo=" + encode(item.logo) +
        "&videoId=" + encode(item.id) +
        "&aioplaySessionId=" + encode(playback.sessionId) +
        "&aioplayContentType=" + encode(contentType)
}

@Composable
fun AioPlayRoot(
    onExit: () -> Unit,
    viewModel: AioPlayViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    NuvioTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = NuvioTheme.colors.Background)
        ) {
            when {
                state.checkingSession -> AioPlayCenteredStatus("Signing in…")
                !state.signedIn -> AioPlayLoginScreen(
                    busy = state.loginBusy,
                    error = state.error,
                    onSignIn = viewModel::signIn,
                    onExit = onExit
                )
                else -> AioPlaySignedInApp(
                    state = state,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun AioPlayLoginScreen(
    busy: Boolean,
    error: String?,
    onSignIn: (String, String) -> Unit,
    onExit: () -> Unit
) {
    BackHandler(onBack = onExit)
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val usernameFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { usernameFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(NuvioTheme.colors.BackgroundElevated)
                .padding(34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.aioplay_brand),
                contentDescription = "AIOPlay",
                modifier = Modifier
                    .width(104.dp)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "AIOPlay",
                style = MaterialTheme.typography.headlineMedium,
                color = NuvioTheme.colors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Sign in to AIOPlay.",
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )

            InputField(
                value = username,
                onValueChange = { username = it },
                placeholder = "Username",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(usernameFocus)
            )

            InputField(
                value = password,
                onValueChange = { password = it },
                placeholder = "Password",
                keyboardType = KeyboardType.Password,
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeAction = {
                    if (!busy && username.isNotBlank() && password.isNotBlank()) {
                        onSignIn(username, password)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.Error
                )
            }

            Button(
                onClick = { onSignIn(username, password) },
                enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Signing in…" else "Sign in")
            }
        }
    }
}

@Composable
private fun AioPlaySignedInApp(
    state: AioPlayUiState,
    viewModel: AioPlayViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE
    ) {
        composable(HOME_ROUTE) {
            AioPlayHomeScreen(
                state = state,
                onSection = viewModel::selectSection,
                onCatalog = viewModel::selectCatalog,
                onItem = { item ->
                    when (state.selectedSection) {
                        AioPlaySection.LIVE -> {
                            val contentType = if (
                                state.catalogs
                                    .firstOrNull { it.selectionKey == state.selectedCatalogId }
                                    ?.id
                                    ?.startsWith("nuvio_sports_channel_") == true
                            ) {
                                "live_channel"
                            } else {
                                "sport_event"
                            }
                            navController.navigate(loadingRoute(item, contentType))
                        }
                        AioPlaySection.VOD -> {
                            navController.navigate(detailRoute(item))
                        }
                        AioPlaySection.CONTINUE -> {
                            val contentType = if (item.type.equals("episode", ignoreCase = true)) {
                                "episode"
                            } else {
                                "movie"
                            }
                            navController.navigate(loadingRoute(item, contentType))
                        }
                    }
                },
                onSettings = { navController.navigate(SETTINGS_ROUTE) },
                onAccount = { navController.navigate(ACCOUNT_ROUTE) }
            )
        }

        composable(SETTINGS_ROUTE) {
            PlaybackSettingsScreen(
                onBackPress = { navController.popBackStack() }
            )
        }

        composable(
            route = DETAIL_ROUTE,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType; defaultValue = "" },
                navArgument("itemType") { type = NavType.StringType; defaultValue = "movie" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                navArgument("backdrop") { type = NavType.StringType; defaultValue = "" },
                navArgument("logo") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val preview = AioPlayItem(
                id = entry.arguments?.getString("itemId").orEmpty(),
                type = entry.arguments?.getString("itemType").orEmpty().ifBlank { "movie" },
                name = entry.arguments?.getString("title").orEmpty(),
                description = null,
                poster = entry.arguments?.getString("poster")?.takeIf { it.isNotBlank() },
                background = entry.arguments?.getString("backdrop")?.takeIf { it.isNotBlank() },
                logo = entry.arguments?.getString("logo")?.takeIf { it.isNotBlank() }
            )
            AioPlayDetailsScreen(
                preview = preview,
                viewModel = viewModel,
                onPlay = { item, contentType ->
                    navController.navigate(loadingRoute(item, contentType))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ACCOUNT_ROUTE) {
            AioPlayAccountScreen(
                user = state.user,
                vodEnabled = state.capabilities?.vodEnabled == true,
                onBack = { navController.popBackStack() },
                onRefresh = viewModel::refreshCurrentCatalog,
                onSignOut = viewModel::signOut
            )
        }

        composable(
            route = SERIES_ROUTE,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                navArgument("backdrop") { type = NavType.StringType; defaultValue = "" },
                navArgument("logo") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val series = AioPlayItem(
                id = entry.arguments?.getString("itemId").orEmpty(),
                type = "series",
                name = entry.arguments?.getString("title").orEmpty(),
                description = null,
                poster = entry.arguments?.getString("poster")?.takeIf { it.isNotBlank() },
                background = entry.arguments?.getString("backdrop")?.takeIf { it.isNotBlank() },
                logo = entry.arguments?.getString("logo")?.takeIf { it.isNotBlank() }
            )
            AioPlaySeriesScreen(
                series = series,
                viewModel = viewModel,
                onEpisode = { episode ->
                    navController.navigate(loadingRoute(episode, "episode"))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = LOADING_ROUTE,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                navArgument("backdrop") { type = NavType.StringType; defaultValue = "" },
                navArgument("logo") { type = NavType.StringType; defaultValue = "" },
                navArgument("contentType") { type = NavType.StringType; defaultValue = "sport_event" },
                navArgument("sessionId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val item = AioPlayItem(
                id = entry.arguments?.getString("itemId").orEmpty(),
                type = "tv",
                name = entry.arguments?.getString("title").orEmpty(),
                description = null,
                poster = entry.arguments?.getString("poster")?.takeIf { it.isNotBlank() },
                background = entry.arguments?.getString("backdrop")?.takeIf { it.isNotBlank() },
                logo = entry.arguments?.getString("logo")?.takeIf { it.isNotBlank() }
            )
            val contentType = entry.arguments?.getString("contentType")
                ?.takeIf { it.isNotBlank() }
                ?: "sport_event"
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()

            AioPlayPlaybackLoadingScreen(
                item = item,
                contentType = contentType,
                sessionId = sessionId,
                viewModel = viewModel,
                onReady = { playback ->
                    navController.navigate(playerRoute(item, contentType, playback)) {
                        popUpTo(HOME_ROUTE) { inclusive = false }
                    }
                },
                onBack = {
                    if (sessionId.isNotBlank()) viewModel.finishPlayback(sessionId)
                    navController.popBackStack(HOME_ROUTE, inclusive = false)
                }
            )
        }

        composable(
            route = PLAYER_ROUTE,
            arguments = listOf(
                navArgument("streamUrl") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("headers") { type = NavType.StringType; defaultValue = "" },
                navArgument("contentId") { type = NavType.StringType; defaultValue = "" },
                navArgument("contentType") { type = NavType.StringType; defaultValue = "tv" },
                navArgument("contentName") { type = NavType.StringType; defaultValue = "" },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                navArgument("backdrop") { type = NavType.StringType; defaultValue = "" },
                navArgument("logo") { type = NavType.StringType; defaultValue = "" },
                navArgument("videoId") { type = NavType.StringType; defaultValue = "" },
                navArgument("aioplaySessionId") { type = NavType.StringType; defaultValue = "" },
                navArgument("aioplayContentType") { type = NavType.StringType; defaultValue = "sport_event" }
            )
        ) { entry ->
            val args = entry.arguments
            val sessionId = args?.getString("aioplaySessionId").orEmpty()
            val item = AioPlayItem(
                id = args?.getString("contentId").orEmpty(),
                type = "tv",
                name = args?.getString("title").orEmpty(),
                description = null,
                poster = args?.getString("poster")?.takeIf { it.isNotBlank() },
                background = args?.getString("backdrop")?.takeIf { it.isNotBlank() },
                logo = args?.getString("logo")?.takeIf { it.isNotBlank() }
            )
            val fallbackContentType = args?.getString("aioplayContentType")
                ?.takeIf { it.isNotBlank() }
                ?: "sport_event"

            PlayerScreen(
                onBackPress = { _, _, _, _, _ ->
                    if (sessionId.isNotBlank()) viewModel.finishPlayback(sessionId)
                    navController.popBackStack(HOME_ROUTE, inclusive = false)
                },
                onPlaybackErrorBack = {
                    if (sessionId.isBlank()) {
                        navController.popBackStack(HOME_ROUTE, inclusive = false)
                    } else {
                        navController.navigate(
                            loadingRoute(
                                item = item,
                                contentType = fallbackContentType,
                                sessionId = sessionId
                            )
                        ) {
                            popUpTo(HOME_ROUTE) { inclusive = false }
                        }
                    }
                },
                onPlaybackEnded = { _, _, _, _ ->
                    if (sessionId.isNotBlank()) viewModel.finishPlayback(sessionId)
                    navController.popBackStack(HOME_ROUTE, inclusive = false)
                }
            )
        }
    }
}

@Composable
private fun AioPlayHomeScreen(
    state: AioPlayUiState,
    onSection: (AioPlaySection) -> Unit,
    onCatalog: (AioPlayCatalog) -> Unit,
    onItem: (AioPlayItem) -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit
) {
    val liveFocus = remember { FocusRequester() }
    val vodFocus = remember { FocusRequester() }
    val continueFocus = remember { FocusRequester() }
    val firstContentFocus = remember(state.selectedSection, state.items.firstOrNull()?.id) { FocusRequester() }
    var pendingSectionFocus by remember { mutableStateOf<AioPlaySection?>(null) }
    val navFocusRequesters = remember(state.catalogs.map { it.selectionKey }) {
        state.catalogs.associate { it.selectionKey to FocusRequester() }
    }
    var focusZone by remember { mutableStateOf(AioPlayHomeFocusZone.TOP) }

    fun selectedTopRequester(): FocusRequester = when (state.selectedSection) {
        AioPlaySection.LIVE -> liveFocus
        AioPlaySection.VOD -> vodFocus
        AioPlaySection.CONTINUE -> continueFocus
    }

    fun focusFirstNavItem() {
        val first = state.catalogs.firstOrNull() ?: return
        runCatching { navFocusRequesters[first.selectionKey]?.requestFocus() }
    }

    fun focusSelectedNavItem() {
        val selected = state.catalogs.firstOrNull {
            it.selectionKey == state.selectedCatalogId
        } ?: state.catalogs.firstOrNull() ?: return
        runCatching { navFocusRequesters[selected.selectionKey]?.requestFocus() }
    }

    LaunchedEffect(
        pendingSectionFocus,
        state.selectedSection,
        state.catalogs,
        state.items
    ) {
        val requested = pendingSectionFocus ?: return@LaunchedEffect
        if (requested != state.selectedSection) return@LaunchedEffect

        when (requested) {
            AioPlaySection.CONTINUE -> {
                if (state.items.isNotEmpty()) {
                    runCatching { firstContentFocus.requestFocus() }
                    pendingSectionFocus = null
                }
            }
            AioPlaySection.LIVE,
            AioPlaySection.VOD -> {
                if (state.catalogs.isNotEmpty()) {
                    focusFirstNavItem()
                    pendingSectionFocus = null
                }
            }
        }
    }

    BackHandler {
        when (focusZone) {
            AioPlayHomeFocusZone.TOP -> focusFirstNavItem()
            AioPlayHomeFocusZone.NAV -> runCatching { selectedTopRequester().requestFocus() }
            AioPlayHomeFocusZone.CONTENT -> focusSelectedNavItem()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        Column(
            modifier = Modifier
                .width(205.dp)
                .fillMaxHeight()
                .background(NuvioTheme.colors.BackgroundElevated)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.aioplay_brand),
                    contentDescription = "AIOPlay",
                    modifier = Modifier
                        .width(50.dp)
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AIOPlay",
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioTheme.colors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(state.catalogs, key = { it.selectionKey }) { catalog ->
                    val requester = navFocusRequesters[catalog.selectionKey]
                    AioPlayNavCard(
                        text = catalog.name,
                        selected = state.selectedCatalogId == catalog.selectionKey,
                        onClick = { onCatalog(catalog) },
                        modifier = Modifier
                            .then(
                                if (requester != null) Modifier.focusRequester(requester)
                                else Modifier
                            )
                            .onFocusChanged {
                                if (it.isFocused) focusZone = AioPlayHomeFocusZone.NAV
                            }
                    )
                }
            }

            AioPlayNavCard(
                text = state.user?.displayName?.ifBlank { state.user.username } ?: "Account",
                selected = false,
                onClick = onAccount,
                modifier = Modifier.onFocusChanged {
                    if (it.isFocused) focusZone = AioPlayHomeFocusZone.NAV
                }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.capabilities?.sportsEnabled == true) {
                        AioPlaySectionCard(
                            text = "Live",
                            selected = state.selectedSection == AioPlaySection.LIVE,
                            onClick = {
                                pendingSectionFocus = AioPlaySection.LIVE
                                onSection(AioPlaySection.LIVE)
                            },
                            modifier = Modifier
                                .focusRequester(liveFocus)
                                .onFocusChanged {
                                    if (it.isFocused) focusZone = AioPlayHomeFocusZone.TOP
                                }
                        )
                    }
                    if (state.capabilities?.vodEnabled == true) {
                        Spacer(modifier = Modifier.width(10.dp))
                        AioPlaySectionCard(
                            text = "VOD",
                            selected = state.selectedSection == AioPlaySection.VOD,
                            onClick = {
                                pendingSectionFocus = AioPlaySection.VOD
                                onSection(AioPlaySection.VOD)
                            },
                            modifier = Modifier
                                .focusRequester(vodFocus)
                                .onFocusChanged {
                                    if (it.isFocused) focusZone = AioPlayHomeFocusZone.TOP
                                }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        AioPlaySectionCard(
                            text = "Continue Watching",
                            selected = state.selectedSection == AioPlaySection.CONTINUE,
                            onClick = {
                                pendingSectionFocus = AioPlaySection.CONTINUE
                                onSection(AioPlaySection.CONTINUE)
                            },
                            modifier = Modifier
                                .focusRequester(continueFocus)
                                .onFocusChanged {
                                    if (it.isFocused) focusZone = AioPlayHomeFocusZone.TOP
                                }
                        )
                    }
                }

                Button(
                    onClick = onSettings,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(52.dp)
                        .onFocusChanged {
                            if (it.isFocused) focusZone = AioPlayHomeFocusZone.TOP
                        },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Playback settings"
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val title = state.catalogs
                .firstOrNull { it.selectionKey == state.selectedCatalogId }
                ?.name
                ?: state.selectedSection.label

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioTheme.colors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            when {
                state.loadingCatalog -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AioPlayLoadingLabel("Loading…")
                    }
                }
                state.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.error ?: "Nothing is available in this section right now.",
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }
                }
                else -> {
                    val posterMode = state.selectedSection != AioPlaySection.LIVE
                    LazyVerticalGrid(
                        columns = if (posterMode) {
                            GridCells.Fixed(5)
                        } else {
                            GridCells.Adaptive(minSize = 245.dp)
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = if (posterMode) 8.dp else 0.dp,
                            bottom = if (posterMode) 24.dp else 30.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (posterMode) 12.dp else 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(
                            if (posterMode) 8.dp else 18.dp
                        )
                    ) {
                        gridItems(state.items, key = { it.id }) { item ->
                            AioPlayContentCard(
                                item = item,
                                posterMode = posterMode,
                                onClick = { onItem(item) },
                                modifier = Modifier
                                    .then(
                                        if (item.id == state.items.firstOrNull()?.id) {
                                            Modifier.focusRequester(firstContentFocus)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            focusZone = AioPlayHomeFocusZone.CONTENT
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AioPlaySectionCard(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { RoundedCornerShape(20.dp) }
    var isFocused by remember { mutableStateOf(false) }
    val border = CardDefaults.border(
        focusedBorder = Border(
            border = BorderStroke(NuvioTheme.spacing.xxs, Color.Transparent),
            shape = shape
        )
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .width(if (text == "Continue Watching") 196.dp else 132.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = shape),
        colors = CardDefaults.colors(
            containerColor = if (selected) {
                NuvioTheme.colors.Secondary
            } else {
                Color.White.copy(alpha = 0.08f)
            },
            focusedContainerColor = NuvioTheme.colors.Secondary
        ),
        border = border,
        scale = CardDefaults.scale(focusedScale = 1.0f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused || selected) {
                    NuvioTheme.colors.OnSecondary
                } else {
                    Color(0xFFE8E8EC)
                },
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AioPlayNavCard(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { RoundedCornerShape(20.dp) }
    var isFocused by remember { mutableStateOf(false) }
    val border = CardDefaults.border(
        focusedBorder = Border(
            border = BorderStroke(NuvioTheme.spacing.xxs, Color.Transparent),
            shape = shape
        )
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = shape),
        colors = CardDefaults.colors(
            containerColor = if (selected) {
                NuvioTheme.colors.Secondary
            } else {
                Color.White.copy(alpha = 0.08f)
            },
            focusedContainerColor = NuvioTheme.colors.Secondary
        ),
        border = border,
        scale = CardDefaults.scale(focusedScale = 1.0f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused || selected) {
                NuvioTheme.colors.OnSecondary
            } else {
                Color(0xFFE8E8EC)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun AioPlayContentCard(
    item: AioPlayItem,
    posterMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (posterMode) {
        val posterStyle = PosterCardDefaults.Style
        val shape = remember(posterStyle.cornerRadius) {
            RoundedCornerShape(posterStyle.cornerRadius)
        }
        val cardDepthStyle = LocalCardDepthStyle.current
        var isFocused by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 5.dp)
        ) {
            Card(
                onClick = onClick,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .onFocusChanged { isFocused = it.isFocused },
                shape = CardDefaults.shape(shape = shape),
                colors = CardDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                ),
                border = CardDefaults.border(
                    focusedBorder = Border(
                        border = NuvioTheme.focusRing.border(posterStyle.focusedBorderWidth),
                        shape = shape
                    )
                ),
                scale = CardDefaults.scale(focusedScale = posterStyle.focusedScale)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .nuvioCardDepth(
                            shape = shape,
                            surface = CardDepthSurface.POSTERS,
                            style = cardDepthStyle
                        )
                        .background(NuvioTheme.colors.BackgroundCard)
                ) {
                    val image = item.poster ?: item.background
                    if (!image.isNullOrBlank()) {
                        AsyncImage(
                            model = image,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            FocusMarqueeText(
                text = item.name,
                focused = isFocused,
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = NuvioTheme.spacing.sm,
                        start = NuvioTheme.spacing.xxs,
                        end = NuvioTheme.spacing.xxs
                    )
            )
        }
        return
    }

    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = CardDefaults.shape(shape = shape),
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.BackgroundCard
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(3.dp),
                shape = shape
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.035f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(NuvioTheme.colors.BackgroundCard)
        ) {
            val image = item.background ?: item.poster
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.24f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.description.isNullOrBlank()) {
                    Text(
                        text = item.description.lineSequence().firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AioPlaySeriesScreen(
    series: AioPlayItem,
    viewModel: AioPlayViewModel,
    onEpisode: (AioPlayItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var details by remember(series.id) { mutableStateOf<AioPlayMetaDetails?>(null) }
    var loading by remember(series.id) { mutableStateOf(true) }
    var error by remember(series.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(series.id) {
        loading = true
        error = null
        viewModel.loadVodMeta("series", series.id)
            .onSuccess { details = it }
            .onFailure { error = it.message ?: "Series details could not be loaded." }
        loading = false
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .padding(28.dp)
    ) {
        val artwork = details?.item?.poster ?: series.poster
        if (!artwork.isNullOrBlank()) {
            AsyncImage(
                model = artwork,
                contentDescription = series.name,
                modifier = Modifier
                    .width(210.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(28.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text(
                text = details?.item?.name ?: series.name,
                style = MaterialTheme.typography.headlineMedium,
                color = NuvioTheme.colors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            details?.item?.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(18.dp))

            when {
                loading -> AioPlayLoadingLabel("Loading episodes…")
                error != null -> Text(
                    text = error.orEmpty(),
                    color = NuvioTheme.colors.Error
                )
                details?.videos.isNullOrEmpty() -> Text(
                    text = "No episodes are available.",
                    color = NuvioTheme.colors.TextSecondary
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(details!!.videos, key = { it.id }) { video ->
                            val label = buildString {
                                if (video.season != null && video.episode != null) {
                                    append("S")
                                    append(video.season.toString().padStart(2, '0'))
                                    append("E")
                                    append(video.episode.toString().padStart(2, '0'))
                                    append("  ")
                                }
                                append(video.title)
                            }
                            AioPlayNavCard(
                                text = label,
                                selected = false,
                                onClick = {
                                    onEpisode(
                                        AioPlayItem(
                                            id = video.id,
                                            type = "series",
                                            name = video.title,
                                            description = null,
                                            poster = details?.item?.poster ?: series.poster,
                                            background = details?.item?.background ?: series.background,
                                            logo = details?.item?.logo ?: series.logo
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AioPlayPlaybackLoadingScreen(
    item: AioPlayItem,
    contentType: String,
    sessionId: String,
    viewModel: AioPlayViewModel,
    onReady: (AioPlayPlayback) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(item.id, contentType, sessionId, attempt) {
        error = null
        var result = if (sessionId.isBlank()) {
            viewModel.startPlayback(item, contentType)
        } else {
            viewModel.nextPlayback(sessionId)
        }

        var resolved = result.getOrNull()
        var safety = 0

        // TV playback accepts direct media only. Web/embed-only targets are
        // silently skipped; the viewer never sees their names or a picker.
        while (resolved != null && resolved.target.kind != "direct" && safety < 10) {
            result = viewModel.nextPlayback(resolved.sessionId)
            resolved = result.getOrNull()
            safety++
        }

        if (resolved != null && resolved.target.kind == "direct") {
            onReady(resolved)
        } else {
            error = result.exceptionOrNull()?.message
                ?: "No playable stream is available right now."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (error == null) {
                AioPlayLoadingLabel(
                    if (sessionId.isBlank()) {
                        "Finding the best available stream…"
                    } else {
                        "Trying another stream automatically…"
                    }
                )
            } else {
                Text(
                    text = error.orEmpty(),
                    color = NuvioTheme.colors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { attempt++ }) {
                        Text("Retry")
                    }
                    Button(onClick = onBack) {
                        Text("Back")
                    }
                }
            }
        }
    }
}

@Composable
private fun AioPlayLoadingLabel(text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(color = NuvioTheme.colors.Secondary)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = NuvioTheme.colors.TextSecondary
        )
    }
}

@Composable
private fun AioPlayAccountScreen(
    user: AioPlayUser?,
    vodEnabled: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(NuvioTheme.colors.BackgroundElevated)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = user?.displayName?.ifBlank { user.username } ?: "Account",
                style = MaterialTheme.typography.headlineSmall,
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = "@" + user?.username.orEmpty(),
                color = NuvioTheme.colors.TextSecondary
            )
            Text(
                text = if (vodEnabled) {
                    "Sports and VOD are enabled by the server."
                } else {
                    "Sports is enabled. VOD can be enabled later by the server administrator."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRefresh
            ) {
                Text("Refresh content")
            }
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.Secondary,
                    focusedContainerColor = NuvioTheme.colors.FocusBackground
                )
            ) {
                Text("Sign out")
            }
            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun AioPlayCenteredStatus(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center
    ) {
        AioPlayLoadingLabel(text)
    }
}
