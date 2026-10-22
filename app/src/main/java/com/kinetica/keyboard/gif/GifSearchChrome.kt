package com.kinetica.keyboard.gif

import java.net.URI

/** Presentation changes belong only to the Google results page we open. */
object GifSearchChrome {
    fun isResultsPage(url: String?): Boolean = runCatching {
        val uri = URI(url ?: return false)
        uri.scheme.equals("https", true) &&
            (uri.host.equals("www.google.com", true) || uri.host.equals("google.com", true)) &&
            uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) && uri.path == "/search"
    }.getOrDefault(false)
}
