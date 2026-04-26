package com.luopan.compass.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.luopan.compass.R

/**
 * Task 5.2 — Ring visibility BottomSheetDialogFragment.
 *
 * Displays six toggle rows (one per ring) for controlling ring visibility on the
 * Luopan dial. Changes are applied immediately via [CompassViewModel.setRingVisible]
 * and reflected in [LuopanView] in real time — no "Done" required to see the effect.
 *
 * TSPEC §6.4 / FSPEC Flow 5 / REQ §7.1.
 *
 * **Triggering:**
 * - Long-press on [LuopanView] (≥ 500 ms): [LuopanFragment] calls
 *   [show] via [LuopanView.onLongPressDetected].
 * - Three-dot overflow menu item "Show/hide rings" in the Luopan Mode action bar:
 *   accessibility alternative for TalkBack users (TSPEC §6.4 / FSPEC Flow 5 step 315).
 *
 * **Dismiss behaviour:**
 * - Tap outside the sheet (default [BottomSheetDialogFragment] behaviour).
 * - Swipe sheet downward (default).
 * - Tap "Done" button.
 *
 * **Session-only state:** Ring visibility is held in [CompassViewModel] in-memory state
 * only; it is NOT written to [SettingsRepository] (BR-09 / FSPEC Flow 5 step 6).
 */
@Suppress("DEPRECATION")  // Switch widget: acceptable for API 26 min-target
class RingVisibilityBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: CompassViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_ring_visibility, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switches = listOf(
            view.findViewById<Switch>(R.id.switchRing1),
            view.findViewById<Switch>(R.id.switchRing2),
            view.findViewById<Switch>(R.id.switchRing3),
            view.findViewById<Switch>(R.id.switchRing4),
            view.findViewById<Switch>(R.id.switchRing5),
            view.findViewById<Switch>(R.id.switchRing6)
        )

        // Initialise each switch from the current ring visibility state (FSPEC Flow 5 step 3).
        val currentVisibility = viewModel.ringVisibility.value
        switches.forEachIndexed { index, switch ->
            switch.isChecked = currentVisibility[index]

            // On toggle: update ViewModel immediately → LuopanView re-renders (Flow 5 step 4).
            switch.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setRingVisible(index, isChecked)
            }
        }

        // "Done" button dismisses the sheet (FSPEC Flow 5 step 5).
        view.findViewById<Button>(R.id.btnRingVisibilityDone).setOnClickListener {
            dismiss()
        }
    }
}
