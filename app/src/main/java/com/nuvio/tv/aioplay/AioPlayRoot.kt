@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.aioplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import android.view.KeyEvent as AndroidKeyEvent
import java.net.URLEncoder
import kotlinx.coroutines.delay
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
    "aioplay_loading?itemId={itemId}&title={title}&poster={poster}&backdrop={backdrop}&logo={logo}&contentType={contentType}&sessionId={sessionId}&parentId={parentId}&parentName={parentName}&season={season}&episode={episode}&episodeTitle={episodeTitle}&resumePositionMs={resumePositionMs}&resumeDurationMs={resumeDurationMs}"
private const val PLAYER_ROUTE =
    "aioplay_player?streamUrl={streamUrl}&title={title}&headers={headers}&contentId={contentId}&contentType={contentType}&contentName={contentName}&poster={poster}&backdrop={backdrop}&logo={logo}&videoId={videoId}&season={season}&episode={episode}&episodeTitle={episodeTitle}&aioplayResumePositionMs={aioplayResumePositionMs}&aioplayResumeDurationMs={aioplayResumeDurationMs}&aioplaySessionId={aioplaySessionId}&aioplayContentType={aioplayContentType}"

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
        "&sessionId=" + encode(sessionId) +
        "&parentId=" + encode(item.parentId) +
        "&parentName=" + encode(item.parentName) +
        "&season=" + encode(item.season?.toString()) +
        "&episode=" + encode(item.episode?.toString()) +
        "&episodeTitle=" + encode(item.episodeTitle) +
        "&resumePositionMs=" + encode(item.resumePositionMs?.toString()) +
        "&resumeDurationMs=" + encode(item.resumeDurationMs?.toString())

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
    val trackingContentType = when (contentType.lowercase()) {
        "episode" -> "series"
        "movie" -> "movie"
        else -> "tv"
    }
    val trackingContentId = item.parentId ?: item.id
    val trackingContentName = item.parentName ?: item.name
    val displayTitle = item.episodeTitle ?: item.name
    return "aioplay_player" +
        "?streamUrl=" + encode(playback.target.url) +
        "&title=" + encode(displayTitle) +
        "&headers=" + encode(headers) +
        "&contentId=" + encode(trackingContentId) +
        "&contentType=" + encode(trackingContentType) +
        "&contentName=" + encode(trackingContentName) +
        "&poster=" + encode(item.poster) +
        "&backdrop=" + encode(item.background) +
        "&logo=" + encode(item.logo) +
        "&videoId=" + encode(item.id) +
        "&season=" + encode(item.season?.toString()) +
        "&episode=" + encode(item.episode?.toString()) +
        "&episodeTitle=" + encode(item.episodeTitle) +
        "&aioplayResumePositionMs=" + encode(item.resumePositionMs?.toString()) +
        "&aioplayResumeDurationMs=" + encode(item.resumeDurationMs?.toString()) +
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AioPlayBackgroundGradient)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                colors = SurfaceDefaults.colors(containerColor = Color.Transparent)
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
            .background(AioPlayBackgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AioPlayDetailCard)
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
                            val resumeItem = viewModel.withSharedResume(item, contentType)
                            navController.navigate(loadingRoute(resumeItem, contentType))
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
                    val resumeItem = viewModel.withSharedResume(item, contentType)
                    navController.navigate(loadingRoute(resumeItem, contentType))
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
                navArgument("sessionId") { type = NavType.StringType; defaultValue = "" },
                navArgument("parentId") { type = NavType.StringType; defaultValue = "" },
                navArgument("parentName") { type = NavType.StringType; defaultValue = "" },
                navArgument("season") { type = NavType.StringType; defaultValue = "" },
                navArgument("episode") { type = NavType.StringType; defaultValue = "" },
                navArgument("episodeTitle") { type = NavType.StringType; defaultValue = "" },
                navArgument("resumePositionMs") { type = NavType.StringType; defaultValue = "" },
                navArgument("resumeDurationMs") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val item = AioPlayItem(
                id = entry.arguments?.getString("itemId").orEmpty(),
                type = "tv",
                name = entry.arguments?.getString("title").orEmpty(),
                description = null,
                poster = entry.arguments?.getString("poster")?.takeIf { it.isNotBlank() },
                background = entry.arguments?.getString("backdrop")?.takeIf { it.isNotBlank() },
                logo = entry.arguments?.getString("logo")?.takeIf { it.isNotBlank() },
                parentId = entry.arguments?.getString("parentId")?.takeIf { it.isNotBlank() },
                parentName = entry.arguments?.getString("parentName")?.takeIf { it.isNotBlank() },
                season = entry.arguments?.getString("season")?.toIntOrNull(),
                episode = entry.arguments?.getString("episode")?.toIntOrNull(),
                episodeTitle = entry.arguments?.getString("episodeTitle")?.takeIf { it.isNotBlank() },
                resumePositionMs = entry.arguments?.getString("resumePositionMs")?.toLongOrNull(),
                resumeDurationMs = entry.arguments?.getString("resumeDurationMs")?.toLongOrNull()
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
                navArgument("season") { type = NavType.StringType; defaultValue = "" },
                navArgument("episode") { type = NavType.StringType; defaultValue = "" },
                navArgument("episodeTitle") { type = NavType.StringType; defaultValue = "" },
                navArgument("aioplayResumePositionMs") { type = NavType.StringType; defaultValue = "" },
                navArgument("aioplayResumeDurationMs") { type = NavType.StringType; defaultValue = "" },
                navArgument("aioplaySessionId") { type = NavType.StringType; defaultValue = "" },
                navArgument("aioplayContentType") { type = NavType.StringType; defaultValue = "sport_event" }
            )
        ) { entry ->
            val args = entry.arguments
            val sessionId = args?.getString("aioplaySessionId").orEmpty()
            val fallbackContentType = args?.getString("aioplayContentType")
                ?.takeIf { it.isNotBlank() }
                ?: "sport_event"
            val trackingContentId = args?.getString("contentId").orEmpty()
            val videoId = args?.getString("videoId").orEmpty()
            val contentName = args?.getString("contentName")?.takeIf { it.isNotBlank() }
            val item = AioPlayItem(
                id = if (fallbackContentType == "episode") videoId.ifBlank { trackingContentId } else trackingContentId,
                type = if (fallbackContentType == "episode") "series" else "tv",
                name = args?.getString("title").orEmpty(),
                description = null,
                poster = args?.getString("poster")?.takeIf { it.isNotBlank() },
                background = args?.getString("backdrop")?.takeIf { it.isNotBlank() },
                logo = args?.getString("logo")?.takeIf { it.isNotBlank() },
                parentId = trackingContentId.takeIf { fallbackContentType == "episode" && it.isNotBlank() },
                parentName = contentName.takeIf { fallbackContentType == "episode" },
                season = args?.getString("season")?.toIntOrNull(),
                episode = args?.getString("episode")?.toIntOrNull(),
                episodeTitle = args?.getString("episodeTitle")?.takeIf { it.isNotBlank() },
                resumePositionMs = args?.getString("aioplayResumePositionMs")?.toLongOrNull(),
                resumeDurationMs = args?.getString("aioplayResumeDurationMs")?.toLongOrNull()
            )

            LaunchedEffect(sessionId) {
                if (sessionId.isBlank()) return@LaunchedEffect
                while (true) {
                    delay(30_000)
                    val heartbeat = viewModel.heartbeatPlayback(sessionId)
                    if (heartbeat.isFailure) {
                        viewModel.finishPlayback(sessionId)
                        navController.popBackStack(HOME_ROUTE, inclusive = false)
                        break
                    }
                }
            }

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
    val contentFocusRequesters = remember(
        state.selectedSection,
        state.selectedCatalogId,
        state.items.map { it.id },
        firstContentFocus
    ) {
        state.items.associate { item ->
            item.id to if (item.id == state.items.firstOrNull()?.id) {
                firstContentFocus
            } else {
                FocusRequester()
            }
        }
    }
    var contentWindowStartRow by remember(
        state.selectedSection,
        state.selectedCatalogId
    ) { mutableIntStateOf(0) }
    var pendingContentFocusId by remember(
        state.selectedSection,
        state.selectedCatalogId
    ) { mutableStateOf<String?>(null) }
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
            .background(AioPlayBackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .width(205.dp)
                .fillMaxHeight()
                .background(AioPlayGlass)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        .width(25.dp)
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "AIOPlay",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 8.sp,
                        lineHeight = 10.sp
                    ),
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                .padding(start = 24.dp, end = 24.dp, bottom = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AioPlayGlass)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.capabilities?.vodEnabled == true) {
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
                    }
                    if (state.capabilities?.sportsEnabled == true) {
                        if (state.capabilities?.vodEnabled == true) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
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
                        Spacer(modifier = Modifier.width(8.dp))
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
                        .width(42.dp)
                        .onFocusChanged {
                            if (it.isFocused) focusZone = AioPlayHomeFocusZone.TOP
                        },
                    colors = ButtonDefaults.colors(
                        containerColor = AioPlayPillIdle,
                        focusedContainerColor = AioPlayAccentBlue
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Playback settings"
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 17.sp,
                        lineHeight = 20.sp
                    ),
                    color = NuvioTheme.colors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LaunchedEffect(state.selectedSection, state.selectedCatalogId) {
                contentWindowStartRow = 0
                pendingContentFocusId = null
            }

            LaunchedEffect(contentWindowStartRow, pendingContentFocusId) {
                val focusId = pendingContentFocusId ?: return@LaunchedEffect
                runCatching { contentFocusRequesters[focusId]?.requestFocus() }
                pendingContentFocusId = null
            }

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
                    val columnCount = if (posterMode) 6 else 3
                    val totalRows = (state.items.size + columnCount - 1) / columnCount
                    val maxWindowStart = (totalRows - 2).coerceAtLeast(0)
                    if (contentWindowStartRow > maxWindowStart) {
                        contentWindowStartRow = maxWindowStart
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val rowSpacing = if (posterMode) 6.dp else 8.dp
                        val columnSpacing = if (posterMode) 9.dp else 10.dp
                        val rowHeight = (maxHeight - rowSpacing) / 2

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(rowSpacing)
                        ) {
                            repeat(2) { visibleRow ->
                                val absoluteRow = contentWindowStartRow + visibleRow

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight),
                                    horizontalArrangement = Arrangement.spacedBy(columnSpacing),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(columnCount) { column ->
                                        val itemIndex = absoluteRow * columnCount + column
                                        val item = state.items.getOrNull(itemIndex)

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item != null) {
                                                val requester = contentFocusRequesters[item.id]
                                                AioPlayContentCard(
                                                    item = item,
                                                    posterMode = posterMode,
                                                    liveCardHeight = if (posterMode) null else rowHeight - 8.dp,
                                                    onClick = { onItem(item) },
                                                    modifier = Modifier
                                                        .then(
                                                            if (requester != null) {
                                                                Modifier.focusRequester(requester)
                                                            } else {
                                                                Modifier
                                                            }
                                                        )
                                                        .onPreviewKeyEvent { event ->
                                                            val native = event.nativeKeyEvent
                                                            if (native.action != AndroidKeyEvent.ACTION_DOWN) {
                                                                return@onPreviewKeyEvent false
                                                            }

                                                            val direction = when (native.keyCode) {
                                                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> 1
                                                                AndroidKeyEvent.KEYCODE_DPAD_UP -> -1
                                                                else -> 0
                                                            }
                                                            if (direction == 0) {
                                                                return@onPreviewKeyEvent false
                                                            }

                                                            val targetRow = absoluteRow + direction
                                                            if (targetRow < 0 || targetRow >= totalRows) {
                                                                return@onPreviewKeyEvent false
                                                            }

                                                            val targetRowStart = targetRow * columnCount
                                                            val targetIndex = minOf(
                                                                targetRowStart + column,
                                                                state.items.lastIndex
                                                            )
                                                            if (targetIndex < targetRowStart) {
                                                                return@onPreviewKeyEvent false
                                                            }

                                                            val targetItem = state.items[targetIndex]
                                                            val nextWindowStart = when {
                                                                targetRow < contentWindowStartRow ->
                                                                    targetRow
                                                                targetRow > contentWindowStartRow + 1 ->
                                                                    targetRow - 1
                                                                else ->
                                                                    contentWindowStartRow
                                                            }.coerceIn(0, maxWindowStart)

                                                            pendingContentFocusId = targetItem.id
                                                            contentWindowStartRow = nextWindowStart
                                                            true
                                                        }
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
                }
            }
        }
    }
}

