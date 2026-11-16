package com.xingzhi.bootstrap.diagnostics

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

/**
 * Thread-safe rolling file writer.
 *
 * The active file is `<filePrefix>.log`. When the next record would make it larger than
 * [maxFileBytes], it rotates to `<filePrefix>.1.log`, shifting older files up through
 * `<filePrefix>.<maxFiles-1>.log` (the oldest). A single record larger than [maxFileBytes]
 * is still written. [log] swallows formatting and disk failures.
 *
 * [logFiles] returns the files that exist, oldest to newest.
 * [exportZip] stores those files as `logs/<filename>` (oldest to newest) plus [extraEntries].
 * UTF-8 text extras are passed through [redactor]; other bytes are copied unchanged.
 * The zip is written to a temp file in the destination directory and moved into place
 * while the writer lock is held.
 * [exportZipBytes] uses the same entry names but keeps the archive within [maxTotalBytes]
 * by including extras first, then logs newest-to-oldest, truncating the entry that would
 * cross the cap. [clear] deletes `<filePrefix>.log` and `<filePrefix>.<index>.log` in
 * [logDirectory].
 */
class RollingFileLogWriter(
    val logDirectory: Path,
    private val filePrefix: String = "app",
    private val maxFileBytes: Long = 1024 * 1024L,
    private val maxFiles: Int = 5,
    private val minSeverity: Severity = Severity.Debug,
    private val clock: Clock = Clock.systemUTC(),
    private val redactor: (String) -> String = LogRedactor::sanitize,
) : LogWriter() {
    private val lock = Any()

    init {
        require(filePrefix.isNotBlank()) { "filePrefix must not be blank" }
        require(maxFileBytes > 0L) { "maxFileBytes must be positive" }
        require(maxFiles >= 1) { "maxFiles must be at least 1" }
    }

    override fun isLoggable(tag: String, severity: Severity): Boolean = severity >= minSeverity

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        if (!isLoggable(tag, severity)) return
        try {
            val payload = formatRecord(severity, message, tag, throwable)
            synchronized(lock) {
                Files.createDirectories(logDirectory)
                val bytes = payload.toByteArray(Charsets.UTF_8)
                val current = currentFile()
                val size = if (Files.isRegularFile(current)) Files.size(current) else 0L
                if (size > 0L && size + bytes.size > maxFileBytes) {
                    rotate()
                }
                Files.write(
                    current,
                    bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND,
                )
            }
        } catch (_: Exception) {
            // Disk and formatting failures must not escape the logger.
        }
    }

    /** Existing rotated and current log files, oldest first. */
    fun logFiles(): List<Path> = synchronized(lock) { existingLogFiles() }

    fun exportZip(targetZip: Path, extraEntries: Map<String, ByteArray> = emptyMap()): Path =
        synchronized(lock) {
            val absoluteTarget = targetZip.toAbsolutePath()
            val parent = absoluteTarget.parent
            if (parent != null) Files.createDirectories(parent)
            val temp = Files.createTempFile(parent, "export-", ".zip.tmp")
            try {
                ZipOutputStream(Files.newOutputStream(temp)).use { zip ->
                    existingLogFiles().forEach { path ->
                        zip.putNextEntry(ZipEntry("logs/${path.fileName}"))
                        Files.newInputStream(path).use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                    extraEntries.forEach { (name, bytes) ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(sanitizeExtra(bytes))
                        zip.closeEntry()
                    }
                }
                moveIntoPlace(temp, absoluteTarget)
            } catch (error: Exception) {
                Files.deleteIfExists(temp)
                throw error
            }
            targetZip
        }

    fun exportZipBytes(
        maxTotalBytes: Long = 4 * 1024 * 1024L,
        extraEntries: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        if (maxTotalBytes <= 0L) return ByteArray(0)
        return synchronized(lock) {
            val parts = buildList {
                extraEntries.forEach { (name, bytes) -> add(name to sanitizeExtra(bytes)) }
                existingLogFiles().asReversed().forEach { path ->
                    add("logs/${path.fileName}" to Files.readAllBytes(path))
                }
            }
            var budget = maxTotalBytes
            var zipped = zipParts(limitParts(parts, budget))
            while (zipped.size.toLong() > maxTotalBytes && budget > 0L) {
                val overflow = zipped.size.toLong() - maxTotalBytes
                budget = (budget - overflow - 32L).coerceAtLeast(0L)
                zipped = zipParts(limitParts(parts, budget))
            }
            if (zipped.size.toLong() <= maxTotalBytes) zipped else ByteArray(0)
        }
    }

    fun clear() {
        synchronized(lock) {
            if (!Files.isDirectory(logDirectory)) return
            Files.list(logDirectory).use { paths ->
                paths.filter { path ->
                    Files.isRegularFile(path) && isManagedLogFile(path.fileName.toString())
                }.forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun formatRecord(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ): String = buildString {
        append(clock.instant())
        append(' ')
        append(severity.name.uppercase(Locale.ROOT))
        append('/')
        append(tag)
        append(": ")
        append(redactor(message))
        if (throwable != null) {
            append('\n')
            append(redactor(throwable.stackTraceToString()))
        }
        if (!endsWith('\n')) append('\n')
    }

    private fun rotate() {
        if (maxFiles <= 1) {
            Files.deleteIfExists(currentFile())
            return
        }
        val oldest = maxFiles - 1
        Files.deleteIfExists(indexedFile(oldest))
        for (index in (oldest - 1) downTo 1) {
            moveIfExists(indexedFile(index), indexedFile(index + 1))
        }
        moveIfExists(currentFile(), indexedFile(1))
    }

    private fun moveIfExists(source: Path, target: Path) {
        if (!Files.exists(source)) return
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun existingLogFiles(): List<Path> {
        if (!Files.isDirectory(logDirectory)) return emptyList()
        return Files.list(logDirectory).use { paths ->
            paths.filter { path ->
                Files.isRegularFile(path) && isManagedLogFile(path.fileName.toString())
            }.toList()
        }.sortedByDescending { rotationIndex(it.fileName.toString()) }
    }

    private fun isManagedLogFile(name: String): Boolean {
        if (name == "$filePrefix.log") return true
        val marker = "$filePrefix."
        if (!name.startsWith(marker) || !name.endsWith(".log")) return false
        val index = name.substring(marker.length, name.length - ".log".length).toIntOrNull()
        return index != null && index >= 1
    }

    /** `0` is the active file; higher indexes are older. */
    private fun rotationIndex(name: String): Int {
        if (name == "$filePrefix.log") return 0
        val marker = "$filePrefix."
        return name.substring(marker.length, name.length - ".log".length).toInt()
    }

    private fun currentFile(): Path = logDirectory.resolve("$filePrefix.log")

    private fun indexedFile(index: Int): Path = logDirectory.resolve("$filePrefix.$index.log")

    private fun sanitizeExtra(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes
        return try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            redactor(text).toByteArray(Charsets.UTF_8)
        } catch (_: CharacterCodingException) {
            bytes
        }
    }

    private fun limitParts(
        parts: List<Pair<String, ByteArray>>,
        uncompressedBudget: Long,
    ): List<Pair<String, ByteArray>> {
        if (uncompressedBudget <= 0L) return emptyList()
        val selected = mutableListOf<Pair<String, ByteArray>>()
        var used = 0L
        for ((name, bytes) in parts) {
            if (used >= uncompressedBudget) break
            val allowed = min(bytes.size.toLong(), uncompressedBudget - used).toInt()
            if (allowed <= 0) break
            selected += name to if (allowed == bytes.size) bytes else bytes.copyOf(allowed)
            used += allowed
        }
        return selected
    }

    private fun zipParts(parts: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun moveIntoPlace(temp: Path, target: Path) {
        try {
            Files.move(
                temp,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
