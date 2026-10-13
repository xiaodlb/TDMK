package com.xiaodlb.tdmk

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.xiaodlb.tdmk.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runners.MethodSorters
import org.json.JSONObject
import java.io.File

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TaskChecklistRestartTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val provider get()=(compose.activity.application as TdmkApplication).databaseProvider
    private val marker get()=File(compose.activity.filesDir,"task18-restart.json")
    private fun waitTag(tag:String) { compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(tag:String) { val node=compose.onNodeWithTag(tag); if(!tag.startsWith("tab_") && !tag.startsWith("confirm_")) node.performScrollTo(); if(tag=="details_save") android.os.SystemClock.sleep(700); node.performClick() }
    private fun edit(tag:String,text:String)=compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
    private fun open(id:String) { waitTag("tab_Tasks"); click("tab_Tasks"); waitTag("details_$id"); click("details_$id"); waitTag("new_child_text") }
    @Test fun aSaveThroughRealApp() {
        val task=provider.getTaskRepository().create(TaskContent("Task18 offline fixture",shortNote="Preserved note"))
        val subs=provider.getSubtaskRepository(); val retained=subs.create(task.id,"Original"); val deleted=subs.create(task.id,"Delete me")
        open(task.id); edit("child_text_${retained.id}","Offline edited 猫"); click("child_check_${retained.id}")
        click("child_delete_${deleted.id}"); click("confirm_child_delete"); edit("new_child_text","Offline added"); click("add_child")
        val added=ViewModelProvider(compose.activity)[TaskDetailsModel::class.java].children.last().key
        click("child_check_$added"); click("details_save")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_details").fetchSemanticsNodes().isEmpty() }
        val children=subs.readForParent(task.id); assertEquals(2,children.size); assertTrue(children.all { it.isCompleted })
        marker.writeText(JSONObject().put("parent",task.id).put("created",task.createdAt.toEpochMilli()).put("retained",retained.id).put("added",children.last().id).put("deleted",deleted.id).toString())
    }
    @Test fun bVerifySavedValuesAndClean() {
        val expected=JSONObject(marker.readText()); val id=expected.getString("parent")
        val task=provider.getTaskRepository().read(id); val children=provider.getSubtaskRepository().readForParent(id)
        assertEquals(expected.getLong("created"),task.createdAt.toEpochMilli()); assertEquals("Preserved note",task.shortNote)
        assertEquals(listOf(expected.getString("retained"),expected.getString("added")),children.map { it.id })
        assertEquals(listOf("Offline edited 猫","Offline added"),children.map { it.text }); assertEquals(listOf(0L,1L),children.map { it.order }); assertTrue(children.all { it.isCompleted })
        open(id); children.forEach { compose.onNodeWithTag("child_text_${it.id}").performScrollTo().assertTextContains(it.text); compose.onNodeWithTag("child_check_${it.id}").assertIsOn() }
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val file=File(compose.activity.getExternalFilesDir(null),"task18-restart.png")
        automation.takeScreenshot()!!.let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
        automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/tdmk-task18-restart.png").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
        click("details_cancel"); provider.getTaskRepository().delete(id); marker.delete()
    }
}
