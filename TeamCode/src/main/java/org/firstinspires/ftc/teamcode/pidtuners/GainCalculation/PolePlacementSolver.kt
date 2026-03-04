package com.dacodingbeast.pidtuners.GainCalculation

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Calculates PIDF gains using pole placement control theory.
 * Maps desired system response (settling time, overshoot) to Kp, Ki, Kd values.
 *
 * For a second-order system with desired response:
 * - Settling Time (Ts): Time to reach and stay within ±2% of setpoint
 * - Overshoot (OS%): Peak exceeding setpoint, expressed as percentage
 *
 * These are converted to:
 * - Natural frequency (ωn): Related to settling time
 * - Damping ratio (ζ): Related to overshoot
 *
 * Then gains are calculated via pole placement:
 * - Kp = J * ωn²
 * - Kd = 2*J*ζ*ωn - b
 * - Ki = (Kp / Kd) * (ωn / 10) [approximation for integral action]
 */
object PolePlacementSolver {

    /**
     * Calculate PIDF gains from desired settling time and overshoot.
     *
     * @param settlingTime Desired settling time in seconds (±2% criterion)
     * @param overshootPercent Desired overshoot as percentage (0-100)
     * @param momentOfInertia System moment of inertia (J)
     * @param viscousDamping System viscous damping (b)
     * @param integratorStrength Factor to adjust integral gain strength (default 0.5)
     * @return PolePlacementResult containing Kp, Ki, Kd
     */
    fun calculateGains(
        settlingTime: Double,
        overshootPercent: Double,
        momentOfInertia: Double,
        viscousDamping: Double,
        integratorStrength: Double = 0.5
    ): PolePlacementResult {
        require(settlingTime > 0) { "Settling time must be positive" }
        require(overshootPercent in 0.0..100.0) { "Overshoot must be between 0 and 100%" }
        require(momentOfInertia > 0) { "Moment of inertia must be positive" }
        require(viscousDamping >= 0) { "Viscous damping must be non-negative" }

        // Step 1: Convert overshoot to damping ratio (ζ)
        // OS% = 100 * exp(-ζ*π / sqrt(1-ζ²))
        val zeta = calculateDampingRatio(overshootPercent)

        // Step 2: Convert settling time to natural frequency (ωn)
        // For ±2% criterion: Ts ≈ 4 / (ζ * ωn)
        val omegaN = 4.0 / (zeta * settlingTime)

        // Step 3: Calculate proportional gain
        // Desired characteristic equation: s² + 2ζωn*s + ωn² = 0
        // Actual system: J*s² + (b + Kd)*s + Kp = 0 → s² + ((b+Kd)/J)*s + (Kp/J) = 0
        // Therefore: Kp = J * ωn²
        val kp = momentOfInertia * omegaN * omegaN

        // Step 4: Calculate derivative gain
        // Kd = 2*J*ζ*ωn - b
        val kd = 2.0 * momentOfInertia * zeta * omegaN - viscousDamping

        // Step 5: Calculate integral gain (approximation)
        // Ki ≈ (Kp / Kd) * (ωn / scale_factor) * strength
        // This ensures integral action is responsive without causing overshoot
        val ki = if (kd > 0.001) {
            (kp / kd) * (omegaN / 20.0) * integratorStrength
        } else {
            0.0 // No integral if derivative gain too small
        }

        return PolePlacementResult(
            kp = kp,
            ki = ki,
            kd = kd,
            dampingRatio = zeta,
            naturalFrequency = omegaN,
            settlingTime = settlingTime,
            overshootPercent = overshootPercent
        )
    }

    /**
     * Calculate damping ratio from desired overshoot percentage.
     *
     * Given: OS% = 100 * exp(-ζ*π / sqrt(1-ζ²))
     * Solve for ζ numerically or using approximation.
     */
    private fun calculateDampingRatio(overshootPercent: Double): Double {
        if (overshootPercent >= 100.0) return 0.0
        if (overshootPercent <= 0.01) return 1.0

        // Numerical approach: iterate to find ζ that produces desired OS%
        var zeta = 0.5 // Initial guess
        val target = overshootPercent / 100.0

        repeat(50) { // Newton-Raphson iterations
            val sqrt1MinusZ2 = sqrt(1.0 - zeta * zeta)
            val overshoot = (-zeta * Math.PI / sqrt1MinusZ2).let { kotlin.math.exp(it) }

            if ((overshoot - target).let { kotlin.math.abs(it) } < 1e-6) {
                return@repeat // Converged
            }

            // Derivative of exp(-ζ*π/sqrt(1-ζ²)) w.r.t. ζ
            val dOvershoot = overshoot * (-Math.PI / sqrt1MinusZ2 - zeta * Math.PI * zeta / (sqrt1MinusZ2 * (1 - zeta * zeta)))

            zeta -= (overshoot - target) / dOvershoot
            zeta = zeta.coerceIn(0.01, 0.99) // Keep in valid range
        }

        return zeta
    }

    /**
     * Result of pole placement calculation
     */
    data class PolePlacementResult(
        val kp: Double,
        val ki: Double,
        val kd: Double,
        val dampingRatio: Double,
        val naturalFrequency: Double,
        val settlingTime: Double,
        val overshootPercent: Double
    ) {
        /**
         * Validate that calculated gains are reasonable
         */
        fun validate(): Boolean {
            return kp > 0 && kd > 0 && ki >= 0 &&
                   dampingRatio in 0.01..1.0 &&
                   naturalFrequency > 0
        }
    }
}

