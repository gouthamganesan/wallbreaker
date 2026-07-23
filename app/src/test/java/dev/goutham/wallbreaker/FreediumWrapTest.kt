package dev.goutham.wallbreaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreediumWrapTest {

    @Test fun `active base is the live mirror, not the dead one`() {
        assertEquals("https://freedium-mirror.cfd", Freedium.base)
        assertTrue("freedium.cfd kept as documented fallback", Freedium.BASES.contains("https://freedium.cfd"))
    }

    @Test fun `wrap keeps scheme and does not percent-encode the inner url`() {
        val url = "https://medium.com/@a/post-1a2b3c4d5e6f"
        assertEquals("https://freedium-mirror.cfd/https://medium.com/@a/post-1a2b3c4d5e6f", Freedium.wrap(url))
    }

    @Test fun `medium article with 12-hex slug is recognised`() {
        assertTrue(Freedium.looksLikeMediumArticle("https://medium.com/@a/some-post-1a2b3c4d5e6f"))
        assertTrue(Freedium.looksLikeMediumArticle("https://towards.medium.com/x-0011223344ff"))
        assertTrue(Freedium.looksLikeMediumArticle("https://medium.com/@a/post-1a2b3c4d5e6f?source=rss"))
    }

    @Test fun `medium non-article is not wrapped`() {
        // profile / listing pages have no 12-hex post id
        assertFalse(Freedium.looksLikeMediumArticle("https://medium.com/@someauthor"))
        assertFalse(Freedium.looksLikeMediumArticle("https://medium.com/tag/design"))
    }

    @Test fun `non-medium host is never wrapped`() {
        assertFalse(Freedium.looksLikeMediumArticle("https://example.com/post-1a2b3c4d5e6f"))
        // a look-alike host must not slip through the suffix check
        assertFalse(Freedium.looksLikeMediumArticle("https://notmedium.com/x-1a2b3c4d5e6f"))
        assertFalse(Freedium.looksLikeMediumArticle("https://medium.com.evil.com/x-1a2b3c4d5e6f"))
    }

    @Test fun `process wraps medium articles and passes everything else through`() {
        val article = "https://medium.com/@a/post-1a2b3c4d5e6f"
        assertEquals("https://freedium-mirror.cfd/$article", Freedium.process(article))

        val other = "https://example.com/article"
        assertEquals(other, Freedium.process(other))
    }

    @Test fun `garbage input does not crash the gate`() {
        assertFalse(Freedium.looksLikeMediumArticle("not a url"))
        assertFalse(Freedium.looksLikeMediumArticle(""))
    }
}
