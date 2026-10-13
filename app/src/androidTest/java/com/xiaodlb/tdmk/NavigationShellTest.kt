package com.xiaodlb.tdmk

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import java.io.File

class NavigationShellTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tabs = listOf("Home", "Tasks", "Notes", "Log")
    @Before fun awaitAppearanceLoad() { compose.waitUntil(10_000) { compose.onAllNodesWithTag("screen_Home").fetchSemanticsNodes().isNotEmpty() } }
    private fun awaitShell() { compose.waitUntil(10_000) { compose.onAllNodesWithTag("tab_Home").fetchSemanticsNodes().isNotEmpty() } }
    private fun show(route: String) {
        awaitShell()
        compose.onNodeWithTag("screen_$route").assertIsDisplayed()
        (tabs + "Settings").filter { it != route }.forEach { compose.onNodeWithTag("screen_$it").assertDoesNotExist() }
        val selected = if (route == "Settings") "Home" else route
        tabs.forEach { name ->
            val node = compose.onNodeWithTag("tab_$name")
            node.assertIsDisplayed().assertHasClickAction().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            if (name == selected) node.assertIsSelected() else node.assertIsNotSelected()
        }
    }
    private fun back() { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); compose.waitForIdle() }
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }.trim()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(compose.activity.getExternalFilesDir(null), "navigation-checks").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        shell("mkdir -p /sdcard/Download/tdmk-navigation-checks")
        shell("cp ${File(directory, "$name.png").absolutePath} /sdcard/Download/tdmk-navigation-checks/$name.png")
        bitmap.recycle()
    }

    @Test fun coldLaunchTabOrderExclusiveSelectionAndEveryAndroidBackCase() {
        show("Home")
        val bounds = tabs.map { compose.onNodeWithTag("tab_$it").fetchSemanticsNode().boundsInRoot }
        assertTrue(bounds.zipWithNext().all { (first, second) -> first.left < second.left })
        compose.onAllNodes(isSelectable()).assertCountEquals(4)
        for (name in tabs.drop(1)) {
            repeat(3) { compose.onNodeWithTag("tab_$name").performClick() }
            show(name); back(); show("Home")
        }
        for (name in listOf("Tasks","Notes","Log","Tasks","Home","Log")) compose.onNodeWithTag("tab_$name").performClick()
        show("Log"); back(); show("Home") // Tab switch history adds no Back steps.
        repeat(3) {
            compose.onNodeWithTag("open_settings").assertIsDisplayed().performClick(); show("Settings")
            back(); show("Home")
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { compose.activityRule.scenario.state != Lifecycle.State.RESUMED }
        assertNotEquals(Lifecycle.State.RESUMED, compose.activityRule.scenario.state) // Android root exit/background.
    }

    @Test fun selectedTabsAndSettingsSurviveRecreationWithoutExtraBackSteps() {
        for (name in listOf("Tasks","Notes","Log","Home","Notes")) {
            compose.onNodeWithTag("tab_$name").performClick(); compose.activityRule.scenario.recreate(); show(name)
            if (name != "Home") { back(); show("Home") }
        }
        compose.onNodeWithTag("open_settings").performClick(); show("Settings")
        compose.activityRule.scenario.recreate(); show("Settings")
        back(); show("Home")
        compose.onNodeWithTag("open_settings").performClick()
        compose.onNodeWithContentDescription("Back to Home").assertIsDisplayed().performClick(); show("Home")
    }

    @Test fun portraitLandscapeNormalAndDoubleTextHaveOperableLabelsAndSafeInsets() {
        val oldScale = shell("settings get system font_scale")
        val originalOrientation = compose.activity.requestedOrientation
        try {
            for (scale in listOf("1.0", "2.0")) {
                shell("settings put system font_scale $scale")
                for ((orientation, label) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "portrait", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "landscape")) {
                    compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
                    val configuration = if (label == "portrait") Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                    compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == configuration && compose.activity.resources.configuration.fontScale == scale.toFloat() }
                    awaitShell()
                    compose.onNodeWithTag("tab_Home").performClick(); show("Home")
                    screenshot("home-$label-font$scale")
                    val density = compose.activity.resources.displayMetrics.density
                    val decor = compose.activity.window.decorView
                    assertEquals(0, requireNotNull(decor.windowInsetsController).systemBarsAppearance and android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                    val insets = requireNotNull(decor.rootWindowInsets).getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                    tabs.forEach { name ->
                        val node = compose.onNodeWithTag("tab_$name")
                        val bounds = node.fetchSemanticsNode().boundsInWindow
                        assertTrue(bounds.width >= 48 * density); assertTrue(bounds.height >= 48 * density)
                        assertTrue("$label/$scale $name left=$bounds inset=$insets decor=${decor.width}x${decor.height}",bounds.left >= insets.left)
                        assertTrue("$label/$scale $name right=$bounds inset=$insets decor=${decor.width}x${decor.height}",bounds.right <= decor.width - insets.right)
                        assertTrue(bounds.bottom <= decor.height - insets.bottom)
                        // Exactly one text label, with no redundant icon description in the merged item.
                        assertEquals(listOf(name), node.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
                        assertFalse(node.fetchSemanticsNode().config.contains(SemanticsProperties.ContentDescription))
                        node.performClick(); show(name)
                    }
                    compose.onNodeWithTag("tab_Home").performClick()
                    val entry = compose.onNodeWithTag("open_settings").assertIsDisplayed().assertHasClickAction()
                    assertTrue(entry.fetchSemanticsNode().boundsInWindow.top >= insets.top)
                    screenshot("home-$label-font$scale")
                    entry.performClick(); show("Settings"); screenshot("settings-$label-font$scale")
                    if (scale == "2.0" && label == "landscape") {
                        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
                        show("Settings") // Real rotation preserves the open child route too.
                    }
                    back(); show("Home")
                    if (scale == "2.0" && label == "landscape") {
                        compose.onNodeWithTag("tab_Notes").performClick()
                        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                        show("Notes"); back(); show("Home")
                    }
                }
            }
        } finally {
            shell("settings put system font_scale ${if (oldScale == "null") "1.0" else oldScale}")
            compose.activityRule.scenario.onActivity { it.requestedOrientation = originalOrientation }
        }
    }
}
