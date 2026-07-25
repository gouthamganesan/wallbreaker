package dev.goutham.wallbreaker

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goutham.wallbreaker.ui.RouteArrow
import dev.goutham.wallbreaker.ui.WallMark
import kotlinx.coroutines.delay

@Composable
fun OverlayScreen(
    state: SaveState,
    onDismiss: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    // Success self-dismisses after 3s; every other state waits for a human.
    LaunchedEffect(state) {
        if (state is SaveState.Saved) {
            delay(3_000)
            onDismiss()
        }
    }

    // Enter: card rises + fades; the wall mark's crack draws itself.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "enter")
    val crack by animateFloatAsState(if (shown) 1f else 0f, tween(420), label = "crack")

    // Once saved, the wall mark crossfades to the real Instapaper badge (the badge
    // swap IS the "delivered" signal).
    val saved = state is SaveState.Saved
    var showBadge by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(240); showBadge = true } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(bottom = 32.dp)
                .graphicsLayer {
                    alpha = enter
                    translationY = (1f - enter) * 40.dp.toPx()
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Leading(showBadge = showBadge && saved, crack = crack)
                Content(state = state, onOpenSetup = onOpenSetup, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun Leading(showBadge: Boolean, crack: Float) {
    Crossfade(targetState = showBadge, animationSpec = tween(200), label = "leading") { badge ->
        if (badge) {
            Image(
                painter = painterResource(R.drawable.ic_instapaper),
                contentDescription = "Instapaper",
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            WallMark(modifier = Modifier.size(30.dp), progress = crack)
        }
    }
}

@Composable
private fun Content(state: SaveState, onOpenSetup: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        SaveState.Working -> Text("Saving…", fontWeight = FontWeight.Medium)

        is SaveState.Saved -> Column {
            Text("Saved to Instapaper", fontWeight = FontWeight.SemiBold)
            if (state.viaFreedium) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RouteArrow(modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        "Unlocked via Freedium",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else {
                (state.title ?: state.host)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SaveState.NoCredentials -> Column {
            Text("Instapaper account not set up")
            TextButton(onClick = onOpenSetup) { Text("Set up") }
        }

        SaveState.NeedsFullApi -> Column {
            Text("Add Instapaper API keys to save HTML files")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpenSetup) { Text("Set up") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }

        is SaveState.Unusable -> Column {
            Text(state.message)
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}
