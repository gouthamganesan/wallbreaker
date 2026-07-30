package dev.goutham.wallbreaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreediumWrapTest {

    private fun settings(
        vararg domains: String,
        enabled: Boolean = true,
        mirror: String = AppSettings.DEFAULT_MIRROR,
    ) = AppSettings(freediumEnabled = enabled, freediumMirror = mirror, freediumDomains = domains.toList())

    @Test fun `active base is the live mirror, not the dead one`() {
        assertEquals("https://freedium-mirror.cfd", Freedium.base)
        assertTrue("freedium.cfd kept as documented fallback", Freedium.BASES.contains("https://freedium.cfd"))
    }

    @Test fun `wrap keeps scheme and does not percent-encode the inner url`() {
        val url = "https://medium.com/@a/post-1a2b3c4d5e6f"
        assertEquals("https://freedium-mirror.cfd/https://medium.com/@a/post-1a2b3c4d5e6f", Freedium.wrap(url))
    }

    // --- the allowlist is the whole gate ----------------------------------

    @Test fun `allowlisted domain and its subdomains route`() {
        val s = settings("medium.com")
        assertTrue(Freedium.shouldRoute("https://medium.com/@a/post-1a2b3c4d5e6f", s))
        assertTrue(Freedium.shouldRoute("https://towards.medium.com/x-0011223344ff", s))
        // no post-id slug needed — membership of the list is the only question
        assertTrue(Freedium.shouldRoute("https://medium.com/tag/design", s))
    }

    @Test fun `a medium-shaped url NOT on the list is never routed`() {
        // The old build routed anything carrying Medium's 12-hex post id, which
        // sent links through a third-party mirror without them appearing in the
        // user's list. The allowlist is now the only authority.
        val s = settings("medium.com")
        assertFalse(Freedium.shouldRoute("https://ehandbook.com/some-post-1a2b3c4d5e6f", s))
        assertFalse(Freedium.shouldRoute("https://example.com/post-1a2b3c4d5e6f", s))
    }

    @Test fun `a look-alike host must not slip through the suffix check`() {
        val s = settings("medium.com")
        assertFalse(Freedium.shouldRoute("https://notmedium.com/x-1a2b3c4d5e6f", s))
        assertFalse(Freedium.shouldRoute("https://medium.com.evil.com/x-1a2b3c4d5e6f", s))
    }

    @Test fun `custom-domain publications route once they are on the list`() {
        val s = settings("levelup.gitconnected.com", "uxdesign.cc")
        assertTrue(Freedium.shouldRoute("https://levelup.gitconnected.com/a-post-1a2b3c4d5e6f", s))
        assertTrue(Freedium.shouldRoute("https://uxdesign.cc/a-post", s))
        assertFalse(Freedium.shouldRoute("https://medium.com/@a/post-1a2b3c4d5e6f", s))
    }

    @Test fun `routing off beats the allowlist`() {
        assertFalse(Freedium.shouldRoute("https://medium.com/@a/post-1a2b3c4d5e6f", settings("medium.com", enabled = false)))
    }

    @Test fun `an already-wrapped url is not double-wrapped`() {
        val s = settings("medium.com")
        assertFalse(Freedium.shouldRoute("https://freedium-mirror.cfd/https://medium.com/@a/post-1a2b3c4d5e6f", s))
    }

    @Test fun `garbage input does not crash the gate`() {
        val s = settings("medium.com")
        assertFalse(Freedium.shouldRoute("not a url", s))
        assertFalse(Freedium.shouldRoute("", s))
    }

    @Test fun `fresh-install defaults list only publications Medium still hosts`() {
        // A seed for the visible list, not a second hidden allowlist — routing
        // only ever consults whatever ends up stored.
        assertTrue(AppSettings.DEFAULT_DOMAINS.contains("medium.com"))
        assertTrue(AppSettings.DEFAULT_DOMAINS.contains("levelup.gitconnected.com"))
        assertTrue(AppSettings.DEFAULT_DOMAINS.contains("uxdesign.cc"))

        // Freedium can only unlock Medium-served pages, so a publication that
        // has moved off Medium must not be here — routing it through the mirror
        // would achieve nothing. Verified against the live sites 2026-07-30.
        for (moved in listOf("plainenglish.io", "medium.freecodecamp.org", "towardsdatascience.com")) {
            assertFalse("$moved no longer runs on Medium", AppSettings.DEFAULT_DOMAINS.contains(moved))
        }
    }
}
