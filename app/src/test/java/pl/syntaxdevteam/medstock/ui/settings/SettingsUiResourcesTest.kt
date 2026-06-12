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

    @Test
    fun `support buttons inherit palette contrast text from design system`() {
        val designSystem = File("src/main/res/values/design_system.xml").readText()
        val styleStart = designSystem.indexOf("Widget.MedStock.Button.CtaSupport")
        val styleEnd = designSystem.indexOf("</style>", styleStart)
        val supportStyle = designSystem.substring(styleStart, styleEnd)

        assertTrue(supportStyle.contains("""<item name="backgroundTint">?attr/medColorPrimarySoft</item>"""))
        assertTrue(supportStyle.contains("""<item name="android:textColor">?attr/medColorTextPrimary</item>"""))
        listOf(
            File("src/main/res/values/themes.xml"),
            File("src/main/res/values-night/themes.xml")
        ).forEach { themeFile ->
            assertTrue(
                themeFile.readText().contains(
                    """<item name="colorOnSecondary">?attr/medColorTextPrimary</item>"""
                )
            )
        }

        var supportButtonCount = 0
        File("src/main/res").walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "xml" &&
                    file.parentFile?.name?.startsWith("layout") == true
            }
            .forEach { layoutFile ->
                val layout = layoutFile.readText()
                var searchFrom = 0
                while (true) {
                    val buttonStart = layout.indexOf(
                        """style="@style/Widget.MedStock.Button.CtaSupport"""",
                        searchFrom
                    )
                    if (buttonStart < 0) break
                    val buttonEnd = layout.indexOf("/>", buttonStart)
                    val supportButton = layout.substring(buttonStart, buttonEnd)
                    assertTrue(
                        "${layoutFile.path} overrides CtaSupport text color",
                        !supportButton.contains("android:textColor=")
                    )
                    supportButtonCount++
                    searchFrom = buttonEnd + 2
                }
            }
        assertTrue(supportButtonCount > 0)
    }

    @Test
    fun `theme choices use readable vertical radio lists`() {
        val layout = File("src/main/res/layout/fragment_settings.xml").readText()

        assertTrue(layout.contains("<RadioGroup"))
        assertTrue(layout.contains("@+id/settings_theme_mode_group"))
        assertTrue(layout.contains("@+id/settings_palette_group"))
        assertTrue(layout.contains("@style/Widget.MedStock.RadioButton.SettingsOption"))
        assertTrue(layout.contains("android:orientation=\"vertical\""))
    }

    @Test
    fun `catalog visibility switch uses the Material Components widget and palette colors`() {
        val layout = File("src/main/res/layout/fragment_settings.xml").readText()
        val designSystem = File("src/main/res/values/design_system.xml").readText()
        val styleStart = designSystem.indexOf("Widget.MedStock.Switch")
        val styleEnd = designSystem.indexOf("</style>", styleStart)
        val switchStyle = designSystem.substring(styleStart, styleEnd)

        assertTrue(layout.contains("com.google.android.material.switchmaterial.SwitchMaterial"))
        assertTrue(layout.contains("style=\"@style/Widget.MedStock.Switch\""))
        assertTrue(switchStyle.contains("Widget.MaterialComponents.CompoundButton.Switch"))
        assertTrue(switchStyle.contains("<item name=\"showText\">false</item>"))
        assertTrue(switchStyle.contains("<item name=\"useMaterialThemeColors\">false</item>"))
        assertTrue(switchStyle.contains("@color/med_switch_thumb_tint"))
        assertTrue(switchStyle.contains("@color/med_switch_track_tint"))
    }

    @Test
    fun `programmatic settings rendering does not invoke preference listeners`() {
        val fragment = File(
            "src/main/java/pl/syntaxdevteam/medstock/ui/settings/SettingsFragment.kt"
        ).readText()

        assertTrue(fragment.contains("isRenderingUiState = true"))
        assertTrue(fragment.contains("isRenderingUiState = false"))
        assertTrue(fragment.contains("isViewStateRestored = true"))
        assertTrue(fragment.contains("if (!canHandlePreferenceChange()) return@setOnCheckedChangeListener"))
        assertTrue(fragment.contains("if (!isChecked || !canHandlePreferenceChange())"))
    }

    @Test
    fun `palette recreation runs after the radio group callback finishes`() {
        val fragment = File(
            "src/main/java/pl/syntaxdevteam/medstock/ui/settings/SettingsFragment.kt"
        ).readText()

        assertTrue(fragment.contains("schedulePaletteRecreation(colorPalette)"))
        assertTrue(fragment.contains("yield()"))
        assertTrue(fragment.contains("isPaletteRecreationPending"))
    }

    @Test
    fun `catalog visibility setting is localized in every supported language`() {
        listOf(
            File("src/main/res/values/strings.xml"),
            File("src/main/res/values-pl/strings.xml"),
            File("src/main/res/values-de/string.xml")
        ).map(::stringResources).forEach { strings ->
            assertTrue(strings.getValue("settings_catalog_view_title").isNotBlank())
            assertTrue(strings.getValue("settings_show_inactive_pharmacies").isNotBlank())
        }
    }

    @Test
    fun `inactive pharmacies are hidden by default`() {
        val preferences = File(
            "src/main/java/pl/syntaxdevteam/medstock/core/settings/CatalogViewPreferences.kt"
        ).readText()

        assertTrue(preferences.contains("getBoolean(KEY_SHOW_INACTIVE_PHARMACIES, false)"))
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
