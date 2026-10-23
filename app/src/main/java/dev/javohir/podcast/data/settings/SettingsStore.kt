package dev.javohir.podcast.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val defaultSpeed: Float = 1.0f,
    val completionThreshold: Float = 0.99f,
    val seekForwardMs: Int = 30_000,
    val seekBackMs: Int = 10_000,
    val resumeRewindMs: Int = 5_000,
    val rssRefreshHours: Int = 6,
    val wifiOnlyDownloads: Boolean = true,
    val autoDeleteListenedDownloads: Boolean = false,
    val skipSilence: Boolean = false,
    val theme: String = "system"
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val COMPLETION_THRESHOLD = floatPreferencesKey("completion_threshold")
        val SEEK_FORWARD_MS = intPreferencesKey("seek_forward_ms")
        val SEEK_BACK_MS = intPreferencesKey("seek_back_ms")
        val RESUME_REWIND_MS = intPreferencesKey("resume_rewind_ms")
        val RSS_REFRESH_HOURS = intPreferencesKey("rss_refresh_hours")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val AUTO_DELETE = booleanPreferencesKey("auto_delete_listened")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val THEME = stringPreferencesKey("theme")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            defaultSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            completionThreshold = prefs[Keys.COMPLETION_THRESHOLD] ?: 0.99f,
            seekForwardMs = prefs[Keys.SEEK_FORWARD_MS] ?: 30_000,
            seekBackMs = prefs[Keys.SEEK_BACK_MS] ?: 10_000,
            resumeRewindMs = prefs[Keys.RESUME_REWIND_MS] ?: 5_000,
            rssRefreshHours = prefs[Keys.RSS_REFRESH_HOURS] ?: 6,
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY_DOWNLOADS] ?: true,
            autoDeleteListenedDownloads = prefs[Keys.AUTO_DELETE] ?: false,
            skipSilence = prefs[Keys.SKIP_SILENCE] ?: false,
            theme = prefs[Keys.THEME] ?: "system"
        )
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.DEFAULT_SPEED] = speed }
    }

    suspend fun setCompletionThreshold(threshold: Float) {
        context.dataStore.edit { it[Keys.COMPLETION_THRESHOLD] = threshold }
    }

    suspend fun setSeekForwardMs(ms: Int) {
        context.dataStore.edit { it[Keys.SEEK_FORWARD_MS] = ms }
    }

    suspend fun setSeekBackMs(ms: Int) {
        context.dataStore.edit { it[Keys.SEEK_BACK_MS] = ms }
    }

    suspend fun setResumeRewindMs(ms: Int) {
        context.dataStore.edit { it[Keys.RESUME_REWIND_MS] = ms }
    }

    suspend fun setRssRefreshHours(hours: Int) {
        context.dataStore.edit { it[Keys.RSS_REFRESH_HOURS] = hours }
    }

    suspend fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = wifiOnly }
    }

    suspend fun setAutoDeleteListenedDownloads(autoDelete: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DELETE] = autoDelete }
    }

    suspend fun setSkipSilence(skip: Boolean) {
        context.dataStore.edit { it[Keys.SKIP_SILENCE] = skip }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME] = theme }
    }
}
