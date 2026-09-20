package com.nuvio.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Enables focus/current-item driven vertical scrolling for truncated descriptions.
 * Off by default so existing layouts remain unchanged until the user opts in.
 */
val LocalAutoScrollDescriptionsEnabled = compositionLocalOf { false }

@Composable
fun AutoScrollOverflowText(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalAutoScrollDescriptionsEnabled.current
) {
    if (!enabled || !active || maxLines <= 0) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines.coerceAtLeast(1),
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }

    val density = LocalDensity.current
    val lineHeightDp = with(density) {
        val textUnit = if (style.lineHeight.value.isFinite() && style.lineHeight.value > 0f) {
            style.lineHeight
        } else {
            style.fontSize * 1.25f
        }
        runCatching { textUnit.toDp() }.getOrDefault(20.dp)
    }
    val viewportMaxHeight = lineHeightDp * maxLines
    val scrollState = rememberScrollState()

    LaunchedEffect(text, active, enabled, scrollState.maxValue) {
        if (!active || !enabled || scrollState.maxValue <= 0) {
            scrollState.scrollTo(0)
            return@LaunchedEffect
        }

        while (isActive && active && enabled) {
            scrollState.scrollTo(0)
            delay(1_250)
            val distance = scrollState.maxValue
            if (distance <= 0) break
            val duration = (distance * 12).coerceIn(2_500, 8_000)
            scrollState.animateScrollTo(
                value = distance,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing)
            )
            delay(1_250)
            scrollState.animateScrollTo(
                value = 0,
                animationSpec = tween(durationMillis = 700)
            )
            delay(900)
        }
    }

    Column(
        modifier = modifier
            .heightIn(max = viewportMaxHeight)
            .verticalScroll(scrollState, enabled = false)
    ) {
        Text(
            text = text,
            style = style,
            color = color
        )
    }
}
