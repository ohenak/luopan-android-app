# Cross-Review: Product Manager — PROPERTIES-luopan-p3-luopan-mode

| Field | Value |
|-------|-------|
| Reviewer | Product Manager |
| Document | PROPERTIES-luopan-p3-luopan-mode.md |
| Date | 2026-04-25 |
| Recommendation | Approved with Minor Issues |

## Summary

The PROPERTIES document is well-structured and covers the majority of acceptance criteria and business rules from REQ v0.3-draft and FSPEC v1.1. The coverage matrix is thorough, the gap analysis is honest, and the property groups align logically with the feature flows. All three P0 user stories (US-02, US-07, US-12) have corresponding E2E properties.

However, several user-visible behaviors from the REQ acceptance criteria lack a corresponding property at the correct test level (E2E vs. unit-only), and one legibility requirement from REQ §6 is entirely absent. None of the missing items are P0 blockers, but they should be addressed before the properties are considered final.

---

## Findings

### High Priority

None.

---

### Medium Priority

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Medium | **Scenario J E2E coverage is unit-only.** REQ Scenario J and AC-22 require that when the user navigates to Settings, switches north reference, and returns to Luopan Mode, all readout fields update immediately. The PROPERTIES document covers this via PROP-03-031 (unit, mapper pass-through) and PROP-03-032 (unit, 山 labels invariant). However, no E2E property tests the complete Scenario J-1 user journey end-to-end: Settings navigation → north switch → return to Luopan Mode → readout updates. The user-visible behavior (the readout shows "Mag N" and a different bearing value without any user action in Luopan Mode) is only validated at the data-model layer, not at the UI layer. | REQ §12 Scenario J, AC-22, REQ-L10N-02 |
| F-02 | Medium | **Ring labels visible under pointer — Scenarios B and C tested at mapper level only.** REQ Scenarios B and C require that specific ring labels appear "displayed centered under the pointer" on the dial. PROP-03-033 and PROP-03-034 verify mapper output (the `LuopanState` data). No E2E property verifies that the label that maps to the current bearing is actually rendered aligned with (or pointing to) the fixed pointer triangle in the View. A user could observe the correct data in the readout panel but the dial rendering could show the wrong sector under the pointer (e.g., off-by-one in the rotation transform) and no property would catch it at the E2E level. | REQ §12 Scenarios B, C; AC-03, AC-04; US-12 |
| F-03 | Medium | **Legibility requirement has no property.** REQ §6 states: "On a 5-inch 1080p screen at default brightness, all 12 地支 characters (Ring 4), all 24 山 characters (Ring 5), and all 60 分金 labels (Ring 6) must be readable by a user with 20/40 corrected vision at 30 cm." This user-facing accessibility requirement is not reflected in any property in PG-04 (LuopanView Rendering) or PG-08 (Performance). PROP-08-065 through 068 cover frame rate, latency, and battery — but not text legibility. This is a user-visible behavior that directly impacts the core persona (Master Li, feng shui practitioner). | REQ §6 Legibility requirement, REQ-DISPLAY-04, Scenario A |

---

### Low Priority

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-04 | Low | **Scenario A ring visibility at first render has no property.** Scenario A acceptance criterion states "all six rings are visible and their labels legible on a 5-inch 1080p screen." PROP-06-053 covers mode entry latency (300 ms). No property asserts that all six rings are rendered visible by default on first entry to Luopan Mode (as opposed to "eventually visible after user interaction"). This is a low risk because PROP-05-048 asserts ring visibility flags default to `true`, but the property tests `SettingsRepository` behaviour, not the initial UI render state. | REQ §12 Scenario A; REQ §5.3 |
| F-05 | Low | **OQ-P3-01 practitioner validation gate has no corresponding property.** REQ §5.6 and §14 explicitly state the 六十分金 label table is a placeholder pending feng shui consultant sign-off (OQ-P3-01), and that "it MUST NOT be shipped without practitioner sign-off." Risk P3-R3 repeats this gate for the full ring arrangement. There is no property in PG-01 (or elsewhere) that documents this as a pre-release gate condition — e.g., a property asserting that Ring 6 data is marked as provisional pending OQ-P3-01 resolution. Without a property, the release checklist has no formal traceability back to this open question. | REQ §5.6 OQ-P3-01 note; REQ §14 OQ-P3-01, Risk P3-R3 |
| F-06 | Low | **Scenario C-2 (Moderate confidence, readout at 90°) has no dedicated E2E property.** REQ Scenario C-2 specifies the exact readout string at 90° with Moderate confidence: `"卯 (Mǎo) · 卯 (Mǎo) · ☳ 震 東 · 90.0° · True N · N/A — calibrate for 分金 precision · Moderate"`. PROP-03-029 covers the abstract rule that MODERATE produces populated sector fields + null 分金, but no property pin-tests this specific string at 90° under Moderate confidence in the way PROP-03-033 and PROP-03-034 do for the High confidence cases at 180° and 90°. | REQ §12 Scenario C-2; AC-06 |
| F-07 | Low | **Description phrasing for PG-02 properties is implementation-centric.** Several properties in PG-02 (ZuoXiangLock Invariants) describe implementation internals (e.g., PROP-02-018: "`ZuoXiangLock` must NOT apply declination to the stored `xiangBearing` field"). From a product perspective, the observable behavior is: "when the user switches north reference while a 坐向 lock is active, the 山 labels on the overlay do not change." Phrasing properties in terms of internal field names rather than user-observable outcomes makes it harder to detect if the same bug manifests through a different code path. This is a style concern rather than a missing property — the observable behavior is tested, just described at the wrong level of abstraction. | REQ §8 North reference switch while locked; AC-23; FSPEC §4d |

---

## Recommendation

**Approved with Minor Issues**

The PROPERTIES document is substantially complete and traces correctly to REQ acceptance criteria. All three P0 user stories are represented. The coverage matrix and gap analysis are honest and accurate.

The three Medium findings (F-01, F-02, F-03) should be addressed before implementation begins:

- **F-01**: Add an E2E property for the full Scenario J-1 user journey (Settings navigation → north switch → Luopan Mode readout update). A unit property for the mapper alone is insufficient to catch View-layer failures.
- **F-02**: Add an E2E property asserting that the ring label under the pointer matches the sector predicted by the mapper at a canonical bearing (e.g., 180°). This closes the gap between correct mapper output and correct visual rendering.
- **F-03**: Add a property in PG-04 or PG-08 asserting that ring label font sizes meet the minimum specified in REQ §6 (8sp for Ring 6, 11sp for Ring 5, 12sp for Ring 4), or add a rendering snapshot test that confirms legibility at the minimum supported screen size.

The Low findings (F-04 through F-07) may be addressed at the TE author's discretion and do not block implementation.
