package com.luopan.compass.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.luopan.compass.R
import com.luopan.compass.calibration.ui.CalibrationWizardActivity
import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import com.luopan.compass.sensor.SensorLayer
import kotlinx.coroutines.launch

class CompassActivity : AppCompatActivity() {

    private val viewModel: CompassViewModel by viewModels()

    private lateinit var compassRose: CompassRoseView
    private lateinit var headingText: TextView
    private lateinit var northLabel: TextView
    private lateinit var tiltText: TextView
    private lateinit var calDot: View
    private lateinit var calAgeLabel: TextView
    private lateinit var calCta: Button

    // New views — T-6-01, T-6-05
    private lateinit var confidenceBadge: TextView
    private lateinit var interferenceBanner: TextView
    private lateinit var noGyroAdvisory: TextView
    private lateinit var powerSavingAdvisoryText: TextView
    private lateinit var sensorStuckText: TextView
    private lateinit var noMagErrorLayout: LinearLayout

    private var calSnackbar: Snackbar? = null
    private var bannerDismissedThisSession = false
    private var interferenceBannerDismissed = false
    private val wakeLockManager by lazy { WakeLockManager(this) }

    private val calibrationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onCalibrationComplete()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compass)

        compassRose = findViewById(R.id.compassRose)
        headingText = findViewById(R.id.headingText)
        northLabel = findViewById(R.id.northLabel)
        tiltText = findViewById(R.id.tiltText)
        calDot = findViewById(R.id.calDot)
        calAgeLabel = findViewById(R.id.calAgeLabel)
        calCta = findViewById(R.id.calCta)

        confidenceBadge = findViewById(R.id.confidence_badge)
        interferenceBanner = findViewById(R.id.interference_banner)
        noGyroAdvisory = findViewById(R.id.no_gyro_advisory)
        powerSavingAdvisoryText = findViewById(R.id.power_saving_advisory_text)
        sensorStuckText = findViewById(R.id.sensor_stuck_text)
        noMagErrorLayout = findViewById(R.id.no_mag_error_layout)

        // T-6-05: Check for magnetometer before proceeding
        val sensorLayer = SensorLayer(this)
        if (!sensorLayer.hasMagnetometer) {
            showNoMagnetometerError()
            return
        }

        calCta.setOnClickListener { launchCalibrationWizard() }
        interferenceBanner.setOnClickListener {
            interferenceBannerDismissed = true
            interferenceBanner.visibility = View.GONE
        }

        observeUiState()
    }

    override fun onStart() {
        super.onStart()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        wakeLockManager.acquire()
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        wakeLockManager.release()
    }

    private fun showNoMagnetometerError() {
        noMagErrorLayout.visibility = View.VISIBLE
        // Hide all compass UI
        compassRose.visibility = View.GONE
        headingText.visibility = View.GONE
        northLabel.visibility = View.GONE
        tiltText.visibility = View.GONE
        findViewById<View>(R.id.calDotRow).visibility = View.GONE
        calCta.visibility = View.GONE
        confidenceBadge.visibility = View.GONE
        interferenceBanner.visibility = View.GONE
        noGyroAdvisory.visibility = View.GONE
        powerSavingAdvisoryText.visibility = View.GONE
        sensorStuckText.visibility = View.GONE
    }

    private fun launchCalibrationWizard() {
        val intent = Intent(this, CalibrationWizardActivity::class.java)
        calibrationLauncher.launch(intent)
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->

                // --- Heading update: freeze when sensor is STUCK ---
                if (state.sensor_state == SensorState.STUCK) {
                    val frozenHeading = state.last_valid_heading_deg
                    if (frozenHeading != null) {
                        compassRose.setHeading(frozenHeading.toFloat())
                        headingText.text = "%05.1f°".format(frozenHeading)
                    }
                    sensorStuckText.text = getString(R.string.sensor_not_responding)
                    sensorStuckText.visibility = View.VISIBLE
                } else {
                    compassRose.setHeading(state.heading_deg.toFloat())
                    headingText.text = state.heading_formatted
                    sensorStuckText.visibility = View.GONE
                }

                compassRose.setConfidence(state.confidence.name)
                northLabel.text = state.north_label

                tiltText.text = state.tilt_text
                tiltText.visibility = if (state.tilt_text != null) View.VISIBLE else View.GONE

                calAgeLabel.text = state.calibration_age_label
                calDot.setBackgroundColor(
                    when (state.cal_dot_color) {
                        CalDotColor.GREEN -> getColor(R.color.cal_dot_green)
                        CalDotColor.AMBER -> getColor(R.color.cal_dot_yellow)
                        CalDotColor.RED -> getColor(R.color.cal_dot_red)
                    }
                )

                calCta.visibility = if (state.show_calibration_cta) View.VISIBLE else View.GONE

                // --- Confidence badge ---
                when (state.confidence) {
                    OverallConfidence.HIGH -> {
                        confidenceBadge.text = "High accuracy"
                        confidenceBadge.setBackgroundColor(Color.parseColor("#4CAF50"))
                    }
                    OverallConfidence.MODERATE -> {
                        confidenceBadge.text = "Moderate accuracy"
                        confidenceBadge.setBackgroundColor(Color.parseColor("#FFC107"))
                    }
                    OverallConfidence.POOR -> {
                        confidenceBadge.text = "Poor accuracy"
                        confidenceBadge.setBackgroundColor(Color.parseColor("#F44336"))
                    }
                    OverallConfidence.STABILIZING -> {
                        confidenceBadge.text = getString(R.string.stabilizing)
                        confidenceBadge.setBackgroundColor(Color.parseColor("#FFC107"))
                    }
                    OverallConfidence.SENSOR_ERROR -> {
                        confidenceBadge.text = getString(R.string.sensor_error)
                        confidenceBadge.setBackgroundColor(Color.parseColor("#F44336"))
                    }
                }
                confidenceBadge.visibility = View.VISIBLE

                // --- Interference banner ---
                when (state.interference_state) {
                    InterferenceState.CLEAR -> {
                        interferenceBanner.visibility = View.GONE
                        interferenceBannerDismissed = false
                    }
                    InterferenceState.MODERATE -> {
                        if (!interferenceBannerDismissed) {
                            interferenceBanner.text = getString(R.string.interference_explanation)
                            interferenceBanner.setBackgroundColor(Color.parseColor("#FFC107"))
                            interferenceBanner.visibility = View.VISIBLE
                        }
                    }
                    InterferenceState.WARNING -> {
                        if (!interferenceBannerDismissed) {
                            interferenceBanner.text = getString(R.string.interference_explanation)
                            interferenceBanner.setBackgroundColor(Color.parseColor("#F44336"))
                            interferenceBanner.visibility = View.VISIBLE
                        }
                    }
                }

                // --- No-gyroscope advisory ---
                if (state.no_gyroscope_advisory) {
                    noGyroAdvisory.text = getString(R.string.no_gyroscope_advisory)
                    noGyroAdvisory.visibility = View.VISIBLE
                } else {
                    noGyroAdvisory.visibility = View.GONE
                }

                // --- Power-saving advisory ---
                if (state.power_saving_advisory) {
                    powerSavingAdvisoryText.text = getString(R.string.power_saving_advisory)
                    powerSavingAdvisoryText.visibility = View.VISIBLE
                } else {
                    powerSavingAdvisoryText.visibility = View.GONE
                }

                // Snackbar CTA for first-launch calibration prompt
                if (state.show_calibration_cta && !bannerDismissedThisSession) {
                    showCalibrationBanner()
                } else if (!state.show_calibration_cta) {
                    dismissBannerPermanently()
                }
            }
        }
    }

    private fun showCalibrationBanner() {
        if (calSnackbar?.isShown == true) return
        val root = findViewById<View>(android.R.id.content)
        calSnackbar = Snackbar.make(root, R.string.calibration_cta_title, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.calibrate_now) { launchCalibrationWizard() }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(sb: Snackbar, event: Int) {
                    if (event == DISMISS_EVENT_SWIPE || event == DISMISS_EVENT_ACTION) {
                        bannerDismissedThisSession = true
                    }
                    calSnackbar = null
                }
            })
        calSnackbar?.show()
    }

    private fun dismissBannerPermanently() {
        calSnackbar?.dismiss()
        calSnackbar = null
    }
}
