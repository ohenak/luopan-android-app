package com.luopan.compass.sensor

import com.luopan.compass.magnetic.WmmResult
import kotlin.math.abs

/**
 * Constructs [InterferenceMetrics] for a single sensor frame.
 *
 * When a [WmmResult] is available (location fix present), the expected field magnitude
 * and inclination come from the WMM model. When no WMM result is available, the EMA
 * baseline is used for expected field, and inclination deviation is computed from 0°.
 *
 * TSPEC §3.8 — P4.2 WMM-baseline upgrade.
 *
 * Two code paths (PLAN §5.3):
 *  - **WMM path** (wmmResult != null): uses wmmResult.totalField / wmmResult.inclination
 *  - **EMA fallback** (wmmResult == null): uses emaBaselineFallback; inclinationDeviation = abs(sensorInclination)
 *
 * Neither path modifies the other's state.
 */
object InterferenceMetricsFactory {

    /**
     * Builds [InterferenceMetrics] for one sensor frame.
     *
     * @param sensorFieldMagnitude  Total magnetic field magnitude from the sensor, in µT.
     * @param sensorInclination     Pitch / inclination from the fusion engine, in degrees.
     * @param wmmResult             Latest cached [WmmResult] from [MagneticFieldModelProvider.getLastResult],
     *                              or null when no location fix is available.
     * @param emaBaselineFallback   Rolling EMA of observed field magnitude (µT).
     *                              Used only when [wmmResult] is null.
     * @return Fully populated [InterferenceMetrics] ready to pass to [InterferenceDetector.updateState].
     */
    fun build(
        sensorFieldMagnitude: Float,
        sensorInclination: Float,
        wmmResult: WmmResult?,
        emaBaselineFallback: Float
    ): InterferenceMetrics {
        return if (wmmResult != null) {
            // WMM path: use model-derived expected values
            val expectedField = wmmResult.totalField
            val expectedInclination = wmmResult.inclination
            val fieldDev = if (expectedField > 0f)
                abs(sensorFieldMagnitude - expectedField) / expectedField
            else 0f
            val inclDev = abs(sensorInclination - expectedInclination)

            InterferenceMetrics(
                fieldMagnitude_uT = sensorFieldMagnitude,
                expectedField_uT = expectedField,
                fieldDeviation = fieldDev.coerceIn(0f, 1f),
                inclination_deg = sensorInclination,
                expectedInclination_deg = expectedInclination,
                inclinationDeviation_deg = inclDev
            )
        } else {
            // EMA fallback path: legacy behaviour
            val fieldDev = if (emaBaselineFallback > 0f)
                abs(sensorFieldMagnitude - emaBaselineFallback) / emaBaselineFallback
            else 0f

            InterferenceMetrics(
                fieldMagnitude_uT = sensorFieldMagnitude,
                expectedField_uT = emaBaselineFallback,
                fieldDeviation = fieldDev.coerceIn(0f, 1f),
                inclination_deg = sensorInclination,
                expectedInclination_deg = 0f,
                inclinationDeviation_deg = abs(sensorInclination)
            )
        }
    }
}
