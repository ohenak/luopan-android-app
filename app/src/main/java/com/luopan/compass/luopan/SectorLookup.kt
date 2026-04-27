package com.luopan.compass.luopan

/**
 * Pure singleton for sector boundary look-up tables (LUTs) for all six Luopan rings.
 *
 * All rings use inclusive-left, exclusive-right [start°, end°) membership (BR-01).
 * Sectors that straddle the 0°/360° boundary carry [wrapsAround = true] and match
 * bearings in [start°, 360°) ∪ [0°, end°) (BR-02, BR-03, BR-04).
 *
 * The caller does not need to pre-normalise input; each public function normalises
 * its bearing to [0°, 360°) before the table scan.
 *
 * No Android dependencies — safe to use in pure JVM unit tests.
 */
object SectorLookup {

    // -------------------------------------------------------------------------
    // Internal data structures
    // -------------------------------------------------------------------------

    private data class SectorEntry(
        val startDeg: Float,
        val endDeg: Float,
        val wrapsAround: Boolean = false
    )

    // -------------------------------------------------------------------------
    // Sector tables (compile-time constants)
    // -------------------------------------------------------------------------

    /**
     * Ring 2 — 後天八卦 (King Wen / Later Heaven), 8 sectors × 45°.
     *
     * ☵ 坎 北 wraps around 0° per BR-03.
     *
     * Index | Sector  | Start   | End
     *   0   | ☵ 坎 北 | 337.5°  | 22.5°   (wrap)
     *   1   | ☶ 艮 東北| 22.5°  | 67.5°
     *   2   | ☳ 震 東  | 67.5°  | 112.5°
     *   3   | ☴ 巽 東南| 112.5° | 157.5°
     *   4   | ☲ 離 南  | 157.5° | 202.5°
     *   5   | ☷ 坤 西南| 202.5° | 247.5°
     *   6   | ☱ 兌 西  | 247.5° | 292.5°
     *   7   | ☰ 乾 西北| 292.5° | 337.5°
     */
    private val RING2_SECTORS = arrayOf(
        SectorEntry(337.5f,  22.5f, wrapsAround = true),  // 0: ☵ 坎
        SectorEntry( 22.5f,  67.5f),                      // 1: ☶ 艮
        SectorEntry( 67.5f, 112.5f),                      // 2: ☳ 震
        SectorEntry(112.5f, 157.5f),                      // 3: ☴ 巽
        SectorEntry(157.5f, 202.5f),                      // 4: ☲ 離
        SectorEntry(202.5f, 247.5f),                      // 5: ☷ 坤
        SectorEntry(247.5f, 292.5f),                      // 6: ☱ 兌
        SectorEntry(292.5f, 337.5f)                       // 7: ☰ 乾
    )

    /**
     * Ring 3 — 十二地支 (Twelve Earthly Branches), 12 sectors × 30°.
     *
     * 子 wraps around 0° per BR-02: [345°, 15°).
     *
     * Index | 地支 | Start  | End
     *   0   | 子   | 345°   | 15°    (wrap)
     *   1   | 丑   | 15°    | 45°
     *   2   | 寅   | 45°    | 75°
     *   3   | 卯   | 75°    | 105°
     *   4   | 辰   | 105°   | 135°
     *   5   | 巳   | 135°   | 165°
     *   6   | 午   | 165°   | 195°
     *   7   | 未   | 195°   | 225°
     *   8   | 申   | 225°   | 255°
     *   9   | 酉   | 255°   | 285°
     *  10   | 戌   | 285°   | 315°
     *  11   | 亥   | 315°   | 345°
     */
    private val RING3_SECTORS = arrayOf(
        SectorEntry(345.0f,  15.0f, wrapsAround = true),  // 0:  子
        SectorEntry( 15.0f,  45.0f),                      // 1:  丑
        SectorEntry( 45.0f,  75.0f),                      // 2:  寅
        SectorEntry( 75.0f, 105.0f),                      // 3:  卯
        SectorEntry(105.0f, 135.0f),                      // 4:  辰
        SectorEntry(135.0f, 165.0f),                      // 5:  巳
        SectorEntry(165.0f, 195.0f),                      // 6:  午
        SectorEntry(195.0f, 225.0f),                      // 7:  未
        SectorEntry(225.0f, 255.0f),                      // 8:  申
        SectorEntry(255.0f, 285.0f),                      // 9:  酉
        SectorEntry(285.0f, 315.0f),                      // 10: 戌
        SectorEntry(315.0f, 345.0f)                       // 11: 亥
    )

