package dev.javohir.podcast.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class YouTubeVideoMeta(
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String
)

@Singleton
class YouTubeMetadataFetcher @Inject constructor(private val okHttpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(url: String): YouTubeVideoMeta? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url) ?: return@withContext null
        val oembedUrl =
            "https://www.youtube.com/oembed?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D$videoId&format=json"
        val request = Request.Builder()
            .url(oembedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .build()
        val responseBody = try {
            val resp = okHttpClient.newCall(request).execute()
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string() ?: return@withContext null
        } catch (e: Exception) {
            return@withContext null
        }
        val root = try {
            json.parseToJsonElement(responseBody).jsonObject
        } catch (e: Exception) {
            return@withContext null
        }
        val title = root["title"]?.jsonPrimitive?.content ?: return@withContext null
        val author = root["author_name"]?.jsonPrimitive?.content ?: ""
        // Use maxresdefault; if unavailable the player falls back to hqdefault automatically
        val thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        YouTubeVideoMeta(videoId, title, author, thumbnailUrl)
    }

    companion object {
        // Matches: youtu.be/ID  ·  ?v=ID  ·  /shorts/ID  ·  /embed/ID
        private val VIDEO_ID_REGEX =
            Regex("""(?:youtu\.be/|[?&]v=|/(?:shorts|embed|v)/)([A-Za-z0-9_-]{11})""")

        fun extractVideoId(url: String): String? =
            VIDEO_ID_REGEX.find(url)?.groupValues?.getOrNull(1)
    }
}
