package dev.goutham.wallbreaker

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** App-lifetime scope: the POST must survive early dismissal of the 3-second overlay. */
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

sealed interface SaveState {
    data object Saving : SaveState
    data class Saved(val title: String?) : SaveState
    data object NoUrl : SaveState
    data object NoCredentials : SaveState
    data class Failed(
        val message: String,
        val credsProblem: Boolean = false,
        val retriable: Boolean = false,
    ) : SaveState
}

class SaveViewModel : ViewModel() {
    private val _state = MutableStateFlow<SaveState>(SaveState.Saving)
    val state: StateFlow<SaveState> = _state

    private var started = false
    private var sendUrl: String? = null
    private var creds: Credentials? = null

    fun start(context: Context, sharedText: String?) {
        if (started) return           // idempotent across re-creation
        started = true
        val appCtx = context.applicationContext
        val url = UrlExtractor.firstUrl(sharedText)
        if (url == null) {
            _state.value = SaveState.NoUrl
            return
        }
        appScope.launch {
            val c = CredentialStore.load(appCtx)
            if (c == null) {
                _state.value = SaveState.NoCredentials
                return@launch
            }
            creds = c
            // Wrap Medium articles through the Freedium mirror; pass anything
            // else straight through (Freedium can't unlock non-Medium URLs).
            sendUrl = Freedium.process(url)
            post()
        }
    }

    /**
     * A fresh share arrived on an already-live instance (rare: the OS reused
     * this activity instead of creating a new one). Reset and process it as if
     * new, so a second share is never silently dropped for a stale card.
     */
    fun restart(context: Context, sharedText: String?) {
        started = false
        sendUrl = null
        creds = null
        _state.value = SaveState.Saving
        start(context, sharedText)
    }

    fun retry() {
        _state.value = SaveState.Saving
        appScope.launch { post() }
    }

    private fun post() {
        val url = sendUrl ?: return
        val c = creds ?: return
        val result = InstapaperClient.add(c, url)
        Log.i("Wallbreaker", "add -> $result")   // status + title only; never credentials
        _state.value = when (result) {
            is InstapaperClient.AddResult.Saved -> SaveState.Saved(result.title)
            InstapaperClient.AddResult.BadCredentials ->
                SaveState.Failed("Instapaper rejected your credentials", credsProblem = true)
            InstapaperClient.AddResult.BadUrl ->
                SaveState.Failed("Instapaper rejected the URL")
            is InstapaperClient.AddResult.ServerError ->
                SaveState.Failed("Instapaper error ${result.code}", retriable = true)
            is InstapaperClient.AddResult.NetworkError ->
                SaveState.Failed("Network error — are you online?", retriable = true)
        }
    }
}