    /**
     * Ring 4 — 二十四山 (Twenty-Four Mountains), 24 sectors × 15°.
     *
     * 子 wraps around 0° per BR-03: [352.5°, 7.5°).
     * 壬 does NOT wrap: [337.5°, 352.5°).
     *
     * Index | 山 | Start   | End
     *   0   | 壬 | 337.5°  | 352.5°
     *   1   | 子 | 352.5°  | 7.5°   (wrap)
     *   2   | 癸 | 7.5°    | 22.5°
     *   3   | 丑 | 22.5°   | 37.5°
     *   4   | 艮 | 37.5°   | 52.5°
     *   5   | 寅 | 52.5°   | 67.5°
     *   6   | 甲 | 67.5°   | 82.5°
     *   7   | 卯 | 82.5°   | 97.5°
     *   8   | 乙 | 97.5°   | 112.5°
     *   9   | 辰 | 112.5°  | 127.5°
     *  10   | 巽 | 127.5°  | 142.5°
     *  11   | 巳 | 142.5°  | 157.5°
     *  12   | 丙 | 157.5°  | 172.5°
     *  13   | 午 | 172.5°  | 187.5°
     *  14   | 丁 | 187.5°  | 202.5°
     *  15   | 未 | 202.5°  | 217.5°
     *  16   | 坤 | 217.5°  | 232.5°
     *  17   | 申 | 232.5°  | 247.5°
     *  18   | 庚 | 247.5°  | 262.5°
     *  19   | 酉 | 262.5°  | 277.5°
     *  20   | 辛 | 277.5°  | 292.5°
     *  21   | 戌 | 292.5°  | 307.5°
     *  22   | 乾 | 307.5°  | 322.5°
     *  23   | 亥 | 322.5°  | 337.5°
     */
    private val RING4_SECTORS = arrayOf(
        SectorEntry(337.5f, 352.5f),                      // 0:  壬
        SectorEntry(352.5f,   7.5f, wrapsAround = true),  // 1:  子
        SectorEntry(  7.5f,  22.5f),                      // 2:  癸
        SectorEntry( 22.5f,  37.5f),                      // 3:  丑
        SectorEntry( 37.5f,  52.5f),                      // 4:  艮
        SectorEntry( 52.5f,  67.5f),                      // 5:  寅
        SectorEntry( 67.5f,  82.5f),                      // 6:  甲
        SectorEntry( 82.5f,  97.5f),                      // 7:  卯
        SectorEntry( 97.5f, 112.5f),                      // 8:  乙
        SectorEntry(112.5f, 127.5f),                      // 9:  辰
        SectorEntry(127.5f, 142.5f),                      // 10: 巽
        SectorEntry(142.5f, 157.5f),                      // 11: 巳
        SectorEntry(157.5f, 172.5f),                      // 12: 丙
        SectorEntry(172.5f, 187.5f),                      // 13: 午
        SectorEntry(187.5f, 202.5f),                      // 14: 丁
        SectorEntry(202.5f, 217.5f),                      // 15: 未
        SectorEntry(217.5f, 232.5f),                      // 16: 坤
        SectorEntry(232.5f, 247.5f),                      // 17: 申
        SectorEntry(247.5f, 262.5f),                      // 18: 庚
        SectorEntry(262.5f, 277.5f),                      // 19: 酉
        SectorEntry(277.5f, 292.5f),                      // 20: 辛
        SectorEntry(292.5f, 307.5f),                      // 21: 戌
        SectorEntry(307.5f, 322.5f),                      // 22: 乾
        SectorEntry(322.5f, 337.5f)                       // 23: 亥
    )

