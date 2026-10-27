package com.espflasher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.espflasher.model.FlashStatus
import com.espflasher.model.PortState
import com.espflasher.theme.AppColors
import kotlinx.coroutines.delay

fun statusColor(status: FlashStatus): Color = when (status) {
    FlashStatus.Unconnected -> AppColors.textFaint
    FlashStatus.Connected -> AppColors.connected
    FlashStatus.BootMode -> AppColors.warn
    FlashStatus.Flashing -> AppColors.flashing
    FlashStatus.Flashed -> AppColors.success
    FlashStatus.Failed -> AppColors.danger
}

@Composable
fun PortCard(
    state: PortState,
    onFlash: () -> Unit,
    onReboot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent by animateColorAsState(statusColor(state.status))
    val idle = state.status == FlashStatus.Unconnected
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .shadow(if (idle) 0.dp else 2.dp, shape)
            .clip(shape)
            .background(if (idle) AppColors.panelBackgroundRaised else AppColors.panelBackground)
            .border(1.dp, AppColors.panelBorder, shape),
    ) {
        // Status stripe across the top of the card.
        Box(Modifier.fillMaxWidth().height(4.dp).background(if (idle) AppColors.panelBorder else accent))
        Column(Modifier.padding(14.dp)) { CardContent(state, accent, idle, onFlash, onReboot) }
    }
}

@Composable
private fun CardContent(
    state: PortState,
    accent: Color,
    idle: Boolean,
    onFlash: () -> Unit,
    onReboot: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.port,
            color = if (idle) AppColors.textFaint else AppColors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.weight(1f))
        StatusPill(state.status, accent)
    }

    Spacer(Modifier.height(4.dp))
    Text(
        state.mac.ifBlank { "MAC unknown" },
        color = AppColors.textFaint,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
    )

    // Fixed-height body so every card in the grid lines up regardless of status.
    Box(Modifier.fillMaxWidth().height(44.dp).padding(top = 10.dp)) {
        CardBody(state)
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val enabled = !state.busy && !idle
        Button(
            onClick = onFlash,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.accent,
                contentColor = AppColors.onAccent,
                disabledContainerColor = AppColors.panelBorder,
                disabledContentColor = AppColors.textFaint,
            ),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f).height(34.dp),
        ) { ButtonLabel(Icons.Rounded.Bolt, "Flash") }

        OutlinedButton(
            onClick = onReboot,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.panelBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AppColors.textSecondary,
                disabledContentColor = AppColors.textFaint.copy(alpha = 0.5f),
            ),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            modifier = Modifier.weight(1f).height(34.dp),
        ) { ButtonLabel(Icons.Rounded.RestartAlt, "Reboot") }
    }
}

@Composable
private fun CardBody(state: PortState) {
    when (state.status) {
        FlashStatus.Flashing -> {
            // Tick the elapsed timer while this card is flashing.
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(state.startedAt) {
                while (true) { now = System.currentTimeMillis(); delay(100) }
            }
            val shown by animateFloatAsState(state.progress)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressBar(shown, AppColors.flashing)
                Row {
                    Text("${(state.progress * 100).toInt()}%", color = AppColors.textPrimary,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(seconds(now - state.startedAt), color = AppColors.textFaint,
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        FlashStatus.Flashed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = AppColors.success, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Done in ${seconds(state.lastDurationMs)}", color = AppColors.success, fontSize = 12.sp)
        }
        FlashStatus.Failed -> Text(
            state.error.ifBlank { "Flash failed" },
            color = AppColors.danger,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text(
            when (state.status) {
                FlashStatus.BootMode -> "In bootloader — ready to flash"
                FlashStatus.Connected -> "Running firmware"
                else -> "Silent — press EN or BOOT"
            },
            color = AppColors.textFaint,
            fontSize = 12.sp,
        )
    }
}

@Composable
fun StatusPill(status: FlashStatus, color: Color = statusColor(status)) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(status.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color) {
    Box(
        Modifier.fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AppColors.panelBorder)
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

@Composable
fun ButtonLabel(icon: ImageVector, text: String) {
    Icon(icon, null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
}

private fun seconds(ms: Long) = "%.1fs".format(ms.coerceAtLeast(0) / 1000.0)
