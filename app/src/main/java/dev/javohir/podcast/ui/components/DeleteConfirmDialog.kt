package dev.javohir.podcast.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.javohir.podcast.data.download.DeletePrompt
import dev.javohir.podcast.data.download.DeleteTarget

@Composable
fun DeleteConfirmDialog(
    prompt: DeletePrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.title) },
        text = { Text(prompt.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(prompt.confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Waits until the context menu dialog is gone before showing the confirm dialog.
 * Opening both in the same frame lets the menu's dismiss close the confirm immediately.
 */
@Composable
fun DeleteConfirmHost(
    menuOpen: Boolean,
    target: DeleteTarget?,
    prompt: DeletePrompt?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(menuOpen, target) {
        visible = !menuOpen && target != null
    }
    if (visible && prompt != null) {
        DeleteConfirmDialog(prompt = prompt, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}
