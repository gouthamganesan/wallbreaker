package dev.goutham.wallbreaker

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** App-lifetime scope: the local save must finish even if the overlay is dismissed early. */
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

sealed interface SaveState {
    data object Working : SaveState
    data class Saved(
        val title: String?,
        val host: String?,
        val viaFreedium: Boolean,
        /** The article was already in Instapaper; this share refreshed it. */
        val wasAlreadySaved: Boolean = false,
        /** Delivery is confirmed, not just queued. */
        val confirmed: Boolean = false,
    ) : SaveState
    data class Failed(val message: String) : SaveState
    data object NoCredentials : SaveState          // no Instapaper account at all
    data object NeedsFullApi : SaveState           // HTML file shared, but no API keys
    data class Unusable(val message: String) : SaveState
}

/**
 * The share path is local-first: extract the payload, route it, write the
 * receipt, schedule the sync — the local save can never fail on the network.
 *
 * It then **waits, briefly, for the delivery to actually land**. That wait is
 * not cosmetic: while the overlay is on screen the app is a foreground process,
 * which is the one state no OEM battery manager will freeze. Dismissing the
 * card at a fixed 3s was killing in-flight requests on Samsung — the sync then
 * only completed the next time the app happened to be opened. Holding the card
 * for the round trip (~1–3s in practice) is what makes a share land there and
 * then, and it lets the card report the real article title instead of an
 * optimistic guess.
 *
 * Past [CONFIRM_TIMEOUT_MS] the card stops waiting and says so; WorkManager
 * carries on in the background from there.
 */
class SaveViewModel : ViewModel() {
    private val _state = MutableStateFlow<SaveState>(SaveState.Working)
    val state: StateFlow<SaveState> = _state

    private var started = false

    fun start(context: Context, sharedText: String?, streamUri: Uri?, type: String?) {
        if (started) return
        started = true
        val app = context.applicationContext
        appScope.launch {
            if (CredentialStore.load(app) == null) {
                _state.value = SaveState.NoCredentials
                return@launch
            }
            val payload = intake(app, sharedText, streamUri, type)
            if (payload == null) {
                _state.value = SaveState.Unusable("No link or HTML found in the share")
                return@launch
            }
            when (val result = SendRouter.plan(app, payload)) {
                is RouteResult.Ready -> deliver(app, result.save)
                RouteResult.NeedsFullApi -> _state.value = SaveState.NeedsFullApi
                RouteResult.Unusable -> _state.value = SaveState.Unusable("Couldn't read the shared HTML")
            }
        }
    }

    private suspend fun deliver(app: Context, save: PlannedSave) {
        val enqueued = ShareRepository.createAndEnqueue(app, save)
        val viaFreedium = save.route == Route.FREEDIUM_CONTENT || save.route == Route.FREEDIUM_WRAP

        fun queued(title: String?) = SaveState.Saved(
            title = title,
            host = save.host,
            viaFreedium = viaFreedium,
            wasAlreadySaved = enqueued.wasAlreadySaved,
            confirmed = false,
        )

        _state.value = queued(save.title)

        val settled = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) {
            ShareRepository.awaitSettled(app, enqueued.id)
        }

        _state.value = when {
            settled == null -> queued(save.title)          // still in flight; the worker takes over
            settled.status == SyncStatus.SYNCED.name -> SaveState.Saved(
                title = settled.title ?: save.title,
                host = save.host,
                viaFreedium = viaFreedium,
                wasAlreadySaved = enqueued.wasAlreadySaved,
                confirmed = true,
            )
            else -> SaveState.Failed(settled.error ?: "Couldn't reach Instapaper")
        }
    }

    /** A fresh share arrived on an already-live instance. Reset and process it. */
    fun restart(context: Context, sharedText: String?, streamUri: Uri?, type: String?) {
        started = false
        _state.value = SaveState.Working
        start(context, sharedText, streamUri, type)
    }

    /** Turn the raw intent extras into a [SharePayload], or null if unusable. */
    private fun intake(context: Context, sharedText: String?, streamUri: Uri?, type: String?): SharePayload? {
        // A shared HTML file arrives as a content: URI stream.
        if (streamUri != null) {
            val html = readStream(context, streamUri)
            val looksHtml = type?.contains("html", ignoreCase = true) == true ||
                html?.trimStart()?.startsWith("<") == true
            if (!html.isNullOrBlank() && looksHtml) return SharePayload.Html(html)
        }
        // A shared link arrives as plain text ("Title https://…").
        UrlExtractor.firstUrl(sharedText)?.let { return SharePayload.Link(it) }
        // Some apps paste raw HTML into EXTRA_TEXT.
        val text = sharedText?.trim()
        if (text != null && text.startsWith("<") && text.contains("</")) return SharePayload.Html(text)
        return null
    }

    private fun readStream(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.reader(Charsets.UTF_8).readText().take(MAX_HTML_CHARS)
        }
    }.getOrNull()

    private companion object {
        const val MAX_HTML_CHARS = 4_000_000

        /**
         * How long the card is willing to stay up waiting for confirmation.
         * A healthy Freedium fetch + content upload measures ~2–3s; this leaves
         * headroom for one slow leg without holding the user hostage.
         */
        const val CONFIRM_TIMEOUT_MS = 9_000L
    }
}
