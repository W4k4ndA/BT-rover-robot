package com.example.domain

import kotlin.math.roundToInt

data class ParsedBatteryUpdate(
    val percent: Int?,
    val voltage: Float?,
)

/**
 * Analiza las tramas ASCII de telemetría enviadas por el ESP32-WROOM-32 vía SPP.
 *
 * ¿Por qué soportar múltiples formatos?
 * Permite que la app funcione de inmediato con nuestro firmware PlatformIO
 * ("BAT:85,V:7.84") y también con sketches simples personalizados ("85%", "V:7.8").
 */
object TelemetryParser {

    private val percentRegex =
        Regex("""(?:BAT(?:TERY)?|B)\s*[:=]\s*(\d{1,3})\s*%?|(\d{1,3})\s*%""", RegexOption.IGNORE_CASE)
    private val voltageRegex =
        Regex("""(?:VOLT(?:AGE)?|V)\s*[:=]\s*(\d+(?:\.\d+)?)\s*V?|(\d+\.\d+)\s*V""", RegexOption.IGNORE_CASE)
    private val plainIntRegex = Regex("""^\s*(\d{1,3})\s*$""")

    fun parseBatteryLine(rawLine: String): ParsedBatteryUpdate? {
        val line = rawLine.trim()
        if (line.isEmpty()) return null

        var percent: Int? = null
        var voltage: Float? = null

        percentRegex.find(line)?.let { match ->
            val numStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
            percent = numStr.toIntOrNull()?.coerceIn(0, 100)
        }

        voltageRegex.find(line)?.let { match ->
            val voltStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
            voltage = voltStr.toFloatOrNull()
        }

        if (percent == null && voltage == null) {
            plainIntRegex.find(line)?.let { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 0..100) {
                    percent = value
                }
            }
        }

        // Si el ESP32 reporta solo voltaje (ej. "V:7.84"), estimamos el % según
        // la curva típica LiPo (1S, 2S o 3S) detectada automáticamente por rango.
        if (percent == null && voltage != null) {
            percent = estimatePercentFromVoltage(voltage!!)
        }

        if (percent == null && voltage == null) return null
        return ParsedBatteryUpdate(percent = percent, voltage = voltage)
    }

    private fun estimatePercentFromVoltage(v: Float): Int {
        val (minV, maxV) = when {
            v <= 4.35f -> 3.2f to 4.2f // Batería LiPo 1S
            v <= 8.70f -> 6.4f to 8.4f // Batería LiPo 2S (la más común en carritos TT)
            else -> 9.6f to 12.6f      // Batería LiPo 3S
        }
        return (((v - minV) / (maxV - minV)) * 100f).roundToInt().coerceIn(0, 100)
    }
}
