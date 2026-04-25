package com.luopan.compass.magnetic

import com.luopan.compass.location.LocationResult
import com.luopan.compass.model.NorthType

/**
 * Snapshot of declination metadata surfaced by [CompassViewModel] to the declination info panel.
 *
 * **Field masking (FSPEC §2.3):** [lat_masked] and [lon_masked] are truncated to 2 decimal places
 * (approximately 1.1 km resolution at the equator) per location privacy requirements.
 *
 * **Source label rules (TSPEC §3.3, FSPEC §2.3 step 4):**
 * - WMM2025, not expired, GPS or cached fix: `"WMM2025 (valid to 2030)"`
 * - WMM2025, not expired, manual entry:       `"WMM2025 (manual location)"`
 * - AndroidGeoField or WMM2025 expired:       `"Android model — may be less accurate"`
 *
 * **Lifecycle (PLAN §4 P5.1):**
 * - [DeclinationInfo] is null when [NorthType] is [NorthType.MAGNETIC] or [NorthType.GRID],
 *   or when no location is available.
 * - Non-null only when [NorthType.TRUE] is active and a resolved location is available.
 *
 * PLAN P5.1 — `com.luopan.compass.magnetic` package.
 */
data class DeclinationInfo(
    /** Current declination in degrees (positive = east, negative = west). */
    val declination_deg: Float,

    /**
     * Human-readable label identifying the data source and its validity.
     * See source label rules above and FSPEC §2.3 step 4.
     */
    val source_label: String,

    /**
     * Latitude masked to 2 decimal places as a formatted string (e.g., "37.42").
     * Never shows full precision to protect location privacy (FSPEC §2.3 / REQ-CAPTURE-06).
     */
    val lat_masked: String,

    /**
     * Longitude masked to 2 decimal places as a formatted string (e.g., "-122.08").
     * Never shows full precision to protect location privacy.
     */
    val lon_masked: String,

    /**
     * UTC epoch-ms timestamp of the location fix used for this declination computation.
     * For GPS fix: time of the fix. For cached: time the cache was written.
     * For manual entry: time the user last entered coordinates.
     */
    val last_updated: Long,

    /**
     * ISO-8601 date string representing the end of the model's validity period.
     * `"2030-01-01"` for WMM2025; empty string for [AndroidGeoFieldModel] (no expiry concept).
     */
    val valid_until: String
) {

    companion object {

        /**
         * Builds a [DeclinationInfo] for the given inputs, or returns null when the
         * preconditions for True North computation are not met:
         * - [northType] is [NorthType.MAGNETIC] or [NorthType.GRID]
         * - [locationResult] is [LocationResult.Unavailable]
         *
         * PLAN §4 P5.1 success criteria.
         *
         * @param northType      Current north reference type.
         * @param declinationDeg Declination computed by the active [MagneticFieldModel].
         * @param model          The active [MagneticFieldModel] (determines source label).
         * @param locationResult Current resolved location (used for coordinate masking and source label).
         * @param lastUpdatedMs  UTC epoch-ms for the location fix that produced this declination.
         * @return Populated [DeclinationInfo], or null when True North is off or location unavailable.
         */
        fun buildOrNull(
            northType: NorthType,
            declinationDeg: Float,
            model: MagneticFieldModel,
            locationResult: LocationResult,
            lastUpdatedMs: Long
        ): DeclinationInfo? {
            if (northType != NorthType.TRUE) return null
            if (locationResult is LocationResult.Unavailable) return null
            return build(declinationDeg, model, locationResult, lastUpdatedMs)
        }

        /**
         * Builds a [DeclinationInfo] unconditionally for a resolved location.
         * Callers must ensure [locationResult] is not [LocationResult.Unavailable].
         *
         * @param declinationDeg Declination in degrees.
         * @param model          Active magnetic field model.
         * @param locationResult Resolved location (GpsFix, CachedFix, or ManualEntry).
         * @param lastUpdatedMs  UTC epoch-ms of the location fix.
         */
        fun build(
            declinationDeg: Float,
            model: MagneticFieldModel,
            locationResult: LocationResult,
            lastUpdatedMs: Long
        ): DeclinationInfo {
            val (lat, lon) = when (locationResult) {
                is LocationResult.GpsFix     -> locationResult.lat to locationResult.lon
                is LocationResult.CachedFix  -> locationResult.lat to locationResult.lon
                is LocationResult.ManualEntry -> locationResult.lat to locationResult.lon
                is LocationResult.Unavailable -> 0.0 to 0.0
            }

            val latMasked  = "%.2f".format(lat)
            val lonMasked  = "%.2f".format(lon)

            val sourceLabel = deriveSourceLabel(model, locationResult)

            val validUntil = when {
                model.getModelId() == "WMM2025" && !model.isExpired() -> "2030-01-01"
                else -> ""
            }

            return DeclinationInfo(
                declination_deg = declinationDeg,
                source_label    = sourceLabel,
                lat_masked      = latMasked,
                lon_masked      = lonMasked,
                last_updated    = lastUpdatedMs,
                valid_until     = validUntil
            )
        }

        /**
         * Derives the [source_label] string from the active model and location type.
         *
         * Rules (TSPEC §3.3 / FSPEC §2.3 step 4):
         * - AndroidGeoField, or WMM2025 expired → `"Android model — may be less accurate"`
         * - WMM2025 valid + ManualEntry          → `"WMM2025 (manual location)"`
         * - WMM2025 valid + GPS/cached fix       → `"WMM2025 (valid to 2030)"`
         */
        private fun deriveSourceLabel(
            model: MagneticFieldModel,
            locationResult: LocationResult
        ): String {
            if (model.getModelId() != "WMM2025" || model.isExpired()) {
                return "Android model — may be less accurate"
            }
            return if (locationResult is LocationResult.ManualEntry) {
                "WMM2025 (manual location)"
            } else {
                "WMM2025 (valid to 2030)"
            }
        }
    }
}
