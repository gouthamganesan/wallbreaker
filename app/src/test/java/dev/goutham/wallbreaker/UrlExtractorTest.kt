package dev.goutham.wallbreaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlExtractorTest {

    @Test fun `bare url is returned as-is`() {
        assertEquals(
            "https://medium.com/@a/post-123",
            UrlExtractor.firstUrl("https://medium.com/@a/post-123"),
        )
    }

    @Test fun `url embedded in shared text is extracted`() {
        // Medium's share sheet typically sends "Title https://…"
        assertEquals(
            "https://medium.com/@a/great-read-9z8y",
            UrlExtractor.firstUrl("A great read https://medium.com/@a/great-read-9z8y"),
        )
    }

    @Test fun `trailing punctuation is stripped`() {
        assertEquals(
            "https://medium.com/@a/post",
            UrlExtractor.firstUrl("check this out (https://medium.com/@a/post)."),
        )
    }

    @Test fun `first url wins when several are present`() {
        assertEquals(
            "https://medium.com/@a/first",
            UrlExtractor.firstUrl("https://medium.com/@a/first and https://medium.com/@a/second"),
        )
    }

    @Test fun `text with no url yields null`() {
        assertNull(UrlExtractor.firstUrl("just some words, no link here"))
    }

    @Test fun `null input yields null`() {
        assertNull(UrlExtractor.firstUrl(null))
    }

    @Test fun `http scheme is accepted`() {
        assertEquals("http://example.com/x", UrlExtractor.firstUrl("http://example.com/x"))
    }
}
