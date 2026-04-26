# Cross-Review: Product Manager — PROPERTIES-luopan-p3-luopan-mode (v2)

| Field | Value |
|-------|-------|
| Reviewer | Product Manager |
| Document | PROPERTIES-luopan-p3-luopan-mode.md v1.1 |
| Date | 2026-04-25 |
| Iteration | 2 |
| Recommendation | Approved |

---

## Prior Findings Resolution

### F-01 (Medium) — Scenario J E2E coverage was unit-only

**Status: Resolved.**

PROP-07-065A (PG-07, Localization/E2E) has been added. It specifies an instrumented E2E test in `LuopanFragmentTest` that injects a rederived `LockState` (`displayXiangBearing = 48.5f`, `displayZuoBearing = 228.5f`) via StateFlow and asserts the overlay shows `"向: 艮 (48.5° Mag N)"` immediately without additional user action. The test level is E2E (Instrumented), which closes the gap between mapper-only validation and UI-layer validation.

**Note on scope:** The test exercises state injection rather than literal Settings-screen navigation. From a product perspective, this is the correct boundary: the Settings navigation capability is Phase 2 owned, and the Phase 3 property correctly isolates the Luopan Mode's responsibility — that the UI reacts correctly to any StateFlow emission reflecting a north-reference change. This approach is product-appropriate and catches the class of failure the prior finding was concerned with (View layer not updating its binding).

---

### F-02 (Medium) — Ring labels visible under pointer tested at mapper level only

**Status: Resolved.**

PROP-04-045A (PG-04, LuopanView Rendering / Integration) has been added. It specifies a screenshot test or Robolectric canvas inspection at bearing 180.0°, asserting that the Ring 5 label at the pointer position corresponds to `RingLabelProvider.ring5Label(SectorLookup.ring5(180.0f)) = "午"`. This closes the gap between correct mapper output and correct visual rendering alignment under the pointer.

---

### F-03 (Medium) — Legibility requirement had no property

**Status: Resolved.**

PROP-04-045B (PG-04, LuopanView Rendering / Unit) has been added. It specifies minimum text sizes matching the REQ §6 thresholds: Ring 4 ≥ 12sp, Ring 5 ≥ 11sp, Ring 6 ≥ 8sp, verified by inspecting `paint.textSize` values. This directly covers the REQ §6 legibility requirement and REQ-DISPLAY-04.

---

## Additional Changes Verified (not from prior PM findings)

The v1.1 revision also:

- Added PROP-04-045C (initial ring visibility, Unit) covering Scenario A's "all six rings visible" requirement — this resolves prior Low finding F-04.
- Updated PROP-02-017 to the correct TSPEC v1.2 `rederive()` contract and added PROP-02-026A–E covering `displayXiangBearing`/`displayZuoBearing` fields and `lockXiang()` Mag-N conversion — these are SE findings but their resolution is product-relevant as they close the risk of AC-23 not being validated at implementation time.
- Updated the gap analysis to reference N-F01–N-F05 findings — the prior false claim of "No uncovered gaps" has been corrected.
- Removed the duplicate PROP-08-067 (SE-F07) — the ≤300 ms mode-entry latency invariant is now the sole authority of PROP-06-053.

---

## New Findings (if any)

### Low Priority

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-08 | Low | **Ring visibility menu UX behaviors have no E2E properties.** REQ §7.1 specifies a detailed UX contract for the ring visibility `BottomSheetDialog`: long-press trigger (≥500 ms), real-time dial update while the sheet is open, a "Done" button, and an accessibility alternative via the three-dot overflow menu for TalkBack users. PROP-04-041 covers the render outcome (hidden ring produces no draw calls) and PROP-04-045C covers the initial visibility state, but no property covers: (a) the long-press trigger launching the `BottomSheetDialog`, (b) the real-time ring update while the sheet is still open, or (c) the overflow-menu accessibility alternative. All three are user-visible behaviors specified in REQ §7.1. | REQ §7.1 Ring Visibility Menu UX, AC-14, Scenario G |
| F-09 | Low | **Disabled "Lock 向" tooltip text has no property.** REQ §12 Scenario E-3 specifies that when confidence is Poor and the user attempts to tap "Lock 向", the app shows a tooltip: "Cannot lock — heading is unreliable." No property in PG-09 asserts the tooltip text content or that a tooltip appears at all. The button's disabled state is covered by PROP-09-069, but the user-facing feedback message is absent from the properties. | REQ §12 Scenario E-3; REQ-DISPLAY-06 |
| F-10 | Low | **Scenario J-2 mountain-label boundary crossing has no dedicated property.** REQ §12 Scenario J-2 specifies: "If the magnetic-adjusted bearing crosses a 山 boundary (e.g., falls into 甲 sector instead of 艮), the overlay MUST update to show the corrected 山 label." PROP-02-019 asserts that 山 labels are invariant to north type switches (derived from stored True North), and PROP-07-065A covers the standard AC-23 case (45° True N → 48.5° Mag N, mountain label remains 艮). However, no property tests the case where the True North bearing is close enough to a 山 boundary that a north-type switch causes the `rederive()` display bearing to cross the boundary and produce a different sector — which per Scenario J-2 should update the overlay. The relationship between the stored True North mountain label and the potentially different display-bearing sector mountain label is an under-specified area in the properties. | REQ §12 Scenario J-2; AC-23; FSPEC §4d |

---

## Recommendation

**Approved**

All three Medium findings from the iteration 1 PM review (F-01, F-02, F-03) are resolved. The v1.1 PROPERTIES document now provides E2E-level coverage for the north reference switch user behavior (PROP-07-065A), ring label alignment under the pointer (PROP-04-045A), and the legibility threshold requirement (PROP-04-045B). The gap analysis is honest and correctly acknowledges the N-F01–N-F05 coverage.

The three new Low findings (F-08, F-09, F-10) do not block implementation. They represent UX interaction details and an edge-case scenario that are lower risk than the issues already resolved:

- F-08 (ring visibility menu trigger and accessibility): the render outcome is tested; the menu trigger is a gesture-recognition detail.
- F-09 (tooltip text): button disable state is tested; tooltip text is a localization detail.
- F-10 (Scenario J-2 boundary crossing): the standard AC-23 case is fully covered; the boundary-crossing variant is a corner case that would require explicit PM clarification of whether the stored True North mountain label or the display-bearing mountain label takes precedence in the overlay — this ambiguity predates the properties document.

The TE author may address F-08, F-09, and F-10 at their discretion in a future revision. They do not block implementation.
