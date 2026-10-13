package com.xiaodlb.tdmk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.xiaodlb.tdmk.data.DatabaseProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailsSheet(model: TaskDetailsModel, provider: DatabaseProvider) {
    val id = model.selectedId ?: return
    val pending by rememberUpdatedState(model.pending)
    val maximumContentHeight = (LocalConfiguration.current.screenHeightDp - 96).coerceAtLeast(96).dp
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !pending })
    ModalBottomSheet(onDismissRequest = model::dismiss, sheetState = sheet,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !model.pending),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { BottomSheetDefaults.DragHandle(modifier = Modifier.testTag("details_handle")) },
        modifier = Modifier.testTag("task_details")) {
        val view = LocalView.current
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        LaunchedEffect(view, dark) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = maximumContentHeight).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = ShellSpacing.page).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(ShellSpacing.gap)) {
            Text("Task details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if(model.loading) CircularProgressIndicator(Modifier.testTag("details_loading"))
            val draft = model.draft
            if(draft != null) {
                val recurring = model.series
                if(recurring != null) {
                    val revision = recurring.revisions.last().revision
                    Text("Series ${if(recurring.template.enabled) "Enabled" else "Paused"} · ${revision.frequency.replaceFirstChar { it.uppercase() }} · anchor ${revision.anchorDate}",
                        modifier = Modifier.testTag("details_series_status"))
                    model.saved?.let { task -> if(task.slotDate != task.scheduledDate) Text("Original slot ${task.slotDate} · displayed ${task.scheduledDate}", modifier = Modifier.testTag("details_slot")) }
                    Text("Edit", style = MaterialTheme.typography.titleMedium)
                    listOf(false to "This occurrence", true to "Whole series").forEach { (whole, label) ->
                        Row(Modifier.fillMaxWidth().selectable(model.seriesScope == whole, enabled = !model.pending,
                            role = Role.RadioButton, onClick = { model.chooseScope(whole) })
                            .testTag(if(whole) "details_scope_series" else "details_scope_occurrence").padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(model.seriesScope == whole, null); Text(label)
                        }
                    }
                }
                OutlinedTextField(draft.title, { model.edit(draft.copy(title = it)) }, enabled = !model.pending,
                    label = { Text("Title") }, modifier = Modifier.fillMaxWidth().testTag("details_title"))
                var expanded by remember(id) { mutableStateOf(false) }
                val chosen = model.lists.firstOrNull { it.id == draft.listId }
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !model.pending,
                        modifier = Modifier.fillMaxWidth().testTag("details_list").semantics { stateDescription = chosen?.name ?: "Missing list" }) {
                        Text("List: ${chosen?.name ?: "Missing list"}${chosen?.let { " · " + if(it.isDefault) "Default" else "List ${it.order}" }.orEmpty()}")
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        model.lists.forEach { list -> DropdownMenuItem(text = { Text("${list.name} · ${if(list.isDefault) "Default" else "List ${list.order}"}") },
                            onClick = { model.edit(draft.copy(listId = list.id)); expanded = false },
                            modifier = Modifier.testTag("choose_list_${list.id}").semantics { selected = list.id == draft.listId }) }
                    }
                }
                OutlinedTextField(draft.date, { model.edit(draft.copy(date = it)) }, enabled = !model.pending,
                    label = { Text(if(model.seriesScope && recurring != null) "Series start date" else "Scheduled date") }, supportingText = { Text("Optional · YYYY-MM-DD (0001–9999)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("details_date"))
                if(draft.date.isNotEmpty()) TextButton(onClick = { model.edit(draft.copy(date = "")) }, enabled = !model.pending,
                    modifier = Modifier.testTag("clear_date")) { Text("Clear date") }
                if(recurring == null || model.seriesScope) {
                    Text("Repeat", style = MaterialTheme.typography.titleMedium)
                    listOf("one_time" to "One time", "daily" to "Daily", "weekly" to "Weekly", "monthly" to "Monthly").forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().selectable(model.frequency == value, enabled = !model.pending,
                            role = Role.RadioButton, onClick = { model.chooseFrequency(value) })
                            .testTag("details_frequency_$value").padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(model.frequency == value, null); Text(label)
                        }
                    }
                    if(model.preview.isNotEmpty()) Text("Next dates: ${model.preview.joinToString(", ")}", modifier = Modifier.testTag("details_recurrence_preview"))
                    if(recurring != null) Text("Choosing One time pauses this series after confirmation. Existing occurrences stay saved.")
                }
                Text("Priority", style = MaterialTheme.typography.titleMedium)
                for(priority in listOf("high", "medium", "low")) {
                    val label = priority.replaceFirstChar { it.uppercase() }
                    Row(Modifier.fillMaxWidth().testTag("details_priority_$priority")
                        .selectable(selected = draft.priority == priority, enabled = !model.pending, role = Role.RadioButton,
                            onClick = { model.edit(draft.copy(priority = priority)) }).padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(draft.priority == priority, null); Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(draft.note, { model.edit(draft.copy(note = it)) }, enabled = !model.pending,
                    label = { Text("Short note (optional)") }, minLines = 3, maxLines = 8,
                    modifier = Modifier.fillMaxWidth().testTag("details_note"))
                ChecklistEditor(model)
            }
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("details_error").semantics { liveRegion = LiveRegionMode.Polite }) }
            if(draft == null && !model.loading) Button(onClick = { model.open(id, provider) }, modifier = Modifier.testTag("details_retry")) { Text("Retry") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if(draft != null) Button(onClick = { model.save(provider) }, enabled = !model.pending,
                    modifier = Modifier.weight(1f).testTag("details_save")) { Text(if(model.pending) "Saving…" else "Save") }
                OutlinedButton(onClick = model::dismiss, enabled = !model.pending,
                    modifier = Modifier.weight(1f).testTag("details_cancel")) { Text("Cancel") }
            }
            if(model.series != null && draft != null) {
                if(model.series?.template?.enabled == false) OutlinedButton(onClick = { model.resume(provider) }, enabled = !model.pending,
                    modifier = Modifier.fillMaxWidth().testTag("details_resume")) { Text("Resume series") }
                TextButton(onClick = model::requestDelete, enabled = !model.pending,
                    modifier = Modifier.fillMaxWidth().testTag("details_delete_series")) { Text("Delete recurring task…") }
            }
        }
    }
    model.deleting?.let { child ->
        AlertDialog(onDismissRequest = model::cancelDelete,
            title = { Text("Delete checklist item?") },
            text = { Text("“${child.text}” will be permanently deleted when you Save the task. Canceling the sheet keeps the saved item.") },
            confirmButton = { TextButton(onClick = model::confirmDelete, modifier = Modifier.testTag("confirm_child_delete")) { Text("Delete on Save") } },
            dismissButton = { TextButton(onClick = model::cancelDelete, modifier = Modifier.testTag("cancel_child_delete")) { Text("Keep item") } })
    }
    when(model.confirmation) {
        "pause" -> AlertDialog(onDismissRequest = model::cancelConfirmation, title = { Text("Pause series?") },
            text = { Text("Existing occurrences and completed history remain. New dates will resume when you resume this series.") },
            confirmButton = { TextButton(onClick = { model.confirmPause(provider) }, modifier = Modifier.testTag("confirm_pause")) { Text("Pause series") } },
            dismissButton = { TextButton(onClick = model::cancelConfirmation) { Text("Keep repeating") } })
        "resume" -> AlertDialog(onDismissRequest = model::cancelConfirmation, title = { Text("Resume series?") },
            text = { Text("Missed due dates will be added once, while existing occurrences remain.") },
            confirmButton = { TextButton(onClick = { model.resume(provider) }, modifier = Modifier.testTag("confirm_resume")) { Text("Resume series") } },
            dismissButton = { TextButton(onClick = model::cancelConfirmation) { Text("Cancel") } })
        "delete_scope" -> AlertDialog(onDismissRequest = model::cancelConfirmation, title = { Text("Delete recurring task") },
            text = { Text("Choose which records to delete.") },
            confirmButton = { Column {
                TextButton(onClick = { model.confirmDelete(provider, false) }, modifier = Modifier.testTag("delete_one_choice")) { Text("This occurrence") }
                TextButton(onClick = { model.confirmDelete(provider, true) }, modifier = Modifier.testTag("delete_series_choice")) { Text("Entire series") }
            } }, dismissButton = { TextButton(onClick = model::cancelConfirmation) { Text("Cancel") } })
        "delete_confirm" -> AlertDialog(onDismissRequest = model::cancelConfirmation,
            title = { Text(if(model.seriesScope) "Delete entire series permanently?" else "Delete this occurrence permanently?") },
            text = { Text(if(model.seriesScope) "All occurrences, completed history and exclusions will be removed." else "This original date will be reserved so it does not return.") },
            confirmButton = { TextButton(onClick = { model.confirmDelete(provider, model.seriesScope) }, modifier = Modifier.testTag("confirm_recurrence_delete")) { Text("Delete permanently") } },
            dismissButton = { TextButton(onClick = model::cancelConfirmation) { Text("Cancel") } })
    }
}

