# Cross-Review: Software Engineer — REQ-luopan-p3-luopan-mode (v3)

| Field | Value |
|-------|-------|
| Reviewer | Software Engineer |
| Document | REQ-luopan-p3-luopan-mode.md v0.3-draft |
| Date | 2026-04-25 |
| Iteration | 3 |
| Recommendation | Approved with Minor Issues |

---

## Prior Findings Resolution

All findings from the v2 SE cross-review are confirmed resolved.

| v2 ID | Severity | v2 Finding Summary | Resolution Status | Notes |
|-------|----------|--------------------|-------------------|-------|
| NF-01 | Medium | `LuopanState.confidence` used non-existent type `Confidence`; `STABILIZING` and `SENSOR_ERROR` states had no defined behavior | **Resolved** | §10 now declares `val confidence: OverallConfidence` with explicit reference to `com.luopan.compass.model.OverallConfidence`. A normative table defines button state and badge text for all five enum values including `STABILIZING` ("Disabled" / "Calibrating...") and `SENSOR_ERROR` ("Disabled" / "Sensor error"). The type name `Confidence` is explicitly forbidden. |
| NF-02 | Medium | §5.5 canonical readout at 180° showed `丙子分金` (wrong, sector 10°–16°) instead of `壬午分金` (sector 178°–184°) | **Resolved** | §5.5 canonical examples now correctly show `壬午分金` at 180° High and `N/A — calibrate for 分金 precision` at 180° Moderate. Scenario B and Scenario C key test bearings are consistent with the §5.6 table. Cross-checked: 180° falls in sector #32 (178°–184°) = `壬午分金` ✓; 90° falls in sector #17 (88°–94°) = `壬卯分金` ✓. |
| NF-03 | Low | Session-only key names in §5.7 could mislead engineering into writing them to `SettingsRepository` | **Resolved** | §5.7 now opens with a bold normative warning: "MUST be held in `CompassViewModel` in-memory state and MUST NOT be written to `SettingsRepository`." Engineering MUST NOT add session-only keys to SharedPreferences is stated as an explicit constraint. |
| NF-05 | Low | Sector-boundary [start, end) convention not stated; "inclusive" comment on 15.0° was ambiguous | **Resolved** | §5.3b is a new normative section defining the universal rule: all sectors use inclusive-left, exclusive-right `[start°, end°)`. The 子 wrap-around exception is called out explicitly with a four-row example table. Scenario H is updated to use `[start°, end°)` notation uniformly. |
| NF-07 | Low | "Show in my language" toggle scope ambiguity for the numerical readout panel | **Resolved** | REQ-L10N-02 (§5.4) now explicitly states: "Both toggles apply uniformly to ring labels on the dial AND to all character fields in the numerical readout panel." The `luopan_show_my_language` key description in §5.7 cross-references this. |

---

## New Findings

