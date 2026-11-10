package dev.robinsiegl.kairo

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private val payment = Payment(id = "one", name = "Example", icon = "SERVICE_PROTON_VPN", category = "Work",
        terms = listOf(Terms("2026-01-31", "9.999", Interval.Monthly), Terms("2026-01-31", "12.99", Interval.Monthly, "2026-12-01")),
        trial = true, cancelBy = "2026-01-30", lastActiveDay = "2027-01-31", cancelled = true, remind = true)
    private val book = Book(payments = listOf(payment), categories = listOf(Category("Work", "Teal")),
        preferences = Preferences(theme = ThemeMode.Dark, dynamicColor = true, secondTrialReminder = true))

    @Test
    fun aVerifiedBackupPreservesPricesHistoryIconsCategoriesAndPreferences() {
        val bytes = verifiedBackupBytes(book)
        assertEquals(book, readBackup(ByteArrayInputStream(bytes)))
        assertTrue(verifyExport(book) { ByteArrayInputStream(bytes) })
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bytes
        assertEquals(book, readBackup(ByteArrayInputStream(withBom)))
    }

    @Test
    fun corruptUnreadableOrChangedExportsNeverReportVerified() {
        val bytes = verifiedBackupBytes(book)
        assertFalse(verifyExport(book) { ByteArrayInputStream(bytes.copyOf(bytes.size / 2)) })
        assertFalse(verifyExport(book) { throw IOException("Document provider is unavailable") })
        assertFalse(verifyExport(book) { ByteArrayInputStream(verifiedBackupBytes(Book())) })
        assertThrows(Exception::class.java) { readBackup(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }
        assertThrows(IllegalArgumentException::class.java) { readBackup(ByteArrayInputStream(ByteArray(MAX_BACKUP_BYTES + 1))) }
    }

    @Test
    fun malformedAndUnsupportedBooksAreRejectedBeforeWriting() {
        assertThrows(IllegalArgumentException::class.java) { verifiedBackupBytes(book.copy(version = 2)) }
        assertThrows(IllegalArgumentException::class.java) { verifiedBackupBytes(book.copy(payments = listOf(payment, payment))) }
        assertThrows(IllegalArgumentException::class.java) { verifiedBackupBytes(book.copy(payments = listOf(payment.copy(lastActiveDay = "+10000-01-01")))) }
        assertThrows(IllegalArgumentException::class.java) { verifiedBackupBytes(book.copy(preferences = Preferences(reminderHour = 25))) }
    }

    @Test
    fun repeatedRestoreKeepsLocalEditsAndDoesNotDuplicateHistoryOrCategories() {
        val restored = Book().merge(readBackup(ByteArrayInputStream(verifiedBackupBytes(book)))).book
        assertEquals(book, restored)
        val edited = restored.copy(payments = listOf(payment.copy(name = "Local edit")))
        val merge = edited.merge(book)
        assertEquals(1, merge.conflicts)
        assertEquals(0, merge.added)
        assertEquals(edited, merge.book)
        assertEquals(edited, merge.book.merge(book).book)
    }

    @Test
    fun incompatibleCurrenciesCannotAddPaymentsButAnEmptyBookAdoptsTheBackupCurrency() {
        val foreign = book.copy(payments = listOf(payment.copy(id = "foreign")), preferences = Preferences(defaultCurrency = "USD"))
        assertThrows(IllegalArgumentException::class.java) { book.merge(foreign) }
        val empty = Book(preferences = Preferences(theme = ThemeMode.Dark))
        val restored = empty.merge(foreign).book
        assertEquals("USD", restored.preferences.defaultCurrency)
        assertEquals(ThemeMode.Dark, restored.preferences.theme)
        val duplicate = book.copy(preferences = foreign.preferences)
        assertEquals(book, book.merge(duplicate).book)
    }
}
