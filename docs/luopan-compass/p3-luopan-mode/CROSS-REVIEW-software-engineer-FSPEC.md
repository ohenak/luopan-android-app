# Cross-Review: software-engineer — FSPEC

**Reviewer:** software-engineer
**Document reviewed:** docs/luopan-compass/p3-luopan-mode/FSPEC-luopan-p3-luopan-mode.md
**Date:** 2026-04-25
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | High | **Navigation architecture not present in codebase.** The FSPEC assumes `NavHostFragment`, `NavController`, `TabLayout`, and a `LuopanFragment` as prerequisites, but the current codebase has none of these. `CompassActivity` renders a flat `ConstraintLayout` directly; there is no nav graph, no `TabLayout`, no fragment infrastructure. Phase 3 requires: (1) restructuring `activity_compass.xml` to host a `NavHostFragment` + bottom `TabLayout`, (2) creating `nav_graph.xml` with `dest_modern` and `dest_luopan` destinations, (3) migrating all existing `CompassActivity` UI logic into a new `ModernCompassFragment`. This is substantial architectural groundwork not called out anywhere in the FSPEC. | §1.3 Assumptions, Flow 1 step 1, REQ §9 |
| F-02 | High | **Flow 4d "current bearing" is semantically ambiguous when device has moved since lock.** Step 3 reads: "xiangBearing is updated to the current bearing in the new reference frame." The phrase "current bearing" could mean (A) the live device heading at the instant of the north-type switch, or (B) the stored `xiangBearing` converted to the new reference by ± declination. ES-03 and the accompanying example ("45° True N → approximately 40° Mag N") clearly intend interpretation (B), but the prose says "current bearing" — which reads as (A). If the practitioner has moved the device after locking, interpretation (A) would silently and incorrectly reassign 向 to a completely different direction. The implementation rule must state: **recalculate `xiangBearing = storedXiangBearing_trueN ± declinationDelta` (no live-heading read)** when the north reference changes. | Flow 4d steps 2–3, ES-03 |
| F-03 | Medium | **`LuopanState` data class is missing `bearingDeg` and north-reference fields.** The REQ §10 data class definition includes `mountainChar`, `earthlyBranchChar`, `trigramSymbol`, etc., but does NOT include the raw bearing in degrees or the north-type label — both of which appear as independent fields in the numerical readout panel (Flow 3 field table: "Bearing in degrees: `bearingDeg` from `CompassViewModel`", "North type: `northType` from `CompassViewModel`"). If `LuopanFragment` reads exclusively from `LuopanState`, it cannot populate those two readout fields without also observing `CompassUiState` or having these fields added to `LuopanState`. The intended data-flow contract is underspecified. | Flow 3 field table, REQ §10 data class |
| F-04 | Medium | **ES-07 (cold-start initialization) incorrectly prescribes "Calibrating..." badge.** ES-07 says the confidence badge shows "Calibrating..." until the first valid heading arrives. However, "Calibrating…" maps to `STABILIZING` — a state that requires `angular velocity > 180°/s for > 0.5 s` (ES-02). At cold start, the device is stationary; the confidence model emits `POOR` (the value in `CompassUiState.INITIAL`), not `STABILIZING`. The FSPEC must either (a) define a distinct `INITIALIZING` OverallConfidence value and map it to a "Calibrating…" badge, or (b) change ES-07 to show a "Poor" badge until the pipeline stabilizes. Adding a new enum value would require confirming the `OverallConfidence` enum (Phase 1) is extensible without breaking existing exhaustive `when` expressions in `CompassActivity`. | ES-07, ES-02, §1.3 (`OverallConfidence` enum note) |
| F-05 | Medium | **Gold tick mark's parent ring is unspecified, making ES-05 unimplementable.** Flow 4a step 6b says a gold tick mark is "drawn on the dial ring at the `xiangBearing` position." ES-05 says when all rings are hidden the tick mark "is not shown (the ring it would be drawn on is hidden)." This implies the tick mark belongs to one specific ring — but no ring number is named anywhere. If it is drawn as an overlay on the ring assembly independent of individual rings, it should always be visible unless the entire assembly is hidden. If it is tied to a specific ring (e.g., Ring 5 二十四山), the FSPEC must state that ring. Engineers cannot implement ES-05 correctly without this. | Flow 4a step 6b, ES-05 |
| F-06 | Medium | **Ring 6 `Parent 山` column has systematic disagreement with Ring 5 sector boundaries.** A mathematical check against the Ring 5 LUT shows that 57 out of 60 `Parent 山` entries do not correspond to the Ring 5 sector at the stated center bearing. For example, `庚子分金` (center 355°) is labeled parent 壬, but 355° falls in Ring 5's 子 sector `[352.5°, 7.5°)`. This is acknowledged as pending practitioner validation (OQ-P3-01), but the current mismatch pattern is systematic (nearly universal), not isolated. An engineer implementing a cross-ring validation test or any UI feature that correlates 分金 to its parent 山 will produce wrong results. The FSPEC should either mark the column "informational only — do not use for lookup" or provide a corrected table. | §4.6 Ring 6 label reference, BR-04, OQ-P3-01 |
| F-07 | Medium | **`SettingsRepository` is missing three keys required by BR-09.** The current `SettingsRepository` implementation (`com.luopan.compass.settings.SettingsRepository`) contains only four keys (`declination_mode`, `manual_declination_deg`, `display_format`, `wake_lock_enabled`). Phase 3 requires adding three persisted keys: `display_mode`, `luopan_show_romanization`, and `luopan_show_my_language`. Without these additions the FSPEC's persistence contract cannot be implemented. Engineering must extend `SettingsRepository` as part of Phase 3. | BR-09, Flow 1 step 8, Flow 7 |
| F-08 | Low | **Effect of "Show romanization" on the 坐向 overlay is not specified.** Flow 7 says the romanization toggle applies to "ring labels on the dial AND all character fields in the numerical readout panel." The 坐向 overlay (Flow 4a step 6a) displays `xiangMountain` and `zuoMountain` — both are character fields. Whether these show pinyin when the toggle is ON is not stated. This will surface as an implementation question for the rendering engineer. | Flow 4a §6a, Flow 7 Toggle A |
| F-09 | Low | **No English mapping for Ring 2 (先天八卦) in §4.7.** Section §4.7 provides English label tables for Rings 3, 4, and 5 only. Ring 2 is a rendered ring on the dial and will be visible when "Show in my language" is ON. The FSPEC does not specify whether Ring 2 continues to render in Traditional Chinese or uses English equivalents. | §4.7, Flow 7 Toggle B step 3 |
| F-10 | Low | **300 ms first-render budget is tight for API 26 with CJK font measurement on first `onSizeChanged`.** The FSPEC correctly notes that ring geometry must be pre-computed in `onSizeChanged`. However, the first `onSizeChanged` triggers `TextPaint.measureText()` for 60 CJK labels (Ring 6) plus 24 + 12 + 8 + 8 for the other rings. On API 26 mid-range hardware, initial font load + text measurement can take 50–100 ms. No mitigation strategy (e.g., pre-warming the fragment, async font loading, pre-computed glyph extents) is described. The FSPEC should acknowledge this risk and recommend a mitigation. | Flow 1 step 3, AC-01, REQ §11 REQ-NFR-LUOPAN-04 |
| F-11 | Low | **Canvas transform order for scale + rotation is underspecified.** Flow 2 step 8 states "scale is applied before rotation." In Android's Canvas drawing model, `canvas.scale(s,s,cx,cy)` before `canvas.rotate(angle,cx,cy)` does not produce the same result as the reverse order. The intended behavior (uniform dial expansion around dial center, then rotation) corresponds to applying scale first on the matrix stack — but this should be made explicit as pseudo-code or Canvas API sequence to prevent renderer bugs. | Flow 2 step 8 |
| F-12 | Low | **Gesture conflict between long-press and pinch-to-zoom is unspecified.** If a user begins a long-press (≥ 500 ms) and then adds a second finger before the `BottomSheetDialog` appears, the gesture detector receives conflicting events. Whether the pinch gesture cancels the long-press, the long-press fires and then pinch is active, or neither fires is undefined. On Android, `GestureDetector` and `ScaleGestureDetector` need explicit coordination (e.g., `onInterceptTouchEvent` strategy) for this case. | Flow 5 trigger, Flow 6 trigger |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Flow 4d: Is the intent that `xiangBearing` after a north-reference switch equals `storedXiangBearing ± declinationDelta` (preserving the physical direction), or does it equal the live device heading at the moment of the switch? The ES-03 example implies the former; the prose implies the latter. Please confirm and update the spec to remove ambiguity. |
| Q-02 | ES-07: Should the cold-start confidence state be `POOR` (current `CompassUiState.INITIAL`) or a new `INITIALIZING` state with "Calibrating…" label? If a new state is needed, does it require extending the Phase 1 `OverallConfidence` enum, and have all exhaustive `when` expressions been audited? |
| Q-03 | Flow 4a step 6b: Which specific ring number (1–6) does the gold tick mark belong to, for the purpose of ES-05 (hiding tick when ring is hidden)? |
| Q-04 | §4.6: Should the `Parent 山` column be treated as purely informational metadata (not used in any lookup), or is it expected to be used programmatically (e.g., for grouping or cross-validation)? If informational only, please add a note to the table; if programmatic, provide a corrected table. |
| Q-05 | Navigation architecture: Is the Phase 3 plan to migrate the existing `CompassActivity` + flat view hierarchy to a `NavHostFragment` + fragment-per-mode architecture, and is that migration work budgeted in the Phase 3 plan? |

