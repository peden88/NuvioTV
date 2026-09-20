package com.peden88.animecalendar.tv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFB39DDB),
                    secondary = Color(0xFF80CBC4),
                    background = Color(0xFF090A0F),
                    surface = Color(0xFF141620)
                )
            ) {
                AnimeCalendarTvApp()
            }
        }
    }
}

@Composable
private fun AnimeCalendarTvApp(
    viewModel: AnimeCalendarViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(CalendarTab.WATCHLIST) }
    var detailItem by remember { mutableStateOf<AnimeItem?>(null) }
    var actionItem by remember { mutableStateOf<AnimeItem?>(null) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                selectedTab = selectedTab,
                refreshing = state.refreshing,
                onTabSelected = { selectedTab = it },
                onRefresh = { viewModel.refresh() },
                onSettings = { viewModel.requestConfiguration() }
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    CalendarTab.WATCHLIST -> AnimeGrid(
                        title = "Watching",
                        subtitle = "Titles selected in Anime Calendar",
                        items = state.watchlist,
                        watchlistIds = state.watchlistIds,
                        mutatingIds = state.mutatingIds,
                        emptyMessage = "Your Anime Calendar watchlist is empty.",
                        onOpen = { detailItem = it },
                        onLongPress = { actionItem = it }
                    )
                    CalendarTab.CALENDAR -> CalendarSection(
                        items = state.calendar,
                        watchlistIds = state.watchlistIds,
                        mutatingIds = state.mutatingIds,
                        onOpen = { detailItem = it },
                        onLongPress = { actionItem = it }
                    )
                    CalendarTab.UPCOMING -> AnimeGrid(
                        title = "Upcoming",
                        subtitle = "Upcoming anime and scheduled releases",
                        items = state.upcoming,
                        showStartDate = true,
                        watchlistIds = state.watchlistIds,
                        mutatingIds = state.mutatingIds,
                        emptyMessage = "No upcoming titles were returned by the calendar.",
                        onOpen = { detailItem = it },
                        onLongPress = { actionItem = it }
                    )
                    CalendarTab.BROWSE -> BrowseSection(
                        current = state.currentSeason,
                        upcoming = state.upcoming,
                        watchlistIds = state.watchlistIds,
                        mutatingIds = state.mutatingIds,
                        onOpen = { detailItem = it },
                        onLongPress = { actionItem = it }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !state.error.isNullOrBlank(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
        ) {
            Text(
                text = state.error.orEmpty(),
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xDD7F1D1D))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }

    detailItem?.let { item ->
        AnimeDetailDialog(
            item = item,
            isWatchlisted = item.anilistId in state.watchlistIds,
            busy = item.anilistId in state.mutatingIds,
            onDismiss = { detailItem = null },
            onToggleWatchlist = { viewModel.toggleWatchlist(item) },
            onOpenNuvio = { openInNuvio(context, item, state.resolutions[item.anilistId]) }
        )
    }

    actionItem?.let { item ->
        AnimeActionsDialog(
            item = item,
            isWatchlisted = item.anilistId in state.watchlistIds,
            busy = item.anilistId in state.mutatingIds,
            onDismiss = { actionItem = null },
            onToggleWatchlist = { viewModel.toggleWatchlist(item) },
            onOpenNuvio = { openInNuvio(context, item, state.resolutions[item.anilistId]) }
        )
    }

    if (state.needsConfiguration) {
        ConnectionDialog(
            initialBaseUrl = state.apiBaseUrl,
            onSave = viewModel::saveConnection
        )
    }
}

