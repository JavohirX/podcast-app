package dev.javohir.podcast.data.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class OkHttpDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
        
        val requestHeaders = request.headers()
        
        // Consistent User-Agent with the downloader worker
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        
        builder.addHeader("User-Agent", userAgent)
        builder.addHeader("Accept-Language", "en-US,en;q=0.9")
        builder.addHeader("Accept", "*/*")
        builder.addHeader("Cache-Control", "no-cache")

        requestHeaders.forEach { (key, values) ->
            values.forEach { value -> 
                if (!key.equals("User-Agent", ignoreCase = true) && 
                    !key.equals("Accept-Language", ignoreCase = true) &&
                    !key.equals("Accept", ignoreCase = true)) {
                    builder.addHeader(key, value)
                }
            }
        }
        
        if (request.httpMethod() == "POST") {
            val data = request.dataToSend() ?: ByteArray(0)
            builder.post(data.toRequestBody())
        } else {
            builder.get()
        }

        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string() ?: ""
        val headers = response.headers.toMultimap()
        return Response(response.code, response.message, headers, body, response.request.url.toString())
    }

    companion object {
        fun create(client: OkHttpClient): OkHttpDownloader = OkHttpDownloader(client)
    }
}
