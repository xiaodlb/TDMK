package com.xiaodlb.tdmk

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaodlb.tdmk.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.UUID
import java.io.File

class TaskDetailsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val provider get()=(compose.activity.application as TdmkApplication).databaseProvider
    private val repository get()=provider.getTaskRepository()
    private val ownedTasks=mutableListOf<String>()
    private val ownedSeries=mutableListOf<String>()
    private val ownedLists=mutableListOf<String>()
    private lateinit var seeded: Task
    private var oldAppearance=Appearance.SYSTEM
    private var oldKeyboard="0"
    private fun shell(command:String)=instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }.trim()
    }
    private fun waitTag(tag:String) { compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun closed() { compose.waitUntil(10_000) { compose.onAllNodesWithTag("task_details").fetchSemanticsNodes().isEmpty() && compose.activity.hasWindowFocus() }; compose.onNodeWithTag("screen_Tasks").assertExists() }
    private fun edit(tag:String,value:String) { compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value); text(tag,value) }
    private fun click(tag:String) { val node=compose.onNodeWithTag(tag); if(!tag.startsWith("tab_") && !tag.startsWith("choose_list_")) node.performScrollTo(); if(tag=="details_save") { compose.waitForIdle(); android.os.SystemClock.sleep(700) }; node.performClick() }
    private fun open() { waitTag("details_${seeded.id}"); click("details_${seeded.id}"); waitTag("details_title") }
    private fun text(tag:String,value:String) { compose.onNodeWithTag(tag).assert(SemanticsMatcher("Editable value $value") { it.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text==value }) }
    @Before fun prepare() {
        oldAppearance=provider.getDatabase().settings().read().appearance
        oldKeyboard=shell("settings get secure show_ime_with_hard_keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        seeded=repository.create(TaskContent("Details ${UUID.randomUUID()}",shortNote="Saved note")); ownedTasks.add(seeded.id)
        waitTag("tab_Tasks"); click("tab_Tasks"); waitTag("details_${seeded.id}")
    }
    @After fun clean() {
        provider.getDatabase().openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_details")
        ownedSeries.forEach { provider.getRecurrenceRepository().delete(it) }
        ownedTasks.forEach { id -> try { repository.delete(id) } catch(_:NoSuchElementException) {} }
        ownedLists.forEach { id -> try { provider.getListRepository().delete(id) } catch(_:NoSuchElementException) {} }
        provider.getDatabase().settings().setAppearance(oldAppearance)
        shell("settings put secure show_ime_with_hard_keyboard $oldKeyboard")
    }
    @Test fun realAppSaveReopenCancelBackAndConfigurationRetainDraft() {
        val duplicate1=provider.getListRepository().create("Same name").also { ownedLists.add(it.id) }
        val duplicate2=provider.getListRepository().create("Same name").also { ownedLists.add(it.id) }
        open(); text("details_title",seeded.title)
        edit("details_title","Edited title"); click("details_list"); click("choose_list_${duplicate2.id}")
        click("details_priority_high"); edit("details_note","New plain note")
        click("details_save"); closed()
        val saved=repository.read(seeded.id)
        assertEquals("Edited title",saved.title); assertEquals(duplicate2.id,saved.listId); assertNotEquals(duplicate1.id,saved.listId)
        assertEquals("high",saved.priority); assertEquals("New plain note",saved.shortNote)
        assertEquals(seeded.id,saved.id); assertEquals(seeded.createdAt,saved.createdAt)
        compose.waitUntil(10_000) { compose.onNodeWithTag("details_${seeded.id}").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Focused] }
        open(); text("details_title","Edited title"); click("details_save"); closed(); assertEquals(saved,repository.read(seeded.id))
        open(); edit("details_title","Unsaved change"); compose.activityRule.scenario.recreate(); waitTag("details_title"); text("details_title","Unsaved change")
        click("details_cancel"); closed(); assertEquals(saved,repository.read(seeded.id))
        open(); edit("details_title","Back draft"); click("details_title")
        compose.waitUntil(10_000) { shell("dumpsys input_method").contains("mInputShown=true") }
        shell("input keyevent 4")
        compose.waitUntil(10_000) { !shell("dumpsys input_method").contains("mInputShown=true") }
        compose.onNodeWithTag("task_details").assertExists(); text("details_title","Back draft")
        shell("input keyevent 4"); closed(); assertEquals(saved,repository.read(seeded.id))
        open(); edit("details_title","Outside draft"); click("details_title")
        compose.waitUntil(10_000) { shell("dumpsys input_method").contains("mInputShown=true") }; shell("input keyevent 4")
        compose.waitUntil(10_000) { !shell("dumpsys input_method").contains("mInputShown=true") }
        android.os.SystemClock.sleep(700)
        val density=compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("details_handle",useUnmergedTree=true).performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX,-8*density)) }; closed(); assertEquals(saved,repository.read(seeded.id))
        open(); edit("details_title","Swipe draft"); click("details_title")
        compose.waitUntil(10_000) { shell("dumpsys input_method").contains("mInputShown=true") }; shell("input keyevent 4")
        compose.waitUntil(10_000) { !shell("dumpsys input_method").contains("mInputShown=true") }
        compose.onNodeWithTag("details_handle",useUnmergedTree=true).performTouchInput { swipeDown(startY=height/2f,endY=height+500*density,durationMillis=500) }
        closed(); assertEquals(saved,repository.read(seeded.id))
        compose.activityRule.scenario.recreate(); waitTag("details_${seeded.id}"); open(); text("details_note","New plain note"); click("details_cancel"); closed()
        val reopened=DatabaseProvider(compose.activity,"tdmk.db")
        try { assertEquals(saved,reopened.getTaskRepository().read(seeded.id)) } finally { reopened.close() }
    }
    @Test fun validationWriteFailurePendingAndDeletedIdentityAreSafe() {
        open(); edit("details_title","\u00a0\u2003"); click("details_save"); waitTag("details_error"); assertEquals(seeded,repository.read(seeded.id))
        edit("details_title","Retained draft"); edit("details_date","2026-02-30"); click("details_save"); waitTag("details_error"); assertEquals(seeded,repository.read(seeded.id))
        edit("details_date","")
        provider.getDatabase().openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_details BEFORE UPDATE ON tasks BEGIN SELECT RAISE(ABORT,'injected details failure'); END")
        click("details_save"); waitTag("details_error"); text("details_title","Retained draft"); assertEquals(seeded,repository.read(seeded.id))
        provider.getDatabase().openHelper.writableDatabase.execSQL("DROP TRIGGER fail_details")
        val entered=java.util.concurrent.CountDownLatch(1); val release=java.util.concurrent.CountDownLatch(1)
        val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
        val holding=executor.submit { provider.getDatabase().runInTransaction { entered.countDown(); check(release.await(15,java.util.concurrent.TimeUnit.SECONDS)) } }
        try {
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)); click("details_save")
            compose.onNodeWithTag("details_save").assertIsNotEnabled().performClick()
            compose.onNodeWithTag("details_cancel").assertIsNotEnabled().performClick()
            shell("input keyevent 4"); compose.onNodeWithTag("task_details").assertExists()
        } finally { release.countDown(); holding.get(10,java.util.concurrent.TimeUnit.SECONDS); executor.shutdown() }
        closed(); assertEquals("Retained draft",repository.read(seeded.id).title)
        assertEquals(1,runBlocking { repository.viewCandidates().first() }.count { it.id==seeded.id })
        open()
        val vanishedList=provider.getListRepository().create("Deleted selection").also { ownedLists.add(it.id) }
        click("details_cancel"); closed(); open(); click("details_list"); click("choose_list_${vanishedList.id}")
        provider.getListRepository().delete(vanishedList.id); edit("details_title","Must not switch lists"); click("details_save"); waitTag("details_error")
        assertEquals("Retained draft",repository.read(seeded.id).title); click("details_cancel"); closed()
        open(); repository.delete(seeded.id); edit("details_title","Cannot replace"); click("details_save"); waitTag("details_error")
        assertFalse(runBlocking { repository.viewCandidates().first() }.any { it.id==seeded.id }); click("details_cancel"); closed()
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[TaskDetailsModel::class.java].open("missing-id",provider) }
        waitTag("details_retry"); click("details_retry"); waitTag("details_retry"); click("details_cancel"); closed()
    }
    @Test fun realAppKeyboardLongNoteThemeFontAndOrientation() {
        val oldScale=shell("settings get system font_scale")
        val oldOrientation=compose.activity.requestedOrientation
        val longNote=(1..18).joinToString("\n") { "Long plain note line $it remains editable and reachable." }
        open(); edit("details_note",longNote)
        try {
            for(scale in listOf("1.0","2.0")) {
                shell("settings put system font_scale $scale")
                for((orientation,label) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "portrait",ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "landscape")) {
                    compose.activityRule.scenario.onActivity { it.requestedOrientation=orientation }
                    compose.waitUntil(10_000) { compose.activity.resources.configuration.fontScale==scale.toFloat() && compose.activity.resources.configuration.orientation==if(label=="portrait") Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE }
                    waitTag("details_note"); text("details_note",longNote)
                    for(mode in listOf(Appearance.LIGHT,Appearance.DARK)) {
                        provider.getDatabase().settings().setAppearance(mode)
                        compose.waitUntil(10_000) { compose.onAllNodesWithTag(if(mode==Appearance.DARK) "theme_dark" else "theme_light").fetchSemanticsNodes().isNotEmpty() }
                        click("details_note")
                        compose.waitUntil(10_000) { shell("dumpsys input_method").contains("mInputShown=true") }
                        for(tag in listOf("details_title","details_list","details_date","details_priority_high","details_priority_medium","details_priority_low","details_note","details_save","details_cancel")) compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                        compose.onNodeWithTag("details_save").performScrollTo().assertIsDisplayed(); compose.onNodeWithTag("details_cancel").assertIsDisplayed()
                        android.os.SystemClock.sleep(700)
                        assertTrue(shell("dumpsys input_method").contains("mInputShown=true"))
                        capture("sheet-${mode.storedValue}-$label-font$scale")
                    }
                }
            }
        } finally { shell("settings put system font_scale $oldScale"); compose.activityRule.scenario.onActivity { it.requestedOrientation=oldOrientation } }
        click("details_cancel"); closed(); assertEquals(seeded,repository.read(seeded.id))
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder=File(compose.activity.getExternalFilesDir(null),"task17").apply { mkdirs() }
        File(folder,"$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }; bitmap.recycle()
        shell("mkdir -p /sdcard/Download/tdmk-task17")
        shell("cp ${folder.absolutePath}/$name.png /sdcard/Download/tdmk-task17/$name.png")
    }
    @Test fun dateReminderCompletionAndOccurrenceMetadataSurvive() {
        val date=LocalDate.now()
        val original=repository.update(seeded.id,seeded.content().copy(scheduledDate=date,reminderTime=LocalTime.of(18,0)))
        val child=provider.getSubtaskRepository().create(seeded.id,"Saved child"); provider.getSubtaskRepository().setChecked(child.id,true)
        val completed=repository.complete(seeded.id)
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[TaskDetailsModel::class.java].open(seeded.id,provider) }
        waitTag("details_title"); edit("details_date",date.plusDays(1).toString()); click("details_save"); closed()
        val moved=repository.read(seeded.id)
        assertEquals(original.reminderTime,moved.reminderTime); assertEquals(completed.completedAt,moved.completedAt); assertEquals(completed.completionDate,moved.completionDate)
        assertEquals(completed.createdAt,moved.createdAt); assertTrue(provider.getSubtaskRepository().read(child.id).isCompleted)
        assertFalse(TaskContext.Anytime.results(listOf(moved),Clock.systemDefaultZone()).any { it.id==moved.id })
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[TaskDetailsModel::class.java].open(seeded.id,provider) }
        waitTag("clear_date"); click("clear_date"); click("details_save"); closed()
        val undated=repository.read(seeded.id); assertNull(undated.reminderTime); assertNull(undated.scheduledDate)
        waitTag("details_${seeded.id}"); assertTrue(TaskContext.Anytime.results(listOf(undated),Clock.systemDefaultZone()).isNotEmpty())
        open(); edit("details_date",date.toString()); click("details_save"); closed()
        val redated=repository.read(seeded.id); assertNull(redated.reminderTime); assertEquals(date,redated.scheduledDate)
        assertTrue(TaskContext.Today.results(listOf(redated),Clock.systemDefaultZone()).isNotEmpty())
        val recurring=provider.getRecurrenceRepository().create(RecurrenceSpec(date.minusDays(1),"daily","Occurrence",checklist=listOf(Prototype(0,"Child"))))
        ownedSeries.add(recurring.template.id)
        provider.getRecurrenceRepository().reconcile(recurring.template.id)
        val occurrences=runBlocking { repository.viewCandidates().first() }.filter { it.seriesId==recurring.template.id }
        val occurrence=occurrences.first()
        val other=occurrences.last()
        val children=provider.getSubtaskRepository().readForParent(occurrence.id)
        compose.activityRule.scenario.onActivity { ViewModelProvider(it)[TaskDetailsModel::class.java].open(occurrence.id,provider) }
        waitTag("details_title"); edit("details_title","Only this occurrence"); click("details_save"); closed()
        val override=repository.read(occurrence.id)
        assertTrue(override.isOverride); assertEquals(occurrence.seriesId,override.seriesId); assertEquals(occurrence.slotDate,override.slotDate); assertEquals(occurrence.sourceRevision,override.sourceRevision)
        assertEquals(occurrence.createdAt,override.createdAt); assertEquals(children,provider.getSubtaskRepository().readForParent(occurrence.id))
        assertEquals(other,repository.read(other.id))
    }
}
