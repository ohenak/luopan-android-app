package com.luopan.compass.luopan

/**
 * Compile-time label lookup for all six Luopan rings.
 *
 * All arrays are 0-based sector indices matching [SectorLookup] return values.
 * No Android dependencies — pure Kotlin object.
 *
 * Ring sizes:
 *   Ring 2 — 先天八卦  (Fuxi / Earlier Heaven): 8  sectors × 45°
 *   Ring 3 — 後天八卦  (King Wen / Later Heaven): 8  sectors × 45°
 *   Ring 4 — 十二地支  (Twelve Earthly Branches): 12 sectors × 30°
 *   Ring 5 — 二十四山  (Twenty-Four Mountains):   24 sectors × 15°
 *   Ring 6 — 六十分金  (Sixty Gold Divisions):    60 sectors × 6°
 *
 * Sources: FSPEC §4.2–4.8, REQ §5.3a, §5.6, §5.8, TSPEC §4.2.
 */
object RingLabelProvider {

    // ─── Ring 2 — 先天八卦 (Fuxi / Earlier Heaven Arrangement) ───────────────
    //
    // Sector index 0 = 巽(☴) at [337.5°, 22.5°) — wrap-around, centre 0°/North
    // Sector index 4 = 乾(☰) at [157.5°, 202.5°) — centre 180°/South
    //
    // Source: TSPEC §4.1 corrected table (TE-F01); FSPEC §4.3; REQ §5.3a.
    // English uses the trigram element name per the task description §5.8.
    private val ring2Labels: Array<LabelData> = arrayOf(
        /* 0 */ LabelData("巽", "Xùn", "Wind"),       // ☴ 北 centre 0°  [337.5°, 22.5°) wrap
        /* 1 */ LabelData("震", "Zhèn", "Thunder"),    // ☳ 東北 centre 45°
        /* 2 */ LabelData("離", "Lí", "Fire"),         // ☲ 東  centre 90°
        /* 3 */ LabelData("兌", "Duì", "Lake"),        // ☱ 東南 centre 135°
        /* 4 */ LabelData("乾", "Qián", "Heaven"),     // ☰ 南  centre 180°
        /* 5 */ LabelData("坤", "Kūn", "Earth"),       // ☷ 西南 centre 225°
        /* 6 */ LabelData("艮", "Gèn", "Mountain"),    // ☶ 西  centre 270°
        /* 7 */ LabelData("坎", "Kǎn", "Water")        // ☵ 西北 centre 315°
    )

    // ─── Ring 3 — 後天八卦 (King Wen / Later Heaven Arrangement) ─────────────
    //
    // Sector index 0 = ☵ 坎 北 at [337.5°, 22.5°) — wrap-around, centre 0°/North
    // Sector index 4 = ☲ 離 南 at [157.5°, 202.5°) — centre 180°/South
    //
    // character field uses format "☲ 離 南" (trigram symbol + 卦名 + 方位).
    // Trigram symbols are retained in all language modes per FSPEC §4.8.
    // English per FSPEC §4.8 Ring 3 table.
    private val ring3Labels: Array<LabelData> = arrayOf(
        /* 0 */ LabelData("☵ 坎 北", "Kǎn · Běi", "Kan · North"),         // 北  [337.5°, 22.5°) wrap
        /* 1 */ LabelData("☶ 艮 東北", "Gèn · Dōngběi", "Gen · Northeast"),  // 東北 [22.5°, 67.5°)
        /* 2 */ LabelData("☳ 震 東", "Zhèn · Dōng", "Zhen · East"),         // 東  [67.5°, 112.5°)
        /* 3 */ LabelData("☴ 巽 東南", "Xùn · Dōngnán", "Xun · Southeast"),  // 東南 [112.5°, 157.5°)
        /* 4 */ LabelData("☲ 離 南", "Lí · Nán", "Li · South"),             // 南  [157.5°, 202.5°)
        /* 5 */ LabelData("☷ 坤 西南", "Kūn · Xīnán", "Kun · Southwest"),    // 西南 [202.5°, 247.5°)
        /* 6 */ LabelData("☱ 兌 西", "Duì · Xī", "Dui · West"),             // 西  [247.5°, 292.5°)
        /* 7 */ LabelData("☰ 乾 西北", "Qián · Xīběi", "Qian · Northwest")   // 西北 [292.5°, 337.5°)
    )

