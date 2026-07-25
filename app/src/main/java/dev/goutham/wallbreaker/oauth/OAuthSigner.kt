package dev.goutham.wallbreaker.oauth

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure OAuth 1.0a HMAC-SHA1 request signer — a direct Kotlin port of the
 * Wallbreaker Chrome extension's `lib/oauth.js` (which itself mirrors the CLI's
 * `oauth.py`). Deliberately Android-free: it uses only `javax.crypto.Mac` and
 * `java.util.Base64` (both present on the JVM and on Android API 26+), so the
 * whole thing is verified by a plain JVM unit test against golden headers
 * produced by the JS implementation.
 *
 * The classic OAuth 1.0a footguns, preserved exactly:
 *  - [pctEncode] is RFC 3986 *unreserved* (A-Z a-z 0-9 - _ . ~ pass through;
 *    every other UTF-8 byte becomes %XX, uppercase). It is applied EVERYWHERE:
 *    base-string parts, param keys, param values, and header values.
 *  - The signature base string is `METHOD & pct(url) & pct(normalized)` — the
 *    already single-encoded `normalized` string is percent-encoded a SECOND
 *    time here (a space becomes %20 then %2520). Correct, not a bug.
 *  - `normalized` = every oauth_* param (minus oauth_signature) plus every body
 *    param, each key and value pctEncoded once, sorted by encoded key then
 *    encoded value, joined `k=v` with `&`.
 *  - The signing key is `pct(consumerSecret) & pct(tokenSecret)`; the trailing
 *    `&` stays even when tokenSecret is empty (the xAuth call).
 *
 * [timestamp] and [nonce] are injectable so the known-answer test is
 * deterministic; in production they default to now + a random nonce.
 */
object OAuthSigner {

    /** `authHeader` carries only oauth_*; `body` carries only the method params. */
    data class Signed(val authHeader: String, val body: String)

    private const val HEX = "0123456789ABCDEF"

    /** RFC 3986 unreserved percent-encode over the string's UTF-8 bytes. */
    fun pctEncode(input: String): String {
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        val sb = StringBuilder(bytes.size)
        for (byte in bytes) {
            val c = byte.toInt() and 0xFF
            val unreserved = c in 'A'.code..'Z'.code ||
                c in 'a'.code..'z'.code ||
                c in '0'.code..'9'.code ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
            if (unreserved) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append(HEX[c ushr 4]).append(HEX[c and 0x0F])
            }
        }
        return sb.toString()
    }

    private fun hmacSha1Base64(key: String, base: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
        val sig = mac.doFinal(base.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig)   // basic encoder: no line wrapping
    }

    fun sign(
        method: String,
        url: String,
        params: Map<String, String> = emptyMap(),
        consumerKey: String,
        consumerSecret: String,
        token: String = "",
        tokenSecret: String = "",
        timestamp: Long? = null,
        nonce: String? = null,
    ): Signed {
        val ts = (timestamp ?: (System.currentTimeMillis() / 1000)).toString()
        val nn = nonce ?: UUID.randomUUID().toString().replace("-", "")

        // oauth_* params (oauth_signature is added after signing).
        val oauth = LinkedHashMap<String, String>()
        oauth["oauth_consumer_key"] = consumerKey
        oauth["oauth_nonce"] = nn
        oauth["oauth_signature_method"] = "HMAC-SHA1"
        oauth["oauth_timestamp"] = ts
        oauth["oauth_version"] = "1.0"
        if (token.isNotEmpty()) oauth["oauth_token"] = token

        // Base-string parameters: all oauth_* + body params, each encoded once,
        // sorted by encoded key then encoded value.
        val encoded = ArrayList<Pair<String, String>>(oauth.size + params.size)
        for ((k, v) in oauth) encoded.add(pctEncode(k) to pctEncode(v))
        for ((k, v) in params) encoded.add(pctEncode(k) to pctEncode(v))
        encoded.sortWith(compareBy({ it.first }, { it.second }))
        val normalized = encoded.joinToString("&") { "${it.first}=${it.second}" }

        val base = "${method.uppercase()}&${pctEncode(url)}&${pctEncode(normalized)}"
        val signingKey = "${pctEncode(consumerSecret)}&${pctEncode(tokenSecret)}"
        oauth["oauth_signature"] = hmacSha1Base64(signingKey, base)

        // Header: only oauth_* params, sorted by key, each pct(k)="pct(v)".
        val authHeader = "OAuth " + oauth.keys.sorted().joinToString(", ") { k ->
            "${pctEncode(k)}=\"${pctEncode(oauth.getValue(k))}\""
        }

        // Body: only the method params, in insertion order.
        val body = params.entries.joinToString("&") { (k, v) ->
            "${pctEncode(k)}=${pctEncode(v)}"
        }

        return Signed(authHeader, body)
    }
}
