package com.example.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.BluetoothConnectionState
import com.example.domain.PowerMode
import com.example.ui.components.BluetoothDeviceSheet
import com.example.ui.components.FuturisticJoystick
import com.example.ui.components.HudTopBar
import com.example.ui.components.MetallicActionButton
import com.example.ui.components.ProtocolTerminalSheet
import com.example.ui.components.RoboticsBackdrop
import com.example.ui.components.TelemetryDeck
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumBezel

/**
 * Pantalla principal del controlador ESP32 Rover.
 * Adapta automáticamente su distribución entre teléfonos verticales (Compact)
 * y tablets / horizontal (Wide >= 660.dp) respetando recortes de cámara e insets.
 */
@Composable
fun RoverControlScreen(
    viewModel: RoverViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // Lanzador en tiempo de ejecución para permisos Bluetooth (Android 11 y Android 12+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshBluetoothEnvironment()
    }

    // Lanzador del diálogo del sistema para encender el adaptador Bluetooth si está apagado
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshBluetoothEnvironment()
    }

    val backdropAccent = when (uiState.connectionState) {
        is BluetoothConnectionState.Connected -> NeonLime
        is BluetoothConnectionState.Error -> NeonCrimson
        else -> NeonCyan
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        RoboticsBackdrop(accentColor = backdropAccent) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                val isWideLayout = maxWidth >= 660.dp

                if (isWideLayout) {
                    // Cabina panorámica para pantallas anchas / tablets / modo horizontal
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .widthIn(max = 520.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            HudTopBar(
                                connectionState = uiState.connectionState,
                                battery = uiState.battery,
                                onBluetoothClick = { viewModel.openBluetoothSheet() },
                            )

                            TelemetryDeck(
                                motorCommand = uiState.motorCommand,
                                maxPwm = uiState.maxPwm,
                                lastRxLine = uiState.lastRxLine,
                                isConnected = uiState.connectionState is BluetoothConnectionState.Connected,
                                isTxActive = uiState.isTxActive,
                                txPacketCount = uiState.txPacketCount,
                            )

                            TacticalControlsBar(
                                selectedPowerMode = uiState.powerMode,
                                onSelectPowerMode = viewModel::setPowerMode,
                                onEmergencyStop = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.triggerEmergencyStop()
                                },
                                onOpenProtocol = viewModel::openProtocolSheet,
                            )
                        }

                        FuturisticJoystick(
                            onPositionChanged = viewModel::onJoystickChanged,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    // Compact Portrait Handheld Cockpit
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 560.dp)
                            .align(Alignment.Center),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HudTopBar(
                            connectionState = uiState.connectionState,
                            battery = uiState.battery,
                            onBluetoothClick = { viewModel.openBluetoothSheet() },
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        TelemetryDeck(
                            motorCommand = uiState.motorCommand,
                            maxPwm = uiState.maxPwm,
                            lastRxLine = uiState.lastRxLine,
                            isConnected = uiState.connectionState is BluetoothConnectionState.Connected,
                            isTxActive = uiState.isTxActive,
                            txPacketCount = uiState.txPacketCount,
                        )

                        FuturisticJoystick(
                            onPositionChanged = viewModel::onJoystickChanged,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )

                        TacticalControlsBar(
                            selectedPowerMode = uiState.powerMode,
                            onSelectPowerMode = viewModel::setPowerMode,
                            onEmergencyStop = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.triggerEmergencyStop()
                            },
                            onOpenProtocol = viewModel::openProtocolSheet,
                        )
                    }
                }
            }
        }
    }

    if (uiState.isBluetoothSheetOpen) {
        BluetoothDeviceSheet(
            uiState = uiState,
            onDismiss = viewModel::closeBluetoothSheet,
            onRequestPermissions = {
                permissionLauncher.launch(viewModel.requiredBluetoothPermissions())
            },
            onRequestEnableBluetooth = {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            },
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onConnectDevice = { device ->
                viewModel.connectToDevice(device)
                viewModel.closeBluetoothSheet()
            },
            onDisconnect = viewModel::disconnectDevice,
        )
    }

    if (uiState.isProtocolSheetOpen) {
        ProtocolTerminalSheet(
            uiState = uiState,
            onDismiss = viewModel::closeProtocolSheet,
            onSelectMaxPwm = viewModel::setMaxPwm,
            onSelectFormat = viewModel::setCommandFormat,
            onSendStopPing = viewModel::triggerEmergencyStop,
        )
    }
}

@Composable
private fun TacticalControlsBar(
    selectedPowerMode: PowerMode,
    onSelectPowerMode: (PowerMode) -> Unit,
    onEmergencyStop: () -> Unit,
    onOpenProtocol: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Power Multiplier Selector Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PowerMode.entries.forEach { mode ->
                val isSelected = mode == selectedPowerMode
                val modeColor = when (mode) {
                    PowerMode.ECO -> NeonLime
                    PowerMode.SPORT -> NeonCyan
                    PowerMode.TURBO -> NeonAmber
                }
                MetallicActionButton(
                    onClick = { onSelectPowerMode(mode) },
                    accentColor = if (isSelected) modeColor else TitaniumBezel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("power_mode_${mode.id}"),
                ) {
                    Text(
                        text = stringResource(mode.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) modeColor else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // Emergency Stop + ESP32 Protocol/Serial Console Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetallicActionButton(
                onClick = onEmergencyStop,
                accentColor = NeonCrimson,
                modifier = Modifier
                    .weight(1.4f)
                    .testTag("emergency_stop_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Dangerous,
                        contentDescription = stringResource(R.string.cd_emergency_stop),
                        tint = NeonCrimson,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.btn_emergency_stop),
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCrimson,
                    )
                }
            }

            MetallicActionButton(
                onClick = onOpenProtocol,
                accentColor = NeonCyan,
                modifier = Modifier
                    .weight(1f)
                    .testTag("protocol_terminal_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = stringResource(R.string.cd_protocol_settings),
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.btn_protocol_terminal),
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyan,
                    )
                }
            }
        }
    }
}
