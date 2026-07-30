package dev.goutham.wallbreaker

import dev.goutham.wallbreaker.oauth.OAuthSigner
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The four durable OAuth fields the Full API signs with. */
data class FullCredentials(
    val consumerKey: String,
    val consumerSecret: String,
    val oauthToken: String,
    val oauthTokenSecret: String,
)

/** A failure the server reported (or a client-side guard). */
class InstapaperApiException(
    val httpStatus: Int?,
    val errorCode: Int?,
    override val message: String,
    val hint: String?,
    val retryable: Boolean,
) : Exception(message)

/** The request never reached the server (DNS, connection, timeout). Always retryable. */
class InstapaperNetworkException(message: String?) : Exception(message)

/**
 * Instapaper **Full API** (OAuth 1.0a) over HttpURLConnection — the Kotlin port
 * of the Chrome extension's `lib/instapaper.js`. Signs every call with
 * [OAuthSigner], POSTs a form body, and parses the JSON (or, for xAuth, the
 * form-encoded) response. This is the "advanced API": it is the only path that
 * can upload article HTML via `content`, which is how a bookmark keeps its clean
 * original URL instead of a Freedium-mirror one.
 *
 * The password is touched in exactly one place — [xauth] — and never persisted
 * or logged. Article HTML (`content`) is never logged either.
 */
object InstapaperFullApi {

    private const val API_BASE = "https://www.instapaper.com/api/1"
    private const val USER_AGENT = "wallbreaker-android/1.2"

    // Instapaper folds `content` into the OAuth signature and pctEncodes it
    // twice; guard the size before signing (mirrors lib/instapaper.js).
    const val MAX_CONTENT_CHARS = 2_000_000

    /** Remediation hints for known Instapaper error codes (surfaced in the UI). */
    private val ERROR_HINTS = mapOf(
        1040 to "Rate limited — will retry later",
        1041 to "Instapaper Premium required",
        1220 to "Domain needs full-page HTML — full-text unlock handles this",
        1221 to "This domain has opted out of Instapaper saving",
        1240 to "Invalid URL",
        1245 to "Private/paywalled source — full-text unlock handles this",
    )

    data class OAuthToken(val token: String, val tokenSecret: String)
    data class VerifiedUser(val userId: Long, val username: String)
    data class AddedBookmark(val bookmarkId: Long, val title: String?, val url: String?)

    /**
     * Exchange username + password for an OAuth access token via xAuth. The
     * password lives only inside this call. Returns the token + secret to cache.
     */
    fun xauth(
        consumerKey: String,
        consumerSecret: String,
        username: String,
        password: String,
    ): OAuthToken {
        val text = apiCall(
            path = "/oauth/access_token",
            params = linkedMapOf(
                "x_auth_username" to username,
                "x_auth_password" to password,
                "x_auth_mode" to "client_auth",
            ),
            creds = FullCredentials(consumerKey, consumerSecret, "", ""),
            raw = true,
        )
        // Response is a form-encoded line, not JSON.
        val fields = HashMap<String, String>()
        for (pair in text.split("&")) {
            val i = pair.indexOf('=')
            if (i > 0) fields[pair.substring(0, i)] = urlDecode(pair.substring(i + 1))
        }
        val token = fields["oauth_token"].orEmpty()
        val secret = fields["oauth_token_secret"].orEmpty()
        if (token.isEmpty() || secret.isEmpty()) {
            throw InstapaperApiException(200, null, "Login failed: no token in response", null, false)
        }
        return OAuthToken(token, secret)
    }

    /** Verify stored creds → the account user. Throws on rejection. */
    fun verifyCredentials(creds: FullCredentials): VerifiedUser {
        val text = apiCall("/account/verify_credentials", linkedMapOf(), creds, raw = false)
        val user = firstOfType(text, "user")
            ?: throw InstapaperApiException(200, null, "Verify failed: no user in response", null, false)
        return VerifiedUser(user.optLong("user_id"), user.optString("username"))
    }

