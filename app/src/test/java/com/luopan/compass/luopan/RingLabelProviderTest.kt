package com.luopan.compass.luopan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for RingLabelProvider.
 *
 * Sector indices are 0-based from North reference (clockwise).
 *
 * Ring 2 (先天八卦 Fuxi — 8 sectors × 45°):
 *   Index 0 = ☴ 巽 (North wrap-around), Index 4 = ☰ 乾 (South)
 *
 * Ring 3 (後天八卦 King Wen — 8 sectors × 45°):
 *   Index 0 = ☵ 坎 北 (North wrap-around), Index 4 = ☲ 離 南 (South)
 *
 * Ring 4 (十二地支 — 12 sectors × 30°):
 *   Index 0 = 子 (North wrap-around), Index 6 = 午 (South)
 *
 * Ring 5 (二十四山 — 24 sectors × 15°):
 *   Index 0 = 壬, Index 1 = 子 (North wrap-around), Index 13 = 午 (South)
 *
 * Ring 6 (六十分金 — 60 sectors × 6°):
 *   Index 0 = 壬子分金 (North wrap-around), Index 31 = 壬午分金 (South vicinity)
 */
class RingLabelProviderTest {

    // ─── Ring 2 (先天八卦) ────────────────────────────────────────────────────

    @Test
    fun `ring2_sector4_is_qian_south`() {
        val label = RingLabelProvider.ring2Label(4)
        assertEquals("乾", label.character)
        assertEquals("Qián", label.pinyin)
        assertTrue(
            "Ring 2 sector 4 english should reference Heaven or Qian, got: ${label.english}",
            label.english.contains("Heaven", ignoreCase = true) || label.english.contains("Qian", ignoreCase = true)
        )
    }

    @Test
    fun `ring2_sector0_is_xun_north_wraparound`() {
        val label = RingLabelProvider.ring2Label(0)
        assertEquals("巽", label.character)
        assertEquals("Xùn", label.pinyin)
    }

    @Test
    fun `ring2_all_labels_non_empty`() {
        for (i in 0 until 8) {
            val label = RingLabelProvider.ring2Label(i)
            assertFalse("Ring 2 sector $i character should not be empty", label.character.isEmpty())
            assertFalse("Ring 2 sector $i pinyin should not be empty", label.pinyin.isEmpty())
        }
    }

    // ─── Ring 3 (後天八卦) ────────────────────────────────────────────────────

    @Test
    fun `ring3_sector4_is_li_south`() {
        val label = RingLabelProvider.ring3Label(4)
        assertEquals("☲ 離 南", label.character)
        assertEquals("Lí · Nán", label.pinyin)
        assertEquals("Li · South", label.english)
    }

    @Test
    fun `ring3_sector0_is_kan_north_wraparound`() {
        val label = RingLabelProvider.ring3Label(0)
        assertEquals("☵ 坎 北", label.character)
        assertEquals("Kǎn · Běi", label.pinyin)
        assertEquals("Kan · North", label.english)
    }

    @Test
    fun `ring3_all_labels_non_empty`() {
        for (i in 0 until 8) {
            val label = RingLabelProvider.ring3Label(i)
            assertFalse("Ring 3 sector $i character should not be empty", label.character.isEmpty())
            assertFalse("Ring 3 sector $i pinyin should not be empty", label.pinyin.isEmpty())
            assertFalse("Ring 3 sector $i english should not be empty", label.english.isEmpty())
        }
    }

    @Test
    fun `ring3_all_labels_have_english_equivalents`() {
        for (i in 0 until 8) {
            val label = RingLabelProvider.ring3Label(i)
            assertTrue(
                "Ring 3 sector $i english should be non-empty per §5.8",
                label.english.isNotEmpty()
            )
        }
    }

    // ─── Ring 4 (十二地支) ────────────────────────────────────────────────────

