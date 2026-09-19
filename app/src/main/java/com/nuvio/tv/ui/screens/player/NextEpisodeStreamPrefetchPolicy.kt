package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.NextEpisodeThresholdMode
import com.nuvio.tv.data.repository.SkipInterval

internal const val NEXT_EPISODE_STREAM_PREFETCH_LEAD_MS = 60_000L

/**
 * Arms next-episode stream discovery one minute before the same outro-aware
 * threshold that will hand playback to the next episode.
 *
 * This deliberately owns timing only. The current StreamPrefetchCache remains
 * responsible for single-flight deduplication, ranking, pre-resolution and
 * connection prewarming.
 */
internal fun shouldPrefetchNextEpisodeStreams(
    positionMs: Long,
    durationMs: Long,
    skipIntervals: List<SkipInterval>,
    thresholdMode: NextEpisodeThresholdMode,
    thresholdPercent: Float,
    thresholdMinutesBeforeEnd: Float,
    isLive: Boolean,
    hasRenderedFirstFrame: Boolean,
    hasPlaybackError: Boolean,
    autoPlayNextEpisodeEnabled: Boolean,
    nextEpisodeHasAired: Boolean,
    hasNextEpisode: Boolean,
    isCloudPlayback: Boolean,
    prefetchLeadMs: Long = NEXT_EPISODE_STREAM_PREFETCH_LEAD_MS,
): Boolean {
    if (isLive || !hasRenderedFirstFrame || hasPlaybackError || !autoPlayNextEpisodeEnabled) return false
    if (!nextEpisodeHasAired || !hasNextEpisode || isCloudPlayback) return false
    if (durationMs <= 0L || positionMs < 0L || positionMs >= durationMs) return false
    if (isShortPlaceholderDuration(durationMs)) return false

    val leadMs = prefetchLeadMs.coerceAtLeast(0L)
    val projectedPositionMs = (positionMs + leadMs).coerceAtMost(durationMs)

    return PlayerNextEpisodeRules.shouldShowNextEpisodeCard(
        positionMs = projectedPositionMs,
        durationMs = durationMs,
        skipIntervals = skipIntervals,
        thresholdMode = thresholdMode,
        thresholdPercent = thresholdPercent,
        thresholdMinutesBeforeEnd = thresholdMinutesBeforeEnd,
    )
}