    /**
     * Ring 5 — 六十分金 (Sixty Gold Divisions), 60 sectors × 6°.
     *
     * 壬子分金 wraps around 0° per BR-04: [358°, 4°).
     *
     * Index 0  = 庚子分金 [352°, 358°)
     * Index 1  = 壬子分金 [358°, 4°)   ← wrap-around
     * Index 2  = 甲子分金 [4°,   10°)
     * ...
     * Index 16 = 壬卯分金 [88°,  94°)
     * ...
     * Index 31 = 壬午分金 [178°, 184°)
     * ...
     * Index 59 = 戊亥分金 [346°, 352°)
     *
     * Source: FSPEC §4.7 (1-based index → 0-based).
     */
    private val RING5_SECTORS = arrayOf(
        SectorEntry(352.0f, 358.0f),                      // 0:  庚子分金
        SectorEntry(358.0f,   4.0f, wrapsAround = true),  // 1:  壬子分金
        SectorEntry(  4.0f,  10.0f),                      // 2:  甲子分金
        SectorEntry( 10.0f,  16.0f),                      // 3:  丙子分金
        SectorEntry( 16.0f,  22.0f),                      // 4:  戊子分金
        SectorEntry( 22.0f,  28.0f),                      // 5:  庚丑分金
        SectorEntry( 28.0f,  34.0f),                      // 6:  壬丑分金
        SectorEntry( 34.0f,  40.0f),                      // 7:  甲丑分金
        SectorEntry( 40.0f,  46.0f),                      // 8:  丙丑分金
        SectorEntry( 46.0f,  52.0f),                      // 9:  戊丑分金
        SectorEntry( 52.0f,  58.0f),                      // 10: 庚寅分金
        SectorEntry( 58.0f,  64.0f),                      // 11: 壬寅分金
        SectorEntry( 64.0f,  70.0f),                      // 12: 甲寅分金
        SectorEntry( 70.0f,  76.0f),                      // 13: 丙寅分金
        SectorEntry( 76.0f,  82.0f),                      // 14: 戊寅分金
        SectorEntry( 82.0f,  88.0f),                      // 15: 庚卯分金
        SectorEntry( 88.0f,  94.0f),                      // 16: 壬卯分金
        SectorEntry( 94.0f, 100.0f),                      // 17: 甲卯分金
        SectorEntry(100.0f, 106.0f),                      // 18: 丙卯分金
        SectorEntry(106.0f, 112.0f),                      // 19: 戊卯分金
        SectorEntry(112.0f, 118.0f),                      // 20: 庚辰分金
        SectorEntry(118.0f, 124.0f),                      // 21: 壬辰分金
        SectorEntry(124.0f, 130.0f),                      // 22: 甲辰分金
        SectorEntry(130.0f, 136.0f),                      // 23: 丙辰分金
        SectorEntry(136.0f, 142.0f),                      // 24: 戊辰分金
        SectorEntry(142.0f, 148.0f),                      // 25: 庚巳分金
        SectorEntry(148.0f, 154.0f),                      // 26: 壬巳分金
        SectorEntry(154.0f, 160.0f),                      // 27: 甲巳分金
        SectorEntry(160.0f, 166.0f),                      // 28: 丙巳分金
        SectorEntry(166.0f, 172.0f),                      // 29: 戊巳分金
        SectorEntry(172.0f, 178.0f),                      // 30: 庚午分金
        SectorEntry(178.0f, 184.0f),                      // 31: 壬午分金
        SectorEntry(184.0f, 190.0f),                      // 32: 甲午分金
        SectorEntry(190.0f, 196.0f),                      // 33: 丙午分金
        SectorEntry(196.0f, 202.0f),                      // 34: 戊午分金
        SectorEntry(202.0f, 208.0f),                      // 35: 庚未分金
        SectorEntry(208.0f, 214.0f),                      // 36: 壬未分金
        SectorEntry(214.0f, 220.0f),                      // 37: 甲未分金
        SectorEntry(220.0f, 226.0f),                      // 38: 丙未分金
        SectorEntry(226.0f, 232.0f),                      // 39: 戊未分金
        SectorEntry(232.0f, 238.0f),                      // 40: 庚申分金
        SectorEntry(238.0f, 244.0f),                      // 41: 壬申分金
        SectorEntry(244.0f, 250.0f),                      // 42: 甲申分金
        SectorEntry(250.0f, 256.0f),                      // 43: 丙申分金
        SectorEntry(256.0f, 262.0f),                      // 44: 戊申分金
        SectorEntry(262.0f, 268.0f),                      // 45: 庚酉分金
        SectorEntry(268.0f, 274.0f),                      // 46: 壬酉分金
        SectorEntry(274.0f, 280.0f),                      // 47: 甲酉分金
        SectorEntry(280.0f, 286.0f),                      // 48: 丙酉分金
        SectorEntry(286.0f, 292.0f),                      // 49: 戊酉分金
        SectorEntry(292.0f, 298.0f),                      // 50: 庚戌分金
        SectorEntry(298.0f, 304.0f),                      // 51: 壬戌分金
        SectorEntry(304.0f, 310.0f),                      // 52: 甲戌分金
        SectorEntry(310.0f, 316.0f),                      // 53: 丙戌分金
        SectorEntry(316.0f, 322.0f),                      // 54: 戊戌分金
        SectorEntry(322.0f, 328.0f),                      // 55: 庚亥分金
        SectorEntry(328.0f, 334.0f),                      // 56: 壬亥分金
        SectorEntry(334.0f, 340.0f),                      // 57: 甲亥分金
        SectorEntry(340.0f, 346.0f),                      // 58: 丙亥分金
        SectorEntry(346.0f, 352.0f)                       // 59: 戊亥分金
    )

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Normalises any bearing to [0°, 360°).
     * Handles values > 360°, values == 360°, and negative values.
     */
    private fun normaliseBearing(raw: Float): Float = ((raw % 360f) + 360f) % 360f

