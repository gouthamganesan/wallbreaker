package dev.goutham.wallbreaker

import android.content.Context

/** SharedPreferences persistence for [AppSettings]. */
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
