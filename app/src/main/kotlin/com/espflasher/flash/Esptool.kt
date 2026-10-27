package com.espflasher.flash

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin wrapper around the esptool.exe CLI, used for the actual flash. All methods
 * block; callers run them off the UI thread (see FlasherController). Probing and
 * reboot don't need esptool at all — see [SerialIo].
 */
object Esptool {
    // Resolved once so a missing install produces a clear error rather than a crash at load.
    val exePath: String = findEsptool()

    const val CHIP = "esp32"
    val BAUD_RATES = listOf(460800, 921600, 1500000)
    const val DEFAULT_BAUD = 921600

    private fun findEsptool(): String {
        val candidates = listOf(
            System.getenv("ESPTOOL_PATH"),
            "C:\\Users\\${System.getProperty("user.name")}\\AppData\\Roaming\\Python\\Python314\\Scripts\\esptool.exe",
        )
        for (c in candidates) {
            if (c != null && File(c).exists()) return c
        }
        return "esptool.exe" // assume on PATH
    }

    data class Image(val offset: Long, val file: File, val size: Long)

    /**
     * What to write and how. [options] are the flash mode/freq/size flags. Images are
     * sorted by offset, which is the order esptool writes them in.
     */
    data class FlashPlan(val images: List<Image>, val options: List<String>, val fromFlashArgs: Boolean) {
        val missing: List<File> get() = images.filter { it.size <= 0 }.map { it.file }
        val ok: Boolean get() = images.isNotEmpty() && missing.isEmpty()
        val totalBytes: Long get() = images.sumOf { it.size }
        /** The application image: the one at 0x10000, else the largest. */
        val app: Image? get() = images.firstOrNull { it.offset == 0x10000L } ?: images.maxByOrNull { it.size }
    }

    /**
     * Resolves the images from an ESP-IDF build directory. Prefers the build's own
     * `flash_args` (exact offsets and flash settings for this project's partition
     * table); falls back to the classic ESP32 layout if it isn't there.
     */
    fun resolveBuild(buildDir: String): FlashPlan {
        val dir = File(buildDir)
        val flashArgs = File(dir, "flash_args")
        if (flashArgs.isFile) {
            runCatching { parseFlashArgs(dir, flashArgs.readText()) }.getOrNull()
                ?.takeIf { it.images.isNotEmpty() }
                ?.let { return it }
        }
        // App bin: <projectname>.bin in the build root. Prefer a name matching the dir,
        // else the first non-CMake, non-ota .bin at the top level.
        var app = File(dir, dir.name + ".bin")
        if (!app.exists()) {
            val bins = dir.listFiles { f ->
                f.isFile && f.name.endsWith(".bin") &&
                    !f.name.contains("CMake") && f.name != "ota_data_initial.bin"
            }
            if (bins != null && bins.isNotEmpty()) app = bins.first()
        }
        val images = listOf(
            0x1000L to File(dir, "bootloader/bootloader.bin"),
            0x8000L to File(dir, "partition_table/partition-table.bin"),
            0xd000L to File(dir, "ota_data_initial.bin"),
            0x10000L to app,
        ).map { (off, f) -> Image(off, f, f.length()) }
        return FlashPlan(images, listOf("--flash-mode", "dio", "--flash-freq", "40m", "--flash-size", "4MB"), false)
    }

