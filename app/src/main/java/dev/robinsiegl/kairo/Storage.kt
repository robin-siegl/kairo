package dev.robinsiegl.kairo

import android.app.backup.BackupManager
import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import java.util.Currency
import kotlinx.serialization.json.Json

private val json = Json { encodeDefaults = true }
const val MAX_BACKUP_BYTES = 20 * 1024 * 1024

fun encodeBook(book: Book): String = json.encodeToString(book)

fun decodeBook(text: String): Book {
    val book = json.decodeFromString<Book>(text)
    require(book.version == 1)
    require(book.payments.distinctBy { it.id }.size == book.payments.size)
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
            require(parseAmount(terms.amount, terms.currency) != null)
        }
        payment.end
        payment.cancelBy?.let(LocalDate::parse)
        require(payment.image == null || payment.image.length <= 250_000)
    }
    return book
}

class Storage(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "kairo.json"))

    fun read(): Book = try {
        decodeBook(file.readFully().decodeToString())
    } catch (_: FileNotFoundException) {
        Book()
    }

    fun write(book: Book) {
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
    }
}
