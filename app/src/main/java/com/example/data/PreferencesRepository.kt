package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.CommandFormatPreset
import com.example.domain.PowerMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.roverDataStore: DataStore<Preferences> by preferencesDataStore(name = "rover_prefs")

data class RoverPreferences(
    val maxPwm: Int = 255,
    val commandFormat: CommandFormatPreset = CommandFormatPreset.L_R_COLON,
    val powerMode: PowerMode = PowerMode.TURBO,
    val lastDeviceAddress: String? = null,
    val invertLeftMotor: Boolean = false,
    val invertRightMotor: Boolean = false,
    val swapLeftRight: Boolean = false,
)

/**
 * Repositorio local basado en Jetpack DataStore para recordar entre sesiones
 * la escala PWM, el formato de trama, el modo de potencia, la calibración de motores
 * y la última dirección MAC del ESP32.
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val MAX_PWM = intPreferencesKey("max_pwm")
        val COMMAND_FORMAT = stringPreferencesKey("command_format")
        val POWER_MODE = stringPreferencesKey("power_mode")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val INVERT_LEFT_MOTOR = booleanPreferencesKey("invert_left_motor")
        val INVERT_RIGHT_MOTOR = booleanPreferencesKey("invert_right_motor")
        val SWAP_LEFT_RIGHT = booleanPreferencesKey("swap_left_right")
    }

    val preferences: Flow<RoverPreferences> = context.roverDataStore.data.map { prefs ->
        RoverPreferences(
            maxPwm = prefs[Keys.MAX_PWM] ?: 255,
            commandFormat = CommandFormatPreset.fromId(prefs[Keys.COMMAND_FORMAT]),
            powerMode = PowerMode.fromId(prefs[Keys.POWER_MODE]),
            lastDeviceAddress = prefs[Keys.LAST_DEVICE_ADDRESS],
            invertLeftMotor = prefs[Keys.INVERT_LEFT_MOTOR] ?: false,
            invertRightMotor = prefs[Keys.INVERT_RIGHT_MOTOR] ?: false,
            swapLeftRight = prefs[Keys.SWAP_LEFT_RIGHT] ?: false,
        )
    }

    suspend fun setMaxPwm(maxPwm: Int) {
        context.roverDataStore.edit { it[Keys.MAX_PWM] = maxPwm }
    }

    suspend fun setCommandFormat(format: CommandFormatPreset) {
        context.roverDataStore.edit { it[Keys.COMMAND_FORMAT] = format.id }
    }

    suspend fun setPowerMode(mode: PowerMode) {
        context.roverDataStore.edit { it[Keys.POWER_MODE] = mode.id }
    }

    suspend fun setLastDeviceAddress(address: String) {
        context.roverDataStore.edit { it[Keys.LAST_DEVICE_ADDRESS] = address }
    }

    suspend fun setInvertLeftMotor(invert: Boolean) {
        context.roverDataStore.edit { it[Keys.INVERT_LEFT_MOTOR] = invert }
    }

    suspend fun setInvertRightMotor(invert: Boolean) {
        context.roverDataStore.edit { it[Keys.INVERT_RIGHT_MOTOR] = invert }
    }

    suspend fun setSwapLeftRight(swap: Boolean) {
        context.roverDataStore.edit { it[Keys.SWAP_LEFT_RIGHT] = swap }
    }
}
