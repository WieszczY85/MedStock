package pl.syntaxdevteam.medstock.core.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppColorPaletteTest {
    @Test
    fun `stored palette value resolves matching palette`() {
        assertEquals(AppColorPalette.OCEAN, AppColorPalette.fromPreferenceValue("ocean"))
        assertEquals(AppColorPalette.LAVENDER, AppColorPalette.fromPreferenceValue("lavender"))
    }

    @Test
    fun `missing or unknown palette keeps original green default`() {
        assertEquals(AppColorPalette.GREEN, AppColorPalette.fromPreferenceValue(null))
        assertEquals(AppColorPalette.GREEN, AppColorPalette.fromPreferenceValue("unknown"))
    }
}
