package dev.javohir.podcast.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.javohir.podcast.data.settings.AppSettings
import dev.javohir.podcast.data.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setDefaultSpeed(speed: Float) = viewModelScope.launch { settingsStore.setDefaultSpeed(speed) }
    fun setCompletionThreshold(v: Float) = viewModelScope.launch { settingsStore.setCompletionThreshold(v) }
    fun setSeekForwardMs(ms: Int) = viewModelScope.launch { settingsStore.setSeekForwardMs(ms) }
    fun setSeekBackMs(ms: Int) = viewModelScope.launch { settingsStore.setSeekBackMs(ms) }
    fun setResumeRewindMs(ms: Int) = viewModelScope.launch { settingsStore.setResumeRewindMs(ms) }
    fun setRssRefreshHours(h: Int) = viewModelScope.launch { settingsStore.setRssRefreshHours(h) }
    fun setWifiOnlyDownloads(v: Boolean) = viewModelScope.launch { settingsStore.setWifiOnlyDownloads(v) }
    fun setAutoDeleteListened(v: Boolean) = viewModelScope.launch { settingsStore.setAutoDeleteListenedDownloads(v) }
    fun setSkipSilence(v: Boolean) = viewModelScope.launch { settingsStore.setSkipSilence(v) }
    fun setTheme(theme: String) = viewModelScope.launch { settingsStore.setTheme(theme) }
}
