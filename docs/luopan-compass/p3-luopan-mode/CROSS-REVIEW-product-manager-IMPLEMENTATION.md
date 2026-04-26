# Cross-Review: product-manager — Implementation

**Reviewer:** product-manager
**Document reviewed:** Implementation — Phase 3 Luopan Display Mode (feat-luopan-p3-luopan-mode)
**Date:** 2026-04-25
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Medium | `onNorthTypeChanged()` is defined in `CompassViewModel` but never called from any production code path. `setNorthType()` does not call `onNorthTypeChanged()` or `zuoXiangLock.rederive()`. As a result, when the user switches north reference while a 坐向 lock is active, `displayXiangBearing` and `displayZuoBearing` are NOT rederived. The overlay will continue showing the stale display bearing from lock time rather than the north-adjusted value. This breaks Scenario J-2 / AC-23 for the overlay bearing display update. Note: the live readout panel (`bearingDeg`, `northLabel`) updates correctly on the next sensor frame because the sensor pipeline reads `northTypeEngine.northType.value` directly — only the overlay lock display values are affected. | Scenario J-2, AC-23, FSPEC §4d, REQ §8 |
| F-02 | Medium | The "Cannot lock — heading is unreliable" tooltip (AC-09 / Scenario E-3) is wired as a Toast on the `btnLockXiang.setOnClickListener`, but the button is set to `isEnabled = false` when confidence is POOR/STABILIZING/SENSOR_ERROR. Disabled Android buttons do not receive click events, so the Toast never fires. The FSPEC and REQ both specify a tooltip on the disabled button tap. This means the affordance required by AC-09 ("tooltip shows 'Cannot lock — heading is unreliable'") is silently missing. | AC-09, Scenario E-3, FSPEC §4e, REQ §8 |
| F-03 | Low | Ring 3 (`後天八卦`) labels on the dial are rendered as only the first character of the compound label (e.g. "☵" instead of "☵ 坎 北"). This is a rendering truncation in `drawRing3Labels()`: `label.character.take(1)`. This is likely an intentional engineering trade-off for space (the ring band is narrow), but it is not documented in the REQ or FSPEC as an approved truncation. The REQ §5.3 states "trigram symbol + 卦名 + direction name", and the acceptance criterion for dial rendering (Scenario A, Scenario B) requires "☲ 離 南 is displayed centered under the pointer". The abbreviated rendering on the dial may confuse practitioners who expect the full compound label. The numerical readout panel correctly shows the full "☲ 離 南" string. | REQ-LUOPAN-01, Scenario A, Scenario B, FSPEC §4.4 |
| F-04 | Low | Ring 6 (`六十分金`) labels on the dial are rendered as the first two characters (e.g. "壬午" instead of "壬午分金"). The implementation comment notes this is for legibility. However this is an unspecified truncation — the REQ §5.3 / §5.6 requires 分金 labels to be rendered on Ring 6. Practitioners may not recognise truncated 2-character labels as 分金 divisions. This should be explicitly approved as an engineering trade-off or have the full label rendered at the minimum 8sp font size with pinch-to-zoom as the legibility mitigation (as stated in Risk P3-R1). | REQ-DISPLAY-04, REQ §5.6, FSPEC §4.7, Risk P3-R1 |
| F-05 | Low | `LuopanState.INITIAL` sets all character fields to "—" (matching the SENSOR_ERROR sentinel), including at bearing 0° which is a valid cold-start bearing with POOR confidence. The FSPEC (ES-07) specifies that at cold start with POOR confidence, the character fields should be populated from the 0° bearing (e.g. 壬 for Ring 5, 子 for Ring 4, ☵ 坎 北 for Ring 3) — not "—". The "—" sentinel is reserved for SENSOR_ERROR only (FSPEC ES-01 / AC-24). The initial state sets fields to "—" before the first sensor frame, which will briefly show the SENSOR_ERROR display at cold start even though the state is POOR, not SENSOR_ERROR. | ES-07, AC-24, FSPEC §5 ES-01 / ES-07 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | F-03: Is the single-character truncation of Ring 3 labels on the dial (showing only the trigram symbol, e.g. "☵") an approved product decision, or should the full compound label ("☵ 坎 北") be rendered — possibly at reduced font size or on two lines? This needs explicit PM sign-off before release given REQ-LUOPAN-01 specifies the full compound label. |
| Q-02 | F-04: Is the 2-character truncation of Ring 6 labels on the dial (showing e.g. "壬午" for "壬午分金") an approved product decision? The REQ notes that legibility at 8sp is required (REQ-NFR-LUOPAN-02) and pinch-to-zoom is the mitigation for small screens. The truncation should be explicitly documented as approved. |
| Q-03 | F-01: Should `setNorthType()` automatically call `onNorthTypeChanged()` — making it the single point of truth for all north type change side-effects — or should the caller (UI layer) be responsible for calling `onNorthTypeChanged()` separately? The current design creates a gap where `onNorthTypeChanged()` is never invoked. |
| Q-04 | F-02: Should the disabled button tooltip use `View.setOnLongClickListener` + `TooltipCompat.setTooltipText()` (Android's accessibility-compatible tooltip API), or is a long-press Toast acceptable? This affects accessibility (TalkBack does not announce Toast messages). |

---

## Positive Observations

- All six rings are implemented with the correct sector counts and angular widths matching the REQ: Ring 2 (8 × 45°), Ring 3 (8 × 45°), Ring 4 (12 × 30°), Ring 5 (24 × 15°), Ring 6 (60 × 6°).
- Sector lookup tables in `SectorLookup` correctly implement the inclusive-left, exclusive-right `[start°, end°)` rule (BR-01) with wrap-around sectors for 子 (Ring 4), 壬子分金 (Ring 6), and the analogous Ring 3 and Ring 5 cases. The LUTs match the REQ/FSPEC reference tables exactly.
- The 坐向 lock derivation is correct: `zuoBearing = (xiangBearing + 180f) % 360f` (BR-06). All wrap-around cases (向 = 270°, 向 = 350°) are handled by the modulo operation.
- The True North bearing invariance for 山 labels is correctly implemented: `xiangBearing` and `zuoBearing` in `ZuoXiangLock.LockState` are never modified by `rederive()`, and mountain labels are derived once at lock time from the True North bearing (FSPEC §4d).
- Session-only vs persisted state contract (BR-09) is correctly implemented: ring visibility and zoom scale are held only in `CompassViewModel` memory; `SettingsRepository` contains exactly the three persisted keys (`display_mode`, `luopan_show_romanization`, `luopan_show_my_language`). No session-only keys are written to SharedPreferences.
- The PM-Q01 fix is correctly implemented: "Clear 向" remains enabled when lock is active regardless of current confidence (`canLock || isLockActive`). Users can always clear a frozen lock even during SENSOR_ERROR (PROP-09-072).
- The `LuopanFragment` correctly reads `displayXiangBearing` (not `xiangBearing`) for the overlay and for `LuopanView.setLockState()` tick mark positioning (V3-F01 fix implemented).
- Lock state is preserved across mode switches: `zuoXiangLock` lives in `CompassViewModel` (Activity-scoped), so switching to Modern Mode and returning to Luopan Mode correctly restores the overlay (BR-10, Scenario I, AC-21).
- The mode switcher correctly uses NavController + TabLayout with `NavHostFragment`; `CompassViewModel` is scoped to `CompassActivity` and shared via `activityViewModels()` in both fragments (REQ §9.2).
- Cold-start mode restoration from `SettingsRepository.displayMode` is implemented in `CompassActivity.restoreDisplayModeOnColdStart()`.
- Ring visibility bottom sheet is triggered correctly by long-press (≥500 ms via `GestureDetector`) and by the three-dot overflow menu (accessibility alternative per FSPEC Flow 5 / REQ §7.1). Session-only state is correctly not written to `SettingsRepository`.
- Hardware acceleration is enabled via `LAYER_TYPE_HARDWARE` in `LuopanView.init`, meeting REQ-NFR-LUOPAN-02. Ring geometry is pre-computed in `onSizeChanged` with no trig or allocation in `onDraw`.
- The confidence state → UI state mapping (BR-05) is correctly implemented across all five confidence levels including `STABILIZING` and `SENSOR_ERROR`.
- The 分金 field is correctly gated on `HIGH` confidence only (BR-07): `fenJinLabel` is `null` for all other states, and the Fragment renders the substitute string from `R.string.fen_jin_na`.
- Default language baseline (BR-08) is correctly handled: ring labels use Traditional Chinese by default. The `showMyLanguage` toggle — not the system locale — controls English equivalents. Both toggles are persisted in `SettingsRepository`.

---

## Recommendation

**Need Attention**

Two Medium findings require resolution before the feature can ship:

1. **F-01 (Medium):** `onNorthTypeChanged()` is never called. Wire it into `setNorthType()` or `toggleNorthType()` so north-type switches immediately rederive the 坐向 overlay display bearings. Without this fix, Scenario J-2 / AC-23 (the overlay bearing update on north reference switch) will fail end-to-end on device.

2. **F-02 (Medium):** The "Cannot lock" tooltip does not fire because the button is disabled. Switch to `TooltipCompat.setTooltipText()` or `View.setOnLongClickListener` to surface the tooltip on a disabled button, matching AC-09 and FSPEC §4e.

Low findings (F-03, F-04) require PM sign-off on the ring label truncation decisions (Q-01, Q-02) before release, but do not block the implementation from merging for testing. F-05 is a cosmetic cold-start flash issue with low user-visible impact.
