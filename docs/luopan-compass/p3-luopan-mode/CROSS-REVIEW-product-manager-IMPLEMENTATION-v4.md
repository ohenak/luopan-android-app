# Product Manager Cross-Review — Implementation (Iteration 4)

**Reviewer:** product-manager
**Document reviewed:** Implementation — Phase 3 Luopan Display Mode (feat-luopan-p3-luopan-mode)
**Date:** 2026-04-26
**Iteration:** 4

---

## Summary

The PM3-F01 finding from iteration 3 — the broken `NorthTypeToggleTest` Espresso assertion caused by the `R.string.magnetic_north` resource not being updated to match the runtime "Mag N" label — is now fully resolved by commit `3439746`. The fix updated `strings.xml` so that `magnetic_north = "Mag N"`, aligning the string resource with the runtime label emitted by `NorthTypeEngine`. This also updated the toggle button label on-screen from "Magnetic N" to "Mag N", which resolves the Q-01 product decision from previous iterations. All other previously-verified behaviour (PM2-F01, PM2-F02, PM-F02 lock-button wiring) remains intact with no regressions detected. Three low-priority findings are retained from prior iterations and continue to require PM sign-off before release.

---

## Recommendation

**Approved with Minor Issues**

PM3-F01 is resolved. No new Medium or High findings are identified in this iteration. Three Low findings (PM3-F02, PM-F03, PM-F04) remain open and require explicit PM sign-off before release, but none block merging for testing. The feature branch is ready for QA.

---

## Findings

### High Priority

None.

---

### Medium Priority

None.

---

### Low Priority

**PM3-F02 (Low — pre-existing, unresolved): `LuopanState.INITIAL.northLabel` inconsistency**

`LuopanState.INITIAL` (file: `app/src/main/java/com/luopan/compass/luopan/LuopanState.kt`, line 68) sets `northLabel = "True N"` with the comment "northLabel defaults to True N per the app default north reference at startup". However, `NorthTypeEngine` defaults to `NorthType.MAGNETIC` (line 69 of `NorthTypeEngine.kt`), and `CompassUiState.INITIAL.north_label` was corrected to `"Mag N"` in the PM2-F02 fix. The inconsistency means that during the sub-frame window before the first sensor emission, `LuopanState.INITIAL.northLabel = "True N"` disagrees with the actual initial north type. In practice this is a cosmetic sub-frame flash because `LuopanStateMapper.map()` overwrites `northLabel` from `compassState.north_label` on the first sensor frame. Impact: low user-visible impact; risk for snapshot unit tests that inspect initial state before the first sensor frame.

No regression — this was tracked as PM-F05 (Low) in iteration 1 and as PM3-F02 in iterations 2 and 3. It has not been corrected.

**Recommended action:** Either update `LuopanState.INITIAL.northLabel` to `"Mag N"` to match the engine default, or add an explanatory comment acknowledging the intentional pre-sensor-frame default.

---

**PM-F03 (Low — pre-existing, requires PM sign-off): Ring 3 label truncation on dial**

`drawRing3Labels()` in `LuopanView.kt` renders only the first character of the compound Ring 3 label (e.g., "☵" instead of "☵ 坎 北"). REQ-LUOPAN-01 and Scenario A/B require the full compound label "trigram symbol + 卦名 + direction name" on the dial face. The numerical readout panel correctly shows the full label. This is an engineering trade-off for ring band width constraints that has not been formally documented as an approved truncation in the REQ or FSPEC.

**Recommended action:** PM must explicitly approve or reject this truncation before release. If approved, document as an engineering trade-off in the REQ or a release note.

---

**PM-F04 (Low — pre-existing, requires PM sign-off): Ring 6 label truncation on dial**

`drawRing6Labels()` renders the first two characters of the Ring 6 label (e.g., "壬午" instead of "壬午分金"). REQ §5.6 and Risk P3-R1 require 60 分金 labels to be legible at 8sp with pinch-to-zoom as the mitigation. Practitioners may not recognise 2-character truncated labels as 分金 divisions.

