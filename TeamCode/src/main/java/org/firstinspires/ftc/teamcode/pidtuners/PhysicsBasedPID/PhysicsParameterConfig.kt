package com.dacodingbeast.pidtuners.PhysicsBasedPID

import com.dacodingbeast.pidtuners.PhysicsModel.SystemDefinition
import com.dacodingbeast.pidtuners.GainCalculation.GainSynthesizer
import com.dacodingbeast.pidtuners.HardwareSetup.PIDParams
import com.dacodingbeast.pidtuners.HardwareSetup.MotorSpecs

/**
 * Configuration builder for physics-based PIDF tuning.
 * Provides a fluent API to specify system parameters and control objectives.
 */
class PhysicsParameterConfig private constructor(
    val system: SystemDefinition,
    val motorSpecs: MotorSpecs,
    val settlingTime: Double,
    val overshootPercent: Double,
    val enableGravityFeedforward: Boolean,
    val integratorStrength: Double
) {
    /**
     * Builder for PhysicsParameterConfig
     */
    class Builder {
        // Arm geometry parameters (for automatic inertia calculation)
        private var pointMass: Double? = null
        private var armMass: Double? = null
        private var armLength: Double? = null

        // System parameters (if not using geometry)
        private var momentOfInertia: Double? = null
        private var viscousDamping: Double = 0.1
        private var centerOfMass: Double? = null
        private var systemMass: Double? = null

        // Motor specifications
        private var motorSpecs: MotorSpecs? = null
        private var maxMotorTorque: Double? = null

        // Control objectives
        private var settlingTime: Double = 0.5 // Default: 500ms
        private var overshootPercent: Double = 10.0 // Default: 10%
        private var enableGravityFeedforward: Boolean = true
        private var integratorStrength: Double = 0.5

        /**
         * Define arm geometry for automatic inertia calculation.
         * This is the preferred method.
         */
        fun withArmGeometry(
            pointMass: Double,
            armMass: Double,
            armLength: Double
        ) = apply {
            this.pointMass = pointMass
            this.armMass = armMass
            this.armLength = armLength
        }

        /**
         * Directly specify moment of inertia instead of calculating from geometry.
         */
        fun withMomentOfInertia(moi: Double) = apply {
            this.momentOfInertia = moi
        }

        /**
         * Set viscous damping coefficient (default 0.1).
         */
        fun withViscousDamping(damping: Double) = apply {
            this.viscousDamping = damping
        }

        /**
         * Set center of mass distance and system mass.
         * Required if not using arm geometry.
         */
        fun withGravityParameters(centerOfMass: Double, systemMass: Double) = apply {
            this.centerOfMass = centerOfMass
            this.systemMass = systemMass
        }

        /**
         * Set motor specifications.
         */
        fun withMotorSpecs(specs: MotorSpecs) = apply {
            this.motorSpecs = specs
        }

        /**
         * Set maximum motor torque (if not using MotorSpecs).
         */
        fun withMaxMotorTorque(torque: Double) = apply {
            this.maxMotorTorque = torque
        }

        /**
         * Set desired settling time in seconds.
         */
        fun withSettlingTime(seconds: Double) = apply {
            this.settlingTime = seconds
        }

        /**
         * Set desired overshoot as percentage (0-100).
         */
        fun withOvershoot(percent: Double) = apply {
            this.overshootPercent = percent
        }

        /**
         * Enable/disable gravity feedforward calculation.
         */
        fun withGravityFeedforward(enable: Boolean) = apply {
            this.enableGravityFeedforward = enable
        }

        /**
         * Set integrator strength (0-1, higher = more responsive).
         */
        fun withIntegratorStrength(strength: Double) = apply {
            this.integratorStrength = strength
        }

        /**
         * Build the PhysicsParameterConfig.
         */
        fun build(): PhysicsParameterConfig {
            // Determine system definition
            val systemDef = when {
                // Use arm geometry if provided
                pointMass != null && armMass != null && armLength != null -> {
                    val motorTorque = maxMotorTorque ?: motorSpecs?.stallTorque?.value ?: 1.0
                    SystemDefinition.fromArmGeometry(
                        pointMass = pointMass!!,
                        armMass = armMass!!,
                        armLength = armLength!!,
                        viscousDamping = viscousDamping,
                        maxMotorTorque = motorTorque
                    )
                }
                // Use manual inertia if provided
                momentOfInertia != null && centerOfMass != null && systemMass != null -> {
                    val motorTorque = maxMotorTorque ?: motorSpecs?.stallTorque?.value ?: 1.0
                    SystemDefinition(
                        momentOfInertia = momentOfInertia!!,
                        viscousDamping = viscousDamping,
                        centerOfMass = centerOfMass!!,
                        systemMass = systemMass!!,
                        maxMotorTorque = motorTorque
                    )
                }
                else -> throw IllegalStateException(
                    "Must provide either arm geometry (pointMass, armMass, armLength) " +
                    "or manual inertia (momentOfInertia, centerOfMass, systemMass)"
                )
            }

            val motorSpecsRequired = motorSpecs ?: throw IllegalStateException(
                "Must provide motorSpecs"
            )

            return PhysicsParameterConfig(
                system = systemDef,
                motorSpecs = motorSpecsRequired,
                settlingTime = settlingTime,
                overshootPercent = overshootPercent,
                enableGravityFeedforward = enableGravityFeedforward,
                integratorStrength = integratorStrength
            )
        }
    }

    /**
     * Synthesize PIDF parameters from this configuration.
     */
    fun synthesizeGains(): PIDParams {
        return GainSynthesizer.synthesizeGains(
            system = system,
            settlingTime = settlingTime,
            overshootPercent = overshootPercent,
            enableGravityFeedforward = enableGravityFeedforward,
            integratorStrength = integratorStrength
        )
    }

    /**
     * Get summary of configuration for debugging.
     */
    override fun toString(): String {
        return """
            Physics-Based PIDF Configuration:
            ================================
            System Physics:
              - Moment of Inertia: ${system.momentOfInertia} kg⋅m²
              - Viscous Damping: ${system.viscousDamping}
              - Center of Mass: ${system.centerOfMass} m
              - System Mass: ${system.systemMass} kg
              - Max Motor Torque: ${system.maxMotorTorque} N⋅m
            
            Control Objectives:
              - Settling Time: $settlingTime s
              - Overshoot: $overshootPercent %
              - Gravity FF: $enableGravityFeedforward
              - Integrator Strength: $integratorStrength
            """.trimIndent()
    }
}

