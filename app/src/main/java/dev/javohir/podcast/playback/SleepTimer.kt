package dev.javohir.podcast.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class SleepTimerTarget {
    data class Duration(val durationMs: Long) : SleepTimerTarget()
    object EndOfEpisode : SleepTimerTarget()
}

class SleepTimer {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null
    private var onSleep: (() -> Unit)? = null
    private var onFade: ((Float) -> Unit)? = null

    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs

    fun start(target: SleepTimerTarget, onFadeVolume: (Float) -> Unit, onSleep: () -> Unit) {
        cancel()
        this.onSleep = onSleep
        this.onFade = onFadeVolume
        when (target) {
            is SleepTimerTarget.Duration -> startCountdown(target.durationMs)
            SleepTimerTarget.EndOfEpisode -> { /* triggered externally via onEpisodeEnd() */ }
        }
    }

    fun onEpisodeEnd() {
        onSleep?.invoke()
        cancel()
    }

    fun cancel() {
        job?.cancel()
        job = null
        _remainingMs.value = null
        onFade?.invoke(1f)
    }

    private fun startCountdown(durationMs: Long) {
        val endAt = System.currentTimeMillis() + durationMs
        val fadeStart = endAt - 5_000L
        job = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = endAt - now
                _remainingMs.value = remaining.coerceAtLeast(0L)
                when {
                    remaining <= 0 -> {
                        onFade?.invoke(0f)
                        delay(200)
                        onSleep?.invoke()
                        cancel()
                        break
                    }
                    now >= fadeStart -> {
                        val fadeProgress = 1f - ((endAt - now).toFloat() / 5_000f)
                        onFade?.invoke(1f - fadeProgress)
                    }
                    else -> onFade?.invoke(1f)
                }
                delay(200)
            }
        }
    }

    fun release() {
        scope.cancel()
    }
}
