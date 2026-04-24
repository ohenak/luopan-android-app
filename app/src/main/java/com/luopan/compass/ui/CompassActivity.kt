package com.luopan.compass.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.banner.MaterialBanner
import com.luopan.compass.R
import com.luopan.compass.calibration.ui.CalibrationWizardActivity
import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.OverallConfidence
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

    private var calBanner: MaterialBanner? = null
    private var bannerDismissedThisSession = false

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

        calCta.setOnClickListener { launchCalibrationWizard() }

        observeUiState()
    }

    private fun launchCalibrationWizard() {
        val intent = Intent(this, CalibrationWizardActivity::class.java)
        calibrationLauncher.launch(intent)
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                compassRose.setHeading(state.heading_deg.toFloat())
                compassRose.setConfidence(state.confidence.name)

                headingText.text = state.heading_formatted
                northLabel.text = state.north_label

                tiltText.text = state.tilt_text
                tiltText.visibility = if (state.tilt_text != null) View.VISIBLE else View.GONE

                calAgeLabel.text = state.calibration_age_label
                calDot.setBackgroundColor(
                    when (state.cal_dot_color) {
                        CalDotColor.GREEN -> getColor(R.color.cal_dot_green)
                        CalDotColor.YELLOW -> getColor(R.color.cal_dot_yellow)
                        CalDotColor.RED -> getColor(R.color.cal_dot_red)
                    }
                )

                calCta.visibility = if (state.show_calibration_cta) View.VISIBLE else View.GONE

                // MaterialBanner for first-launch CTA
                if (state.show_calibration_cta && !bannerDismissedThisSession) {
                    showCalibrationBanner()
                } else if (!state.show_calibration_cta) {
                    dismissBannerPermanently()
                }
            }
        }
    }

    private fun showCalibrationBanner() {
        if (calBanner != null) return
        val banner = MaterialBanner(this, null, 0)
        banner.setContentTextResId(R.string.calibrate_now)
        banner.addButton(R.string.calibrate_now, 0) { launchCalibrationWizard() }
        banner.addButton(android.R.string.cancel, 0) {
            bannerDismissedThisSession = true
            banner.dismiss()
            calBanner = null
        }
        calBanner = banner
        // Attach to root layout — simplified: show as a floating overlay
        // In a real fragment-based implementation, add to coordinator layout
        banner.show()
    }

    private fun dismissBannerPermanently() {
        calBanner?.dismiss()
        calBanner = null
    }
}
