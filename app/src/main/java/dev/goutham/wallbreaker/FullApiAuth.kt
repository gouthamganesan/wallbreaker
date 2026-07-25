package dev.goutham.wallbreaker

import android.content.Context

/**
 * Resolves usable [FullCredentials] for the advanced API. The password is
 * exchanged for an OAuth token exactly once (via xAuth) and cached encrypted;
 * every later call reuses the token. On an auth failure the caller
 * [invalidate]s the cached token so the next attempt re-exchanges.
 */
object FullApiAuth {

    /**
     * @return usable creds, or null if the Full API isn't set up (no consumer
     * app, or no account to exchange). May perform a network xAuth call and thus
     * throw [InstapaperNetworkException] / [InstapaperApiException].
     */
    fun resolve(context: Context): FullCredentials? {
        val app = CredentialStore.loadConsumerApp(context) ?: return null

        CredentialStore.loadOAuthToken(context)?.let { (token, secret) ->
            return FullCredentials(app.consumerKey, app.consumerSecret, token, secret)
        }

        // No cached token — exchange the stored password for one.
        val account = CredentialStore.load(context) ?: return null
        val exchanged = InstapaperFullApi.xauth(
            consumerKey = app.consumerKey,
            consumerSecret = app.consumerSecret,
            username = account.username,
            password = account.password,
        )
        CredentialStore.saveOAuthToken(context, exchanged.token, exchanged.tokenSecret)
        return FullCredentials(app.consumerKey, app.consumerSecret, exchanged.token, exchanged.tokenSecret)
    }

    fun invalidate(context: Context) = CredentialStore.clearOAuthToken(context)
}
