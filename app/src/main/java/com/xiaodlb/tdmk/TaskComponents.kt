package com.xiaodlb.tdmk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.xiaodlb.tdmk.data.Task

@Composable
fun TaskRow(task: Task, listName: String, pending: Boolean, error: String?, onCompletion: (String) -> Unit, onDetails: (String) -> Unit, detailsModifier: Modifier = Modifier) {
    val completed = task.completedAt != null
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("task_${task.id}")) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                Checkbox(checked = completed, enabled = !pending, onCheckedChange = { onCompletion(task.id) },
                    modifier = Modifier.padding(top = 4.dp).testTag("complete_${task.id}").semantics {
                        contentDescription = if (completed) "Reopen ${task.title}" else "Complete ${task.title}"
                    })
                Column(detailsModifier.focusProperties { canFocus = true }.weight(1f).clickable(enabled = !pending, onClickLabel = "Open task details") { onDetails(task.id) }
                    .testTag("details_${task.id}").semantics(mergeDescendants = true) {
                        stateDescription = if (completed) "Completed" else "Not completed"
                    }.padding(vertical = 16.dp, horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None)
                    Text("${when(task.priority) { "high" -> "High"; "low" -> "Low"; else -> "Medium" }} priority · $listName",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    task.scheduledDate?.let { Text("Scheduled $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)
                .testTag("completion_error_${task.id}").semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
}

@Composable
fun QuickEntry(draft: String, saving: Boolean, error: String?, onDraft: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("quick_entry"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = draft, onValueChange = onDraft, enabled = !saving,
            label = { Text(stringResource(R.string.task_title)) }, minLines = 1, maxLines = 4,
            modifier = Modifier.fillMaxWidth().testTag("task_title"), isError = error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onSave() }))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("entry_error").semantics { liveRegion = LiveRegionMode.Polite }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f).testTag("save_task")) { Text(stringResource(if(saving) R.string.saving_task else R.string.save_task)) }
            OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.weight(1f).testTag("cancel_task")) { Text(stringResource(R.string.cancel)) }
        }
    }
}
