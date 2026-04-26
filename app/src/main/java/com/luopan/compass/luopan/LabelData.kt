package com.luopan.compass.luopan

/**
 * Holds all display representations of a single ring sector label.
 *
 * @param character Primary Traditional Chinese character(s), e.g. "午", "☲ 離 南".
 *                  Always non-empty. Displayed by default (zh-Hant baseline).
 * @param pinyin    Pinyin romanization, e.g. "Wǔ", "Lí · Nán".
 *                  Displayed when showRomanization is enabled.
 * @param english   English equivalent per FSPEC §4.8 / REQ §5.8.
 *                  Empty string if no standard English equivalent is defined for this entry.
 *                  Displayed when showMyLanguage is enabled (where non-empty).
 */
data class LabelData(
    val character: String,
    val pinyin: String,
    val english: String
)
