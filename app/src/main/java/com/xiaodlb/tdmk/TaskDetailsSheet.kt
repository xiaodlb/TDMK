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
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !pending })
    ModalBottomSheet(onDismissRequest = model::dismiss, sheetState = sheet,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !model.pending),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(top = 32.dp).testTag("task_details")) {
        val view = LocalView.current
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        LaunchedEffect(view, dark) {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            }
        }
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = ShellSpacing.page).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(ShellSpacing.gap)) {
            Text("Task details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if(model.loading) CircularProgressIndicator(Modifier.testTag("details_loading"))
            val draft = model.draft
            if(draft != null) {
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
                    label = { Text("Scheduled date") }, supportingText = { Text("Optional · YYYY-MM-DD (0001–9999)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("details_date"))
                if(draft.date.isNotEmpty()) TextButton(onClick = { model.edit(draft.copy(date = "")) }, enabled = !model.pending,
                    modifier = Modifier.testTag("clear_date")) { Text("Clear date") }
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
        }
    }
    model.deleting?.let { child ->
        AlertDialog(onDismissRequest = model::cancelDelete,
            title = { Text("Delete checklist item?") },
            text = { Text("“${child.text}” will be permanently deleted when you Save the task. Canceling the sheet keeps the saved item.") },
            confirmButton = { TextButton(onClick = model::confirmDelete, modifier = Modifier.testTag("confirm_child_delete")) { Text("Delete on Save") } },
            dismissButton = { TextButton(onClick = model::cancelDelete, modifier = Modifier.testTag("cancel_child_delete")) { Text("Keep item") } })
    }
}

@Composable
private fun ChecklistEditor(model: TaskDetailsModel) {
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
                    TextButton(onClick = { model.askDelete(child) }, enabled = !model.pending,
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