    /**
     * Scans [sectors] for the first entry whose [start°, end°) interval contains [bearing].
     * Wrap-around sectors match bearings in [start°, 360°) ∪ [0°, end°).
     *
     * @throws IllegalStateException if no sector matches (should never occur for a complete LUT).
     */
    private fun lookupSector(bearing: Float, sectors: Array<SectorEntry>): Int {
        val b = normaliseBearing(bearing)
        sectors.forEachIndexed { index, entry ->
            if (entry.wrapsAround) {
                if (b >= entry.startDeg || b < entry.endDeg) return index
            } else {
                if (b >= entry.startDeg && b < entry.endDeg) return index
            }
        }
        throw IllegalStateException("No sector found for bearing $b — LUT is incomplete")
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns the 0-based 後天八卦 sector index (0–7) for the given bearing. */
    fun ring2(bearing: Float): Int = lookupSector(bearing, RING2_SECTORS)

    /** Returns the 0-based 十二地支 sector index (0–11) for the given bearing. */
    fun ring3(bearing: Float): Int = lookupSector(bearing, RING3_SECTORS)

    /** Returns the 0-based 二十四山 sector index (0–23) for the given bearing. */
    fun ring4(bearing: Float): Int = lookupSector(bearing, RING4_SECTORS)

    /** Returns the 0-based 六十分金 sector index (0–59) for the given bearing. */
    fun ring5(bearing: Float): Int = lookupSector(bearing, RING5_SECTORS)
}
