package dev.javohir.podcast.data.rss

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*

data class ParsedFeed(
    val title: String,
    val description: String?,
    val author: String?,
    val imageUrl: String?,
    val link: String?,
    val items: List<ParsedEpisode>
)

data class ParsedEpisode(
    val guid: String?,
    val title: String?,
    val description: String?,
    val author: String?,
    val pubDate: Long?,
    val durationMs: Long?,
    val imageUrl: String?,
    val enclosureUrl: String?,
    val enclosureSize: Long?,
    val enclosureMimeType: String?
)

class RssParser {

    private val rfc822Formats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm Z", Locale.ENGLISH)
    )

    fun parse(xml: String): ParsedFeed? {
        return try {
            parseInternal(xml)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseInternal(xml: String): ParsedFeed {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(xml))
        }

        var feedTitle = ""
        var feedDescription: String? = null
        var feedAuthor: String? = null
        var feedImageUrl: String? = null
        var feedLink: String? = null
        val episodes = mutableListOf<ParsedEpisode>()

        var inChannel = false
        var inItem = false
        var inImage = false

        // Item accumulator
        var guid: String? = null
        var title: String? = null
        var description: String? = null
        var author: String? = null
        var pubDate: Long? = null
        var durationMs: Long? = null
        var imageUrl: String? = null
        var enclosureUrl: String? = null
        var enclosureSize: Long? = null
        var enclosureMime: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val ns = parser.namespace ?: ""
            val name = parser.name ?: ""

            when (event) {
                XmlPullParser.START_TAG -> when {
                    name == "channel" -> inChannel = true
                    name == "item" && inChannel -> inItem = true
                    name == "image" && inChannel && !inItem -> inImage = true
                    inItem -> when {
                        name == "title" -> title = parser.nextText().trim()
                        name == "guid" -> guid = parser.nextText().trim()
                        name == "description" || name == "encoded" -> {
                            if (description == null) description = parser.nextText().trim()
                        }
                        name == "author" || (name == "author" && ns.contains("itunes")) -> {
                            author = parser.nextText().trim()
                        }
                        name == "pubDate" -> pubDate = parseDate(parser.nextText().trim())
                        name == "duration" && ns.contains("itunes") -> {
                            durationMs = parseDuration(parser.nextText().trim())
                        }
                        name == "image" && ns.contains("itunes") -> {
                            imageUrl = parser.getAttributeValue(null, "href")
                        }
                        name == "enclosure" -> {
                            enclosureUrl = parser.getAttributeValue(null, "url")
                            enclosureSize = parser.getAttributeValue(null, "length")?.toLongOrNull()
                            enclosureMime = parser.getAttributeValue(null, "type")
                        }
                        name == "subtitle" && ns.contains("itunes") && description == null -> {
                            description = parser.nextText().trim()
                        }
                        name == "author" && ns.contains("itunes") && author == null -> {
                            author = parser.nextText().trim()
                        }
                    }
                    inChannel && !inItem && !inImage -> when {
                        name == "title" -> feedTitle = parser.nextText().trim()
                        name == "description" && feedDescription == null -> feedDescription = parser.nextText().trim()
                        name == "link" && ns.isEmpty() -> feedLink = parser.nextText().trim()
                        name == "author" && ns.contains("itunes") -> feedAuthor = parser.nextText().trim()
                        name == "image" && ns.contains("itunes") -> {
                            feedImageUrl = parser.getAttributeValue(null, "href")
                        }
                        name == "subtitle" && ns.contains("itunes") && feedDescription == null -> {
                            feedDescription = parser.nextText().trim()
                        }
                    }
                    inImage && !inItem -> when (name) {
                        "url" -> if (feedImageUrl == null) feedImageUrl = parser.nextText().trim()
                    }
                }
                XmlPullParser.END_TAG -> when {
                    name == "item" && inItem -> {
                        // Only keep items with an audio enclosure
                        if (enclosureUrl != null && (enclosureMime == null || enclosureMime!!.startsWith("audio"))) {
                            val resolvedGuid = guid ?: enclosureUrl
                            episodes.add(
                                ParsedEpisode(
                                    guid = resolvedGuid,
                                    title = title,
                                    description = description,
                                    author = author,
                                    pubDate = pubDate,
                                    durationMs = durationMs,
                                    imageUrl = imageUrl,
                                    enclosureUrl = enclosureUrl,
                                    enclosureSize = enclosureSize,
                                    enclosureMimeType = enclosureMime
                                )
                            )
                        }
                        // Reset item state
                        guid = null; title = null; description = null; author = null
                        pubDate = null; durationMs = null; imageUrl = null
                        enclosureUrl = null; enclosureSize = null; enclosureMime = null
                        inItem = false
                    }
                    name == "image" && inImage -> inImage = false
                    name == "channel" -> inChannel = false
                }
            }
            event = parser.next()
        }

        return ParsedFeed(
            title = feedTitle.ifBlank { "Unknown Feed" },
            description = feedDescription,
            author = feedAuthor,
            imageUrl = feedImageUrl,
            link = feedLink,
            items = episodes
        )
    }

    private fun parseDate(raw: String): Long? {
        for (fmt in rfc822Formats) {
            try {
                return fmt.parse(raw)?.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseDuration(raw: String): Long? {
        if (raw.isBlank()) return null
        return try {
            val parts = raw.split(":").map { it.trim().toInt() }
            when (parts.size) {
                1 -> parts[0] * 1000L
                2 -> (parts[0] * 60L + parts[1]) * 1000L
                3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
                else -> null
            }
        } catch (_: Exception) {
            raw.toLongOrNull()?.times(1000L)
        }
    }
}