    /** `--flash_mode dio --flash_freq 40m --flash_size 4MB` then `0x1000 bootloader/bootloader.bin` lines. */
    private fun parseFlashArgs(dir: File, text: String): FlashPlan {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val options = mutableListOf<String>()
        val images = mutableListOf<Image>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            if (t.startsWith("--")) {
                // esptool v5 spells options with dashes; the IDF file still uses underscores.
                options += t.replace('_', '-')
                if (next != null && !next.startsWith("--") && !next.startsWith("0x")) { options += next; i++ }
            } else if (t.startsWith("0x") && next != null) {
                val f = File(dir, next)
                images += Image(t.removePrefix("0x").toLong(16), f, f.length())
                i++
            }
            i++
        }
        return FlashPlan(images.sortedBy { it.offset }, options, true)
    }

    data class FlashResult(val ok: Boolean, val error: String)

    /**
     * Flash all images. [onProgress] reports 0..1 across the whole job (weighted by
     * image size), not per image. [inBootMode] skips esptool's reset dance when we
     * already know the ROM bootloader is listening.
     */
    fun flash(
        port: String,
        plan: FlashPlan,
        baud: Int,
        inBootMode: Boolean,
        onProgress: (Float) -> Unit,
        onLine: (String) -> Unit,
    ): FlashResult {
        val cmd = listOf(
            exePath, "--port", port, "--chip", CHIP, "--baud", baud.toString(),
            "--before", if (inBootMode) "no-reset" else "default-reset",
            "write-flash",
        ) + plan.options + plan.images.flatMap { listOf("0x%x".format(it.offset), it.file.absolutePath) }

        val sizes = plan.images.map { it.size.coerceAtLeast(1) }
        val total = sizes.sum().toFloat()
        var index = 0
        var done = 0L
        val pct = Regex("(\\d+(?:\\.\\d+)?)%")

        val out = run(cmd, timeoutSec = 180) { line ->
            // esptool prints "Writing at 0x... [===>  ]  45.9% 212992/463636 bytes..." per
            // image, then "Wrote N bytes ..." when that image is done.
            if (line.startsWith("Wrote ")) {
                if (index < sizes.size) done += sizes[index++]
                onProgress(done / total)
            } else if (line.startsWith("Writing at") && index < sizes.size) {
                pct.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let {
                    onProgress((done + sizes[index] * it / 100f) / total)
                }
            }
            onLine(line.trim())
        }
        if (out.exit == 0) return FlashResult(true, "")
        return FlashResult(false, errorSummary(out))
    }

    /** The one line worth showing on a card when esptool fails. */
    private fun errorSummary(out: RunResult): String {
        if (out.timedOut) return "Timed out"
        val lines = out.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        lines.lastOrNull { it.startsWith("A fatal error occurred") }
            ?.let { return it.removePrefix("A fatal error occurred:").trim() }
        return lines.lastOrNull { !it.startsWith("For troubleshooting") } ?: "esptool exited ${out.exit}"
    }

    data class RunResult(val text: String, val exit: Int, val timedOut: Boolean = false)

    private fun run(
        cmd: List<String>,
        timeoutSec: Long,
        onLine: ((String) -> Unit)? = null,
    ): RunResult {
        return try {
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            // The read loop below blocks until EOF, so the timeout has to be enforced
            // from outside: kill the process, which closes its stdout and ends the loop.
            val timedOut = AtomicBoolean(false)
            Thread {
                if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                    timedOut.set(true)
                    proc.destroyForcibly()
                }
            }.apply { isDaemon = true; start() }

            val sb = StringBuilder()
            proc.inputStream.bufferedReader().use { reader ->
                // esptool uses \r for in-place progress; split on both.
                val buf = CharArray(512)
                val line = StringBuilder()
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    for (i in 0 until n) {
                        val ch = buf[i]
                        if (ch == '\n' || ch == '\r') {
                            if (line.isNotEmpty()) {
                                val l = line.toString()
                                sb.append(l).append('\n')
                                onLine?.invoke(l)
                                line.clear()
                            }
                        } else line.append(ch)
                    }
                }
                if (line.isNotEmpty()) { sb.append(line); onLine?.invoke(line.toString()) }
            }
            proc.waitFor()
            RunResult(sb.toString(), if (timedOut.get()) -1 else proc.exitValue(), timedOut.get())
        } catch (e: Exception) {
            RunResult("[error] ${e.message}", -1)
        }
    }
}
