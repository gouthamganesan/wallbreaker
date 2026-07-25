package dev.goutham.wallbreaker

import java.net.URI

object UrlExtractor {
    private val URL_REGEX = Regex("""https?://\S+""")
    private const val TRAILING_JUNK = ".,;:!?)\"'>»]"

    /** First http(s) URL in the shared text, with trailing punctuation stripped. */
    fun firstUrl(text: String?): String? {
        val match = URL_REGEX.find(text ?: return null)?.value ?: return null
        return match.trimEnd { it in TRAILING_JUNK }.ifBlank { null }
    }

    /** Lowercased host of an http(s) URL, or null. */
    fun host(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            URI(url).host?.lowercase()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Normalise whatever the user pasted into the Freedium-routing field into a
     * domain to allowlist. Accepts a full URL ("https://blog.example.com/x?y")
     * or a bare host ("example.com"). Strips a leading "www." so the suffix
     * match also covers the apex. Returns null if it can't find a plausible host.
     */
    fun domainFromInput(input: String?): String? {
        val raw = input?.trim()?.ifBlank { null } ?: return null
        // Try as a URL first (with or without scheme).
        val fromUrl = host(raw) ?: host("https://$raw")
        val candidate = (fromUrl ?: raw.substringBefore('/').substringBefore('?'))
            .lowercase()
            .removePrefix("www.")
        // A plausible domain has a dot and no whitespace/at-sign.
        return if (candidate.contains('.') && candidate.none { it.isWhitespace() } && '@' !in candidate) {
            candidate
        } else {
            null
        }
    }
}
