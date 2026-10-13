package com.xiaodlb.tdmk

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.xiaodlb.tdmk.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Run aSave, force-stop the app process, then run bVerify in a separate invocation. */
class TaskRecurrenceRestartTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val provider get() = (compose.activity.application as TdmkApplication).databaseProvider
    private val marker get() = File(compose.activity.filesDir, "task19-restart.json")
    private fun waitTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun aSave() {
        val date = LocalDate.now().minusDays(1)
        val task = provider.getTaskRepository().create(TaskContent("Task19 restart", scheduledDate = date))
        waitTag("tab_Tasks")
        compose.onNodeWithTag("tab_Tasks").performClick()
        // Dated rows can be outside Anytime; use the real MainActivity details model for the same sheet.
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[TaskDetailsModel::class.java].open(task.id, provider) }
        waitTag("details_title")
        compose.onNodeWithTag("details_frequency_daily").performScrollTo().performClick()
        compose.onNodeWithTag("details_recurrence_preview").assertExists()
        compose.onNodeWithTag("details_save").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_details").fetchSemanticsNodes().isEmpty() }
        val attached = provider.getTaskRepository().read(task.id)
        val seriesId = requireNotNull(attached.seriesId)
        val dueIds = provider.getDatabase().tasks().forSeries(seriesId).map { it.id }
        assertEquals(2, dueIds.size)
        marker.writeText(JSONObject().put("taskId", task.id).put("seriesId", seriesId)
            .put("createdAt", task.createdAt.toEpochMilli()).put("firstId", dueIds[0]).put("secondId", dueIds[1]).toString())
    }

    @Test fun bVerifyAndClean() {
        val expected = JSONObject(marker.readText())
        val taskId = expected.getString("taskId")
        val seriesId = expected.getString("seriesId")
        val task = provider.getTaskRepository().read(taskId)
        assertEquals(seriesId, task.seriesId)
        assertEquals(expected.getLong("createdAt"), task.createdAt.toEpochMilli())
        val repo = provider.getRecurrenceRepository()
        assertEquals("daily", repo.read(seriesId).revisions.last().revision.frequency)
        assertEquals(0, repo.reconcile(seriesId))
        assertEquals(listOf(expected.getString("firstId"), expected.getString("secondId")),
            provider.getDatabase().tasks().forSeries(seriesId).map { it.id })
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[TaskDetailsModel::class.java].open(taskId, provider) }
        waitTag("details_series_status")
        compose.onNodeWithTag("details_series_status").assert(hasText("Daily", substring = true))
        compose.onNodeWithTag("details_cancel").performScrollTo().performClick()
        repo.delete(seriesId)
        marker.delete()
    }
}
