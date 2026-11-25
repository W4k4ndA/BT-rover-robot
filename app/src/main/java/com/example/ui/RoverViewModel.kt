package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BluetoothClassicManager
import com.example.data.DeviceBatteryMonitor
import com.example.data.PreferencesRepository
import com.example.domain.BatteryTelemetry
import com.example.domain.BluetoothConnectionState
import com.example.domain.BluetoothDeviceUi
import com.example.domain.CommandFormatPreset
import com.example.domain.DifferentialDriveCalculator
import com.example.domain.MotorCommand
import com.example.domain.PowerMode
import com.example.domain.SerialLogEntry
import com.example.domain.TelemetryParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RoverUiState(
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.Disconnected,
    val isBluetoothSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val hasBluetoothPermissions: Boolean = false,
    val pairedDevices: List<BluetoothDeviceUi> = emptyList(),
    val discoveredDevices: List<BluetoothDeviceUi> = emptyList(),
    val isScanning: Boolean = false,
    val lastDeviceAddress: String? = null,
    val joyX: Float = 0f,
    val joyY: Float = 0f,
    val motorCommand: MotorCommand = MotorCommand.stop(CommandFormatPreset.L_R_COLON),
    val maxPwm: Int = 255,
    val commandFormat: CommandFormatPreset = CommandFormatPreset.L_R_COLON,
    val powerMode: PowerMode = PowerMode.TURBO,
    val battery: BatteryTelemetry = BatteryTelemetry(),
    val lastRxLine: String? = null,
    val serialLogs: List<SerialLogEntry> = emptyList(),
    val txPacketCount: Long = 0L,
    val isTxActive: Boolean = false,
    val isBluetoothSheetOpen: Boolean = false,
    val isProtocolSheetOpen: Boolean = false,
    val invertLeftMotor: Boolean = false,
    val invertRightMotor: Boolean = false,
    val swapLeftRight: Boolean = false,
)

/**
 * ViewModel principal que coordina:
 * 1. El cálculo diferencial del joystick y la transmisión throttled (~22 Hz) por Bluetooth Classic.
 * 2. Un keepalive periódico (250 ms) mientras el joystick está activo para mantener vivo el watchdog del ESP32.
 * 3. La lectura en tiempo real de la batería del rover (ESP32) y del mando (teléfono).
 * 4. La persistencia de preferencias de protocolo mediante DataStore.
 */
class RoverViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager = BluetoothClassicManager(application.applicationContext)
    private val batteryMonitor = DeviceBatteryMonitor(application.applicationContext)
    private val preferencesRepository = PreferencesRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        RoverUiState(
            isBluetoothSupported = bluetoothManager.isBluetoothSupported(),
            isBluetoothEnabled = bluetoothManager.isBluetoothEnabled(),
            hasBluetoothPermissions = bluetoothManager.hasRequiredPermissions(),
        ),
    )
    val uiState: StateFlow<RoverUiState> = _uiState.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var logSequence = 0L
    private var lastSentCommand: String = ""
    private var lastSendTimeMs: Long = 0L

    companion object {
        // Limitamos la tasa de envío a ~22 Hz (cada 45 ms) para no saturar el buffer SPP del ESP32
        private const val TX_THROTTLE_MS = 45L
        // Si el usuario mantiene el joystick fijo en marcha, reenviamos cada 250 ms para alimentar
        // el temporizador de seguridad (watchdog de 1000 ms) en el firmware del ESP32.
        private const val TX_KEEPALIVE_MS = 250L
    }

    init {
        batteryMonitor.start()
        refreshBluetoothEnvironment()

        // Bucle keepalive mientras el robot esté recibiendo tracción continua
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(TX_KEEPALIVE_MS)
                val state = _uiState.value
                if (state.connectionState is BluetoothConnectionState.Connected && state.isTxActive) {
                    val now = System.currentTimeMillis()
                    if ((now - lastSendTimeMs) >= TX_KEEPALIVE_MS) {
                        lastSendTimeMs = now
                        transmitCommandNow(state.motorCommand.formattedString, forceLog = false)
                    }
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _uiState.update { state ->
                    val updatedCmd = DifferentialDriveCalculator.calculate(
                        joyX = state.joyX,
                        joyY = state.joyY,
                        maxPwm = prefs.maxPwm,
                        powerMultiplier = prefs.powerMode.multiplier,
                        format = prefs.commandFormat,
                        invertLeftMotor = prefs.invertLeftMotor,
                        invertRightMotor = prefs.invertRightMotor,
                        swapLeftRight = prefs.swapLeftRight,
                    )
                    state.copy(
                        maxPwm = prefs.maxPwm,
                        commandFormat = prefs.commandFormat,
                        powerMode = prefs.powerMode,
                        lastDeviceAddress = prefs.lastDeviceAddress,
                        invertLeftMotor = prefs.invertLeftMotor,
                        invertRightMotor = prefs.invertRightMotor,
                        swapLeftRight = prefs.swapLeftRight,
                        motorCommand = updatedCmd,
                    )
                }
            }
        }

        viewModelScope.launch {
            bluetoothManager.connectionState.collect { connState ->
                _uiState.update { state ->
                    val clearedBattery = if (connState !is BluetoothConnectionState.Connected) {
                        state.battery.copy(
                            roverPercent = null,
                            roverVoltage = null,
                            lastRoverUpdateMs = null,
                        )
                    } else {
                        state.battery
                    }
                    state.copy(
                        connectionState = connState,
                        battery = clearedBattery,
                        lastRxLine = if (connState is BluetoothConnectionState.Connected) state.lastRxLine else null,
                    )
                }
                if (connState is BluetoothConnectionState.Connected) {
                    preferencesRepository.setLastDeviceAddress(connState.address)
                    // Send initial neutral stop frame upon connection
                    transmitCommandNow(_uiState.value.commandFormat.format(0, 0), forceLog = true)
                }
            }
        }

        viewModelScope.launch {
            bluetoothManager.pairedDevices.collect { paired ->
                _uiState.update { it.copy(pairedDevices = paired) }
            }
        }

        viewModelScope.launch {
            bluetoothManager.discoveredDevices.collect { discovered ->
                _uiState.update { it.copy(discoveredDevices = discovered) }
            }
        }

        viewModelScope.launch {
            bluetoothManager.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }

        viewModelScope.launch {
            batteryMonitor.batteryState.collect { phoneBat ->
                _uiState.update { state ->
                    state.copy(
                        battery = state.battery.copy(
                            phonePercent = phoneBat.percent,
                            isPhoneCharging = phoneBat.isCharging,
                        ),
                    )
                }
            }
        }

        viewModelScope.launch {
            bluetoothManager.incomingLines.collect { line ->
                appendLog(isTx = false, payload = line)
                val parsed = TelemetryParser.parseBatteryLine(line)
                _uiState.update { state ->
                    state.copy(
                        lastRxLine = line,
                        battery = if (parsed != null) {
                            state.battery.copy(
                                roverPercent = parsed.percent ?: state.battery.roverPercent,
                                roverVoltage = parsed.voltage ?: state.battery.roverVoltage,
                                lastRoverUpdateMs = System.currentTimeMillis(),
                            )
                        } else {
                            state.battery
                        },
                    )
                }
            }
        }
    }

    fun requiredBluetoothPermissions(): Array<String> = bluetoothManager.requiredPermissions()

    fun refreshBluetoothEnvironment() {
        val supported = bluetoothManager.isBluetoothSupported()
        val enabled = bluetoothManager.isBluetoothEnabled()
        val hasPerms = bluetoothManager.hasRequiredPermissions()
        _uiState.update {
            it.copy(
                isBluetoothSupported = supported,
                isBluetoothEnabled = enabled,
                hasBluetoothPermissions = hasPerms,
            )
        }
        if (hasPerms) {
            bluetoothManager.refreshPairedDevices()
        }
    }

    fun openBluetoothSheet() {
        refreshBluetoothEnvironment()
        _uiState.update { it.copy(isBluetoothSheetOpen = true) }
    }

    fun closeBluetoothSheet() {
        bluetoothManager.stopDiscovery()
        _uiState.update { it.copy(isBluetoothSheetOpen = false) }
    }

    fun openProtocolSheet() {
        _uiState.update { it.copy(isProtocolSheetOpen = true) }
    }

    fun closeProtocolSheet() {
        _uiState.update { it.copy(isProtocolSheetOpen = false) }
    }

    fun startScan() {
        refreshBluetoothEnvironment()
        bluetoothManager.startDiscovery()
    }

    fun stopScan() {
        bluetoothManager.stopDiscovery()
    }

    fun connectToDevice(device: BluetoothDeviceUi) {
        bluetoothManager.connect(device)
    }

    fun disconnectDevice() {
        viewModelScope.launch {
            if (_uiState.value.connectionState is BluetoothConnectionState.Connected) {
                bluetoothManager.sendCommand(_uiState.value.commandFormat.format(0, 0))
            }
            bluetoothManager.disconnect()
        }
    }

    fun onJoystickChanged(joyX: Float, joyY: Float, isReleased: Boolean) {
        val current = _uiState.value
        val nextX = if (isReleased) 0f else joyX
        val nextY = if (isReleased) 0f else joyY

        val command = DifferentialDriveCalculator.calculate(
            joyX = nextX,
            joyY = nextY,
            maxPwm = current.maxPwm,
            powerMultiplier = current.powerMode.multiplier,
            format = current.commandFormat,
            invertLeftMotor = current.invertLeftMotor,
            invertRightMotor = current.invertRightMotor,
            swapLeftRight = current.swapLeftRight,
        )

        _uiState.update {
            it.copy(
                joyX = nextX,
                joyY = nextY,
                motorCommand = command,
                isTxActive = !isReleased && (command.leftSpeed != 0 || command.rightSpeed != 0),
            )
        }

        if (current.connectionState !is BluetoothConnectionState.Connected) return

        val now = System.currentTimeMillis()
        val isStopCommand = command.leftSpeed == 0 && command.rightSpeed == 0
        val shouldTransmit = if (isReleased || isStopCommand) {
            lastSentCommand != command.formattedString || isReleased
        } else {
            (now - lastSendTimeMs) >= TX_THROTTLE_MS && command.formattedString != lastSentCommand
        }

        if (shouldTransmit) {
            lastSendTimeMs = now
            transmitCommandNow(command.formattedString, forceLog = isReleased)
        }
    }

    fun triggerEmergencyStop() {
        val stopCmd = MotorCommand.stop(_uiState.value.commandFormat)
        _uiState.update {
            it.copy(
                joyX = 0f,
                joyY = 0f,
                motorCommand = stopCmd,
                isTxActive = false,
            )
        }
        transmitCommandNow(stopCmd.formattedString, forceLog = true)
    }

    fun setPowerMode(mode: PowerMode) {
        viewModelScope.launch {
            preferencesRepository.setPowerMode(mode)
        }
    }

    fun setMaxPwm(maxPwm: Int) {
        viewModelScope.launch {
            preferencesRepository.setMaxPwm(maxPwm)
        }
    }

    fun setCommandFormat(format: CommandFormatPreset) {
        viewModelScope.launch {
            preferencesRepository.setCommandFormat(format)
        }
    }

    fun setInvertLeftMotor(invert: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setInvertLeftMotor(invert)
        }
    }

    fun setInvertRightMotor(invert: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setInvertRightMotor(invert)
        }
    }

    fun setSwapLeftRight(swap: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSwapLeftRight(swap)
        }
    }

    private fun transmitCommandNow(payload: String, forceLog: Boolean) {
        lastSentCommand = payload
        if (_uiState.value.connectionState !is BluetoothConnectionState.Connected) return
        viewModelScope.launch {
            val sent = bluetoothManager.sendCommand(payload)
            if (sent) {
                _uiState.update { it.copy(txPacketCount = it.txPacketCount + 1) }
                if (forceLog || _uiState.value.serialLogs.firstOrNull()?.payload != payload) {
                    appendLog(isTx = true, payload = payload)
                }
            }
        }
    }

    private fun appendLog(isTx: Boolean, payload: String) {
        val entry = SerialLogEntry(
            id = ++logSequence,
            timestamp = timeFormatter.format(Date()),
            isTx = isTx,
            payload = payload,
        )
        _uiState.update { state ->
            state.copy(serialLogs = (listOf(entry) + state.serialLogs).take(40))
        }
    }

    override fun onCleared() {
        super.onCleared()
        batteryMonitor.stop()
        bluetoothManager.release()
    }
}
