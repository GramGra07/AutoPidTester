package com.dacodingbeast.pidtuners.GainCalculation

import com.dacodingbeast.pidtuners.HardwareSetup.MotorSpecs
import com.dacodingbeast.pidtuners.HardwareSetup.torque.TorqueUnit

/**
 * Converts motor power commands (0-1) to equivalent torque values.
 * Maps the dimensionless motor power to actual physical torque output.
 */
class MotorPowerToTorqueMapper(
    private val motorSpecs: MotorSpecs
) {
    /**
     * Map motor power command to torque output.
     * Assumes linear relationship: torque = power * stallTorque
     *
     * @param powerCommand Motor power from -1.0 (full reverse) to +1.0 (full forward)
     * @return Torque in N⋅m
     */
    fun powerToTorque(powerCommand: Double): Double {
        require(powerCommand in -1.0..1.0) { "Power command must be between -1 and 1" }

        // Get stall torque in N⋅m (converted from its current unit)
        motorSpecs.stallTorque.to(TorqueUnit.NEWTON_METER)
        val stallTorqueNm = motorSpecs.stallTorque.value

        // Apply gear ratio to torque
        val gearRatio = motorSpecs.motorGearRatio
        val effectiveStallTorque = stallTorqueNm * gearRatio

        // Linear mapping from power to torque
        return powerCommand * effectiveStallTorque
    }

    /**
     * Map torque requirement to motor power command.
     * Inverse of powerToTorque.
     *
     * @param torque Required torque in N⋅m
     * @return Motor power command (-1.0 to +1.0), clamped to valid range
     */
    fun torqueToPower(torque: Double): Double {
        motorSpecs.stallTorque.to(TorqueUnit.NEWTON_METER)
        val stallTorqueNm = motorSpecs.stallTorque.value
        val gearRatio = motorSpecs.motorGearRatio
        val effectiveStallTorque = stallTorqueNm * gearRatio

        if (effectiveStallTorque == 0.0) return 0.0

        val powerCommand = torque / effectiveStallTorque
        return powerCommand.coerceIn(-1.0, 1.0)
    }

    /**
     * Get maximum available torque in the current motor direction.
     */
    fun getMaxTorque(): Double {
        motorSpecs.stallTorque.to(TorqueUnit.NEWTON_METER)
        return motorSpecs.stallTorque.value * motorSpecs.motorGearRatio
    }

    /**
     * Get RPM accounting for gear ratio
     */
    fun getEffectiveRPM(): Double {
        return motorSpecs.rpm / motorSpecs.motorGearRatio
    }
}

