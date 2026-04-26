# Cross-Review: Test Engineer — REQ-luopan-p3-luopan-mode

| Field | Value |
|-------|-------|
| Reviewer | Test Engineer |
| Document | REQ-luopan-p3-luopan-mode.md |
| Date | 2026-04-25 |
| Recommendation | Need Attention |

## Summary

REQ-luopan-p3-luopan-mode.md covers the Luopan display mode with generally good structure and correct cross-references to the master REQ ring-label tables. However, several acceptance criteria are untestable as written due to missing ring-label data for Rings 2, 5, and 6 directly in the phase REQ, two contradictions between this document and the master REQ (坐向 lock confidence threshold and REQ-DISPLAY-06 priority level), and under-specified behaviors for pinch-to-zoom boundaries, ring-visibility persistence, and the numerical readout format. Edge cases for sector boundary crossings, modular bearing wrap-around, and localization failure modes are absent or implicit.

## Findings

### High Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| F-01 | High | **坐向 lock confidence threshold conflict.** §8 / Scenario E specifies the lock is disabled only at "Poor" confidence — implying Moderate is permitted. Master REQ §6.5 REQ-DISPLAY-06 states "Locking is only permitted when confidence is 'High' or 'Moderate'". However the phase REQ §5.1 REQ-DISPLAY-06 key spec says "disabled at Poor confidence" — which matches the master on permitting Moderate. Scenario E then uses "High" as the precondition for the positive case, but never tests the Moderate case. Without a Moderate acceptance criterion, it is impossible to write an automated test that verifies Moderate is allowed. A test author reading only §8 might infer Moderate is blocked. | §5.1 REQ-DISPLAY-06, §8 Scenario E |
| F-02 | High | **No ring-label test data for Rings 2, 5, and 6 inline in this document.** Rings 5 (二十四山, 24 × 15°) and 6 (六十分金, 60 × 6°) carry no label tables here; they are referenced only by pointer to master REQ §10.2c and an unlinked §10.2 reference. Ring 2 (先天八卦) has the full table in master REQ §10.2a but not in this phase REQ. A test engineer writing look-up table unit tests from the phase REQ alone cannot verify correctness of sector-to-label mapping without jumping to the master, increasing the risk of stale cross-document references. The 六十分金 labels (the most precision-critical ring at 6°/division) are referenced but never tabulated anywhere in the phase REQ or via a hyperlink that resolves. | §5.3, §5.1 REQ-LUOPAN-01 |
| F-03 | High | **Scenario B acceptance criterion mixes two readout formats.** Scenario B specifies the numerical panel must show `"☲ 離 · 南 · 午 (Wǔ) · 180.0° · True N · [confidence]"`. The master REQ §6.5 REQ-DISPLAY-05 example shows `"午 (Wǔ) · ☲ 離 南 · 180.0° · True N · 丙子分金 · High"`. The field order is different (时辰 before 方位 in master, 方位 before 时辰 in Scenario B), and master's example at High confidence includes a 分金 label `丙子分金` that is absent from Scenario B even though the given state is implicitly High. A test that validates the panel string verbatim will fail against one implementation while passing against the other. Engineering cannot write a single well-defined assertion. | §10 Scenario B, master REQ §6.5 REQ-DISPLAY-05 |
| F-04 | High | **No acceptance criteria for sector boundary crossings.** The 子/亥 wrap-around at 0°/360° for Ring 4 is called out as a risk in the master REQ (§10.2b note) and REQ-LUOPAN-02. No acceptance criterion in this phase REQ tests the boundary — e.g., device at 344.9° (亥), 345.0° (子), 15.0° (丑). Without explicit boundary test cases, an off-by-one in the sector assignment logic will never be caught by a test written from this document. The same gap applies to Ring 3 (後天八卦 sector boundaries at 22.5°, 67.5°, 112.5°, etc.) and Ring 5 (二十四山 at 7.5°, 22.5°, etc.). | §5.2 REQ-LUOPAN-02, §10 Scenario C |

