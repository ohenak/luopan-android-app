# Cross-Review: product-manager — TSPEC

**Reviewer:** product-manager
**Document reviewed:** docs/luopan-compass/p4-bearing-history/TSPEC-luopan-p4-bearing-history.md (v0.2-draft)
**Date:** 2026-04-27
**Iteration:** 2

---

## Iteration 1 Finding Resolution Audit

Before new findings, each PM TSPEC-v1 finding is assessed as resolved or not.

| PM v1 ID | Severity | Status | Notes |
|----------|----------|--------|-------|
| F-01 | Medium | Resolved — with a caveat (see F-01 below) | §5.3 documents the "re-evaluate each frame after threshold" rule with product rationale. Implementation is coherent. FSPEC-CAL-03 has NOT yet been updated; the TSPEC flags this as deferred ("should be added to FSPEC-CAL-03 in the next FSPEC revision"). |
| F-02 | Medium | Resolved | `catch (e: Exception)` broadened; `SecurityException` now caught, logged, version key not updated. §5.5 and §10 both confirmed. |
| F-03 | Low | Resolved | Snackbar string changed from "Deleted" to "Bearing deleted" in §7.5. |
| F-04 | Low | Resolved | `onCalibrationCompleteFromHistory()` sets `calAgeBannerDismissed = true` immediately on `RESULT_OK`, then clears it once the async load confirms age ≤ 30. Mitigation is specified in §6.1.3 and §7.1. |
| F-05 | Low | Resolved | `BearingAdapter.toggleExpanded()` now uses `notifyItemChanged()` on only the two affected positions (§7.2). |
| F-06 | Low | Resolved | `updateListState()` helper (§7.1) explicitly handles all four states and restores `searchBar.visibility = View.VISIBLE` on the same Flow emission that reveals the RecyclerView. |

**Overall iteration 1 resolution:** All six PM v1 findings are substantively resolved. Two new issues were discovered during this review.

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Medium | **FSPEC-CAL-03 has not been updated with the approved "re-evaluate each frame after threshold" rule.** The TSPEC §5.3 documents and codifies a product-behavior edge case: when the 60-second drift timer has elapsed but field deviation is ≤ 10%, the detector stays in COUNTING indefinitely and re-evaluates deviation on every subsequent frame — there is no maximum counting duration. The TSPEC correctly flags this as "should be added to FSPEC-CAL-03 in the next FSPEC revision," but the FSPEC v0.2-draft (the current approved FSPEC) does not contain this rule. As written, the FSPEC and TSPEC are inconsistent on this point: FSPEC-CAL-03 Timer Behavior states "Threshold: Timer must exceed 60 seconds continuously" and is silent on what happens when the timer exceeds 60 s but deviation is ≤ 10%. An engineer working only from the FSPEC would have no basis for the "stay COUNTING" behavior and could legitimately implement a timer reset instead. Because the TSPEC is a derivative of the FSPEC (not authoritative over it), the FSPEC must be updated before implementation begins. This finding carries forward from v1 and is not yet closed. | REQ-CAL-05, FSPEC-CAL-03 |
| F-02 | Low | **Internal inconsistency in §12 (SE FSPEC-v2 F-04 resolution note) contradicts the actual implementation.** The resolution note for SE FSPEC-v2 F-04 in §12 states: "`calAgeBannerDismissed` is not set to true on this path — the hide is driven entirely by `calibration_age_days ≤ 30`." This statement is leftover language from v0.1 and is now incorrect. The actual v0.2 implementation in §6.1.3 and §7.1 sets `calAgeBannerDismissed = true` immediately on `RESULT_OK` (the PM v1 F-04 race-condition mitigation). An engineer reading §12 for the RESULT_OK handling would receive contradictory guidance: the resolution note says the flag is not set, but the method body says it is. The correct behavior is in the method body. The §12 note must be corrected to reflect the actual implementation. This is a documentation accuracy issue that could mislead an implementer. | REQ-CAL-05, FSPEC-CAL-01 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | The §12 TE TSPEC-v1 resolution table lists F-01, F-02, F-03, F-04, F-05, F-07, F-10 but omits F-06, F-08, and F-09. These three are in fact addressed in §9.7 (F-06 → AT-CAL-03-B2), §9.9 (F-08 → AT-HIST-04-A search bar), and §9.6 (F-09 → `elapsedCountingMs()`). Is it intentional to omit them from the resolution table? If so, should the TSPEC header be corrected to "TE TSPEC-v1 (F-01–F-05, F-07, F-10)" to avoid implying a complete resolution table? |
| Q-02 | Q-03 from iteration 1 (undo failure gives no user feedback) was raised as a question and not formally answered. §10 marks it out-of-scope for Phase 4, which is acceptable — but was this a conscious PM decision to defer, or is it simply not addressed? Confirming the deferral decision is in scope for a future phase would close this question cleanly. |
| Q-03 | The `onCalibrationCompleteFromHistory()` method does not handle `record == null` (the case where no calibration record exists after a successful wizard). In that branch, `calAgeBannerDismissed` remains `true` indefinitely for the session, suppressing the banner even though calibration age is effectively unknown. Is this the intended behavior (suppress when no record found), or should the `record == null` branch reset `calAgeBannerDismissed = false`? |

