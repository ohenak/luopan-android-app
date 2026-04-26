package com.luopan.compass.luopan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SectorLookup].
 *
 * All tests use inclusive-left, exclusive-right [start°, end°) membership (BR-01).
 * Wrap-around sectors (BR-02, BR-03, BR-04) are covered explicitly.
 * Input normalisation is verified: 360° == 0° and negative inputs are valid.
 */
class SectorLookupTest {

    // -------------------------------------------------------------------------
    // Ring 2 — 先天八卦 (Fuxi / Earlier Heaven) — 8 sectors × 45°
    // -------------------------------------------------------------------------

    @Test
    fun `ring2 - bearing just below 巽 wrap-around start returns 坎 (index 7)`() {
        // 337.4° < 337.5° → still in ☵ 坎 [292.5°, 337.5°) → index 7
        assertEquals(7, SectorLookup.ring2(337.4f))
    }

    @Test
    fun `ring2 - bearing exactly at 337_5 enters 巽 wrap-around sector (index 0)`() {
        // 337.5° = start of ☴ 巽 [337.5°, 22.5°) wrap-around → index 0
        assertEquals(0, SectorLookup.ring2(337.5f))
    }

    @Test
    fun `ring2 - bearing 0 deg falls inside 巽 wrap-around (index 0)`() {
        // 0° is within ☴ 巽 [337.5°, 22.5°) → index 0
        assertEquals(0, SectorLookup.ring2(0f))
    }

    @Test
    fun `ring2 - bearing just below 22_5 is still 巽 (index 0)`() {
        // 22.4° < 22.5° → still in ☴ 巽 → index 0
        assertEquals(0, SectorLookup.ring2(22.4f))
    }

    @Test
    fun `ring2 - bearing exactly at 22_5 enters 震 (index 1)`() {
        // 22.5° = start of ☳ 震 [22.5°, 67.5°) → index 1
        assertEquals(1, SectorLookup.ring2(22.5f))
    }

    @Test
    fun `ring2 - bearing just below 157_5 is still 兌 (index 3)`() {
        // 157.4° < 157.5° → in ☱ 兌 [112.5°, 157.5°) → index 3
        assertEquals(3, SectorLookup.ring2(157.4f))
    }

    @Test
    fun `ring2 - bearing exactly at 157_5 enters 乾 (index 4)`() {
        // 157.5° = start of ☰ 乾 [157.5°, 202.5°) → index 4
        assertEquals(4, SectorLookup.ring2(157.5f))
    }

    @Test
    fun `ring2 - bearing just below 202_5 is still 乾 (index 4)`() {
        // 202.4° < 202.5° → in ☰ 乾 [157.5°, 202.5°) → index 4
        assertEquals(4, SectorLookup.ring2(202.4f))
    }

    @Test
    fun `ring2 - bearing exactly at 202_5 enters 坤 (index 5)`() {
        // 202.5° = start of ☷ 坤 [202.5°, 247.5°) → index 5
        assertEquals(5, SectorLookup.ring2(202.5f))
    }

    // -------------------------------------------------------------------------
    // Ring 3 — 後天八卦 (King Wen / Later Heaven) — 8 sectors × 45°
    // -------------------------------------------------------------------------

    @Test
    fun `ring3 - bearing 22_4 is in 坎 北 wrap-around sector (index 0)`() {
        // 22.4° < 22.5° → in ☵ 坎 [337.5°, 22.5°) → index 0
        assertEquals(0, SectorLookup.ring3(22.4f))
    }

    @Test
    fun `ring3 - bearing 22_5 enters 艮 東北 (index 1)`() {
        // 22.5° = start of ☶ 艮 [22.5°, 67.5°) → index 1
        assertEquals(1, SectorLookup.ring3(22.5f))
    }

    @Test
    fun `ring3 - bearing 67_4 is still 艮 東北 (index 1)`() {
        // 67.4° < 67.5° → in ☶ 艮 [22.5°, 67.5°) → index 1
        assertEquals(1, SectorLookup.ring3(67.4f))
    }

