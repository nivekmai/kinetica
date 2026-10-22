package com.kinetica.keyboard.gif

import org.junit.Assert.*
import org.junit.Test

class GifSearchChromeTest {
    @Test fun presentationChangesAreLimitedToGoogleSearchResults() {
        assertTrue(GifSearchChrome.isResultsPage(GifPolicy.searchUrl("cats")))
        assertTrue(GifSearchChrome.isResultsPage("https://google.com:443/search?q=cat#preview"))
        listOf(null, "", "not a url", "http://www.google.com/search", "https://www.google.com:444/search",
            "https://www.google.com/sorry/index", "https://consent.google.com/search",
            "https://accounts.google.com/search", "https://example.com/search",
            "https://www.google.com.example.com/search", "https://www.google.com@other.com/search",
            "https://user@www.google.com/search", "https://www.google.com/", "https://www.google.com/search/"
        ).forEach { assertFalse(it, GifSearchChrome.isResultsPage(it)) }
    }
}