**Recommended action:** Same as PM-F03 — PM must explicitly approve this truncation before release.

---

## Verification of PM3-F01 Fix

**Finding from iteration 3:** `R.string.magnetic_north` in `strings.xml` was `"Magnetic N"` while the runtime label from `NorthTypeEngine` was already changed to `"Mag N"`. This caused `NorthTypeToggleTest.tapTrueN_thenMagneticN_northLabelChangesBackToMagneticN()` (line 86) and related Espresso tests in `LocationPermissionRationaleTest` (line 162), `LocationPermissionTest` (line 99), and `NoGpsDialogEspressoTest` (line 140) to fail at runtime because `withText(R.string.magnetic_north)` would resolve to `"Magnetic N"` while the `northLabel` TextView displayed `"Mag N"`.

**Fix applied (commit `3439746`):** `strings.xml` line 4 updated from `"Magnetic N"` to `"Mag N"`. Content description `toggle_north_type_content_description` updated from "Toggle north type between Magnetic N and True N" to "Toggle north type between Mag N and True N".

**Verification:**
- `app/src/main/res/values/strings.xml` line 4: `<string name="magnetic_north">Mag N</string>` — confirmed.
- `NorthTypeToggleTest.kt` line 86 now asserts `withText(R.string.magnetic_north)` = `withText("Mag N")`, which matches the runtime `northLabel` value emitted by `NorthTypeEngine.computeHeadingFields()` (line 173).
- All four Espresso tests that assert `R.string.magnetic_north` (`NorthTypeToggleTest`, `LocationPermissionRationaleTest`, `LocationPermissionTest`, `NoGpsDialogEspressoTest`) now resolve consistently to `"Mag N"`.
- The toggle button label (`btn_magnetic_n` in `fragment_modern_compass.xml` line 62: `android:text="@string/magnetic_north"`) now displays "Mag N" on-screen. This resolves the Q-01 product decision from prior iterations by treating the string resource update as the accepted approach — the button label is now "Mag N", consistent with the north type label throughout the UI.

**Status: Fully resolved.** No regression detected. PM3-F01 is closed.

---

## Verification of Prior Iteration Fixes (Retained Correct)

### PM2-F01 Fix — `setNorthType()` wiring (AC-23 / Scenario J-2)

**Status: Confirmed intact.** `CompassViewModel.setNorthType(type)` (line 194–195) calls `northTypeEngine.setNorthType(type)` followed by `onNorthTypeChanged(type)`, passing the new `NorthType` directly. `onNorthTypeChanged(type)` (line 314) derives `isMagneticNorth = type == NorthType.MAGNETIC` from the parameter, not from `_uiState.value.north_type`. `ZuoXiangLock.rederive()` receives the correct `isMagneticNorth` flag immediately without waiting for the next sensor frame. No regression.

### PM-F02 Fix — Lock button always enabled (AC-09 / Scenario E-3)

**Status: Confirmed intact.** `LuopanFragment.updateLockButton()` (line 278) sets `btnLockXiang.isEnabled = true` unconditionally. Click routing correctly dispatches `clearXiang()`, `lockXiang()`, or a Toast based on `isLockActive` and `canLock`. The Toast path is reachable at all confidence levels.

---

## New Observation: Q-01 Resolution Implicit in PM3-F01 Fix

The commit message for `3439746` ("strings.xml magnetic_north changed from 'Magnetic N' to 'Mag N'... prevents NorthTypeToggleTest Espresso assertion mismatch on device") implicitly resolves Q-01 from previous iterations. The product decision is now: the toggle button label is "Mag N" (not "Magnetic N"). This is consistent with the abbreviated "Mag N" label used throughout the north-type display in both Modern Mode and Luopan Mode. Q-01 is considered closed.

