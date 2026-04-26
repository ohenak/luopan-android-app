package com.luopan.compass.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.luopan.compass.R
import com.luopan.compass.magnetic.DeclinationInfo
import com.luopan.compass.model.NorthType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * P5.2 — Declination info bottom sheet.
 *
 * A [BottomSheetDialogFragment] that displays all [DeclinationInfo] fields:
 * - Declination value in °E/°W format with decimal precision
 * - Source label (WMM2025 / Android fallback / no location)
 * - Masked coordinates (2 decimal places)
 * - Last-updated date
 * - Model valid-until date (when available)
 * - "True North is off" note when Magnetic N is active
 *
 * Dismiss behaviour: tapping outside the sheet dismisses it automatically —
 * standard [BottomSheetDialogFragment] behaviour, no extra code required.
 *
 * TSPEC §7.2 / PLAN §4 P5.2 / FSPEC §2.3 FSPEC-DECLPANEL.
 */
class DeclinationInfoBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "DeclinationInfoBottomSheet"

        private const val ARG_DECLINATION_DEG     = "declination_deg"
        private const val ARG_SOURCE_LABEL        = "source_label"
        private const val ARG_LAT_MASKED          = "lat_masked"
        private const val ARG_LON_MASKED          = "lon_masked"
        private const val ARG_LAST_UPDATED_MS     = "last_updated_ms"
        private const val ARG_VALID_UNTIL         = "valid_until"
        private const val ARG_NORTH_TYPE          = "north_type"

        /**
         * Creates a new instance with [DeclinationInfo] payload.
         *
         * @param info        Declination info to display. May be null when no location is available
         *                    (Magnetic-only mode or no GPS fix). When null, the sheet shows a
         *                    "no location" state.
         * @param northType   Current north type, used to show the "True North is off" note.
         */
        fun newInstance(info: DeclinationInfo?, northType: NorthType): DeclinationInfoBottomSheet {
            val sheet = DeclinationInfoBottomSheet()
            sheet.arguments = Bundle().apply {
                if (info != null) {
                    putFloat(ARG_DECLINATION_DEG, info.declination_deg)
                    putString(ARG_SOURCE_LABEL, info.source_label)
                    putString(ARG_LAT_MASKED, info.lat_masked)
                    putString(ARG_LON_MASKED, info.lon_masked)
                    putLong(ARG_LAST_UPDATED_MS, info.last_updated)
                    putString(ARG_VALID_UNTIL, info.valid_until)
                }
                putString(ARG_NORTH_TYPE, northType.name)
            }
            return sheet
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_declination_info, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: Bundle()
        val northTypeStr = args.getString(ARG_NORTH_TYPE, NorthType.MAGNETIC.name)
        val northType = runCatching { NorthType.valueOf(northTypeStr) }.getOrDefault(NorthType.MAGNETIC)

        val hasInfo = args.containsKey(ARG_DECLINATION_DEG)

        val tvDeclinationValue   = view.findViewById<TextView>(R.id.tv_declination_value)
        val tvDeclinationDecimal = view.findViewById<TextView>(R.id.tv_declination_decimal)
        val tvSourceLabel        = view.findViewById<TextView>(R.id.tv_source_label)
        val tvCoordinates        = view.findViewById<TextView>(R.id.tv_coordinates)
        val tvLastUpdated        = view.findViewById<TextView>(R.id.tv_last_updated)
        val tvValidUntilHeader   = view.findViewById<TextView>(R.id.tv_valid_until_header)
        val tvValidUntil         = view.findViewById<TextView>(R.id.tv_valid_until)
        val tvTrueNorthOffNote   = view.findViewById<TextView>(R.id.tv_true_north_off_note)

        val emDash = getString(R.string.no_data_em_dash)

        if (!hasInfo) {
            // No location available — show "no location" state (FSPEC §2.3 step 4)
            tvDeclinationValue.text = emDash
            tvDeclinationDecimal.text = ""
            tvSourceLabel.text = getString(R.string.no_location_source_label)
            tvCoordinates.text = emDash
            tvLastUpdated.text = emDash
        } else {
            val declinationDeg = args.getFloat(ARG_DECLINATION_DEG)
            val sourceLabel    = args.getString(ARG_SOURCE_LABEL, emDash)
            val latMasked      = args.getString(ARG_LAT_MASKED, emDash)
            val lonMasked      = args.getString(ARG_LON_MASKED, emDash)
            val lastUpdatedMs  = args.getLong(ARG_LAST_UPDATED_MS, 0L)
            val validUntil     = args.getString(ARG_VALID_UNTIL, "")

            // Declination value: "7.7° E" or "5.3° W" (1 decimal place, FSPEC §2.3 step 3)
            val absDecl   = abs(declinationDeg)
            val direction = if (declinationDeg >= 0f) "E" else "W"
            tvDeclinationValue.text = "${"%.1f".format(absDecl)}° $direction"

            // Secondary decimal format: "(7.74°)" (2 decimal places, FSPEC §2.3 step 3)
            tvDeclinationDecimal.text = "(${"%.2f".format(declinationDeg)}°)"

            // Source label
            tvSourceLabel.text = sourceLabel

            // Coordinates: "Lat 37.42°, Lon -122.08°" (FSPEC §2.3 step 3 / TSPEC §7.2)
            tvCoordinates.text = getString(R.string.coordinates_format, latMasked, lonMasked)

            // Last updated: YYYY-MM-DD in device local timezone (FSPEC §2.3 step 5)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            tvLastUpdated.text = if (lastUpdatedMs > 0L) {
                dateFormat.format(Date(lastUpdatedMs))
            } else {
                emDash
            }

            // Valid until row: shown only when validUntil is non-empty (WMM2025 has expiry;
            // AndroidGeoField returns empty string — FSPEC §2.3 / DeclinationInfo.valid_until)
            if (validUntil.isNotEmpty()) {
                tvValidUntilHeader.visibility = View.VISIBLE
                tvValidUntil.visibility = View.VISIBLE
                tvValidUntil.text = validUntil
            } else {
                tvValidUntilHeader.visibility = View.GONE
                tvValidUntil.visibility = View.GONE
            }
        }

        // "True North is off" note — shown when northType == MAGNETIC (FSPEC §2.3 step 6)
        if (northType == NorthType.MAGNETIC && hasInfo) {
            tvTrueNorthOffNote.visibility = View.VISIBLE
        } else {
            tvTrueNorthOffNote.visibility = View.GONE
        }
    }
}
