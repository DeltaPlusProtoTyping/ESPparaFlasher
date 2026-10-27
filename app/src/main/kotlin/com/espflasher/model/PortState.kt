package com.espflasher.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex

/** The lifecycle status of one programmer/board, shown as a colored badge on its card. */
enum class FlashStatus(val label: String) {
    Unconnected("No signal"),
    Connected("Connected"),
    BootMode("Boot Mode"),
    Flashing("Flashing"),
    Flashed("Flashed"),
    Failed("Failed"),
}

enum class LogLevel { Info, Ok, Error }

data class LogLine(val time: String, val text: String, val level: LogLevel)

/** Reactive per-port state. One [PortState] backs one card in the UI. */
class PortState(val port: String) {
    var status by mutableStateOf(FlashStatus.Unconnected)
    var mac by mutableStateOf("")
    var progress by mutableStateOf(0f)   // 0..1 across the whole flash job
    var detail by mutableStateOf("")     // last line of esptool output while flashing
    var error by mutableStateOf("")      // why the last flash failed
    var busy by mutableStateOf(false)    // true while a flash/reboot job owns the port
    var startedAt by mutableStateOf(0L)  // epoch ms the current flash began
    var lastDurationMs by mutableStateOf(0L)

    // Latched true once we have positive proof an ESP is on this port: it answered
    // the ROM bootloader (boot mode / flash) or emitted UART traffic. Until then a
    // silent port is treated as an empty socket, not "Connected". Reset implicitly
    // when the port is unplugged (the PortState is discarded and recreated).
    var espSeen by mutableStateOf(false)

    // MAC of the board the last flash was attempted on. A different MAC showing up in
    // boot mode means a fresh board was seated, so the old Flashed/Failed badge is stale.
    var lastMac = ""

    // Windows COM ports are exclusive: a probe holding the port makes esptool fail to
    // open it. Everything that touches the port takes this lock.
    val lock = Mutex()
}
