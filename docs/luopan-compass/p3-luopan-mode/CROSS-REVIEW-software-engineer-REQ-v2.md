# Cross-Review: Software Engineer — REQ-luopan-p3-luopan-mode (v2)

| Field | Value |
|-------|-------|
| Reviewer | Software Engineer |
| Document | REQ-luopan-p3-luopan-mode.md v0.2-draft |
| Date | 2026-04-25 |
| Iteration | 2 |
| Recommendation | Approved with Minor Issues |

---

## Prior Findings Resolution

The following table covers every High and Medium finding from the v1 SE cross-review.

| v1 ID | Severity | v1 Finding Summary | Resolution Status | Notes |
|-------|----------|--------------------|-------------------|-------|
| F-01 | High | No mode-switcher architecture defined | **Resolved** | §9 (new) specifies single-Activity + NavHostFragment + TabLayout + `NavigationUI.setupWithNavController`. Fragment class names, nav destination IDs, and ViewModel sharing via `activityViewModels()` are all named. |
| F-02 | High | `CompassUiState` has no luopan-specific fields | **Resolved** | §10 (new) defines `LuopanState` data class with all required fields (rings 3–6 lookups, 坐向 lock state, confidence). Mapping responsibility is assigned to ViewModel. |
| F-03 | High | 坐向 lock confidence threshold contradicts master REQ | **Resolved** | §2 capability table, §5.1 REQ-DISPLAY-06, §8, and the explicit single-testable-statement in §8 all now consistently state "enabled at High or Moderate; disabled at Poor only." Scenario E adds four sub-cases including E-2 (Moderate) and E-3 (Poor). |
| F-04 | High | `SettingsRepository` has no keys for localization toggles | **Resolved** | §5.7 (new) defines exact key names (`luopan_show_romanization`, `luopan_show_my_language`), types, defaults, and persistence scope. `SettingsRepository` does not yet have these keys in the implementation, but the contract is now fully specified for engineering to implement. |
| F-05 | High | Ring-visibility persistence is deferred to engineering | **Resolved** | §5.7.1 (new) makes an explicit PM decision: session-only, reset to all-visible on cold start, with rationale. Key names provided for completeness even though they are ViewModel-memory (not SharedPreferences). |
| F-06 | High | No architecture spec for `LuopanView` Canvas rendering thread model | **Resolved** | §11 REQ-NFR-LUOPAN-01/02 (new) specify ≥20 fps, ≤16 ms `onDraw` budget via `FrameMetrics`, mandate ring geometry pre-computation in `onSizeChanged`, and permit engineering to choose View/Canvas or SurfaceView provided they meet the budget. Hardware-accelerated Canvas is required. |
| F-07 | Medium | Pinch-to-zoom scale factor not threadsafe and no persistence contract | **Resolved** | §5.7.2 (new) explicitly states session-only, MUST survive configuration changes via `CompassViewModel` or `onSaveInstanceState`. Key name (`luopan_zoom_scale`) provided. Thread-safety is an implementation detail now correctly deferred to engineering with the constraint stated. |
| F-08 | Medium | Long-press ring visibility menu UX completely unspecified | **Resolved** | §7.1 (new) specifies `BottomSheetDialog`, 500 ms long-press trigger, per-ring `Switch` rows with bilingual labels, dismiss gestures, and an overflow-menu accessibility alternative for TalkBack. |
| F-09 | Medium | Mode-switcher transition error and loading states not specified | **Resolved** | §9.3 (new) states the 300 ms budget, specifies a dark background (#2C0E0E) as the pending-render placeholder (no spinner needed), and confirms sensor data continues uninterrupted via shared ViewModel. |
| F-10 | Medium | 分金 label content never defined | **Resolved** | §5.6 (new) provides the full 60-entry look-up table with center bearings and ranges. Key test bearings (0°, 90°, 180°) are called out. A validation caveat correctly flags these as pending feng shui consultant sign-off. |
| F-11 | Medium | NFR coverage for Luopan Mode absent | **Resolved** | §11 (new) adds REQ-NFR-LUOPAN-01 through 04: frame rate, `onDraw` budget, battery allowance (≤7%/hr), and first-render latency. |
| F-12 | Medium | Gold tick mark coordinate-space math not specified | **Partially Resolved** | §8 workflow step 6 retains the same wording. §10 `LuopanState.xiangBearing` provides the locked bearing as a ViewModel field, which is the correct data contract for correct coordinate-space rendering. However, the REQ still does not acknowledge that the tick mark must be drawn in the dial's rotated coordinate space (not as a fixed screen overlay). This is implementation-level guidance that belongs in the TSPEC, not the REQ, so this is acceptable at the REQ stage. Downgraded to Low for v2. |

---

## New Findings

### Medium Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| NF-01 | Medium | **`LuopanState.confidence` uses an unresolved type alias `Confidence` instead of `OverallConfidence`.** The Kotlin data class in §10 declares `val confidence: Confidence` with the comment `// HIGH \| MODERATE \| POOR`. However, the existing enum in the codebase is `OverallConfidence` (values: `HIGH`, `MODERATE`, `POOR`, `STABILIZING`, `SENSOR_ERROR`). The type `Confidence` does not exist in the codebase. Engineering will need to resolve this: either use `OverallConfidence` directly, introduce a type alias, or define a separate sealed class. More critically, the spec comment lists only three values (HIGH, MODERATE, POOR) but `OverallConfidence` has five. `LuopanState` and the `坐向 lock` button logic must define behavior for `STABILIZING` and `SENSOR_ERROR` — neither is addressed. Should STABILIZING be treated as POOR for the lock button? Should the confidence badge show "Stabilizing" in the Luopan readout panel? | §10 `LuopanState`; `OverallConfidence.kt` |
| NF-02 | Medium | **Internal contradiction: 分金 label for 180° differs between §5.5 and §5.6.** The canonical readout example in §5.5 at 180° High confidence shows `丙子分金`. The §5.6 table entry #32 shows `壬午分金` at center bearing 181° (range 178°–184°), and §5.6's own key test bearing explicitly states "180° → 壬午分金". The correct value per the table is `壬午分金` (sector 178°–184° maps to 180°). The §5.5 example showing `丙子分金` (sector 10°–16°, center 13°) is wrong by ~167°. This contradiction will produce a test written from §5.5 that asserts the wrong label. | §5.5 canonical readout example; §5.6 table entry #32; §5.6 key test bearings |

### Low Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| NF-03 | Low | **`SettingsRepository` key names for session-only ring-visibility and zoom state are defined but misleading.** §5.7 provides key names (`luopan_ring_visible_1` through `luopan_ring_visible_6`, `luopan_zoom_scale`) and marks them as "Session-only (not persisted)". However, `SettingsRepository` is currently implemented as `SharedPreferences`, which persists all writes. If engineering naively adds these keys to `SettingsRepository` they will be persisted, contradicting the session-only decision. The spec should clarify that these keys are NOT to be written to `SettingsRepository` — they live only in `CompassViewModel` in-memory state. Providing key names for non-persisted state is likely to mislead. | §5.7; `SettingsRepository.kt` |
| NF-04 | Low | **§9.3 transition loading state does not address a slow first-render on low-end hardware.** The spec states no spinner is required given the 300 ms budget, and REQ-NFR-LUOPAN-04 targets ≤300 ms first-render latency. On minimum-spec API-26 hardware, a `LuopanView` with six ring geometries, CJK font rasterization for 104 labels (8+8+12+24+60 = 112 total label strings), and `Path` pre-computation in `onSizeChanged` may exceed 300 ms on the very first inflation. There is no fallback state if this budget is missed on minimum-spec hardware. The NFR and the loading-state spec are aligned in intent, but engineering should be aware there is no graceful degradation path specified if NFR-LUOPAN-04 cannot be met on API-26 mid-range hardware. Consider adding: "If first render exceeds 300 ms on the target device, engineering may show a progress indicator rather than leaving a blank dark screen." | §9.3; §11 REQ-NFR-LUOPAN-04 |
| NF-05 | Low | **Scenario H boundary table for Ring 4 has an ambiguous boundary at 15.0°.** The table shows: bearing 15.0° → 子 (sector 345°–15°, end boundary — inclusive). This means the sector includes 15.0° as its right endpoint. Immediately below, bearing 15.1° → 丑. Engineering needs a precise rule: is the sector defined as [start, end) or (start, end]? The note says "inclusive" for the right end of 子, making it [345°, 15°]. But this convention must hold consistently for all sectors — e.g., is 345.0° the first bearing in 子 or the last in 亥? The table shows 344.9° → 亥 and 345.0° → 子, so left endpoint is inclusive (closed-left). This would mean all sectors are [start, end). The "inclusive" comment for 15.0° is confusing because it implies the right end is also closed, which contradicts a closed-left convention. Engineering needs a single clear rule: **all sectors use closed-left, open-right [start, end) intervals, with 子 treated as [345°, 375°) in modular space**. | §12 Scenario H, Ring 4 boundary table |
| NF-06 | Low | **Scenario B asserts Ring 5 shows "卯" at 90° but does not match the acceptance test for Ring 5 sector boundaries.** Scenario C (§12) states: "Ring 5 shows '卯' under the pointer" at 90°. From the §5.6 key test bearings and the 二十四山 sector system (15° per sector, centered at each 山), 卯 is an Earthly Branch appearing at both Ring 4 (30° sector, center 90°) and Ring 5 (15° sector, center 90°). The coincidence is correct for 90° exactly, but no entry in Scenario H covers Ring 5 at 90°. If the test author derives the expected Ring 5 label from Scenario C rather than the data table, they may miss the correct sector-center. This is a minor consistency gap — not a contradiction. | §12 Scenario C; §12 Scenario H |
| NF-07 | Low | **"Show in my language" toggle scope ambiguity remains for the numerical readout panel.** §5.8 specifies English label mappings for ring labels (visual elements on the dial). v1 question Q-06 asked whether the toggle also affects the numerical readout panel fields (e.g., whether "南" in the readout shows as "South"). §5.8 does not address this. The readout panel is specified separately from ring labels, and a practitioner might expect consistent behavior between the dial and the readout panel. Engineering will make an arbitrary choice without guidance. | §5.4 REQ-L10N-02; §5.8; §5.5 canonical readout |

---

## Positive Observations

- The six High findings from v1 are all fully resolved. The new sections (§5.5–§5.8, §7.1, §8, §9, §10, §11, §12) are well-structured and comprehensive. This is a materially improved document.
- The §5.7 persistence decisions with explicit PM rationale are exactly the right level of specificity. Engineering no longer has to guess.
- The §10 `LuopanState` Kotlin snippet establishes the ViewModel-View contract at the REQ stage, which is unusual and helpful — it closes the gap between product intent and implementation skeleton without invading the TSPEC.
- §5.6's 60-row 分金 table with parent 山, center bearing, and range is directly usable as a compile-time array. The key test bearings for unit tests are a practical addition.
- §5.8's English ring label mapping table is complete and correctly marks entries with "—" for those without standard English equivalents.
- §12's Scenario H sector boundary tables are the right mitigation for v1 finding F-04 (TE review). The explicit 子/亥 wrap-around at 345°/15° with four boundary rows is the correct level of test specification.
- §9.2's `activityViewModels()` sharing decision eliminates any risk of dual sensor pipelines during mode transitions.
- The `(向 + 180°) mod 360°` formula is now explicit in §5.1, §8, and Scenarios E-4/E-5, with wrap-around test cases at 270° and 350°. This closes the modular arithmetic gap from v1 TE review F-09.

---

## Recommendation

**Approved with Minor Issues**

NF-01 (undefined type `Confidence` and missing `STABILIZING`/`SENSOR_ERROR` handling) and NF-02 (wrong 分金 label in §5.5 canonical example) should be corrected before the TSPEC is authored — both will directly produce incorrect code or tests if left as-is. The remaining findings (NF-03 through NF-07) are clarifications that can be addressed in the TSPEC or FSPEC without blocking TSPEC authoring.

**Required before TSPEC:**
1. **NF-01** — Replace `Confidence` with `OverallConfidence` in `LuopanState` (§10), and define explicit behavior for `STABILIZING` and `SENSOR_ERROR` states in the 坐向 lock button and the confidence badge in the numerical readout.
2. **NF-02** — Correct the §5.5 canonical readout example: at 180°, the 分金 label is `壬午分金`, not `丙子分金`.

**Addressable in TSPEC/FSPEC:**
3. **NF-03** — Clarify in §5.7 that session-only keys must NOT be written to `SettingsRepository`; they live in ViewModel memory only.
4. **NF-05** — State the sector-boundary convention explicitly (closed-left, open-right) in the REQ or TSPEC.
5. **NF-07** — Decide whether "Show in my language" applies to the numerical readout panel fields.
