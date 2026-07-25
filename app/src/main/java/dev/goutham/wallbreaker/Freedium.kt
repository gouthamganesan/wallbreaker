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
 *  2. Freedium only unlocks *.medium.com articles (URLs ending in Medium's
 *     12-hex-char post id). Wrapping anything else yields a broken save, so
 *     wrapping is GATED: Medium articles get wrapped, everything else is saved
 *     as-is.
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

    // Medium article URLs end in a 12-hex-char post id: ...-1a2b3c4d5e6f
    private val MEDIUM_SLUG = Regex("""-[0-9a-f]{12}(?:$|[/?#])""")

    private fun hostOf(url: String): String? = try {
        URI(url).host?.lowercase()
    } catch (e: Exception) {
        null
    }

    /** True iff [url] is a *.medium.com article Freedium can actually unlock. */
    fun looksLikeMediumArticle(url: String): Boolean {
        val host = hostOf(url) ?: return false
        val isMedium = host == "medium.com" || host.endsWith(".medium.com")
        return isMedium && MEDIUM_SLUG.containsMatchIn(url)
    }

    /** True iff [url]'s path carries Medium's 12-hex post-id fingerprint. */
    fun hasMediumSlug(url: String): Boolean = MEDIUM_SLUG.containsMatchIn(url)

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
     * The routing decision, honouring user settings. A link is routed through
     * Freedium iff routing is enabled, it isn't already a mirror URL, and either
     * its domain is on the allowlist OR it carries Medium's post-id slug (which
     * catches custom-domain Medium publications the user hasn't listed).
     */
    fun shouldRoute(url: String, settings: AppSettings): Boolean {
        if (!settings.freediumEnabled) return false
        if (isAtMirror(url, settings.freediumMirror)) return false
        return matchesAllowlist(url, settings.freediumDomains) || hasMediumSlug(url)
    }

    /** BASE + "/" + rawURL, scheme kept, inner URL left raw. */
    fun wrap(url: String): String = wrap(url, base)

    /** [base] + "/" + rawURL, scheme kept, inner URL left raw. */
    fun wrap(url: String, base: String): String = base.trimEnd('/') + "/" + url

    /** Wrap Medium articles; pass everything else through untouched. */
    fun process(url: String): String = if (looksLikeMediumArticle(url)) wrap(url) else url
}
