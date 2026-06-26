package com.espflasher.flash

/**
 * Enumerates CP210x USB-UART COM ports.
 *
 * Reads HKLM\HARDWARE\DEVICEMAP\SERIALCOMM via reg.exe — this is effectively
 * instant (a few ms), unlike the Win32_PnPEntity WMI/CIM query which takes
 * seconds. The CP210x driver registers its ports under the value name
 * "\Device\Silabser<n>", so we filter on "Silabser" to keep only the programmers.
 */
object PortScanner {
    private val comRegex = Regex("(COM\\d+)")

    fun scan(): List<String> {
        return try {
            val proc = ProcessBuilder(
                "reg", "query", "HKLM\\HARDWARE\\DEVICEMAP\\SERIALCOMM"
            ).redirectErrorStream(true).start()
            val text = proc.inputStream.bufferedReader().readText()
            proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            text.lineSequence()
                .filter { it.contains("Silabser", ignoreCase = true) }
                .mapNotNull { comRegex.find(it)?.groupValues?.get(1) }
                .distinct()
                .sortedBy { it.removePrefix("COM").toIntOrNull() ?: 0 }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
