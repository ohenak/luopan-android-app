package com.luopan.compass.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.luopan.compass.R
import android.widget.TextView

/**
 * Bearing name/notes capture dialog (P6.3 + P6.4).
 *
 * Shows:
 * - Bearing preview (snapshot heading, north type, confidence — read-only).
 * - Name field (required, max 100 chars, pre-filled with "Bearing N").
 * - Notes field (optional, max 1000 chars).
 * - GPS toggle ([MaterialSwitch], default ON) — P6.4.
 * - First-capture privacy notice (shown inline when [bearingLocationConsentShown] is false) — P6.4.
 * - "Save" button (enabled only when name is non-empty after trim).
 * - "Cancel" button.
 *
 * FSPEC §2.5 step 4, BR-15, PLAN §4 P6.3 / P6.4.
 *
 * The dialog uses a [SharedPreferences] key `bearing_location_consent_shown` (in prefs
 * file `luopan_capture_prefs`) to control the first-capture privacy notice visibility.
 * On first confirm the key is written to `true`; subsequent opens skip the notice.
 */
class BearingCaptureDialogFragment : DialogFragment() {

    /**
     * Called when the user taps "Save".
     *
     * @param name      Trimmed bearing name (non-empty, max 100 chars).
     * @param notes     Trimmed notes or null if not entered.
     * @param includeGps True if the GPS toggle is ON.
     */
    var onSave: ((name: String, notes: String?, includeGps: Boolean) -> Unit)? = null

    /** Called when the user taps "Cancel" or dismisses the dialog. */
    var onCancel: (() -> Unit)? = null

    // Arguments
    private var bearingPreviewText: String = ""
    private var bearingMeta: String = ""
    private var defaultName: String = ""
    private var bearingLocationConsentShown: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Re-read consent state every time dialog is created (supports rotation restore)
        bearingLocationConsentShown = requireContext()
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONSENT_SHOWN, false)

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_bearing_capture, null)

        val tvBearingPreview = view.findViewById<TextView>(R.id.tv_bearing_preview)
        val tvBearingMeta = view.findViewById<TextView>(R.id.tv_bearing_meta)
        val etName = view.findViewById<TextInputEditText>(R.id.et_bearing_name)
        val etNotes = view.findViewById<TextInputEditText>(R.id.et_bearing_notes)
        val switchGps = view.findViewById<MaterialSwitch>(R.id.switch_include_gps)
        val tvPrivacyNotice = view.findViewById<TextView>(R.id.tv_location_privacy_notice)
        val btnSave = view.findViewById<MaterialButton>(R.id.btn_save_bearing_confirm)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_cancel_bearing_capture)

        // Populate bearing preview (immutable snapshot values — do not update while dialog is open)
        tvBearingPreview.text = bearingPreviewText
        tvBearingMeta.text = bearingMeta

        // Pre-fill name field and select all so user can immediately type a replacement
        etName.setText(defaultName)
        etName.selectAll()

        // P6.4: Show/hide first-capture privacy notice based on SharedPreferences
        tvPrivacyNotice.visibility = if (bearingLocationConsentShown) View.GONE else View.VISIBLE

        // Save button enabled only when name is non-empty after trim (FSPEC §2.5 step 4)
        btnSave.isEnabled = defaultName.isNotBlank()
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                btnSave.isEnabled = (s?.toString()?.trim()?.isNotEmpty() == true)
            }
        })

        btnSave.setOnClickListener {
            val name = etName.text?.toString()?.trim() ?: ""
            val notesRaw = etNotes.text?.toString()?.trim()
            val notes = if (notesRaw.isNullOrEmpty()) null else notesRaw
            val includeGps = switchGps.isChecked

            // P6.4: Mark consent as shown on first confirm (best-effort write before DB insert)
            if (!bearingLocationConsentShown) {
                requireContext()
                    .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_CONSENT_SHOWN, true)
                    .apply()
            }

            dismiss()
            onSave?.invoke(name, notes, includeGps)
        }

        btnCancel.setOnClickListener {
            dismiss()
            onCancel?.invoke()
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.bearing_capture_dialog_title)
            .setView(view)
            .create()
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        onCancel?.invoke()
    }

    companion object {
        const val TAG = "BearingCaptureDialog"
        const val PREFS_FILE = "luopan_capture_prefs"
        const val KEY_CONSENT_SHOWN = "bearing_location_consent_shown"

        /**
         * Creates a new instance with the provided snapshot values.
         *
         * @param bearingPreviewText Formatted heading string (e.g., "045.0°").
         * @param bearingMeta        North type + confidence label (e.g., "True N · High").
         * @param defaultName        Pre-filled name (e.g., "Bearing 3").
         * @param consentShown       Current value of [KEY_CONSENT_SHOWN] SharedPreferences key.
         */
        fun newInstance(
            bearingPreviewText: String,
            bearingMeta: String,
            defaultName: String,
            consentShown: Boolean
        ): BearingCaptureDialogFragment {
            return BearingCaptureDialogFragment().also {
                it.bearingPreviewText = bearingPreviewText
                it.bearingMeta = bearingMeta
                it.defaultName = defaultName
                it.bearingLocationConsentShown = consentShown
            }
        }
    }
}
