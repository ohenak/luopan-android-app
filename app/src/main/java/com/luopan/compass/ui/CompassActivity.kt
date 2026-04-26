package com.luopan.compass.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.luopan.compass.R
import com.luopan.compass.bearing.BearingSnapshot
import com.luopan.compass.calibration.ui.CalibrationWizardActivity
import com.luopan.compass.location.LocationRepository
import com.luopan.compass.magnetic.AndroidGeoFieldModel
import com.luopan.compass.magnetic.MagneticFieldModelProvider
import com.luopan.compass.magnetic.Wmm2025Model
import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.NorthType
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import com.luopan.compass.sensor.SensorLayer
import com.luopan.compass.util.WallClock
import kotlinx.coroutines.launch

class CompassActivity : AppCompatActivity() {

    // Single WallClock shared by the factory and the capture-dialog tap-timestamp recording.
    private val clock = WallClock()

    private val viewModel: CompassViewModel by viewModels {
        val locationPrefs = getSharedPreferences("location_cache", MODE_PRIVATE)
        val locationRepository = LocationRepository(locationPrefs, clock)
        val wmm = try { Wmm2025Model.fromContext(this, clock) } catch (e: Exception) { null }
        val fallback = AndroidGeoFieldModel(clock)
        val modelProvider = MagneticFieldModelProvider(wmm = wmm, fallback = fallback)
        CompassViewModel.Factory(application, modelProvider, locationRepository, clock)
    }

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

    // P4.3: North type toggle group (binary: TRUE / MAGNETIC only — AT-G-08)
    private lateinit var northTypeToggleGroup: MaterialButtonToggleGroup

    // P5.2: Declination info icon button
    private lateinit var btnDeclinationInfo: ImageButton

    // P7.1: Extreme latitude advisory banner
    private lateinit var extremeLatitudeAdvisoryBanner: TextView

    // P6.3: Bearing capture FAB
    private lateinit var fabSaveBearing: FloatingActionButton

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

