package pl.syntaxdevteam.medstock.ui.settings

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import com.google.android.material.switchmaterial.SwitchMaterial
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pl.syntaxdevteam.medstock.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsLayoutInflationTest {

    @Test
    fun `settings layout inflates a palette tinted switch without Material 3 overlay`() {
        val context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            R.style.Theme_MedStock_Ocean_NoActionBar
        )

        val root = LayoutInflater.from(context).inflate(R.layout.fragment_settings, null, false)
        val switch = root.findViewById<SwitchMaterial>(
            R.id.settings_show_inactive_pharmacies_switch
        )

        assertNotNull(switch)
        assertFalse(switch.showText)
        assertFalse(switch.isUseMaterialThemeColors)
        assertNotNull(switch.thumbTintList)
        assertNotNull(switch.trackTintList)
        assertTrue(switch.context.theme.resolveAttribute(R.attr.medColorPrimary, android.util.TypedValue(), true))
    }
}
