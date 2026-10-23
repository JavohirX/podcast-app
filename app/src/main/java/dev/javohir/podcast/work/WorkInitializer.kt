package dev.javohir.podcast.work

import android.content.Context
import androidx.work.*
import dev.javohir.podcast.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object WorkInitializer {

    suspend fun schedule(context: Context, settingsStore: SettingsStore) {
        val settings = settingsStore.settings.first()
        val refreshHours = settings.rssRefreshHours.toLong()

        val refreshWork = PeriodicWorkRequestBuilder<RefreshFeedsWorker>(refreshHours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        val rescanWork = PeriodicWorkRequestBuilder<RescanFoldersWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresDeviceIdle(true)
                    .build()
            )
            .build()

        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork("refresh_feeds", ExistingPeriodicWorkPolicy.UPDATE, refreshWork)
        wm.enqueueUniquePeriodicWork("rescan_folders", ExistingPeriodicWorkPolicy.UPDATE, rescanWork)

        // Pick up new/changed files shortly after launch — the periodic rescan
        // only runs while the device is idle.
        wm.enqueueUniqueWork(
            "rescan_folders_startup",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RescanFoldersWorker>().build()
        )
    }
}