---

## Positive Observations

- The six-ring data tables (§4.2–§4.5) are complete, exhaustively enumerated, and machine-readable. Implementing the LUTs from these tables is straightforward.
- Business Rules BR-01 through BR-04 precisely specify the inclusive-left, exclusive-right sector boundary convention and all wrap-around cases with worked examples and key-assertion tables. These are directly translatable to unit tests.
- The confidence state → UI state mapping (BR-05) is a single authoritative table covering all five states across five UI surfaces. No ambiguity for the renderer.
- `LuopanState` data class (REQ §10, referenced in §1.3) gives a concrete Kotlin type with explicit nullability for the lock fields — the ViewModel contract is clear.
- BR-09 unambiguously separates session-only from persisted settings with exact key names. The `luopan_ring_visible_*` and `luopan_zoom_scale` pattern prevents accidental persistence.
- Acceptance criteria AC-16 through AC-20 are tightly specified boundary-value tables; they are directly expressible as parameterized unit tests.
- The 坐向 lock derivation (BR-06) includes a `mod 360.0` worked-example table covering the wrapping case — no ambiguity for edge cases near 180° and 350°.

---

## Recommendation

**Need Attention**

Two High findings (F-01, F-02) and five Medium findings (F-03 through F-07) require resolution before the FSPEC can be handed to engineering for TSPEC authoring.

