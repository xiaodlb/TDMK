package com.xiaodlb.tdmk

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.content.pm.ActivityInfo
import com.xiaodlb.tdmk.data.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.util.UUID

class TaskTabsIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val provider get() = (compose.activity.application as TdmkApplication).databaseProvider
    private val tasks get() = provider.getTaskRepository()
    private val lists get() = provider.getListRepository()
    private val createdTasks = mutableListOf<String>()
    private val createdLists = mutableListOf<String>()
    private val originalName by lazy { lists.initialize().name }

    private fun task(title: String, listId: String, date: LocalDate?, priority: String = "medium") =
        tasks.create(TaskContent(title, listId, date, priority)).also { createdTasks += it.id }
    private fun tab(key: String) {
        compose.onNodeWithTag("task_tab_$key").performScrollTo().performClick()
        compose.onNodeWithTag("task_tab_$key").assertIsSelected()
    }
    private fun present(id: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_$id").fetchSemanticsNodes().isNotEmpty() }
    private fun absent(id: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_$id").fetchSemanticsNodes().isEmpty() }

    @Before fun openTasks() {
        compose.onNodeWithTag("tab_Tasks").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_subtabs").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun cleanup() {
        createdTasks.forEach { id -> try { tasks.delete(id) } catch (_: NoSuchElementException) {} }
        createdLists.forEach { id -> try { lists.delete(id) } catch (_: NoSuchElementException) {} }
        lists.rename(lists.initialize().id, originalName)
        compose.activity.getSharedPreferences("task_navigation", android.content.Context.MODE_PRIVATE).edit().remove("selected_tab").commit()
    }

    @Test fun roomViewsCompletionDetailsAndTabRestoration() {
        val today = LocalDate.now()
        val inbox = lists.initialize()
        val custom = lists.create("Work ${UUID.randomUUID()}").also { createdLists += it.id }
        val due = task("Due ${UUID.randomUUID()}", custom.id, today, "high")
        val future = task("Future ${UUID.randomUUID()}", custom.id, today.plusDays(7))
        val beyond = task("Beyond ${UUID.randomUUID()}", custom.id, today.plusDays(8))
        val old = task("Old ${UUID.randomUUID()}", custom.id, today.minusDays(1))
        tab("today"); present(due.id); absent(old.id)
        compose.onNodeWithTag("complete_${due.id}").assertContentDescriptionContains("Complete ${due.title}")
        tab("upcoming"); present(future.id); absent(beyond.id)
        tab("list_${custom.id}"); present(old.id); present(beyond.id)
        compose.onNodeWithTag("complete_${due.id}").performScrollTo().performClick()
        compose.waitUntil(10_000) { tasks.read(due.id).completedAt != null }
        compose.onNodeWithTag("details_${due.id}").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("details_title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("details_cancel").performClick()
        compose.onNodeWithTag("tab_Notes").performClick()
        compose.onNodeWithTag("tab_Tasks").performClick()
        compose.onNodeWithTag("task_tab_list_${custom.id}").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_tab_list_${custom.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("task_tab_list_${custom.id}").assertIsSelected()
        lists.delete(custom.id)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_tab_list_${custom.id}").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("task_tab_today").assertIsSelected()
        assertEquals(inbox.id, lists.initialize().id)
    }

    @Test fun renamedInboxQuickEntryAndSharedDraft() {
        val inbox = lists.initialize()
        originalName
        lists.rename(inbox.id, "General ${UUID.randomUUID()}")
        tab("anytime")
        val title = "Quick ${UUID.randomUUID()}"
        compose.onNodeWithTag("task_title").performTextReplacement(title)
        tab("today")
        tab("anytime")
        compose.onNodeWithTag("task_title").assertTextContains(title)
        compose.onNodeWithTag("save_task").performClick()
        compose.waitUntil(10_000) { tasks.readAllForTestTitle(title).isNotEmpty() }
        val saved = tasks.readAllForTestTitle(title).single().also { createdTasks += it.id }
        assertEquals(inbox.id, saved.listId)
        assertEquals("medium", saved.priority)
        assertNull(saved.scheduledDate)
        present(saved.id)
        tab("list_${inbox.id}"); present(saved.id)
    }

    private fun TaskRepository.readAllForTestTitle(title: String): List<Task> =
        kotlinx.coroutines.runBlocking { viewCandidates().first() }.filter { it.title == title }

    @Test fun emptyLongResultsAndLargeLandscapeRemainReachable() {
        tab("today")
        val custom = lists.create("Long ${UUID.randomUUID()}").also { createdLists += it.id }
        val empty = lists.create("Empty ${UUID.randomUUID()}").also { createdLists += it.id }
        val ids = (1..24).map { task("Row $it ${UUID.randomUUID()}", custom.id, null).id }
        tab("list_${custom.id}")
        val instrument = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) = instrument.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
        val priorScale = shell("settings get system font_scale").trim()
        try {
            shell("settings put system font_scale 2.0")
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                compose.onAllNodesWithTag("task_${ids.last()}").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            android.os.SystemClock.sleep(500)
            compose.onNodeWithTag("task_${ids.last()}").performScrollTo().assertExists()
            compose.onNodeWithTag("task_subtabs").performScrollTo()
            compose.onNodeWithTag("task_tab_list_${custom.id}").assertIsSelected()
            tab("list_${empty.id}")
            compose.onNodeWithTag("task_title").performScrollTo().assertExists()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("tasks_empty").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            shell("settings put system font_scale $priorScale")
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
