package com.xiaodlb.tdmk

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherConfigurationTest {
    @Test
    fun launcherActivityIsDeclaredAndExported() {
        val manifest = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val xpath = XPathFactory.newInstance().newXPath()
        val launcher = "/manifest/application/activity[intent-filter[" +
            "action/@*[name()='android:name']='android.intent.action.MAIN' and " +
            "category/@*[name()='android:name']='android.intent.category.LAUNCHER']]"

        assertEquals("1", xpath.evaluate("count($launcher)", manifest))
        assertEquals(".MainActivity", xpath.evaluate("$launcher/@*[name()='android:name']", manifest))
        assertEquals("true", xpath.evaluate("$launcher/@*[name()='android:exported']", manifest))
    }
}
