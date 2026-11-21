package com.example

import com.example.domain.CommandFormatPreset
import com.example.domain.DifferentialDriveCalculator
import com.example.domain.TelemetryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoverLogicTest {

    @Test
    fun `joystick up drives both motors forward equally`() {
        val cmd = DifferentialDriveCalculator.calculate(
            joyX = 0f,
            joyY = 1f,
            maxPwm = 255,
            powerMultiplier = 1f,
            format = CommandFormatPreset.L_R_COLON,
        )
        assertEquals(255, cmd.leftSpeed)
        assertEquals(255, cmd.rightSpeed)
        assertEquals("L:255,R:255", cmd.formattedString)
    }

    @Test
    fun `joystick down drives both motors backward equally`() {
        val cmd = DifferentialDriveCalculator.calculate(
            joyX = 0f,
            joyY = -1f,
            maxPwm = 255,
            powerMultiplier = 1f,
            format = CommandFormatPreset.L_R_COLON,
        )
        assertEquals(-255, cmd.leftSpeed)
        assertEquals(-255, cmd.rightSpeed)
        assertEquals("L:-255,R:-255", cmd.formattedString)
    }

    @Test
    fun `joystick right pivots rover clockwise`() {
        val cmd = DifferentialDriveCalculator.calculate(
            joyX = 1f,
            joyY = 0f,
            maxPwm = 255,
            powerMultiplier = 1f,
            format = CommandFormatPreset.L_R_COLON,
        )
        assertEquals(255, cmd.leftSpeed)
        assertEquals(-255, cmd.rightSpeed)
        assertEquals("L:255,R:-255", cmd.formattedString)
    }

    @Test
    fun `joystick left pivots rover counter-clockwise`() {
        val cmd = DifferentialDriveCalculator.calculate(
            joyX = -1f,
            joyY = 0f,
            maxPwm = 255,
            powerMultiplier = 1f,
            format = CommandFormatPreset.L_R_COLON,
        )
        assertEquals(-255, cmd.leftSpeed)
        assertEquals(255, cmd.rightSpeed)
        assertEquals("L:-255,R:255", cmd.formattedString)
    }

    @Test
    fun `joystick inside deadzone stops both motors`() {
        val cmd = DifferentialDriveCalculator.calculate(
            joyX = 0.03f,
            joyY = -0.04f,
            maxPwm = 255,
            powerMultiplier = 1f,
        )
        assertEquals(0, cmd.leftSpeed)
        assertEquals(0, cmd.rightSpeed)
        assertEquals("L:0,R:0", cmd.formattedString)
    }

    @Test
    fun `telemetry parser extracts percentage and voltage from ESP32 lines`() {
        val batOnly = TelemetryParser.parseBatteryLine("BAT:82")
        assertNotNull(batOnly)
        assertEquals(82, batOnly?.percent)

        val voltAndBat = TelemetryParser.parseBatteryLine("BAT:76,V:7.92")
        assertNotNull(voltAndBat)
        assertEquals(76, voltAndBat?.percent)
        assertEquals(7.92f, voltAndBat?.voltage ?: 0f, 0.01f)

        val voltOnly = TelemetryParser.parseBatteryLine("V:7.40")
        assertNotNull(voltOnly)
        assertTrue((voltOnly?.percent ?: 0) in 40..60)

        assertNull(TelemetryParser.parseBatteryLine("READY"))
    }
}
