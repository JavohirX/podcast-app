package dev.javohir.podcast.data.rss

import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class FetchResult(
    val body: String,
    val finalUrl: String,
    val etag: String?,
    val lastModified: String?
)

sealed class FetchOutcome {
    data class Success(val result: FetchResult) : FetchOutcome()
    object NotModified : FetchOutcome()
    data class Error(val message: String, val cause: Throwable? = null) : FetchOutcome()
}

@Singleton
class RssClient @Inject constructor(private val okHttpClient: OkHttpClient) {

    fun fetch(url: String, etag: String? = null, lastModified: String? = null): FetchOutcome {
        return try {
            val request = Request.Builder().url(url).apply {
                etag?.let { header("If-None-Match", it) }
                lastModified?.let { header("If-Modified-Since", it) }
            }.build()

            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                when {
                    resp.code == 304 -> FetchOutcome.NotModified
                    !resp.isSuccessful -> FetchOutcome.Error("HTTP ${resp.code}: ${resp.message}")
                    else -> {
                        val body = resp.body?.string() ?: return FetchOutcome.Error("Empty response body")
                        FetchOutcome.Success(
                            FetchResult(
                                body = body,
                                finalUrl = resp.request.url.toString(),
                                etag = resp.header("ETag"),
                                lastModified = resp.header("Last-Modified")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            FetchOutcome.Error(e.message ?: "Network error", e)
        }
    }
}
