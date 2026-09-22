package com.nuvio.tv.aioplay

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal val AioPlayAccentCyan = Color(0xFF36D8FF)
internal val AioPlayAccentBlue = Color(0xFF1668FF)
internal val AioPlayAccentViolet = Color(0xFF7857FF)

internal val AioPlayAccentGradient = Brush.linearGradient(
    colors = listOf(
        AioPlayAccentCyan,
        AioPlayAccentBlue,
        AioPlayAccentViolet
    )
)

internal val AioPlayBackgroundGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF355F7F),
        Color(0xFF2F4D7F),
        Color(0xFF414A7F)
    )
)

internal val AioPlayGlass = Color(0x750F0F11)
internal val AioPlayPillIdle = Color(0xF51F1F25)
internal val AioPlayDetailCard = Color(0xFB121216)
internal val AioPlayContentDim = Color.Black.copy(alpha = 0.15f)