    // ─── Ring 4 — 十二地支 (Twelve Earthly Branches) ─────────────────────────
    //
    // Sector index 0 = 子 at [345°, 15°) — wrap-around, centre 0°/North
    // Sector index 6 = 午 at [165°, 195°) — centre 180°/South
    //
    // English per FSPEC §4.8 Ring 4 / §5.8 table (zodiac animal names).
    private val ring4Labels: Array<LabelData> = arrayOf(
        /* 0  */ LabelData("子", "Zǐ", "Rat"),       // 北  [345°, 15°) wrap
        /* 1  */ LabelData("丑", "Chǒu", "Ox"),      // [15°, 45°)
        /* 2  */ LabelData("寅", "Yín", "Tiger"),    // [45°, 75°)
        /* 3  */ LabelData("卯", "Mǎo", "Rabbit"),   // [75°, 105°)
        /* 4  */ LabelData("辰", "Chén", "Dragon"),  // [105°, 135°)
        /* 5  */ LabelData("巳", "Sì", "Snake"),     // [135°, 165°)
        /* 6  */ LabelData("午", "Wǔ", "Horse"),     // [165°, 195°)
        /* 7  */ LabelData("未", "Wèi", "Goat"),     // [195°, 225°)
        /* 8  */ LabelData("申", "Shēn", "Monkey"),  // [225°, 255°)
        /* 9  */ LabelData("酉", "Yǒu", "Rooster"),  // [255°, 285°)
        /* 10 */ LabelData("戌", "Xū", "Dog"),       // [285°, 315°)
        /* 11 */ LabelData("亥", "Hài", "Pig")       // [315°, 345°)
    )

    // ─── Ring 5 — 二十四山 (Twenty-Four Mountains) ────────────────────────────
    //
    // Sector index 0  = 壬 at [337.5°, 352.5°) — just west of North
    // Sector index 1  = 子 at [352.5°, 7.5°)  — wrap-around, centre 0°/North
    // Sector index 13 = 午 at [172.5°, 187.5°) — centre 180°/South
    //
    // English uses pinyin-based English labels per FSPEC §4.8 Ring 5 table.
    private val ring5Labels: Array<LabelData> = arrayOf(
        /* 0  */ LabelData("壬", "Rén", "Ren (Water-Yang)"),      // [337.5°, 352.5°)
        /* 1  */ LabelData("子", "Zǐ", "Rat"),                    // [352.5°, 7.5°) wrap
        /* 2  */ LabelData("癸", "Guǐ", "Gui (Water-Yin)"),       // [7.5°, 22.5°)
        /* 3  */ LabelData("丑", "Chǒu", "Ox"),                   // [22.5°, 37.5°)
        /* 4  */ LabelData("艮", "Gèn", "Gen (Mountain)"),        // [37.5°, 52.5°)
        /* 5  */ LabelData("寅", "Yín", "Tiger"),                 // [52.5°, 67.5°)
        /* 6  */ LabelData("甲", "Jiǎ", "Jia (Wood-Yang)"),       // [67.5°, 82.5°)
        /* 7  */ LabelData("卯", "Mǎo", "Rabbit"),                // [82.5°, 97.5°)
        /* 8  */ LabelData("乙", "Yǐ", "Yi (Wood-Yin)"),          // [97.5°, 112.5°)
        /* 9  */ LabelData("辰", "Chén", "Dragon"),               // [112.5°, 127.5°)
        /* 10 */ LabelData("巽", "Xùn", "Xun (Wind)"),            // [127.5°, 142.5°)
        /* 11 */ LabelData("巳", "Sì", "Snake"),                  // [142.5°, 157.5°)
        /* 12 */ LabelData("丙", "Bǐng", "Bing (Fire-Yang)"),     // [157.5°, 172.5°)
        /* 13 */ LabelData("午", "Wǔ", "Horse"),                  // [172.5°, 187.5°)
        /* 14 */ LabelData("丁", "Dīng", "Ding (Fire-Yin)"),      // [187.5°, 202.5°)
        /* 15 */ LabelData("未", "Wèi", "Goat"),                  // [202.5°, 217.5°)
        /* 16 */ LabelData("坤", "Kūn", "Kun (Earth)"),           // [217.5°, 232.5°)
        /* 17 */ LabelData("申", "Shēn", "Monkey"),               // [232.5°, 247.5°)
        /* 18 */ LabelData("庚", "Gēng", "Geng (Metal-Yang)"),    // [247.5°, 262.5°)
        /* 19 */ LabelData("酉", "Yǒu", "Rooster"),               // [262.5°, 277.5°)
        /* 20 */ LabelData("辛", "Xīn", "Xin (Metal-Yin)"),       // [277.5°, 292.5°)
        /* 21 */ LabelData("戌", "Xū", "Dog"),                    // [292.5°, 307.5°)
        /* 22 */ LabelData("乾", "Qián", "Qian (Heaven)"),        // [307.5°, 322.5°)
        /* 23 */ LabelData("亥", "Hài", "Pig")                    // [322.5°, 337.5°)
    )

