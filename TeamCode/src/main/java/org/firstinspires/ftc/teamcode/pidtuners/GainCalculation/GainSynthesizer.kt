package com.dacodingbeast.pidtuners.GainCalculation

import com.dacodingbeast.pidtuners.PhysicsModel.SystemDefinition
import com.dacodingbeast.pidtuners.HardwareSetup.PIDParams

/**
 * Synthesizes complete PIDF parameters based on physics and control theory.
 * Combines:
 * 1. Pole placement for Kp, Ki, Kd (tracking response)
 * 2. Gravity feedforward for Kff (steady-state accuracy)
 */
object GainSynthesizer {

    /**
     * Create complete PIDF parameters from system physics and control objectives.
     *
     * @param system Physical system definition
     * @param settlingTime Desired settling time in seconds
     * @param overshootPercent Desired overshoot (0-100%)
     * @param enableGravityFeedforward Whether to calculate and include Kff
     * @return PIDParams ready for use in controller
     */
    fun synthesizeGains(
        system: SystemDefinition,
        settlingTime: Double,
        overshootPercent: Double,
        enableGravityFeedforward: Boolean = true,
        integratorStrength: Double = 0.5
    ): PIDParams {
        // Step 1: Calculate PID gains using pole placement
        val gainResult = PolePlacementSolver.calculateGains(
            settlingTime = settlingTime,
            overshootPercent = overshootPercent,
            momentOfInertia = system.momentOfInertia,
            viscousDamping = system.viscousDamping,
            integratorStrength = integratorStrength
        )

        // Step 2: Calculate gravity feedforward
        val kff = if (enableGravityFeedforward) {
            GravityFeedforwardCalculator.calculateStaticKff(system)
        } else {
            0.0
        }

        // Step 3: Scale gains appropriately
        // The gains from pole placement are in natural units (N⋅m)
        // We need to scale them for motor power commands (-1 to 1)
        val scaledGains = scaleGainsForMotorPower(
            gainResult.kp,
            gainResult.ki,
            gainResult.kd,
            system.maxMotorTorque
        )

        return PIDParams(
            kp = scaledGains.kp,
            ki = scaledGains.ki,
            kd = scaledGains.kd,
            kf = kff
        )
    }

    /**
     * Scale gains from torque domain to motor power command domain.
     * The motor power command ranges from -1 to 1.
     * We need to normalize gains so that:
     * - Power command is clamped to [-1, 1]
     * - Control effort = Kp*error + Ki*integral + Kd*derivative + Kff
     *   is meaningful in this domain
     *
     * @param kp Proportional gain in torque domain (N⋅m per radian)
     * @param ki Integral gain in torque domain (N⋅m per radian⋅s)
     * @param kd Derivative gain in torque domain (N⋅m⋅s per radian)
     * @param maxMotorTorque Maximum motor torque in N⋅m
     * @return ScaledGains ready for use
     */
    private fun scaleGainsForMotorPower(
        kp: Double,
        ki: Double,
        kd: Double,
        maxMotorTorque: Double
    ): ScaledGains {
        // Scale factor: gain_scaled = gain_torque / maxMotorTorque
        // This ensures that maximum control effort = 1.0 (motor power)
        // when the error/integral/derivative reaches physically meaningful values
        val scaleFactor = 1.0 / maxMotorTorque

        return ScaledGains(
            kp = kp * scaleFactor,
            ki = ki * scaleFactor,
            kd = kd * scaleFactor
        )
    }

    /**
     * Create PIDF parameters optimized for specific control characteristics.
     * Useful for fine-tuning performance.
     *
     * @param baseGains Base PIDF parameters from synthesizeGains
     * @param kpTuning Multiplier for Kp (1.0 = no change)
     * @param kiTuning Multiplier for Ki
     * @param kdTuning Multiplier for Kd
     * @param kffTuning Multiplier for Kff
     * @return Adjusted PIDParams
     */
    fun tuneGains(
        baseGains: PIDParams,
        kpTuning: Double = 1.0,
        kiTuning: Double = 1.0,
        kdTuning: Double = 1.0,
        kffTuning: Double = 1.0
    ): PIDParams {
        return PIDParams(
            kp = baseGains.kp * kpTuning,
            ki = baseGains.ki * kiTuning,
            kd = baseGains.kd * kdTuning,
            kf = baseGains.kf * kffTuning
        )
    }

    /**
     * Validate that synthesized gains are reasonable and won't cause instability.
     */
    fun validateGains(gains: PIDParams): ValidationResult {
        val issues = mutableListOf<String>()

        if (gains.kp < 0) issues.add("Kp must be non-negative")
        if (gains.ki < 0) issues.add("Ki must be non-negative")
        if (gains.kd < 0) issues.add("Kd must be non-negative")
        if (gains.kf < 0 || gains.kf > 1.0) issues.add("Kf must be in [0, 1]")

        // Check for unrealistic gains
        if (gains.kp > 100.0) issues.add("Kp seems unreasonably high (>100)")
        if (gains.kd > 50.0) issues.add("Kd seems unreasonably high (>50)")

        // Check derivative term is significant (damping)
        if (gains.kd < gains.kp * 0.01) {
            issues.add("Kd is very small relative to Kp; may cause oscillation")
        }

        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }

    /**
     * Container for scaled gains
     */
    private data class ScaledGains(
        val kp: Double,
        val ki: Double,
        val kd: Double
    )

    /**
     * Validation result with detailed information
     */
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String> = emptyList()
    ) {
        override fun toString(): String {
            return if (isValid) {
                "Gains are valid"
            } else {
                "Validation issues:\n" + issues.joinToString("\n") { "  - $it" }
            }
        }
    }
}

