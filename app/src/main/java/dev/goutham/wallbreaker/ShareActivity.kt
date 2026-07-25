package dev.goutham.wallbreaker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.goutham.wallbreaker.ui.theme.WallbreakerTheme

class ShareActivity : ComponentActivity() {
    private val vm: SaveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        vm.start(this, i?.getStringExtra(Intent.EXTRA_TEXT), i?.streamUri(), i?.type)
        setContent {
            WallbreakerTheme {
                val state by vm.state.collectAsState()
                OverlayScreen(
                    state = state,
                    onDismiss = { finish() },
                    onOpenSetup = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    // Safety net: the OS handed a new share to this live instance instead of a
    // fresh one — process it rather than showing a stale card.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.restart(this, intent.getStringExtra(Intent.EXTRA_TEXT), intent.streamUri(), intent.type)
    }
}

@Suppress("DEPRECATION")
private fun Intent.streamUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
