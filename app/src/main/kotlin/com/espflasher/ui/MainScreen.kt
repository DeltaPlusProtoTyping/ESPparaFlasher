package com.espflasher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.espflasher.flash.Esptool
import com.espflasher.flash.FlasherController
import com.espflasher.model.FlashStatus
import com.espflasher.model.LogLevel
import com.espflasher.theme.AppColors
import java.io.File
import javax.swing.JFileChooser

@Composable
fun MainScreen(controller: FlasherController) {
    LaunchedEffect(Unit) { controller.startPolling() }

    Column(Modifier.fillMaxSize().background(AppColors.canvasBackground)) {
        AppBar(controller)
        StepBand(controller)
        StatusBar(controller)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (controller.ports.isEmpty()) {
                EmptyState(Modifier.align(Alignment.Center))
            } else {
                // Fixed rows of 4 (8 ports -> 2 rows) to mirror the physical jig. Cards
                // are a fixed width so the layout never reflows with window size.
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    controller.ports.chunked(4).forEach { rowPorts ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rowPorts.forEach { p ->
                                PortCard(
                                    state = p,
                                    onFlash = { controller.flashPort(p.port) },
                                    onReboot = { controller.rebootPort(p.port) },
                                    modifier = Modifier.width(230.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        LogPanel(controller)
    }
}

/** White bar with the brand block on the left (nRF Connect style) and settings on the right. */
@Composable
private fun AppBar(controller: FlasherController) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(AppColors.panelBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.fillMaxHeight().background(AppColors.brand).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Memory, null, tint = AppColors.onBrand, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("ESPparaFlasher", color = AppColors.onBrand, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(18.dp))
        Text("${Esptool.CHIP.uppercase()} PARALLEL FLASHER", color = AppColors.textSecondary,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.weight(1f))

        BaudSelector(controller)
        Spacer(Modifier.width(16.dp))
        AutoFlashToggle(controller)
        Spacer(Modifier.width(16.dp))
        OutlinedButton(
            onClick = { controller.rebootAll() },
            enabled = controller.actionable.isNotEmpty(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AppColors.panelBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.textSecondary),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(34.dp),
        ) { ButtonLabel(Icons.Rounded.RestartAlt, "Reboot All") }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { controller.manualScan() }) {
            Icon(Icons.Rounded.Refresh, "Rescan ports", tint = AppColors.textSecondary)
        }
        Spacer(Modifier.width(8.dp))
    }
}

/**
 * The balenaEtcher / Raspberry Pi Imager idea: the whole job as three big steps on a
 * bold band — Firmware -> Boards -> Flash — each with one clear button.
 */
@Composable
private fun StepBand(controller: FlasherController) {
    val build = controller.build
    val ports = controller.ports
    val ready = controller.actionable.size
    val boot = ports.count { it.status == FlashStatus.BootMode }
    val running = ports.count { it.status == FlashStatus.Connected }
    val empty = ports.count { it.status == FlashStatus.Unconnected }
    val flashing = ports.count { it.status == FlashStatus.Flashing }

    Row(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(AppColors.brandDeep, AppColors.brandDeeper)))
            .padding(vertical = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top,
    ) {
        Step(
            icon = Icons.Rounded.InsertDriveFile,
            label = build.app?.file?.name?.takeIf { build.ok } ?: "Choose build",
            caption = if (build.ok) "${kb(build.totalBytes)} · ${build.images.size} images · ${shortPath(controller.buildDir)}"
                else if (build.images.isEmpty()) "No images in ${shortPath(controller.buildDir)}"
                else "Missing: ${build.missing.joinToString { it.name }}",
            captionColor = if (build.ok) null else Color(0xFFFFCDD2),
            primary = false,
            enabled = true,
            onClick = { pickBuildDir(controller) },
        )
        Connector()
        Step(
            icon = Icons.Rounded.DeveloperBoard,
            label = if (ports.isEmpty()) "No jig" else "$ready of ${ports.size} ready",
            caption = if (ports.isEmpty()) "Plug in the programmers"
                else listOfNotNull(
                    "$boot in boot mode".takeIf { boot > 0 },
                    "$running running".takeIf { running > 0 },
                    "$empty silent".takeIf { empty > 0 },
                ).joinToString(" · ").ifEmpty { "${ports.size} sockets" },
            primary = false,
            enabled = true,
            onClick = { controller.manualScan() },
        )
        Connector()
        Step(
            icon = Icons.Rounded.Bolt,
            label = when {
                flashing > 0 -> "Flashing $flashing…"
                ready > 0 -> "Flash All ($ready)"
                else -> "Flash All"
            },
            caption = "${controller.baud / 1000}k baud · auto-flash ${if (controller.autoFlash) "on" else "off"}",
            primary = true,
            enabled = ready > 0 && build.ok,
            onClick = { controller.flashAll() },
        )
    }
}

@Composable
private fun Step(
    icon: ImageVector,
    label: String,
    caption: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    captionColor: Color? = null,
) {
    Column(Modifier.width(240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(14.dp))
        val shape = RoundedCornerShape(50)
        val buttonMod = Modifier.width(200.dp).height(40.dp)
        if (primary) {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = AppColors.brandDeep,
                    disabledContainerColor = Color.White.copy(alpha = 0.25f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = buttonMod,
            ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        } else {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = buttonMod,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            caption,
            color = captionColor ?: Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(230.dp),
        )
    }
}

/** Line joining two steps, level with the step icons. */
@Composable
private fun Connector() {
    Box(Modifier.padding(top = 23.dp).width(70.dp).height(2.dp).background(Color.White.copy(alpha = 0.3f)))
}

private fun pickBuildDir(controller: FlasherController) {
    val chooser = JFileChooser(File(controller.buildDir).takeIf { it.isDirectory })
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = "Choose ESP-IDF build folder"
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        controller.changeBuildDir(chooser.selectedFile.absolutePath)
    }
}

@Composable
private fun BaudSelector(controller: FlasherController) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(34.dp).clip(RoundedCornerShape(8.dp))
                .border(1.dp, AppColors.panelBorder, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Speed, null, tint = AppColors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("${controller.baud} baud", color = AppColors.textPrimary, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace)
            Icon(Icons.Rounded.ExpandMore, null, tint = AppColors.textFaint, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false },
            modifier = Modifier.background(AppColors.panelBackground)) {
            Esptool.BAUD_RATES.forEach { rate ->
                DropdownMenuItem(
                    text = { Text("$rate baud", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = if (rate == controller.baud) AppColors.accent else AppColors.textPrimary) },
                    onClick = { controller.changeBaud(rate); open = false },
                )
            }
        }
    }
}

