# Cross-Review: Test Engineer — REQ-luopan-p3-luopan-mode (v2)

| Field | Value |
|-------|-------|
| Reviewer | Test Engineer |
| Document | REQ-luopan-p3-luopan-mode.md v0.2-draft |
| Date | 2026-04-25 |
| Iteration | 2 |
| Recommendation | Need Attention |

---

## Prior Findings Resolution

The following table summarises the resolution status of every High and Medium finding from the iteration-1 review.

| Prior ID | Severity | Status | Notes |
|----------|----------|--------|-------|
| F-01 | High | **Resolved** | §8 now states "enabled at High or Moderate; disabled at Poor only" with a single testable statement. Scenario E Case E-2 adds the explicit Moderate acceptance criterion. |
| F-02 | High | **Resolved** | §5.6 inlines the full 60-row 六十分金 table. §5.8 provides Ring 4 and Ring 5 label tables; Ring 2 (先天八卦) remains cross-referenced to master REQ only (see new finding NF-03). |
| F-03 | High | **Resolved** | §5.5 defines the canonical field order with a template string and two worked examples. Scenario B now uses the canonical order. One data error in §5.5 example is flagged as new finding NF-01. |
| F-04 | High | **Resolved** | Scenario H adds explicit boundary rows for Rings 3, 4, and 5 including the 子/亥 wrap at 345°/15° boundaries. Inclusive/exclusive semantics are stated per row. |
| F-05 | Medium | **Partially Resolved** | §5.7.2 defines zoom as session-only and states it must survive configuration changes via ViewModel or `onSaveInstanceState`. No acceptance criterion tests (a) clamping below 0.8× or above 2.0×, (b) zoom survival across screen-rotation configuration change. See NF-06. |
| F-06 | Medium | **Resolved** | §5.7.1 makes a definitive PM decision: ring visibility is session-only. Scenario G tests cold-start reset. |
| F-07 | Medium | **Resolved** | §5.6 provides key test bearings: 180°→壬午分金, 0°→壬子分金, 90°→壬卯分金. |
| F-08 | Medium | **Unresolved** | §6 retains the human-perception legibility criterion ("readable by a user with 20/40 corrected vision at 30 cm"). Font size minimums in sp are specified but no proxy metric (rendered height in dp, contrast ratio, screenshot assertion threshold) suitable for automated or scripted tests has been added. Manual exploratory test designation is also absent. |
| F-09 | Medium | **Resolved** | Cases E-4 (向 = 270°) and E-5 (向 = 350°) explicitly verify modular reduction in 坐 derivation. |
| F-10 | Medium | **Resolved** | §8 specifies lock preservation across mode switches; Scenario I provides a two-step acceptance criterion (switch to Modern then back to Luopan). |
| F-11 | Medium | **Resolved** | Scenario D precondition updated to "all conditions Good per master REQ §8.1". |
| F-12 | Medium | **Resolved** | §5.8 provides complete English mapping tables for Rings 3, 4, and 5, with explicit "—" handling for entries with no equivalent. |

Low-priority findings (F-13 through F-18) status:

| Prior ID | Severity | Status | Notes |
|----------|----------|--------|-------|
| F-13 | Low | **Resolved** | Scenario F now includes a `ja` locale case. |
| F-14 | Low | **Unresolved** | Scenario A legibility criterion is still untestable by automation; no manual test designation added. |
| F-15 | Low | **Resolved** | "1:1 mapping ±2°" added to Scenario A. |
| F-16 | Low | **Unresolved** | Phase REQ still marks REQ-DISPLAY-06 as P0 (elevated) while master REQ §6.5 still says P1. No note or alignment text added. |
| F-17 | Low | **Partially Resolved** | §7.1 adds a TalkBack-accessible overflow menu for ring visibility. The "Cannot lock — heading is unreliable" tooltip localization (zh-Hant text) and TalkBack reachability for the "Lock 向" button tooltip remain unspecified. |
| F-18 | Low | **Partially Resolved** | §5.8 tables provide pinyin for all 12 地支 (Ring 4) and all 24 山 (Ring 5) entries. Pinyin for Ring 3 direction names (南/北/東/西/東南/西北/東北/西南) used by the "Show romanization" toggle is not explicitly specified. See NF-07. |

