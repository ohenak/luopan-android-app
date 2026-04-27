package com.luopan.compass.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.luopan.compass.R
import com.luopan.compass.bearing.BearingSnapshot
import com.luopan.compass.calibration.ui.CalibrationWizardActivity
import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.NorthType
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import com.luopan.compass.sensor.SensorLayer
import com.luopan.compass.settings.SettingsRepository
import com.luopan.compass.util.WallClock
import kotlinx.coroutines.launch

/**
 * Modern Mode fragment — the existing CompassActivity UI migrated to a Fragment.
 *
 * All UI binding, observer setup, permission handling, calibration wizard calls,
 * bearing capture flow, and declination info sheet are handled here.
 *
 * The shared [CompassViewModel] is obtained via [activityViewModels] so sensor data
 * is never interrupted during mode transitions (TSPEC §1.3 / FSPEC §1.4).
 *
 * Permission request launchers are declared in this Fragment (not the Activity) because
 * [registerForActivityResult] must be called before [onStart] and the launchers need
 * access to Fragment-specific UI (Snackbar on fragment root, AlertDialogs with fragment
 * context). Keeping them here avoids cross-component coupling.
 *
 * Task 3.1 — Navigation graph and Activity migration.
 */
class ModernCompassFragment : Fragment() {

    // Shared ViewModel scoped to CompassActivity (TSPEC §1.3)
    private val viewModel: CompassViewModel by activityViewModels()

    // Wall clock — created once per fragment instance; shared with bearing capture timestamp.
    private val clock = WallClock()

    // -----------------------------------------------------------------------
    // View references — set in onViewCreated
    // -----------------------------------------------------------------------
    private lateinit var compassRose: CompassRoseView
    private lateinit var headingText: TextView
    private lateinit var northLabel: TextView
    private lateinit var tiltText: TextView
    private lateinit var calDot: View
    private lateinit var calAgeLabel: TextView
    private lateinit var calCta: Button
    private lateinit var confidenceBadge: TextView
    private lateinit var interferenceBanner: TextView
    private lateinit var noGyroAdvisory: TextView
    private lateinit var powerSavingAdvisoryText: TextView
    private lateinit var sensorStuckText: TextView
    private lateinit var noMagErrorLayout: LinearLayout
    private lateinit var northTypeToggleGroup: MaterialButtonToggleGroup
    private lateinit var btnDeclinationInfo: ImageButton
    private lateinit var extremeLatitudeAdvisoryBanner: TextView
    private lateinit var fabSaveBearing: FloatingActionButton

    // Session state
    private var calSnackbar: Snackbar? = null
    private var bannerDismissedThisSession = false
    private var interferenceBannerDismissed = false

    // -----------------------------------------------------------------------
    // Permission launchers — must be registered before onStart
    // -----------------------------------------------------------------------

