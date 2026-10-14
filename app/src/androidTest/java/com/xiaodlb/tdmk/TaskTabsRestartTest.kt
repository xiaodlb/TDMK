package com.xiaodlb.tdmk

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.xiaodlb.tdmk.data.TaskContent
import org.junit.*
import org.junit.Assert.*
import org.junit.runners.MethodSorters
import org.json.JSONObject
import java.io.File

/** Run each method in a separate instrumentation invocation with force-stop between. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TaskTabsRestartTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val provider get() = (compose.activity.application as TdmkApplication).databaseProvider
    private val marker get() = File(compose.activity.filesDir, "task20-restart.json")
    private fun waitTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun aSaveListSelectionAndTask() {
        val list = provider.getListRepository().create("Offline tab")
        val task = provider.getTaskRepository().create(TaskContent("Offline selected task", list.id))
        compose.onNodeWithTag("tab_Tasks").performClick()
        waitTag("task_tab_list_${list.id}")
        compose.onNodeWithTag("task_tab_list_${list.id}").performScrollTo().performClick()
        compose.onNodeWithTag("task_tab_list_${list.id}").assertIsSelected()
        waitTag("task_${task.id}")
        marker.writeText(JSONObject().put("list", list.id).put("task", task.id).toString())
    }

    @Test fun bVerifyAfterOfflineForceStopAndClean() {
        val saved = JSONObject(marker.readText())
        val listId = saved.getString("list")
        val taskId = saved.getString("task")
        assertEquals(listId, provider.getTaskRepository().read(taskId).listId)
        compose.onNodeWithTag("tab_Tasks").performClick()
        waitTag("task_tab_list_$listId")
        compose.onNodeWithTag("task_tab_list_$listId").assertIsSelected()
        waitTag("task_$taskId")
        provider.getTaskRepository().delete(taskId)
        provider.getListRepository().delete(listId)
        compose.activity.getSharedPreferences("task_navigation", android.content.Context.MODE_PRIVATE).edit().remove("selected_tab").commit()
        marker.delete()
    }
}
