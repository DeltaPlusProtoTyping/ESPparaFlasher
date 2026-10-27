package com.espflasher.flash

import com.fazecast.jSerialComm.SerialPort
import java.io.ByteArrayOutputStream

/**
 * Direct, in-process serial access via jSerialComm.
 *
 * Polling used to spawn an esptool process (Python start-up) plus a PowerShell
 * process per port every cycle — ~16 process launches a pass for an 8-up jig.
 * Here the same checks are a few hundred milliseconds of serial I/O with no
 * child processes, so status updates faster and the CPU stays free for flashing.
 */
object SerialIo {
    /** Outcome of one probe. [bootMac] is non-null when the ROM bootloader answered. */
    data class Probe(val bootMac: String?, val active: Boolean)

    private const val PROBE_BAUD = 115200
    private const val OP_SYNC = 0x08
    private const val OP_READ_REG = 0x0A
    private const val OP_CHANGE_BAUDRATE = 0x0F
    private const val EFUSE_RD_REG_BASE = 0x3FF5A000L  // ESP32

    // SYNC payload from the ESP serial protocol: 07 07 12 20, then 32 x 0x55.
    private val SYNC_DATA = byteArrayOf(0x07, 0x07, 0x12, 0x20) + ByteArray(32) { 0x55 }

