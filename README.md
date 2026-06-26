# ESPparaFlasher

A desktop GUI for flashing several ESP32 boards in parallel from a pogo-pin jig.
Built with **Kotlin + Compose for Desktop** (same stack as
[NodeNote](https://github.com/DeltaPlusProtoTyping/NodeNote)).

It scans for CP210x USB-UART programmers, lays them out as a grid of cards, shows
each board's live status, and flashes them individually or all at once.

## Features

- **Auto-discovery** of CP210x COM ports (fast `reg`-based scan, ~tens of ms)
- **Live status per board**, polled continuously:
  - `Unconnected` — programmer present, no ESP detected yet
  - `Connected` — an ESP is present and running firmware
  - `Boot Mode` — board is in the ROM download bootloader
  - `Flashing` — write in progress (with a progress bar)
  - `Flashed` — flash completed successfully
  - `Failed` — flash failed
- **Boot-mode detection over USB** — probes each idle port without resetting it
- **Presence proof** — a port only shows `Connected` once an ESP has actually
  answered the bootloader or emitted UART traffic, so empty sockets stay grey
- **Flash All / Reboot All**, plus per-card Flash and Reboot
- **Reboot** pulses the serial control lines directly (no bootloader round-trip)
- Editable build directory, live log panel, status legend

## Requirements

- Windows (port scanning and reset use Windows APIs)
- JDK 17 (the Gradle toolchain targets 17)
- [`esptool`](https://github.com/espressif/esptool) on `PATH`, or set `ESPTOOL_PATH`
- An ESP-IDF build directory containing `bootloader/bootloader.bin`,
  `partition_table/partition-table.bin`, `ota_data_initial.bin`, and the app `.bin`

## Running

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.3.1"
./gradlew :app:run
```

Point the **Build** field at your ESP-IDF `build/` directory, then use the per-card
or **Flash All** buttons.

## Packaging

```powershell
./gradlew :app:packageMsi   # Windows installer
```

## Notes on hardware wiring

This tool assumes a jig where the ESP's `EN` / `IO0` pins are on physical buttons
rather than wired to the programmer's RTS/DTR. Because of that, a board stays in
download mode after flashing until it is physically reset — the UI accounts for
this when deciding between `Flashed` and `Connected`.
