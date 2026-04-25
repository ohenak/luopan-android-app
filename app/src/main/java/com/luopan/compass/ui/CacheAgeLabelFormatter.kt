package com.luopan.compass.ui

/**
 * Formats a cache age (in milliseconds) as a human-readable "N days ago" label.
 *
 * Uses floor division per FSPEC §2.3 step 7:
 *   N = floor(elapsed_ms / 86_400_000L)
 *
 * P7.2 — PLAN §4 P7.2 / FSPEC §2.3 step 7 (AT-C).
 */
object CacheAgeLabelFormatter {

    private const val MS_PER_DAY = 86_400_000L

    /**
     * Returns a human-readable label for the given cache age.
     *
     * @param ageMs  Age of the cached location in milliseconds. Must be >= 0.
     * @return  A string of the form "N days ago" where N = floor(ageMs / 86_400_000).
     */
    fun format(ageMs: Long): String {
        val days = ageMs / MS_PER_DAY  // integer floor division
        return "$days days ago"
    }
}