    private fun open(port: String): SerialPort? {
        val sp = SerialPort.getCommPort(port)
        sp.setComPortParameters(PROBE_BAUD, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
        // Keep DTR/RTS released on open so we never twitch EN/IO0 on boards that wire them.
        sp.clearDTR()
        sp.clearRTS()
        return if (sp.openPort()) sp else null
    }

    /**
     * Probe a port WITHOUT resetting the chip. If the ROM bootloader answers SYNC, the
     * board is in download mode and we read its MAC from eFuse (same as esptool
     * `--before no-reset read-mac`). Otherwise we listen for up to [listenMs] for any
     * UART bytes — positive proof a powered ESP is running firmware. Quiet firmware
     * sends nothing, so the window is long (and the poll gap short) to keep the port
     * open most of the time and catch the ROM boot banner when someone presses EN.
     */
    fun probe(port: String, listenMs: Long = 600): Probe {
        val sp = open(port) ?: return Probe(null, false)
        try {
            sp.flushIOBuffers()
            val reader = SlipReader(sp)
            repeat(2) {
                sp.write(command(OP_SYNC, SYNC_DATA))
                if (reader.awaitResponse(OP_SYNC, 120) != null) {
                    // The ROM answers one SYNC with several replies; let them drain.
                    reader.drain(60)
                    return Probe(readMac(sp, reader), true)
                }
            }
            // Not in the bootloader. Listen for running firmware / a boot banner.
            reader.listen(listenMs)
            return Probe(null, reader.sawBytes)
        } finally {
            sp.closePort()
        }
    }

    /**
     * After a flash at [fromBaud], esptool's flasher stub is still running at that baud:
     * its post-flash hard reset is an RTS pulse, which does nothing on a jig without
     * EN wired. Every later connect starts at 115200, so the board would look dead and
     * a re-flash would fail. Tell the stub to drop back to 115200 (CHANGE_BAUDRATE,
     * new/old baud) so it answers SYNC again like the ROM bootloader.
     */
    fun restoreStubBaud(port: String, fromBaud: Int) {
        if (fromBaud == PROBE_BAUD) return
        val sp = open(port) ?: return
        try {
            sp.setBaudRate(fromBaud)
            sp.flushIOBuffers()
            val data = ByteArray(8)
            putLe32(data, 0, PROBE_BAUD.toLong())
            putLe32(data, 4, fromBaud.toLong())
            sp.write(command(OP_CHANGE_BAUDRATE, data))
            SlipReader(sp).awaitResponse(OP_CHANGE_BAUDRATE, 300)
        } finally {
            sp.closePort()
        }
    }

    /**
     * Pulse the reset line, mirroring esptool's hard reset (DTR released -> IO0 high /
     * normal boot, RTS pulse -> EN low then high). Only has an effect on jigs/boards
     * that wire RTS to EN.
     */
    fun reboot(port: String): Boolean {
        val sp = open(port) ?: return false
        return try {
            sp.setRTS()
            Thread.sleep(120)
            sp.clearRTS()
            Thread.sleep(50)
            true
        } finally {
            sp.closePort()
        }
    }

    private fun SerialPort.write(bytes: ByteArray) {
        writeBytes(bytes, bytes.size)
    }

    /**
     * MAC from eFuse, or "" if it can't be read reliably. A bad read would look like a
     * newly seated board (and trigger auto-flash), so a value only counts once two
     * back-to-back reads agree.
     */
    private fun readMac(sp: SerialPort, reader: SlipReader): String {
        fun once(): String? {
            val w1 = readReg(sp, reader, EFUSE_RD_REG_BASE + 4) ?: return null
            val w2 = readReg(sp, reader, EFUSE_RD_REG_BASE + 8) ?: return null
            return formatMac(w2, w1)
        }
        repeat(3) {
            val a = once()
            if (a != null && a == once()) return a
        }
        return ""
    }

    private fun readReg(sp: SerialPort, reader: SlipReader, addr: Long): Long? {
        val data = ByteArray(4)
        putLe32(data, 0, addr)
        sp.write(command(OP_READ_REG, data))
        val resp = reader.awaitResponse(OP_READ_REG, 150) ?: return null
        // Response data ends in status bytes; the first is 0 on success (ROM and stub).
        if (resp.size > 8 && resp[8].toInt() != 0) return null
        return getLe32(resp, 4)
    }

    // esptool: struct.pack(">II", efuse[2], efuse[1])[2:8]
    private fun formatMac(w2: Long, w1: Long): String {
        val b = longArrayOf(w2 shr 8, w2, w1 shr 24, w1 shr 16, w1 shr 8, w1)
        return b.joinToString(":") { "%02x".format(it and 0xFF) }
    }

    /** Request packet: dir(0) op size16 checksum32 data, SLIP-framed. */
    private fun command(op: Int, data: ByteArray): ByteArray {
        val pkt = ByteArray(8 + data.size)
        pkt[1] = op.toByte()
        pkt[2] = (data.size and 0xFF).toByte()
        pkt[3] = (data.size shr 8 and 0xFF).toByte()
        data.copyInto(pkt, 8)
        return slipEncode(pkt)
    }

    private fun slipEncode(pkt: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(pkt.size + 8)
        out.write(0xC0)
        for (b in pkt) {
            when (val v = b.toInt() and 0xFF) {
                0xC0 -> { out.write(0xDB); out.write(0xDC) }
                0xDB -> { out.write(0xDB); out.write(0xDD) }
                else -> out.write(v)
            }
        }
        out.write(0xC0)
        return out.toByteArray()
    }

    private fun putLe32(buf: ByteArray, off: Int, v: Long) {
        for (i in 0 until 4) buf[off + i] = (v shr (8 * i) and 0xFF).toByte()
    }

    private fun getLe32(buf: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = v or ((buf[off + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    /** Pulls SLIP frames off the port; also records whether *any* byte arrived. */
    private class SlipReader(private val sp: SerialPort) {
        var sawBytes = false
            private set
        private val buf = ByteArray(512)
        private val frame = ByteArrayOutputStream()
        private val frames = ArrayDeque<ByteArray>()
        private var esc = false

        /** Next response frame `01 <op> ...` within [timeoutMs], or null. */
        fun awaitResponse(op: Int, timeoutMs: Long): ByteArray? {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                while (frames.isNotEmpty()) {
                    val f = frames.removeFirst()
                    if (f.size >= 8 && f[0].toInt() == 0x01 && (f[1].toInt() and 0xFF) == op) return f
                }
                val left = deadline - System.currentTimeMillis()
                if (left <= 0 || !fill(left)) return null
            }
        }

        /** Read and discard whatever arrives for [ms]. */
        fun drain(ms: Long) {
            val deadline = System.currentTimeMillis() + ms
            while (true) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0 || !fill(left)) break
            }
            frames.clear()
        }

        /** Wait up to [ms] for any byte at all; returns as soon as one arrives. */
        fun listen(ms: Long) {
            val deadline = System.currentTimeMillis() + ms
            while (!sawBytes) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0 || !fill(left)) break
            }
        }

        /** One blocking read of up to [timeoutMs]. Returns false if the port errored. */
        private fun fill(timeoutMs: Long): Boolean {
            sp.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, timeoutMs.toInt(), 0)
            val n = sp.readBytes(buf, buf.size)
            if (n < 0) return false
            if (n > 0) sawBytes = true
            for (i in 0 until n) feed(buf[i].toInt() and 0xFF)
            return true
        }

        private fun feed(v: Int) {
            when {
                v == 0xC0 -> {
                    if (frame.size() > 0) frames.addLast(frame.toByteArray())
                    frame.reset()
                    esc = false
                }
                esc -> { frame.write(if (v == 0xDC) 0xC0 else if (v == 0xDD) 0xDB else v); esc = false }
                v == 0xDB -> esc = true
                else -> frame.write(v)
            }
        }
    }
}
