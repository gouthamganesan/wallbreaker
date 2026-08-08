package dev.goutham.wallbreaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [Freedium.unlockCandidate] — what the share card offers to allowlist.
 *
 * Its contract is a mirror image of [Freedium.shouldRoute]: for any given URL
 * exactly one of the two answers, never both, never neither. The last test in
 * this file is the one that matters — it asserts that relationship directly, so
 * the offer can never drift into recommending something routing would ignore.
 */
class UnlockCandidateTest {

    private fun settings(
        vararg domains: String,
        enabled: Boolean = true,
        mirror: String = AppSettings.DEFAULT_MIRROR,
    ) = AppSettings(freediumEnabled = enabled, freediumMirror = mirror, freediumDomains = domains.toList())

    @Test fun `offers the domain of a link that went out unrouted`() {
        val s = settings("medium.com")
        assertEquals("uxdesign.cc", Freedium.unlockCandidate("https://uxdesign.cc/some-post-1a2b3c4d5e6f", s))
    }

    @Test fun `strips www so the stored domain matches the apex`() {
        val s = settings("medium.com")
        assertEquals("example.com", Freedium.unlockCandidate("https://www.example.com/a/b?c=d", s))
    }

    @Test fun `keeps a real subdomain — publications are listed individually`() {
        // aws.plainenglish.io is Medium-hosted while plainenglish.io is not, so
        // collapsing to the apex would allowlist the wrong thing.
        val s = settings("medium.com")
        assertEquals("aws.plainenglish.io", Freedium.unlockCandidate("https://aws.plainenglish.io/post-x", s))
    }

    @Test fun `nothing to offer when the domain is already on the list`() {
        val s = settings("medium.com", "uxdesign.cc")
        assertNull(Freedium.unlockCandidate("https://uxdesign.cc/post-x", s))
    }

    @Test fun `nothing to offer for a subdomain already covered by a listed apex`() {
        val s = settings("medium.com")
        assertNull(Freedium.unlockCandidate("https://towards.medium.com/post-x", s))
    }

    @Test fun `nothing to offer while routing is switched off`() {
        // Offering to add to a list nothing consults would be a lie.
        assertNull(Freedium.unlockCandidate("https://uxdesign.cc/post-x", settings("medium.com", enabled = false)))
    }

    @Test fun `nothing to offer for a link that is already a mirror url`() {
        val s = settings("medium.com")
        assertNull(Freedium.unlockCandidate("https://freedium-mirror.cfd/https://medium.com/@a/p-1a2b3c4d5e6f", s))
    }

    @Test fun `nothing to offer for a custom mirror the user configured`() {
        val s = settings("medium.com", mirror = "https://my-mirror.example")
        assertNull(Freedium.unlockCandidate("https://my-mirror.example/https://medium.com/@a/p", s))
    }

    @Test fun `garbage in, no offer out`() {
        val s = settings("medium.com")
        assertNull(Freedium.unlockCandidate("not a url at all", s))
    }

    /**
     * The invariant. An offer means "routing would take this if you said yes",
     * so offering and routing must never both be true for the same URL, and a
     * link that isn't routed must always have something to offer.
     */
    @Test fun `offer and route are exact complements`() {
        val s = settings("medium.com", "uxdesign.cc")
        val urls = listOf(
            "https://medium.com/@a/post-1a2b3c4d5e6f",
            "https://towards.medium.com/x",
            "https://uxdesign.cc/y",
            "https://netflixtechblog.com/z",
            "https://www.nytimes.com/2026/01/01/a.html",
            "https://blog.example.co.uk/post",
        )
        for (url in urls) {
            val routed = Freedium.shouldRoute(url, s)
            val offered = Freedium.unlockCandidate(url, s) != null
            assertFalse("$url is both routed and offered", routed && offered)
            assertFalse("$url is neither routed nor offered", !routed && !offered)
        }
    }

    /** Accepting an offer must actually make the link route — end to end. */
    @Test fun `accepting the offer is what makes the link route`() {
        val before = settings("medium.com")
        val url = "https://uxdesign.cc/some-post-1a2b3c4d5e6f"
        assertFalse(Freedium.shouldRoute(url, before))

        val domain = Freedium.unlockCandidate(url, before)!!
        val after = before.copy(freediumDomains = before.freediumDomains + domain)

        assert(Freedium.shouldRoute(url, after)) { "adding $domain should route $url" }
        assertNull("offer is spent once accepted", Freedium.unlockCandidate(url, after))
    }
}