    // ─── Ring 6 — 六十分金 (Sixty Gold Divisions) ─────────────────────────────
    //
    // Sector index 0  = 庚子分金 at [352°, 358°)
    // Sector index 1  = 壬子分金 at [358°, 4°) — wrap-around (BR-04)
    // Sector index 16 = 壬卯分金 at [88°, 94°)
    // Sector index 31 = 壬午分金 at [178°, 184°)
    // Sector index 46 = 壬酉分金 at [268°, 274°)
    //
    // NOTE (OQ-P3-01): This table uses the 三元/通用 convention. It MUST be
    // validated by a practicing feng shui consultant before production release.
    // The full 60-entry table is from FSPEC §4.7 (1-based #1..#60 → index 0..59).
    // pinyin and english are intentionally empty for Ring 6 — no §5.8 mapping exists.
    private val ring6Labels: Array<LabelData> = arrayOf(
        /* 0  FSPEC#1  */ LabelData("庚子分金", "", ""),   // [352°, 358°)
        /* 1  FSPEC#2  */ LabelData("壬子分金", "", ""),   // [358°, 4°) wrap (BR-04)
        /* 2  FSPEC#3  */ LabelData("甲子分金", "", ""),   // [4°, 10°)
        /* 3  FSPEC#4  */ LabelData("丙子分金", "", ""),   // [10°, 16°)
        /* 4  FSPEC#5  */ LabelData("戊子分金", "", ""),   // [16°, 22°)
        /* 5  FSPEC#6  */ LabelData("庚丑分金", "", ""),   // [22°, 28°)
        /* 6  FSPEC#7  */ LabelData("壬丑分金", "", ""),   // [28°, 34°)
        /* 7  FSPEC#8  */ LabelData("甲丑分金", "", ""),   // [34°, 40°)
        /* 8  FSPEC#9  */ LabelData("丙丑分金", "", ""),   // [40°, 46°)
        /* 9  FSPEC#10 */ LabelData("戊丑分金", "", ""),   // [46°, 52°)
        /* 10 FSPEC#11 */ LabelData("庚寅分金", "", ""),   // [52°, 58°)
        /* 11 FSPEC#12 */ LabelData("壬寅分金", "", ""),   // [58°, 64°)
        /* 12 FSPEC#13 */ LabelData("甲寅分金", "", ""),   // [64°, 70°)
        /* 13 FSPEC#14 */ LabelData("丙寅分金", "", ""),   // [70°, 76°)
        /* 14 FSPEC#15 */ LabelData("戊寅分金", "", ""),   // [76°, 82°)
        /* 15 FSPEC#16 */ LabelData("庚卯分金", "", ""),   // [82°, 88°)
        /* 16 FSPEC#17 */ LabelData("壬卯分金", "", ""),   // [88°, 94°)
        /* 17 FSPEC#18 */ LabelData("甲卯分金", "", ""),   // [94°, 100°)
        /* 18 FSPEC#19 */ LabelData("丙卯分金", "", ""),   // [100°, 106°)
        /* 19 FSPEC#20 */ LabelData("戊卯分金", "", ""),   // [106°, 112°)
        /* 20 FSPEC#21 */ LabelData("庚辰分金", "", ""),   // [112°, 118°)
        /* 21 FSPEC#22 */ LabelData("壬辰分金", "", ""),   // [118°, 124°)
        /* 22 FSPEC#23 */ LabelData("甲辰分金", "", ""),   // [124°, 130°)
        /* 23 FSPEC#24 */ LabelData("丙辰分金", "", ""),   // [130°, 136°)
        /* 24 FSPEC#25 */ LabelData("戊辰分金", "", ""),   // [136°, 142°)
        /* 25 FSPEC#26 */ LabelData("庚巳分金", "", ""),   // [142°, 148°)
        /* 26 FSPEC#27 */ LabelData("壬巳分金", "", ""),   // [148°, 154°)
        /* 27 FSPEC#28 */ LabelData("甲巳分金", "", ""),   // [154°, 160°)
        /* 28 FSPEC#29 */ LabelData("丙巳分金", "", ""),   // [160°, 166°)
        /* 29 FSPEC#30 */ LabelData("戊巳分金", "", ""),   // [166°, 172°)
        /* 30 FSPEC#31 */ LabelData("庚午分金", "", ""),   // [172°, 178°)
        /* 31 FSPEC#32 */ LabelData("壬午分金", "", ""),   // [178°, 184°)
        /* 32 FSPEC#33 */ LabelData("甲午分金", "", ""),   // [184°, 190°)
        /* 33 FSPEC#34 */ LabelData("丙午分金", "", ""),   // [190°, 196°)
        /* 34 FSPEC#35 */ LabelData("戊午分金", "", ""),   // [196°, 202°)
        /* 35 FSPEC#36 */ LabelData("庚未分金", "", ""),   // [202°, 208°)
        /* 36 FSPEC#37 */ LabelData("壬未分金", "", ""),   // [208°, 214°)
        /* 37 FSPEC#38 */ LabelData("甲未分金", "", ""),   // [214°, 220°)
        /* 38 FSPEC#39 */ LabelData("丙未分金", "", ""),   // [220°, 226°)
        /* 39 FSPEC#40 */ LabelData("戊未分金", "", ""),   // [226°, 232°)
        /* 40 FSPEC#41 */ LabelData("庚申分金", "", ""),   // [232°, 238°)
        /* 41 FSPEC#42 */ LabelData("壬申分金", "", ""),   // [238°, 244°)
        /* 42 FSPEC#43 */ LabelData("甲申分金", "", ""),   // [244°, 250°)
        /* 43 FSPEC#44 */ LabelData("丙申分金", "", ""),   // [250°, 256°)
        /* 44 FSPEC#45 */ LabelData("戊申分金", "", ""),   // [256°, 262°)
        /* 45 FSPEC#46 */ LabelData("庚酉分金", "", ""),   // [262°, 268°)
        /* 46 FSPEC#47 */ LabelData("壬酉分金", "", ""),   // [268°, 274°)
        /* 47 FSPEC#48 */ LabelData("甲酉分金", "", ""),   // [274°, 280°)
        /* 48 FSPEC#49 */ LabelData("丙酉分金", "", ""),   // [280°, 286°)
        /* 49 FSPEC#50 */ LabelData("戊酉分金", "", ""),   // [286°, 292°)
        /* 50 FSPEC#51 */ LabelData("庚戌分金", "", ""),   // [292°, 298°)
        /* 51 FSPEC#52 */ LabelData("壬戌分金", "", ""),   // [298°, 304°)
        /* 52 FSPEC#53 */ LabelData("甲戌分金", "", ""),   // [304°, 310°)
        /* 53 FSPEC#54 */ LabelData("丙戌分金", "", ""),   // [310°, 316°)
        /* 54 FSPEC#55 */ LabelData("戊戌分金", "", ""),   // [316°, 322°)
        /* 55 FSPEC#56 */ LabelData("庚亥分金", "", ""),   // [322°, 328°)
        /* 56 FSPEC#57 */ LabelData("壬亥分金", "", ""),   // [328°, 334°)
        /* 57 FSPEC#58 */ LabelData("甲亥分金", "", ""),   // [334°, 340°)
        /* 58 FSPEC#59 */ LabelData("丙亥分金", "", ""),   // [340°, 346°)
        /* 59 FSPEC#60 */ LabelData("戊亥分金", "", "")    // [346°, 352°)
    )

