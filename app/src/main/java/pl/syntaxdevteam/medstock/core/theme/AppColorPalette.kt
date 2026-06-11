package pl.syntaxdevteam.medstock.core.theme

import pl.syntaxdevteam.medstock.R

enum class AppColorPalette(
    val preferenceValue: String,
    val themeResId: Int
) {
    GREEN("green", R.style.Theme_MedStock_NoActionBar),
    OCEAN("ocean", R.style.Theme_MedStock_Ocean_NoActionBar),
    BERRY("berry", R.style.Theme_MedStock_Berry_NoActionBar),
    SAGE("sage", R.style.Theme_MedStock_Sage_NoActionBar),
    LAVENDER("lavender", R.style.Theme_MedStock_Lavender_NoActionBar);

    companion object {
        fun fromPreferenceValue(value: String?): AppColorPalette = entries.firstOrNull {
            it.preferenceValue == value
        } ?: GREEN
    }
}
