package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.MotorCommand
import com.example.ui.theme.ChassisDark
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumBezel
import kotlin.math.abs

@Composable
fun TelemetryDeck(
    motorCommand: MotorCommand,
    maxPwm: Int,
    lastRxLine: String?,
    isConnected: Boolean,
    isTxActive: Boolean,
    txPacketCount: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MotorChannelCard(
                label = stringResource(R.string.motor_left_label),
                speed = motorCommand.leftSpeed,
                maxPwm = maxPwm,
                modifier = Modifier
                    .weight(1f)
                    .testTag("motor_left_card"),
            )
            MotorChannelCard(
                label = stringResource(R.string.motor_right_label),
                speed = motorCommand.rightSpeed,
                maxPwm = maxPwm,
                modifier = Modifier
                    .weight(1f)
                    .testTag("motor_right_card"),
            )
        }

        // Live TX String & RX Stream Monitor
        MetallicPanel(
            glowColor = if (isTxActive) NeonCyan else TitaniumBezel,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tx_console_panel"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    StatusLed(
                        color = when {
                            isTxActive && isConnected -> NeonLime
                            isConnected -> NeonCyan
                            else -> TextMuted
                        },
                        size = 8.dp,
                    )
                    Text(
                        text = stringResource(R.string.tx_console_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Text(
                        text = motorCommand.formattedString,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = NeonCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = if (lastRxLine != null) {
                        "RX: $lastRxLine"
                    } else if (isConnected) {
                        "PKT #$txPacketCount"
                    } else {
                        "OFFLINE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lastRxLine != null) NeonLime else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MotorChannelCard(
    label: String,
    speed: Int,
    maxPwm: Int,
    modifier: Modifier = Modifier,
) {
    val accent = when {
        speed > 0 -> NeonCyan
        speed < 0 -> NeonCrimson
        else -> TitaniumBezel
    }
    val dirLabel = when {
        speed > 0 -> stringResource(R.string.motor_dir_fwd)
        speed < 0 -> stringResource(R.string.motor_dir_rev)
        else -> stringResource(R.string.motor_dir_idle)
    }
    val signedSpeed = if (speed > 0) "+$speed" else "$speed"
    val ratio by animateFloatAsState(
        targetValue = (abs(speed).toFloat() / maxPwm.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 70),
        label = "motor_ratio",
    )

    MetallicPanel(
        glowColor = accent,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Text(
                    text = dirLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (speed == 0) TextMuted else accent,
                )
            }

            Text(
                text = signedSpeed,
                style = MaterialTheme.typography.titleLarge,
                color = if (speed == 0) TextSecondary else TextPrimary,
            )

            // Neon thrust bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ChassisDark)
                    .border(0.6.dp, TitaniumBezel, RoundedCornerShape(3.dp)),
            ) {
                if (ratio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(accent),
                    )
                }
            }
        }
    }
}