    private val calibrationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onCalibrationComplete()
        }
    }

    /**
     * Location permission launcher (P3.2).
     *
     * On grant: location chain proceeds via [onLocationPermissionGranted].
     * On denial: degrade gracefully to Magnetic N; show informational Snackbar.
     * Permanent denial: detected when shouldShowRationale returns false after denial.
     */
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionResults ->
        val fineGranted = permissionResults[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            onLocationPermissionGranted()
        } else {
            val isPermanentDenial = !ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (isPermanentDenial) {
                showOpenSettingsDialog()
            } else {
                showLocationPermissionDeniedSnackbar()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Fragment lifecycle
    // -----------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_modern_compass, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)

        // T-6-05: Check for magnetometer before proceeding
        val sensorLayer = SensorLayer(requireContext())
        if (!sensorLayer.hasMagnetometer) {
            showNoMagnetometerError()
            return
        }

        wireListeners()
        observeNorthType()
        observeManualLocationDialog()
        observeCaptureConfirmation()
        observeCaptureButtonEnabled()
        observeUiState()
    }

    override fun onStart() {
        super.onStart()
        viewModel.setDisplayMode(SettingsRepository.DISPLAY_MODE_MODERN)
    }

    // -----------------------------------------------------------------------
    // View binding
    // -----------------------------------------------------------------------

    private fun bindViews(view: View) {
        compassRose = view.findViewById(R.id.compassRose)
        headingText = view.findViewById(R.id.headingText)
        northLabel = view.findViewById(R.id.northLabel)
        tiltText = view.findViewById(R.id.tiltText)
        calDot = view.findViewById(R.id.calDot)
        calAgeLabel = view.findViewById(R.id.calAgeLabel)
        calCta = view.findViewById(R.id.calCta)
        confidenceBadge = view.findViewById(R.id.confidence_badge)
        interferenceBanner = view.findViewById(R.id.interference_banner)
        noGyroAdvisory = view.findViewById(R.id.no_gyro_advisory)
        powerSavingAdvisoryText = view.findViewById(R.id.power_saving_advisory_text)
        sensorStuckText = view.findViewById(R.id.sensor_stuck_text)
        noMagErrorLayout = view.findViewById(R.id.no_mag_error_layout)
        northTypeToggleGroup = view.findViewById(R.id.northTypeToggleGroup)
        btnDeclinationInfo = view.findViewById(R.id.btn_declination_info)
        extremeLatitudeAdvisoryBanner = view.findViewById(R.id.extreme_latitude_advisory_banner)
        fabSaveBearing = view.findViewById(R.id.fab_save_bearing)
    }

    // -----------------------------------------------------------------------
    // Listener wiring
    // -----------------------------------------------------------------------

    private fun wireListeners() {
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

        // P5.2: Wire info icon → DeclinationInfoBottomSheet
        btnDeclinationInfo.setOnClickListener {
            showDeclinationInfoSheet()
        }

        // P6.3: Wire capture FAB → bearing capture flow
        fabSaveBearing.setOnClickListener {
            onCaptureFabTapped()
        }
    }

    // -----------------------------------------------------------------------
    // Observers
    // -----------------------------------------------------------------------

    private fun observeNorthType() {
        // P4.3: Observe northType StateFlow and keep toggle group in sync
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.northType.collect { northType ->
                    val targetId = when (northType) {
                        NorthType.TRUE -> R.id.btn_true_n
                        NorthType.MAGNETIC, NorthType.GRID -> R.id.btn_magnetic_n
                    }
                    if (northTypeToggleGroup.checkedButtonId != targetId) {
                        northTypeToggleGroup.check(targetId)
                    }
                }
            }
        }
    }

    private fun observeManualLocationDialog() {
        // P7.2: Observe showManualLocationDialog — show manual entry dialog when GPS unavailable
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.showManualLocationDialog.collect {
                    showManualCoordinateEntryDialog()
                }
            }
        }
    }

    private fun observeCaptureConfirmation() {
        // P6.3: Observe capture confirmation events → show Toast
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.captureConfirmation.collect { name ->
                    val message = getString(R.string.bearing_saved_toast, name)
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeCaptureButtonEnabled() {
        // P8.3 / BR-CAP-08: Observe captureButtonEnabled → enable/disable FAB
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.captureButtonEnabled.collect { enabled ->
                    fabSaveBearing.isEnabled = enabled
                }
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                        CalDotColor.GREEN -> requireContext().getColor(R.color.cal_dot_green)
                        CalDotColor.AMBER -> requireContext().getColor(R.color.cal_dot_yellow)
                        CalDotColor.RED -> requireContext().getColor(R.color.cal_dot_red)
                    }
                )

                calCta.visibility = if (state.show_calibration_cta) View.VISIBLE else View.GONE

                // --- Confidence badge ---
                when (state.confidence) {
                    OverallConfidence.HIGH -> {
                        confidenceBadge.text = "High accuracy"
                        confidenceBadge.setBackgroundColor(requireContext().getColor(R.color.confidence_high))
                    }
                    OverallConfidence.MODERATE -> {
                        confidenceBadge.text = "Moderate accuracy"
                        confidenceBadge.setBackgroundColor(requireContext().getColor(R.color.confidence_moderate))
                    }
                    OverallConfidence.POOR -> {
                        confidenceBadge.text = "Poor accuracy"
                        confidenceBadge.setBackgroundColor(requireContext().getColor(R.color.confidence_poor))
                    }
                    OverallConfidence.STABILIZING -> {
                        confidenceBadge.text = getString(R.string.stabilizing)
                        confidenceBadge.setBackgroundColor(requireContext().getColor(R.color.confidence_moderate))
                    }
                    OverallConfidence.SENSOR_ERROR -> {
                        confidenceBadge.text = getString(R.string.sensor_error)
                        confidenceBadge.setBackgroundColor(requireContext().getColor(R.color.confidence_poor))
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
                            interferenceBanner.setBackgroundColor(requireContext().getColor(R.color.confidence_moderate))
                            interferenceBanner.visibility = View.VISIBLE
                        }
                    }
                    InterferenceState.WARNING -> {
                        if (!interferenceBannerDismissed) {
                            interferenceBanner.text = getString(R.string.interference_explanation)
                            interferenceBanner.setBackgroundColor(requireContext().getColor(R.color.confidence_poor))
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
    }

    // -----------------------------------------------------------------------
    // No-magnetometer error
    // -----------------------------------------------------------------------

    private fun showNoMagnetometerError() {
        noMagErrorLayout.visibility = View.VISIBLE
        compassRose.visibility = View.GONE
        headingText.visibility = View.GONE
        northLabel.visibility = View.GONE
        tiltText.visibility = View.GONE
        northTypeToggleGroup.visibility = View.GONE
        btnDeclinationInfo.visibility = View.GONE
        fabSaveBearing.visibility = View.GONE
        view?.findViewById<View>(R.id.calDotRow)?.visibility = View.GONE
        calCta.visibility = View.GONE
        confidenceBadge.visibility = View.GONE
        interferenceBanner.visibility = View.GONE
        noGyroAdvisory.visibility = View.GONE
        powerSavingAdvisoryText.visibility = View.GONE
        sensorStuckText.visibility = View.GONE
        extremeLatitudeAdvisoryBanner.visibility = View.GONE
    }

    // -----------------------------------------------------------------------
    // Calibration
    // -----------------------------------------------------------------------

    private fun launchCalibrationWizard() {
        val intent = Intent(requireContext(), CalibrationWizardActivity::class.java)
        calibrationLauncher.launch(intent)
    }

    private fun showCalibrationBanner() {
        if (calSnackbar?.isShown == true) return
        val root = requireView()
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

    // -----------------------------------------------------------------------
    // P5.2: Declination info panel
    // -----------------------------------------------------------------------

    /**
     * Opens [DeclinationInfoBottomSheet] with the current [DeclinationInfo] snapshot.
     */
    internal fun showDeclinationInfoSheet() {
        val info = viewModel.declinationInfo.value
        val northType = viewModel.northType.value
        val sheet = DeclinationInfoBottomSheet.newInstance(info, northType)
        sheet.show(parentFragmentManager, DeclinationInfoBottomSheet.TAG)
    }

    // -----------------------------------------------------------------------
    // P6.3 / P6.4: Bearing capture flow
    // -----------------------------------------------------------------------

    /**
     * Entry point when the capture FAB is tapped.
     *
     * Records tap timestamp immediately (PM-T-01) before any dialog is shown.
     */
    private fun onCaptureFabTapped() {
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
     * Exposed as `internal` for test access.
     */
    internal fun showInterferenceWarningDialog(
        onSaveWithWarning: () -> Unit,
        onCancel: () -> Unit
    ) {
        val dialog = InterferenceWarningDialogFragment.newInstance()
        dialog.onSaveWithWarning = onSaveWithWarning
        dialog.onCancel = onCancel
        dialog.show(parentFragmentManager, InterferenceWarningDialogFragment.TAG)
    }

    /**
     * Shows the [BearingCaptureDialogFragment].
     * Exposed as `internal` for test access.
     */
    internal fun showBearingCaptureDialog(
        tapTimestampMs: Long = clock.nowMs(),
        tapUiState: CompassUiState = viewModel.uiState.value
    ) {
        val bearingPreview = tapUiState.heading_formatted
        val bearingMeta = "${tapUiState.north_label} · ${tapUiState.confidence.name.lowercase().replaceFirstChar { it.uppercase() }}"

        val consentShown = requireActivity().getSharedPreferences(
            BearingCaptureDialogFragment.PREFS_FILE,
            android.content.Context.MODE_PRIVATE
        ).getBoolean(BearingCaptureDialogFragment.KEY_CONSENT_SHOWN, false)

        val dialog = BearingCaptureDialogFragment.newInstance(
            bearingPreviewText = bearingPreview,
            bearingMeta = bearingMeta,
            defaultName = "Bearing",
            consentShown = consentShown
        )

        dialog.onSave = { name, notes, includeGps ->
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
        dialog.show(parentFragmentManager, BearingCaptureDialogFragment.TAG)
    }

    // -----------------------------------------------------------------------
    // P3.2: Location permission request flow
    // -----------------------------------------------------------------------

    /**
     * Entry point called when the user activates True North mode (P3.2 / P4.3).
     */
    internal fun requestLocationPermissionForTrueNorth() {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            onLocationPermissionGranted()
            return
        }

        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            requireActivity(),
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

    private fun launchLocationPermissionRequest() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun onLocationPermissionGranted() {
        viewModel.requestTrueNorth()
    }

    internal fun showLocationPermissionRationale(
        onContinue: () -> Unit,
        onNotNow: () -> Unit
    ) {
        AlertDialog.Builder(requireContext())
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

    internal fun showOpenSettingsDialog() {
        AlertDialog.Builder(requireContext())
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

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    private fun showLocationPermissionDeniedSnackbar() {
        val root = requireView()
        Snackbar.make(root, R.string.location_permission_denied_message, Snackbar.LENGTH_LONG)
            .show()
    }

    // -----------------------------------------------------------------------
    // P7.2: Manual coordinate entry dialog (GPS unavailable flow)
    // -----------------------------------------------------------------------

    internal fun showManualCoordinateEntryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_coordinates, null)
        val latInput = dialogView.findViewById<android.widget.EditText>(R.id.et_manual_lat)
        val lonInput = dialogView.findViewById<android.widget.EditText>(R.id.et_manual_lon)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.manual_coordinates_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.use_true_north, null)
            .setNegativeButton(R.string.use_magnetic_north) { d, _ ->
                viewModel.setNorthType(NorthType.MAGNETIC)
                d.dismiss()
            }
            .setCancelable(true)
            .create()

        dialog.setOnCancelListener {
            viewModel.setNorthType(NorthType.MAGNETIC)
        }

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.isEnabled = false

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
}
