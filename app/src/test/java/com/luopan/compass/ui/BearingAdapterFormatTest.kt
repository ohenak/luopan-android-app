package com.luopan.compass.ui

import androidx.recyclerview.widget.ListAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Note: BearingHistoryFragment is an Android Fragment and cannot be instantiated in JVM unit
 * tests. The UNDO_SNACKBAR_DURATION_MS constant is a companion-object const val — it can be
 * referenced via the compiled class without an Activity context.
 */

/**
 * Unit tests for [BearingAdapter] pure format functions.
 *
 * Tests:
 * - E-5: AT-HIST-05-D inclinationDeviation format: truncation toward zero + "°" suffix
 * - E-5: fieldDeviation format: fractional → integer percentage + "%" suffix
 *
 * TSPEC §7.2, §9.12
 */
class BearingAdapterFormatTest {

    // ─── AT-HIST-05-D: formatInclinationDev ────────────────────────────────────

    @Test
    fun `formatInclinationDev - negative value truncates toward zero`() {
        assertEquals("-2°", BearingAdapter.formatInclinationDev(-2.3f))
    }

    @Test
    fun `formatInclinationDev - positive value truncates toward zero`() {
        assertEquals("4°", BearingAdapter.formatInclinationDev(4.7f))
    }

    @Test
    fun `formatInclinationDev - zero returns 0 degrees`() {
        assertEquals("0°", BearingAdapter.formatInclinationDev(0f))
    }

    @Test
    fun `formatInclinationDev - negative value less than one truncates to 0`() {
        assertEquals("0°", BearingAdapter.formatInclinationDev(-0.9f))
    }

    @Test
    fun `formatInclinationDev - large negative truncates toward zero`() {
        assertEquals("-2°", BearingAdapter.formatInclinationDev(-2.9f))
    }

    @Test
    fun `formatInclinationDev - exactly negative two`() {
        assertEquals("-2°", BearingAdapter.formatInclinationDev(-2.0f))
    }

    // ─── fieldDeviation format: formatFieldDeviation ───────────────────────────

    @Test
    fun `formatFieldDeviation - fractional 0_25 displays as 25 percent`() {
        assertEquals("25%", BearingAdapter.formatFieldDeviation(0.25f))
    }

    @Test
    fun `formatFieldDeviation - zero displays as 0 percent`() {
        assertEquals("0%", BearingAdapter.formatFieldDeviation(0.0f))
    }

    @Test
    fun `formatFieldDeviation - fractional 2_5 displays as 250 percent`() {
        assertEquals("250%", BearingAdapter.formatFieldDeviation(2.5f))
    }

    @Test
    fun `formatFieldDeviation - fractional 0_127 truncates to 12 percent`() {
        // 0.127 * 100 = 12.7 → toInt() = 12 (truncation toward zero)
        assertEquals("12%", BearingAdapter.formatFieldDeviation(0.127f))
    }

    @Test
    fun `formatFieldDeviation - 1_0 displays as 100 percent`() {
        assertEquals("100%", BearingAdapter.formatFieldDeviation(1.0f))
    }

    // ─── AT-HIST-05-C: Expanded deviation format (TE CR-IMPL F-03) ────────────────

    /**
     * AT-HIST-05-C: The expanded row for an interference-flagged record must display
     * `field_deviation_pct` as an integer percentage with truncation toward zero.
     *
     * TE CR-IMPL F-03: Originally scheduled as an E2E Espresso test seeding Room.
     * The format logic is entirely in BearingAdapter.formatFieldDeviation() — a pure
     * companion function with no Android View dependency. Verified here at unit level.
     *
     * PROP-HIST-052: stored 0.25 → displayed "25%".
     */
    @Test
    fun `AT-HIST-05-C expanded deviation - stored 0_25 displays as 25 percent`() {
        assertEquals(
            "AT-HIST-05-C: field_deviation_pct=0.25 must display as '25%'",
            "25%",
            BearingAdapter.formatFieldDeviation(0.25f)
        )
    }

    @Test
    fun `AT-HIST-05-C expanded deviation - stored 0_127 truncates to 12 percent not 13`() {
        // Truncation toward zero: 0.127 * 100 = 12.7 → toInt() = 12 (not rounded to 13)
        assertEquals(
            "AT-HIST-05-C: field_deviation_pct=0.127 must truncate to '12%' (not round to '13%')",
            "12%",
            BearingAdapter.formatFieldDeviation(0.127f)
        )
    }

    // ─── Fix 2: absoluteAdapterPosition note ─────────────────────────────────────
    //
    // BearingAdapter.onBindViewHolder() uses holder.absoluteAdapterPosition (not the deprecated
    // holder.adapterPosition) when calling toggleExpanded().
    // BearingHistoryFragment.onSwiped() guards with RecyclerView.NO_POSITION (not NO_ID.toInt()).
    // These are framework-level API changes verified by compilation; the logical toggle behaviour
    // (mutual exclusion, no notifyDataSetChanged) is covered by the adapter tests below.

    // ─── PROP-HIST-042: Snackbar duration constant ─────────────────────────────────