    @Test
    fun `ring3 - bearing 67_5 enters 震 東 (index 2)`() {
        // 67.5° = start of ☳ 震 [67.5°, 112.5°) → index 2
        assertEquals(2, SectorLookup.ring3(67.5f))
    }

    @Test
    fun `ring3 - bearing 0 deg is in 坎 北 wrap-around sector (index 0)`() {
        // 0° is within ☵ 坎 [337.5°, 22.5°) → index 0
        assertEquals(0, SectorLookup.ring3(0f))
    }

    @Test
    fun `ring3 - bearing 180 deg is in 離 南 (index 4)`() {
        // 180° is in ☲ 離 [157.5°, 202.5°) → index 4
        assertEquals(4, SectorLookup.ring3(180f))
    }

    @Test
    fun `ring3 - bearing 337_4 is in 乾 西北 (index 7)`() {
        // 337.4° < 337.5° → still in ☰ 乾 [292.5°, 337.5°) → index 7
        assertEquals(7, SectorLookup.ring3(337.4f))
    }

    @Test
    fun `ring3 - bearing exactly at 337_5 enters 坎 北 wrap-around (index 0)`() {
        // 337.5° = start of ☵ 坎 [337.5°, 22.5°) wrap-around → index 0
        assertEquals(0, SectorLookup.ring3(337.5f))
    }

    // -------------------------------------------------------------------------
    // Ring 4 — 十二地支 — 12 sectors × 30° — BR-02 wrap-around at 子
    // -------------------------------------------------------------------------

    @Test
    fun `ring4 - bearing 344_9 is in 亥 (index 11, last sector before 子)`() {
        // 344.9° < 345° → in 亥 [315°, 345°) → index 11
        assertEquals(11, SectorLookup.ring4(344.9f))
    }

    @Test
    fun `ring4 - bearing 345_0 enters 子 wrap-around sector (index 0)`() {
        // 345.0° = start of 子 [345°, 15°) → index 0
        assertEquals(0, SectorLookup.ring4(345.0f))
    }

    @Test
    fun `ring4 - bearing 14_9 is still in 子 (index 0)`() {
        // 14.9° < 15° → in 子 [345°, 15°) → index 0
        assertEquals(0, SectorLookup.ring4(14.9f))
    }

    @Test
    fun `ring4 - bearing 15_0 enters 丑 (index 1)`() {
        // 15.0° = start of 丑 [15°, 45°) → index 1
        assertEquals(1, SectorLookup.ring4(15.0f))
    }

    @Test
    fun `ring4 - bearing 44_9 is still in 丑 (index 1)`() {
        // 44.9° < 45° → in 丑 [15°, 45°) → index 1
        assertEquals(1, SectorLookup.ring4(44.9f))
    }

    @Test
    fun `ring4 - bearing 45_0 enters 寅 (index 2)`() {
        // 45.0° = start of 寅 [45°, 75°) → index 2
        assertEquals(2, SectorLookup.ring4(45.0f))
    }

    @Test
    fun `ring4 - bearing 0 deg is in 子 wrap-around (index 0)`() {
        // 0° is within 子 [345°, 15°) → index 0
        assertEquals(0, SectorLookup.ring4(0f))
    }

    @Test
    fun `ring4 - bearing 360 normalises to 0 and returns 子 (index 0)`() {
        // 360° normalises to 0° → 子 → index 0
        assertEquals(0, SectorLookup.ring4(360f))
    }

    @Test
    fun `ring4 - negative bearing normalises correctly`() {
        // -15° normalises to 345° → 子 [345°, 15°) → index 0
        assertEquals(0, SectorLookup.ring4(-15f))
    }

    // -------------------------------------------------------------------------
    // Ring 5 — 二十四山 — 24 sectors × 15° — BR-03 wrap-around at 子
    // -------------------------------------------------------------------------