@Composable
private fun AioPlaySectionCard@Composable
private fun AioPlaySectionCard(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { RoundedCornerShape(20.dp) }
    val innerShape = remember { RoundedCornerShape(17.dp) }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(if (text == "Continue Watching") 157.dp else 106.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = shape),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(1.dp, Color.Transparent),
                shape = shape
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.035f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFocused) {
                        Modifier.background(Color.White, shape)
                    } else {
                        Modifier.background(AioPlayAccentGradient, shape)
                    }
                )
                .padding(if (isFocused) 3.dp else 2.dp)
                .then(
                    if (selected) {
                        Modifier.background(AioPlayAccentGradient, innerShape)
                    } else {
                        Modifier.background(AioPlayPillIdle, innerShape)
                    }
                )
                .padding(vertical = 6.dp, horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                ),
                color = Color.White,
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
    val shape = remember { RoundedCornerShape(14.dp) }
    val innerShape = remember { RoundedCornerShape(12.dp) }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = shape),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(1.dp, Color.Transparent),
                shape = shape
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.025f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFocused) {
                        Modifier.background(Color.White, shape)
                    } else {
                        Modifier.background(AioPlayAccentGradient, shape)
                    }
                )
                .padding(if (isFocused) 2.dp else 1.5.dp)
                .then(
                    if (selected) {
                        Modifier.background(AioPlayAccentGradient, innerShape)
                    } else {
                        Modifier.background(AioPlayPillIdle, innerShape)
                    }
                )
                .padding(horizontal = 13.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AioPlayContentCard(
    item: AioPlayItem,
    posterMode: Boolean,
    liveCardHeight: Dp? = null,
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
                        border = BorderStroke(posterStyle.focusedBorderWidth, AioPlayAccentGradient),
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
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                ),
                color = NuvioTheme.colors.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 4.dp,
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
            .then(
                if (liveCardHeight != null) {
                    Modifier.height(liveCardHeight)
                } else {
                    Modifier.aspectRatio(16f / 9f)
                }
            ),
        shape = CardDefaults.shape(shape = shape),
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.BackgroundCard
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, AioPlayAccentGradient),
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
                    .padding(9.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.description.isNullOrBlank()) {
                    Text(
                        text = item.description.lineSequence().firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        ),
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
            .background(AioPlayBackgroundGradient)
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
            .background(AioPlayBackgroundGradient),
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
            .background(AioPlayBackgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AioPlayDetailCard)
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
                onClick = onRefresh,
                colors = ButtonDefaults.colors(
                    containerColor = AioPlayPillIdle,
                    focusedContainerColor = AioPlayAccentBlue
                )
            ) {
                Text("Refresh content")
            }
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.colors(
                    containerColor = AioPlayPillIdle,
                    focusedContainerColor = AioPlayAccentViolet
                )
            ) {
                Text("Sign out")
            }
            Button(
                onClick = onBack,
                colors = ButtonDefaults.colors(
                    containerColor = AioPlayPillIdle,
                    focusedContainerColor = AioPlayAccentBlue
                )
            ) {
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
            .background(AioPlayBackgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        AioPlayLoadingLabel(text)
    }
}
