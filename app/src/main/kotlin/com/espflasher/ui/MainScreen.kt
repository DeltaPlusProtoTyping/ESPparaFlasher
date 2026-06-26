package com.espflasher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.espflasher.flash.FlasherController
import com.espflasher.model.FlashStatus
import com.espflasher.theme.AppColors

@Composable
fun MainScreen(controller: FlasherController) {
    LaunchedEffect(Unit) { controller.startPolling() }

    Column(Modifier.fillMaxSize().background(AppColors.canvasBackground)) {
        Toolbar(controller)
        StatusLegend(controller)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (controller.ports.isEmpty()) {
                Text(
                    "Scanning for ESP32 programmers...",
                    color = AppColors.textFaint,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                // Fixed rows of 4 (8 ports -> 2 rows). Cards are a fixed width so the
                // layout never reflows with window size; the surrounding padding just
                // grows/shrinks around the centered group.
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 28.dp),
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
                                    modifier = Modifier.width(210.dp),
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

@Composable
private fun Toolbar(controller: FlasherController) {
    Row(
        Modifier.fillMaxWidth().background(AppColors.panelBackground).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "ESP32 Flasher",
            color = AppColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))

        Text("Build:", color = AppColors.textSecondary, fontSize = 12.sp)
        Box(
            Modifier
                .weight(1f)
                .height(34.dp)
                .background(AppColors.panelBackgroundRaised, RoundedCornerShape(6.dp))
                .border(1.dp, AppColors.panelBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = controller.buildDir,
                onValueChange = { controller.buildDir = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.textPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(AppColors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ToolbarButton("⚡ Flash All", AppColors.success) { controller.flashAll() }
        ToolbarButton("↻ Reboot All", AppColors.accent) { controller.rebootAll() }
        ToolbarButton("🔍 Scan", AppColors.warn) { controller.manualScan() }
    }
}

@Composable
private fun ToolbarButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = AppColors.onAccent),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun StatusLegend(controller: FlasherController) {
    val counts = FlashStatus.entries.associateWith { s -> controller.ports.count { it.status == s } }
    Row(
        Modifier.fillMaxWidth().background(AppColors.panelBackground)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlashStatus.entries.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(10.dp).height(10.dp)
                    .background(statusColor(s), RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(5.dp))
                Text("${s.label} (${counts[s] ?: 0})",
                    color = AppColors.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun LogPanel(controller: FlasherController) {
    val scroll = rememberScrollState()
    LaunchedEffect(controller.log.size) { scroll.scrollTo(scroll.maxValue) }
    Column(
        Modifier.fillMaxWidth().height(120.dp)
            .background(Color(0xFF101216))
            .padding(10.dp)
            .verticalScroll(scroll),
    ) {
        controller.log.forEach { line ->
            Text(line, color = AppColors.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
