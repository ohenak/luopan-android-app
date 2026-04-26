# Cross-Review: Test Engineer — REQ-luopan-p3-luopan-mode (v3)

| Field | Value |
|-------|-------|
| Reviewer | Test Engineer |
| Document | REQ-luopan-p3-luopan-mode.md v0.3-draft |
| Date | 2026-04-25 |
| Iteration | 3 |
| Recommendation | Approved with Minor Issues |

---

## Prior Findings Resolution

All five High and Medium findings from iteration 2 (NF-01 through NF-05) are evaluated below.

| Prior ID | Severity | Status | Notes |
|----------|----------|--------|-------|
| NF-01 | High | **Resolved** | §5.5 worked example at 180°, High confidence now correctly reads "壬午分金". Matches §5.6 table row 32 (178°–184°) and Scenario B. The internal data contradiction is gone. |
| NF-02 | Medium | **Resolved** | Scenario C is split into Case C-1 (High confidence → "壬卯分金") and Case C-2 (Moderate confidence → "N/A — calibrate for 分金 precision"). Both are concrete assertions that can be automated. |
| NF-03 | Medium | **Resolved** | §5.3a adds the full 8-row 先天八卦 sector-to-label table inline, with sector ranges, center bearings, pinyin, and the normative note that Ring 2 does not contribute to the numerical readout. Sufficient for unit-test authorship without reading any external document. |
| NF-04 | Medium | **Resolved** | §5.5 now specifies "True N" and "Mag N" as the two acceptable display strings for the `[north type]` field. Scenario J adds two cases (J-1: live update on north-reference switch; J-2: lock re-derivation after switch) covering the full observable behaviour including 坐向 overlay update. |
| NF-05 | Medium | **Resolved** | §5.3b defines the normative sector boundary rule ("inclusive-left, exclusive-right `[start°, end°)`") uniformly for all rings, with the 子 wrap-around exception stated explicitly. Scenario H now includes a boundary-rule preamble and the example table in §5.3b provides four worked rows that cross-check the rule. All Scenario H rows are consistent with §5.3b. |

Carry-forward Low findings from iteration 2:

| Prior ID | Severity | Status | Notes |
|----------|----------|--------|-------|
| NF-06 | Low | **Unresolved** | §5.7.2 still requires zoom to survive configuration changes, but no scenario tests screen-rotation survival. Still a Low gap; acceptable to defer to TSPEC. |
| NF-07 | Low | **Partially resolved** | Scenario F now includes "南 Nán" as a Ring 3 example, implying pinyin applies to direction characters. §5.3a adds a pinyin column for Ring 2, but §5.8 still has no pinyin column for Ring 3 direction names. Compound directions (東南 → Dōngnán, 西北 → Xīběi, etc.) remain unspecified as a table. Low severity; acceptable to defer to TSPEC or localization spec. |
| NF-08 | Low | **Unresolved** | REQ-NFR-LUOPAN-03 battery NFR still lacks test device spec, test duration, and background conditions. Still Low; defer to TSPEC. |
| NF-09 | Low | **Unresolved** | No scenario tests cold-start clearing of 坐向 lock. §8 states the rule ("cleared on cold start") but the assertion has no acceptance test row. Still Low; acceptable to defer to TSPEC. |

---

## New Findings

### High Priority

None.

### Medium Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| NF-10 | Medium | **Scenario J-2 does not specify the locked 向 bearing value after north-reference switch — making the re-derivation assertion untestable as written.** Case J-2 states "the locked 向 bearing re-derives its 山 label from the magnetic-adjusted heading" and "if the magnetic-adjusted bearing crosses a 山 boundary … the overlay MUST update". However, the scenario provides no concrete expected values: it neither specifies the local magnetic declination used in the test, nor pins a specific before/after bearing pair that crosses a 山 boundary. A test engineer cannot write a deterministic assertion from "e.g., falls into 甲 sector instead of 艮" — the "e.g." makes it conditional on real-world declination, which is not available in a unit or integration test. The scenario needs either: (a) a concrete fixed-declination test value (e.g., "assume declination = +5°; True N 45° → Mag N 40°; 40° falls in 艮 `[37.5°, 52.5°)` → 山 label unchanged") or (b) an explicit statement that J-2 is a manual exploratory test and unit coverage of lock re-derivation is tested by injecting a mock bearing directly into `CompassViewModel`. | §12 Scenario J-2 |

