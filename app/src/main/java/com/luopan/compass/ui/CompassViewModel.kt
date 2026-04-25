package com.luopan.compass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luopan.compass.calibration.CalibrationRepository
import com.luopan.compass.confidence.ConfidenceModel
import com.luopan.compass.model.CalibrationQuality
import com.luopan.compass.confidence.NoiseVarianceTracker
import com.luopan.compass.db.DatabaseKeyManager
import com.luopan.compass.db.LuopanDatabase
import com.luopan.compass.fusion.FusionEngine
import com.luopan.compass.location.LocationRepository
import com.luopan.compass.location.LocationResult
import com.luopan.compass.magnetic.DeclinationInfo
import com.luopan.compass.magnetic.MagneticFieldModelProvider
import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.NorthType
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import com.luopan.compass.sensor.InterferenceDetector
import com.luopan.compass.sensor.InterferenceMetricsFactory
import com.luopan.compass.sensor.NoiseSpikeFilter
import com.luopan.compass.sensor.SensorLayer
import com.luopan.compass.sensor.SensorRateMonitor
import com.luopan.compass.sensor.SensorStateMonitor
import com.luopan.compass.settings.SettingsRepository
import com.luopan.compass.bearing.BearingCapturePort
import com.luopan.compass.bearing.BearingCaptureUseCase
import com.luopan.compass.bearing.BearingSnapshot
import com.luopan.compass.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class CompassViewModel(
    application: Application,
    private val modelProvider: MagneticFieldModelProvider?,
    private val locationRepository: LocationRepository?,
    private val clock: Clock,
    private val captureUseCase: BearingCapturePort? = null
) : AndroidViewModel(application) {

    private val sensorLayer = SensorLayer(application)
    private val fusionEngine = FusionEngine()
    private val spikeFilter = NoiseSpikeFilter()
    private val interferenceDetector = InterferenceDetector()
    private val confidenceModel = ConfidenceModel()
    private val noiseTracker = NoiseVarianceTracker()
    private val rateMonitor = SensorRateMonitor()
    private val stateMonitor = SensorStateMonitor()
    private val settings = SettingsRepository(application)

    private val keyManager = DatabaseKeyManager(application)
    private val db by lazy {
        LuopanDatabase.getInstance(application, keyManager.getOrCreatePassphrase())
    }
    private val calibrationRepo by lazy { CalibrationRepository(db.calibrationDao()) }

    /** North-type engine encapsulates the pure declination computation logic. */
    private val northTypeEngine = NorthTypeEngine()

    /** Exposed so observers can react to north-type changes (e.g., P4.3 toggle button). */
    val northType: StateFlow<NorthType> = northTypeEngine.northType

    /**
     * Event emitted when the user requests True North but no location is available.
     *
     * The Activity observes this flow and shows the manual coordinate entry dialog
     * (FSPEC §2.2 step 4c). northType stays MAGNETIC until coordinates are confirmed.
     *
     * P7.2 — PLAN §4 P7.2.
     */
    val showManualLocationDialog = northTypeEngine.showManualLocationDialog

    private val _uiState = MutableStateFlow(CompassUiState.INITIAL)
    val uiState: StateFlow<CompassUiState> = _uiState.asStateFlow()

    /**
     * Declination info panel data.
     *
     * Non-null only when [NorthType.TRUE] is active and a resolved location is available.
     * Null when [NorthType.MAGNETIC] is active or no location has been resolved.
     *
     * PLAN §4 P5.1 — exposed as [StateFlow] for the P5.2 declination info panel.
     */
    private val _declinationInfo = MutableStateFlow<DeclinationInfo?>(null)
    val declinationInfo: StateFlow<DeclinationInfo?> = _declinationInfo.asStateFlow()

    /**
     * Capture button enabled state (BR-CAP-08).
     *
     * Set to `false` while a [captureBearing] coroutine is running; `true` otherwise.
     * Prevents concurrent double-tap capture.
     *
     * TSPEC §3.7 — exposed as [StateFlow].
     */
    private val _captureButtonEnabled = MutableStateFlow(true)
    val captureButtonEnabled: StateFlow<Boolean> = _captureButtonEnabled.asStateFlow()

    private var calibrationAgeDays: Long = -1L
    private var calibrationQuality: CalibrationQuality = CalibrationQuality.POOR
    private var lastValidHeading: Double? = null

    // Power-saving advisory tracking
    private var lowRateStartNs: Long = -1L
    private var baselineFieldUt: Float = -1f

    // WMM expiry debounce (TSPEC §3.7): at most once per 60 s
    private var lastExpiryCheckMs: Long = -1L

    init {
        loadCalibrationAge()
        startSensorCollection()
    }

    // -----------------------------------------------------------------------
    // Public API — north type
    // -----------------------------------------------------------------------

    /**
     * Changes the active north reference type.
     *
     * Updates [northType] StateFlow and triggers a heading recomputation on the next
     * sensor frame (guaranteed ≤50 ms / one 20 Hz frame, well within the 200 ms budget).
     *
     * TSPEC §3.7: `setNorthType()` is the only entry point for north type changes.
     */
    fun setNorthType(type: NorthType) {
        northTypeEngine.setNorthType(type)
    }

    /**
     * Toggles between [NorthType.TRUE] and [NorthType.MAGNETIC].
     *
     * Convenience method used by the P4.3 toggle button.
     */
    fun toggleNorthType() {
        setNorthType(
            if (northType.value == NorthType.TRUE) NorthType.MAGNETIC else NorthType.TRUE
        )
    }

    /**
     * Requests switching to True North mode.
     *
     * Delegates to [NorthTypeEngine.requestTrueNorth] with the current [LocationResult]:
     * - If a location is available: switches to TRUE immediately.
     * - If location is [LocationResult.Unavailable]: keeps northType as MAGNETIC and emits
     *   on [showManualLocationDialog] so the Activity can show the manual entry dialog
     *   (FSPEC §2.2 step 4c).
     *
     * P7.2 — PLAN §4 P7.2.
     *
     * @return The [TrueNorthRequestResult] indicating whether the switch happened or a dialog is needed.
     */
    fun requestTrueNorth(): TrueNorthRequestResult {
        val currentLocation = locationRepository?.location?.value ?: LocationResult.Unavailable
        return northTypeEngine.requestTrueNorth(currentLocation)
    }

    /**
     * Stores manual coordinates entered by the user via the No-GPS dialog (P7.2).
     *
     * Delegates to [LocationRepository.setManualLocation]. After calling this,
     * [northType] will be switched to [NorthType.TRUE] so the heading reflects
     * the manually entered location.
     *
     * FSPEC §2.2 step 4c: "If the user enters valid coordinates and taps Use True North:
     * the toggle switches to True N, declination is computed from the manual coordinates."
     *
     * P7.2 — PLAN §4 P7.2.
     *
     * @param latDeg  Latitude in decimal degrees [-90, 90].
     * @param lonDeg  Longitude in decimal degrees [-180, 180].
     * @param altM    Altitude in meters (default 0.0 when unknown).
     */
    fun setManualLocation(latDeg: Double, lonDeg: Double, altM: Double = 0.0) {
        locationRepository?.setManualLocation(latDeg, lonDeg)
        // Switch to TRUE after setting manual location — location is now available
        northTypeEngine.setNorthType(NorthType.TRUE)
    }

    /**
     * Executes a bearing capture asynchronously.
     *
     * Records `snapshot.tapTimestampMs` — which the caller must set to `clock.nowMs()`
     * BEFORE showing any dialog (PM-T-01). Sets [captureButtonEnabled] to `false` for
     * the duration of the coroutine (BR-CAP-08), restoring it to `true` in a `finally`
     * block.
     *
     * TSPEC §3.7 — the capture dialog calls this method on "Save" confirmation.
     *
     * @param snapshot Immutable snapshot built by the capture dialog at tap time.
     */
    fun captureBearing(snapshot: BearingSnapshot) {
        val useCase = captureUseCase ?: return
        _captureButtonEnabled.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                useCase.execute(snapshot)
            } finally {
                _captureButtonEnabled.value = true
            }
        }
    }

    /**
     * Checks whether the WMM2025 model has expired and a model switch is required.
     * Debounced to at most once per 60 seconds to avoid repeated checks on rapid
     * lifecycle transitions (e.g., screen rotation).
     *
     * FSPEC §2.1 step 7: called from Activity.onResume when northType == TRUE.
     */
    fun checkWmmExpiry() {
        val nowMs = clock.nowMs()
        if (lastExpiryCheckMs >= 0L && nowMs - lastExpiryCheckMs < 60_000L) return
        lastExpiryCheckMs = nowMs
        modelProvider?.activeModel()?.isExpired()  // side-effect: updates modelProvider state
    }

    // -----------------------------------------------------------------------
    // Internal — calibration and sensor pipeline
    // -----------------------------------------------------------------------

    private fun loadCalibrationAge() {
        viewModelScope.launch(Dispatchers.IO) {
            val record = calibrationRepo.getCurrent()
            if (record != null) {
                val ageMs = clock.nowMs() - record.recorded_at
                calibrationAgeDays = TimeUnit.MILLISECONDS.toDays(ageMs)
                calibrationQuality = CalibrationQuality.valueOf(record.quality)
            }
        }
    }

    private fun startSensorCollection() {
        viewModelScope.launch(Dispatchers.Default) {
            sensorLayer.frames().collect { frame ->
                val filtered = spikeFilter.filter(frame.mag_x, frame.mag_y, frame.mag_z)
                    ?: return@collect

                val filteredFrame = frame.copy(
                    mag_x = filtered[0], mag_y = filtered[1], mag_z = filtered[2]
                )

                rateMonitor.onSample(frame.timestamp_ns)
                val sensorState = stateMonitor.update(frame.timestamp_ns)

                // Power-saving advisory: track low sensor rate
                val actualHz = rateMonitor.getActualRateHz()
                val powerSavingAdvisory: Boolean
                if (actualHz < 15.0) {
                    if (lowRateStartNs < 0) {
                        lowRateStartNs = frame.timestamp_ns
                    }
                    powerSavingAdvisory = (frame.timestamp_ns - lowRateStartNs >= 2_000_000_000L)
                } else {
                    lowRateStartNs = -1L
                    powerSavingAdvisory = false
                }

                val fusion = fusionEngine.process(filteredFrame)
                val magneticHeading = fusion.heading_deg
                val tilt = fusion.tilt_deg

                val magnitude = kotlin.math.sqrt(
                    (frame.mag_x * frame.mag_x + frame.mag_y * frame.mag_y + frame.mag_z * frame.mag_z).toDouble()
                )
                noiseTracker.addSample(magnitude)
                val noiseVariance = noiseTracker.getVariance()

                // EMA baseline (fallback when no WMM location fix is available — PLAN §5.3)
                if (baselineFieldUt < 0f) {
                    baselineFieldUt = magnitude.toFloat()
                } else {
                    baselineFieldUt = baselineFieldUt * 0.99f + magnitude.toFloat() * 0.01f
                }

                // Resolve location and active model for north-type computation
                val locationResult: LocationResult = locationRepository?.location?.value
                    ?: LocationResult.Unavailable
                val activeModel = modelProvider?.activeModel()

                // P4.2 — WMM-baseline upgrade (TSPEC §3.8):
                // When a location fix is available, evaluate the WMM model to get a
                // model-derived expected field and inclination. Keep EMA as fallback
                // when no location is available. The two paths must not interfere (PLAN §5.3).
                val hasLocation = locationResult !is LocationResult.Unavailable
                if (hasLocation && modelProvider != null && activeModel != null) {
                    val epochYears = clock.toEpochYears()
                    val coords = resolveCoords(locationResult)
                    if (coords != null) {
                        modelProvider.evaluate(coords.lat, coords.lon, coords.alt, epochYears)
                    }
                }
                val wmmResult = modelProvider?.getLastResult()

                val metrics = InterferenceMetricsFactory.build(
                    sensorFieldMagnitude = magnitude.toFloat(),
                    sensorInclination = fusion.pitch_deg.toFloat(),
                    wmmResult = wmmResult,
                    emaBaselineFallback = baselineFieldUt
                )
                interferenceDetector.updateState(metrics, frame.timestamp_ns)
                val interferenceState = interferenceDetector.getState()

                val hasGyro = sensorLayer.hasGyroscope

                // P7.1 — Extreme latitude advisory: if abs(inclination) >= 80°, near magnetic poles.
                // Confidence is capped at MODERATE; advisory flag is set in UiState.
                val extremeLatitudeActive = wmmResult != null &&
                    kotlin.math.abs(wmmResult.inclination) >= 80.0f

                val confidence = confidenceModel.compute(
                    interferencState = interferenceState,
                    tilt_deg = tilt,
                    noiseVariance = noiseVariance,
                    calibrationAgeDays = calibrationAgeDays,
                    hasGyroscope = hasGyro,
                    sensorState = sensorState,
                    calibrationQuality = calibrationQuality,
                    extremeLatitudeActive = extremeLatitudeActive
                )

                // Update lastValidHeading only when sensor is not STUCK
                if (sensorState != SensorState.STUCK) {
                    lastValidHeading = magneticHeading
                }

                // When stuck, use lastValidHeading for display
                val rawHeading = if (sensorState == SensorState.STUCK) {
                    lastValidHeading ?: magneticHeading
                } else {
                    magneticHeading
                }

                // Compute heading fields via NorthTypeEngine (handles declination + labels)
                val headingFields = if (activeModel != null) {
                    val epochYears = clock.toEpochYears()
                    northTypeEngine.computeHeadingFields(rawHeading, locationResult, activeModel, epochYears)
                } else {
                    // No model provider: magnetic mode only
                    HeadingFields(
                        displayHeading = rawHeading,
                        northLabel = "Magnetic N",
                        locationFallbackAdvisory = false,
                        fallbackMagAdvisory = false
                    )
                }

                // P5.1 — Update declination info panel data (null when MAGNETIC or no location).
                if (activeModel != null) {
                    val lastUpdatedMs = resolveLastUpdatedMs(locationResult)
                    _declinationInfo.value = DeclinationInfo.buildOrNull(
                        northType = northTypeEngine.northType.value,
                        declinationDeg = headingFields.declination_deg,
                        model = activeModel,
                        locationResult = locationResult,
                        lastUpdatedMs = lastUpdatedMs
                    )
                } else {
                    _declinationInfo.value = null
                }

                val uiState = buildUiState(
                    heading = headingFields.displayHeading,
                    northLabel = headingFields.northLabel,
                    northType = northTypeEngine.northType.value,
                    declinationDeg = headingFields.declination_deg,
                    locationFallbackAdvisory = headingFields.locationFallbackAdvisory,
                    fallbackMagAdvisory = headingFields.fallbackMagAdvisory,
                    tilt = tilt,
                    confidence = confidence,
                    interferenceState = interferenceState,
                    interferenceMetrics = metrics,
                    sensorState = sensorState,
                    hasGyro = hasGyro,
                    powerSavingAdvisory = powerSavingAdvisory,
                    extremeLatitudeAdvisory = extremeLatitudeActive,
                    locationResult = locationResult
                )
                _uiState.value = uiState
            }
        }
    }

    private fun buildUiState(
        heading: Double,
        northLabel: String,
        northType: NorthType,
        declinationDeg: Float,
        locationFallbackAdvisory: Boolean,
        fallbackMagAdvisory: Boolean,
        tilt: Double,
        confidence: OverallConfidence,
        interferenceState: InterferenceState,
        interferenceMetrics: com.luopan.compass.sensor.InterferenceMetrics,
        sensorState: SensorState,
        hasGyro: Boolean,
        powerSavingAdvisory: Boolean,
        extremeLatitudeAdvisory: Boolean = false,
        locationResult: LocationResult = LocationResult.Unavailable
    ): CompassUiState {
        val isStabilizing = sensorState == SensorState.STABILIZING
        val headingFormatted = if (isStabilizing) "---" else formatHeading(heading)
        val tiltText = if (tilt > 5.0) "%.1f°".format(tilt) else null

        // P7.2 — cache age label: shown when using a CachedFix (FSPEC §2.3 step 7)
        val locationCacheAgeLabel: String? = when (locationResult) {
            is LocationResult.CachedFix -> CacheAgeLabelFormatter.format(locationResult.ageMs)
            else -> null
        }

        val ageDays = calibrationAgeDays
        val ageLabel = when {
            ageDays < 0 -> "Uncalibrated"
            ageDays == 0L -> "Calibrated today"
            ageDays == 1L -> "1 day ago"
            else -> "$ageDays days ago"
        }
        val calDotColor = when {
            ageDays < 0 -> CalDotColor.RED
            ageDays <= 7 -> CalDotColor.GREEN
            ageDays <= 30 -> CalDotColor.AMBER
            else -> CalDotColor.RED
        }
        val showCalCta = ageDays < 0 || calDotColor == CalDotColor.RED

        return CompassUiState(
            heading_deg = heading,
            heading_formatted = headingFormatted,
            north_label = northLabel,
            north_type = northType,
            declination_deg = declinationDeg,
            confidence = confidence,
            interference_state = interferenceState,
            interference_metrics = interferenceMetrics,
            tilt_deg = tilt,
            tilt_text = tiltText,
            calibration_age_days = ageDays,
            calibration_age_label = ageLabel,
            cal_dot_color = calDotColor,
            power_saving_advisory = powerSavingAdvisory,
            no_gyroscope_advisory = !hasGyro,
            fallback_mag_advisory = fallbackMagAdvisory,
            location_fallback_advisory = locationFallbackAdvisory,
            extreme_latitude_advisory = extremeLatitudeAdvisory,
            location_cache_age_label = locationCacheAgeLabel,
            sensor_state = sensorState,
            is_stabilizing = isStabilizing,
            last_valid_heading_deg = lastValidHeading,
            show_calibration_cta = showCalCta
        )
    }

    private fun formatHeading(deg: Double): String {
        return "%05.1f°".format(deg)
    }

    /**
     * Derives the `last_updated` UTC epoch-ms from the resolved [LocationResult].
     *
     * - [LocationResult.GpsFix]: uses `clock.nowMs()` — the fix is fresh.
     * - [LocationResult.CachedFix]: `clock.nowMs() - ageMs` — when the cache was written.
     * - [LocationResult.ManualEntry]: `clock.nowMs()` — best approximation with no stored ts.
     * - [LocationResult.Unavailable]: `clock.nowMs()` (caller should not reach here; safe default).
     *
     * P5.1 — used to populate [DeclinationInfo.last_updated].
     */
    private fun resolveLastUpdatedMs(locationResult: LocationResult): Long = when (locationResult) {
        is LocationResult.GpsFix     -> clock.nowMs()
        is LocationResult.CachedFix  -> clock.nowMs() - locationResult.ageMs
        is LocationResult.ManualEntry -> clock.nowMs()
        is LocationResult.Unavailable -> clock.nowMs()
    }

    /**
     * Resolves lat/lon/alt from a [LocationResult] for WMM evaluation.
     * Returns null when no coordinates are available ([LocationResult.Unavailable]).
     */
    private fun resolveCoords(locationResult: LocationResult): Coords? = when (locationResult) {
        is LocationResult.GpsFix      -> Coords(locationResult.lat, locationResult.lon, locationResult.altM)
        is LocationResult.CachedFix   -> Coords(locationResult.lat, locationResult.lon, locationResult.altM)
        is LocationResult.ManualEntry  -> Coords(locationResult.lat, locationResult.lon, locationResult.altM)
        is LocationResult.Unavailable  -> null
    }

    private data class Coords(val lat: Double, val lon: Double, val alt: Double)

    fun onCalibrationComplete() { loadCalibrationAge() }

    // -----------------------------------------------------------------------
    // Factory for constructor injection (ViewModelProvider.Factory pattern)
    // -----------------------------------------------------------------------

    class Factory(
        private val application: Application,
        private val modelProvider: MagneticFieldModelProvider?,
        private val locationRepository: LocationRepository?,
        private val clock: Clock
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == CompassViewModel::class.java) {
                "Factory only creates CompassViewModel"
            }
            return CompassViewModel(application, modelProvider, locationRepository, clock) as T
        }
    }
}

// ---------------------------------------------------------------------------
// Clock extension — epoch year computation (TSPEC §5.3)
// ---------------------------------------------------------------------------

/**
 * Converts a [Clock.nowMs] value to a decimal year (e.g., 2025.5 ≈ 2025-07-02).
 *
 * Formula: `year + (dayOfYear - 1) / daysInYear`
 * Accounts for leap years (366 days) for correctness.
 *
 * TSPEC §5.3 — used by [CompassViewModel] to supply [epochYears] to the magnetic model.
 */
internal fun Clock.toEpochYears(): Double {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = this.nowMs()
    val year = cal.get(Calendar.YEAR)
    val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    val daysInYear = cal.getActualMaximum(Calendar.DAY_OF_YEAR).toDouble()
    return year + (dayOfYear - 1) / daysInYear
}
