package dev.robinsiegl.kairo

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun readBackup(stream: InputStream): Book {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var count = stream.read(buffer)
    while (count != -1) {
        require(output.size() + count <= MAX_BACKUP_BYTES) { "Backup is too large" }
        output.write(buffer, 0, count)
        count = stream.read(buffer)
    }
    return decodeBook(output.toByteArray().decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF"))
}

internal fun verifiedBackupBytes(book: Book): ByteArray {
    val bytes = encodeBook(book).encodeToByteArray()
    require(bytes.size <= MAX_BACKUP_BYTES) { "Backup is too large" }
    require(decodeBook(bytes.decodeToString()) == book) { "Backup did not round-trip" }
    return bytes
}

// A failed read-back must not report a successfully verified export.
internal fun verifyExport(book: Book, read: () -> InputStream): Boolean =
    runCatching { read().use(::readBackup) == book }.getOrDefault(false)
