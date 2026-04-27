# Cross-Review: product-manager — TSPEC

**Reviewer:** product-manager
**Document reviewed:** docs/luopan-compass/p4-bearing-history/TSPEC-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Medium | **COUNTING state does not reset after 60 s when deviation ≤ 10%.** Section 5.3 (`DriftDetector`) documents this explicitly: "If the timer exceeds 60 s but deviation is ≤ 10%, the detector stays COUNTING — it does not reset, because there is no precondition violation." However, REQ-CAL-05 and FSPEC-CAL-03 define the threshold as "preconditions held continuously for >60 seconds AND measured field magnitude differs from expected_field_ut by >10%." The REQ and FSPEC are silent on what happens when >60 s elapses but deviation is ≤ 10% — they only say TRIGGERED fires if both conditions are met. The TSPEC's resolution (stay COUNTING and re-evaluate on each subsequent frame) is a product-behavior decision. If deviation later rises above 10% and TRIGGERED fires, the user will have been in a drifted environment for much longer than 60 s before being prompted. This may lead to delayed prompts in environments with fluctuating field deviation around the 10% boundary. The product team must confirm this is the intended behavior or explicitly add it to the FSPEC as an approved edge-case rule. | REQ-CAL-05, FSPEC-CAL-03 |
| F-02 | Medium | **`SecurityException` from `SensorManager` is not caught; potential crash not flagged as a known risk.** Section 10 (Error Handling) notes: "If `SensorCapabilityLogger.maybeWrite()` throws non-`IOException` (e.g., `SecurityException`) — Not caught; propagates to `CompassActivity.lifecycleScope`; crash in production." REQ-SENSOR-07 requires the write to fail gracefully ("If the write fails, failure is logged at `Log.e` level"). Although the REQ scopes the failure contract to `IOException`, the product intent is clearly that a sensor diagnostics write should never crash the app. The TSPEC acknowledges the gap but defers it to the engineer's judgment. From a product perspective, a crash in `CompassActivity.onCreate()` during what is a silent diagnostic write is unacceptable. The TSPEC must either catch `Exception` broadly or escalate this as an explicit product risk with a deferred fix in the backlog. | REQ-SENSOR-07 |
| F-03 | Low | **Snackbar text for swipe-to-delete is "Deleted" only; REQ specifies an "Undo" action button — both are present, but the Snackbar body text is not specified in the REQ.** The REQ says "5-second undo toast" and FSPEC-HIST-03 confirms Snackbar with "Undo" action. Section 7.5 defines `<string name="bearing_deleted">Deleted</string>` as the Snackbar body text. This string is not in the REQ or FSPEC. "Deleted" is minimal — competitors and Material Design guidelines often use "Record deleted" or "[Record name] deleted" for clarity. This is a low-severity observation since the REQ does not mandate the Snackbar body text, but the PM should decide if "Deleted" is acceptable or if a richer string (e.g., "Bearing deleted") is preferred before implementation. | REQ-CAPTURE-03, FSPEC-HIST-03 |
| F-04 | Low | **`RESULT_OK` from CalibrationWizardActivity on the age-banner path does NOT set `calAgeBannerDismissed = true`; the banner hides only if `calibration_age_days` drops to ≤ 30 after `loadCalibrationAge()`.** This is correct per FSPEC-CAL-01. However, if `loadCalibrationAge()` runs asynchronously and the stored calibration record was not updated yet (e.g., the user completed the wizard but the DB write on the wizard side has not committed by the time the Fragment calls `loadCalibrationAge()`), there is a brief window where the age banner could remain visible after a successful recalibration. The TSPEC does not address this race condition. The product impact would be a flickering or briefly re-visible banner after calibration, which degrades trust in the recalibration flow. The TSPEC should acknowledge this race and either document it as acceptable or provide a mitigation (e.g., set `calAgeBannerDismissed = true` immediately on `RESULT_OK` while the async load completes). | REQ-CAL-05, FSPEC-CAL-01 |
| F-05 | Low | **`notifyDataSetChanged()` used for expand/collapse in `BearingAdapter`.** Section 7.2 explicitly calls `notifyDataSetChanged()` for the expand/collapse toggle: "acceptable for expand/collapse; DiffUtil handles insertions/deletions." This is a technical trade-off, but from a product perspective it risks frame drops (janky animation) on the History list at 500 records, which is within the REQ's defined performance ceiling. The 16 ms fling frame-time NFR (REQ-CAPTURE-03 Performance NFR) covers fling only, not expand/collapse. If expand/collapse on large lists causes visible jank, it will harm the professional user experience without violating the specified NFR. The TSPEC should either justify why expand/collapse at 500 records remains smooth with `notifyDataSetChanged()` or commit to a targeted `notifyItemChanged()` approach. | REQ-CAPTURE-03 |
| F-06 | Low | **Search bar hidden (`View.GONE`) in State A (zero records) is correctly spec'd, but the TSPEC does not specify what happens to the search bar when the list transitions from State A to the normal list state.** FSPEC-HIST-04 says the transition from State A to normal is reactive (no manual refresh). If the search bar becomes visible only after the list is non-empty, the Fragment must restore it on the same Flow emission that reveals the RecyclerView. Section 7.1 describes State A logic but does not explicitly state "restore search bar visibility to `VISIBLE` when `getAllFlow()` emits a non-empty list." An engineer could leave the search bar `GONE` after the first record is inserted. This is an implementation gap that could silently fail acceptance test AT-HIST-04-B (which only asserts that the RecyclerView shows the new record, not that the search bar reappears). | REQ-CAPTURE-03, FSPEC-HIST-04 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For F-01: Is the intended behavior that TRIGGERED can fire long after 60 s if deviation is fluctuating around the 10% threshold? Or should there be a maximum counting window (e.g., 5 minutes) after which the timer resets even without a precondition violation, forcing a fresh 60 s window? |
| Q-02 | For F-04: Should the age-based banner be immediately hidden on `RESULT_OK` (before the async `loadCalibrationAge()` call completes) by setting `calAgeBannerDismissed = true`, then un-suppressed only if `loadCalibrationAge()` finds the age is still > 30 days? |
| Q-03 | The TSPEC notes that `Room.insert` failures during undo are silently swallowed (Section 10). If undo silently fails, the user sees no feedback and thinks the record was restored when it was not. Is this acceptable for a Phase 4 MVP, or should a brief error Toast/Snackbar be shown on undo failure? |
| Q-04 | FSPEC-CAL-02 specifies: when the drift banner is visible and a precondition clears, the banner stays visible. The TSPEC correctly implements this (§5.3: `RESET` is informational only; ViewModel does not auto-hide). However, if the device moves (ending stationary precondition), the drift banner remains visible even though the drift condition is no longer active. Is this the intended UX, or should the banner auto-dismiss when the drift condition stops being active? |

