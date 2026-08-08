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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
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
    onUnlockDomain: () -> Unit = {},
) {
    // A confirmed save has nothing left to protect, so it goes quickly. An
    // unconfirmed one deliberately lingers: a visible card means a foreground
    // process, and that is what stops the OS freezing the upload half-finished.
    // Failures wait for a human. Tapping dismisses either at any time.
    //
    // A card carrying an unlock offer is the exception among confirmed saves. It
    // is asking a question, and 1.6s is not long enough to read one, decide, and
    // hit a target — measured against automation it wasn't even long enough to
    // *find*. It only ever appears on links that went out still paywalled, so
    // the extra seconds are spent on the saves that have something to say.
    LaunchedEffect(state) {
        if (state is SaveState.Saved) {
            delay(
                when {
                    !state.confirmed -> 7_000
                    state.offer?.accepted == false -> 6_000
                    state.offer?.accepted == true -> 2_400
                    else -> 1_600
                },
            )
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
                Content(
                    state = state,
                    onOpenSetup = onOpenSetup,
                    onDismiss = onDismiss,
                    onUnlockDomain = onUnlockDomain,
                )
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

/**
 * The one-tap allowlist affordance, at the only moment the user actually knows
 * the domain needs it — and on the one screen that already has the domain in
 * hand. The alternative it replaces is: leave the app you were reading in, open
 * Wallbreaker, find Settings, and retype what the card was holding.
 *
 * Once accepted with the Full API configured there is nothing to render: the
 * card falls back into the ordinary save flow, and "Saving…" → "Unlocked via
 * Freedium" tells the story better than a confirmation line would.
 */
@Composable
private fun UnlockRow(offer: UnlockOffer, onAccept: () -> Unit) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    if (offer.accepted) {
        Text(
            "${offer.domain} added — unlocks from now on",
            style = MaterialTheme.typography.bodySmall,
            color = tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    TextButton(
        onClick = onAccept,
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp),
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
        Icon(
            Icons.Outlined.LockOpen,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tertiary,
        )
        Spacer(Modifier.size(8.dp))
        // No padding baked into the string: the label is also the accessibility
        // node and the handle every UI test grabs it by.
        Text(
            "Always unlock ${offer.domain}",
            style = MaterialTheme.typography.labelLarge,
            color = tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Content(
    state: SaveState,
    onOpenSetup: () -> Unit,
    onDismiss: () -> Unit,
    onUnlockDomain: () -> Unit,
) {
    when (state) {
        SaveState.Working -> Text("Saving…", fontWeight = FontWeight.Medium)

        is SaveState.Saved -> Column {
            Text(
                when {
                    state.wasAlreadySaved -> "Already in Instapaper"
                    state.confirmed -> "Saved to Instapaper"
                    else -> "Saving to Instapaper…"
                },
                fontWeight = FontWeight.SemiBold,
            )
            // The article title, once the delivery came back with one — it is the
            // only proof on screen that the *right* thing landed.
            val detail = state.title?.takeIf { it.isNotBlank() }
            when {
                detail != null ->
                    Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                state.viaFreedium ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RouteArrow(modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            "Unlocked via Freedium",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                else -> state.host?.takeIf { it.isNotBlank() }?.let {
                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
            state.offer?.let { UnlockRow(offer = it, onAccept = onUnlockDomain) }
        }

        is SaveState.Failed -> Column {
            Text("Couldn't reach Instapaper", fontWeight = FontWeight.SemiBold)
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onDismiss) { Text("Close") }
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