**Mandatory changes before TSPEC:**

1. **(F-01)** Add an explicit "Architectural prerequisites" section to the FSPEC enumerating the activity→fragment migration and nav graph setup as Phase 3 work. Reference the specific files that must be created or restructured (`activity_compass.xml`, `nav_graph.xml`, `ModernCompassFragment`, `LuopanFragment`).

2. **(F-02)** Replace "current bearing" in Flow 4d step 3 with: "Convert the stored `xiangBearing` to the new north reference by applying the declination delta: `newXiangBearing = storedXiangBearing_trueN − declinationDeg` (for True→Mag) or `+ declinationDeg` (for Mag→True). Do NOT read the live device heading."

3. **(F-03)** Either add `bearingDeg: Double` and `northLabel: String` to `LuopanState`, or explicitly specify that `LuopanFragment` reads these two fields from `CompassUiState` (via a second `uiState` observation) and document the dual-flow pattern.

4. **(F-04)** Either change ES-07 to show "Poor" badge (matching `POOR` initial state), or define a formal `INITIALIZING` sub-state and specify which `OverallConfidence` value and badge text it maps to.

5. **(F-05)** Specify the ring number the gold tick mark is logically part of (or state it is drawn as an independent overlay on the ring assembly and is always visible regardless of individual ring visibility).

The Medium findings F-06 and F-07 are implementation prerequisites that engineering will block on early in Phase 3, but do not require spec revision to unblock TSPEC authoring.
