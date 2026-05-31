package pl.syntaxdevteam.medstock.core.account

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {

    @Test
    fun `android 12 cloud backup includes backup snapshot file`() {
        val includedPaths = xmlIncludes(File("src/main/res/xml/data_extraction_rules.xml"), "cloud-backup")

        assertTrue(includedPaths.contains(BACKUP_SNAPSHOT_PATH))
    }

    @Test
    fun `android 12 device transfer includes backup snapshot file`() {
        val includedPaths = xmlIncludes(File("src/main/res/xml/data_extraction_rules.xml"), "device-transfer")

        assertTrue(includedPaths.contains(BACKUP_SNAPSHOT_PATH))
    }

    @Test
    fun `legacy full backup includes backup snapshot file`() {
        val includedPaths = xmlIncludes(File("src/main/res/xml/backup_rules.xml"), "full-backup-content")

        assertTrue(includedPaths.contains(BACKUP_SNAPSHOT_PATH))
    }

    private fun xmlIncludes(file: File, sectionName: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val section = document.getElementsByTagName(sectionName).item(0) as Element
        val includes = section.getElementsByTagName("include")
        return buildSet {
            for (index in 0 until includes.length) {
                val include = includes.item(index) as Element
                if (include.getAttribute("domain") == "file") {
                    add(include.getAttribute("path"))
                }
            }
        }
    }

    private companion object {
        const val BACKUP_SNAPSHOT_PATH = "drive_backup/medstock_medications_backup.json"
    }
}