    /**
     * P3.2: Launcher for location permission request using RequestMultiplePermissions.
     *
     * Result is a map of permission → granted. On grant: location chain proceeds.
     * On denial: degrade gracefully to Magnetic N and show informational Snackbar.
     * Permanent denial detection: if permission is denied AND shouldShowRationale returns false,
     * the permission has been permanently denied ("Don't ask again").
     */
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionResults ->
        val fineGranted = permissionResults[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            onLocationPermissionGranted()
        } else {
            val isPermanentDenial = !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (isPermanentDenial) {
                showOpenSettingsDialog()
            } else {
                showLocationPermissionDeniedSnackbar()
            }
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
        northTypeToggleGroup = findViewById(R.id.northTypeToggleGroup)
        btnDeclinationInfo = findViewById(R.id.btn_declination_info)
        extremeLatitudeAdvisoryBanner = findViewById(R.id.extreme_latitude_advisory_banner)
        fabSaveBearing = findViewById(R.id.fab_save_bearing)

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

        // P4.3: Wire toggle group → ViewModel north type changes
        northTypeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btn_true_n -> {
                    // P3.2: trigger permission request flow when switching to TRUE
                    requestLocationPermissionForTrueNorth()
                }
                R.id.btn_magnetic_n -> {
                    viewModel.setNorthType(NorthType.MAGNETIC)
                }
            }
        }

        // P4.3: Observe northType StateFlow and keep toggle group in sync
        lifecycleScope.launch {
            viewModel.northType.collect { northType ->
                val targetId = when (northType) {
                    NorthType.TRUE -> R.id.btn_true_n
                    NorthType.MAGNETIC, NorthType.GRID -> R.id.btn_magnetic_n
                }
                // Only update if the checked button differs to avoid listener re-entrancy
                if (northTypeToggleGroup.checkedButtonId != targetId) {
                    northTypeToggleGroup.check(targetId)
                }
            }
        }

        // P7.2: Observe showManualLocationDialog — show manual entry dialog when GPS unavailable
        lifecycleScope.launch {
            viewModel.showManualLocationDialog.collect {
                showManualCoordinateEntryDialog()
            }
        }

        // P5.2: Wire info icon → DeclinationInfoBottomSheet
        btnDeclinationInfo.setOnClickListener {
            showDeclinationInfoSheet()
        }

        // P6.3: Wire capture FAB → bearing capture flow
        fabSaveBearing.setOnClickListener {
            onCaptureFabTapped()
        }

        // P6.3: Observe capture confirmation events → show Toast
        lifecycleScope.launch {
            viewModel.captureConfirmation.collect { name ->
                val message = getString(R.string.bearing_saved_toast, name)
                Toast.makeText(this@CompassActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        // P8.3 / BR-CAP-08: Observe captureButtonEnabled → enable/disable FAB
        lifecycleScope.launch {
            viewModel.captureButtonEnabled.collect { enabled ->
                fabSaveBearing.isEnabled = enabled
            }
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

    override fun onResume() {
        super.onResume()
        if (viewModel.northType.value == NorthType.TRUE) {
            viewModel.checkWmmExpiry()
        }
    }

    private fun showNoMagnetometerError() {
        noMagErrorLayout.visibility = View.VISIBLE
        // Hide all compass UI
        compassRose.visibility = View.GONE
        headingText.visibility = View.GONE
        northLabel.visibility = View.GONE
        tiltText.visibility = View.GONE
        northTypeToggleGroup.visibility = View.GONE
        btnDeclinationInfo.visibility = View.GONE
        fabSaveBearing.visibility = View.GONE
        findViewById<View>(R.id.calDotRow).visibility = View.GONE
        calCta.visibility = View.GONE
        confidenceBadge.visibility = View.GONE
        interferenceBanner.visibility = View.GONE
        noGyroAdvisory.visibility = View.GONE
        powerSavingAdvisoryText.visibility = View.GONE
        sensorStuckText.visibility = View.GONE
        extremeLatitudeAdvisoryBanner.visibility = View.GONE
    }

    private fun launchCalibrationWizard() {
        val intent = Intent(this, CalibrationWizardActivity::class.java)
        calibrationLauncher.launch(intent)
    }

    // -------------------------------------------------------------------------
    // P5.2: Declination info panel
    // -------------------------------------------------------------------------

    /**
     * Opens [DeclinationInfoBottomSheet] with the current [DeclinationInfo] snapshot.
     *
     * The bottom sheet is safe to show whether [CompassViewModel.declinationInfo] is null
     * (Magnetic-only / no location) or non-null (True North active with a location).
     * When null, the sheet shows a "no location" state per FSPEC §2.3 step 4.
     *
     * PLAN §4 P5.2: "Trigger from info icon near heading label in activity_compass.xml."
     */
    internal fun showDeclinationInfoSheet() {
        val info = viewModel.declinationInfo.value
        val northType = viewModel.northType.value
        val sheet = DeclinationInfoBottomSheet.newInstance(info, northType)
        sheet.show(supportFragmentManager, DeclinationInfoBottomSheet.TAG)
    }

    // -------------------------------------------------------------------------
    // P6.3 / P6.4: Bearing capture flow
    // -------------------------------------------------------------------------

    /**
     * Entry point when the capture FAB is tapped.
     *
     * Records [tapTimestampMs] immediately (PM-T-01) before any dialog is shown.
     * Then decides whether to show [InterferenceWarningDialogFragment] first (step 3b)
     * or go directly to [BearingCaptureDialogFragment] (step 3c).
     *
     * FSPEC §2.5 step 3: "When the user taps the capture button: 3a snapshot taken,
     * 3b pre-capture warning dialog if InterferenceState ∈ {MODERATE, WARNING} OR POOR."
     */
    private fun onCaptureFabTapped() {
        // PM-T-01: record tap timestamp AND freeze the full UI state BEFORE any dialog is shown.
        // The frozen state is threaded through to onSave so the saved snapshot reflects the
        // compass state at the moment the user tapped the FAB, not when they dismissed the dialog.
        val tapTimestampMs = clock.nowMs()
        val tapUiState = viewModel.uiState.value

        val needsWarning = tapUiState.interference_state == InterferenceState.MODERATE
                || tapUiState.interference_state == InterferenceState.WARNING
                || tapUiState.confidence == OverallConfidence.POOR

        if (needsWarning) {
            showInterferenceWarningDialog(
                onSaveWithWarning = { showBearingCaptureDialog(tapTimestampMs, tapUiState) },
                onCancel = { /* Capture abandoned — no action */ }
            )
        } else {
            showBearingCaptureDialog(tapTimestampMs, tapUiState)
        }
    }

    /**
     * Shows the [InterferenceWarningDialogFragment].
     *
     * Exposed as `internal` for [InterferenceWarningCaptureTest] to call directly.
     *
     * FSPEC §2.5 step 3b: "pre-capture warning dialog".
     * PLAN §4 P6.3: "InterferenceWarningDialogFragment".
     */
    internal fun showInterferenceWarningDialog(
        onSaveWithWarning: () -> Unit,
        onCancel: () -> Unit
    ) {
        val dialog = InterferenceWarningDialogFragment.newInstance()
        dialog.onSaveWithWarning = onSaveWithWarning
        dialog.onCancel = onCancel
        dialog.show(supportFragmentManager, InterferenceWarningDialogFragment.TAG)
    }

    /**
     * Shows the [BearingCaptureDialogFragment].
     *
     * Exposed as `internal` for [InterferenceWarningCaptureTest] to call directly.
     *
     * @param tapTimestampMs Wall-clock ms at the capture FAB tap instant (PM-T-01).
     *                       Defaults to [clock.nowMs()] when called directly from tests.
     *
     * FSPEC §2.5 step 4: "A capture dialog appears showing bearing preview, name field,
     * notes, GPS toggle, first-capture privacy notice."
     * PLAN §4 P6.3 / P6.4.
     */
    internal fun showBearingCaptureDialog(
        tapTimestampMs: Long = clock.nowMs(),
        tapUiState: CompassUiState = viewModel.uiState.value
    ) {
        val bearingPreview = tapUiState.heading_formatted
        val bearingMeta = "${tapUiState.north_label} · ${tapUiState.confidence.name.lowercase().replaceFirstChar { it.uppercase() }}"

        // Default name "Bearing N+1" — derived from repository count asynchronously
        // For immediate display, we use the current count from the ViewModel's last known state.
        // A simple sequential counter — computed on the fly.
        val consentShown = getSharedPreferences(
            BearingCaptureDialogFragment.PREFS_FILE,
            MODE_PRIVATE
        ).getBoolean(BearingCaptureDialogFragment.KEY_CONSENT_SHOWN, false)

        val dialog = BearingCaptureDialogFragment.newInstance(
            bearingPreviewText = bearingPreview,
            bearingMeta = bearingMeta,
            defaultName = "Bearing",   // simplified default; final name from user
            consentShown = consentShown
        )

        dialog.onSave = { name, notes, includeGps ->
            // Use tapUiState (frozen at FAB tap time) — compass state fields reflect the moment
            // the user tapped, regardless of how long the dialog was open (BearingSnapshot KDoc).
            // Only user-entered fields (name, notes, includeGps) come from the save callback.

            // Resolve location at tap time from LocationRepository (TSPEC §3.6)
            val latDeg: Double?
            val lonDeg: Double?
            val altM: Double?

            if (includeGps) {
                val locationResult = viewModel.resolvedLocation()
                when (locationResult) {
                    is com.luopan.compass.location.LocationResult.GpsFix -> {
                        latDeg = locationResult.lat
                        lonDeg = locationResult.lon
                        altM = locationResult.altM
                    }
                    is com.luopan.compass.location.LocationResult.CachedFix -> {
                        latDeg = locationResult.lat
                        lonDeg = locationResult.lon
                        altM = locationResult.altM
                    }
                    is com.luopan.compass.location.LocationResult.ManualEntry -> {
                        latDeg = locationResult.lat
                        lonDeg = locationResult.lon
                        altM = locationResult.altM
                    }
                    is com.luopan.compass.location.LocationResult.Unavailable -> {
                        latDeg = null
                        lonDeg = null
                        altM = null
                    }
                }
            } else {
                latDeg = null
                lonDeg = null
                altM = null
            }

            val snapshot = BearingSnapshot(
                bearingDeg = tapUiState.heading_deg.toFloat(),
                northType = tapUiState.north_type,
                confidence = tapUiState.confidence,
                interferenceState = tapUiState.interference_state,
                fieldDeviationPct = tapUiState.interference_metrics?.fieldDeviation?.times(100f) ?: 0f,
                inclinationDeviationDeg = tapUiState.interference_metrics?.inclinationDeviation_deg ?: 0f,
                latDeg = latDeg,
                lonDeg = lonDeg,
                altM = altM,
                name = name,
                notes = notes,
                displayMode = "MODERN",
                includeLocation = includeGps,
                tapTimestampMs = tapTimestampMs
            )
            viewModel.captureBearing(snapshot)
        }

        dialog.onCancel = { /* Capture abandoned */ }
        dialog.show(supportFragmentManager, BearingCaptureDialogFragment.TAG)
    }

    // -------------------------------------------------------------------------
    // P3.2: Location permission request flow
    // -------------------------------------------------------------------------

    /**
     * Entry point called when the user first activates True North mode (P3.2).
     * This will be wired to the north type toggle in P4.3.
     *
     * Flow:
     * 1. If permission already granted → proceed with location chain
     * 2. If should show rationale → show rationale dialog → on Continue: launch request
     * 3. If first-time request or permanent denial state → launch request directly
     *    (OS determines which case via its own state; permanent denial detected in result callback)
     */
    internal fun requestLocationPermissionForTrueNorth() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            onLocationPermissionGranted()
            return
        }

        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (shouldShowRationale) {
            showLocationPermissionRationale(
                onContinue = { launchLocationPermissionRequest() },
                onNotNow = { /* Toggle remains on Magnetic N; no action needed */ }
            )
        } else {
            launchLocationPermissionRequest()
        }
    }

    /**
     * Launches the system permission request for location.
     * Uses RequestMultiplePermissions to request both FINE and COARSE (per P1.4/Manifest).
     */
    private fun launchLocationPermissionRequest() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    /**
     * Called when ACCESS_FINE_LOCATION permission has been granted (P3.2 + P4.3).
     * Activates True North mode in the ViewModel after permission is confirmed.
     * If no location is available, [CompassViewModel.showManualLocationDialog] will fire
     * and the manual entry dialog will appear (P7.2).
     */
    private fun onLocationPermissionGranted() {
        viewModel.requestTrueNorth()
    }

    /**
     * Shows a Material AlertDialog explaining why location is needed.
     * Displayed when shouldShowRequestPermissionRationale is true (user has denied once before).
     *
     * Per TSPEC §6.5 and FSPEC §2.4 step 2a / BR-LOC-04.
     *
     * @param onContinue invoked when the user taps "Continue" (proceed to system dialog)
     * @param onNotNow   invoked when the user taps "Not now" (abandon permission request)
     */
    internal fun showLocationPermissionRationale(
        onContinue: () -> Unit,
        onNotNow: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(R.string.location_permission_rationale_title)
            .setMessage(R.string.location_permission_rationale_message)
            .setPositiveButton(R.string.continue_label) { dialog, _ ->
                dialog.dismiss()
                onContinue()
            }
            .setNegativeButton(R.string.not_now) { dialog, _ ->
                dialog.dismiss()
                onNotNow()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Shows a Material AlertDialog directing the user to device settings
     * when location permission has been permanently denied.
     *
     * Per TSPEC §6.5 and FSPEC §2.4 step 3c.
     */
    internal fun showOpenSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.location_permission_settings_title)
            .setMessage(R.string.location_permission_settings_message)
            .setPositiveButton(R.string.open_settings) { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Launches the app's settings page so the user can manually grant location permission.
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    // -------------------------------------------------------------------------
    // P7.2: Manual coordinate entry dialog (GPS unavailable flow)
    // -------------------------------------------------------------------------

    /**
     * Shows a dialog asking the user to enter manual coordinates for True North when GPS
     * is unavailable (FSPEC §2.2 step 4c).
     *
     * The dialog contains:
     * - A title: "Enter coordinates for True North or use Magnetic North only"
     * - Latitude and longitude input fields
     * - "Use True North" button: activates True N with manual coordinates
     * - "Use Magnetic North" button: dismisses and keeps Magnetic N
     *
     * P7.2 — PLAN §4 P7.2, FSPEC §2.2 step 4c.
     */
    internal fun showManualCoordinateEntryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_coordinates, null)
        val latInput = dialogView.findViewById<android.widget.EditText>(R.id.et_manual_lat)
        val lonInput = dialogView.findViewById<android.widget.EditText>(R.id.et_manual_lon)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.manual_coordinates_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.use_true_north, null) // set below to prevent auto-dismiss
            .setNegativeButton(R.string.use_magnetic_north) { d, _ ->
                // User chose Magnetic North — keep MAGNETIC, do not switch
                viewModel.setNorthType(NorthType.MAGNETIC)
                d.dismiss()
            }
            .setCancelable(true)
            .create()

        dialog.setOnCancelListener {
            // Dialog dismissed without choosing — stay on Magnetic N
            viewModel.setNorthType(NorthType.MAGNETIC)
        }

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.isEnabled = false  // disabled until valid coordinates entered

            val watcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    val latText = latInput.text.toString().trim()
                    val lonText = lonInput.text.toString().trim()
                    val latValid = latText.toDoubleOrNull()?.let { it in -90.0..90.0 } == true
                    val lonValid = lonText.toDoubleOrNull()?.let { it in -180.0..180.0 } == true
                    positiveButton.isEnabled = latValid && lonValid
                }
            }
            latInput.addTextChangedListener(watcher)
            lonInput.addTextChangedListener(watcher)

            positiveButton.setOnClickListener {
                val lat = latInput.text.toString().trim().toDoubleOrNull()
                val lon = lonInput.text.toString().trim().toDoubleOrNull()
                if (lat != null && lon != null) {
                    viewModel.setManualLocation(lat, lon, 0.0)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    /**
     * Shows an informational Snackbar when the user denies the permission.
     * Informs the user they can use manual coordinate entry as an alternative.
     */
    private fun showLocationPermissionDeniedSnackbar() {
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, R.string.location_permission_denied_message, Snackbar.LENGTH_LONG)
            .show()
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
                        confidenceBadge.setBackgroundColor(getColor(R.color.confidence_high))
                    }
                    OverallConfidence.MODERATE -> {
                        confidenceBadge.text = "Moderate accuracy"
                        confidenceBadge.setBackgroundColor(getColor(R.color.confidence_moderate))
                    }
                    OverallConfidence.POOR -> {
                        confidenceBadge.text = "Poor accuracy"
                        confidenceBadge.setBackgroundColor(getColor(R.color.confidence_poor))
                    }
                    OverallConfidence.STABILIZING -> {
                        confidenceBadge.text = getString(R.string.stabilizing)
                        confidenceBadge.setBackgroundColor(getColor(R.color.confidence_moderate))
                    }
                    OverallConfidence.SENSOR_ERROR -> {
                        confidenceBadge.text = getString(R.string.sensor_error)
                        confidenceBadge.setBackgroundColor(getColor(R.color.confidence_poor))
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
                            interferenceBanner.setBackgroundColor(getColor(R.color.confidence_moderate))
                            interferenceBanner.visibility = View.VISIBLE
                        }
                    }
                    InterferenceState.WARNING -> {
                        if (!interferenceBannerDismissed) {
                            interferenceBanner.text = getString(R.string.interference_explanation)
                            interferenceBanner.setBackgroundColor(getColor(R.color.confidence_poor))
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

                // --- P7.1: Extreme latitude advisory banner ---
                // Non-dismissible; no close button; amber background set in layout.
                extremeLatitudeAdvisoryBanner.visibility =
                    if (state.extreme_latitude_advisory) View.VISIBLE else View.GONE

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