---

## New Findings

### High Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| NF-01 | High | **Internal data contradiction: §5.5 canonical example shows wrong 分金 label at 180°.** The §5.5 worked example at 180°, High confidence reads `… · 丙子分金 · High`. However, the §5.6 table (row 32) places 壬午分金 in the 178°–184° range, and the §5.6 key test bearings explicitly state `180° → 壬午分金`. Scenario B also correctly uses `壬午分金`. A test author following the §5.5 example verbatim will assert "丙子分金" and fail against a correct implementation. One of the two must be corrected; per the table and Scenario B, `壬午分金` is correct for 180°. | §5.5 canonical example, §5.6 table row 32, §12 Scenario B |

### Medium Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| NF-02 | Medium | **Scenario C 分金 field uses a placeholder, not a testable value.** Scenario C (device at 90°) ends with `[分金 per confidence] · [confidence]` instead of a concrete assertion. The §5.6 key test bearings already supply the answer: 90° → 壬卯分金. At High confidence the panel should show "壬卯分金"; at Moderate it should show "N/A — calibrate for 分金 precision". The placeholder means no automated test can be written from Scenario C alone for the 分金 field. | §12 Scenario C, §5.6 key test bearings |
| NF-03 | Medium | **Ring 2 (先天八卦) label table remains absent from this document.** §5.8 provides complete tables for Rings 3, 4, and 5. Ring 2 (先天八卦, 8 × 45° Fuxi arrangement) is referenced only by pointer to master REQ §10.2a — a cross-document link that is not guaranteed to resolve in the worktree build or test harness. A test engineer writing unit tests for Ring 2 sector-to-label look-up from this phase REQ cannot verify mapping correctness without reading a separate document, increasing the risk of divergence. At minimum, add the 8-row trigram-to-sector table inline or as an anchor hyperlink that is tested to resolve. | §5.3, §5.1 REQ-DISPLAY-04 |
| NF-04 | Medium | **No acceptance criterion covers north-type display string in the numerical panel when north reference switches.** §5.5 canonical format includes `[north type]` (e.g., "True N"). All worked examples and scenarios hard-code "True N". There is no scenario testing what the panel shows when the user switches from True North to Magnetic North during a session (a capability inherited from Phase 2). The north-type field in the readout is underspecified: acceptable values, display strings, and live-update behaviour when the north type changes are not defined. | §5.5, §12 Scenarios B–E |
| NF-05 | Medium | **Scenario H boundary table uses "inclusive" at 15.0° but the general boundary rule is not stated.** Scenario H Row 3 for Ring 4 labels 15.0° as "子 (sector 345°–15°, end boundary — inclusive)". Row 4 labels 15.1° as "丑". This implies the sector convention is [start, end) for the start and [start, end] for the end — or equivalently that 15.0° belongs to 子. However, no universal sector-membership rule is stated (e.g., "a bearing exactly on a boundary belongs to the lower-index sector" or "sector N covers [N×30°−15°, N×30°+15°)"). Without a stated rule, boundary test rows for Rings 5 and 3 are unverifiable independently: e.g., Scenario H Ring 5 row 2 labels 7.5° as "癸 (sector 7.5°–22.5°, start)" — is 7.5° inclusive or exclusive of the 子 sector? The general rule must be stated once and apply uniformly to all rings. | §12 Scenario H |