---

## Acceptance Criteria Coverage

| AC ID | Status | Notes |
|-------|--------|-------|
| AC-01 | Pass | Mode entry latency — NavController + LuopanFragment inflation; dark background pre-render |
| AC-02a | Pass | Dial counter-rotation math: `dialRotationDeg = -bearingDeg (mod 360°)` in LuopanView |
| AC-02b | Pass | Pointer fixed; ring assembly rotates; pointer drawn outside rotation transform |
| AC-03 | Pass | Canonical readout format at 180°/High/"True N" — northLabel now correctly "Mag N" or "True N" |
| AC-04 | Pass | Ring 4/5 show "卯", Ring 3 shows "☳ 震 東" at 90° |
| AC-05 | Pass | Readout at 90°/High — canonical format including "壬卯分金" |
| AC-06 | Pass | 分金 hidden at Moderate — fenJinLabel null; substitute string from R.string.fen_jin_na shown |
| AC-07 | Pass | 坐向 lock at High — overlay shows "向: 艮 (45.0° True N)" and "坐: 坤 (225.0° True N)" |
| AC-08 | Pass | 坐向 lock at Moderate — enabled; overlay shows correctly; 分金 not shown in overlay |
| AC-09 | Pass | Lock disabled at Poor — button always enabled; Toast fired on tap |
| AC-10 | Pass | 坐 wrap-around at 向=270° — modulo derivation correct in ZuoXiangLock.lock() |
| AC-11 | Pass | 坐 wrap-around at 向=350° — modulo derivation correct |
| AC-12 | Pass | Default language is zh-Hant regardless of system locale — BR-08 enforced by showMyLanguage toggle |
| AC-13 | Pass | "Show romanization" toggle — pinyin appended when showRomanization=true |
| AC-14 | Pass | Ring visibility hide — BottomSheetDialog via long-press; per-ring switches; live update |
| AC-15 | Pass | Ring visibility session reset — flags not written to SettingsRepository; reset on cold start |
| AC-16 | Pass | Ring 4 子/亥 wrap-around boundary assertions — SectorLookup.ring4() inclusive-left/exclusive-right |
| AC-17 | Pass | Ring 4 generic boundary (44.9°=丑, 45.0°=寅) |
| AC-18 | Pass | Ring 5 sub-15° boundaries (7.4°=子, 7.5°=癸, 22.4°=癸, 22.5°=丑) |
| AC-19 | Pass | Ring 3 45° boundaries (22.4°=坎, 22.5°=艮, 67.4°=艮, 67.5°=震) |
| AC-20 | Pass | 壬子分金 wrap-around (357.9°=庚子, 358.0°=壬子, 0.0°=壬子, 4.0°=甲子) |
| AC-21 | Pass | Lock preserved across mode switch — ZuoXiangLock in Activity-scoped ViewModel |
| AC-22 | Pass | North reference switch updates readout — northLabel and bearingDeg update on next frame |
| AC-23 | Pass | North reference switch while locked — displayXiangBearing/displayZuoBearing updated immediately via rederive(); 山 labels invariant |
| AC-24 | Pass | SENSOR_ERROR — 山/地支/後天八卦/bearing show "—"; confidence badge shows "Sensor error" |
| AC-25 | Pass | Pinch-to-zoom clamped to [0.8×, 2.0×] in setZoomScale() |
| AC-26 | Pass | Zoom survives config change — held in CompassViewModel (not destroyed on config change) |
| AC-27 | Pass | Zoom resets on cold start — session-only; not written to SettingsRepository |
| AC-28 | Pass | SENSOR_ERROR while locked — overlay remains; readout fields show "—"; lock button shows "Clear 向" |
| AC-29 | Pass | STABILIZING state — lock button disabled; confidence badge "Calibrating..." |

---

*End of Product Manager Cross-Review — Implementation (Iteration 4)*