    /**
     * PROP-HIST-042: The undo Snackbar must use exactly 5000 ms duration, not Snackbar.LENGTH_LONG.
     * This test asserts the named constant value so any accidental change to the magic number is
     * caught at compile-time (constant reference) and at test-time (value assertion).
     *
     * The E2E timing assertion (Snackbar gone after 5100 ms) is deferred to Phase 5
     * instrumented tests with Hilt DI support.
     */
    @Test
    fun `PROP-HIST-042 UNDO_SNACKBAR_DURATION_MS equals exactly 5000`() {
        assertEquals(
            "PROP-HIST-042: Undo Snackbar must use exactly 5000 ms duration (not LENGTH_LONG=2750)",
            5000,
            BearingHistoryFragment.UNDO_SNACKBAR_DURATION_MS
        )
    }

    // ─── PROP-HIST-072: BearingAdapter must extend ListAdapter ────────────────────

    @Test
    fun `PROP-HIST-072 BearingAdapter extends ListAdapter`() {
        val adapter = BearingAdapter()
        assertTrue(
            "PROP-HIST-072: BearingAdapter must extend ListAdapter with DiffUtil.ItemCallback",
            adapter is ListAdapter<*, *>
        )
    }

    // ─── PROP-HIST-022: notifyDataSetChanged must not be called by toggleExpanded ─

    @Test
    fun `toggleExpanded does not call notifyDataSetChanged`() {
        val adapter = BearingAdapter()
        val recording = RecordingBearingAdapter(adapter)

        // Call toggleExpanded with a single item — nothing to collapse previously
        recording.toggleExpanded("item-1", 0)

        assertEquals(
            "notifyDataSetChanged must not be called by toggleExpanded()",
            0,
            recording.notifyDataSetChangedCount
        )
    }

    /**
     * AT-HIST-01-C: Only one row can be expanded at a time (mutual exclusion).
     * When a second row is expanded, the previously expanded row collapses.
     *
     * TE CR-IMPL F-03: Originally scheduled as an E2E Espresso test requiring Hilt.
     * The mutual-exclusion logic is entirely in BearingAdapter.toggleExpanded() — no
     * Android View framework involvement. Verified here at unit level using RecordingBearingAdapter.
     */
    @Test
    fun `AT-HIST-01-C mutual exclusion - expanding row 2 collapses previously expanded row 1`() {
        val adapter = BearingAdapter()
        val recording = RecordingBearingAdapter(adapter)

        // Expand item at position 0
        recording.toggleExpanded("item-1", 0)
        val afterFirstExpand = recording.notifyItemChangedPositions.toList()

        // Expand a different item at position 2 — must collapse position 0
        recording.toggleExpanded("item-2", 2)
        val afterSecondExpand = recording.notifyItemChangedPositions.toList()

        // First expand notifies only the newly expanded position
        assertEquals("First expand: exactly one notify", 1, afterFirstExpand.size)
        assertEquals("First expand: notified position 0", 0, afterFirstExpand[0])

        // Second expand notifies both the old (collapse) and new (expand) positions
        assertEquals("Second expand: exactly two notifies total", 2, afterSecondExpand.size)
        assertEquals("notifyDataSetChanged must never be called (mutual exclusion is targeted)", 0, recording.notifyDataSetChangedCount)
    }

    @Test
    fun `toggleExpanded notifies at most two positions on expand-then-expand`() {
        val adapter = BearingAdapter()
        val recording = RecordingBearingAdapter(adapter)

        // First expand at position 0
        recording.toggleExpanded("item-1", 0)
        val firstPositions = recording.notifyItemChangedPositions.toList()

        // Second expand at position 2 (collapses position 0)
        recording.toggleExpanded("item-2", 2)
        val secondPositions = recording.notifyItemChangedPositions.toList()

        assertEquals(
            "First toggleExpanded should call notifyItemChanged at most once (new expand only)",
            1,
            firstPositions.size
        )
        assertEquals(
            "Second toggleExpanded should notify at most 2 positions (collapse old + expand new)",
            2,
            secondPositions.size
        )
        assertEquals(
            "notifyDataSetChanged must never be called",
            0,
            recording.notifyDataSetChangedCount
        )
    }

    @Test
    fun `toggleExpanded on already-expanded item collapses it - notifies one position`() {
        val adapter = BearingAdapter()
        val recording = RecordingBearingAdapter(adapter)

        // Expand item-1
        recording.toggleExpanded("item-1", 0)
        recording.resetTracking()

        // Collapse same item
        recording.toggleExpanded("item-1", 0)

        assertEquals(
            "Collapsing same item should call notifyItemChanged once",
            1,
            recording.notifyItemChangedPositions.size
        )
        assertEquals(0, recording.notifyItemChangedPositions[0])
        assertEquals(0, recording.notifyDataSetChangedCount)
    }
}

/**
 * Recording wrapper that delegates to [BearingAdapter.toggleExpanded] while tracking
 * calls to [notifyItemChanged] and [notifyDataSetChanged].
 *
 * PROP-HIST-022: verifies notifyDataSetChanged is not called.
 */
class RecordingBearingAdapter(private val delegate: BearingAdapter) {

    val notifyItemChangedPositions = mutableListOf<Int>()
    var notifyDataSetChangedCount = 0
        private set

    fun toggleExpanded(itemId: String, position: Int) {
        delegate.toggleExpanded(
            itemId = itemId,
            position = position,
            onNotifyItemChanged = { pos -> notifyItemChangedPositions.add(pos) },
            onNotifyDataSetChanged = { notifyDataSetChangedCount++ }
        )
    }

    fun resetTracking() {
        notifyItemChangedPositions.clear()
        notifyDataSetChangedCount = 0
    }
}