    /**
     * Returns the label for a 先天八卦 (Fuxi / Earlier Heaven) sector.
     *
     * @param sectorIndex 0-based index 0–7, matching [SectorLookup.ring2] output.
     *                    Index 0 = ☴ 巽 (North wrap-around). Index 4 = ☰ 乾 (South).
     */
    fun ring2Label(sectorIndex: Int): LabelData = ring2Labels[sectorIndex]

    /**
     * Returns the label for a 後天八卦 (King Wen / Later Heaven) sector.
     *
     * @param sectorIndex 0-based index 0–7, matching [SectorLookup.ring3] output.
     *                    Index 0 = ☵ 坎 北 (North wrap-around). Index 4 = ☲ 離 南 (South).
     */
    fun ring3Label(sectorIndex: Int): LabelData = ring3Labels[sectorIndex]

    /**
     * Returns the label for a 十二地支 (Twelve Earthly Branches) sector.
     *
     * @param sectorIndex 0-based index 0–11, matching [SectorLookup.ring4] output.
     *                    Index 0 = 子 (North wrap-around). Index 6 = 午 (South).
     */
    fun ring4Label(sectorIndex: Int): LabelData = ring4Labels[sectorIndex]

    /**
     * Returns the label for a 二十四山 (Twenty-Four Mountains) sector.
     *
     * @param sectorIndex 0-based index 0–23, matching [SectorLookup.ring5] output.
     *                    Index 0 = 壬. Index 1 = 子 (North wrap-around). Index 13 = 午 (South).
     */
    fun ring5Label(sectorIndex: Int): LabelData = ring5Labels[sectorIndex]

    /**
     * Returns the label for a 六十分金 (Sixty Gold Divisions) sector.
     *
     * NOTE (OQ-P3-01): Full table requires validation by a feng shui practitioner
     * before release. Uses 三元/通用 convention from FSPEC §4.7.
     *
     * @param sectorIndex 0-based index 0–59, matching [SectorLookup.ring6] output.
     *                    Index 0 = 庚子分金 [352°, 358°).
     *                    Index 1 = 壬子分金 [358°, 4°) — wrap-around sector (BR-04).
     *                    Index 31 = 壬午分金 [178°, 184°).
     */
    fun ring6Label(sectorIndex: Int): LabelData = ring6Labels[sectorIndex]

    // ─── Internal size accessors (for unit tests — PROP-01-011) ──────────────
    internal val ring2LabelCount: Int get() = ring2Labels.size
    internal val ring3LabelCount: Int get() = ring3Labels.size
    internal val ring4LabelCount: Int get() = ring4Labels.size
    internal val ring5LabelCount: Int get() = ring5Labels.size
    internal val ring6LabelCount: Int get() = ring6Labels.size
}
