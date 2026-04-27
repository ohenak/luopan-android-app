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

class BearingAdapter(
    private val onItemClick: (BearingRecord, Int) -> Unit
) : ListAdapter<BearingRecord, BearingAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var expandedId: String? = null

    companion object {
        object DIFF_CALLBACK : DiffUtil.ItemCallback<BearingRecord>() {
            override fun areItemsTheSame(old: BearingRecord, new: BearingRecord) = old.id == new.id
            override fun areContentsTheSame(old: BearingRecord, new: BearingRecord) = old == new
        }

        /** Formats field_deviation_pct (stored as fraction e.g. 0.25) to "25%" */
        internal fun formatFieldDeviation(pct: Float): String {
            val intPct = (pct * 100).toInt()
            return "$intPct%"
        }

        /**
         * Formats inclination_deviation_deg to integer with "°" suffix.
         * Truncation toward zero: -2.3 → "-2°", 4.7 → "4°", -0.9f → "0°"
         */
        internal fun formatInclinationDev(deg: Float): String {
            val intDeg = if (deg >= 0f) deg.toInt() else -((-deg).toInt())
            return "$intDeg°"
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.bearing_name)
        val bearingText: TextView = view.findViewById(R.id.bearing_value)
        val confidenceBadge: TextView = view.findViewById(R.id.confidence_badge)
        val timestampText: TextView = view.findViewById(R.id.bearing_timestamp)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bearing_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        val isExpanded = record.id == expandedId

        holder.nameText.text = record.name
        holder.bearingText.text = "%.1f° %s".format(record.bearing_deg, record.north_type)
        holder.confidenceBadge.text = record.confidence
        holder.timestampText.text = java.text.SimpleDateFormat.getDateTimeInstance().format(java.util.Date(record.captured_at))

        // Interference badge
        if (record.interference_flag) {
            holder.interferenceBadge.visibility = View.VISIBLE
        } else {
            holder.interferenceBadge.visibility = View.GONE
        }

        // Expanded panel
        holder.expandedPanel.visibility = if (isExpanded) View.VISIBLE else View.GONE
        if (isExpanded) {
            holder.expandedBearing.text = "%.1f° %s".format(record.bearing_deg, record.north_type)
            holder.expandedConfidence.text = record.confidence
            holder.expandedCapturedAt.text = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(record.captured_at))
            holder.expandedName.text = record.name
            holder.expandedNotes.text = record.notes ?: ""

            if (record.interference_flag) {
                holder.expandedFieldDeviation.visibility = View.VISIBLE
                holder.expandedInclinationDeviation.visibility = View.VISIBLE
                holder.expandedFieldDeviation.text = formatFieldDeviation(record.field_deviation_pct)
                holder.expandedInclinationDeviation.text = formatInclinationDev(record.inclination_deviation_deg)
            } else {
                holder.expandedFieldDeviation.visibility = View.GONE
                holder.expandedInclinationDeviation.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(record, holder.adapterPosition)
        }
    }

    /**
     * Toggles the expanded state of an item.
     * Uses notifyItemChanged() on only the two affected positions to avoid full rebind (PM TSPEC-v1 F-05).
     */
    fun toggleExpanded(itemId: String, position: Int) {
        val previousExpandedId = expandedId
        expandedId = if (expandedId == itemId) null else itemId

        val previousPosition = currentList.indexOfFirst { it.id == previousExpandedId }
        if (previousPosition != -1) notifyItemChanged(previousPosition)  // collapse old
        if (expandedId != null) notifyItemChanged(position)              // expand new
    }

    fun getExpandedId(): String? = expandedId
}
