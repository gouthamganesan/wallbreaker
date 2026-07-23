package dev.goutham.wallbreaker

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Instapaper Simple API over HttpURLConnection. Two endpoints, form-encoded
 * bodies. The request body carries the password on every call, so it is never
 * logged.
 */
object InstapaperClient {

    private const val ADD = "https://www.instapaper.com/api/add"
    private const val AUTHENTICATE = "https://www.instapaper.com/api/authenticate"

    sealed interface AddResult {
        data class Saved(val title: String?) : AddResult
        data object BadCredentials : AddResult          // 403
        data object BadUrl : AddResult                  // 400
        data class ServerError(val code: Int) : AddResult
        data class NetworkError(val message: String?) : AddResult
    }

    fun add(creds: Credentials, url: String): AddResult {
        val conn = open(ADD)
        return try {
            writeForm(
                conn,
                mapOf(
                    "username" to creds.username,
                    "password" to creds.password,
                    "url" to url,
                ),
            )
            when (val code = conn.responseCode) {
                201 -> AddResult.Saved(conn.getHeaderField("X-Instapaper-Title"))
                403 -> AddResult.BadCredentials
                400 -> AddResult.BadUrl
                else -> AddResult.ServerError(code)
            }
        } catch (e: IOException) {
            AddResult.NetworkError(e.message)
        } finally {
            conn.disconnect()
        }
    }

    /** true = valid; false = rejected (403). Throws IOException on network failure. */
    fun authenticate(creds: Credentials): Boolean {
        val conn = open(AUTHENTICATE)
        return try {
            writeForm(conn, mapOf("username" to creds.username, "password" to creds.password))
            conn.responseCode == 200
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }

    // URLEncoder handles the form-value encoding of the freedium-wrapped URL —
    // the one place the spec requires encoding. Never log this body: it has creds.
    private fun writeForm(conn: HttpURLConnection, fields: Map<String, String>) {
        val body = fields.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }
}
