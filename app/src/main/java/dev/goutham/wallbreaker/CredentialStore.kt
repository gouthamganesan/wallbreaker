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

/**
 * The only place a secret is touched. The password is encrypted with an
 * AES-GCM key that lives inside the Android Keystore (StrongBox where present).
 * The key never enters app memory; the ciphertext in SharedPreferences is
 * useless off-device. It does NOT protect against code running as this app
 * (root, or a debuggable build) — hence the release build is non-debuggable
 * and allowBackup="false".
 */
object CredentialStore {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "wallbreaker.instapaper"
    private const val PREFS = "wallbreaker_creds"
    private const val K_USER = "username"        // plaintext — an email is not the secret
    private const val K_PASS = "password_enc"    // Base64(iv || ciphertext+tag)

    fun save(context: Context, username: String, password: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())   // Keystore picks a fresh random 12-byte IV
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        prefs(context).edit()
            .putString(K_USER, username)
            .putString(K_PASS, blob)
            .apply()
    }

    /**
     * null == "never configured". An EMPTY password is a valid configured state:
     * presence of the K_PASS entry is the configured flag, never string length
     * (GCM of "" still produces a 16-byte tag, so the blob is never empty).
     * An undecryptable blob (key lost — e.g. data restored to a new device)
     * degrades to "not configured" instead of crashing.
     */
    fun load(context: Context): Credentials? {
        val p = prefs(context)
        val user = p.getString(K_USER, null) ?: return null
        val blob = p.getString(K_PASS, null) ?: return null
        return try {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, raw.copyOfRange(0, 12)),
            )
            Credentials(user, String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            null
        }
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