### Low Priority

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| NF-06 | Low | **No acceptance criterion tests pinch-to-zoom survival across a configuration change.** §5.7.2 requires zoom scale to survive screen rotation via `CompassViewModel` or `onSaveInstanceState` — but no Scenario tests this. Without a test, an engineer who stores scale in a local variable (instead of the ViewModel) will satisfy the session-only requirement while breaking the configuration-change requirement. A one-line addition to Scenario G (or a new Scenario J) covering: "Given zoom is set to 1.5×; When device rotates 90°; Then zoom remains 1.5×" would make this testable. | §5.7.2, §7 |
| NF-07 | Low | **Pinyin for Ring 3 direction-name characters under "Show romanization" is unspecified.** §5.4 REQ-L10N-02 states "Show romanization" toggle shows pinyin below each character. §5.8 provides English translations for Ring 3 labels but no pinyin column. When the "Show romanization" toggle is on, should the direction characters 南/北/東/西/東南/西北/東北/西南 display pinyin (Nán, Běi, Dōng, Xī, Dōng Nán, Xī Běi, Dōng Běi, Xī Nán)? This is unambiguous for Rings 4 and 5 (§5.8 provides pinyin there), but Ring 3 direction names are omitted. Scenario F tests "子 Zǐ" and "南 Nán" as examples — the latter implies Ring 3 direction characters should also show pinyin — but no comprehensive table confirms this, and the pinyin for compound directions (東南, 西北 etc.) is not given. | §5.4 REQ-L10N-02, §5.8, §12 Scenario F |
| NF-08 | Low | **REQ-NFR-LUOPAN-03 (battery ≤7%/hr) has no acceptance criterion or measurement procedure.** The NFR states it is "measured via Android battery historian" but does not specify: test device (API level, hardware class), test duration, background-app conditions during measurement, or a defined pass/fail protocol. Without a repeatable measurement procedure, this NFR cannot be verified by any test. The other NFRs (LUOPAN-01 and LUOPAN-02) specify the tool (`FrameMetrics`) and hardware class (API-26 mid-range); LUOPAN-03 should match that level of precision. | §11 REQ-NFR-LUOPAN-03 |
| NF-09 | Low | **Scenario I does not specify whether lock state persists across a cold start.** §8 states "The lock is cleared only when the user explicitly taps 'Clear 向' or the app is cold-started." No acceptance criterion in Scenario I or elsewhere tests the cold-start clearing behaviour. Adding a cold-start row ("Given 坐向 locked; When app cold-starts; Then lock is cleared and overlay absent") would close this gap and prevent an implementation that incorrectly persists the lock across restarts. | §8, §12 Scenario I |

---

## Positive Observations

- The addition of §5.5 canonical field order is a significant improvement — the format is now unambiguous and directly referenceable in test assertions. The fix is undermined only by the internal data error in the worked example (NF-01).
- §5.6's full 60-row 六十分金 table with ranges is exactly the level of detail needed for LUT unit tests. The three key test bearings at the end of the section are a helpful authoring signal.
- §5.7 Settings Persistence Contract is a model of testable specification: named keys, types, defaults, and explicit session-only vs. persisted scope. This eliminates an entire class of ambiguity for the TSPEC author.
- Scenario H sector boundary tables are well-structured and cover the critical 子/亥 wrap correctly. The parallel structure across Rings 3, 4, and 5 will map directly to parameterised unit tests.
- Cases E-2 through E-5 in Scenario E transform it from a single-path test into a minimal equivalence partition covering all three confidence states and two modular wrap cases.
- Scenario I and the §8 lock-lifecycle paragraph resolve a genuinely ambiguous design question in a testable, unambiguous way.
- §7.1 Ring Visibility Menu UX specification (long-press trigger ≥500 ms, `BottomSheetDialog`, "Done" button, TalkBack overflow alternative) elevates an implicit interaction into a precise acceptance target.

---

## Recommendation

**Need Attention**

NF-01 is a High finding (internal data contradiction in a worked example that will produce a wrong test assertion). It must be corrected before the FSPEC or TSPEC can be authored:

1. **NF-01** — Fix the §5.5 canonical example at 180°: replace `丙子分金` with `壬午分金` to match the §5.6 table and Scenario B.
2. **NF-02** — Replace the `[分金 per confidence]` placeholder in Scenario C with concrete expected values: "壬卯分金" at High; "N/A — calibrate for 分金 precision" at Moderate.
3. **NF-03** — Inline the 8-row 先天八卦 (Ring 2) sector-to-label table or add a resolvable anchor hyperlink so Ring 2 unit tests can be written from this document alone.
4. **NF-04** — Define the acceptable display strings for the `[north type]` field (e.g., "True N" / "Mag N") and add a scenario or note covering the readout when the user switches north reference mid-session.
5. **NF-05** — Add a single normative statement for the sector boundary membership rule (e.g., "sector covers [startBearing, endBearing); a bearing exactly equal to endBearing belongs to the next sector") and verify all Scenario H rows are consistent with it.

Items NF-06 through NF-09 are Low severity and may be addressed in a v0.3 pass or deferred to the TSPEC.
