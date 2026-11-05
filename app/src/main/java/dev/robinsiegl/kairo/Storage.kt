package dev.robinsiegl.kairo

import android.app.backup.BackupManager
import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import java.util.Currency
import kotlinx.serialization.json.Json

private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

fun encodeBook(book: Book): String = json.encodeToString(book)

fun decodeBook(text: String): Book {
    val book = json.decodeFromString<Book>(text)
    require(book.version == 1)
    require(book.payments.distinctBy { it.id }.size == book.payments.size)
    require(book.categories.map { it.name }.distinct().size == book.categories.size)
    require(book.categories.all { it.name.isNotBlank() && it.name == it.name.trim() && it.color in categoryColors })
    val prefs = book.preferences
    Currency.getInstance(prefs.defaultCurrency)
    require(prefs.renewalLead in 1..7 && prefs.trialLead in 1..7)
    require(prefs.reminderHour in 0..23 && prefs.reminderMinute in 0..59)
    for (payment in book.payments) {
        require(payment.id.isNotBlank() && payment.name.isNotBlank() && payment.terms.isNotEmpty())
        require(payment.terms.zipWithNext().all { (first, second) ->
            first.effectiveDate < second.effectiveDate
        })
        for (terms in payment.terms) {
            require(terms.anchor.year in 1900..9998 && terms.effectiveDate.year in 1900..9998)
            require(parseStoredAmount(terms.amount) != null)
        }
        payment.end
        payment.cancelBy?.let(LocalDate::parse)
    }
    return book
}

class Storage(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "kairo.json"))

    fun read(): Book = synchronized(lock) {
        try {
            decodeBook(file.readFully().decodeToString())
        } catch (_: FileNotFoundException) {
            Book()
        }
    }

    fun write(book: Book) = synchronized(lock) {
        val bytes = encodeBook(book).encodeToByteArray()
        require(bytes.size <= MAX_BACKUP_BYTES)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        BackupManager(context).dataChanged()
        WidgetUpdates.enqueue(context)
    }

    companion object {
        private val lock = Any()
    }
}
