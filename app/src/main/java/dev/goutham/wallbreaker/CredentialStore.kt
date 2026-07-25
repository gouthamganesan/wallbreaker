package dev.goutham.wallbreaker

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Credentials(val username: String, val password: String)

/** The Instapaper API application (Full API): consumer key + secret. */
data class ConsumerApp(val consumerKey: String, val consumerSecret: String)

/**
 * The only place a secret is touched. Every secret is encrypted with an AES-GCM
 * key that lives inside the Android Keystore (StrongBox where present); the key
 * never enters app memory, and the ciphertext in SharedPreferences is useless
 * off-device. It does NOT protect against code running as this app (root, or a
 * debuggable build) — hence the release build is non-debuggable and
 * allowBackup="false".
 *
 * What's stored:
 *  - username           — plaintext (an email is not the secret)
 *  - password           — encrypted (Simple API + the one-time xAuth exchange)
 *  - consumer key/secret— encrypted (the Full API application identity)
 *  - oauth token/secret — encrypted (cached result of the xAuth exchange, so the
 *                         password isn't re-exchanged on every save)
 */
object CredentialStore {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "wallbreaker.instapaper"
    private const val PREFS = "wallbreaker_creds"

    private const val K_USER = "username"          // plaintext
    private const val K_PASS = "password_enc"      // Base64(iv || ciphertext+tag)
    private const val K_CKEY = "consumer_key_enc"
    private const val K_CSECRET = "consumer_secret_enc"
    private const val K_OTOKEN = "oauth_token_enc"
    private const val K_OSECRET = "oauth_secret_enc"

    // --- Simple API credentials (username + password) ---------------------

    fun save(context: Context, username: String, password: String) {
        prefs(context).edit()
            .putString(K_USER, username)
            .putString(K_PASS, encrypt(password))
            .apply()
    }

    /**
     * null == "never configured". An EMPTY password is a valid configured state:
     * presence of the K_PASS entry is the configured flag, never string length.
     * An undecryptable blob (key lost — e.g. data restored to a new device)
     * degrades to "not configured" instead of crashing.
     */
    fun load(context: Context): Credentials? {
        val p = prefs(context)
        val user = p.getString(K_USER, null) ?: return null
        val pass = p.getString(K_PASS, null)?.let { decrypt(it) } ?: return null
        return Credentials(user, pass)
    }

    // --- Full API application (consumer key + secret) ---------------------

    /** Persist the API app identity. Any cached OAuth token is invalidated,
     *  since a token is only valid for the app that minted it. */
    fun saveConsumerApp(context: Context, consumerKey: String, consumerSecret: String) {
        prefs(context).edit()
            .putString(K_CKEY, encrypt(consumerKey))
            .putString(K_CSECRET, encrypt(consumerSecret))
            .remove(K_OTOKEN)
            .remove(K_OSECRET)
            .apply()
    }

    fun loadConsumerApp(context: Context): ConsumerApp? {
        val p = prefs(context)
        val key = p.getString(K_CKEY, null)?.let { decrypt(it) } ?: return null
        val secret = p.getString(K_CSECRET, null)?.let { decrypt(it) } ?: return null
        if (key.isBlank()) return null
        return ConsumerApp(key, secret)
    }

    fun hasConsumerApp(context: Context): Boolean = loadConsumerApp(context) != null

    // --- Cached OAuth token (result of the xAuth exchange) ----------------

    fun saveOAuthToken(context: Context, token: String, tokenSecret: String) {
        prefs(context).edit()
            .putString(K_OTOKEN, encrypt(token))
            .putString(K_OSECRET, encrypt(tokenSecret))
            .apply()
    }

    fun loadOAuthToken(context: Context): Pair<String, String>? {
        val p = prefs(context)
        val token = p.getString(K_OTOKEN, null)?.let { decrypt(it) } ?: return null
        val secret = p.getString(K_OSECRET, null)?.let { decrypt(it) } ?: return null
        if (token.isBlank()) return null
        return token to secret
    }

    /** Drop the cached token so the next Full API call re-runs xAuth. */
    fun clearOAuthToken(context: Context) {
        prefs(context).edit().remove(K_OTOKEN).remove(K_OSECRET).apply()
    }

    // --- crypto -----------------------------------------------------------

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())   // Keystore picks a fresh random 12-byte IV
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    /** Returns null on an undecryptable blob (lost key) rather than crashing. */
    private fun decrypt(blob: String): String? = try {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        null
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return generate(strongBox = true)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)  // deliberate: no biometric inside the save flow
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .build()
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        return try {
            kg.init(spec)
            kg.generateKey()
        } catch (e: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }
}
