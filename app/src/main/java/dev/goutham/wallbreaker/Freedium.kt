package dev.goutham.wallbreaker

import java.net.URI

/**
 * Freedium paywall-mirror wrapping. Two facts drive this design:
 *
 *  1. .cfd mirror hosts are volatile — freedium.cfd's DNS stopped resolving
 *     (mid-2026); the live official mirror is freedium-mirror.cfd. So the base
 *     is a configurable, ordered fallback chain, never a hardcoded literal.
 *     When the head dies, move a live mirror to the front (one-line change).
 *
 *  2. Freedium only unlocks Medium-hosted articles, so wrapping is GATED — but
 *     the gate is **exactly the domain allowlist in Settings, and nothing
 *     else**. There is deliberately no additional "looks like a Medium URL"
 *     heuristic hiding behind it. An earlier version also routed any URL whose
 *     slug carried Medium's 12-hex post id, which meant links went through a
 *     third-party mirror without appearing anywhere in the user's list — the
 *     one thing a visible allowlist exists to prevent. If a domain should be
 *     unlocked, it is in the list; if it isn't in the list, it is saved as-is.
 *
 * Wrapping rule: BASE + "/" + rawURL — scheme kept, inner URL NOT
 * percent-encoded (form-encoding of the whole value happens later, in the
 * HTTP client, which is a separate concern).
 */
object Freedium {

    /** Ordered fallback chain; head is the active mirror. Edit to switch. */
    val BASES: List<String> = listOf(
        "https://freedium-mirror.cfd",   // live official mirror
        "https://freedium.cfd",          // original — DNS not resolving as of 2026-07
    )

    val base: String get() = BASES.first()

    private fun hostOf(url: String): String? = try {
        URI(url).host?.lowercase()
    } catch (e: Exception) {
        null
    }

    /** True iff [url]'s host equals or is a subdomain of any allowlisted domain. */
    fun matchesAllowlist(url: String, domains: List<String>): Boolean {
        val host = hostOf(url) ?: return false
        return domains.any { d ->
            val dom = d.lowercase()
            host == dom || host.endsWith(".$dom")
        }
    }

    /** True iff [url] already points at the [mirror] host (avoids double-wrapping). */
    fun isAtMirror(url: String, mirror: String): Boolean {
        val host = hostOf(url) ?: return false
        val mirrorHost = hostOf(mirror) ?: return false
        return host == mirrorHost
    }

    /**
     * The routing decision, and the *only* one. A link goes through Freedium
     * iff routing is enabled, it isn't already a mirror URL, and its domain is
     * on the user's allowlist. What Settings shows is exactly what gets routed.
     */
    fun shouldRoute(url: String, settings: AppSettings): Boolean {
        if (!settings.freediumEnabled) return false
        if (isAtMirror(url, settings.freediumMirror)) return false
        return matchesAllowlist(url, settings.freediumDomains)
    }

    /** BASE + "/" + rawURL, scheme kept, inner URL left raw. */
    fun wrap(url: String): String = wrap(url, base)

    /** [base] + "/" + rawURL, scheme kept, inner URL left raw. */
    fun wrap(url: String, base: String): String = base.trimEnd('/') + "/" + url

    // Freedium stamps its own name into the page <title>: "Real Title - Freedium".
    private val MIRROR_SUFFIX = Regex("""\s*[-–—|]\s*Freedium\s*$""", RegexOption.IGNORE_CASE)

    /**
     * The article title as the *original* publisher wrote it.
     *
     * Uploading Freedium's HTML as Instapaper `content` means Instapaper never
     * crawls the real page, so whatever title we send is the title the user
     * sees. Send Freedium's raw <title> and the mirror leaks into an inbox that
     * otherwise looks untouched — the whole point of uploading content under
     * the canonical URL was to keep it invisible.
     */
    fun cleanTitle(raw: String?): String? =
        raw?.let { MIRROR_SUFFIX.replace(it, "") }?.trim()?.ifBlank { null }
}
