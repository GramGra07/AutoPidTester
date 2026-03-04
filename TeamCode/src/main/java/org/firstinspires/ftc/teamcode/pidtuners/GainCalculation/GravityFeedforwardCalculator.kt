package com.dacodingbeast.pidtuners.GainCalculation

import com.dacodingbeast.pidtuners.PhysicsModel.SystemDefinition

/**
 * Calculates gravity feedforward gain (Kff) to counteract gravitational torque.
 *
 * For a rotating arm: τ_gravity = m*g*L_cm*cos(θ)
 * To exactly counteract gravity: Kff = τ_gravity_max / τ_motor_max
 *
 * This allows the PID controller to focus only on tracking errors,
 * not fighting gravity.
 */
object GravityFeedforwardCalculator {

    /**
     * Calculate static gravity feedforward gain for the entire joint range.
     * Uses maximum gravity torque to ensure saturation doesn't occur.
     *
     * @param system SystemDefinition containing gravity parameters
     * @return Kff value to scale motor output for gravity compensation
     */
    fun calculateStaticKff(system: SystemDefinition): Double {
        // Find maximum gravity torque across the arm's range
        val maxGravityTorque = findMaxGravityTorque(system)

        // Kff scales motor command (0-1) to torque equivalent
        // When motor is at full power, it produces maxMotorTorque
        // To counteract gravity: Kff = maxGravityTorque / maxMotorTorque
        val kff = maxGravityTorque / system.maxMotorTorque

        // Clamp to [0, 1] since motor power command is bounded
        return kff.coerceIn(0.0, 1.0)
    }

    /**
     * Find maximum gravitational torque across the joint range.
     * Samples the joint range to find peak gravity torque magnitude.
     */
    private fun findMaxGravityTorque(system: SystemDefinition): Double {
        var maxTorque = 0.0
        val samples = 100
        val angleStep = (system.maxAngle - system.minAngle) / samples

        for (i in 0..samples) {
            val angle = system.minAngle + i * angleStep
            val gravityTorque = system.calculateGravityTorque(angle)
            maxTorque = maxOf(maxTorque, kotlin.math.abs(gravityTorque))
        }

        return maxTorque
    }

    /**
     * Calculate dynamic gravity feedforward that varies with joint angle.
     * Returns a function that maps angle to appropriate Kff.
     * More accurate than static Kff but more computationally expensive.
     *
     * @param system SystemDefinition containing gravity parameters
     * @return Function mapping angle (rad) to Kff value
     */
    fun calculateDynamicKff(system: SystemDefinition): (Double) -> Double {
        val maxGravityTorque = findMaxGravityTorque(system)

        return { angle: Double ->
            val gravityAtAngle = kotlin.math.abs(system.calculateGravityTorque(angle))
            val kff = gravityAtAngle / system.maxMotorTorque
            kff.coerceIn(0.0, 1.0)
        }
    }

    /**
     * Estimate required motor torque given voltage sag.
     * Nominal stall torque assumes nominal voltage.
     * Under voltage sag, available torque decreases proportionally.
     *
     * @param nominalVoltage Battery voltage in volts (typically 12V)
     * @param actualVoltage Current battery voltage in volts
     * @param nominalStallTorque Motor stall torque at nominal voltage
     * @return Effective stall torque under voltage sag
     */
    fun adjustTorqueForVoltageSag(
        nominalVoltage: Double,
        actualVoltage: Double,
        nominalStallTorque: Double
    ): Double {
        require(nominalVoltage > 0) { "Nominal voltage must be positive" }
        require(actualVoltage in 0.0..nominalVoltage * 1.2) { "Actual voltage should be near nominal" }

        val voltageRatio = actualVoltage / nominalVoltage
        return nominalStallTorque * voltageRatio
    }

    /**
     * Adjust Kff based on voltage sag to maintain gravity compensation.
     * If voltage drops, effective motor torque drops, so Kff should increase
     * to use more of the available power for gravity compensation.
     *
     * @param baseKff Static Kff calculated at nominal voltage
     * @param nominalVoltage Nominal battery voltage
     * @param actualVoltage Current battery voltage
     * @return Adjusted Kff for current voltage conditions
     */
    fun adjustKffForVoltage(
        baseKff: Double,
        nominalVoltage: Double,
        actualVoltage: Double
    ): Double {
        require(nominalVoltage > 0) { "Nominal voltage must be positive" }
        require(actualVoltage > 0) { "Actual voltage must be positive" }

        // Under voltage sag, need to use more of available power for gravity
        val voltageRatio = nominalVoltage / actualVoltage
        val adjustedKff = baseKff * voltageRatio

        // Clamp to [0, 1] since motor power command is bounded
        return adjustedKff.coerceIn(0.0, 1.0)
    }
}

