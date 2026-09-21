@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.aioplay

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import com.nuvio.tv.ui.screens.account.InputField
import com.nuvio.tv.ui.screens.player.PlayerScreen
import com.nuvio.tv.ui.screens.settings.PlaybackSettingsScreen
import com.nuvio.tv.ui.theme.NuvioTheme
import java.net.URLEncoder
import org.json.JSONObject

private const val HOME_ROUTE = "aioplay_home"
private const val SETTINGS_ROUTE = "aioplay_settings"
private const val ACCOUNT_ROUTE = "aioplay_account"
private const val LOADING_ROUTE =
    "aioplay_loading?itemId={itemId}&title={title}&poster={poster}&backdrop={backdrop}&contentType={contentType}&sessionId={sessionId}"
private const val PLAYER_ROUTE =
    "aioplay_player?streamUrl={streamUrl}&title={title}&headers={headers}&contentId={contentId}&contentType={contentType}&contentName={contentName}&poster={poster}&backdrop={backdrop}&videoId={videoId}&aioplaySessionId={aioplaySessionId}&aioplayContentType={aioplayContentType}"

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
        "&contentType=" + encode(contentType) +
        "&sessionId=" + encode(sessionId)

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
                onCatalog = viewModel::selectCatalog,
                onRefresh = viewModel::refreshCurrentCatalog,
                onPlay = { item ->
                    val contentType = if (
                        state.selectedCatalogId == "nuvio_sports_channels"
                    ) {
                        "live_channel"
                    } else {
                        "sport_event"
                    }
                    navController.navigate(loadingRoute(item, contentType))
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

        composable(ACCOUNT_ROUTE) {
            AioPlayAccountScreen(
                user = state.user,
                vodEnabled = state.capabilities?.vodEnabled == true,
                onBack = { navController.popBackStack() },
                onSignOut = viewModel::signOut
            )
        }

        composable(
            route = LOADING_ROUTE,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
                navArgument("backdrop") { type = NavType.StringType; defaultValue = "" },
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
                logo = null
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
                logo = null
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
    onCatalog: (AioPlayCatalog) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (AioPlayItem) -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        Column(
            modifier = Modifier
                .width(235.dp)
                .fillMaxHeight()
                .background(NuvioTheme.colors.BackgroundElevated)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "AIOPlay TV",
                style = MaterialTheme.typography.titleLarge,
                color = NuvioTheme.colors.TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            )

            state.catalogs.forEach { catalog ->
                AioPlayNavCard(
                    text = catalog.name,
                    selected = state.selectedCatalogId == catalog.id,
                    onClick = { onCatalog(catalog) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            AioPlayNavCard(
                text = "Playback Settings",
                selected = false,
                onClick = onSettings
            )
            AioPlayNavCard(
                text = state.user?.displayName?.ifBlank { state.user.username } ?: "Account",
                selected = false,
                onClick = onAccount
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 28.dp, vertical = 22.dp)
        ) {
            val title = state.catalogs
                .firstOrNull { it.id == state.selectedCatalogId }
                ?.name
                ?: "Sports"

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = NuvioTheme.colors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.capabilities?.vodEnabled == true) {
                        Text(
                            text = "VOD services connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }
                }
                Button(onClick = onRefresh) {
                    Text("Refresh")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 245.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 30.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            AioPlayContentCard(
                                item = item,
                                onClick = { onPlay(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AioPlayNavCard(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardDefaults.shape(shape = shape),
        colors = CardDefaults.colors(
            containerColor = if (selected) {
                NuvioTheme.colors.Secondary
            } else {
                NuvioTheme.colors.BackgroundCard
            },
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(2.dp),
                shape = shape
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)
        )
    }
}

@Composable
private fun AioPlayContentCard(
    item: AioPlayItem,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = onClick,
        modifier = Modifier
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
                                Color.Black.copy(alpha = 0.88f)
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
