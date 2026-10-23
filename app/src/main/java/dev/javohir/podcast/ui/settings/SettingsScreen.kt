package dev.javohir.podcast.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import dev.javohir.podcast.ui.components.rememberAnchoredListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.javohir.podcast.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onStorageClick: () -> Unit,
    onBackupClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val listState = rememberAnchoredListState("settings")

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(state = listState, contentPadding = padding) {
            item { SectionLabel("Playback") }
            item {
                SwitchListItem(
                    title = "Skip silence",
                    checked = settings.skipSilence,
                    onCheckedChange = viewModel::setSkipSilence
                )
            }
            item {
                SliderListItem(
                    title = "Default speed",
                    value = settings.defaultSpeed,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    display = { "%.1f×".format(it) },
                    onValueChange = viewModel::setDefaultSpeed
                )
            }
            item { SectionLabel("Feeds") }
            item {
                SwitchListItem(
                    title = "Wi-Fi only downloads",
                    checked = settings.wifiOnlyDownloads,
                    onCheckedChange = viewModel::setWifiOnlyDownloads
                )
            }
            item {
                SwitchListItem(
                    title = "Auto-delete listened downloads",
                    checked = settings.autoDeleteListenedDownloads,
                    onCheckedChange = viewModel::setAutoDeleteListened
                )
            }
            item { SectionLabel("Appearance") }
            item {
                var showThemePicker by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text(settings.theme.replaceFirstChar { it.uppercase() }) },
                    leadingContent = { Icon(Icons.Default.Palette, null) },
                    modifier = Modifier.clickable { showThemePicker = true }
                )
                if (showThemePicker) {
                    ThemePickerDialog(
                        current = settings.theme,
                        onSelect = { viewModel.setTheme(it); showThemePicker = false },
                        onDismiss = { showThemePicker = false }
                    )
                }
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Storage") },
                    leadingContent = { Icon(Icons.Default.Storage, null) },
                    modifier = Modifier.clickable(onClick = onStorageClick)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Backup & Restore") },
                    leadingContent = { Icon(Icons.Default.Backup, null) },
                    modifier = Modifier.clickable(onClick = onBackupClick)
                )
            }
            item { SectionLabel("About") }
            item {
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchListItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
    HorizontalDivider()
}

@Composable
private fun SliderListItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(display(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
    HorizontalDivider()
}

@Composable
private fun ThemePickerDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                listOf("system", "light", "dark").forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == option, onClick = { onSelect(option) })
                        Spacer(Modifier.width(8.dp))
                        Text(option.replaceFirstChar { it.uppercase() })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
