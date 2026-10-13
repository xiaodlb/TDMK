package com.xiaodlb.tdmk

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaodlb.tdmk.data.Appearance
import org.junit.*
import org.junit.Assert.*
import java.io.File

class AppearanceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val provider get()=(compose.activity.application as TdmkApplication).databaseProvider
    private var oldMode=Appearance.SYSTEM
    private var oldNight="no"
    private fun shell(command:String)=instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }.trim()
    }
    @Before fun prepare() {
        oldMode=provider.getDatabase().settings().read().appearance
        oldNight=if(shell("cmd uimode night").contains("yes")) "yes" else "no"
        provider.getDatabase().settings().setAppearance(Appearance.SYSTEM)
        shell("cmd uimode night no")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("screen_Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_settings").performClick()
        selected(Appearance.SYSTEM); theme(false)
    }
    @After fun restore() { provider.getDatabase().settings().setAppearance(oldMode); shell("cmd uimode night $oldNight") }
    private fun selected(mode:Appearance) {
        compose.waitUntil(10_000) {
            val config=compose.onAllNodesWithTag("appearance_${mode.storedValue}").fetchSemanticsNodes().singleOrNull()?.config
            config != null && config.contains(SemanticsProperties.Selected) && config[SemanticsProperties.Selected] && !config.contains(SemanticsProperties.Disabled)
        }
        Appearance.entries.forEach { item ->
            val node=compose.onNodeWithTag("appearance_${item.storedValue}")
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.RadioButton))
            if(item==mode) node.assertIsSelected() else node.assertIsNotSelected()
        }
    }
    private fun choose(mode:Appearance) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("appearance_${mode.storedValue}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("appearance_${mode.storedValue}").performScrollTo().performClick(); selected(mode)
    }
    private fun theme(dark:Boolean) { compose.waitUntil(10_000) { compose.onAllNodesWithTag(if(dark) "theme_dark" else "theme_light").fetchSemanticsNodes().isNotEmpty() } }
    private fun capture(name:String) {
        compose.waitForIdle(); val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder=File(compose.activity.getExternalFilesDir(null),"appearance-checks").apply { mkdirs() }
        val file=File(folder,"$name.png"); file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }; bitmap.recycle()
        shell("mkdir -p /sdcard/Download/tdmk-appearance-checks"); shell("cp ${file.absolutePath} /sdcard/Download/tdmk-appearance-checks/${file.name}")
    }

    @Test fun overridesSystemObservationRecreationAndSafeFailedSave() {
        choose(Appearance.DARK); theme(true); assertEquals(Appearance.DARK,provider.getDatabase().settings().read().appearance)
        for(name in listOf("Home","Tasks","Notes","Log")) { compose.onNodeWithTag("tab_$name").performClick(); theme(true); compose.onNodeWithTag("screen_$name").assertIsDisplayed() }
        compose.onNodeWithTag("tab_Home").performClick(); compose.onNodeWithTag("open_settings").performClick()
        compose.activityRule.scenario.recreate(); selected(Appearance.DARK); theme(true)
        shell("cmd uimode night yes"); selected(Appearance.DARK); theme(true)
        choose(Appearance.LIGHT); theme(false); compose.activityRule.scenario.recreate(); selected(Appearance.LIGHT); theme(false)
        shell("cmd uimode night no"); selected(Appearance.LIGHT); theme(false)
        shell("cmd uimode night yes"); selected(Appearance.LIGHT); theme(false)
        choose(Appearance.SYSTEM); theme(true); assertEquals(Appearance.SYSTEM,provider.getDatabase().settings().read().appearance)
        shell("cmd uimode night no"); selected(Appearance.SYSTEM); theme(false)
        shell("cmd uimode night yes"); selected(Appearance.SYSTEM); theme(true)
        choose(Appearance.LIGHT); theme(false)
        val listsBefore=provider.getListRepository().readAll(); val settingBefore=provider.getDatabase().settings().read()
        provider.getDatabase().openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_appearance BEFORE UPDATE ON settings BEGIN SELECT RAISE(ABORT,'injected appearance write failure'); END")
        try {
            compose.onNodeWithTag("appearance_dark").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("appearance_error").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("appearance_error").performScrollTo().assertIsDisplayed(); selected(Appearance.LIGHT); theme(false)
            assertEquals(settingBefore,provider.getDatabase().settings().read()); assertEquals(listsBefore,provider.getListRepository().readAll())
        } finally { provider.getDatabase().openHelper.writableDatabase.execSQL("DROP TRIGGER fail_appearance") }
        choose(Appearance.DARK); theme(true); compose.onNodeWithTag("appearance_error").assertDoesNotExist()
        val reopened=com.xiaodlb.tdmk.data.DatabaseProvider(compose.activity,filename="tdmk.db")
        try { assertEquals(Appearance.DARK,reopened.getDatabase().settings().read().appearance) } finally { reopened.close() }
    }

    @Test fun lightDarkControlsAndShellAtNormalDoubleTextAndBothOrientations() {
        val oldScale=shell("settings get system font_scale"); val oldOrientation=compose.activity.requestedOrientation
        try {
            for(scale in listOf("1.0","2.0")) {
                shell("settings put system font_scale $scale")
                for((orientation,label) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "portrait",ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "landscape")) {
                    compose.activityRule.scenario.onActivity { it.requestedOrientation=orientation }
                    val expected=if(label=="portrait") Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                    compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation==expected && compose.activity.resources.configuration.fontScale==scale.toFloat() }
                    for(mode in listOf(Appearance.LIGHT,Appearance.DARK)) {
                        choose(mode); theme(mode==Appearance.DARK)
                        for(item in Appearance.entries) {
                            val node=compose.onNodeWithTag("appearance_${item.storedValue}").performScrollTo().assertIsDisplayed().assertHasClickAction()
                            val bounds=node.fetchSemanticsNode().boundsInWindow
                            val density=compose.activity.resources.displayMetrics.density
                            val decor=compose.activity.window.decorView
                            val insets=requireNotNull(decor.rootWindowInsets).getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                            assertTrue(bounds.height>=48*density); assertTrue(bounds.left>=insets.left); assertTrue(bounds.right<=decor.width-insets.right)
                            assertTrue(bounds.top>=insets.top); assertTrue(bounds.bottom<=decor.height-insets.bottom)
                        }
                        capture("settings-${mode.storedValue}-$label-font$scale")
                        compose.onNodeWithTag("tab_Home").performClick(); theme(mode==Appearance.DARK); capture("home-${mode.storedValue}-$label-font$scale")
                        compose.onNodeWithTag("open_settings").performClick(); selected(mode)
                    }
                }
            }
        } finally { shell("settings put system font_scale ${if(oldScale=="null") "1.0" else oldScale}"); compose.activityRule.scenario.onActivity { it.requestedOrientation=oldOrientation } }
    }
}