---

## Positive Observations

- **All six PM v1 findings are substantively resolved.** The two Medium findings (F-01 on COUNTING behavior, F-02 on SecurityException) are addressed in the implementation code and error handling table. All four Low findings are addressed with specific code or text changes.
- **Requirements traceability remains complete.** §11 maps every P0/P1/P2 requirement (REQ-CAPTURE-03, REQ-DETECT-05, REQ-CAL-05, REQ-SENSOR-07) to named technical components. No requirement is silently absent.
- **Scope compliance is confirmed.** No new user-visible features, UX behaviors, or data fields appear in v0.2-draft beyond what the REQ and FSPEC approve. REQ-CAPTURE-05 (export/CSV) remains correctly deferred to Phase 5.
- **Acceptance criteria fidelity is high.** All named acceptance tests from the FSPEC (AT-HIST-01 through AT-NAV-01, AT-SENSOR-01) remain present and are mapped to implementation strategies. None were narrowed, reinterpreted, or dropped.
- **PM F-04 race-condition mitigation is well-designed.** The two-step `calAgeBannerDismissed` flag approach in `onCalibrationCompleteFromHistory()` — suppress immediately, then clear once age is confirmed ≤ 30 — correctly prevents banner re-flash without permanently suppressing the banner.
- **PM F-05 expand/collapse fix is correct and better than the minimum.** Using `notifyItemChanged()` on only the two affected positions not only fixes the stated jank concern but provides a foundation for smooth expand/collapse at any list size.
- **"Bearing deleted" Snackbar text** is a clean resolution that adds context without departing from platform conventions.
- **The COUNTING-past-60s product rationale in §5.3** is well-articulated. The explanation — a gradual environmental drift near the 10% boundary should trigger rather than be suppressed by a timer reset — is a sound product decision consistent with the overall UX intent of REQ-CAL-05 ("recalibrate in a new environment anyway").

---

## Recommendation

**Approved with Minor Issues**

All P0 and P1 requirements are covered. Acceptance criteria are preserved. No scope creep. Iteration 1 PM findings are resolved.

**Required before implementation sign-off:**

1. **F-01 (Medium):** FSPEC-CAL-03 must be updated to add the "re-evaluate each frame after threshold" edge-case rule before engineering begins. The TSPEC cannot be the sole source of this approved product behavior; the FSPEC is the authoritative behavioral contract for implementation.

**Recommended but not blocking:**

2. **F-02 (Low):** Correct the §12 SE FSPEC-v2 F-04 resolution note to accurately reflect the v0.2 implementation (flag IS set immediately on RESULT_OK). The current note contradicts the method body and could confuse implementers.
3. **Q-01:** Complete the §12 TE TSPEC-v1 resolution table (add F-06, F-08, F-09 rows) or correct the header claim.
