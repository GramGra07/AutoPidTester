package com.dacodingbeast.pidtuners.PhysicsModel

import kotlin.math.abs

/**
 * Represents the physical system being controlled (rotational arm).
 * Models the differential equation: J*θ'' + b*θ' + τ_gravity(θ) = τ_motor
 *
 * @param momentOfInertia J - Moment of inertia in kg⋅m²
 * @param viscousDamping b - Viscous damping coefficient
 * @param centerOfMass Distance from pivot to center of mass in meters
 * @param systemMass Total mass of the system in kg
 */
data class SystemDefinition(
    val momentOfInertia: Double,      // J in kg⋅m²
    val viscousDamping: Double,        // b - friction coefficient
    val centerOfMass: Double,          // L_cm in meters
    val systemMass: Double,            // Total mass in kg
    val maxMotorTorque: Double,        // Maximum motor torque in N⋅m
    val minAngle: Double = -Math.PI,   // Minimum joint angle in radians
    val maxAngle: Double = Math.PI     // Maximum joint angle in radians
) {
    init {
        require(momentOfInertia > 0) { "Moment of inertia must be positive" }
        require(viscousDamping >= 0) { "Viscous damping must be non-negative" }
        require(centerOfMass > 0) { "Center of mass distance must be positive" }
        require(systemMass > 0) { "System mass must be positive" }
        require(maxMotorTorque > 0) { "Max motor torque must be positive" }
        require(maxAngle > minAngle) { "Max angle must be greater than min angle" }
    }

    /**
     * Calculate gravitational torque at a given angle
     * τ_g = m*g*L_cm*cos(θ)
     */
    fun calculateGravityTorque(angle: Double): Double {
        val gravityAccel = 9.81 // m/s²
        return systemMass * gravityAccel * centerOfMass * kotlin.math.cos(angle)
    }

    /**
     * Clamp angle to valid range
     */
    fun clampAngle(angle: Double): Double {
        return angle.coerceIn(minAngle, maxAngle)
    }

    companion object {
        /**
         * Create a SystemDefinition from arm geometry
         * @param pointMass Mass of load at arm tip (kg)
         * @param armMass Mass of the arm itself (kg)
         * @param armLength Length from pivot to tip (m)
         * @param viscousDamping Damping coefficient
         * @param maxMotorTorque Maximum motor torque (N⋅m)
         */
        fun fromArmGeometry(
            pointMass: Double,
            armMass: Double,
            armLength: Double,
            viscousDamping: Double,
            maxMotorTorque: Double
        ): SystemDefinition {
            // Calculate moment of inertia
            // J = (m_p * L²) + (1/3 * m_a * L²)
            val pointMassInertia = pointMass * armLength * armLength
            val armInertia = (1.0 / 3.0) * armMass * armLength * armLength
            val totalInertia = pointMassInertia + armInertia

            // Center of mass for combined system
            // x_cm = (m_p * L + m_a * L/2) / (m_p + m_a)
            val totalMass = pointMass + armMass
            val centerOfMass = (pointMass * armLength + armMass * armLength / 2.0) / totalMass

            return SystemDefinition(
                momentOfInertia = totalInertia,
                viscousDamping = viscousDamping,
                centerOfMass = centerOfMass,
                systemMass = totalMass,
                maxMotorTorque = maxMotorTorque
            )
        }
    }
}

