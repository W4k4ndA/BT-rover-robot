package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.BluetoothConnectionState
import com.example.domain.BluetoothDeviceUi
import com.example.ui.RoverUiState
import com.example.ui.theme.ChassisDark
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumBezel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDeviceSheet(
    uiState: RoverUiState,
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (BluetoothDeviceUi) -> Unit,
    onDisconnect: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChassisDark,
        contentColor = TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Sheet Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bt_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                    )
                    Text(
                        text = stringResource(R.string.bt_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bt_sheet_close"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close_sheet),
                        tint = TextSecondary,
                    )
                }
            }

            // Active Connection / Error Banner
            when (val conn = uiState.connectionState) {
                is BluetoothConnectionState.Connected -> {
                    MetallicPanel(glowColor = NeonLime, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${stringResource(R.string.bt_status_connected)}: ${conn.deviceName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NeonLime,
                                )
                                Text(
                                    text = conn.address,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                            }
                            MetallicActionButton(
                                onClick = onDisconnect,
                                accentColor = NeonCrimson,
                                modifier = Modifier.testTag("bt_disconnect_button"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LinkOff,
                                        contentDescription = null,
                                        tint = NeonCrimson,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.bt_disconnect_button),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCrimson,
                                    )
                                }
                            }
                        }
                    }
                }

                is BluetoothConnectionState.Connecting -> {
                    MetallicPanel(glowColor = NeonCyan, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "${stringResource(R.string.bt_status_connecting)} ${conn.deviceName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonCyan,
                            )
                        }
                    }
                }

                is BluetoothConnectionState.Error -> {
                    MetallicPanel(glowColor = NeonCrimson, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = conn.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonCrimson,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                BluetoothConnectionState.Disconnected -> Unit
            }

            // Hardware / Permissions / Adapter Gates
            if (!uiState.isBluetoothSupported) {
                MetallicPanel(glowColor = NeonAmber, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BluetoothDisabled,
                            contentDescription = null,
                            tint = NeonAmber,
                        )
                        Text(
                            text = stringResource(R.string.bt_unsupported),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                    }
                }
            } else if (!uiState.hasBluetoothPermissions) {
                MetallicPanel(glowColor = NeonAmber, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bt_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        MetallicActionButton(
                            onClick = onRequestPermissions,
                            accentColor = NeonCyan,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bt_grant_permissions_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.bt_grant_permission),
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonCyan,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            } else if (!uiState.isBluetoothEnabled) {
                MetallicPanel(glowColor = NeonAmber, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bt_enable_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        MetallicActionButton(
                            onClick = onRequestEnableBluetooth,
                            accentColor = NeonLime,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bt_enable_adapter_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.bt_enable_action),
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonLime,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            } else {
                // Scan Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bt_paired_section),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    MetallicActionButton(
                        onClick = { if (uiState.isScanning) onStopScan() else onStartScan() },
                        accentColor = if (uiState.isScanning) NeonAmber else NeonCyan,
                        modifier = Modifier.testTag("bt_scan_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (uiState.isScanning) {
                                CircularProgressIndicator(
                                    color = NeonAmber,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Radar,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (uiState.isScanning) R.string.bt_scanning else R.string.bt_scan_action,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isScanning) NeonAmber else NeonCyan,
                            )
                        }
                    }
                }

                // Device List (Paired + Discovered)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.pairedDevices.isEmpty() && uiState.discoveredDevices.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.bt_empty_paired),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        items(uiState.pairedDevices, key = { "paired_${it.address}" }) { device ->
                            BluetoothDeviceRow(
                                device = device,
                                isLastUsed = device.address == uiState.lastDeviceAddress,
                                onClick = { onConnectDevice(device) },
                            )
                        }
                        if (uiState.discoveredDevices.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.bt_discovered_section),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                            }
                            items(uiState.discoveredDevices, key = { "disc_${it.address}" }) { device ->
                                BluetoothDeviceRow(
                                    device = device,
                                    isLastUsed = false,
                                    onClick = { onConnectDevice(device) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothDeviceRow(
    device: BluetoothDeviceUi,
    isLastUsed: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (device.isEspLikely) NeonCyan else TitaniumBezel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .background(GunmetalElevated)
            .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("bt_device_${device.address}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = if (device.isEspLikely) Icons.Rounded.Memory else Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = if (device.isEspLikely) NeonCyan else TextSecondary,
                modifier = Modifier.size(22.dp),
            )
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (device.isEspLikely) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .border(0.8.dp, NeonCyan, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.bt_esp32_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                    )
                }
            }
            if (isLastUsed) {
                StatusLed(color = NeonLime, size = 8.dp)
            }
        }
    }
}
