package com.luopan.compass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luopan.compass.calibration.CalibrationRepository
import com.luopan.compass.confidence.ConfidenceModel
import com.luopan.compass.confidence.NoiseVarianceTracker
import com.luopan.compass.db.DatabaseKeyManager
import com.luopan.compass.db.LuopanDatabase
import com.luopan.compass.fusion.FusionEngine
import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import com.luopan.compass.sensor.InterferenceDetector
import com.luopan.compass.sensor.NoiseSpikeFilter
import com.luopan.compass.sensor.SensorLayer
import com.luopan.compass.sensor.SensorRateMonitor
import com.luopan.compass.sensor.SensorStateMonitor
import com.luopan.compass.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class CompassViewModel(application: Application) : AndroidViewModel(application) {

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

    private val _uiState = MutableStateFlow(CompassUiState.INITIAL)
    val uiState: StateFlow<CompassUiState> = _uiState.asStateFlow()

    private var calibrationAgeDays: Long = -1L
    private var lastValidHeading: Double? = null

    init {
        loadCalibrationAge()
        startSensorCollection()
    }

    private fun loadCalibrationAge() {
        viewModelScope.launch(Dispatchers.IO) {
            val record = calibrationRepo.getCurrent()
            if (record != null) {
                val ageMs = System.currentTimeMillis() - record.recorded_at
                calibrationAgeDays = TimeUnit.MILLISECONDS.toDays(ageMs)
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

                val fusion = fusionEngine.process(filteredFrame)
                val heading = fusion.heading_deg
                val tilt = fusion.tilt_deg

                val magnitude = kotlin.math.sqrt(
                    (frame.mag_x * frame.mag_x + frame.mag_y * frame.mag_y + frame.mag_z * frame.mag_z).toDouble()
                )
                noiseTracker.addSample(magnitude)
                val noiseVariance = noiseTracker.getVariance()

                // Build InterferenceMetrics: use simple deviation proxy
                val fieldDev = (noiseVariance / 50.0).toFloat().coerceIn(0f, 1f)
                val metrics = com.luopan.compass.sensor.InterferenceMetrics(
                    fieldMagnitude_uT = magnitude.toFloat(),
                    expectedField_uT = magnitude.toFloat(),
                    fieldDeviation = fieldDev,
                    inclination_deg = fusion.pitch_deg.toFloat(),
                    expectedInclination_deg = 0f,
                    inclinationDeviation_deg = abs(fusion.pitch_deg).toFloat()
                )
                interferenceDetector.updateState(metrics, frame.timestamp_ns)
                val interferenceState = interferenceDetector.getState()

                val hasGyro = sensorLayer.hasGyroscope
                val isStabilizing = sensorState == SensorState.STABILIZING

                val confidence = confidenceModel.compute(
                    interferencState = interferenceState,
                    tilt_deg = tilt,
                    noiseVariance = noiseVariance,
                    calibrationAgeDays = calibrationAgeDays,
                    hasGyroscope = hasGyro,
                    isStabilizing = isStabilizing
                )

                if (confidence != OverallConfidence.POOR && confidence != OverallConfidence.STABILIZING) {
                    lastValidHeading = heading
                }

                val uiState = buildUiState(
                    heading = heading,
                    tilt = tilt,
                    confidence = confidence,
                    interferenceState = interferenceState,
                    interferenceMetrics = metrics,
                    sensorState = sensorState,
                    hasGyro = hasGyro
                )
                _uiState.value = uiState
            }
        }
    }

    private fun buildUiState(
        heading: Double,
        tilt: Double,
        confidence: OverallConfidence,
        interferenceState: InterferenceState,
        interferenceMetrics: com.luopan.compass.sensor.InterferenceMetrics,
        sensorState: SensorState,
        hasGyro: Boolean
    ): CompassUiState {
        val isStabilizing = sensorState == SensorState.STABILIZING
        val headingFormatted = if (isStabilizing) "---" else formatHeading(heading)
        val tiltText = if (tilt > 5.0) "%.1f°".format(tilt) else null
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
            ageDays <= 30 -> CalDotColor.YELLOW
            else -> CalDotColor.RED
        }
        val showCalCta = ageDays < 0 || calDotColor == CalDotColor.RED

        return CompassUiState(
            heading_deg = heading,
            heading_formatted = headingFormatted,
            north_label = if (settings.declinationMode == SettingsRepository.DECLINATION_MAGNETIC) "Magnetic N" else "True N",
            confidence = confidence,
            interference_state = interferenceState,
            interference_metrics = interferenceMetrics,
            tilt_deg = tilt,
            tilt_text = tiltText,
            calibration_age_days = ageDays,
            calibration_age_label = ageLabel,
            cal_dot_color = calDotColor,
            power_saving_advisory = false,
            no_gyroscope_advisory = !hasGyro,
            fallback_mag_advisory = false,
            location_fallback_advisory = false,
            sensor_state = sensorState,
            is_stabilizing = isStabilizing,
            last_valid_heading_deg = lastValidHeading,
            show_calibration_cta = showCalCta
        )
    }

    private fun formatHeading(deg: Double): String {
        val d = deg.toInt()
        return when (settings.displayFormat) {
            SettingsRepository.FORMAT_DMS -> "%03d°".format(d)
            else -> "%03d°".format(d)
        }
    }

    fun onCalibrationComplete() { loadCalibrationAge() }
}
