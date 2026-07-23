package dev.goutham.wallbreaker

object UrlExtractor {
    private val URL_REGEX = Regex("""https?://\S+""")
    private const val TRAILING_JUNK = ".,;:!?)\"'>»]"

    /** First http(s) URL in the shared text, with trailing punctuation stripped. */
    fun firstUrl(text: String?): String? {
        val match = URL_REGEX.find(text ?: return null)?.value ?: return null
        return match.trimEnd { it in TRAILING_JUNK }.ifBlank { null }
    }
}
