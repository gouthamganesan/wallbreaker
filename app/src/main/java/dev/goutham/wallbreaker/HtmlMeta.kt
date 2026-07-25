package dev.goutham.wallbreaker

/**
 * Minimal, dependency-free HTML head parsing for the raw-HTML-file path: pull a
 * canonical/original URL and a title out of a shared HTML document so the upload
 * can be saved under the real article URL. Regex over the markup (no jsoup) —
 * these tags live in <head> and are simple enough that a tolerant scan beats a
 * 350 KB parser dependency.
 */
object HtmlMeta {

    private val LINK_TAG = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val META_TAG = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val TITLE_TAG = Regex(
        """<title[^>]*>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** Original/canonical URL: <link rel=canonical> first, then <meta og:url>. */
    fun canonicalUrl(html: String): String? {
        val head = html.take(200_000)   // canonical/og live in <head>; bound the scan
        LINK_TAG.findAll(head).forEach { m ->
            val tag = m.value
            if (attr(tag, "rel")?.contains("canonical", ignoreCase = true) == true) {
                attr(tag, "href")?.let { href ->
                    if (href.startsWith("http", ignoreCase = true)) return unescape(href.trim())
                }
            }
        }
        metaContent(head, "og:url")?.let { if (it.startsWith("http", true)) return it }
        return null
    }

    /** Best-effort title: <meta og:title> first, then <title>. */
    fun title(html: String): String? {
        val head = html.take(200_000)
        metaContent(head, "og:title")?.let { return it }
        TITLE_TAG.find(head)?.groupValues?.get(1)?.let { raw ->
            val t = unescape(raw.trim())
            if (t.isNotBlank()) return t
        }
        return null
    }

    /** Content of the <meta> tag whose property/name equals [key]. */
    private fun metaContent(html: String, key: String): String? {
        META_TAG.findAll(html).forEach { m ->
            val tag = m.value
            val id = attr(tag, "property") ?: attr(tag, "name")
            if (id?.equals(key, ignoreCase = true) == true) {
                attr(tag, "content")?.let { return unescape(it.trim()).ifBlank { null } }
            }
        }
        return null
    }

    /** Value of attribute [name] in a tag string (double/single/unquoted). */
    private fun attr(tag: String, name: String): String? {
        val re = Regex(
            """\b""" + Regex.escape(name) + """\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""",
            RegexOption.IGNORE_CASE,
        )
        val m = re.find(tag) ?: return null
        return m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
    }

    /** Unescape the handful of HTML entities that show up in URLs/titles. */
    private fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x2F;", "/")
        .replace("&#47;", "/")
}
