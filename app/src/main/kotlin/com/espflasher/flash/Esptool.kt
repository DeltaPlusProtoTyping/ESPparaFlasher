package com.espflasher.flash

import java.io.File

/**
 * Thin wrapper around the esptool.exe CLI. All methods block; callers run them
 * off the UI thread (see FlasherController, which uses coroutines on Dispatchers.IO).
 */
object Esptool {
    // Resolved lazily so a missing install produces a clear error rather than a crash at load.
    val exePath: String = findEsptool()

    const val CHIP = "esp32"
    const val BAUD = "460800"

    private fun findEsptool(): String {
        val candidates = listOf(
            System.getenv("ESPTOOL_PATH"),
            "C:\\Users\\${System.getProperty("user.name")}\\AppData\\Roaming\\Python\\Python314\\Scripts\\esptool.exe",
            "esptool.exe",
            "esptool",
        )
        for (c in candidates) {
            if (c == null) continue
            if (c == "esptool.exe" || c == "esptool") return c // assume on PATH
            if (File(c).exists()) return c
        }
        return "esptool.exe"
    }

    data class FlashFiles(
        val bootloader: File,
        val partitionTable: File,
        val otaData: File,
        val app: File,
    ) {
        fun allExist() = listOf(bootloader, partitionTable, otaData, app).all { it.exists() }
        fun missing() = listOf(bootloader, partitionTable, otaData, app).filter { !it.exists() }
    }

    /** Resolves the four flash images from an ESP-IDF build directory. */
    fun resolveBuild(buildDir: String): FlashFiles {
        val dir = File(buildDir)
        val bootloader = File(dir, "bootloader/bootloader.bin")
        val partition = File(dir, "partition_table/partition-table.bin")
        val ota = File(dir, "ota_data_initial.bin")
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
        return FlashFiles(bootloader, partition, ota, app)
    }

    /**
     * Probe a port WITHOUT resetting the chip. If the bootloader answers, the board
     * was put into download mode (boot button held during reset) -> returns mac.
     * If it doesn't answer, the chip is running firmware normally -> returns null.
     */
    fun probeBootMode(port: String): String? {
        // --connect-attempts 1: a single sync try. A board in download mode answers
        // in well under a second; one that's running firmware fails fast instead of
        // burning esptool's default multi-second connect loop. The 3s process cap is
        // a backstop in case the port hangs.
        val out = run(
            listOf(exePath, "--port", port, "--chip", CHIP, "--no-stub",
                "--connect-attempts", "1", "--before", "no-reset", "read-mac"),
            timeoutSec = 3,
        )
        val mac = Regex("MAC:\\s+([\\w:]+)").find(out.text)?.groupValues?.get(1)
        return if (out.text.contains("MAC:")) mac ?: "" else null
    }

    /**
     * Briefly open the port and see if any UART bytes arrive — i.e. a powered ESP is
     * actually present and talking (boot banner / app logs). Used as positive proof of
     * presence so an empty socket (no bytes ever) is never shown as "Connected". Baud
     * doesn't need to match exactly; we only care that *some* traffic shows up.
     */
    fun hasSerialActivity(port: String): Boolean {
        val script =
            "\$ErrorActionPreference='Stop';" +
            "\$p=New-Object System.IO.Ports.SerialPort('$port',115200);" +
            "try{\$p.Open();Start-Sleep -Milliseconds 250;Write-Output \$p.BytesToRead}" +
            "finally{\$p.Close()}"
        val out = run(listOf("powershell", "-NoProfile", "-Command", script), timeoutSec = 4)
        val n = Regex("(\\d+)").find(out.text.trim())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return n > 0
    }

    /** Flash all four images. onProgress reports 0..1 from the main app-image write. */
    fun flash(
        port: String,
        files: FlashFiles,
        onProgress: (Float) -> Unit,
        onLine: (String) -> Unit,
    ): Boolean {
        val cmd = listOf(
            exePath, "--port", port, "--chip", CHIP, "--baud", BAUD,
            "write-flash", "--flash-mode", "dio", "--flash-freq", "40m", "--flash-size", "4MB",
            "0x1000", files.bootloader.absolutePath,
            "0x8000", files.partitionTable.absolutePath,
            "0xd000", files.otaData.absolutePath,
            "0x10000", files.app.absolutePath,
        )
        val out = run(cmd, timeoutSec = 120) { line ->
            // esptool prints "... 45.9% 212992/463636 bytes..." — pull the percentage.
            Regex("(\\d+\\.\\d+)%").find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let {
                onProgress(it / 100f)
            }
            onLine(line.trim())
        }
        return out.text.contains("Hard resetting")
    }

    /**
     * Restart the board into its firmware by pulsing the reset line directly.
     *
     * We deliberately do NOT use `esptool run` — that connects to the ROM
     * bootloader first, which fails on a board already running firmware ("No
     * serial data received"). Instead we toggle RTS/DTR via .NET's SerialPort,
     * mirroring esptool's hard-reset sequence (DTR=false -> IO0 high/normal boot,
     * pulse RTS -> EN low then high). No bootloader sync required, and it's fast.
     */
    fun reboot(port: String): Boolean {
        val script =
            "\$ErrorActionPreference='Stop';" +
            "\$p=New-Object System.IO.Ports.SerialPort('$port',115200);" +
            "try{\$p.Open();\$p.DtrEnable=\$false;\$p.RtsEnable=\$true;" +
            "Start-Sleep -Milliseconds 120;\$p.RtsEnable=\$false;" +
            "Start-Sleep -Milliseconds 50;Write-Output 'REBOOT_OK'}" +
            "finally{\$p.Close()}"
        val out = run(
            listOf("powershell", "-NoProfile", "-Command", script),
            timeoutSec = 8,
        )
        return out.text.contains("REBOOT_OK")
    }

    data class RunResult(val text: String, val exit: Int)

    private fun run(
        cmd: List<String>,
        timeoutSec: Long,
        onLine: ((String) -> Unit)? = null,
    ): RunResult {
        return try {
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
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
            val finished = proc.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) { proc.destroyForcibly(); return RunResult(sb.toString() + "\n[timeout]", -1) }
            RunResult(sb.toString(), proc.exitValue())
        } catch (e: Exception) {
            RunResult("[error] ${e.message}", -1)
        }
    }
}
