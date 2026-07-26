package dev.goutham.wallbreaker

/**
 * Non-secret configuration — the Android counterpart of the CLI's
 * `~/.config/instapaper/config.json`. Nothing here is a secret: whether Freedium
 * routing is on, which mirror to use, and the user-editable domain allowlist
 * that decides which links get routed.
 *
 * Deliberately Android-free so the routing rules that consume it ([Freedium])
 * stay testable on a plain JVM; the SharedPreferences half lives next door in
 * [AppSettingsStore].
 */
data class AppSettings(
    val freediumEnabled: Boolean = true,
    val freediumMirror: String = DEFAULT_MIRROR,
    val freediumDomains: List<String> = DEFAULT_DOMAINS,
) {
    companion object {
        const val DEFAULT_MIRROR = "https://freedium-mirror.cfd"

        // Mirrors the Chrome extension's DEFAULT_WRAP_DOMAINS (lib/freedium.js):
        // Medium proper plus the known custom-domain publications Freedium can
        // unlock. Suffix-matched, so "medium.com" also covers *.medium.com.
        // towardsdatascience.com is deliberately absent — it left Medium in
        // Feb 2025 and Freedium won't unwrap it. Users can remove any of these.
        val DEFAULT_DOMAINS = listOf(
            "medium.com",
            "betterprogramming.pub",
            "levelup.gitconnected.com",
            "plainenglish.io",
            "blog.devgenius.io",
            "infosecwriteups.com",
            "uxdesign.cc",
            "medium.freecodecamp.org",
        )
    }
}
