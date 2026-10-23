package dev.javohir.podcast.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val SPEED_PRESETS = listOf(1.0f, 1.2f, 1.5f, 1.8f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Playback Speed", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))

            // Stepper row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { onSpeedChange(step(currentSpeed, -1)) },
                    enabled = currentSpeed > 0.5f
                ) { Icon(Icons.Default.Remove, "Decrease") }

                Text(
                    text = "%.1f×".format(currentSpeed),
                    style = MaterialTheme.typography.headlineMedium
                )

                FilledTonalIconButton(
                    onClick = { onSpeedChange(step(currentSpeed, +1)) },
                    enabled = currentSpeed < 2.0f
                ) { Icon(Icons.Default.Add, "Increase") }
            }

            Spacer(Modifier.height(16.dp))

            // Snapping slider 0.5 → 2.0 in 0.1 steps (16 steps total)
            Slider(
                value = speedToSlider(currentSpeed),
                onValueChange = { onSpeedChange(sliderToSpeed(it)) },
                valueRange = 0f..15f,
                steps = 14,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Presets
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SPEED_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = currentSpeed == preset,
                        onClick = { onSpeedChange(preset) },
                        label = { Text("%.1f×".format(preset)) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun step(current: Float, direction: Int): Float {
    val tenths = (current * 10).roundToInt() + direction
    return (tenths.coerceIn(5, 20) / 10f)
}

private fun speedToSlider(speed: Float): Float = ((speed - 0.5f) * 10).coerceIn(0f, 15f)

private fun sliderToSpeed(slider: Float): Float = (slider.roundToInt() / 10f) + 0.5f
