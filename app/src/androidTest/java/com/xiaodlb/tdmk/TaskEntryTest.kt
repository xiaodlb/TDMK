package com.xiaodlb.tdmk

import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaodlb.tdmk.data.*
import org.junit.*
import org.junit.Assert.*
import java.time.*
import java.util.UUID

class TaskEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var provider: DatabaseProvider
    private val filename = "task-entry-${UUID.randomUUID()}.db"
    private var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
    private var dark by mutableStateOf(false)
    private var context by mutableStateOf<TaskContext>(TaskContext.Anytime)
    private var requests = mutableListOf<String>()
    @Before fun prepare() {
        provider = DatabaseProvider(compose.activity, filename)
        provider.getListRepository().rename(provider.getListRepository().initialize().id, "General")
        mount()
        waitTag("task_title")
    }
    private fun mount() {
        compose.activityRule.scenario.onActivity { activity -> activity.setContent {
            AppTheme(dark) {
                val controller = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                SideEffect { keyboard = controller }
                Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())) {
                    TaskWorkspace(provider, context, onDetails = { requests.add(it) })
                } }
            }
        } }
        waitTag("task_title")
    }
    @After fun cleanup() { provider.close(); compose.activity.deleteDatabase(filename) }
    private fun waitTag(tag: String) { compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun save(title: String) {
        compose.onNodeWithTag("task_title").performScrollTo().performTextReplacement(title)
        compose.onNodeWithTag("save_task").performScrollTo().performClick()
    }
    private fun tasks() = provider.getTaskRepository().viewCandidates()
    @Test fun defaultCustomMatchingFlowsAndCompletionActionsPersist() {
        val lists = provider.getListRepository()
        val custom = lists.create("Work")
        context = TaskContext.InList(custom.id)
        save("Custom title")
        waitTag("entry_success")
        val saved = kotlinx.coroutines.runBlocking { tasks().first() }.single()
        assertEquals(custom.id, saved.listId); assertEquals("medium", saved.priority)
        assertNull(saved.scheduledDate); assertNull(saved.reminderTime); assertNull(saved.seriesId)
        compose.onNodeWithTag("task_title").assert(SemanticsMatcher("Empty editable draft") { it.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty() })
        compose.onNodeWithTag("details_${saved.id}").performScrollTo().performClick()
        assertEquals(listOf(saved.id), requests); assertNull(provider.getTaskRepository().read(saved.id).completedAt)
        compose.onNodeWithTag("complete_${saved.id}").performScrollTo().performClick()
        compose.waitUntil(10_000) { provider.getTaskRepository().read(saved.id).completedAt != null }
        assertEquals(listOf(saved.id), requests)
        compose.onNodeWithTag("complete_${saved.id}").assertIsOn().performClick()
        compose.waitUntil(10_000) { provider.getTaskRepository().read(saved.id).completedAt == null }
        val child = provider.getSubtaskRepository().create(saved.id, "Unchecked")
        compose.onNodeWithTag("complete_${saved.id}").performClick(); waitTag("completion_error_${saved.id}")
        assertNull(provider.getTaskRepository().read(saved.id).completedAt)
        assertFalse(provider.getSubtaskRepository().read(child.id).isCompleted)
        compose.onNodeWithTag("complete_${saved.id}").assertIsOff()
        context = TaskContext.Today
        save("Default title"); waitTag("entry_success")
        val all = kotlinx.coroutines.runBlocking { tasks().first() }
        assertEquals(2, all.size)
        val default = all.single { it.title == "Default title" }
        assertEquals(lists.initialize().id, default.listId); assertNull(default.scheduledDate)
        compose.onNodeWithTag("task_${default.id}").assertDoesNotExist()
        context = TaskContext.Anytime; waitTag("task_${default.id}")
        val reopened = DatabaseProvider(compose.activity, filename)
        try { assertEquals(default, reopened.getTaskRepository().read(default.id)) } finally { reopened.close() }
    }
    @Test fun repeatedSubmissionWhileDatabaseBusyCreatesOneRow() {
        val locked = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val holding = executor.submit { provider.getDatabase().runInTransaction { locked.countDown(); check(release.await(15, java.util.concurrent.TimeUnit.SECONDS)) } }
        try {
            assertTrue(locked.await(5, java.util.concurrent.TimeUnit.SECONDS))
            save("One row")
            compose.onNodeWithTag("save_task").assertIsNotEnabled().performClick()
            compose.onNodeWithTag("task_title").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnImeAction) { it() }
        } finally { release.countDown(); holding.get(10, java.util.concurrent.TimeUnit.SECONDS); executor.shutdown() }
        waitTag("entry_success")
        assertEquals(1, kotlinx.coroutines.runBlocking { tasks().first() }.size)
    }
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }.trim() }
    private fun showKeyboardForCapture() {
        compose.runOnIdle { keyboard?.show() }
        android.os.SystemClock.sleep(700)
        compose.waitUntil(10_000) { compose.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true }
    }
    @Test fun keyboardRenderingAcrossAppearanceFontAndOrientation() {
        val longTask = provider.getTaskRepository().create(TaskContent("Long task title " + "reachable words ".repeat(8), priority = "high"))
        val oldKeyboard = shell("settings get secure show_ime_with_hard_keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        val oldScale = shell("settings get system font_scale")
        val oldOrientation = compose.activity.requestedOrientation
        try {
            for (scale in listOf("1.0", "2.0")) {
                shell("settings put system font_scale $scale")
                for ((orientation, label) in listOf(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "portrait", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "landscape")) {
                    compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
                    compose.waitUntil(10_000) { compose.activity.resources.configuration.fontScale == scale.toFloat() && compose.activity.resources.configuration.orientation == if(label == "portrait") 1 else 2 }
                    mount()
                    for (night in listOf(false, true)) {
                        dark = night
                        save(" ")
                        waitTag("entry_error")
                        compose.onNodeWithTag("task_title").performScrollTo().performClick()
                        compose.runOnIdle { keyboard?.show() }
                        compose.waitUntil(10_000) { compose.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true }
                        android.os.SystemClock.sleep(500)
                        compose.waitForIdle()
                        compose.onNodeWithTag("save_task").performScrollTo().assertIsDisplayed()
                        compose.onNodeWithTag("cancel_task").assertIsDisplayed()
                        compose.onNodeWithTag("entry_error").performScrollTo().assertIsDisplayed()
                        val folder = java.io.File(compose.activity.getExternalFilesDir(null), "task16").apply { mkdirs() }
                        val name = "entry-${if(night) "dark" else "light"}-$label-font$scale.png"
                        showKeyboardForCapture()
                        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                        java.io.File(folder, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
                        shell("mkdir -p /sdcard/Download/tdmk-task16")
                        shell("cp ${folder.absolutePath}/$name /sdcard/Download/tdmk-task16/$name")
                        compose.onNodeWithTag("save_task").performScrollTo().assertIsDisplayed()
                        compose.onNodeWithTag("cancel_task").assertIsDisplayed()
                        android.os.SystemClock.sleep(300)
                        showKeyboardForCapture()
                        val controlBitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                        java.io.File(folder, "controls-$name").outputStream().use { controlBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; controlBitmap.recycle()
                        shell("cp ${folder.absolutePath}/controls-$name /sdcard/Download/tdmk-task16/controls-$name")
                        compose.onNodeWithTag("details_${longTask.id}").performScrollTo().assertIsDisplayed()
                        compose.onNodeWithTag("complete_${longTask.id}").performScrollTo().assertIsDisplayed()
                        showKeyboardForCapture()
                        val rowBitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                        java.io.File(folder, "row-$name").outputStream().use { rowBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; rowBitmap.recycle()
                        shell("cp ${folder.absolutePath}/row-$name /sdcard/Download/tdmk-task16/row-$name")
                        compose.onNodeWithTag("cancel_task").performScrollTo().performClick()
                    }
                }
            }
        } finally { shell("settings put secure show_ime_with_hard_keyboard $oldKeyboard"); shell("settings put system font_scale $oldScale"); compose.activityRule.scenario.onActivity { it.requestedOrientation = oldOrientation } }
    }
    @Test fun invalidCancelAndActualWriteFailureKeepDraftAndRetry() {
        save("\u00a0\u2003"); waitTag("entry_error")
        assertTrue(kotlinx.coroutines.runBlocking { tasks().first() }.isEmpty())
        compose.onNodeWithTag("task_title").performTextReplacement("Cancelled")
        compose.onNodeWithTag("cancel_task").performScrollTo().performClick()
        assertTrue(kotlinx.coroutines.runBlocking { tasks().first() }.isEmpty())
        provider.getDatabase().openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_entry BEFORE INSERT ON tasks BEGIN SELECT RAISE(ABORT,'injected save failure'); END")
        try {
            save("Retained draft"); waitTag("entry_error")
            compose.onNodeWithTag("task_title").assertTextContains("Retained draft")
            compose.onNodeWithTag("entry_success").assertDoesNotExist()
            assertTrue(kotlinx.coroutines.runBlocking { tasks().first() }.isEmpty())
        } finally { provider.getDatabase().openHelper.writableDatabase.execSQL("DROP TRIGGER fail_entry") }
        compose.onNodeWithTag("save_task").performScrollTo().performClick(); waitTag("entry_success")
        assertEquals(1, kotlinx.coroutines.runBlocking { tasks().first() }.size)
    }
}
