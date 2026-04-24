package com.luopan.compass.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.luopan.compass.R
import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
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

        calCta.setOnClickListener {
            // TODO: launch calibration wizard (Batch 5)
        }

        observeUiState()
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
            }
        }
    }
}