    @Test
    fun `ring4_sector0_is_zi`() {
        val label = RingLabelProvider.ring4Label(0)
        assertEquals("子", label.character)
        assertEquals("Zǐ", label.pinyin)
        assertEquals("Rat", label.english)
    }

    @Test
    fun `ring4_sector6_is_wu`() {
        val label = RingLabelProvider.ring4Label(6)
        assertEquals("午", label.character)
        assertEquals("Wǔ", label.pinyin)
        assertEquals("Horse", label.english)
    }

    @Test
    fun `ring4_all_labels_non_empty`() {
        for (i in 0 until 12) {
            val label = RingLabelProvider.ring4Label(i)
            assertFalse("Ring 4 sector $i character should not be empty", label.character.isEmpty())
            assertFalse("Ring 4 sector $i pinyin should not be empty", label.pinyin.isEmpty())
        }
    }

    @Test
    fun `ring4_all_labels_have_english_equivalents`() {
        for (i in 0 until 12) {
            val label = RingLabelProvider.ring4Label(i)
            assertTrue(
                "Ring 4 sector $i english should be non-empty per §5.8",
                label.english.isNotEmpty()
            )
        }
    }

    // ─── Ring 5 (二十四山) ────────────────────────────────────────────────────

    @Test
    fun `ring5_sector13_is_wu`() {
        val label = RingLabelProvider.ring5Label(13)
        assertEquals("午", label.character)
        assertEquals("Wǔ", label.pinyin)
    }

    @Test
    fun `ring5_sector4_is_gen`() {
        val label = RingLabelProvider.ring5Label(4)
        assertEquals("艮", label.character)
        assertEquals("Gèn", label.pinyin)
    }

    @Test
    fun `ring5_sector0_is_ren`() {
        val label = RingLabelProvider.ring5Label(0)
        assertEquals("壬", label.character)
        assertEquals("Rén", label.pinyin)
    }

    @Test
    fun `ring5_all_labels_non_empty`() {
        for (i in 0 until 24) {
            val label = RingLabelProvider.ring5Label(i)
            assertFalse("Ring 5 sector $i character should not be empty", label.character.isEmpty())
            assertFalse("Ring 5 sector $i pinyin should not be empty", label.pinyin.isEmpty())
        }
    }

    // ─── Ring 6 (六十分金) ────────────────────────────────────────────────────

    // NOTE: TSPEC §4.1 explicitly states "Sector #0 (庚子分金) has range [352f, 358f)"
    // and "Sector #1 (壬子分金)" is the wrap-around at [358°, 4°).
    // The task prompt's note "Sector 0: 壬子分金" is a typo; indices 16, 31, 46 all
    // confirm 庚子 at index 0. TSPEC is the source of truth per skill instructions.
    @Test
    fun `ring6_sector0_is_gengzi_fenjin`() {
        val label = RingLabelProvider.ring6Label(0)
        assertEquals("庚子分金", label.character)
    }

    @Test
    fun `ring6_sector1_is_renz_i_fenjin_wraparound`() {
        val label = RingLabelProvider.ring6Label(1)
        assertEquals("壬子分金", label.character)
    }

    @Test
    fun `ring6_sector31_is_renwu_fenjin`() {
        val label = RingLabelProvider.ring6Label(31)
        assertEquals("壬午分金", label.character)
    }

    @Test
    fun `ring6_sector16_is_renm_ao_fenjin`() {
        val label = RingLabelProvider.ring6Label(16)
        assertEquals("壬卯分金", label.character)
    }

    @Test
    fun `ring6_sector46_is_renyou_fenjin`() {
        val label = RingLabelProvider.ring6Label(46)
        assertEquals("壬酉分金", label.character)
    }

    @Test
    fun `ring6_all_labels_non_empty`() {
        for (i in 0 until 60) {
            val label = RingLabelProvider.ring6Label(i)
            assertFalse("Ring 6 sector $i character should not be empty", label.character.isEmpty())
        }
    }
}
