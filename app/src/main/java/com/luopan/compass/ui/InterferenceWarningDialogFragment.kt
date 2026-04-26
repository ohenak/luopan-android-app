package com.luopan.compass.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.luopan.compass.R

/**
 * Pre-capture warning dialog shown when [InterferenceState] is MODERATE or WARNING,
 * OR [OverallConfidence] is POOR, before showing [BearingCaptureDialogFragment].
 *
 * Per FSPEC §2.5 step 3b and PLAN §4 P6.3:
 * - Primary action: "Save with warning" (amber) → continues to name/notes dialog.
 * - Secondary action: "Cancel" (grey) → abandons capture entirely.
 *
 * TSPEC §7.3 step 1: "Pre-capture warning dialog (shown only when InterferenceState ∈ {MODERATE,
 * WARNING} OR OverallConfidence == POOR)".
 *
 * Note: `interference_flag` in the saved record is derived solely from [InterferenceState]
 * (BR-10, AT-E-10). The warning dialog appearing due to POOR confidence alone does NOT
 * cause interference_flag=true in the record.
 */
class InterferenceWarningDialogFragment : DialogFragment() {

    /** Called when the user taps "Save with warning". */
    var onSaveWithWarning: (() -> Unit)? = null

    /** Called when the user taps "Cancel" or dismisses the dialog. */
    var onCancel: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_interference_warning, null)

        view.findViewById<MaterialButton>(R.id.btn_save_with_warning).setOnClickListener {
            dismiss()
            onSaveWithWarning?.invoke()
        }
        view.findViewById<MaterialButton>(R.id.btn_cancel_interference_warning).setOnClickListener {
            dismiss()
            onCancel?.invoke()
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.interference_warning_dialog_title)
            .setView(view)
            .create()
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        onCancel?.invoke()
    }

    companion object {
        const val TAG = "InterferenceWarningDialog"

        fun newInstance(): InterferenceWarningDialogFragment {
            return InterferenceWarningDialogFragment()
        }
    }
}
