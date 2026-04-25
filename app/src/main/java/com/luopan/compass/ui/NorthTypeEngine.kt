package com.luopan.compass.ui

import com.luopan.compass.location.LocationResult
import com.luopan.compass.magnetic.MagneticFieldModel
import com.luopan.compass.model.NorthType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Result of a [NorthTypeEngine.requestTrueNorth] call.
 *
 * P7.2 — PLAN §4 P7.2 / FSPEC §2.2 step 4c.
 */
sealed class TrueNorthRequestResult {
    /** True North was activated immediately — a location was available. */
    object Switched : TrueNorthRequestResult()
    /**
     * No location is available; the Activity must show the manual coordinate entry dialog.
     * North type remains [NorthType.MAGNETIC] until the user confirms manual coordinates.
     */
    object NeedsManualEntry : TrueNorthRequestResult()
}

/**
 * Pure, Android-free state machine for true north / magnetic north selection.
 *
 * [CompassViewModel] owns one instance of this engine and delegates all north-type
 * and heading-computation logic here. Because this class has no Android dependencies,
 * it is directly unit-testable in pure JVM tests.
 *
 * TSPEC §3.7 — [CompassViewModel] extended for north type integration.
 * PLAN §4 P4.1 success criteria.
 *
 * ## Heading computation (TSPEC §3.7, FSPEC §2.1 step 3)
 *
 * When [NorthType.TRUE] and a resolved location is available:
 *   `displayHeading = (magneticHeading + declination + 360.0) % 360.0`
 *
 * When [NorthType.MAGNETIC] or no location is available:
 *   `displayHeading = magneticHeading` (identity; declination is not applied)
 *
 * ## north_label rules (PLAN §4 P4.1, FSPEC §5.3)
 *
 * | northType | locationResult        | label                       |
 * |-----------|-----------------------|-----------------------------|
 * | TRUE      | GpsFix                | "True N"                    |
 * | TRUE      | CachedFix             | "True N"  (NOT "cached")    |
 * | TRUE      | ManualEntry           | "True N (manual location)"  |
 * | TRUE      | Unavailable           | "Magnetic N"  (forced)      |
 * | MAGNETIC  | any                   | "Magnetic N"                |
 *
 * ## Advisory flags (PLAN §4 P4.1)
 *
 * `location_fallback_advisory = true` when `northType == TRUE && location is CachedFix`
 * `fallback_mag_advisory = true` when `northType == TRUE && location is available && model.getModelId() == "AndroidGeoField"`
 *
 * ## GPS unavailable flow (P7.2)
 *
 * [requestTrueNorth] checks the current location before switching to TRUE mode.
 * When [LocationResult.Unavailable], it emits on [showManualLocationDialog] and keeps
 * northType as MAGNETIC. The Activity shows the manual coordinate entry dialog.
 */
class NorthTypeEngine {

    private val _northType = MutableStateFlow(NorthType.MAGNETIC)

    /** Observed by the ViewModel and UI layer. */
    val northType: StateFlow<NorthType> = _northType.asStateFlow()

    /**
     * Event emitted when the user requests True North but no location is available.
     *
     * The Activity observes this flow and shows the manual coordinate entry dialog
     * (FSPEC §2.2 step 4c: "Enter coordinates for True North or use Magnetic North only").
     * northType remains [NorthType.MAGNETIC] until the user confirms manual coordinates.
     *
     * P7.2 — PLAN §4 P7.2.
     */
    private val _showManualLocationDialog = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showManualLocationDialog: SharedFlow<Unit> = _showManualLocationDialog.asSharedFlow()

    /**
     * Updates the active north type.
     *
     * Accepts [NorthType.TRUE] or [NorthType.MAGNETIC]. [NorthType.GRID] is allowed by
     * the enum but must never be set via the production toggle (AT-G-08).
     */
    fun setNorthType(type: NorthType) {
        _northType.value = type
    }

