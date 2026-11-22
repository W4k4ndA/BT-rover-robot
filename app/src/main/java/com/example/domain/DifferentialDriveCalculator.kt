package com.example.domain

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Calcula las velocidades diferenciales de los motores izquierdo y derecho
 * a partir de los ejes normalizados del joystick virtual.
 *
 * Convención de ejes:
 * - [joyX]: -1.0 (izquierda) a +1.0 (derecha)
 * - [joyY]: +1.0 (arriba / adelante) a -1.0 (abajo / reversa)
 *
 * ¿Por qué mezcla diferencial (Arcade Drive)?
 * Un robot de 2 motores independientes gira variando la diferencia entre
 * la rueda izquierda y la derecha:
 * - Adelante puro (joyY = +1, joyX = 0): L = +max, R = +max
 * - Reversa pura  (joyY = -1, joyX = 0): L = -max, R = -max
 * - Giro derecha  (joyY =  0, joyX = +1): L = +max, R = -max (pivote sobre eje)
 * - Curva suave   (joyY = +0.7, joyX = +0.4): el motor izquierdo gira más rápido
 */
object DifferentialDriveCalculator {

    fun calculate(
        joyX: Float,
        joyY: Float,
        maxPwm: Int = 255,
        powerMultiplier: Float = 1.0f,
        format: CommandFormatPreset = CommandFormatPreset.L_R_COLON,
        deadzone: Float = 0.08f,
    ): MotorCommand {
        val clampedX = joyX.coerceIn(-1f, 1f)
        val clampedY = joyY.coerceIn(-1f, 1f)
        val magnitude = hypot(clampedX, clampedY)

        // Zona muerta radial: evita que micro-toques en el centro envíen
        // pulsos PWM bajos que solo recalientan los motores TT sin moverlos.
        if (magnitude < deadzone) {
            return MotorCommand.stop(format)
        }

        // Reescalamos el rango [deadzone..1] a [0..1] para que la aceleración
        // comience suave justo al salir del centro sin saltos bruscos.
        val clampedMag = magnitude.coerceAtMost(1f)
        val effectiveMag = ((clampedMag - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)
        val normX = (clampedX / magnitude) * effectiveMag
        val normY = (clampedY / magnitude) * effectiveMag

        // En reversa clara (normY < -0.15f) invertimos el eje X (estilo automóvil):
        // al mover el joystick en diagonal abajo-izquierda, la parte trasera del robot
        // gira hacia la izquierda (la rueda derecha retrocede más rápido que la izquierda).
        // En giro puro sobre el eje (|normY| <= 0.15f) se conserva la rotación directa.
        val effectiveX = if (normY < -0.15f) -normX else normX

        var leftMix = normY + effectiveX
        var rightMix = normY - effectiveX

        // Normalización proporcional: si la suma (normY + normX) supera 1.0,
        // dividimos ambos canales por el máximo para conservar la relación de giro
        // sin saturar ni recortar un solo motor.
        val maxMix = max(abs(leftMix), abs(rightMix))
        if (maxMix > 1f) {
            leftMix /= maxMix
            rightMix /= maxMix
        }

        val scale = maxPwm.coerceAtLeast(1) * powerMultiplier.coerceIn(0.1f, 1f)
        val leftPwm = (leftMix * scale).roundToInt().coerceIn(-maxPwm, maxPwm)
        val rightPwm = (rightMix * scale).roundToInt().coerceIn(-maxPwm, maxPwm)

        return MotorCommand(
            leftSpeed = leftPwm,
            rightSpeed = rightPwm,
            formattedString = format.format(leftPwm, rightPwm),
        )
    }
}
