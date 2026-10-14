package com.idanck.nexplorer.core

import java.net.URI

/**
 * הכתובת של שרת WebDAV כמו שמדביקים אותה ("https://cloud.example.com/remote.php/dav/files/idan",
 * "nas.local:5006/dav") ← שרת, פורט (null — ברירת המחדל של https או http), הנתיב בשרת (בלי / בקצוות,
 * לא מקודד) ו-http. בלי scheme — https. בלי תלות באנדרואיד — נבדק ב-JVM.
 */
data class DavAddress(val host: String, val port: Int?, val path: String, val plainHttp: Boolean) {

    companion object {
        /** null — לא כתובת של שרת (scheme אחר, בלי שרת, עם שאילתה, או פורט לא תקין). */
        fun parse(text: String): DavAddress? {
            val raw = text.trim()
            if (raw.isEmpty()) return null
            // רווח בנתיב (תיקייה בשם "Idan Cohen") — כמו שדפדפן מקודד אותו.
            val withScheme = (if ("://" in raw) raw else "https://$raw").replace(" ", "%20")
            val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
            val plainHttp = when (uri.scheme?.lowercase()) {
                "https", "davs", "webdavs" -> false
                "http", "dav", "webdav" -> true
                else -> return null
            }
            val host = uri.host?.removeSurrounding("[", "]")?.takeIf { it.isNotEmpty() } ?: return null
            if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            if (uri.port != -1 && uri.port !in 1..65535) return null
            val port = uri.port.takeIf { it != -1 && it != if (plainHttp) 80 else 443 }
            val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }.joinToString("/")
            return DavAddress(host, port, path, plainHttp)
        }
    }
}