### Low Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| NF-08 | Low | **§5.3b's list of wrap-around sectors omits Ring 6.** §5.3b states "Rings 3 and 5 have analogous wrap-around sectors" and gives examples for Rings 3, 4, and 5. However, Ring 6 (六十分金) also has a wrap-around sector: entry #2 `壬子分金` spans `[358°, 4°)` modularly (center 1°, range 358°–4°). Engineering implementing the sector-lookup utility must handle this wrap-around. The §5.6 table and key test bearing ("0° → 壬子分金") imply the correct answer, but §5.3b should state explicitly that Ring 6's `壬子分金` sector is `[358°, 360°) ∪ [0°, 4°)` as an additional wrap-around case. Without this call-out, a naive implementation that binary-searches the sorted start-bearing array will produce the wrong result at bearings in `[0°, 4°)` for Ring 6. | §5.3b; §5.6 entry #2; §5.6 key test bearings |
| NF-09 | Low | **`LuopanState.xiangBearing` semantics are unspecified — True North or display-north bearing?** Scenario J-2 requires re-deriving 山 labels when the north reference switches (e.g., True N 45° → Mag N). To implement this, the ViewModel must know the canonical form of the stored bearing. If `xiangBearing` stores the True North value, the ViewModel subtracts declination to get the magnetic display value. If it stores the display-north value at lock time, a north-type switch produces incorrect re-derivation. §10 declares `xiangBearing: Double?` with only the comment "locked 向 bearing in degrees", leaving the canonical form ambiguous. This is sufficient for Phase 3 scenarios that don't exercise a north-type switch mid-lock, but Scenario J-2 requires an unambiguous contract. Addressable in TSPEC. | §10 `LuopanState.xiangBearing`; §12 Scenario J-2 |
| NF-10 | Low | **Scenario J-2 does not specify the numeric bearing value shown in the 坐向 overlay after a north-type switch.** J-1 specifies that "the bearing in degrees in the numerical readout updates immediately to reflect the magnetic bearing." J-2 specifies that the overlay north-type label changes to "Mag N" and that 山 labels are recomputed if a sector boundary is crossed. However, J-2 does not state whether the numeric bearing value in the overlay (e.g., "向: 艮 (45.0° True N)") changes its numeric component when the north type switches. If the stored `xiangBearing` is True N and the user switches to Mag N, the overlay should arguably show the magnetic-equivalent numeric value (e.g., "向: 艮 (43.0° Mag N)"). Without an explicit statement, engineering will make an arbitrary choice — which may diverge from J-1's behavior for the main readout. Addressable in TSPEC or FSPEC. | §12 Scenario J-1, J-2; §10 `LuopanState.xiangBearing` |

---

## Positive Observations

- **NF-01 and NF-02 fully resolved.** The `OverallConfidence` table in §10 is exactly the right artifact — it closes the behavior gap for `STABILIZING` and `SENSOR_ERROR` without requiring a TSPEC amendment. The §5.5 correction is verified correct against the §5.6 look-up table.
- **§5.3a (新增) is well-structured.** The 先天八卦 ring reference table provides center bearing, sector range, trigram symbol, 卦名, 方位, and pinyin in a single table, keyed by sector index 0–7. The explicit note that Ring 2 does NOT contribute to the numerical readout removes a likely source of implementation confusion.
- **§5.3b (新增) resolves the longstanding boundary-rule ambiguity.** The normative `[start°, end°)` rule with the 子 wrap-around worked example directly answers how to implement the sector-lookup utility. Scenario H is now fully consistent with this rule.
- **Scenario J (新增) correctly identifies a live-update requirement** for north-type changes and gives a concrete cross-山-boundary example. The re-derivation formula `(newBearing + 180°) mod 360°` is consistent with §8's 坐 derivation formula.
- **NF-03 resolution is better than a clarification.** The explicit "MUST NOT" constraint on session-only keys in §5.7 is an actionable engineering constraint, not just a note. This eliminates the risk of inadvertent SharedPreferences pollution.
- **All 60 Ring 6 entries cover exactly 360° without boundary gaps** (verified: 60 × 6° = 360°; consecutive entry boundaries are contiguous; entry #60 ends at 352° = start of entry #1). The three key test bearings (0°, 90°, 180°) are verifiable against the table.

---

## Recommendation

**Approved with Minor Issues**

The two required pre-TSPEC fixes from v2 (NF-01 and NF-02) are fully resolved. The three new findings (NF-08 through NF-10) are all Low severity and addressable at the TSPEC or FSPEC stage without blocking TSPEC authoring.

**Addressable in TSPEC (before implementation):**
1. **NF-08** — Add Ring 6 (`壬子分金`, `[358°, 360°) ∪ [0°, 4°)`) to the §5.3b wrap-around exception list, or add a sentence: "The sector-lookup utility MUST handle the Ring 6 wrap-around sector (#2, 壬子分金) identically to the Ring 4 and Ring 5 wrap-around sectors."
2. **NF-09** — Specify in the TSPEC that `xiangBearing` stores the **True North bearing** at lock time (regardless of which north type was active when the user locked). The ViewModel adjusts the display value by current declination when `northType == MAGNETIC`.
3. **NF-10** — Specify in the TSPEC or FSPEC that the overlay numeric bearing value changes to match the current north type's equivalent (consistent with J-1's main-readout behavior), so the overlay always shows `(adjustedBearing° [northTypeLabel])`.