    /**
     * Add a bookmark. When [content] is supplied Instapaper stores it verbatim
     * under [url] (no crawl), which is how the original URL is preserved while
     * the unlocked article text is saved.
     */
    fun addBookmark(
        creds: FullCredentials,
        url: String,
        title: String? = null,
        content: String? = null,
    ): AddedBookmark {
        if (content != null && content.length > MAX_CONTENT_CHARS) {
            throw InstapaperApiException(
                null, null, "Article too large to upload",
                "Article too large — saved as a link instead", false,
            )
        }
        val params = linkedMapOf("url" to url)
        if (!title.isNullOrBlank()) params["title"] = title
        if (content != null) params["content"] = content

        val text = apiCall("/bookmarks/add", params, creds, raw = false)
        val bm = firstOfType(text, "bookmark")
            ?: throw InstapaperApiException(200, null, "Add failed: no bookmark in response", null, false)
        return AddedBookmark(
            bookmarkId = bm.optLong("bookmark_id"),
            title = bm.optString("title").ifBlank { null },
            url = bm.optString("url").ifBlank { null },
        )
    }

    // --- transport --------------------------------------------------------

    private fun apiCall(
        path: String,
        params: LinkedHashMap<String, String>,
        creds: FullCredentials,
        raw: Boolean,
    ): String {
        val url = API_BASE + path
        val signed = OAuthSigner.sign(
            method = "POST",
            url = url,
            params = params,
            consumerKey = creds.consumerKey,
            consumerSecret = creds.consumerSecret,
            token = creds.oauthToken,
            tokenSecret = creds.oauthTokenSecret,
        )

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 40_000
            setRequestProperty("Authorization", signed.authHeader)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        val contentLen = params["content"]?.length
        WbLog.i("fullapi POST $path (bodyChars=${signed.body.length}${contentLen?.let { ", content=$it" } ?: ""})")

        val status: Int
        val text: String
        try {
            conn.outputStream.use { it.write(signed.body.toByteArray(Charsets.UTF_8)) }
            status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (e: IOException) {
            WbLog.w("fullapi $path network failure: ${e.javaClass.simpleName}: ${e.message}")
            throw InstapaperNetworkException(e.message)
        } finally {
            conn.disconnect()
        }

        // xAuth's response body IS the token — never log it.
        WbLog.i("fullapi $path -> HTTP $status${if (raw) "" else " " + WbLog.snippet(text)}")

        if (status !in 200..299) {
            val err = findErrorObject(text)
            if (err != null) throw apiError(status, err)
            // HTTP error with a non-error or non-JSON body: retryable server error.
            throw InstapaperApiException(status, null, "Instapaper server error; will retry", null, true)
        }

        if (raw) return text

        val err = findErrorObject(text)   // Full API errors can ride inside a 200 body
        if (err != null) throw apiError(status, err)
        return text
    }

    private fun apiError(status: Int, err: JSONObject): InstapaperApiException {
        val code = if (err.has("error_code")) err.optInt("error_code") else null
        val retryable = (status >= 500) || code == 1040
        return InstapaperApiException(
            httpStatus = status,
            errorCode = code,
            message = err.optString("message").ifBlank { "Instapaper error" },
            hint = code?.let { ERROR_HINTS[it] },
            retryable = retryable,
        )
    }

    /** Parse the body (JSON array or object) and return the first object of [type]. */
    private fun firstOfType(text: String, type: String): JSONObject? {
        for (obj in objects(text)) {
            if (obj.optString("type") == type) return obj
        }
        return null
    }

    private fun findErrorObject(text: String): JSONObject? {
        for (obj in objects(text)) {
            if (obj.optString("type") == "error") return obj
        }
        return null
    }

    /** Normalise a Full API body into a flat list of JSON objects. */
    private fun objects(text: String): List<JSONObject> {
        val trimmed = text.trim()
        return try {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                }
                trimmed.startsWith("{") -> listOf(JSONObject(trimmed))
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun urlDecode(s: String): String =
        try { java.net.URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }
}
