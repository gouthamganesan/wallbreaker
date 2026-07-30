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
         * Every entry was checked against the live site (2026-07-30) rather
         * than copied from the Chrome extension's list: a Medium-hosted domain
         * still serves Medium's client bundle and appends Medium's `?gi=` param
         * on load. Publications that have since moved off Medium are dropped,
         * because Freedium cannot unlock what Medium no longer serves —
         * listing them would route a link through a third-party mirror for
         * nothing. Removed on that evidence: plainenglish.io and
         * medium.freecodecamp.org (both now self-hosted), and
         * towardsdatascience.com (left Medium in Feb 2025).
         */
        val DEFAULT_DOMAINS = listOf(
            "medium.com",
            "betterprogramming.pub",
            "levelup.gitconnected.com",
            "blog.devgenius.io",
            "infosecwriteups.com",
            "uxdesign.cc",
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