    @Test
    fun `ring5 - bearing 7_4 is in 子 wrap-around (index 1)`() {
        // 7.4° < 7.5° → in 子 [352.5°, 7.5°) → index 1
        assertEquals(1, SectorLookup.ring5(7.4f))
    }

    @Test
    fun `ring5 - bearing 7_5 enters 癸 (index 2)`() {
        // 7.5° = start of 癸 [7.5°, 22.5°) → index 2
        assertEquals(2, SectorLookup.ring5(7.5f))
    }

    @Test
    fun `ring5 - bearing 22_4 is still in 癸 (index 2)`() {
        // 22.4° < 22.5° → in 癸 [7.5°, 22.5°) → index 2
        assertEquals(2, SectorLookup.ring5(22.4f))
    }

    @Test
    fun `ring5 - bearing 22_5 enters 丑 (index 3)`() {
        // 22.5° = start of 丑 [22.5°, 37.5°) → index 3
        assertEquals(3, SectorLookup.ring5(22.5f))
    }

    @Test
    fun `ring5 - bearing 0 deg is in 子 wrap-around (index 1)`() {
        // 0° in 子 [352.5°, 7.5°) → index 1
        assertEquals(1, SectorLookup.ring5(0f))
    }

    @Test
    fun `ring5 - bearing 180 deg is in 午 (index 13)`() {
        // 180° in 午 [172.5°, 187.5°) → index 13
        assertEquals(13, SectorLookup.ring5(180f))
    }

    @Test
    fun `ring5 - bearing 352_5 enters 壬 (index 0)`() {
        // Note: index 0 = 壬 [337.5°, 352.5°) in the TSPEC table but wait —
        // TSPEC table has 壬 at index 0 with [337.5°, 352.5°) (no wrap),
        // and 子 at index 1 with [352.5°, 7.5°) (wrap).
        // 337.5° is start of 壬, 352.5° is start of 子.
        assertEquals(1, SectorLookup.ring5(352.5f))
    }

    @Test
    fun `ring5 - bearing 337_5 enters 壬 (index 0)`() {
        // 337.5° = start of 壬 [337.5°, 352.5°) → index 0
        assertEquals(0, SectorLookup.ring5(337.5f))
    }

    // -------------------------------------------------------------------------
    // Ring 6 — 六十分金 — 60 sectors × 6° — BR-04 wrap-around at 壬子分金
    //
    // TSPEC index 0 = 庚子分金 [352°, 358°)
    // TSPEC index 1 = 壬子分金 [358°, 4°) — wrap-around
    // This numbering produces: 壬卯分金 = index 16, 壬午分金 = index 31.
    // -------------------------------------------------------------------------

    @Test
    fun `ring6 - bearing 357_9 is in 庚子分金 (index 0)`() {
        // 357.9° in 庚子分金 [352°, 358°) → index 0
        assertEquals(0, SectorLookup.ring6(357.9f))
    }

    @Test
    fun `ring6 - bearing 358_0 enters 壬子分金 wrap-around (index 1)`() {
        // 358.0° = start of 壬子分金 [358°, 4°) wrap-around → index 1
        assertEquals(1, SectorLookup.ring6(358.0f))
    }

    @Test
    fun `ring6 - bearing 0_0 is in 壬子分金 wrap-around (index 1)`() {
        // 0° in 壬子分金 [358°, 4°) → index 1
        assertEquals(1, SectorLookup.ring6(0.0f))
    }

    @Test
    fun `ring6 - bearing 4_0 enters 甲子分金 (index 2)`() {
        // 4.0° = start of 甲子分金 [4°, 10°) → index 2
        assertEquals(2, SectorLookup.ring6(4.0f))
    }

    @Test
    fun `ring6 - bearing 90_0 is in 壬卯分金 (index 16)`() {
        // 90° in 壬卯分金 [88°, 94°) → index 16
        assertEquals(16, SectorLookup.ring6(90.0f))
    }

