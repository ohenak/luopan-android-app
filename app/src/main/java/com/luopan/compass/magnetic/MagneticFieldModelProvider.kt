package com.luopan.compass.magnetic

/**
 * Selects the active [MagneticFieldModel] at runtime.
 *
 * Selection policy (TSPEC §3.3):
 *  - [wmm] is preferred when it is non-null and [Wmm2025Model.isExpired] returns false.
 *  - [fallback] ([AndroidGeoFieldModel]) is used when [wmm] is null or expired.
 *
 * This class does NOT hold a [com.luopan.compass.util.Clock] directly. The expiry check
 * is delegated to [wmm]'s internal Clock (injected at Wmm2025Model construction time).
 *
 * Thread safety: call [activeModel] and [evaluate] from a single coroutine (the sensor
 * pipeline coroutine on Dispatchers.Default). Not thread-safe by design.
 *
 * @param wmm      Primary model. May be null if loading failed; provider falls back immediately.
 * @param fallback Always-available fallback model.
 */
class MagneticFieldModelProvider(
    private val wmm: Wmm2025Model?,
    private val fallback: AndroidGeoFieldModel
) {

    private var lastResult: WmmResult? = null

    /**
     * The model ID from the most recent [activeModel] call. Used by [hasModelChanged]
     * to detect transitions between WMM and fallback.
     */
    private var previousModelId: String? = null

    /**
     * The model ID selected on the current [activeModel] call (updated inside [activeModel]).
     * Kept separately from [previousModelId] so that [hasModelChanged] can compare the two
     * across successive calls.
     */
    private var currentModelId: String? = null

    /**
     * Returns the currently active [MagneticFieldModel].
     *
     * Delegates [MagneticFieldModel.isExpired] to [wmm]; if [wmm] is null or expired,
     * returns [fallback].
     */
    fun activeModel(): MagneticFieldModel {
        val active: MagneticFieldModel = if (wmm != null && !wmm.isExpired()) wmm else fallback
        previousModelId = currentModelId
        currentModelId = active.getModelId()
        return active
    }

    /**
     * Evaluates the active model for the given location and epoch year.
     *
     * Caches the result in [lastResult]. Subsequent calls to [getLastResult] return the
     * cached value without re-evaluating.
     *
     * @param latDeg     Latitude in decimal degrees.
     * @param lonDeg     Longitude in decimal degrees.
     * @param altM       Altitude in meters (0.0 if unknown).
     * @param epochYears Current decimal year (caller computes from Clock.nowMs()).
     * @return The [WmmResult] for the given parameters.
     */
    fun evaluate(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): WmmResult {
        val model = activeModel()
        val result = WmmResult(
            declination = model.getDeclination(latDeg, lonDeg, altM, epochYears),
            inclination = model.getExpectedInclination(latDeg, lonDeg, altM, epochYears),
            totalField  = model.getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)
        )
        lastResult = result
        return result
    }

    /**
     * Returns the most recent cached [WmmResult], or null if [evaluate] has never been called.
     *
     * Used by InterferenceDetector to retrieve the expected baseline without re-evaluating.
     */
    fun getLastResult(): WmmResult? = lastResult

    /**
     * Returns true when the active model changed between the most recent two [activeModel] calls.
     *
     * [CompassViewModel] uses this to update the declination info panel source label.
     *
     * Returns false if [activeModel] has been called fewer than two times (no previous call
     * to compare against). Returns true if the model ID on the last call differs from the
     * model ID on the second-to-last call.
     */
    fun hasModelChanged(): Boolean {
        val prev = previousModelId ?: return false
        val curr = currentModelId ?: return false
        return curr != prev
    }
}
