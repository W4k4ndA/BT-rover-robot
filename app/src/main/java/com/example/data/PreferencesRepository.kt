package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val MAX_PWM = intPreferencesKey("max_pwm")
        val COMMAND_FORMAT = stringPreferencesKey("command_format")
        val POWER_MODE = stringPreferencesKey("power_mode")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
    }

    val preferences: Flow<RoverPreferences> = context.roverDataStore.data.map { prefs ->
        RoverPreferences(
            maxPwm = prefs[Keys.MAX_PWM] ?: 255,
            commandFormat = CommandFormatPreset.fromId(prefs[Keys.COMMAND_FORMAT]),
            powerMode = PowerMode.fromId(prefs[Keys.POWER_MODE]),
            lastDeviceAddress = prefs[Keys.LAST_DEVICE_ADDRESS],
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
}