### Low Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| NF-11 | Low | **§5.3a Ring 2 sector ranges do not align with the §5.3b `[start°, end°)` rule at the 0°/360° boundary.** The §5.3b normative rule is stated for all rings. Ring 2 sector 5 (☴ 巽 北) is given as spanning 337.5°–22.5°, which wraps through 0°. The table in §5.3a lists this as "337.5°–22.5°" without the explicit modular-union notation used in §5.3b for the Ring 4 子 wrap-around ("`[345°, 360°) ∪ [0°, 15°)`"). A test engineer implementing the Ring 2 LUT may not recognize this as a wrap-around case unless the analogous union notation is added to §5.3a. Low severity because §5.3b's text mentions "Ring 3's ☵ 坎 北 spans `[337.5°, 22.5°)` modularly", implying the precedent exists, but §5.3a itself is silent. | §5.3a (sector 5 row), §5.3b |
| NF-12 | Low | **Scenario J-1 mentions the bearing "may become a different value in Mag N, depending on local declination" but provides no concrete assertion for the ring-label update.** The parenthetical "(e.g., 182° True N may become a different value in Mag N, depending on local declination)" correctly documents real-world behaviour but means the ring-label update assertion ("All ring positions … update to reflect the new bearing") cannot be verified automatically without injecting a deterministic declination value. Similar to NF-10, this makes J-1 a manual test in practice. Acceptable if marked as such; currently it is not. | §12 Scenario J-1 |
| NF-13 | Low | **The `LuopanState.fenJinLabel` field is documented as `null` when `confidence != HIGH`, but the `STABILIZING` and `SENSOR_ERROR` display strings in §10 show "N/A — calibrate for 分金 precision" — not `null`.** The Kotlin data class comment reads `// null when confidence != HIGH`, which an engineer could implement as returning `null` for STABILIZING/SENSOR_ERROR states, while the §10 behavior table mandates a non-null display string for those states. The View rendering logic is unspecified: does `null` map to the "N/A" string in the View, or is the `null`/non-null distinction a contract the ViewModel must enforce? Adding one sentence clarifying that `null` maps to the "N/A — calibrate for 分金 precision" display string in the View, and that `fenJinLabel` is `null` only when the View should determine the substitution text, would close this gap. | §10 `LuopanState` data class comment vs. §10 behavior table |

---

## Positive Observations

- §5.3a is an exemplary inline reference table: sector index, trigram symbol, 卦名, 方位, pinyin, center bearing, and sector range all in one row. Combined with the §5.3b normative boundary rule, a unit test for the Ring 2 LUT can be written without reading any other document.
- §5.3b's normative sector boundary rule is precisely what was requested — one universal rule with explicit wrap-around treatment. The example table in §5.3b provides four concrete boundary rows that function as ready-made parameterised test cases.
- Scenario J (north reference switch) covers both the live-update case (J-1) and the lock re-derivation case (J-2). The overall intent is clear; the only gap is the absence of a deterministic test fixture for the magnetic-adjusted bearing (NF-10).
- The §10 behavior table for `STABILIZING` and `SENSOR_ERROR` is an excellent addition — five confidence states with explicit button state, badge text, and 分金 field value per row. This directly drives the parameterised unit test matrix for `LuopanFragment`/`CompassViewModel`.
- The `OverallConfidence` type clarification ("MUST use `com.luopan.compass.model.OverallConfidence` — the existing enum from Phase 1") eliminates a class of compile-time ambiguity that would otherwise surface only during TSPEC authoring.
- Scenario C is now fully automatable: both the High-confidence and Moderate-confidence cases supply concrete expected strings for every readout field.

---

## Recommendation

**Approved with Minor Issues**

All High and Medium findings from iterations 1 and 2 are resolved. One new Medium finding (NF-10) is present — Scenario J-2 lacks a deterministic bearing fixture, making lock re-derivation after north-reference switch unverifiable by automated test. This must be addressed before the TSPEC author can write a unit or integration test for that behaviour.

Required action before FSPEC/TSPEC authorship:

1. **NF-10 (Medium)** — Scenario J-2: add a concrete test fixture. Either (a) specify a fixed declination value (e.g., +5°) and the resulting pre/post 山 labels so the scenario is fully deterministic, or (b) explicitly mark J-2 as "manual exploratory test — unit coverage via direct bearing injection into `CompassViewModel`" and add a unit-level scenario that injects a synthetic bearing crossing a 山 boundary without declination dependency.

The three new Low findings (NF-11, NF-12, NF-13) and four carry-forward Low findings (NF-06, NF-07, NF-08, NF-09) may be resolved in the TSPEC or flagged as manual test obligations there. They do not block FSPEC authorship.

The document is otherwise ready to proceed to FSPEC: all data tables are complete, all ring boundary rules are stated normatively, all confidence states have explicit behaviour, and all scenarios (A–I, J-1) are automatable.
