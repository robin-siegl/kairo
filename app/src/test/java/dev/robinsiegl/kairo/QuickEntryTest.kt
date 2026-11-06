package dev.robinsiegl.kairo

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class QuickEntryTest {
    private val today = LocalDate.parse("2026-11-06")

    @Test
    fun duplicatesUseTheCurrentPriceAndANewScheduleWithoutCopyingHistory() {
        val original = Payment(name = "Example", category = "Work", icon = "CODE", notes = "Personal plan",
            manageLink = "https://example.com/", tags = listOf("shared"), trial = true,
            cancelBy = "2026-11-09", lastActiveDay = "2026-11-12", cancelled = true, remind = true,
            terms = listOf(Terms("2026-01-31", "9.99", Interval.Monthly),
                Terms("2026-01-31", "12.99", Interval.Monthly, "2026-12-01")))
        val draft = original.duplicateDraft(today)
        assertEquals("Example copy", draft.name)
        assertEquals("9.99", draft.amount)
        assertEquals(today.toString(), draft.firstCharge)
        assertEquals(original.category, draft.category)
        assertEquals(original.tags, draft.tags)
        assertEquals(original.manageLink, draft.manageLink)
        assertEquals(2, original.terms.size)
    }

    @Test
    fun anActiveDuplicateUsesTheNextOriginalAnchorRatherThanStartingInThePast() {
        val original = Payment(name = "Monthly", terms = listOf(Terms("2026-01-31", "9.99", Interval.Monthly)))
        assertEquals("2026-11-30", original.duplicateDraft(today).firstCharge)
    }

    @Test
    fun presetsAreSearchableAndRequireTheUsersPrice() {
        val preset = matchingServices("proton cloud").single()
        assertEquals("proton_drive", preset.id)
        val draft = preset.draft(today)
        assertEquals("", draft.amount)
        assertEquals(today.toString(), draft.firstCharge)
        assertEquals("", draft.manageLink)
        assertTrue(servicePresets.all { it.draft(today).manageLink.isEmpty() })
        assertTrue(servicePresets.size >= 50)
        assertEquals(servicePresets.size, servicePresets.map { it.id }.distinct().size)
        assertEquals(servicePresets, matchingServices("   "))
        assertTrue(matchingServices("no such service").isEmpty())
    }

    @Test
    fun appearanceSettingsRoundTripAndOldBackupsKeepKairoBlue() {
        val old = decodeBook("""{"version":1,"payments":[],"preferences":{"defaultCurrency":"EUR"}}""")
        assertEquals(ThemeMode.System, old.preferences.theme)
        assertFalse(old.preferences.dynamicColor)
        val settings = old.copy(preferences = old.preferences.copy(theme = ThemeMode.Dark, dynamicColor = true))
        assertEquals(settings, decodeBook(encodeBook(settings)))
    }
}
