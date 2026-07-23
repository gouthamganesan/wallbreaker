package dev.goutham.wallbreaker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class ShareActivity : ComponentActivity() {
    private val vm: SaveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.start(this, intent?.getStringExtra(Intent.EXTRA_TEXT))
        setContent {
            MaterialTheme {
                val state by vm.state.collectAsState()
                OverlayScreen(
                    state = state,
                    onDismiss = { finish() },
                    onRetry = vm::retry,
                    onOpenSetup = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    // Safety net: if the OS ever hands a new share to this live instance instead
    // of creating a fresh one, process it rather than showing a stale card.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.restart(this, intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
