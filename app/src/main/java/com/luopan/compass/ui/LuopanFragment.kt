package com.luopan.compass.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.luopan.compass.R
import com.luopan.compass.luopan.LuopanState
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.settings.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Full implementation of LuopanFragment — Task 3.2.
 *
 * Inflates [fragment_luopan.xml], obtains [CompassViewModel] via [activityViewModels],
 * and observes [CompassViewModel.uiState], [CompassViewModel.zoomScale], and
 * [CompassViewModel.ringVisibility] to drive:
 *   - [LuopanView] (custom dial — Task 4.1 adds rendering; stub draws background only)
 *   - Numeric readout panel (placeholder TextViews — Task 4.2 wires real fields)
 *   - "Lock 向" / "Clear 向" button with PM-Q01 fix
 *   - 坐向 overlay CardView with V3-F01 displayXiangBearing resolution
 *
 * TSPEC §6.2, FSPEC Flow 1 / Flow 3 / Flow 4, PM-Q01, V3-F01.
 */
class LuopanFragment : Fragment() {

    private val viewModel: CompassViewModel by activityViewModels()

    // Views — bound in onViewCreated, released in onDestroyView
    private lateinit var luopanView: LuopanView
    private lateinit var tvMountain: TextView
    private lateinit var tvEarthlyBranch: TextView
    private lateinit var tvTrigram: TextView
    private lateinit var tvBearing: TextView
    private lateinit var tvNorthType: TextView
    private lateinit var tvFenJin: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var btnLockXiang: Button
    private lateinit var zuoXiangOverlay: CardView
    private lateinit var tvXiangOverlay: TextView
    private lateinit var tvZuoOverlay: TextView

    // ---------------------------------------------------------------------------
    // Fragment lifecycle
    // ---------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Set root view background to #2C0E0E immediately on inflate (FSPEC §1.4 / Flow 1 step 2)
        val root = inflater.inflate(R.layout.fragment_luopan, container, false)
        root.setBackgroundColor(0xFF2C0E0E.toInt())
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        luopanView = view.findViewById(R.id.luopanView)
        tvMountain = view.findViewById(R.id.tvMountain)
        tvEarthlyBranch = view.findViewById(R.id.tvEarthlyBranch)
        tvTrigram = view.findViewById(R.id.tvTrigram)
        tvBearing = view.findViewById(R.id.tvBearing)
        tvNorthType = view.findViewById(R.id.tvNorthType)
        tvFenJin = view.findViewById(R.id.tvFenJin)
        tvConfidence = view.findViewById(R.id.tvConfidence)
        btnLockXiang = view.findViewById(R.id.btnLockXiang)
        zuoXiangOverlay = view.findViewById(R.id.zuoXiangOverlay)
        tvXiangOverlay = view.findViewById(R.id.tvXiangOverlay)
        tvZuoOverlay = view.findViewById(R.id.tvZuoOverlay)

        // Observe main UI state — drives dial, readout, lock button, overlay
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                val luopan = state.luopan

                // Drive LuopanView dial rotation (Task 4.1 implements rendering)
                luopanView.setBearingDeg(luopan.bearingDeg)

                // V3-F01 fix: pass displayXiangBearing (not xiangBearing) to LuopanView
                // so tick mark positioning uses display-reference bearing (TSPEC §6.2)
                val displayXiang = resolveDisplayXiangBearing(
                    luopan.displayXiangBearing,
                    luopan.xiangBearing
                )
                luopanView.setLockState(luopan.isLockActive, displayXiang)

