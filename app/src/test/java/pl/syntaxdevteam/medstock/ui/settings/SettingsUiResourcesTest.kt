package pl.syntaxdevteam.medstock.ui.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SettingsUiResourcesTest {

    @Test
    fun `application information labels are localized and keep format placeholders`() {
        val english = stringResources(File("src/main/res/values/strings.xml"))
        val polish = stringResources(File("src/main/res/values-pl/strings.xml"))
        val german = stringResources(File("src/main/res/values-de/string.xml"))

        listOf(english, polish, german).forEach { strings ->
            assertTrue(strings.getValue("settings_version_value").contains("%1\$s"))
            assertTrue(strings.getValue("settings_db_date_value").contains("%1\$s"))
            assertTrue(strings.getValue("settings_database_size_value").contains("%1\$s"))
            assertTrue(strings.getValue("settings_database_size_value").contains("%2\$s"))
        }
        assertNotEquals(english.getValue("settings_author_value"), polish.getValue("settings_author_value"))
        assertNotEquals(english.getValue("settings_version_value"), polish.getValue("settings_version_value"))
        assertNotEquals(english.getValue("settings_db_date_value"), polish.getValue("settings_db_date_value"))
    }

    @Test
    fun `alarm permission card uses palette safe surface colors`() {
        val layout = File("src/main/res/layout/fragment_reminders_list.xml").readText()
        val cardStart = layout.indexOf("@+id/card_reminder_permissions")
        val cardEnd = layout.indexOf("</com.google.android.material.card.MaterialCardView>", cardStart)
        val permissionCard = layout.substring(cardStart, cardEnd)

        assertTrue(permissionCard.contains("app:cardBackgroundColor=\"?attr/medColorSurfaceCardSoft\""))
        assertTrue(permissionCard.contains("android:textColor=\"?attr/medColorTextPrimary\""))
        assertTrue(permissionCard.contains("android:textColor=\"?attr/medColorTextSecondary\""))
    }

    private fun stringResources(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until strings.length) {
                val element = strings.item(index) as Element
                put(element.getAttribute("name"), element.textContent)
            }
        }
    }
}
