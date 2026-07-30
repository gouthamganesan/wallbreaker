package dev.goutham.wallbreaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mirror must not leak into the Instapaper inbox. Uploading Freedium's HTML
 * as `content` stops Instapaper crawling the real page, so whatever title we
 * send is the title the user ends up seeing — and Freedium stamps its own name
 * into the page <title>.
 */
class FreediumTitleTest {

    @Test fun `strips the freedium suffix Freedium actually emits`() {
        assertEquals(
            "Why Smart People Are Suddenly Leaving Social Media in 2026",
            Freedium.cleanTitle("Why Smart People Are Suddenly Leaving Social Media in 2026 - Freedium"),
        )
    }

    @Test fun `strips en dash, em dash and pipe separators`() {
        assertEquals("A Title", Freedium.cleanTitle("A Title – Freedium"))
        assertEquals("A Title", Freedium.cleanTitle("A Title — Freedium"))
        assertEquals("A Title", Freedium.cleanTitle("A Title | Freedium"))
    }

    @Test fun `suffix match is case-insensitive and tolerates stray whitespace`() {
        assertEquals("A Title", Freedium.cleanTitle("A Title  -  freedium  "))
        assertEquals("A Title", Freedium.cleanTitle("A Title - FREEDIUM"))
    }

    @Test fun `leaves an ordinary title untouched`() {
        assertEquals("How to Do Hard Things", Freedium.cleanTitle("How to Do Hard Things"))
    }

    @Test fun `only the trailing occurrence counts`() {
        // An article genuinely about Freedium keeps the word in its title.
        assertEquals("Freedium is a paywall mirror", Freedium.cleanTitle("Freedium is a paywall mirror"))
    }

    @Test fun `null and blank collapse to null so callers fall through`() {
        assertNull(Freedium.cleanTitle(null))
        assertNull(Freedium.cleanTitle("   "))
        assertNull(Freedium.cleanTitle("- Freedium"))
    }
}