                updateNumericReadout(luopan)
                updateLockButton(luopan)
                updateZuoXiangOverlay(luopan)
            }
        }

        // Observe ring visibility — drives per-ring show/hide in LuopanView (Flow 5)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ringVisibility.collect { visible ->
                luopanView.setRingVisible(visible)
            }
        }

        // Observe zoom scale — drives dial scale transform in LuopanView (Flow 6)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.zoomScale.collect { scale ->
                luopanView.setZoomScale(scale)
                luopanView.requestLayout()   // trigger onSizeChanged for geometry recompute (TSPEC §6.2)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Persist display mode so cold start restores Luopan Mode (TSPEC §8.4, FSPEC Flow 1 step 8)
        viewModel.setDisplayMode(SettingsRepository.DISPLAY_MODE_LUOPAN)
    }

    // ---------------------------------------------------------------------------
    // Readout panel update (TSPEC §6.2 updateNumericReadout, FSPEC Flow 3)
    // Task 4.2 will extend this with full field wiring; for now the TextViews
    // receive placeholder values to prove the wiring compiles and renders.
    // ---------------------------------------------------------------------------

    private fun updateNumericReadout(state: LuopanState) {
        tvMountain.text = state.mountainChar
        tvEarthlyBranch.text = state.earthlyBranchChar
        tvTrigram.text = buildTrigramField(state)
        tvBearing.text = formatBearingDisplay(state.bearingDeg)
        tvNorthType.text = state.northLabel
        tvFenJin.text = state.fenJinLabel ?: getString(R.string.fen_jin_na)
        tvConfidence.text = formatConfidenceBadge(state.confidence)
        tvConfidence.setBackgroundColor(confidenceColor(state.confidence))
    }

    // ---------------------------------------------------------------------------
    // Lock button (TSPEC §6.2 updateLockButton, BR-05, PM-Q01 fix)
    // ---------------------------------------------------------------------------

    private fun updateLockButton(state: LuopanState) {
        val canLock = state.confidence == OverallConfidence.HIGH ||
                      state.confidence == OverallConfidence.MODERATE

        // PM-Q01 fix: enabled when can lock a new bearing OR when active (to allow "Clear 向")
        btnLockXiang.isEnabled = isLockButtonEnabled(state.confidence, state.isLockActive)

        btnLockXiang.text = if (state.isLockActive)
            getString(R.string.clear_xiang) else getString(R.string.lock_xiang)

        btnLockXiang.setOnClickListener {
            when {
                state.isLockActive -> viewModel.clearXiang()
                canLock -> viewModel.lockXiang()
                else -> Toast.makeText(
                    requireContext(),
                    R.string.cannot_lock_heading_unreliable,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ---------------------------------------------------------------------------
    // 坐向 overlay update (TSPEC §6.2 updateZuoXiangOverlay, FSPEC Flow 4, V3-F01)
    // ---------------------------------------------------------------------------

    private fun updateZuoXiangOverlay(state: LuopanState) {
        if (!state.isLockActive) {
            zuoXiangOverlay.visibility = View.GONE
            return
        }

        zuoXiangOverlay.visibility = View.VISIBLE

        // V3-F01 fix: use displayXiangBearing (display-reference) not xiangBearing (True N)
        // for the overlay text. Falls back to xiangBearing when displayXiangBearing is null.
        val displayXiang = resolveDisplayXiangBearing(
            state.displayXiangBearing,
            state.xiangBearing
        )
        val displayZuo = resolveDisplayZuoBearing(
            state.displayZuoBearing,
            state.zuoBearing
        )

        tvXiangOverlay.text = if (displayXiang != null && state.xiangMountain != null)
            formatOverlayXiangLine(state.xiangMountain, displayXiang, state.northLabel)
        else
            "向: —"

        tvZuoOverlay.text = if (displayZuo != null && state.zuoMountain != null)
            formatOverlayZuoLine(state.zuoMountain, displayZuo, state.northLabel)
        else
            "坐: —"
    }

    // ---------------------------------------------------------------------------
    // Pure helper functions (tested in LuopanFragmentLogicTest)
    // ---------------------------------------------------------------------------

    /**
     * Button enable rule (PM-Q01 fix):
     * enabled when confidence is HIGH or MODERATE (canLock), OR when lock is already active.
     */
    internal fun isLockButtonEnabled(
        confidence: OverallConfidence,
        isLockActive: Boolean
    ): Boolean {
        val canLock = confidence == OverallConfidence.HIGH ||
                      confidence == OverallConfidence.MODERATE
        return canLock || isLockActive
    }

    /**
     * V3-F01 fix: prefer displayXiangBearing; fall back to xiangBearing when null.
     * Ensures the overlay and LuopanView tick mark use the display-reference bearing,
     * not the stored True North bearing (TSPEC §6.2).
     */
    internal fun resolveDisplayXiangBearing(
        displayXiangBearing: Float?,
        xiangBearing: Float?
    ): Float? = displayXiangBearing ?: xiangBearing

    /**
     * V3-F01 fix for 坐: prefer displayZuoBearing; fall back to zuoBearing when null.
     */
    internal fun resolveDisplayZuoBearing(
        displayZuoBearing: Float?,
        zuoBearing: Float?
    ): Float? = displayZuoBearing ?: zuoBearing

    /**
     * Formats the bearing to 1 decimal place with "°" suffix (TSPEC §6.2).
     */
    internal fun formatBearingDisplay(bearingDeg: Float): String = "%.1f°".format(bearingDeg)

    /**
     * Formats the 向 overlay line (FSPEC §4a step 7a, AC-07/08/10/11/23).
     */
    internal fun formatOverlayXiangLine(
        xiangMountain: String,
        displayBearing: Float,
        northLabel: String
    ): String = "向: $xiangMountain (%.1f° $northLabel)".format(displayBearing)

    /**
     * Formats the 坐 overlay line (FSPEC §4a step 7a, AC-07/08/10/11/23).
     */
    internal fun formatOverlayZuoLine(
        zuoMountain: String,
        displayBearing: Float,
        northLabel: String
    ): String = "坐: $zuoMountain (%.1f° $northLabel)".format(displayBearing)

    /**
     * Builds the trigram display field: symbol + name + direction.
     */
    private fun buildTrigramField(state: LuopanState): String {
        if (state.trigramSymbol == "—") return "—"
        return "${state.trigramSymbol} ${state.trigramName} ${state.trigramDirection}"
    }

    /**
     * Maps [OverallConfidence] to a badge label string (BR-05, FSPEC Flow 3).
     */
    private fun formatConfidenceBadge(confidence: OverallConfidence): String = when (confidence) {
        OverallConfidence.HIGH         -> getString(R.string.confidence_high)
        OverallConfidence.MODERATE     -> getString(R.string.confidence_moderate)
        OverallConfidence.POOR         -> getString(R.string.confidence_poor)
        OverallConfidence.STABILIZING  -> getString(R.string.confidence_stabilizing)
        OverallConfidence.SENSOR_ERROR -> getString(R.string.confidence_sensor_error)
    }

    /**
     * Maps [OverallConfidence] to a badge background color (BR-05).
     */
    private fun confidenceColor(confidence: OverallConfidence): Int = when (confidence) {
        OverallConfidence.HIGH         -> Color.parseColor("#2E7D32") // green
        OverallConfidence.MODERATE     -> Color.parseColor("#F57F17") // amber
        OverallConfidence.POOR         -> Color.parseColor("#C62828") // red
        OverallConfidence.STABILIZING  -> Color.parseColor("#F57F17") // amber
        OverallConfidence.SENSOR_ERROR -> Color.parseColor("#C62828") // red
    }
}
