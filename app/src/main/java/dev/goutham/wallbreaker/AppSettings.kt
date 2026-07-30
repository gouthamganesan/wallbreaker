package dev.goutham.wallbreaker

import android.content.Context

/**
 * Non-secret configuration — the Android counterpart of the CLI's
 * `~/.config/instapaper/config.json`. Plain SharedPreferences (nothing here is a
 * secret): whether Freedium routing is on, which mirror to use, and the
 * user-editable domain allowlist that decides which links get routed.
 */
data class AppSettings(
    val freediumEnabled: Boolean = true,
    val freediumMirror: String = DEFAULT_MIRROR,
    val freediumDomains: List<String> = DEFAULT_DOMAINS,
) {
    companion object {
        const val DEFAULT_MIRROR = "https://freedium-mirror.cfd"

        /**
         * What a *fresh install* starts with, shown in Settings from the first
         * launch. Not a second allowlist: the moment anything is stored, the
         * stored set is the only thing routing consults, and an entry removed
         * here never comes back on its own.
         *
         * Medium proper plus the custom-domain publications it still hosts.
         * Suffix-matched, so "medium.com" also covers *.medium.com.
         *
         * Freedium publishes no list of supported sites — it works by detecting
         * Medium's canonical URL on a page — so this was built by taking the
         * union of the community bypass extensions' domain lists (68 domains)
         * and **checking every one against the live site on 2026-07-30**. A
         * publication Medium still serves ships Medium's client bundle
         * (cdn-client/miro/glyph.medium.com) and appends Medium's own `?gi=`
         * param on load; 40 of the 68 did neither and were dropped. Those lists
         * only ever accrete, and stale entries are not harmless — one of them,
         * blog.coffeeapplied.com, has been re-registered and now redirects to a
         * spam host.
         *
         * Note plainenglish.io: the bare domain left Medium, but its topic
         * subdomains (javascript./python./aws.) are still Medium-hosted, so the
         * subdomains are listed and the parent deliberately is not.
         */
        val DEFAULT_DOMAINS = listOf(
            "medium.com",
            "aws.plainenglish.io",
            "baos.pub",
            "betterhumans.pub",
            "bettermarketing.pub",
            "betterprogramming.pub",
            "blog.devgenius.io",
            "blog.kubernauts.io",
            "blog.prototypr.io",
            "code.likeagirl.io",
            "codeburst.io",
            "entrepreneurshandbook.co",
            "generativeai.pub",
            "infosecwriteups.com",
            "itnext.io",
            "javascript.plainenglish.io",
            "levelup.gitconnected.com",
            "medium.datadriveninvestor.com",
            "medium.muz.li",
            "netflixtechblog.com",
            "proandroiddev.com",
            "pub.towardsai.net",
            "python.plainenglish.io",
            "tech.olx.com",
            "thebelladonnacomedy.com",
            "themakingofamillionaire.com",
            "uxdesign.cc",
            "uxplanet.org",
            "writingcooperative.com",
        )
    }
}

object AppSettingsStore {
    private const val PREFS = "wallbreaker_settings"
    private const val K_ENABLED = "freedium_enabled"
    private const val K_MIRROR = "freedium_mirror"
    private const val K_DOMAINS = "freedium_domains"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): AppSettings {
        val p = prefs(context)
        val domains = p.getStringSet(K_DOMAINS, null)
            ?.filter { it.isNotBlank() }
            ?.sorted()
            ?: AppSettings.DEFAULT_DOMAINS
        return AppSettings(
            freediumEnabled = p.getBoolean(K_ENABLED, true),
            freediumMirror = p.getString(K_MIRROR, AppSettings.DEFAULT_MIRROR)
                .orEmpty().ifBlank { AppSettings.DEFAULT_MIRROR },
            freediumDomains = domains,
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(K_ENABLED, enabled).apply()
    }

    fun setMirror(context: Context, mirror: String) {
        val clean = mirror.trim().trimEnd('/').ifBlank { AppSettings.DEFAULT_MIRROR }
        prefs(context).edit().putString(K_MIRROR, clean).apply()
    }

    /** Add a domain (already extracted/normalised). No-op if blank or present. */
    fun addDomain(context: Context, domain: String) {
        val d = domain.trim().lowercase()
        if (d.isBlank()) return
        val current = load(context).freediumDomains.toMutableSet()
        if (current.add(d)) persistDomains(context, current)
    }

    fun removeDomain(context: Context, domain: String) {
        val current = load(context).freediumDomains.toMutableSet()
        if (current.remove(domain.trim().lowercase())) persistDomains(context, current)
    }

    private fun persistDomains(context: Context, domains: Set<String>) {
        // Copy into a fresh set — SharedPreferences must not be handed a mutable
        // set it keeps a reference to.
        prefs(context).edit().putStringSet(K_DOMAINS, HashSet(domains)).apply()
    }
}
