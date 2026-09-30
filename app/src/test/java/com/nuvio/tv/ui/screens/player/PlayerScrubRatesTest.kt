package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerScrubRatesTest {

    @Test
    fun stepMsForHold_baseStepBeforeThreshold() {
        assertEquals(PlayerScrubRates.STEP_SHORT_MS, PlayerScrubRates.stepMsForHold(0L))
        assertEquals(PlayerScrubRates.STEP_SHORT_MS, PlayerScrubRates.stepMsForHold(2_999L))
    }

    @Test
    fun stepMsForHold_doublesAtThreshold() {
        assertEquals(PlayerScrubRates.STEP_MEDIUM_MS, PlayerScrubRates.stepMsForHold(3_000L))
        assertEquals(PlayerScrubRates.STEP_MEDIUM_MS, PlayerScrubRates.stepMsForHold(600_000L))
    }

    @Test
    fun deltaMsForHold_appliesDirection() {
        assertEquals(-PlayerScrubRates.STEP_SHORT_MS, PlayerScrubRates.deltaMsForHold(0L, forward = false))
        assertEquals(PlayerScrubRates.STEP_MEDIUM_MS, PlayerScrubRates.deltaMsForHold(5_000L, forward = true))
    }

    @Test
    fun stepMsForHold_negativeHoldUsesBaseStep() {
        assertEquals(PlayerScrubRates.STEP_SHORT_MS, PlayerScrubRates.stepMsForHold(-1L))
    }

    @org.junit.Test
    fun `selected interval drives tap seek and doubles on long hold`() {
        org.junit.Assert.assertEquals(15_000L, PlayerScrubRates.stepMsForHold(0L, 15_000L))
        org.junit.Assert.assertEquals(30_000L, PlayerScrubRates.stepMsForHold(3_000L, 15_000L))
        org.junit.Assert.assertEquals(30_000L, PlayerScrubRates.stepMsForHold(0L, 30_000L))
        org.junit.Assert.assertEquals(60_000L, PlayerScrubRates.stepMsForHold(3_000L, 30_000L))
    }

    @org.junit.Test
    fun `selected interval keeps seek direction`() {
        org.junit.Assert.assertEquals(25_000L, PlayerScrubRates.deltaMsForHold(0L, true, 25_000L))
        org.junit.Assert.assertEquals(-25_000L, PlayerScrubRates.deltaMsForHold(0L, false, 25_000L))
    }
}