@Composable
private fun ChecklistEditor(model: TaskDetailsModel) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Text("Checklist", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Text("Changes, including confirmed deletions, apply when you Save.", style = MaterialTheme.typography.bodyMedium)
    model.children.forEachIndexed { index, child ->
        key(child.key) {
            Column(Modifier.fillMaxWidth().testTag("child_${child.key}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(child.text, { model.editChild(child.key, text = it) }, enabled = !model.pending,
                    label = { Text("Checklist item ${index + 1}") }, modifier = Modifier.fillMaxWidth().testTag("child_text_${child.key}"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).heightIn(min = 48.dp).toggleable(child.checked, enabled = !model.pending,
                        role = Role.Checkbox, onValueChange = { model.editChild(child.key, checked = it) })
                        .testTag("child_check_${child.key}").semantics { contentDescription = "Complete checklist item ${index + 1}: ${child.text}" },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(child.checked, null, enabled = !model.pending); Text("Done")
                    }
                    TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); model.askDelete(child) }, enabled = !model.pending,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("child_delete_${child.key}")
                            .semantics { contentDescription = "Delete checklist item ${index + 1}: ${child.text}" }) { Text("Delete") }
                }
            }
        }
    }
    OutlinedTextField(model.newChildText, model::editNewChild, enabled = !model.pending,
        label = { Text("New checklist item") }, modifier = Modifier.fillMaxWidth().testTag("new_child_text"))
    OutlinedButton(onClick = model::addChild, enabled = !model.pending, modifier = Modifier.testTag("add_child")) { Text("Add item") }
}
