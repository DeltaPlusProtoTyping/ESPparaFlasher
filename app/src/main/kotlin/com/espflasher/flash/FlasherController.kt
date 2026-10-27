package com.espflasher.flash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.espflasher.model.FlashStatus
import com.espflasher.model.LogLevel
import com.espflasher.model.LogLine
import com.espflasher.model.PortState
import com.espflasher.theme.darkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

/**
 * Owns all app state and the background work. The UI observes [ports], [log],
 * and the settings; buttons call the action methods, which run off the UI
 * thread and write results back into the observable [PortState]s.
 */
class FlasherController(private val scope: CoroutineScope) {
    private val prefs = Preferences.userNodeForPackage(FlasherController::class.java)

    val ports: SnapshotStateList<PortState> = mutableStateListOf()
    val log: SnapshotStateList<LogLine> = mutableStateListOf()

    // Settings persist across launches.
    var buildDir by mutableStateOf(prefs.get("buildDir", "C:\\Users\\Benjamin\\LastOne1\\build"))
        private set
    var baud by mutableStateOf(prefs.getInt("baud", Esptool.DEFAULT_BAUD))
        private set
    var autoFlash by mutableStateOf(prefs.getBoolean("autoFlash", false))
        private set

    var build by mutableStateOf(Esptool.resolveBuild(buildDir))
        private set
    var scanning by mutableStateOf(false)
        private set
    var sessionFlashed by mutableStateOf(0)
        private set
    var sessionFailed by mutableStateOf(0)
        private set

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun log(msg: String, level: LogLevel = LogLevel.Info) {
        log.add(LogLine(LocalTime.now().format(timeFmt), msg, level))
        if (log.size > 500) log.removeRange(0, log.size - 500)
    }

    fun clearLog() = log.clear()

    fun changeBuildDir(dir: String) {
        buildDir = dir
        prefs.put("buildDir", dir)
        build = Esptool.resolveBuild(dir)
    }

    fun changeBaud(value: Int) {
        baud = value
        prefs.putInt("baud", value)
    }

    init {
        darkTheme = prefs.getBoolean("darkTheme", false)
    }

    fun changeDarkTheme(on: Boolean) {
        darkTheme = on
        prefs.putBoolean("darkTheme", on)
    }

    fun changeAutoFlash(on: Boolean) {
        autoFlash = on
        prefs.putBoolean("autoFlash", on)
        if (on) {
            log("Auto-flash on: boards entering boot mode flash automatically")
            ports.filter { it.status == FlashStatus.BootMode }.forEach { flashPort(it.port) }
        }
    }

    private fun card(port: String): PortState? = ports.firstOrNull { it.port == port }

    /**
     * Two independent loops:
     *  - presence: fast (1s) reg.exe scan so cards appear/disappear almost instantly,
     *    plus a re-read of the build dir so a fresh `idf.py build` shows up.
     *  - probe: boot-mode check of all idle ports IN PARALLEL, in-process.
     */
    fun startPolling() {
        scope.launch {
            while (isActive) {
                refreshPorts()
                val b = withContext(Dispatchers.IO) { Esptool.resolveBuild(buildDir) }
                if (b != build) build = b
                delay(1000)
            }
        }
        scope.launch {
            while (isActive) {
                probeIdlePorts()
                delay(150)
            }
        }
    }

