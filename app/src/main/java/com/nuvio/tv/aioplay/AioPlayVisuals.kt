package com.nuvio.tv.aioplay

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Restrained slate / smoke palette. The old cyan-blue-violet treatment was
// deliberately vivid; AIOPlay now uses lower-saturation accents so artwork,
// focus and typography carry more of the visual hierarchy.
internal val AioPlayAccentCyan = Color(0xFF9AADB5)
internal val AioPlayAccentBlue = Color(0xFF71859B)
internal val AioPlayAccentViolet = Color(0xFF837A8D)
internal val AioPlayAccentWarm = Color(0xFF9A8D7D)

internal val AioPlayAccentGradient = Brush.linearGradient(
    colors = listOf(
        AioPlayAccentCyan,
        AioPlayAccentBlue,
        AioPlayAccentViolet
    )
)

internal val AioPlayBackgroundGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF0A0E13),
        Color(0xFF11161D),
        Color(0xFF171920)
    )
)

// The hero scrims keep navigation/text readable while leaving the right side of
// the focused artwork visible enough to feel cinematic.
internal val AioPlayHeroSideGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xF20A0E13),
        Color(0xD90A0E13),
        Color(0x7A0A0E13),
        Color(0x260A0E13)
    )
)

internal val AioPlayHeroBottomGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color(0x28070A0E),
        Color(0xB8070A0E),
        Color(0xF2070A0E)
    )
)

internal val AioPlayRailGlass = Color(0xC20A0E13)
internal val AioPlayTopGlass = Color(0xA612171E)
internal val AioPlayGlass = Color(0x9911161D)
internal val AioPlayPillIdle = Color(0xE61A2028)
internal val AioPlayPillSelected = Color(0xF0273039)
internal val AioPlayDetailCard = Color(0xF20D1117)
internal val AioPlayContentDim = Color.Black.copy(alpha = 0.28f)
internal val AioPlayFocusSheen = Color.White.copy(alpha = 0.08f)
