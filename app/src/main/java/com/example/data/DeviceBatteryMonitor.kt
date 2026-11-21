package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PhoneBatteryState(
    val percent: Int = 100,
    val isCharging: Boolean = false,
)

/**
 * Monitoriza en tiempo real el porcentaje y estado de carga de la batería del
 * dispositivo Android (mando) combinando [BatteryManager] y el broadcast del
 * sistema [Intent.ACTION_BATTERY_CHANGED].
 */
class DeviceBatteryMonitor(private val context: Context) {

    private val _batteryState = MutableStateFlow(readCurrentBattery())
    val batteryState: StateFlow<PhoneBatteryState> = _batteryState.asStateFlow()

    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                _batteryState.value = extractFromIntent(intent)
            }
        }
    }

    fun start() {
        if (isRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        if (stickyIntent != null) {
            _batteryState.value = extractFromIntent(stickyIntent)
        }
        isRegistered = true
    }

    fun stop() {
        if (!isRegistered) return
        runCatching { context.unregisterReceiver(receiver) }
        isRegistered = false
    }

    private fun readCurrentBattery(): PhoneBatteryState {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: 100
        val charging = manager?.isCharging ?: false
        return PhoneBatteryState(percent = pct, isCharging = charging)
    }

    private fun extractFromIntent(intent: Intent): PhoneBatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            _batteryState.value.percent
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return PhoneBatteryState(percent = percent, isCharging = isCharging)
    }
}
