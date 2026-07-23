package dev.goutham.wallbreaker

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun OverlayScreen(
    state: SaveState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    // The contract: success self-dismisses after 3s; every error waits for a human.
    LaunchedEffect(state) {
        if (state is SaveState.Saved) {
            delay(3_000)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(                       // tap anywhere outside the card = leave now;
                interactionSource = remember { MutableInteractionSource() },
                indication = null,            // the POST survives in appScope regardless
                onClick = onDismiss,
            )
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state) {
                    SaveState.Saving -> {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text("Saving to Instapaper…")
                    }
                    is SaveState.Saved -> {
                        Image(
                            painter = painterResource(R.drawable.ic_instapaper),
                            contentDescription = "Instapaper",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                        Column {
                            Text("Saved to Instapaper", fontWeight = FontWeight.SemiBold)
                            state.title?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    SaveState.NoUrl -> {
                        Text("No link found in the shared text")
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                    SaveState.NoCredentials -> {
                        Text("Instapaper account not set up")
                        TextButton(onClick = onOpenSetup) { Text("Set up") }
                    }
                    is SaveState.Failed -> {
                        Column {
                            Text(state.message)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (state.retriable) TextButton(onClick = onRetry) { Text("Retry") }
                                if (state.credsProblem) TextButton(onClick = onOpenSetup) { Text("Fix account") }
                                TextButton(onClick = onDismiss) { Text("Close") }
                            }
                        }
                    }
                }
            }
        }
    }
}
