package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.PrecisionManufacturing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.BatteryTelemetry
import com.example.domain.BluetoothConnectionState
import com.example.ui.theme.ChassisDark
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumBezel
import java.util.Locale

@Composable
fun HudTopBar(
    connectionState: BluetoothConnectionState,
    battery: BatteryTelemetry,
    onBluetoothClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Brand & Bluetooth Connection Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChassisDark)
                        .border(1.2.dp, NeonCyan.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PrecisionManufacturing,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.brand_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            BluetoothConnectButton(
                connectionState = connectionState,
                onClick = onBluetoothClick,
            )
        }

        // Real-Time Battery HUD Capsule (Rover Telemetry + Controller Battery)
        RealTimeBatteryHud(
            battery = battery,
            isConnected = connectionState is BluetoothConnectionState.Connected,
        )
    }
}

@Composable
fun BluetoothConnectButton(
    connectionState: BluetoothConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bt_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bt_pulse_alpha",
    )

    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            is BluetoothConnectionState.Connected -> NeonLime
            is BluetoothConnectionState.Connecting -> NeonCyan
            is BluetoothConnectionState.Error -> NeonCrimson
            BluetoothConnectionState.Disconnected -> NeonAmber
        },
        label = "bt_color",
    )

    val labelText = when (connectionState) {
        is BluetoothConnectionState.Connected -> connectionState.deviceName
        is BluetoothConnectionState.Connecting -> stringResource(R.string.bt_status_connecting)
        is BluetoothConnectionState.Error -> stringResource(R.string.bt_connect_button)
        BluetoothConnectionState.Disconnected -> stringResource(R.string.bt_connect_button)
    }

    val btIcon = when (connectionState) {
        is BluetoothConnectionState.Connected -> Icons.Rounded.BluetoothConnected
        is BluetoothConnectionState.Connecting -> Icons.Rounded.BluetoothSearching
        else -> Icons.Rounded.Bluetooth
    }

    MetallicActionButton(
        onClick = onClick,
        accentColor = statusColor,
        modifier = modifier.testTag("bluetooth_connect_button"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusLed(
                color = statusColor,
                size = 8.dp,
                modifier = if (connectionState is BluetoothConnectionState.Connecting) {
                    Modifier.alpha(pulseAlpha)
                } else {
                    Modifier
                },
            )
            Icon(
                imageVector = btIcon,
                contentDescription = stringResource(R.string.cd_bluetooth_icon),
                tint = statusColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = labelText.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RealTimeBatteryHud(
    battery: BatteryTelemetry,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val roverPct = battery.roverPercent
    val roverColor = when {
        !isConnected || roverPct == null -> TextMuted
        roverPct <= 20 -> NeonCrimson
        roverPct <= 45 -> NeonAmber
        else -> NeonLime
    }

    val phoneColor = when {
        battery.phonePercent <= 20 -> NeonCrimson
        battery.phonePercent <= 40 -> NeonAmber
        else -> NeonCyan
    }

    MetallicPanel(
        glowColor = if (roverPct != null && isConnected) roverColor else TitaniumBezel,
        modifier = modifier
            .fillMaxWidth()
            .testTag("battery_hud_panel"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Rover Real-Time Battery Gauge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ElectricBolt,
                    contentDescription = stringResource(R.string.cd_battery_icon),
                    tint = roverColor,
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.battery_rover_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Text(
                            text = when {
                                !isConnected -> stringResource(R.string.battery_waiting)
                                roverPct != null && battery.roverVoltage != null ->
                                    String.format(Locale.US, "%d%% · %.2fV", roverPct, battery.roverVoltage)
                                roverPct != null -> "$roverPct%"
                                else -> stringResource(R.string.battery_no_telemetry)
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (roverPct != null && isConnected) roverColor else TextMuted,
                            maxLines = 1,
                        )
                    }
                    SegmentedNeonBatteryBar(
                        percent = if (isConnected) (roverPct ?: 0) else 0,
                        activeColor = roverColor,
                        isActive = isConnected && roverPct != null,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(TitaniumBezel),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Right: Live Phone Controller Battery
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (battery.isPhoneCharging) {
                        Icon(
                            imageVector = Icons.Rounded.BatteryChargingFull,
                            contentDescription = stringResource(R.string.battery_charging),
                            tint = NeonLime,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.battery_controller_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    text = "${battery.phonePercent}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = phoneColor,
                )
            }
        }
    }
}

@Composable
private fun SegmentedNeonBatteryBar(
    percent: Int,
    activeColor: Color,
    isActive: Boolean,
    segments: Int = 10,
) {
    val filledSegments = if (!isActive) 0 else ((percent / 100f) * segments).toInt().coerceIn(0, segments)
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(segments) { index ->
            val lit = index < filledSegments
            Box(
                modifier = Modifier
                    .width(11.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (lit) activeColor else ChassisDark,
                    )
                    .border(
                        width = 0.7.dp,
                        color = if (lit) activeColor else TitaniumBezel,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}
