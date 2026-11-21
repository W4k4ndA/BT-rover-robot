package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.domain.BluetoothConnectionState
import com.example.domain.BluetoothDeviceUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

/**
 * Gestor de Bluetooth Classic (RFCOMM / SPP) para comunicación bidireccional
 * en tiempo real con el módulo ESP32-WROOM-32.
 *
 * ¿Por qué Bluetooth Classic SPP y no BLE?
 * La librería `BluetoothSerial` del ESP32 expone un puerto serie inalámbrico
 * estándar (Serial Port Profile) con el UUID 00001101-..., ideal para enviar
 * cadenas de control continuas de baja latencia y recibir telemetría por líneas.
 */
class BluetoothClassicManager(private val context: Context) {

    companion object {
        // UUID universal del perfil Serial Port Profile (SPP) usado por ESP32 BluetoothSerial
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // Todas las operaciones bloqueantes de socket (connect/read/write) corren en Dispatchers.IO
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Mutex para serializar escrituras concurrentes (ej. keepalive + botón de paro)
    private val writeMutex = Mutex()

    private var activeSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var readerJob: Job? = null
    private var connectJob: Job? = null
    private var isDiscoveryReceiverRegistered = false

    private val _connectionState =
        MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceUi>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceUi>> = _pairedDevices.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceUi>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceUi>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _incomingLines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingLines: SharedFlow<String> = _incomingLines.asSharedFlow()

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    if (!hasConnectPermission()) return
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java,
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    device?.let { found ->
                        val uiDevice = found.toUiDevice(isPaired = false)
                        val current = _discoveredDevices.value
                        if (current.none { it.address == uiDevice.address } &&
                            _pairedDevices.value.none { it.address == uiDevice.address }
                        ) {
                            _discoveredDevices.value = (current + uiDevice)
                                .sortedByDescending { it.isEspLikely }
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasRequiredPermissions(): Boolean = requiredPermissions().all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Devuelve los permisos necesarios según la versión de Android:
     * - Android 12+ (API 31+): BLUETOOTH_CONNECT y BLUETOOTH_SCAN (sin ubicación gracias a neverForLocation).
     * - Android 11 e inferior: BLUETOOTH, BLUETOOTH_ADMIN y ACCESS_FINE_LOCATION para descubrir dispositivos.
     */
    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        val adapter = bluetoothAdapter ?: return
        if (!hasConnectPermission()) {
            _pairedDevices.value = emptyList()
            return
        }
        try {
            val bonded = adapter.bondedDevices.orEmpty()
                .map { it.toUiDevice(isPaired = true) }
                .sortedWith(
                    compareByDescending<BluetoothDeviceUi> { it.isEspLikely }
                        .thenBy { it.name.lowercase() },
                )
            _pairedDevices.value = bonded
        } catch (_: SecurityException) {
            _pairedDevices.value = emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!hasRequiredPermissions() || !adapter.isEnabled) return

        registerDiscoveryReceiverIfNeeded()
        _discoveredDevices.value = emptyList()

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            val started = adapter.startDiscovery()
            _isScanning.value = started
        } catch (_: SecurityException) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (!hasRequiredPermissions()) return
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (_: SecurityException) {
            // Ignore when permission revoked
        } finally {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(deviceUi: BluetoothDeviceUi) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _connectionState.value = BluetoothConnectionState.Error("Hardware Bluetooth no disponible")
            return
        }
        if (!hasConnectPermission()) {
            _connectionState.value = BluetoothConnectionState.Error("Permiso BLUETOOTH_CONNECT requerido")
            return
        }

        connectJob?.cancel()
        connectJob = scope.launch {
            closeSocketOnly()
            stopDiscovery()

            _connectionState.value = BluetoothConnectionState.Connecting(
                deviceName = deviceUi.name,
                address = deviceUi.address,
            )

            try {
                val device = adapter.getRemoteDevice(deviceUi.address)
                val socket = try {
                    device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
                } catch (primaryError: IOException) {
                    // Respaldo con socket RFCOMM inseguro: algunos módulos ESP32/HC-05
                    // rechazan el emparejamiento cifrado estricto en el primer intento.
                    try {
                        device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
                    } catch (_: IOException) {
                        throw primaryError
                    }
                }

                activeSocket = socket
                outputStream = socket.outputStream
                _connectionState.value = BluetoothConnectionState.Connected(
                    deviceName = deviceUi.name,
                    address = deviceUi.address,
                )

                startReaderLoop(socket)
            } catch (e: SecurityException) {
                closeSocketOnly()
                _connectionState.value = BluetoothConnectionState.Error(
                    "Permiso denegado: ${e.localizedMessage ?: "Bluetooth"}",
                )
            } catch (e: IOException) {
                closeSocketOnly()
                _connectionState.value = BluetoothConnectionState.Error(
                    "No se pudo enlazar con ${deviceUi.name}: verifica que el ESP32 esté encendido",
                )
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        scope.launch {
            closeSocketOnly()
            _connectionState.value = BluetoothConnectionState.Disconnected
        }
    }

    suspend fun sendCommand(command: String): Boolean {
        val stream = outputStream ?: return false
        return writeMutex.withLock {
            try {
                val payload = if (command.endsWith("\n")) command else "$command\n"
                stream.write(payload.toByteArray(Charsets.UTF_8))
                stream.flush()
                true
            } catch (_: IOException) {
                closeSocketOnly()
                _connectionState.value = BluetoothConnectionState.Error(
                    "Conexión perdida con el ESP32",
                )
                false
            }
        }
    }

    private fun startReaderLoop(socket: BluetoothSocket) {
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                while (isActive && socket.isConnected) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) {
                        _incomingLines.tryEmit(line.trim())
                    }
                }
            } catch (_: IOException) {
                if (_connectionState.value is BluetoothConnectionState.Connected) {
                    _connectionState.value = BluetoothConnectionState.Error(
                        "Enlace interrumpido con el carrito",
                    )
                }
            } finally {
                closeSocketOnly()
            }
        }
    }

    private fun closeSocketOnly() {
        readerJob?.cancel()
        readerJob = null
        runCatching { outputStream?.close() }
        runCatching { activeSocket?.close() }
        outputStream = null
        activeSocket = null
    }

    private fun registerDiscoveryReceiverIfNeeded() {
        if (isDiscoveryReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            context,
            discoveryReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        isDiscoveryReceiverRegistered = true
    }

    fun release() {
        stopDiscovery()
        if (isDiscoveryReceiverRegistered) {
            runCatching { context.unregisterReceiver(discoveryReceiver) }
            isDiscoveryReceiverRegistered = false
        }
        connectJob?.cancel()
        closeSocketOnly()
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toUiDevice(isPaired: Boolean): BluetoothDeviceUi {
        val safeName = try {
            name?.takeIf { it.isNotBlank() } ?: "Dispositivo BT"
        } catch (_: SecurityException) {
            "Dispositivo BT"
        }
        val upper = safeName.uppercase()
        val isEsp = upper.contains("ESP") ||
            upper.contains("ROVER") ||
            upper.contains("CAR") ||
            upper.contains("HC-05") ||
            upper.contains("HC-06") ||
            upper.contains("ROBOT")
        return BluetoothDeviceUi(
            name = safeName,
            address = address,
            isPaired = isPaired,
            isEspLikely = isEsp,
        )
    }
}
