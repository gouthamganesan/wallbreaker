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

    /** True iff [url] is a *.medium.com article Freedium can actually unlock. */
    fun looksLikeMediumArticle(url: String): Boolean {
        val host = try {
            URI(url).host?.lowercase()
        } catch (e: Exception) {
            null
        } ?: return false
        val isMedium = host == "medium.com" || host.endsWith(".medium.com")
        return isMedium && MEDIUM_SLUG.containsMatchIn(url)
    }

    /** BASE + "/" + rawURL, scheme kept, inner URL left raw. */
    fun wrap(url: String): String = base.trimEnd('/') + "/" + url

    /** Wrap Medium articles; pass everything else through untouched. */
    fun process(url: String): String = if (looksLikeMediumArticle(url)) wrap(url) else url
}
