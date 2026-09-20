package com.nuvio.tv.ui.screens.player

/**
 * Scrub / seek step sizes for remote D-pad and media keys.
 *
 * A single press uses the user-selected base interval. Holding the direction
 * for [LONG_HOLD_THRESHOLD_MS] doubles that interval, capped at 60 seconds,
 * while preserving the existing preview-then-commit seek gesture.
 */
object PlayerScrubRates {
    const val STEP_SHORT_MS = 10_000L
    const val LONG_HOLD_THRESHOLD_MS = 3_000L
    const val MAX_ACCELERATED_STEP_MS = 60_000L

    fun stepMsForHold(
        holdDurationMs: Long,
        baseStepMs: Long = STEP_SHORT_MS
    ): Long {
        val normalizedBase = baseStepMs.coerceIn(10_000L, 30_000L)
        return if (holdDurationMs >= LONG_HOLD_THRESHOLD_MS) {
            (normalizedBase * 2L).coerceAtMost(MAX_ACCELERATED_STEP_MS)
        } else {
            normalizedBase
        }
    }

    fun deltaMsForHold(
        holdDurationMs: Long,
        forward: Boolean,
        baseStepMs: Long = STEP_SHORT_MS
    ): Long {
        val step = stepMsForHold(holdDurationMs, baseStepMs)
        return if (forward) step else -step
    }
}
