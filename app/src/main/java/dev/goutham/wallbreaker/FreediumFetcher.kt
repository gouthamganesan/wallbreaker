package dev.goutham.wallbreaker

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the unlocked article HTML from the Freedium mirror. This is the
 * server-side equivalent of what the Chrome extension does in a background tab:
 * it GETs the Freedium render of a Medium URL so the HTML can be uploaded as
 * `content` under the ORIGINAL url (keeping the clean canonical link).
 *
 * Android has no browser DOM, so — unlike the extension, which runs Defuddle on
 * the live page — we hand Instapaper the fetched page HTML and let its own
 * parser extract the article. Returns null on any failure so the caller can fall
 * back to saving the Freedium-wrapped link via the Simple API.
 */
object FreediumFetcher {

    // Present as a real browser: Freedium serves different markup to bare bots.
    private const val UA =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0 Mobile Safari/537.36"

    // Cap the read so a runaway page can't blow memory; well under the Full API's
    // 2M-char content limit after this many bytes of UTF-8.
    private const val MAX_BYTES = 4_000_000

    /** GET [wrappedUrl] and return its HTML, or null on failure. */
    fun fetch(wrappedUrl: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(wrappedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 40_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                WbLog.w("freedium GET -> HTTP $code (giving up on content upload)")
                return null
            }
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                val out = StringBuilder()
                val reader = input.reader(Charsets.UTF_8)
                val chars = CharArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = reader.read(chars)
                    if (n < 0) break
                    out.append(chars, 0, n)
                    total += n
                    if (total >= MAX_BYTES) break
                }
                WbLog.i("freedium GET -> HTTP $code, $total chars")
                out.toString().ifBlank { null }
            }
        } catch (e: IOException) {
            WbLog.w("freedium GET failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        } catch (e: Exception) {
            WbLog.w("freedium GET failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
