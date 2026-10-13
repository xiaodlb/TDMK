package com.xiaodlb.tdmk

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaodlb.tdmk.data.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.util.UUID

class TaskChecklistTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val provider get()=(compose.activity.application as TdmkApplication).databaseProvider
    private val tasks get()=provider.getTaskRepository()
    private val subs get()=provider.getSubtaskRepository()
    private val ids=mutableListOf<String>()
    private val series=mutableListOf<String>()
    private lateinit var parent:Task
    private var oldKeyboard="0"
    private fun shell(command:String)=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText().trim() } }
    private fun waitTag(tag:String) { compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(tag:String) { val n=compose.onNodeWithTag(tag); if(!tag.startsWith("tab_") && !tag.startsWith("confirm_") && !tag.startsWith("cancel_child")) n.performScrollTo(); compose.waitForIdle(); if(tag=="details_save") android.os.SystemClock.sleep(700); n.performClick() }
    private fun edit(tag:String,text:String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(text); compose.onNodeWithTag(tag).assertTextContains(text) }
    private fun open(id:String=parent.id) { waitTag("details_$id"); click("details_$id"); waitTag("new_child_text") }
    private fun closed() { compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_details").fetchSemanticsNodes().isEmpty() && compose.activity.hasWindowFocus() } }
    private fun add(text:String) { edit("new_child_text",text); click("add_child") }
    private fun draft()=ViewModelProvider(compose.activity)[TaskDetailsModel::class.java]
    private fun capture(name:String) {
        compose.waitForIdle()
        val file=File(compose.activity.getExternalFilesDir(null),"$name.png")
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!.let { bitmap -> file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
        shell("mkdir -p /sdcard/Download/tdmk-task18"); shell("cp ${file.absolutePath} /sdcard/Download/tdmk-task18/${file.name}")
    }
    @Before fun setup() {
        oldKeyboard=shell("settings get secure show_ime_with_hard_keyboard"); shell("settings put secure show_ime_with_hard_keyboard 1")
        parent=tasks.create(TaskContent("Checklist ${UUID.randomUUID()}")); ids.add(parent.id)
        waitTag("tab_Tasks"); click("tab_Tasks"); waitTag("details_${parent.id}")
    }
    @After fun clean() {
        provider.getDatabase().openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_checklist")
        series.forEach { provider.getRecurrenceRepository().delete(it) }
        ids.forEach { try { tasks.delete(it) } catch(_:NoSuchElementException) {} }
        shell("settings put secure show_ime_with_hard_keyboard $oldKeyboard")
    }
    @Test fun realAppAddEditCheckDeleteDiscardAndCompletionGate() {
        open(); add("  猫  item  "); add("猫  item"); assertTrue(subs.readForParent(parent.id).isEmpty())
        val first=draft().children[0].key; click("child_check_$first"); click("details_save"); closed()
        val saved=subs.readForParent(parent.id); assertEquals(2,saved.size); assertNotEquals(saved[0].id,saved[1].id)
        assertEquals(listOf("猫  item","猫  item"),saved.map { it.text }); assertTrue(saved[0].isCompleted); assertFalse(saved[1].isCompleted)
        click("complete_${parent.id}"); assertNull(tasks.read(parent.id).completedAt); assertEquals(saved,subs.readForParent(parent.id))
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Complete all subtasks first. Task remains unfinished.").fetchSemanticsNodes().isNotEmpty() }
        open(); edit("child_text_${saved[0].id}","Edited child"); click("child_delete_${saved[1].id}"); click("cancel_child_delete")
        assertEquals(2,draft().children.size); click("child_delete_${saved[1].id}"); click("confirm_child_delete")
        assertEquals(saved,subs.readForParent(parent.id)); compose.activityRule.scenario.recreate(); waitTag("new_child_text")
        assertEquals(1,draft().children.size); click("details_cancel"); closed(); assertEquals(saved,subs.readForParent(parent.id))
        open(); edit("child_text_${saved[0].id}","Edited child"); click("child_delete_${saved[1].id}"); click("confirm_child_delete"); click("details_save"); closed()
        assertEquals(saved[0].copy(text="Edited child"),subs.readForParent(parent.id).single())
        click("complete_${parent.id}"); compose.waitUntil(10_000) { tasks.read(parent.id).completedAt!=null }; val completed=tasks.read(parent.id)
        open(); edit("child_text_${saved[0].id}","Text only"); click("details_save"); closed(); assertEquals(completed.completedAt,tasks.read(parent.id).completedAt)
        open(); click("child_check_${saved[0].id}"); click("details_cancel"); closed(); assertEquals(completed.completedAt,tasks.read(parent.id).completedAt)
        open(); click("child_check_${saved[0].id}"); click("details_save"); closed(); assertNull(tasks.read(parent.id).completedAt)
        open(); click("child_check_${saved[0].id}"); click("details_save"); closed(); assertNull(tasks.read(parent.id).completedAt)
        click("complete_${parent.id}"); compose.waitUntil(10_000) { tasks.read(parent.id).completedAt!=null }
        open(); add("New unchecked"); click("details_save"); closed(); assertNull(tasks.read(parent.id).completedAt)
        val children=subs.readForParent(parent.id); assertTrue(children[0].isCompleted); assertFalse(children[1].isCompleted)
        open(); children.forEach { click("child_delete_${it.id}"); click("confirm_child_delete") }; click("details_save"); closed(); assertTrue(subs.readForParent(parent.id).isEmpty())
        click("complete_${parent.id}"); compose.waitUntil(10_000) { tasks.read(parent.id).completedAt!=null }
        val completedEmpty=tasks.read(parent.id)
        open(); add("Checked addition"); val addedKey=draft().children.single().key; click("child_check_$addedKey"); click("details_save"); closed()
        assertEquals(completedEmpty.completedAt,tasks.read(parent.id).completedAt)
        val checkedAddition=subs.readForParent(parent.id).single()
        open(); click("child_delete_${checkedAddition.id}"); click("confirm_child_delete"); click("details_save"); closed()
        assertEquals(completedEmpty.completedAt,tasks.read(parent.id).completedAt); assertTrue(subs.readForParent(parent.id).isEmpty())
    }
    @Test fun atomicFailureInvalidMissingCascadeAndOccurrenceIsolation() {
        val child=subs.create(parent.id,"Original"); val original=tasks.read(parent.id)
        open(); edit("child_text_${child.id}","\u00a0\u2003"); click("details_save"); waitTag("details_error"); assertEquals(child,subs.read(child.id))
        edit("child_text_${child.id}","Retained edit"); edit("details_title","Retained title"); add("Inserted")
        provider.getDatabase().openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_checklist BEFORE INSERT ON subtasks BEGIN SELECT RAISE(ABORT,'injected'); END")
        click("details_save"); waitTag("details_error"); assertEquals(original,tasks.read(parent.id)); assertEquals(child,subs.read(child.id)); assertEquals(2,draft().children.size)
        provider.getDatabase().openHelper.writableDatabase.execSQL("DROP TRIGGER fail_checklist"); click("details_save"); closed()
        assertEquals("Retained title",tasks.read(parent.id).title); assertEquals("Retained edit",subs.read(child.id).text)
        open(); subs.delete(child.id); click("details_save"); waitTag("details_error"); assertEquals(1,subs.readForParent(parent.id).size); click("details_cancel"); closed()
        open(); tasks.delete(parent.id); click("details_save"); waitTag("details_error"); assertTrue(provider.getDatabase().subtasks().forParent(parent.id).isEmpty()); click("details_cancel"); closed()
        val list=provider.getListRepository().create("Cascade checklist")
        val cascade=tasks.create(TaskContent("Cascade",list.id)); ids.add(cascade.id); subs.create(cascade.id,"Child")
        open(cascade.id); provider.getListRepository().delete(list.id); click("details_save"); waitTag("details_error"); assertTrue(provider.getDatabase().subtasks().forParent(cascade.id).isEmpty()); click("details_cancel"); closed()
        val template=provider.getRecurrenceRepository().create(RecurrenceSpec(LocalDate.now().minusDays(1),"daily","Series",checklist=listOf(Prototype(0,"Prototype"))))
        series.add(template.template.id); provider.getRecurrenceRepository().reconcile(template.template.id)
        val occurrences=provider.getDatabase().tasks().forSeries(template.template.id)
        val occurrence=occurrences.first(); val other=occurrences.last(); val existing=subs.readForParent(occurrence.id).single()
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[TaskDetailsModel::class.java].open(occurrence.id,provider) }; waitTag("new_child_text")
        edit("child_text_${existing.id}","Occurrence only"); click("details_save"); closed()
        val after=tasks.read(occurrence.id); assertTrue(after.isOverride); assertEquals(occurrence.slotDate,after.slotDate); assertEquals(occurrence.createdAt,after.createdAt)
        assertEquals(other,tasks.read(other.id)); assertEquals("Prototype",subs.readForParent(other.id).single().text)
        val reopened=DatabaseProvider(compose.activity,"tdmk.db"); try { assertEquals(subs.readForParent(occurrence.id),reopened.getSubtaskRepository().readForParent(occurrence.id)) } finally { reopened.close() }
    }
    @Test fun checklistImeBackOutsideSwipeDiscard() {
        val child=subs.create(parent.id,"Saved child")
        open(); edit("child_text_${child.id}","Back draft"); click("child_text_${child.id}")
        compose.waitUntil(10_000) { shell("dumpsys input_method").contains("mInputShown=true") }
        shell("input keyevent 4"); compose.waitUntil(10_000) { !shell("dumpsys input_method").contains("mInputShown=true") }
        assertEquals("Back draft",draft().children.single().text); compose.onNodeWithTag("task_details").assertExists()
        shell("input keyevent 4"); closed(); assertEquals(child,subs.read(child.id))
        open(); edit("child_text_${child.id}","Outside draft"); click("child_text_${child.id}")
        compose.waitUntil(10_000) { shell("dumpsys input_method").contains("mInputShown=true") }; shell("input keyevent 4")
        compose.waitUntil(10_000) { !shell("dumpsys input_method").contains("mInputShown=true") }; android.os.SystemClock.sleep(700)
        val sheetBounds=compose.onNodeWithTag("details_handle",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        android.util.Log.i("TDMKGeometry", "Outside touch above sheet: $sheetBounds")
        compose.onNodeWithTag("details_handle",useUnmergedTree=true).performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX,-8*compose.activity.resources.displayMetrics.density)) }; closed(); assertEquals(child,subs.read(child.id))
        open(); edit("child_text_${child.id}","Swipe draft"); click("child_text_${child.id}")
        compose.waitUntil(10_000) { shell("dumpsys input_method").contains("mInputShown=true") }; shell("input keyevent 4")
        compose.waitUntil(10_000) { !shell("dumpsys input_method").contains("mInputShown=true") }
        compose.onNodeWithTag("details_handle",useUnmergedTree=true).performTouchInput { swipeDown(startY=height/2f,endY=height+500*compose.activity.resources.displayMetrics.density,durationMillis=500) }; closed(); assertEquals(child,subs.read(child.id))
    }
    @Test fun keyboardChecklistThemeFontOrientationMatrix() {
        val oldScale=shell("settings get system font_scale"); val oldOrientation=compose.activity.requestedOrientation
        val oldAppearance=provider.getDatabase().settings().read().appearance
        repeat(12) { subs.create(parent.id,if(it<2) "Duplicate item" else "Long checklist label ${it+1} with Unicode 猫 and several words") }
        tasks.update(parent.id,parent.content().copy(shortNote=(1..18).joinToString("\n") { "Long note line $it" }))
        open(); val child=subs.readForParent(parent.id).last()
        try {
            for(scale in listOf("1.0","2.0")) { shell("settings put system font_scale $scale")
                for((orientation,label) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "portrait",ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "landscape")) {
                    compose.activityRule.scenario.onActivity { it.requestedOrientation=orientation }
                    compose.waitUntil(10_000) { compose.activity.resources.configuration.fontScale==scale.toFloat() && compose.activity.resources.configuration.orientation==if(label=="portrait") Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE }
                    waitTag("new_child_text")
                    for(mode in listOf(Appearance.LIGHT,Appearance.DARK)) {
                        provider.getDatabase().settings().setAppearance(mode); compose.waitUntil(10_000) { compose.onAllNodesWithTag(if(mode==Appearance.DARK) "theme_dark" else "theme_light").fetchSemanticsNodes().isNotEmpty() }
                        click("child_text_${child.id}"); compose.waitUntil(10_000) { shell("dumpsys input_method").contains("mInputShown=true") }
                        for(tag in listOf("details_note","child_text_${child.id}","child_check_${child.id}","child_delete_${child.id}","new_child_text","add_child","details_save","details_cancel")) compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                        compose.onNodeWithTag("child_check_${child.id}").assertIsOff()
                        if(scale=="1.0" && label=="portrait" && mode==Appearance.LIGHT) {
                            compose.onNodeWithTag("child_delete_${child.id}").performScrollTo(); capture("checklist-controls")
                        }
                        click("child_delete_${child.id}"); waitTag("confirm_child_delete"); compose.onNodeWithTag("confirm_child_delete").assertIsDisplayed()
                        if(scale=="1.0" && label=="portrait" && mode==Appearance.LIGHT) capture("checklist-confirmation")
                        click("cancel_child_delete")
                        click("new_child_text"); compose.onNodeWithTag("details_save").performScrollTo().assertIsDisplayed(); compose.onNodeWithTag("details_cancel").assertIsDisplayed(); android.os.SystemClock.sleep(700)
                        capture("checklist-${mode.storedValue}-$label-font$scale")
                    }
                }
            }
            shell("input keyevent 4"); compose.waitUntil(10_000) { !shell("dumpsys input_method").contains("mInputShown=true") }
            click("details_cancel"); closed(); assertEquals(12,subs.readForParent(parent.id).size)
        } finally {
            // Release the focused modal window before restoring font/orientation on the host.
            compose.activityRule.scenario.onActivity { ViewModelProvider(it)[TaskDetailsModel::class.java].dismiss() }
            shell("settings put system font_scale $oldScale"); compose.activityRule.scenario.onActivity { it.requestedOrientation=oldOrientation }
            provider.getDatabase().settings().setAppearance(oldAppearance)
        }
    }
}
