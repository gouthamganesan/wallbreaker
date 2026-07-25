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

/** App-lifetime scope: the local save must finish even if the overlay is dismissed early. */
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

sealed interface SaveState {
    data object Working : SaveState
    data class Saved(val title: String?, val host: String?, val viaFreedium: Boolean) : SaveState
    data object NoCredentials : SaveState          // no Instapaper account at all
    data object NeedsFullApi : SaveState           // HTML file shared, but no API keys
    data class Unusable(val message: String) : SaveState
}

/**
 * The share path is now local-first: extract the payload, route it, write the
 * receipt to the local store, and schedule background sync — then the overlay
 * shows "Saved" immediately. The actual Instapaper POST happens in [SyncWorker];
 * this view model never blocks on the network.
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
                is RouteResult.Ready -> {
                    ShareRepository.createAndEnqueue(app, result.save)
                    val via = result.save.route == Route.FREEDIUM_CONTENT ||
                        result.save.route == Route.FREEDIUM_WRAP
                    _state.value = SaveState.Saved(result.save.title, result.save.host, via)
                }
                RouteResult.NeedsFullApi -> _state.value = SaveState.NeedsFullApi
                RouteResult.Unusable -> _state.value = SaveState.Unusable("Couldn't read the shared HTML")
            }
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
    }
}