@Composable
private fun AutoFlashToggle(controller: FlasherController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Auto-flash", color = AppColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = controller.autoFlash,
            onCheckedChange = { controller.changeAutoFlash(it) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppColors.brand,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = AppColors.panelBackgroundRaised,
                uncheckedBorderColor = AppColors.panelBorder,
                uncheckedThumbColor = AppColors.textFaint,
            ),
            modifier = Modifier.height(24.dp),
        )
    }
}

@Composable
private fun StatusBar(controller: FlasherController) {
    val counts = FlashStatus.entries.associateWith { s -> controller.ports.count { it.status == s } }
    Row(
        Modifier.fillMaxWidth().background(AppColors.panelBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlashStatus.entries.forEach { s ->
            val c = counts[s] ?: 0
            val color = statusColor(s)
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (c > 0) color.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape)
                    .background(if (c > 0) color else color.copy(alpha = 0.35f)))
                Spacer(Modifier.width(6.dp))
                Text(s.label, color = if (c > 0) AppColors.textPrimary else AppColors.textFaint, fontSize = 12.sp)
                Spacer(Modifier.width(5.dp))
                Text("$c", color = if (c > 0) color else AppColors.textFaint, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("This session", color = AppColors.textFaint, fontSize = 12.sp)
        Text("${controller.sessionFlashed} flashed", color = AppColors.success, fontSize = 12.sp,
            fontWeight = FontWeight.Bold)
        if (controller.sessionFailed > 0) {
            Text("${controller.sessionFailed} failed", color = AppColors.danger, fontSize = 12.sp,
                fontWeight = FontWeight.Bold)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.panelBorder))
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.UsbOff, null, tint = AppColors.textFaint, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text("No CP210x programmers found", color = AppColors.textPrimary, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Plug in the jig — ports appear automatically.", color = AppColors.textSecondary, fontSize = 12.sp)
    }
}

/** Dark slate log, as in nRF Connect. */
@Composable
private fun LogPanel(controller: FlasherController) {
    var expanded by remember { mutableStateOf(true) }
    val list = rememberLazyListState()
    LaunchedEffect(controller.log.size) {
        if (controller.log.isNotEmpty()) list.scrollToItem(controller.log.size - 1)
    }
    Column(Modifier.fillMaxWidth().background(AppColors.logBackground)) {
        Row(
            Modifier.fillMaxWidth().background(AppColors.logHeader).clickable { expanded = !expanded }
                .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, null,
                tint = AppColors.logText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("LOG", color = AppColors.logText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp)
            Spacer(Modifier.width(8.dp))
            Text("${controller.log.size}", color = AppColors.logTime, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { controller.clearLog() }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.DeleteSweep, "Clear log", tint = AppColors.logTime,
                    modifier = Modifier.size(16.dp))
            }
        }
        if (expanded) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxWidth().height(130.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
            ) {
                items(controller.log) { line ->
                    Row {
                        Text(line.time, color = AppColors.logTime, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            line.text,
                            color = when (line.level) {
                                LogLevel.Ok -> AppColors.logOk
                                LogLevel.Error -> AppColors.logError
                                LogLevel.Info -> AppColors.logText
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

private fun kb(bytes: Long) = if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / 1048576.0) else "${bytes / 1024} KB"

/** Last two path segments, e.g. `LastOne1\build`. */
private fun shortPath(path: String): String {
    val f = File(path)
    return listOfNotNull(f.parentFile?.name, f.name).filter { it.isNotEmpty() }.joinToString("\\")
}
