package dev.goutham.wallbreaker

import android.util.Log

/**
 * The one logging seam. Everything interesting in this app happens in a
 * background worker with no UI attached, so `adb logcat -s Wallbreaker` is the
 * only way to see why a save did (or didn't) land. That makes logging a
 * feature, not debug scaffolding — it stays on in release.
 *
 * The contract that keeps it safe to leave on: **never** log a credential, an
 * OAuth token, or article HTML. Response bodies are truncated; request bodies
 * are never touched. Sizes and status codes carry the diagnostic value anyway.
 */
object WbLog {
    const val TAG = "Wallbreaker"

    private const val SNIPPET = 300

    fun i(msg: String) {
        Log.i(TAG, msg)
    }

    fun w(msg: String, t: Throwable? = null) {
        if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg)
    }

    /** A response body, collapsed to one line and clipped. Safe: server-sent, no secrets. */
    fun snippet(text: String): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= SNIPPET) flat else flat.take(SNIPPET) + "…(${flat.length} chars)"
    }
}