    /**
     * Requests switching to True North mode.
     *
     * If a location is available (GPS fix, cached fix, or manual entry): switches to TRUE immediately
     * and returns [TrueNorthRequestResult.Switched].
     *
     * If no location is available ([LocationResult.Unavailable]): keeps northType as MAGNETIC,
     * emits on [showManualLocationDialog], and returns [TrueNorthRequestResult.NeedsManualEntry].
     * The caller must show the manual coordinate entry dialog (FSPEC §2.2 step 4c).
     *
     * P7.2 — PLAN §4 P7.2.
     *
     * @param currentLocation  The currently resolved location from [LocationRepository].
     * @return  The result indicating whether the switch happened or if a dialog is needed.
     */
    fun requestTrueNorth(currentLocation: LocationResult): TrueNorthRequestResult {
        return if (currentLocation is LocationResult.Unavailable) {
            // No location available — signal the Activity to show manual entry dialog
            _showManualLocationDialog.tryEmit(Unit)
            TrueNorthRequestResult.NeedsManualEntry
        } else {
            // Location available — switch to TRUE immediately
            _northType.value = NorthType.TRUE
            TrueNorthRequestResult.Switched
        }
    }

    /**
     * Computes the derived heading-display fields for the current sensor frame.
     *
     * This method is pure: given the same inputs it always returns the same outputs.
     * It is safe to call from any thread.
     *
     * @param magneticHeading Raw magnetic heading from the fusion engine, degrees [0, 360).
     * @param locationResult  The best currently resolved location from [LocationRepository].
     * @param activeModel     The active [MagneticFieldModel] resolved by [MagneticFieldModelProvider].
     * @param epochYears      Current decimal year, computed from [Clock.nowMs].
     * @return [HeadingFields] containing displayHeading, northLabel, and advisory flags.
     */
    fun computeHeadingFields(
        magneticHeading: Double,
        locationResult: LocationResult,
        activeModel: MagneticFieldModel,
        epochYears: Double
    ): HeadingFields {
        val type = _northType.value

        // Resolve the location coordinates if available
        val coords = resolveCoords(locationResult)

        val hasLocation = coords != null
        val isTrueNorth = type == NorthType.TRUE

        // Apply declination only when TRUE mode and location is available
        val displayHeading: Double
        val northLabel: String
        val locationFallbackAdvisory: Boolean
        val fallbackMagAdvisory: Boolean

        val declinationDeg: Float
        if (isTrueNorth && hasLocation && coords != null) {
            val declination = activeModel.getDeclination(coords.lat, coords.lon, coords.alt, epochYears)
            displayHeading = (magneticHeading + declination + 360.0) % 360.0
            declinationDeg = declination

            northLabel = when (locationResult) {
                is LocationResult.ManualEntry -> "True N (manual location)"
                else -> "True N"
            }

            locationFallbackAdvisory = locationResult is LocationResult.CachedFix

            // Android fallback advisory: shown when model is NOT WMM2025
            fallbackMagAdvisory = activeModel.getModelId() == "AndroidGeoField"
        } else {
            // Magnetic mode, or True N but no location → identity
            displayHeading = magneticHeading
            declinationDeg = 0.0f
            northLabel = "Magnetic N"
            locationFallbackAdvisory = false
            fallbackMagAdvisory = false
        }

        return HeadingFields(
            displayHeading = displayHeading,
            northLabel = northLabel,
            locationFallbackAdvisory = locationFallbackAdvisory,
            fallbackMagAdvisory = fallbackMagAdvisory,
            declination_deg = declinationDeg
        )
    }

    private data class Coords(val lat: Double, val lon: Double, val alt: Double)

    private fun resolveCoords(locationResult: LocationResult): Coords? = when (locationResult) {
        is LocationResult.GpsFix     -> Coords(locationResult.lat, locationResult.lon, locationResult.altM)
        is LocationResult.CachedFix  -> Coords(locationResult.lat, locationResult.lon, locationResult.altM)
        is LocationResult.ManualEntry -> Coords(locationResult.lat, locationResult.lon, locationResult.altM)
        is LocationResult.Unavailable -> null
    }
}
