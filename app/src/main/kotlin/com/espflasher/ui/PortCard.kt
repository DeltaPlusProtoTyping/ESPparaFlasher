package com.espflasher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.espflasher.model.FlashStatus
import com.espflasher.model.PortState
import com.espflasher.theme.AppColors

fun statusColor(status: FlashStatus): Color = when (status) {
    FlashStatus.Unconnected -> AppColors.textFaint
    FlashStatus.Connected -> AppColors.accent
    FlashStatus.BootMode -> AppColors.warn
    FlashStatus.Flashing -> AppColors.purple
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
    val accent = statusColor(state.status)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.panelBackgroundRaised)
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(12.dp).clip(CircleShape).background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                state.port,
                color = AppColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        Text(state.status.label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)

        Text(
            state.mac.ifBlank { "—" },
            color = AppColors.textFaint,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )

        // Progress bar only meaningful while flashing.
        if (state.status == FlashStatus.Flashing) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = AppColors.purple,
                trackColor = AppColors.panelBorder,
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }

        if (state.detail.isNotBlank()) {
            Text(
                state.detail.take(40),
                color = AppColors.textFaint,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = onFlash,
                enabled = !state.busy && state.status != FlashStatus.Unconnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = AppColors.onAccent,
                ),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Flash", fontSize = 12.sp, maxLines = 1, softWrap = false)
            }

            OutlinedButton(
                onClick = onReboot,
                enabled = !state.busy && state.status != FlashStatus.Unconnected,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Reboot", fontSize = 12.sp, color = AppColors.textSecondary,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}