@Composable
private fun Header(
    selectedTab: CalendarTab,
    refreshing: Boolean,
    onTabSelected: (CalendarTab) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color(0xFF10121A))
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            modifier = Modifier
                .width(126.dp)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Anime Calendar",
                fontSize = 15.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1
            )
            Text(
                "TV",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        CalendarTab.entries.forEach { tab ->
            TvNavButton(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) }
            ) {
                Icon(
                    imageVector = when (tab) {
                        CalendarTab.WATCHLIST -> Icons.Default.Star
                        CalendarTab.CALENDAR -> Icons.Default.CalendarMonth
                        CalendarTab.UPCOMING -> Icons.Default.Upcoming
                        CalendarTab.BROWSE -> Icons.Default.Movie
                    },
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(tab.label, maxLines = 1, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.weight(1f))

        TvNavButton(selected = false, onClick = onRefresh) {
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        TvNavButton(selected = false, onClick = onSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Connection",
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun TvNavButton(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    CompositionLocalProvider(LocalContentColor provides if (focused) Color.Black else Color.White) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(
                    when {
                        focused -> Color.White
                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                        else -> Color.White.copy(alpha = 0.07f)
                    }
                )
                .clickable(onClick = onClick)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun AnimeGrid(
    title: String,
    subtitle: String,
    items: List<AnimeItem>,
    showStartDate: Boolean = false,
    watchlistIds: Set<Int>,
    mutatingIds: Set<Int>,
    emptyMessage: String,
    onOpen: (AnimeItem) -> Unit,
    onLongPress: (AnimeItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp, vertical = 20.dp)
    ) {
        SectionTitle(title, subtitle)
        Spacer(Modifier.height(16.dp))

        if (items.isEmpty()) {
            EmptyMessage(emptyMessage)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 185.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { "${it.anilistId}:${it.episodeNumber ?: -1}:${it.airingAtEpochSeconds ?: -1}" }) { item ->
                    AnimeCard(
                        item = item,
                        isWatchlisted = item.anilistId in watchlistIds,
                        busy = item.anilistId in mutatingIds,
                        onClick = { onOpen(item) },
                        onLongPress = { onLongPress(item) },
                        showStartDate = showStartDate
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarSection(
    items: List<AnimeItem>,
    watchlistIds: Set<Int>,
    mutatingIds: Set<Int>,
    onOpen: (AnimeItem) -> Unit,
    onLongPress: (AnimeItem) -> Unit
) {
    val grouped = remember(items) {
        items
            .sortedBy { it.airingAtEpochSeconds ?: Long.MAX_VALUE }
            .groupBy(::calendarDayKey)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Box(Modifier.padding(horizontal = 30.dp)) {
                SectionTitle("Calendar", "Airing schedule from your Anime Calendar")
            }
        }

        if (grouped.isEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 30.dp)) {
                    EmptyMessage("No calendar entries are available.")
                }
            }
        }

        grouped.forEach { (dayKey, dayItems) ->
            val dayLabel = calendarDayLabel(dayItems.firstOrNull(), dayKey)
            item(key = "header:$dayKey") {
                Text(
                    text = dayLabel,
                    modifier = Modifier.padding(horizontal = 30.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item(key = "row:$dayKey") {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(dayItems, key = { "${it.anilistId}:${it.episodeNumber ?: -1}" }) { item ->
                        AnimeCard(
                            item = item,
                            isWatchlisted = item.anilistId in watchlistIds,
                            busy = item.anilistId in mutatingIds,
                            onClick = { onOpen(item) },
                            onLongPress = { onLongPress(item) },
                            compact = true,
                            scheduleOverride = calendarTimeLabel(item)
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BrowseSection(
    current: List<AnimeItem>,
    upcoming: List<AnimeItem>,
    watchlistIds: Set<Int>,
    mutatingIds: Set<Int>,
    onOpen: (AnimeItem) -> Unit,
    onLongPress: (AnimeItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Box(Modifier.padding(horizontal = 30.dp)) {
                SectionTitle("Browse", "Current and upcoming seasons")
            }
        }

        item {
            BrowseRow(
                heading = "Current season",
                items = current,
                watchlistIds = watchlistIds,
                mutatingIds = mutatingIds,
                onOpen = onOpen,
                onLongPress = onLongPress
            )
        }

        item {
            BrowseRow(
                heading = "Upcoming season",
                items = upcoming,
                watchlistIds = watchlistIds,
                mutatingIds = mutatingIds,
                onOpen = onOpen,
                onLongPress = onLongPress
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BrowseRow(
    heading: String,
    items: List<AnimeItem>,
    watchlistIds: Set<Int>,
    mutatingIds: Set<Int>,
    onOpen: (AnimeItem) -> Unit,
    onLongPress: (AnimeItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = heading,
            modifier = Modifier.padding(horizontal = 30.dp),
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        if (items.isEmpty()) {
            Box(Modifier.padding(horizontal = 30.dp)) {
                Text("Nothing returned for this season.", color = Color.White.copy(alpha = 0.6f))
            }
        } else {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.anilistId }) { item ->
                    AnimeCard(
                        item = item,
                        isWatchlisted = item.anilistId in watchlistIds,
                        busy = item.anilistId in mutatingIds,
                        onClick = { onOpen(item) },
                        onLongPress = { onLongPress(item) },
                        compact = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .padding(24.dp)
    ) {
        Text(message, color = Color.White.copy(alpha = 0.68f))
    }
}

@Composable
private fun AnimeCard(
    item: AnimeItem,
    isWatchlisted: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    compact: Boolean = false,
    showStartDate: Boolean = false,
    scheduleOverride: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    val width = if (compact) 174.dp else 188.dp
    val posterHeight = if (compact) 245.dp else 264.dp
    val shape = RoundedCornerShape(13.dp)

    Column(
        modifier = Modifier
            .width(width)
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.045f))
            .then(
                if (focused) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f), shape)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (!native.keyCode.isSelectKey()) return@onPreviewKeyEvent false
                when (native.action) {
                    KeyEvent.ACTION_DOWN -> {
                        val heldMs = native.eventTime - native.downTime
                        if (!longPressFired && (native.repeatCount > 0 || heldMs >= 650L) && heldMs >= 650L) {
                            longPressFired = true
                            onLongPress()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.ACTION_UP -> {
                        if (longPressFired) {
                            longPressFired = false
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .focusable()
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF242632))
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (isWatchlisted) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xDD171922))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Watching", color = Color.White, fontSize = 11.sp)
                }
            }
            if (busy) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                }
            }
        }

        scheduleOverride
            ?.takeIf(String::isNotBlank)
            ?.let { schedule ->
                Text(
                    text = schedule,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 5.dp, top = 8.dp, end = 5.dp)
                )
            }

        Text(
            text = item.displayTitle,
            color = Color.White,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp)
        )

        val meta = buildList {
            item.format?.takeIf(String::isNotBlank)?.let(::add)
            item.year?.let { add(it.toString()) }
            item.episodeNumber?.let { add("Ep $it") }
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 0.dp)
            )
        }

        if (showStartDate) {
            Text(
                text = formatStartDate(item.startDateLabel),
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
            )
        }

        if (scheduleOverride == null) {
            item.scheduleLabel
                ?.takeIf(String::isNotBlank)
                ?.let { schedule ->
                    Text(
                        text = schedule,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
                    )
                }
        }
    }
}

private fun calendarDayKey(item: AnimeItem): String {
    item.airingAtEpochSeconds?.let { epoch ->
        return Instant.ofEpochSecond(epoch)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }

    return item.scheduleLabel
        ?.substringBefore(" · ")
        ?.takeIf(String::isNotBlank)
        ?: "Schedule"
}

private fun calendarDayLabel(item: AnimeItem?, fallback: String): String {
    item?.airingAtEpochSeconds?.let { epoch ->
        return DateTimeFormatter.ofPattern("EEEE d MMMM")
            .format(
                Instant.ofEpochSecond(epoch)
                    .atZone(ZoneId.systemDefault())
            )
    }

    return runCatching {
        LocalDate.parse(fallback)
            .format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
    }.getOrDefault(fallback)
}

private fun calendarTimeLabel(item: AnimeItem): String? {
    item.airingAtEpochSeconds?.let { epoch ->
        return DateTimeFormatter.ofPattern("HH:mm")
            .format(
                Instant.ofEpochSecond(epoch)
                    .atZone(ZoneId.systemDefault())
            )
    }

    return item.scheduleLabel
        ?.substringAfter(" · ", "")
        ?.takeIf(String::isNotBlank)
}

private fun formatStartDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "Start date TBA"

    val normalized = raw.trim()

    val parsed = listOf(
        "yyyy-MM-dd",
        "yyyy-MM"
    ).firstNotNullOfOrNull { pattern ->
        runCatching {
            when (pattern) {
                "yyyy-MM-dd" -> LocalDate.parse(normalized)
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                else -> {
                    val parts = normalized.split("-")
                    val year = parts.getOrNull(0)?.toIntOrNull() ?: return@runCatching null
                    val month = parts.getOrNull(1)?.toIntOrNull() ?: return@runCatching null
                    java.time.YearMonth.of(year, month)
                        .format(DateTimeFormatter.ofPattern("MMM yyyy"))
                }
            }
        }.getOrNull()
    }

    return parsed?.let { "Starts $it" } ?: "Starts $normalized"
}

@Composable
private fun AnimeDetailDialog(
    item: AnimeItem,
    isWatchlisted: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onOpenNuvio: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(item.displayTitle, fontWeight = FontWeight.Bold)
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(190.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF242632))
                ) {
                    if (!item.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val facts = buildList {
                        item.format?.let(::add)
                        item.year?.let { add(it.toString()) }
                        item.totalEpisodes?.let { add("$it episodes") }
                        item.status?.let(::add)
                    }.joinToString(" · ")
                    if (facts.isNotBlank()) {
                        Text(facts, color = MaterialTheme.colorScheme.primary)
                    }
                    item.scheduleLabel?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(it)
                        }
                    }
                    Text(
                        text = item.description ?: "No synopsis is available for this title.",
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Long-press any poster for quick actions.",
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenNuvio) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Open in Nuvio")
                }
                Button(
                    onClick = onToggleWatchlist,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isWatchlisted) Color(0xFF7A3030) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(if (isWatchlisted) Icons.Default.Check else Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (isWatchlisted) "Remove" else "Add to Watching")
                }
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ConnectionDialog(
    initialBaseUrl: String,
    onSave: (String, String) -> Unit
) {
    var baseUrl by rememberSaveable(initialBaseUrl) { mutableStateOf(initialBaseUrl) }
    var token by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Connect Anime Calendar TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the TV API token configured on the sync server. This is stored only on this device.",
                    color = Color.White.copy(alpha = 0.72f)
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API URL") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("TV API token") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                enabled = baseUrl.isNotBlank() && token.isNotBlank(),
                onClick = { onSave(baseUrl, token) }
            ) {
                Text("Connect")
            }
        }
    )
}

