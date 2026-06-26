package com.espflasher.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The lifecycle status of one programmer/board, shown as a colored dot on its card. */
enum class FlashStatus(val label: String) {
    Unconnected("Unconnected"),
    Connected("Connected"),
    BootMode("Boot Mode"),
    Flashing("Flashing"),
    Flashed("Flashed"),
    Failed("Failed"),
}

/** Reactive per-port state. One [PortState] backs one card in the UI. */
class PortState(val port: String) {
    var status by mutableStateOf(FlashStatus.Connected)
    var mac by mutableStateOf("")
    var progress by mutableStateOf(0f)   // 0..1 during flashing
    var detail by mutableStateOf("")     // last line of esptool output / error
    var busy by mutableStateOf(false)    // true while a flash/reboot job owns the port

    // Latched true once we have positive proof an ESP is on this port: it answered
    // the ROM bootloader (boot mode / flash) or emitted UART traffic. Until then a
    // silent port is treated as an empty socket, not "Connected". Reset implicitly
    // when the port is unplugged (the PortState is discarded and recreated).
    var espSeen by mutableStateOf(false)
}
