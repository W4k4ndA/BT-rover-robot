package com.example.domain

import androidx.annotation.StringRes
import com.example.R

enum class CommandFormatPreset(
    val id: String,
    val displayPattern: String,
    val sampleOutput: String,
) {
    L_R_COLON("L_R_COLON", "L:<izq>,R:<der>", "L:180,R:240"),
    M_COMMA("M_COMMA", "M:<izq>,<der>", "M:180,240"),
    CSV("CSV", "<izq>,<der>", "180,240"),
    SPACE_L_R("SPACE_L_R", "L<izq> R<der>", "L180 R240");

    fun format(left: Int, right: Int): String = when (this) {
        L_R_COLON -> "L:$left,R:$right"
        M_COMMA -> "M:$left,$right"
        CSV -> "$left,$right"
        SPACE_L_R -> "L$left R$right"
    }

    companion object {
        fun fromId(id: String?): CommandFormatPreset =
            entries.firstOrNull { it.id == id } ?: L_R_COLON
    }
}

enum class PowerMode(
    val id: String,
    @StringRes val labelRes: Int,
    val multiplier: Float,
) {
    ECO("ECO", R.string.power_mode_eco, 0.50f),
    SPORT("SPORT", R.string.power_mode_sport, 0.75f),
    TURBO("TURBO", R.string.power_mode_turbo, 1.00f);

    companion object {
        fun fromId(id: String?): PowerMode =
            entries.firstOrNull { it.id == id } ?: TURBO
    }
}

data class MotorCommand(
    val leftSpeed: Int,
    val rightSpeed: Int,
    val formattedString: String,
) {
    companion object {
        fun stop(format: CommandFormatPreset): MotorCommand =
            MotorCommand(0, 0, format.format(0, 0))
    }
}

sealed interface BluetoothConnectionState {
    data object Disconnected : BluetoothConnectionState
    data class Connecting(val deviceName: String, val address: String) : BluetoothConnectionState
    data class Connected(val deviceName: String, val address: String) : BluetoothConnectionState
    data class Error(val message: String) : BluetoothConnectionState
}

data class BluetoothDeviceUi(
    val name: String,
    val address: String,
    val isPaired: Boolean,
    val isEspLikely: Boolean,
)

data class BatteryTelemetry(
    val roverPercent: Int? = null,
    val roverVoltage: Float? = null,
    val lastRoverUpdateMs: Long? = null,
    val phonePercent: Int = 100,
    val isPhoneCharging: Boolean = false,
)

data class SerialLogEntry(
    val id: Long,
    val timestamp: String,
    val isTx: Boolean,
    val payload: String,
)
