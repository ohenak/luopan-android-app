package com.luopan.compass.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luopan.compass.R
import com.luopan.compass.bearing.BearingRecord

/**
 * RecyclerView adapter for the bearing history list.
 *
 * [ListAdapter] backed by [DiffUtil.ItemCallback] — uses [DIFF_CALLBACK] to compute diffs
 * on the background thread, so large lists (500 records) stay on the main thread only for
 * the final bind operations.
 *
 * Expand/collapse: tracks [expandedId] (at most one item expanded at a time).
 * [toggleExpanded] uses two targeted [notifyItemChanged] calls instead of
 * [notifyDataSetChanged] to avoid full-list rebind on 500-record lists (PM TSPEC-v1 F-05).
 *
 * Format functions: [formatInclinationDev] and [formatFieldDeviation] are `internal` pure
 * functions on the companion object so they can be called in JVM unit tests without Android
 * runtime (TSPEC §9.12; BearingAdapterFormatTest).
 *
 * Phase 4 — PLAN E-6; TSPEC §7.2
 */
class BearingAdapter : ListAdapter<BearingRecord, BearingAdapter.ViewHolder>(DIFF_CALLBACK) {

    /** ID of the currently expanded item, or null if no item is expanded. */
    private var expandedId: String? = null

    // ─── DiffUtil callback ──────────────────────────────────────────────────────

    companion object {

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BearingRecord>() {
            override fun areItemsTheSame(oldItem: BearingRecord, newItem: BearingRecord): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: BearingRecord, newItem: BearingRecord): Boolean =
                oldItem == newItem
        }

        // ─── Pure format functions (TSPEC §7.2, §9.12) ───────────────────────

        /**
         * Formats inclination deviation in degrees with truncation toward zero.
         *
         * Examples: -2.3f → "-2°", 4.7f → "4°", 0f → "0°", -0.9f → "0°"
         *
         * AT-HIST-05-D: truncation toward zero (not floor) — matches [Float.toInt] semantics.
         * Negative values: `-((-deg).toInt())` ensures correct truncation (e.g., -2.9 → -2,
         * not -3 which floor would give).
         */
        internal fun formatInclinationDev(deg: Float): String {
            val truncated = if (deg >= 0f) deg.toInt() else -((-deg).toInt())
            return "$truncated°"
        }

        /**
         * Formats field deviation as an integer percentage.
         *
         * [fieldDeviationPct] is stored as a fraction (e.g., 0.25 for 25%).
         * Display: integer truncation of fraction × 100, "%" suffix.
         *
         * Examples: 0.25 → "25%", 0.0 → "0%", 2.5 → "250%"
         */
        internal fun formatFieldDeviation(fieldDeviationPct: Float): String {
            val pct = (fieldDeviationPct * 100).toInt()  // truncation toward zero
            return "$pct%"
        }
    }

    // ─── Expand / collapse ──────────────────────────────────────────────────────

    /**
     * Toggles the expanded state of the item at [position] with [itemId].
     *
     * At most one item is expanded at a time. Tapping an expanded item collapses it.
     * Tapping another item collapses the current and expands the new one.
     *
     * Uses [notifyItemChanged] on only the two affected positions (PM TSPEC-v1 F-05).
     * NEVER calls [notifyDataSetChanged] (PROP-HIST-022).
     *
     * @param itemId  The ID of the item to toggle.
     * @param position The adapter position of the item.
     * @param onNotifyItemChanged Callback for testability — defaults to [notifyItemChanged].
     * @param onNotifyDataSetChanged Callback for testability — must NOT be called.
     */
    fun toggleExpanded(
        itemId: String,
        position: Int,
        onNotifyItemChanged: (Int) -> Unit = { notifyItemChanged(it) },
        onNotifyDataSetChanged: () -> Unit = { notifyDataSetChanged() }
    ) {
        val previousExpandedId = expandedId
        expandedId = if (expandedId == itemId) null else itemId

        // Notify only the two affected items — O(1) view work regardless of list size.
        if (previousExpandedId != null) {
            val previousPosition = currentList.indexOfFirst { it.id == previousExpandedId }
            if (previousPosition != -1) {
                onNotifyItemChanged(previousPosition)  // collapse previously expanded row
            }
        }
        if (expandedId != null) {
            onNotifyItemChanged(position)  // expand newly selected row
        } else if (previousExpandedId == itemId) {
            // Collapsed same item — notify it
            onNotifyItemChanged(position)
        }
    }

    // ─── ViewHolder ─────────────────────────────────────────────────────────────

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.name_text)
        val bearingText: TextView = view.findViewById(R.id.bearing_text)
        val confidenceBadge: TextView = view.findViewById(R.id.confidence_badge)
        val timestampText: TextView = view.findViewById(R.id.timestamp_text)
        val interferenceBadge: TextView = view.findViewById(R.id.interference_badge)
        val expandedPanel: View = view.findViewById(R.id.expanded_panel)
        val expandedBearing: TextView = view.findViewById(R.id.expanded_bearing)
        val expandedConfidence: TextView = view.findViewById(R.id.expanded_confidence)
        val expandedCapturedAt: TextView = view.findViewById(R.id.expanded_captured_at)
        val expandedName: TextView = view.findViewById(R.id.expanded_name)
        val expandedNotes: TextView = view.findViewById(R.id.expanded_notes)
        val expandedFieldDeviation: TextView = view.findViewById(R.id.expanded_field_deviation)
        val expandedInclinationDeviation: TextView = view.findViewById(R.id.expanded_inclination_deviation)
    }

    // ─── ListAdapter overrides ──────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bearing_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        val isExpanded = record.id == expandedId

        // ── Summary row fields ──
        holder.nameText.text = record.name
        holder.bearingText.text = formatBearingText(record)
        holder.confidenceBadge.text = record.confidence
        holder.timestampText.text = record.captured_at.toString()

        // ── Interference badge: VISIBLE only when interference_flag = true ──
        holder.interferenceBadge.visibility =
            if (record.interference_flag) View.VISIBLE else View.GONE

        // ── Expand/collapse panel ──
        holder.expandedPanel.visibility = if (isExpanded) View.VISIBLE else View.GONE

        if (isExpanded) {
            bindExpandedFields(holder, record)
        }

        // ── Row click toggles expansion ──
        holder.itemView.setOnClickListener {
            toggleExpanded(record.id, holder.absoluteAdapterPosition)
        }
    }

    private fun bindExpandedFields(holder: ViewHolder, record: BearingRecord) {
        holder.expandedBearing.text = formatBearingText(record)
        holder.expandedConfidence.text = record.confidence
        holder.expandedCapturedAt.text = record.captured_at.toString()
        holder.expandedName.text = record.name
        holder.expandedNotes.text = record.notes ?: ""

        if (record.interference_flag) {
            holder.expandedFieldDeviation.visibility = View.VISIBLE
            holder.expandedInclinationDeviation.visibility = View.VISIBLE
            holder.expandedFieldDeviation.text = formatFieldDeviation(record.field_deviation_pct)
            holder.expandedInclinationDeviation.text =
                formatInclinationDev(record.inclination_deviation_deg)
        } else {
            holder.expandedFieldDeviation.visibility = View.GONE
            holder.expandedInclinationDeviation.visibility = View.GONE
        }
    }

    private fun formatBearingText(record: BearingRecord): String {
        val northLabel = when (record.north_type) {
            "TRUE" -> "True North"
            "MAGNETIC" -> "Mag North"
            else -> record.north_type
        }
        return "%05.1f° %s".format(record.bearing_deg, northLabel)
    }
}