### Medium Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| F-05 | Medium | **Pinch-to-zoom boundaries are not verified by any acceptance criterion.** §7 specifies scale range 0.8×–2.0×. No acceptance test verifies: (a) that scale below 0.8× is clamped, (b) that scale above 2.0× is clamped, (c) that zoom state survives an orientation change or a mode switch, (d) that zooming does not affect heading computation. Without these, the boundary enforcement is untestable from the REQ. | §7 (Interaction Behavior) |
| F-06 | Medium | **Ring visibility persistence is under-specified and untestable as written.** Scenario G states ring visibility "is not persisted across app restarts (or is persisted — engineering decision, but must be documented in settings)". This is not an acceptance criterion — it is a deferred decision. A test cannot be written without knowing the required behavior. The parenthetical acknowledges the ambiguity but leaves it open. This will result in either no test or a test that is arbitrarily picked by the implementing engineer. | §10 Scenario G |
| F-07 | Medium | **分金 label value is never specified for any test bearing.** Scenario D verifies that 分金 is shown at High confidence but does not state what value is expected for any given bearing. Scenario B at 180° is implicitly High confidence (master REQ example shows "丙子分金") but this phase REQ omits the expected label. Without expected label values for at least 2–3 bearings (e.g., 180°, 0°, 90°), it is impossible to write a correctness test for Ring 6 sector assignment. The 六十分金 reference table is not linked or included in this document. | §10 Scenario D, §5.3 Ring 6 |
| F-08 | Medium | **Legibility acceptance criterion is not mechanically testable.** §6 states "all 12 地支 characters (Ring 4), all 24 山 characters (Ring 5), and all 60 分金 labels (Ring 6) must be readable by a user with 20/40 corrected vision at 30 cm." This is a human-perception criterion with no proxy metric (e.g., minimum rendered character height in dp, minimum contrast ratio against background) that can be asserted in an automated or scripted test. The color values and font size minimums in §6 (8sp, 11sp, 12sp, 14sp) are the right level of precision — the criterion should be expressed as: "rendered size ≥ N dp at 1× zoom on a 1080p 5-inch screen" so it can be verified via screenshot analysis or layout inspection. | §6 (Visual Specification Reference) |
| F-09 | Medium | **No negative test for "Lock 向" label content when 坐 wraps past 360°.** Scenario E locks at 45° and derives 坐 = 225°. Neither this scenario nor any other tests the modular wrap-around: e.g., locking at 270° should derive 坐 = 90°; locking at 181° should derive 坐 = 1°. Without these, a naive implementation using `向 + 180` without modular reduction will pass Scenario E and fail at real-world bearings near 180°–360°. | §8, §10 Scenario E |
| F-10 | Medium | **No failure-mode scenario for switching from Luopan Mode to Modern Mode while 坐向 is locked.** The lock state lifecycle is unspecified for mode switches: is the lock cleared, suspended, or preserved? If preserved and the user returns to Luopan Mode, is the overlay restored? Without a defined behavior, engineers will make an arbitrary choice, and no test can verify the correct outcome. | §7, §8, §10 Scenario E |
| F-11 | Medium | **Confidence threshold for 分金 display uses "High" without a numeric definition in this document.** Scenarios D and B reference "High confidence" but the definition (all dimensions Good — per master REQ §8.1) is not summarized here. If the High confidence conditions change in a future phase, a test author reading this document alone cannot determine what sensor/calibration inputs to inject to produce the exact boundary state. A brief normative reference ("High confidence per master REQ §8.1 accuracy specification") and the upstream preconditions should be stated here or in a linked test data table. | §10 Scenario D, §5.1 REQ-DISPLAY-05 |
| F-12 | Medium | **"Show in my language" toggle for English ring equivalents is specified without any mapping table.** REQ-L10N-02 states ring labels can be displayed "in English/Japanese equivalents where defined" but no mapping table is provided or linked. A test verifying that toggling to English shows the correct label for each trigram/branch/mountain cannot be written without knowing what "English equivalent" means for, e.g., Ring 4 地支 or Ring 5 二十四山 entries. "Where defined" implies some entries may have no translation — those absence cases are also untestable. | §5.4 REQ-L10N-02 |

