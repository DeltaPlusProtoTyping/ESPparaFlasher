package com.espflasher.flash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.espflasher.model.FlashStatus
import com.espflasher.model.PortState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Owns all app state and the background work. The UI observes [ports], [log],
 * and [buildDir]; buttons call the action methods, which run esptool off the UI
 * thread and write results back into the observable [PortState]s.
 */
class FlasherController(private val scope: CoroutineScope) {
    val ports: SnapshotStateList<PortState> = mutableStateListOf()
    val log: SnapshotStateList<String> = mutableStateListOf()

    var buildDir by mutableStateOf("C:\\Users\\Benjamin\\LastOne1\\build")
    var scanning by mutableStateOf(false)

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun log(msg: String) {
        log.add("${LocalTime.now().format(timeFmt)}  $msg")
        if (log.size > 500) log.removeRange(0, log.size - 500)
    }

    private fun card(port: String): PortState? = ports.firstOrNull { it.port == port }

    /**
     * Two independent loops:
     *  - presence: fast (1s) reg.exe scan so cards appear/disappear almost instantly.
     *  - probe: boot-mode check of all idle ports IN PARALLEL, so the whole pass is
     *    bounded by one esptool timeout (~3s) instead of N of them in series.
     */
    fun startPolling() {
        scope.launch {
            while (isActive) {
                refreshPorts()
                delay(1000)
            }
        }
        scope.launch {
            while (isActive) {
                probeIdlePorts()
                delay(1500)
            }
        }
    }

    private suspend fun probeIdlePorts() {
        // Probe every port that isn't actively flashing — including Flashed/Failed ones
        // — so we keep tracking connectivity and catch a board being put (back) into
        // boot mode. The probe uses --before no-reset, so it never disturbs a board
        // that's running firmware.
        val candidates = ports.filter { !it.busy && it.status != FlashStatus.Flashing }
        if (candidates.isEmpty()) return
        // Fire every probe at once. For a port not in the bootloader, also sniff for
        // UART traffic so we can tell a running ESP from an empty socket. Collect
        // everything, then apply on the UI thread.
        val results = coroutineScope {
            candidates.map { p ->
                async(Dispatchers.IO) {
                    val mac = Esptool.probeBootMode(p.port)
                    val active = if (mac == null) Esptool.hasSerialActivity(p.port) else false
                    Triple(p, mac, active)
                }
            }.awaitAll()
        }
        for ((p, mac, active) in results) {
            if (p.busy || p.status == FlashStatus.Flashing) continue
            if (mac != null) {
                // Answering the ROM bootloader = proof of an ESP, in download mode. A
                // just-flashed board sits here (EN isn't wired, so esptool's reset is a
                // no-op), so keep the green "Flashed" badge; anything else is Boot Mode.
                p.espSeen = true
                if (p.status != FlashStatus.Flashed) p.status = FlashStatus.BootMode
                if (mac.isNotEmpty()) p.mac = mac
            } else {
                // Not in the bootloader. UART traffic is also proof of a live ESP.
                if (active) p.espSeen = true
                if (p.espSeen) {
                    // Known-present ESP, now running firmware (e.g. physically reset out
                    // of boot mode / after a flash) -> Connected.
                    if (p.status != FlashStatus.Failed) p.status = FlashStatus.Connected
                } else {
                    // No proof an ESP was ever here -> empty socket, stay Unconnected.
                    p.status = FlashStatus.Unconnected
                }
            }
        }
    }

    suspend fun refreshPorts() {
        val found = withContext(Dispatchers.IO) { PortScanner.scan() }
        // Remove gone ports.
        ports.removeAll { it.port !in found }
        // Add new ports. Start Unconnected (grey) — the programmer is present but we
        // have no proof an ESP is seated yet; the probe loop promotes it once it does.
        for (name in found) {
            if (ports.none { it.port == name }) {
                ports.add(PortState(name).also { it.status = FlashStatus.Unconnected })
            }
        }
        ports.sortBy { it.port.removePrefix("COM").toIntOrNull() ?: 0 }
    }

    fun manualScan() {
        scope.launch {
            scanning = true
            log("Scanning ports...")
            refreshPorts()
            log("Found ${ports.size} port(s)")
            scanning = false
        }
    }

    fun flashAll() {
        val targets = ports.filter { !it.busy && it.status != FlashStatus.Unconnected }.map { it.port }
        if (targets.isEmpty()) { log("No connected ports to flash"); return }
        log("Flashing ${targets.size} port(s)...")
        targets.forEach { flashPort(it) }
    }

    fun rebootAll() {
        val targets = ports.filter { !it.busy && it.status != FlashStatus.Unconnected }.map { it.port }
        if (targets.isEmpty()) { log("No connected ports to reboot"); return }
        log("Rebooting ${targets.size} port(s)...")
        targets.forEach { rebootPort(it) }
    }

    fun flashPort(port: String) {
        val p = card(port) ?: return
        if (p.busy) return
        val files = Esptool.resolveBuild(buildDir)
        if (!files.allExist()) {
            log("Missing build files: ${files.missing().joinToString { it.name }}")
            return
        }
        p.busy = true
        p.status = FlashStatus.Flashing
        p.progress = 0f
        p.detail = ""
        scope.launch {
            log("$port: Flashing...")
            val ok = withContext(Dispatchers.IO) {
                Esptool.flash(port, files,
                    onProgress = { prog -> p.progress = prog },
                    onLine = { line -> if (line.isNotBlank()) p.detail = line })
            }
            if (ok) p.espSeen = true   // a successful flash is proof the ESP is present
            p.status = if (ok) FlashStatus.Flashed else FlashStatus.Failed
            p.progress = if (ok) 1f else p.progress
            p.busy = false
            log("$port: ${if (ok) "Flashed OK" else "FAILED"}")
        }
    }

    fun rebootPort(port: String) {
        val p = card(port) ?: return
        if (p.busy) return
        p.busy = true
        scope.launch {
            log("$port: Rebooting...")
            val ok = withContext(Dispatchers.IO) { Esptool.reboot(port) }
            if (ok) p.status = FlashStatus.Connected
            p.busy = false
            log("$port: ${if (ok) "Rebooted" else "Reboot failed"}")
        }
    }
}
