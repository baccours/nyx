package com.baccours.nyx.data

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Asserts that backup rules exclude all SharedPreferences files. */
class BackupRulesTest {

    private fun root(name: String): Element {
        // Gradle runs unit tests in the module dir; IDEs may run them from the project root.
        val file = listOf("src/main/res/xml", "app/src/main/res/xml").map { File(it, name) }.first { it.exists() }
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun excludedSharedPrefs(parent: Element): List<String> {
        val nodes = parent.getElementsByTagName("exclude")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("domain") == "sharedpref" }
            .map { it.getAttribute("path") }
    }

    @Test
    fun fullBackupContent_excludesAllSharedPrefs() {
        assertTrue("." in excludedSharedPrefs(root("backup_rules.xml")))
    }

    @Test
    fun dataExtractionRules_excludeAllSharedPrefs_fromCloudBackupAndDeviceTransfer() {
        val rules = root("data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = rules.getElementsByTagName(section).item(0) as Element
            assertTrue("$section must exclude all shared preferences", "." in excludedSharedPrefs(element))
        }
    }
}
