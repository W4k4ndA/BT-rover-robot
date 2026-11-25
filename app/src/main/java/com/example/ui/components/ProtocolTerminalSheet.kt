package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CommandFormatPreset
import com.example.ui.RoverUiState
import com.example.ui.theme.ChassisDark
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TitaniumBezel

private const val ESP32_SAMPLE_SKETCH = """#include <BluetoothSerial.h>
BluetoothSerial SerialBT;
// Dirección (digital) y velocidad (2 canales LEDC @ 2000 Hz, 10 bits)
const int IN1 = 13, IN2 = 12, IN3 = 14, IN4 = 27;
const int ENA = 26, ENB = 25;

void setup() {
  SerialBT.begin("ESP32_ROVER");
  pinMode(IN1, OUTPUT); pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT); pinMode(IN4, OUTPUT);
  ledcSetup(0, 2000, 10); ledcAttachPin(ENA, 0);
  ledcSetup(1, 2000, 10); ledcAttachPin(ENB, 1);
}

void loop() {
  if (SerialBT.available()) {
    String cmd = SerialBT.readStringUntil('\n');
    int lIdx = cmd.indexOf("L:"), rIdx = cmd.indexOf(",R:");
    if (lIdx >= 0 && rIdx > lIdx) {
      int l = cmd.substring(lIdx + 2, rIdx).toInt();
      int r = cmd.substring(rIdx + 3).toInt();
      // IN3/IN4 + ENB (ch 1) para izq; IN1/IN2 + ENA (ch 0) para der
    }
  }
  // Telemetría cada 1s: SerialBT.printf("BAT:%d,V:%.2f\n", pct, v);
}"""

/**
 * Consola técnica modal del protocolo ESP32:
 * Permite ajustar la escala PWM (±255 o ±100), elegir el formato de trama,
 * inspeccionar el registro TX/RX en vivo y copiar el código base para PlatformIO/ESP32.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolTerminalSheet(
    uiState: RoverUiState,
    onDismiss: () -> Unit,
    onSelectMaxPwm: (Int) -> Unit,
    onSelectFormat: (CommandFormatPreset) -> Unit,
    onToggleInvertLeft: (Boolean) -> Unit,
    onToggleInvertRight: (Boolean) -> Unit,
    onSelectSwapLeftRight: (Boolean) -> Unit,
    onSendStopPing: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.protocol_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("protocol_sheet_close"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close_sheet),
                        tint = TextSecondary,
                    )
                }
            }

            // PWM Max Selector (255 vs 100)
            Text(
                text = stringResource(R.string.protocol_pwm_max),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(255, 100).forEach { pwmOption ->
                    val selected = uiState.maxPwm == pwmOption
                    MetallicActionButton(
                        onClick = { onSelectMaxPwm(pwmOption) },
                        accentColor = if (selected) NeonCyan else TitaniumBezel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pwm_option_$pwmOption"),
                    ) {
                        Text(
                            text = "±$pwmOption",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) NeonCyan else TextSecondary,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            }

            // Command Format Selector
            Text(
                text = stringResource(R.string.protocol_format_label),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandFormatPreset.entries.forEach { preset ->
                    val selected = uiState.commandFormat == preset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(GunmetalElevated)
                            .border(
                                width = 1.dp,
                                color = if (selected) NeonCyan else TitaniumBezel,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelectFormat(preset) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("format_preset_${preset.id}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = preset.displayPattern,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) NeonCyan else TextPrimary,
                        )
                        Text(
                            text = preset.sampleOutput,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.protocol_rx_format_hint),
                style = MaterialTheme.typography.bodySmall,
                color = NeonLime,
            )

            // Motor Software Calibration Section (Inversion & Left/Right Channel Swap)
            Text(
                text = stringResource(R.string.protocol_motor_calibration_title),
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
            )
            Text(
                text = stringResource(R.string.protocol_motor_mapping_label),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(false, true).forEach { isSwappedOption ->
                    val selected = uiState.swapLeftRight == isSwappedOption
                    val accent = if (selected) {
                        if (isSwappedOption) NeonAmber else NeonCyan
                    } else {
                        TitaniumBezel
                    }
                    MetallicActionButton(
                        onClick = { onSelectSwapLeftRight(isSwappedOption) },
                        accentColor = accent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(if (isSwappedOption) "motor_mapping_swapped" else "motor_mapping_normal"),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = null,
                                    tint = if (selected) accent else TextSecondary,
                                )
                                Text(
                                    text = stringResource(
                                        if (isSwappedOption) R.string.protocol_mapping_swapped
                                        else R.string.protocol_mapping_normal,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) accent else TextPrimary,
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (isSwappedOption) R.string.protocol_mapping_swapped_sub
                                    else R.string.protocol_mapping_normal_sub,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.protocol_motor_mapping_hint),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )

            MotorInvertToggleRow(
                title = stringResource(R.string.protocol_invert_left_title),
                subtitle = stringResource(R.string.protocol_invert_left_desc),
                checked = uiState.invertLeftMotor,
                onCheckedChange = onToggleInvertLeft,
                testTagName = "switch_invert_left",
            )
            MotorInvertToggleRow(
                title = stringResource(R.string.protocol_invert_right_title),
                subtitle = stringResource(R.string.protocol_invert_right_desc),
                checked = uiState.invertRightMotor,
                onCheckedChange = onToggleInvertRight,
                testTagName = "switch_invert_right",
            )

            // Live Serial Console
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.protocol_log_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                MetallicActionButton(
                    onClick = onSendStopPing,
                    accentColor = NeonCrimson,
                    modifier = Modifier.testTag("protocol_ping_button"),
                ) {
                    Text(
                        text = stringResource(R.string.protocol_test_ping),
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCrimson,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, TitaniumBezel, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                if (uiState.serialLogs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.rx_none),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(uiState.serialLogs, key = { it.id }) { log ->
                            Text(
                                text = "[${log.timestamp}] ${if (log.isTx) "TX >" else "RX <"} ${log.payload}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (log.isTx) NeonCyan else NeonLime,
                            )
                        }
                    }
                }
            }

            // ESP32-WROOM-32 Reference Code Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.protocol_arduino_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(ESP32_SAMPLE_SKETCH)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("copy_esp32_sketch"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        tint = NeonCyan,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBg)
                    .border(1.dp, TitaniumBezel, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = ESP32_SAMPLE_SKETCH,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun MotorInvertToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTagName: String,
) {
    val borderColor = if (checked) NeonAmber else TitaniumBezel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .background(GunmetalElevated)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("${testTagName}_row"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = null,
                tint = if (checked) NeonAmber else TextSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (checked) NeonAmber else TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTagName),
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBg,
                checkedTrackColor = NeonAmber,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = ChassisDark,
                uncheckedBorderColor = TitaniumBezel,
            ),
        )
    }
}
