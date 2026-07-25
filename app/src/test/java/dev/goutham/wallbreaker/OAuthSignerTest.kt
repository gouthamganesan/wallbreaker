package dev.goutham.wallbreaker

import dev.goutham.wallbreaker.oauth.OAuthSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-implementation known-answer test. The golden `authHeader`/`body` strings
 * below were produced by running the Wallbreaker Chrome extension's real
 * `lib/oauth.js` `signRequest` on the same vectors (the ones in its
 * `tests/oauth.test.js`). Asserting byte-exact equality proves this Kotlin port
 * canonicalises the request identically to the JS/Python implementations — the
 * only way an OAuth 1.0a signer can be correct is to match the server's
 * canonicalisation exactly, so a self-consistent test would be worthless here.
 */
class OAuthSignerTest {

    // Vector 1: reserved-char + unicode heavy, with a token (exercises the
    // double-encode and oauth_token inclusion). Body order = insertion order.
    @Test fun `bookmarks add vector matches the JS golden header and body`() {
        val signed = OAuthSigner.sign(
            method = "POST",
            url = "https://www.instapaper.com/api/1/bookmarks/add",
            params = linkedMapOf(
                "url" to "https://x.io/p?q=1&r=2",
                "title" to "a & b=café 日",
                "resolve_final_url" to "1",
            ),
            consumerKey = "ck-abc123",
            consumerSecret = "cs-secret-XYZ",
            token = "tok-777",
            tokenSecret = "toksec-999",
            timestamp = 1721000000L,
            nonce = "0123456789abcdef0123456789abcdef",
        )

        assertEquals(
            "OAuth oauth_consumer_key=\"ck-abc123\", oauth_nonce=\"0123456789abcdef0123456789abcdef\", " +
                "oauth_signature=\"ArOgf8YO3P7N%2BtpI4sZW7n7cTmc%3D\", oauth_signature_method=\"HMAC-SHA1\", " +
                "oauth_timestamp=\"1721000000\", oauth_token=\"tok-777\", oauth_version=\"1.0\"",
            signed.authHeader,
        )
        assertEquals(
            "url=https%3A%2F%2Fx.io%2Fp%3Fq%3D1%26r%3D2&title=a%20%26%20b%3Dcaf%C3%A9%20%E6%97%A5&resolve_final_url=1",
            signed.body,
        )
    }

    // Vector 3: xAuth mode — empty token/secret. oauth_token must be absent from
    // the header, but the signing key keeps its trailing '&'.
    @Test fun `xAuth vector (empty token) matches the JS golden and omits oauth_token`() {
        val signed = OAuthSigner.sign(
            method = "POST",
            url = "https://www.instapaper.com/api/1/oauth/access_token",
            params = linkedMapOf(
                "x_auth_username" to "user@example.com",
                "x_auth_password" to "pw & special",
                "x_auth_mode" to "client_auth",
            ),
            consumerKey = "ck",
            consumerSecret = "cs",
            token = "",
            tokenSecret = "",
            timestamp = 1700000000L,
            nonce = "abcdef0123456789abcdef0123456789",
        )

        assertEquals(
            "OAuth oauth_consumer_key=\"ck\", oauth_nonce=\"abcdef0123456789abcdef0123456789\", " +
                "oauth_signature=\"UFwN8xQV%2BJRqetNxG4Ywypm%2BR%2FA%3D\", oauth_signature_method=\"HMAC-SHA1\", " +
                "oauth_timestamp=\"1700000000\", oauth_version=\"1.0\"",
            signed.authHeader,
        )
        assertFalse("no oauth_token when token empty", signed.authHeader.contains("oauth_token="))
        assertEquals(
            "x_auth_username=user%40example.com&x_auth_password=pw%20%26%20special&x_auth_mode=client_auth",
            signed.body,
        )
    }

    @Test fun `pctEncode fixes encodeURIComponent gaps, spares tilde, encodes slash space multibyte`() {
        assertEquals("%21%2A%27%28%29", OAuthSigner.pctEncode("!*'()"))
        assertEquals("~-._", OAuthSigner.pctEncode("~-._"))
        assertEquals("%2F", OAuthSigner.pctEncode("/"))
        assertEquals("%20", OAuthSigner.pctEncode(" "))
        assertEquals("%26%3D%2B", OAuthSigner.pctEncode("&=+"))
        assertEquals("caf%C3%A9", OAuthSigner.pctEncode("café"))
        assertEquals("%E6%97%A5", OAuthSigner.pctEncode("日"))
        assertEquals("%F0%9F%98%80", OAuthSigner.pctEncode("😀"))
    }

    @Test fun `identical injected nonce and timestamp are deterministic while defaults differ`() {
        fun once(nonce: String?) = OAuthSigner.sign(
            method = "POST",
            url = "https://www.instapaper.com/api/1/account/verify_credentials",
            consumerKey = "ck", consumerSecret = "cs", token = "tk", tokenSecret = "ts",
            timestamp = if (nonce == null) null else 1699999999L, nonce = nonce,
        ).authHeader

        assertEquals(once("fixednoncefixednoncefixednonce00"), once("fixednoncefixednoncefixednonce00"))
        assertTrue("random nonce → different headers", once(null) != once(null))
    }
}