---

## Positive Observations

- **Complete requirements traceability table (Section 11):** Every P0/P1/P2 requirement (REQ-CAPTURE-03, REQ-DETECT-05, REQ-CAL-05, REQ-SENSOR-07) maps to named technical components. No requirement is silently absent.
- **No scope creep detected:** The TSPEC introduces no features, UX behaviors, or data fields beyond what the REQ and FSPEC approve. The four technical domains (bearing history, recalibration lifecycle, sensor logging, DB migration) match the REQ scope exactly. REQ-CAPTURE-05 (export/CSV) remains correctly deferred to Phase 5.
- **Acceptance criteria fidelity is high:** All named acceptance tests from the FSPEC (AT-HIST-01 through AT-NAV-01, AT-SENSOR-01) are enumerated in Section 9 with specific implementation strategies. The TSPEC does not narrow, reinterpret, or silently drop any acceptance criterion.
- **Snackbar duration explicitly set to 5000 ms** (Section 7.3), not `Snackbar.LENGTH_LONG` — correctly preserving the product's "exactly 5 seconds" requirement from REQ-CAPTURE-03.
- **Floor-division for calibration age N** is correctly encoded in Section 7.1 (`ageDays > 30L` — Long comparison) and string resource (`%1$d` format) — consistent with REQ-CAL-05's "N is never rounded up" requirement.
- **Interference badge display** correctly implements `View.GONE` (not `INVISIBLE`) for `interference_flag = false` records, and field deviation percentage formatting (stored fractional × 100, integer truncation) matches REQ-DETECT-05 and FSPEC-HIST-05.
- **Dual `ActivityResultLauncher` design** (Section 6.2) is a clean resolution of the disambiguation problem with no product behavior change; each launcher handles only its own side effects.
- **Cross-review finding resolutions are thorough:** All SE FSPEC-v2 and TE FSPEC-v2 findings (F-01 through F-09) are resolved with documented rationale and traceable implementation decisions.

---

## Recommendation

**Approved with Minor Issues**

All P0 and P1 requirements are covered with traceable technical components. Acceptance criteria from the FSPEC are preserved and not narrowed. No scope creep was found.

F-01 (COUNTING state persisting past 60 s when deviation ≤ 10%) and F-02 (unguarded `SecurityException` crash path) are Medium findings that need a PM decision or explicit risk acceptance before implementation begins. Neither blocks the design's correctness for the happy path, but both represent product-behavior gaps that could surprise users or cause production crashes.

**Required before implementation sign-off:**

1. **F-01:** PM must confirm or deny the "stay COUNTING indefinitely past 60 s" behavior in the FSPEC. If accepted, add it as an explicit rule in FSPEC-CAL-03 so it is not a silent TSPEC invention.
2. **F-02:** The TSPEC must either catch `Exception` broadly in `SensorCapabilityLogger.maybeWrite()` (and log it) or add a formal product risk entry to the REQ's risk register acknowledging a potential crash on launch during sensor enumeration.

F-03 through F-06 are Low findings that can be resolved during implementation without requiring FSPEC changes, provided the engineer is made aware.
