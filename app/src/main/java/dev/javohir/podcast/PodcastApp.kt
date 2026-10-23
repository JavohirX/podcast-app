package dev.javohir.podcast

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.settings.SettingsStore
import dev.javohir.podcast.work.WorkInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PodcastApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var downloadCoordinator: DownloadCoordinator

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            WorkInitializer.schedule(this@PodcastApp, settingsStore)
            downloadCoordinator.reconcile()
        }
    }
}
