package com.xingzhi.bootstrap.diagnostics

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RollingFileLogWriterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `appends until the next record would pass maxFileBytes`() {
        val body = "x".repeat(16)
        val line = "2026-01-01T00:00:00Z DEBUG/test: $body\n"
        val writer = newWriter(maxFileBytes = line.toByteArray(Charsets.UTF_8).size * 2L, maxFiles = 4)
        writer.log(Severity.Debug, body, "test", null)
        writer.log(Severity.Debug, body, "test", null)

        assertEquals(listOf("app.log"), names(writer))

        writer.log(Severity.Debug, body, "test", null)

        assertEquals(listOf("app.1.log", "app.log"), names(writer))
    }

    @Test
    fun `rotates across maxFiles and drops the oldest file`() {
        val message = { n: Int -> "M$n".padEnd(24, 'x') }
        val lineLength = "2026-01-01T00:00:00Z DEBUG/test: ${message(1)}\n".toByteArray(Charsets.UTF_8).size
        val writer = newWriter(maxFileBytes = lineLength * 2L, maxFiles = 3)
        repeat(7) { index -> writer.log(Severity.Debug, message(index + 1), "test", null) }

        val files = writer.logFiles()
        assertEquals(listOf("app.2.log", "app.1.log", "app.log"), files.map { it.fileName.toString() })
        val oldest = Files.readString(files[0])
        val middle = Files.readString(files[1])
        val newest = Files.readString(files[2])
        assertTrue(oldest.contains(message(3)) && oldest.contains(message(4)))
        assertTrue(middle.contains(message(5)) && middle.contains(message(6)))
        assertTrue(newest.contains(message(7)))
        assertFalse(newest.contains(message(6)))
        val combined = oldest + middle + newest
        assertFalse(combined.contains(message(1)))
        assertFalse(combined.contains(message(2)))
        assertEquals(3L, Files.list(directory).use { stream -> stream.count() })
    }

    @Test
    fun `drops records below minSeverity`() {
        val writer = newWriter(minSeverity = Severity.Warn)
        assertFalse(writer.isLoggable("t", Severity.Info))
        assertTrue(writer.isLoggable("t", Severity.Warn))
        assertTrue(writer.isLoggable("t", Severity.Assert))
        writer.log(Severity.Info, "hidden-info", "t", null)
        writer.log(Severity.Warn, "visible-warn", "t", null)

        val text = Files.readString(writer.logFiles().single())
        assertFalse(text.contains("hidden-info"))
        assertTrue(text.contains("WARN/t: visible-warn"))
    }

    @Test
    fun `accepts logs from a Kermit Logger and filters verbose`() {
        val writer = newWriter()
        val logger = Logger(loggerConfigInit(writer, minSeverity = Severity.Verbose), tag = "boot")
        logger.v { "hidden-verbose" }
        logger.d { "ready password=hunter2" }

        val text = Files.readString(writer.logFiles().single())
        assertTrue(text.contains("DEBUG/boot: ready password=[REDACTED]"))
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("hidden-verbose"))
    }

    @Test
    fun `redacts messages and throwable text`() {
        val writer = newWriter()
        val failure = IllegalStateException("SIP |COlogin-secret| token=access-secret")
        writer.log(
            Severity.Error,
            "Bearer bearer-secret password: yaml-secret",
            "sip",
            failure,
        )

        val text = Files.readString(writer.logFiles().single())
        assertTrue(text.startsWith("2026-01-01T00:00:00Z ERROR/sip: "))
        listOf("bearer-secret", "yaml-secret", "login-secret", "access-secret").forEach { secret ->
            assertFalse(text.contains(secret), secret)
        }
        assertTrue(text.contains("Bearer [REDACTED]"))
        assertTrue(text.contains("password: [REDACTED]"))
        assertTrue(text.contains("|CO[REDACTED]|"))
        assertTrue(text.contains("token=[REDACTED]"))
        assertTrue(text.contains("IllegalStateException"))
    }

    @Test
    fun `concurrent logs do not interleave or drop records`() {
        val writer = newWriter(maxFileBytes = 1024 * 1024, maxFiles = 5, clock = Clock.systemUTC())
        val threads = 8
        val perThread = 40
        val pool = Executors.newFixedThreadPool(threads)
        try {
            (0 until threads).map { id ->
                pool.submit {
                    repeat(perThread) { n ->
                        writer.log(Severity.Info, "thread-$id-$n", "pool", null)
                    }
                }
            }.forEach { it.get() }
        } finally {
            pool.shutdown()
        }

        val lines = writer.logFiles().flatMap { Files.readAllLines(it) }
        assertEquals(threads * perThread, lines.size)
        val pattern = Regex(
            """^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z INFO/pool: thread-\d+-\d+$""",
        )
        assertTrue(lines.all(pattern::matches))
    }

    @Test
    fun `concurrent rotation keeps surviving records intact`() {
        val writer = newWriter(maxFileBytes = 180, maxFiles = 3, clock = Clock.systemUTC())
        val pool = Executors.newFixedThreadPool(4)
        try {
            (0 until 4).map { id ->
                pool.submit {
                    repeat(30) { n ->
                        writer.log(Severity.Info, "thread-$id-$n", "pool", null)
                    }
                }
            }.forEach { it.get() }
        } finally {
            pool.shutdown()
        }

        val files = writer.logFiles()
        assertTrue(files.isNotEmpty())
        assertTrue(files.size <= 3)
        val lines = files.flatMap { Files.readAllLines(it) }
        val pattern = Regex(
            """^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z INFO/pool: thread-\d+-\d+$""",
        )
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all(pattern::matches))
    }

    @Test
    fun `exportZip packages logs oldest to newest and redacts text extras`() {
        val writer = newWriter(maxFileBytes = 1, maxFiles = 3)
        listOf("event-1", "event-2", "event-3", "event-4").forEach { body ->
            writer.log(Severity.Debug, body, "test", null)
        }
        val zip = directory.resolve("out")
        val binary = byteArrayOf(0x00, 0xff.toByte(), 0xfe.toByte(), 0x10)
        val written = writer.exportZip(
            zip,
            mapOf(
                "device-summary.json" to """{"accessToken":"access-secret"}""".encodeToByteArray(),
                "blob.bin" to binary,
            ),
        )

        assertEquals(zip, written)
        val entries = zipEntries(zip)
        assertEquals(
            listOf("logs/app.2.log", "logs/app.1.log", "logs/app.log", "device-summary.json", "blob.bin"),
            entries.keys.toList(),
        )
        assertTrue(entries.getValue("logs/app.2.log").toString(Charsets.UTF_8).contains("event-2"))
        assertTrue(entries.getValue("logs/app.1.log").toString(Charsets.UTF_8).contains("event-3"))
        assertTrue(entries.getValue("logs/app.log").toString(Charsets.UTF_8).contains("event-4"))
        val combined = entries.values.joinToString("\n") { it.toString(Charsets.UTF_8) }
        assertFalse(combined.contains("event-1"))
        val summary = entries.getValue("device-summary.json").toString(Charsets.UTF_8)
        assertEquals("""{"accessToken":"[REDACTED]"}""", summary)
        assertArrayEquals(binary, entries.getValue("blob.bin"))
        assertTrue(Files.notExists(directory.resolve("export-")))
        assertTrue(
            Files.list(zip.parent).use { paths ->
                paths.noneMatch { it.fileName.toString().startsWith("export-") }
            },
        )
    }

    @Test
    fun `exportZipBytes stays within maxTotalBytes and prefers recent logs`() {
        val writer = newWriter(maxFileBytes = 100_000, maxFiles = 2)
        writer.log(
            Severity.Info,
            "HEAD-" + "x".repeat(8_000) + "-password=tail-secret",
            "test",
            null,
        )
        val extra = mapOf("device-summary.json" to """{"password":"hunter2"}""".encodeToByteArray())

        assertTrue(writer.exportZipBytes(maxTotalBytes = 0, extraEntries = extra).isEmpty())

        val bytes = writer.exportZipBytes(maxTotalBytes = 1_500, extraEntries = extra)
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes.size <= 1_500)
        val text = zipEntries(bytes).values.joinToString("\n") { it.toString(Charsets.UTF_8) }
        assertTrue(text.contains("HEAD-"))
        assertTrue(text.contains("[REDACTED]"))
        assertFalse(text.contains("tail-secret"))
        assertFalse(text.contains("hunter2"))
    }

    @Test
    fun `exportZip while logging does not corrupt the archive`() {
        val writer = newWriter(maxFileBytes = 280, maxFiles = 4, clock = Clock.systemUTC())
        val started = CountDownLatch(1)
        val writerThread = Thread {
            repeat(80) { n ->
                writer.log(Severity.Error, "n=$n password=hunter2", "t", null)
                if (n == 0) started.countDown()
            }
        }
        writerThread.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val zip = directory.resolve("live.zip")
        repeat(20) {
            writer.exportZip(
                zip,
                mapOf("device-summary.json" to """{"password":"hunter2"}""".encodeToByteArray()),
            )
            val bytes = writer.exportZipBytes(
                maxTotalBytes = 2_000,
                extraEntries = mapOf("device-summary.json" to """{"password":"hunter2"}""".encodeToByteArray()),
            )
            assertTrue(bytes.size <= 2_000)
            if (bytes.isNotEmpty()) {
                ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
                    assertNotNull(input.nextEntry)
                }
            }
        }
        writerThread.join(5_000)
        assertFalse(writerThread.isAlive)

        val text = zipEntries(zip).values.joinToString("\n") { it.toString(Charsets.UTF_8) }
        assertFalse(text.contains("hunter2"))
        assertTrue(text.contains("[REDACTED]"))
    }

    @Test
    fun `clear deletes managed logs and leaves unrelated files`() {
        val writer = newWriter(maxFileBytes = 48, maxFiles = 4)
        repeat(12) { writer.log(Severity.Debug, "line-$it-${"abcdefghij"}", "test", null) }
        assertTrue(writer.logFiles().size > 1)
        Files.writeString(directory.resolve("notes.txt"), "password=keep")
        Files.writeString(directory.resolve("apple.log"), "not-ours")
        val zip = directory.resolve("bundle.zip")
        writer.exportZip(zip)

        writer.clear()

        assertTrue(writer.logFiles().isEmpty())
        assertEquals("password=keep", Files.readString(directory.resolve("notes.txt")))
        assertEquals("not-ours", Files.readString(directory.resolve("apple.log")))
        assertTrue(Files.exists(zip))
        assertTrue(
            Files.list(directory).use { paths ->
                paths.noneMatch { path ->
                    val name = path.fileName.toString()
                    name == "app.log" || name.matches(Regex("""app\.\d+\.log"""))
                }
            },
        )
    }

    @Test
    fun `log swallows disk failures`() {
        val blocker = directory.resolve("not-a-directory")
        Files.writeString(blocker, "x")
        val writer = RollingFileLogWriter(logDirectory = blocker.resolve("logs"))

        writer.log(Severity.Assert, "password=hunter2", "t", IllegalStateException("token=abc"))

        assertTrue(writer.logFiles().isEmpty())
    }

    private fun newWriter(
        maxFileBytes: Long = 1024 * 1024L,
        maxFiles: Int = 5,
        minSeverity: Severity = Severity.Debug,
        clock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
        filePrefix: String = "app",
    ): RollingFileLogWriter = RollingFileLogWriter(
        logDirectory = directory,
        filePrefix = filePrefix,
        maxFileBytes = maxFileBytes,
        maxFiles = maxFiles,
        minSeverity = minSeverity,
        clock = clock,
    )

    private fun names(writer: RollingFileLogWriter): List<String> =
        writer.logFiles().map { it.fileName.toString() }

    private fun zipEntries(zip: Path): Map<String, ByteArray> =
        zipEntries(Files.newInputStream(zip))

    private fun zipEntries(bytes: ByteArray): Map<String, ByteArray> =
        zipEntries(ByteArrayInputStream(bytes))

    private fun zipEntries(source: java.io.InputStream): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(source).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                result[entry.name] = input.readBytes()
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        return result
    }
}
