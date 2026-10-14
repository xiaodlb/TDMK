package com.xiaodlb.tdmk

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.xiaodlb.tdmk.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runners.MethodSorters
import java.io.File
import org.json.JSONObject

/** Also run the two methods separately with an external force-stop/relaunch between them. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TaskDetailsRestartTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val provider get()=(compose.activity.application as TdmkApplication).databaseProvider
    private val marker get()=File(compose.activity.filesDir,"task17-restart-check.json")
    private fun waitTag(tag:String) { compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun tasks() { waitTag("tab_Tasks"); compose.onNodeWithTag("tab_Tasks").performClick(); waitTag("task_tab_anytime"); compose.onNodeWithTag("task_tab_anytime").performClick() }
    @Test fun aSaveThroughRealApp() {
        val task=provider.getTaskRepository().create(TaskContent("Task17 restart fixture"))
        tasks(); waitTag("details_${task.id}")
        compose.onNodeWithTag("details_${task.id}").performScrollTo().performClick(); waitTag("details_title")
        compose.onNodeWithTag("details_title").performScrollTo().performTextReplacement("Task17 offline saved")
        compose.onNodeWithTag("details_note").performScrollTo().performTextReplacement("Offline note")
        compose.onNodeWithTag("details_priority_low").performScrollTo().performClick()
        compose.onNodeWithTag("details_save").performScrollTo(); android.os.SystemClock.sleep(400); compose.onNodeWithTag("details_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_details").fetchSemanticsNodes().isEmpty() }
        val saved=provider.getTaskRepository().read(task.id)
        assertEquals(task.id,saved.id); assertEquals(task.createdAt,saved.createdAt)
        assertEquals("Task17 offline saved",saved.title)
        marker.writeText(JSONObject().put("id",task.id).put("createdAt",task.createdAt.toEpochMilli()).put("updatedAt",saved.updatedAt.toEpochMilli()).toString())
    }
    @Test fun bVerifySavedValuesAndClean() {
        val expected=JSONObject(marker.readText()); val id=expected.getString("id")
        val task=provider.getTaskRepository().read(id)
        assertEquals("Task17 offline saved",task.title); assertEquals("Offline note",task.shortNote); assertEquals("low",task.priority)
        assertEquals(expected.getLong("createdAt"),task.createdAt.toEpochMilli()); assertEquals(expected.getLong("updatedAt"),task.updatedAt.toEpochMilli())
        tasks(); waitTag("details_$id"); compose.onNodeWithTag("details_$id").performScrollTo().performClick(); waitTag("details_note")
        compose.onNodeWithTag("details_title").assertTextContains("Task17 offline saved")
        compose.onNodeWithTag("details_note").assertTextContains("Offline note")
        compose.onNodeWithTag("details_priority_low").assertIsSelected()
        compose.onNodeWithTag("details_title").performScrollTo()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.takeScreenshot()?.let { bitmap ->
            val target=File(compose.activity.getExternalFilesDir(null),"task17-restart.png")
            target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            automation.executeShellCommand("cp ${target.absolutePath} /sdcard/Download/tdmk-task17-restart.png").use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
        }
        compose.onNodeWithTag("details_cancel").performScrollTo().performClick()
        provider.getTaskRepository().delete(id); marker.delete()
    }
}