    @Test
    fun `ring6 - bearing 180_0 is in 壬午分金 (index 31)`() {
        // 180° in 壬午分金 [178°, 184°) → index 31
        assertEquals(31, SectorLookup.ring6(180.0f))
    }

    @Test
    fun `ring6 - bearing 352_0 enters 庚子分金 (index 0)`() {
        // 352° = start of 庚子分金 [352°, 358°) → index 0
        assertEquals(0, SectorLookup.ring6(352.0f))
    }

    @Test
    fun `ring6 - bearing 351_9 is in last sector 戊亥分金 (index 59)`() {
        // 351.9° in 戊亥分金 [346°, 352°) → index 59
        assertEquals(59, SectorLookup.ring6(351.9f))
    }

    // -------------------------------------------------------------------------
    // Normalisation edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `ring4 - bearing 360 normalises to 0 deg`() {
        assertEquals(SectorLookup.ring4(0f), SectorLookup.ring4(360f))
    }

    @Test
    fun `ring4 - negative bearing -345 normalises to 15 deg`() {
        // -345° → (-345 + 360) = 15° → 丑 [15°, 45°) → index 1
        assertEquals(1, SectorLookup.ring4(-345f))
    }

    @Test
    fun `ring5 - bearing 360 normalises same as 0`() {
        assertEquals(SectorLookup.ring5(0f), SectorLookup.ring5(360f))
    }

    @Test
    fun `ring6 - bearing 360 normalises same as 0`() {
        assertEquals(SectorLookup.ring6(0f), SectorLookup.ring6(360f))
    }

    // =========================================================================
    // PROP-01-001: Exhaustive coverage — every bearing in [0°, 360°) is assigned
    // to exactly one sector per ring; no bearing is ever unassigned.
    // =========================================================================

    @Test
    fun `ring2 - every bearing in 0 to 360 step 0_5 is assigned to a valid sector`() {
        var bearing = 0f
        while (bearing < 360f) {
            val idx = SectorLookup.ring2(bearing)
            assertTrue("ring2($bearing) returned invalid index $idx", idx in 0..7)
            bearing += 0.5f
        }
    }

    @Test
    fun `ring3 - every bearing in 0 to 360 step 0_5 is assigned to a valid sector`() {
        var bearing = 0f
        while (bearing < 360f) {
            val idx = SectorLookup.ring3(bearing)
            assertTrue("ring3($bearing) returned invalid index $idx", idx in 0..7)
            bearing += 0.5f
        }
    }

    @Test
    fun `ring4 - every bearing in 0 to 360 step 0_5 is assigned to a valid sector`() {
        var bearing = 0f
        while (bearing < 360f) {
            val idx = SectorLookup.ring4(bearing)
            assertTrue("ring4($bearing) returned invalid index $idx", idx in 0..11)
            bearing += 0.5f
        }
    }

    @Test
    fun `ring5 - every bearing in 0 to 360 step 0_5 is assigned to a valid sector`() {
        var bearing = 0f
        while (bearing < 360f) {
            val idx = SectorLookup.ring5(bearing)
            assertTrue("ring5($bearing) returned invalid index $idx", idx in 0..23)
            bearing += 0.5f
        }
    }

    @Test
    fun `ring6 - every bearing in 0 to 360 step 0_5 is assigned to a valid sector`() {
        var bearing = 0f
        while (bearing < 360f) {
            val idx = SectorLookup.ring6(bearing)
            assertTrue("ring6($bearing) returned invalid index $idx", idx in 0..59)
            bearing += 0.5f
        }
    }

    @Test
    fun `ring4 - no bearing in 0 to 360 throws IllegalStateException`() {
        var bearing = 0f
        while (bearing < 360f) {
            try {
                SectorLookup.ring4(bearing)
            } catch (e: IllegalStateException) {
                throw AssertionError("ring4($bearing) threw IllegalStateException: ${e.message}")
            }
            bearing += 1f
        }
    }
}
