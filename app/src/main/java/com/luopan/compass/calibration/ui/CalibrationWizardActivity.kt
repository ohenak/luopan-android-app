package com.luopan.compass.calibration.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.luopan.compass.R
import com.luopan.compass.calibration.CalibrationEngine
import com.luopan.compass.calibration.CalibrationRepository
import com.luopan.compass.calibration.CalibrationResult
import com.luopan.compass.db.DatabaseKeyManager
import com.luopan.compass.db.LuopanDatabase
import com.luopan.compass.model.CalibrationQuality
import com.luopan.compass.sensor.SensorLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalibrationWizardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUALITY = "calibration_quality"
        private const val MIN_SAMPLES = 200
        private const val MIN_COVERAGE = 0.15f
        private const val AUTO_COVERAGE = 0.80f
        private const val AUTO_RMS_THRESHOLD = 1.0f
    }

    private enum class Step { PRE_CHECK, GUIDANCE, COLLECTION, RESULT, DONE }

    private var currentStep = Step.PRE_CHECK
    private val engine = CalibrationEngine()
    private lateinit var sensorLayer: SensorLayer
    private lateinit var repo: CalibrationRepository
    private var collectionJob: Job? = null
    private var computedResult: CalibrationResult? = null

    // Views — pre-check
    private lateinit var stepPreCheck: View
    private lateinit var btnContinueAnyway: Button
    private lateinit var btnMoveAndRetry: Button

    // Views — guidance
    private lateinit var stepGuidance: View
    private lateinit var btnStartCollection: Button
    private lateinit var btnCancelGuidance: Button

    // Views — collection
    private lateinit var stepCollection: View
    private lateinit var tvSampleCount: TextView
    private lateinit var tvCoverageX: TextView
    private lateinit var tvCoverageY: TextView
    private lateinit var tvCoverageZ: TextView
    private lateinit var tvHint: TextView
    private lateinit var btnDone: Button
    private lateinit var btnCancelCollection: Button
    private lateinit var sphereView: SphereVisualizationView

    // Views — result
    private lateinit var stepResult: View
    private lateinit var tvQualityLabel: TextView
    private lateinit var tvResidual: TextView
    private lateinit var btnPrimary: Button
    private lateinit var btnRetry: Button
    private lateinit var tvFairCaption: TextView
    private lateinit var progressComputing: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration_wizard)

        val keyManager = DatabaseKeyManager(this)
        val db = LuopanDatabase.getInstance(this, keyManager.getOrCreatePassphrase())
        repo = CalibrationRepository(db.calibrationDao())
        sensorLayer = SensorLayer(this)

        bindViews()
        showStep(Step.PRE_CHECK)
    }

    private fun bindViews() {
        stepPreCheck = findViewById(R.id.stepPreCheck)
        btnContinueAnyway = findViewById(R.id.btnContinueAnyway)
        btnMoveAndRetry = findViewById(R.id.btnMoveAndRetry)

        stepGuidance = findViewById(R.id.stepGuidance)
        btnStartCollection = findViewById(R.id.btnStartCollection)
        btnCancelGuidance = findViewById(R.id.btnCancelGuidance)

        stepCollection = findViewById(R.id.stepCollection)
        tvSampleCount = findViewById(R.id.tvSampleCount)
        tvCoverageX = findViewById(R.id.tvCoverageX)
        tvCoverageY = findViewById(R.id.tvCoverageY)
        tvCoverageZ = findViewById(R.id.tvCoverageZ)
        tvHint = findViewById(R.id.tvHint)
        btnDone = findViewById(R.id.btnDone)
        btnCancelCollection = findViewById(R.id.btnCancelCollection)
        sphereView = findViewById(R.id.sphereView)

        stepResult = findViewById(R.id.stepResult)
        tvQualityLabel = findViewById(R.id.tvQualityLabel)
        tvResidual = findViewById(R.id.tvResidual)
        btnPrimary = findViewById(R.id.btnPrimary)
        btnRetry = findViewById(R.id.btnRetry)
        tvFairCaption = findViewById(R.id.tvFairCaption)
        progressComputing = findViewById(R.id.progressComputing)

        btnContinueAnyway.setOnClickListener { showStep(Step.GUIDANCE) }
        btnMoveAndRetry.setOnClickListener { showStep(Step.PRE_CHECK) }
        btnStartCollection.setOnClickListener { startCollection() }
        btnCancelGuidance.setOnClickListener { confirmCancel() }
        btnCancelCollection.setOnClickListener { confirmCancel() }
        btnDone.setOnClickListener { triggerFit() }
        btnRetry.setOnClickListener { retryCollection() }
    }

    private fun showStep(step: Step) {
        currentStep = step
        stepPreCheck.visibility = if (step == Step.PRE_CHECK) View.VISIBLE else View.GONE
        stepGuidance.visibility = if (step == Step.GUIDANCE) View.VISIBLE else View.GONE
        stepCollection.visibility = if (step == Step.COLLECTION) View.VISIBLE else View.GONE
        stepResult.visibility = if (step == Step.RESULT || step == Step.DONE) View.VISIBLE else View.GONE
    }

    private fun startCollection() {
        engine.clearSamples()
        sphereView.clearSamples()
        btnDone.isEnabled = false
        showStep(Step.COLLECTION)

        collectionJob = lifecycleScope.launch(Dispatchers.Default) {
            sensorLayer.frames().collect { frame ->
                val corrX = frame.mag_x - frame.mag_bias_x
                val corrY = frame.mag_y - frame.mag_bias_y
                val corrZ = frame.mag_z - frame.mag_bias_z
                engine.addSample(corrX, corrY, corrZ)
                sphereView.addSample(corrX, corrY, corrZ)

                val count = engine.getSampleCount()
                val (cx, cy, cz) = engine.getPerAxisCoverage()

                val doneEnabled = count >= MIN_SAMPLES && cx >= MIN_COVERAGE && cy >= MIN_COVERAGE && cz >= MIN_COVERAGE

                withContext(Dispatchers.Main) {
                    tvSampleCount.text = "$count samples"
                    tvCoverageX.text = "X: ${(cx * 100).toInt()}%"
                    tvCoverageY.text = "Y: ${(cy * 100).toInt()}%"
                    tvCoverageZ.text = "Z: ${(cz * 100).toInt()}%"
                    btnDone.isEnabled = doneEnabled

                    tvHint.text = when {
                        cz < cx && cz < cy -> "Rotate device to cover the Z axis"
                        cy < cx -> "Rotate device to cover the Y axis"
                        cx < 0.20f -> "Continue rotating in all directions"
                        else -> "Good coverage — tap Done when ready"
                    }

                    // Auto-complete
                    if (count >= MIN_SAMPLES && cx >= AUTO_COVERAGE && cy >= AUTO_COVERAGE && cz >= AUTO_COVERAGE) {
                        triggerFit()
                    }
                }
            }
        }
    }

    private fun triggerFit() {
        collectionJob?.cancel()
        collectionJob = null
        showStep(Step.RESULT)
        progressComputing.visibility = View.VISIBLE
        tvQualityLabel.visibility = View.GONE
        tvResidual.visibility = View.GONE
        btnPrimary.visibility = View.GONE
        btnRetry.visibility = View.GONE
        tvFairCaption.visibility = View.GONE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { engine.computeCalibration() }
            progressComputing.visibility = View.GONE
            if (result == null) {
                retryCollection()
                return@launch
            }
            computedResult = result
            showResultScreen(result)
        }
    }

    private fun showResultScreen(result: CalibrationResult) {
        tvQualityLabel.visibility = View.VISIBLE
        tvResidual.visibility = View.VISIBLE
        btnPrimary.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE

        tvResidual.text = "Fit accuracy: %.1f µT".format(result.residualMicroTesla)

        when (result.quality) {
            CalibrationQuality.GOOD -> {
                tvQualityLabel.text = "GOOD"
                tvQualityLabel.setTextColor(getColor(R.color.cal_dot_green))
                tvFairCaption.visibility = View.GONE
                btnPrimary.text = "Save Calibration"
                btnPrimary.setOnClickListener { saveCalibration(result) }
                btnRetry.text = "Retry for better results"
            }
            CalibrationQuality.FAIR -> {
                tvQualityLabel.text = "FAIR"
                tvQualityLabel.setTextColor(getColor(R.color.cal_dot_yellow))
                tvFairCaption.visibility = View.VISIBLE
                tvFairCaption.text = "Confidence will be capped at Moderate. Retry for High accuracy."
                btnPrimary.text = "Save — Moderate Accuracy"
                btnPrimary.setOnClickListener { saveCalibration(result) }
                btnRetry.text = "Retry"
            }
            CalibrationQuality.POOR -> {
                tvQualityLabel.text = "POOR"
                tvQualityLabel.setTextColor(getColor(R.color.cal_dot_red))
                tvFairCaption.visibility = View.GONE
                btnPrimary.text = "Retry"
                btnPrimary.setOnClickListener { retryCollection() }
                btnRetry.text = "Accept Anyway"
                btnRetry.setOnClickListener { confirmPoorSave(result) }
            }
        }
    }

    private fun confirmPoorSave(result: CalibrationResult) {
        AlertDialog.Builder(this)
            .setTitle("Poor calibration quality")
            .setMessage("This calibration is Poor quality. Your confidence level will be Poor and readings may not be reliable. Save anyway?")
            .setPositiveButton("Yes, save") { _, _ -> saveCalibration(result) }
            .setNegativeButton("Go back", null)
            .show()
    }

    private fun saveCalibration(result: CalibrationResult) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { repo.save(result, System.currentTimeMillis()) }
            val intent = Intent().putExtra(EXTRA_QUALITY, result.quality.name)
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    private fun retryCollection() {
        collectionJob?.cancel()
        collectionJob = null
        engine.clearSamples()
        sphereView.clearSamples()
        showStep(Step.GUIDANCE)
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setTitle("Cancel calibration?")
            .setMessage("Data will be discarded.")
            .setPositiveButton("Cancel calibration") { _, _ ->
                collectionJob?.cancel()
                engine.clearSamples()
                setResult(RESULT_CANCELED)
                finish()
            }
            .setNegativeButton("Keep going", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        collectionJob?.cancel()
    }
}