@Composable
private fun AnimeActionsDialog(
    item: AnimeItem,
    isWatchlisted: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onOpenNuvio: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Text(
                "Quick actions",
                color = Color.White.copy(alpha = 0.7f)
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        onOpenNuvio()
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Open in Nuvio")
                }
                Button(
                    onClick = onToggleWatchlist,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isWatchlisted) Color(0xFF7A3030) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isWatchlisted) "Remove from Watching" else "Add to Watching")
                }
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun Int.isSelectKey(): Boolean {
    return this == KeyEvent.KEYCODE_DPAD_CENTER ||
        this == KeyEvent.KEYCODE_ENTER ||
        this == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        this == KeyEvent.KEYCODE_BUTTON_A
}

private fun openInNuvio(
    context: Context,
    item: AnimeItem,
    resolution: NuvioResolution?
) {
    val uri = if (resolution != null) {
        Uri.Builder()
            .scheme("nuvio")
            .authority("detail")
            .appendPath(resolution.contentType)
            .appendPath(resolution.contentId)
            .build()
    } else {
        Uri.Builder()
            .scheme("nuvio")
            .authority("search")
            .appendQueryParameter("q", item.displayTitle)
            .appendQueryParameter("open", "detail")
            .build()
    }

    val packages = listOf("com.nuvio.tv.prefetch4k", "com.nuvio.tv")
    for (packageName in packages) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
        }
    }

    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
