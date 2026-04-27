package com.luopan.compass.ui

import org.junit.Assert.assertEquals
import org.junit.Test

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
