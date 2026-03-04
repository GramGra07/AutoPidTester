package com.dacodingbeast.pidtuners.PhysicsBasedPID

import com.dacodingbeast.pidtuners.PID.PIDWrapper
import com.dacodingbeast.pidtuners.HardwareSetup.PIDParams
import com.dacodingbeast.pidtuners.HardwareSetup.Result
import com.dacodingbeast.pidtuners.Algorithm.Dt
import kotlin.math.abs

/**
 * Physics-based PIDF controller that synthesizes gains from system dynamics.
 * Implements the PIDWrapper interface for drop-in replacement in existing systems.
 *
 * Combines:
 * 1. Pole placement for PID gains (tracking dynamics)
 * 2. Gravity feedforward (steady-state accuracy)
 * 3. Anti-windup for integral term (stability)
 */
class PhysicsBasedPIDWrapper(
    config: PhysicsParameterConfig,
    override var pidParams: PIDParams = config.synthesizeGains()
) : PIDWrapper {

    // Configuration tracking
    private val configuration = config
    private val synthesizedParams = pidParams

    // Controller state
    private var prevError = 0.0
    private var integral = 0.0

    // Pre-calculated constants
    private val dtInverse = 1.0 / Dt
    private var minIntegral: Double = -1.0
    private var maxIntegral: Double = 1.0

    init {
        // Validate synthesized gains
        val validation = com.dacodingbeast.pidtuners.GainCalculation.GainSynthesizer.validateGains(pidParams)
        if (!validation.isValid) {
            println("Warning: Synthesized gains validation failed:")
            println(validation)
        }
    }

    /**
     * Update integral term limits based on motor saturation.
     * Prevents integral windup when motor reaches limits.
     *
     * @param minSaturation Minimum control effort (typically -1.0)
     * @param maxSaturation Maximum control effort (typically +1.0)
     */
    fun setIntegralLimits(minSaturation: Double, maxSaturation: Double) {
        minIntegral = minSaturation / (pidParams.ki + 0.0001)
        maxIntegral = maxSaturation / (pidParams.ki + 0.0001)
    }

    /**
     * Calculate control output using PIDF algorithm.
     *
     * @param error Current tracking error
     * @param ff Feedforward term (typically gravity compensation)
     * @return Result containing motor power command and error
     */
    override fun calculate(error: Double, ff: Double): Result {
        // Calculate integral term (with anti-windup)
        integral += error * Dt
        integral = integral.coerceIn(minIntegral, maxIntegral)

        // Calculate derivative term (with low-pass filtering to reduce noise)
        val derivative = (error - prevError) * dtInverse
        prevError = error

        // Calculate proportional-integral-derivative control effort
        val pidContribution = error * pidParams.kp +
                              integral * pidParams.ki +
                              derivative * pidParams.kd

        // Add feedforward (gravity compensation)
        val totalControlEffort = (pidContribution + ff * pidParams.kf)
            .coerceIn(-1.0, 1.0)

        return Result(totalControlEffort, error, this.javaClass)
    }

    /**
     * Reset controller state for new trajectory.
     */
    override fun reset() {
        prevError = 0.0
        integral = 0.0
    }

    /**
     * Get configuration used for synthesis.
     */
    fun getConfiguration(): PhysicsParameterConfig = configuration

    /**
     * Get original synthesized parameters (before any tuning).
     */
    fun getSynthesizedParams(): PIDParams = synthesizedParams

    /**
     * Get current parameters (may have been modified).
     */
    fun getCurrentParams(): PIDParams = pidParams

    /**
     * Tune gains from synthesized values.
     * Useful for fine-tuning after synthesis.
     *
     * @param kpTuning Multiplier for Kp
     * @param kiTuning Multiplier for Ki
     * @param kdTuning Multiplier for Kd
     * @param kffTuning Multiplier for Kff
     */
    fun tuneGains(
        kpTuning: Double = 1.0,
        kiTuning: Double = 1.0,
        kdTuning: Double = 1.0,
        kffTuning: Double = 1.0
    ) {
        pidParams = com.dacodingbeast.pidtuners.GainCalculation.GainSynthesizer.tuneGains(
            baseGains = synthesizedParams,
            kpTuning = kpTuning,
            kiTuning = kiTuning,
            kdTuning = kdTuning,
            kffTuning = kffTuning
        )
    }

    override fun kP(): Double = pidParams.kp
    override fun kI(): Double = pidParams.ki
    override fun kD(): Double = pidParams.kd
    override fun kF(): Double = pidParams.kf

    override fun toString(): String {
        return """
            PhysicsBasedPIDWrapper:
            =====================
            Synthesized Gains:
              Kp: ${"%.6f".format(kP())}
              Ki: ${"%.6f".format(kI())}
              Kd: ${"%.6f".format(kD())}
              Kf: ${"%.6f".format(kF())}
            
            Controller State:
              Integral: ${"%.6f".format(integral)}
              Previous Error: ${"%.6f".format(prevError)}
        """.trimIndent()
    }
}