    private suspend fun probeIdlePorts() {
        // Probe every port that isn't busy — including Flashed/Failed ones — so we keep
        // tracking connectivity and catch a board being swapped or put (back) into boot
        // mode. The probe never resets the chip.
        val candidates = ports.filter { !it.busy }
        if (candidates.isEmpty()) return
        val results = coroutineScope {
            candidates.map { p ->
                async(Dispatchers.IO) {
                    // A flash/reboot that just claimed the port wins; skip it this pass.
                    if (!p.lock.tryLock()) return@async null
                    try { p to SerialIo.probe(p.port) } finally { p.lock.unlock() }
                }
            }.awaitAll().filterNotNull()
        }
        for ((p, r) in results) {
            if (p.busy) continue
            val mac = r.bootMac
            if (mac != null) {
                // Answering the ROM bootloader = proof of an ESP, in download mode. A
                // just-flashed board sits here (EN isn't wired, so esptool's reset is a
                // no-op), so keep its Flashed/Failed badge while it's the same board.
                p.espSeen = true
                val before = p.status
                val sameBoard = mac.isEmpty() || mac == p.lastMac
                if (!(sameBoard && (before == FlashStatus.Flashed || before == FlashStatus.Failed))) {
                    p.status = FlashStatus.BootMode
                }
                if (mac.isNotEmpty()) p.mac = mac
                if (autoFlash && before != FlashStatus.BootMode && p.status == FlashStatus.BootMode) {
                    flashPort(p.port)
                }
            } else {
                // Not in the bootloader. UART traffic is also proof of a live ESP.
                if (r.active) p.espSeen = true
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
        val current = ports.map { it.port }
        if (found == current) return
        // Remove gone ports, add new ones. New cards start Unconnected (grey) — the
        // programmer is present but we have no proof an ESP is seated yet.
        ports.removeAll { it.port !in found }
        for (name in found) {
            if (ports.none { it.port == name }) ports.add(PortState(name))
        }
        ports.sortBy { it.port.removePrefix("COM").toIntOrNull() ?: 0 }
    }

    fun manualScan() {
        scope.launch {
            scanning = true
            refreshPorts()
            log("Found ${ports.size} port(s)")
            scanning = false
        }
    }

    /** Ports Flash All / Reboot All would act on. */
    val actionable: List<PortState>
        get() = ports.filter { !it.busy && it.status != FlashStatus.Unconnected }

    fun flashAll() {
        val targets = actionable.map { it.port }
        if (targets.isEmpty()) { log("No connected boards to flash", LogLevel.Error); return }
        log("Flashing ${targets.size} board(s) @ $baud baud")
        targets.forEach { flashPort(it) }
    }

    fun rebootAll() {
        val targets = actionable.map { it.port }
        if (targets.isEmpty()) { log("No connected boards to reboot", LogLevel.Error); return }
        targets.forEach { rebootPort(it) }
    }

    fun flashPort(port: String) {
        val p = card(port) ?: return
        if (p.busy) return
        val plan = Esptool.resolveBuild(buildDir)
        if (!plan.ok) {
            log("Missing build files: ${plan.missing.joinToString { it.name }.ifEmpty { "no images" }}", LogLevel.Error)
            return
        }
        // Flashed boards on this jig are still sitting in the ROM bootloader.
        val inBootMode = p.status == FlashStatus.BootMode || p.status == FlashStatus.Flashed
        val rate = baud
        p.busy = true
        p.status = FlashStatus.Flashing
        p.progress = 0f
        p.detail = ""
        p.error = ""
        p.lastMac = p.mac
        p.startedAt = System.currentTimeMillis()
        scope.launch {
            // Waits for an in-flight probe (a few hundred ms at most) to release the port.
            val res = p.lock.withLock {
                withContext(Dispatchers.IO) {
                    Esptool.flash(port, plan, rate, inBootMode,
                        onProgress = { prog -> p.progress = prog },
                        onLine = { line -> if (line.isNotBlank()) p.detail = line })
                        .also { SerialIo.restoreStubBaud(port, rate) }
                }
            }
            p.lastDurationMs = System.currentTimeMillis() - p.startedAt
            val secs = "%.1fs".format(p.lastDurationMs / 1000.0)
            if (res.ok) {
                p.espSeen = true   // a successful flash is proof the ESP is present
                p.status = FlashStatus.Flashed
                p.progress = 1f
                sessionFlashed++
                log("$port: flashed in $secs", LogLevel.Ok)
            } else {
                p.status = FlashStatus.Failed
                p.error = res.error
                sessionFailed++
                log("$port: FAILED after $secs — ${res.error}", LogLevel.Error)
            }
            p.busy = false
        }
    }

    fun rebootPort(port: String) {
        val p = card(port) ?: return
        if (p.busy) return
        p.busy = true
        scope.launch {
            val ok = p.lock.withLock { withContext(Dispatchers.IO) { SerialIo.reboot(port) } }
            if (ok) p.status = FlashStatus.Connected
            p.busy = false
            log("$port: ${if (ok) "rebooted" else "reboot failed (port busy?)"}", if (ok) LogLevel.Info else LogLevel.Error)
        }
    }
}
