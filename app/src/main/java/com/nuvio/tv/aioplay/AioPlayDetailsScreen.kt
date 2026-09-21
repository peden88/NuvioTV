package com.nuvio.tv.aioplay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.screens.detail.EpisodesRow
import com.nuvio.tv.ui.screens.detail.HeroContentSection
import com.nuvio.tv.ui.screens.detail.SeasonTabs
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun AioPlayDetailsScreen(
    preview: AioPlayItem,
    viewModel: AioPlayViewModel,
    onPlay: (AioPlayItem, String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val contentType = remember(preview.type) {
        if (preview.type.equals("series", ignoreCase = true) ||
            preview.type.equals("tv", ignoreCase = true)
        ) {
            "series"
        } else {
            "movie"
        }
    }

    var details by remember(preview.id, contentType) {
        mutableStateOf<AioPlayMetaDetails?>(null)
    }
    var loading by remember(preview.id, contentType) { mutableStateOf(true) }
    var error by remember(preview.id, contentType) { mutableStateOf<String?>(null) }
    var retryKey by remember(preview.id, contentType) { mutableIntStateOf(0) }

    LaunchedEffect(preview.id, contentType, retryKey) {
        loading = true
        error = null
        viewModel.loadVodMeta(contentType, preview.id)
            .onSuccess { details = it }
            .onFailure { error = it.message ?: "Title details could not be loaded." }
        loading = false
    }

    when {
        loading && details == null -> AioPlayDetailsStatus("Loading title details…")
        error != null && details == null -> AioPlayDetailsError(
            message = error.orEmpty(),
            onRetry = { retryKey++ },
            onBack = onBack
        )
        details != null -> AioPlayRichDetails(
            details = details!!,
            onPlay = onPlay
        )
    }
}

@Composable
private fun AioPlayRichDetails(
    details: AioPlayMetaDetails,
    onPlay: (AioPlayItem, String) -> Unit
) {
    val meta = details.meta
    val isSeries = meta.apiType.equals("series", ignoreCase = true) ||
        meta.apiType.equals("tv", ignoreCase = true)
    val seasons = remember(meta.videos) {
        meta.videos.mapNotNull(Video::season).distinct().sorted()
    }
    var selectedSeason by remember(meta.id, seasons) {
        mutableIntStateOf(
            seasons.firstOrNull { it > 0 }
                ?: seasons.firstOrNull()
                ?: 1
        )
    }

    val selectedEpisodes = remember(meta.videos, selectedSeason) {
        meta.videos.filter { it.season == selectedSeason }
    }
    val heroFocus = remember(meta.id) { FocusRequester() }
    val seasonFocus = remember(meta.id) { FocusRequester() }

    fun playVideo(video: Video) {
        onPlay(
            AioPlayItem(
                id = video.id,
                type = "series",
                name = video.title,
                description = video.overview,
                poster = meta.poster,
                background = meta.background,
                logo = meta.logo
            ),
            "episode"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        val backdrop = meta.background ?: meta.landscapePoster ?: meta.poster
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .align(Alignment.TopCenter),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(590.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.58f to Color.Black.copy(alpha = 0.46f),
                        1f to NuvioTheme.colors.Background
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 44.dp)
        ) {
            item(key = "hero") {
                HeroContentSection(
                    meta = meta,
                    nextEpisode = if (isSeries) selectedEpisodes.firstOrNull() else null,
                    nextToWatch = null,
                    onPlayClick = {
                        if (isSeries) {
                            (selectedEpisodes.firstOrNull() ?: meta.videos.firstOrNull())
                                ?.let(::playVideo)
                        } else {
                            onPlay(details.item, "movie")
                        }
                    },
                    onPlayLongPress = null,
                    isInLibrary = false,
                    onToggleLibrary = {},
                    onLibraryLongPress = {},
                    isMovieWatched = false,
                    isMovieWatchedPending = false,
                    onToggleMovieWatched = {},
                    showLibraryAction = false,
                    showWatchedAction = false,
                    onRandomClick = null,
                    trailerAvailable = false,
                    playButtonFocusRequester = heroFocus
                )
            }

            if (isSeries && meta.videos.isNotEmpty()) {
                if (seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        SeasonTabs(
                            seasons = seasons,
                            selectedSeason = selectedSeason,
                            onSeasonSelected = { selectedSeason = it },
                            selectedTabFocusRequester = seasonFocus,
                            upFocusRequester = heroFocus,
                            downFocusRequester = null,
                            isFocusEnabled = true
                        )
                    }
                }

                item(key = "episodes-$selectedSeason") {
                    EpisodesRow(
                        episodes = selectedEpisodes,
                        onEpisodeClick = ::playVideo,
                        onToggleEpisodeWatched = {},
                        showEpisodeOptions = false,
                        selectedSeason = selectedSeason,
                        upFocusRequester = if (seasons.isNotEmpty()) seasonFocus else heroFocus
                    )
                }
            }

            if (meta.cast.isNotEmpty() || meta.country != null || meta.awards != null) {
                item(key = "extra-meta") {
                    AioPlayMetadataSummary(meta)
                }
            }
        }
    }
}

@Composable
private fun AioPlayMetadataSummary(meta: Meta) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTheme.spacing.xxxl, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (meta.cast.isNotEmpty()) {
            Text(
                text = "Cast",
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = meta.cast.take(10).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
        }
        meta.country?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary
            )
        }
        meta.awards?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary
            )
        }
    }
}

@Composable
private fun AioPlayDetailsStatus(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = NuvioTheme.colors.Secondary)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = NuvioTheme.colors.TextSecondary
            )
        }
    }
}

@Composable
private fun AioPlayDetailsError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary
            )
            Button(onClick = onRetry) { Text("Retry") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}
