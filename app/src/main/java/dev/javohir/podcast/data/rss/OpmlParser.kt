package dev.javohir.podcast.data.rss

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

class OpmlParser {

    fun parse(input: InputStream): List<String> {
        val urls = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(input, null)
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "outline") {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                        ?: parser.getAttributeValue(null, "xmlurl")
                    if (!xmlUrl.isNullOrBlank()) urls.add(xmlUrl.trim())
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return urls
    }

    fun generate(feeds: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<opml version="2.0">""")
        sb.appendLine("  <head><title>Podcast Subscriptions</title></head>")
        sb.appendLine("  <body>")
        for ((title, url) in feeds) {
            val safeTitle = title.replace("\"", "&quot;").replace("&", "&amp;").replace("<", "&lt;")
            val safeUrl = url.replace("\"", "&quot;").replace("&", "&amp;")
            sb.appendLine("""    <outline type="rss" text="$safeTitle" title="$safeTitle" xmlUrl="$safeUrl"/>""")
        }
        sb.appendLine("  </body>")
        sb.appendLine("</opml>")
        return sb.toString()
    }
}