### Low Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| F-13 | Low | **Scenario F does not test that the Traditional Chinese default is enforced on a device with locale set to `ja` or `zh-Hans`.** Only `en` is tested. A defect that renders Japanese locale in Japanese characters instead of zh-Hant would pass Scenario F. One additional test row for `ja` system locale would make this criterion exhaustive. | §10 Scenario F |
| F-14 | Low | **Scenario A legibility criterion is not testable as an automated E2E test.** "All six rings are visible and their labels legible on a 5-inch 1080p screen" cannot be asserted by an automated test framework without a visual regression tool. It should be marked as a manual exploratory test or supplemented with a screenshot comparison baseline. | §10 Scenario A |
| F-15 | Low | **No scenario for dial rotation direction.** Scenario A states "pointer shows current heading" and "dial rotates counter-clockwise when device rotates clockwise" but no acceptance criterion measures the magnitude: rotating 30° clockwise should move the dial 30° counter-clockwise, not 29° or 31°. A quantitative criterion (1:1 mapping, ±2°) is testable; the current phrasing is not. | §10 Scenario A, §7 |
| F-16 | Low | **REQ-DISPLAY-06 priority inconsistency between documents.** Phase REQ §5.1 marks REQ-DISPLAY-06 as P0 (elevated). Master REQ §6.5 REQ-DISPLAY-06 header says P1. Priority disagreement does not block test writing but may affect test prioritization and MVP gate decisions. | §5.1, master REQ §6.5 REQ-DISPLAY-06 |
| F-17 | Low | **Tooltip text "Cannot lock — heading is unreliable" is not verified for accessibility or localization.** Scenario E specifies the tooltip string in English only. There is no acceptance criterion for: (a) correct zh-Hant text, (b) screen-reader / TalkBack reachability, (c) whether the tooltip appears on hover, long-press, or is always-visible. | §10 Scenario E |
| F-18 | Low | **No test data for pinyin tone-mark correctness.** Scenario C expects "卯 (Mǎo)" — a specific tone mark. The romanization toggle test in Scenario F expects "子 Zǐ" and "南 Nán". No broader table specifies expected pinyin for all 24 山, all 12 地支, and all 8 后天八卦 direction names. A typo in one tone mark (e.g., "Mao" instead of "Mǎo") would not be caught by any current acceptance criterion other than Scenarios C and F's single examples. | §10 Scenarios C, F; §5.4 REQ-L10N-02 |

## Recommendation

**Need Attention**

The following must be resolved before the FSPEC or TSPEC can be authored:

1. **F-01** — Clarify whether Moderate confidence permits 坐向 lock. Add an explicit acceptance criterion for the Moderate case in Scenario E.
2. **F-02** — Either inline the 六十分金 label table (Ring 6) in this document or add a resolvable hyperlink. Verify the 先天八卦 and 二十四山 tables are reachable and correct.
3. **F-03** — Align the numerical panel field order and content between Scenario B and master REQ §6.5 REQ-DISPLAY-05. Define a single canonical format string.
4. **F-04** — Add explicit boundary acceptance criteria for sector transitions in Rings 3, 4, and 5, including the 0°/360° wrap for Ring 4 (子/亥 boundary).
5. **F-06** — Make a definitive engineering decision on ring visibility persistence and encode it as a testable acceptance criterion in Scenario G.
6. **F-07** — Specify the expected 分金 label for at least two bearings (e.g., 180°, 0°) so a unit test for Ring 6 sector assignment can be written.
7. **F-09** — Add Scenario E rows for 坐 wrap-around: at least one bearing ≥ 181° to verify modular reduction.
