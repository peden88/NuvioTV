package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.NextEpisodeThresholdMode
import com.nuvio.tv.data.repository.SkipInterval
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeStreamPrefetchPolicyTest {

    @Test
    fun `percentage threshold starts prefetch one minute early`() {
        val duration = 40L * 60L * 1_000L
        // 98% starts post-play at 48s remaining, therefore prefetch at 108s.
        assertFalse(base(positionMs = duration - 109_000L, durationMs = duration))
        assertTrue(base(positionMs = duration - 108_000L, durationMs = duration))
    }

    @Test
    fun `outro-aware threshold starts prefetch before outro`() {
        val duration = 30L * 60L * 1_000L
        val outro = SkipInterval(
            startTime = 25.0 * 60.0,
            endTime = 29.0 * 60.0 + 50.0,
            type = "outro",
            provider = "test",
        )
        // Outro starts at 25:00 and ends close enough to the file end that
        // auto-next keys off the outro start. Prefetch therefore arms at 24:00.
        assertFalse(base(
            positionMs = 23L * 60L * 1_000L + 59_000L,
            durationMs = duration,
            skipIntervals = listOf(outro),
            thresholdMode = NextEpisodeThresholdMode.MINUTES_BEFORE_END,
            thresholdMinutesBeforeEnd = 1f,
        ))
        assertTrue(base(
            positionMs = 24L * 60L * 1_000L,
            durationMs = duration,
            skipIntervals = listOf(outro),
            thresholdMode = NextEpisodeThresholdMode.MINUTES_BEFORE_END,
            thresholdMinutesBeforeEnd = 1f,
        ))
    }

    @Test
    fun `prefetch stays off when autoplay is disabled`() {
        assertFalse(base(autoPlayNextEpisodeEnabled = false))
    }

    @Test
    fun `prefetch stays off for unaired next episode`() {
        assertFalse(base(nextEpisodeHasAired = false))
    }

    @Test
    fun `prefetch stays off for cloud playback and playback errors`() {
        assertFalse(base(isCloudPlayback = true))
        assertFalse(base(hasPlaybackError = true))
    }

    private fun base(
        positionMs: Long = 39L * 60L * 1_000L,
        durationMs: Long = 40L * 60L * 1_000L,
        skipIntervals: List<SkipInterval> = emptyList(),
        thresholdMode: NextEpisodeThresholdMode = NextEpisodeThresholdMode.PERCENTAGE,
        thresholdPercent: Float = 98f,
        thresholdMinutesBeforeEnd: Float = 1f,
        isLive: Boolean = false,
        hasRenderedFirstFrame: Boolean = true,
        hasPlaybackError: Boolean = false,
        autoPlayNextEpisodeEnabled: Boolean = true,
        nextEpisodeHasAired: Boolean = true,
        hasNextEpisode: Boolean = true,
        isCloudPlayback: Boolean = false,
    ): Boolean = shouldPrefetchNextEpisodeStreams(
        positionMs = positionMs,
        durationMs = durationMs,
        skipIntervals = skipIntervals,
        thresholdMode = thresholdMode,
        thresholdPercent = thresholdPercent,
        thresholdMinutesBeforeEnd = thresholdMinutesBeforeEnd,
        isLive = isLive,
        hasRenderedFirstFrame = hasRenderedFirstFrame,
        hasPlaybackError = hasPlaybackError,
        autoPlayNextEpisodeEnabled = autoPlayNextEpisodeEnabled,
        nextEpisodeHasAired = nextEpisodeHasAired,
        hasNextEpisode = hasNextEpisode,
        isCloudPlayback = isCloudPlayback,
    )
}
