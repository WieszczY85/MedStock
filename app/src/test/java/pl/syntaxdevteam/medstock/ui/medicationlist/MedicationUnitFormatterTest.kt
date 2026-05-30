package pl.syntaxdevteam.medstock.ui.medicationlist

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationUnitFormatterTest {

    @Test
    fun abbreviatePolishTabletFormsToTabl() {
        assertEquals("tabl.", MedicationUnitFormatter.abbreviate("Tabletki"))
        assertEquals("tabl.", MedicationUnitFormatter.abbreviate("Tabletki powlekane"))
        assertEquals("tabl.", MedicationUnitFormatter.abbreviate("tabletka"))
        assertEquals("tabl.", MedicationUnitFormatter.abbreviate(" tabletek powlekanych "))
    }

    @Test
    fun abbreviateCommonLongMedicationUnits() {
        assertEquals("kaps.", MedicationUnitFormatter.abbreviate("Kapsułki twarde"))
        assertEquals("amp.", MedicationUnitFormatter.abbreviate("Ampułki"))
        assertEquals("fiol.", MedicationUnitFormatter.abbreviate("Fiolki"))
        assertEquals("sasz.", MedicationUnitFormatter.abbreviate("Saszetki"))
        assertEquals("szt.", MedicationUnitFormatter.abbreviate("sztuki"))
    }

    @Test
    fun preserveShortAndUnknownUnits() {
        assertEquals("tabl.", MedicationUnitFormatter.abbreviate("tabl."))
        assertEquals("ml", MedicationUnitFormatter.abbreviate("ML"))
        assertEquals("wkład", MedicationUnitFormatter.abbreviate("wkład"))
        assertEquals("", MedicationUnitFormatter.abbreviate("   "))
    }
}
