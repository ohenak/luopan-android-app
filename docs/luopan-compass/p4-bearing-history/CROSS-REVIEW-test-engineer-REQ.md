# Cross-Review: test-engineer — REQ

**Reviewer:** test-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/REQ-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|-------------|
| F-01 | High | **Drift detection trigger condition is undertestable — "stable, low-interference location" is undefined.** REQ-CAL-05 and Scenario E both require drift detection to fire when field magnitude differs from calibration-time expected by >10% for >60 consecutive seconds, but only at a "stable, low-interference location." Stability and low-interference are not defined quantitatively. A test cannot be written without knowing the specific thresholds: what deviation from WMM-expected constitutes "low interference" for the drift-detection gate, and how is "stable" measured (acceleration variance threshold, time window)? Without these thresholds the 60-second timer start condition is untestable in isolation. | §5.3 REQ-CAL-05, Scenario E |
| F-02 | High | **Undo toast interaction contract is incomplete — tap-after-expiry and partial-delete sequences are missing.** Scenario C specifies: swipe left → record removed → undo toast for 5 s → tap Undo restores record. Three critical cases are absent: (a) user does NOT tap Undo within 5 s — is the deletion committed to the database at the moment of swipe or at toast expiry? (b) user swipes a second record while the first Undo toast is still visible — how many toasts are shown, and which deletion can be undone? (c) user taps Undo after the toast has visually disappeared but before 5 s has elapsed due to animation — does Undo still work? Without these, the delete/undo state machine cannot be fully tested. | §5.1 REQ-CAPTURE-03, Scenario C |
| F-03 | High | **"First launch after Phase 4 install" trigger for sensor_profile.json is ambiguous and untestable.** REQ-SENSOR-07 says the file is written "on first launch after Phase 4 installs." This is undefined for: (a) what constitutes a "Phase 4 install" (app version number, migration flag, BuildConfig constant?); (b) whether re-install with data cleared counts as another "first launch"; (c) whether the file is overwritten on a subsequent launch if it is missing (e.g., user deleted it). A test must know exactly what state to inspect (a SharedPreferences flag? a file existence check? a DB migration version?) to assert "first launch only." | §5.4 REQ-SENSOR-07, Scenario F |
| F-04 | High | **Confidence cap rule for calibration age >30 days is not testable from the phase REQ alone — it references §8.1 without quoting the rule.** Scenario D states: "Confidence is capped at 'Moderate' until recalibration occurs (calibration age >30 days condition per master REQ §8.1)." The phase REQ does not reproduce the exact capping rule (which confidence levels are affected, at what threshold). A tester reading only this document cannot write a test for the cap without cross-referencing the master REQ. Phase REQs must be self-contained or quote the rule inline. | §7 Scenario D |
| F-05 | Medium | **No negative scenario for the drift detection prompt — it must NOT fire when interference (not drift) causes the deviation.** The REQ distinguishes between interference (field deviation exceeds 25% → Poor confidence → REQ-DETECT-01) and drift (field deviation >10% for >60 s at a stable, low-interference location). However, there is no explicit negative acceptance criterion stating that the drift prompt must NOT appear when the field deviation is caused by a proximate interference source (e.g., when REQ-DETECT-01 has already set confidence to Poor). Without this negative case, the implementation could incorrectly double-fire both the interference warning and the drift prompt for the same event. | §5.3 REQ-CAL-05 |
| F-06 | Medium | **Search behavior under no-match and empty-query conditions is absent.** REQ-CAPTURE-03 and Scenario A specify search by name but do not state: (a) what is displayed when the search query matches zero records (empty state message? empty list?); (b) what happens when the search field is cleared — does the full list restore immediately or require a tap? (c) is search case-insensitive? (d) is search a prefix match, substring match, or fuzzy? Each of these is a distinct test case; all are untestable without the spec. | §5.1 REQ-CAPTURE-03 |
| F-07 | Medium | **Scroll performance threshold is risk-acknowledged but not made measurable.** Risk P4-R2 acknowledges that with hundreds of records the history screen must remain performant and that "smooth scrolling and no jank" is required, but this is a non-functional requirement stated in prose in the risks section rather than as a measurable acceptance criterion. "No jank" is not testable. A specific threshold is needed: e.g., "frame time must not exceed 16 ms during fling scroll at 60 fps on a mid-range device with 500 records." Without this, performance tests cannot have a pass/fail gate. | §8 Risk P4-R2 |
| F-08 | Medium | **Recalibration prompt dismissal is specified as one-time only by omission, but persistence of dismissal is not stated.** REQ-CAL-05 says prompts are dismissible and the app remains usable. It does not state: (a) once the age-based banner is dismissed, does it reappear on the next app launch (same calibration still >30 days old), every launch, or only once per calibration? (b) does a dismissed drift-detection prompt suppress re-triggering for a cooldown period? This is a testable behavior that is currently implied but not specified, making it impossible to write a regression test. | §5.3 REQ-CAL-05, Scenario D, Scenario E |
| F-09 | Medium | **No negative scenario for the interference flag — records captured at MODERATE or HIGH confidence must NOT show the "⚠ Captured under interference" badge.** REQ-DETECT-05 states the flag appears when interference_flag=true (captured under Poor confidence). There is no explicit AC stating the badge must be absent for MODERATE or HIGH confidence captures. Without a negative case, an implementation that shows the badge unconditionally on all records would pass the positive AC while silently exhibiting a defect. | §5.2 REQ-DETECT-05, Scenario B |
| F-10 | Medium | **"Tap to expand full record" acceptance criteria does not specify what fields are shown — the set of fields is testable but not enumerated here.** Scenario A states "expanding the record shows all fields including notes" but does not list what "all fields" means. The master REQ §6.6 REQ-CAPTURE-03 mentions: bearing, north type, confidence badge, date/time, and full record details. REQ-DETECT-05 specifies that the expanded record shows field_deviation_pct and inclination_deviation_deg specifically for flagged records, but does not clarify whether these fields are present (but empty/null) for non-interference records. An engineer cannot write a completeness assertion without an explicit field list. | §7 Scenario A, §5.2 REQ-DETECT-05 |
| F-11 | Low | **Scenario D uses a specific example ("31 days old") but does not assert the exact boundary — a 30-day-old calibration must NOT trigger the banner.** The requirement says ">30 days." The scenario uses 31 days as the Given but does not include a scenario where the calibration is exactly 30 days old and the banner must be absent. Without the boundary-negative case, an off-by-one in the ≥30 days vs >30 days comparison is not caught by the E2E test. | §7 Scenario D |
| F-12 | Low | **Scenario F does not specify the schema/format of sensor_profile.json precisely enough to write a content assertion.** The requirement lists the fields (device model + manufacturer, Android version + API level, sensor types + names, resolution, range per sensor) but does not specify JSON field names, the unit of resolution (µT/LSB?), or whether the file is pretty-printed or minified. A test that asserts content (rather than mere existence) needs the exact schema. | §5.4 REQ-SENSOR-07, Scenario F |
| F-13 | Low | **No negative scenario for sensor_profile.json network-accessibility.** Scenario F asserts the file is "not network-accessible" but does not specify how this is verified in an automated test. Asserting the absence of network transmission in an instrumented test requires a mock network layer or a no-internet test run; the mechanism is not referenced. If this is only a design-level guarantee, it should be stated as such rather than as part of an E2E test scenario. | §7 Scenario F |
| F-14 | Low | **History sort order is not verified at a boundary — the Scenario A Given uses 10 records but does not include a case with 0 records or 1 record.** Sorted-order logic is often correct for N>1 but broken for empty or single-element lists. The empty-history state (zero records) should have an explicit scenario: what is displayed (empty state view? message? illustration?). | §7 Scenario A, §5.1 REQ-CAPTURE-03 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For REQ-CAL-05 drift detection: what specific condition determines that the device is at a "stable, low-interference location"? Is there an acceleration-variance threshold, a field-deviation gate, or is this inferred from the existing REQ-DETECT-01 interference check (i.e., drift detection only runs when confidence is not already Poor)? |
| Q-02 | For the undo toast (REQ-CAPTURE-03, Scenario C): is the deletion committed to the database immediately on swipe, or only after the toast expires without a tap? What happens to the undo buffer if the user swipes two records in quick succession? |
| Q-03 | For the age-based recalibration banner: after the user dismisses it in one session, does it reappear on the next app launch (assuming calibration age still >30 days)? Is there a per-session or per-calibration suppression? |
| Q-04 | For the drift-detection prompt: is there a cooldown or re-arm period after dismissal, to prevent the banner from reappearing every 60 seconds if the drift condition persists? |
| Q-05 | What is the exact definition of "first launch after Phase 4 install" for REQ-SENSOR-07? Is this tracked via a migration version flag, a BuildConfig constant, or a first-run SharedPreferences key? If the user clears app data, does the sensor_profile.json get rewritten on the next launch? |
| Q-06 | Is search in the history screen case-insensitive? Substring or prefix only? What is the expected UX when the query matches zero records? |
| Q-07 | Does the expanded bearing record detail view show field_deviation_pct and inclination_deviation_deg for ALL records (with null/0 for non-interference records), or only when interference_flag=true? |
| Q-08 | For Scenario F's assertion that the file is "not network-accessible" — is this meant as an automated test assertion, or is it a design-level guarantee that should be verified by a security review rather than an instrumented test? |

---

## Positive Observations

- The distinction between interference (REQ-DETECT-01, immediate trigger) and drift (REQ-CAL-05, 60-second sustained trigger) is clearly articulated in Risk P4-R1 and reflected in the different prompt wording — this is good testability practice.
- Scenario D explicitly states the banner wording with the dynamic calibration age ("31 days old"), allowing an exact string assertion in an instrumented test.
- The 5-second undo toast duration is a concrete, measurable value enabling automated timing assertions.
- The requirement to write `sensor_profile.json` to internal storage (not transmitted) reflects an explicit non-transmission guarantee that maps cleanly to a data-at-rest test and an absence-of-network-call assertion.
- The interference flag field (`interference_flag=true`) being stored in the record data model rather than derived at display time is correct from a testability standpoint — it allows unit tests on the history ViewModel without needing to replay sensor conditions.
- Risk P4-R2 proactively identifies the performance risk for large lists and recommends an appropriate implementation pattern (RecyclerView virtualization), even if the associated NFR threshold still needs to be quantified (see F-07).

---

## Recommendation

**Need Attention**

> Five High findings (F-01, F-02, F-03, F-04) and five Medium findings (F-05, F-06, F-07, F-08, F-09, F-10) are present. The following must be resolved before FSPEC authoring begins:
>
> 1. **F-01** — Define the "stable, low-interference location" precondition for drift detection with measurable thresholds, or explicitly state that drift detection only activates when the existing interference check (REQ-DETECT-01) is NOT already in a Poor state.
> 2. **F-02** — Specify the undo commit timing (immediate vs. toast-expiry) and the multi-swipe undo behavior.
> 3. **F-03** — Define what constitutes a "first launch after Phase 4 install" in terms of a concrete app-state predicate (migration version, flag key, etc.).
> 4. **F-04** — Reproduce the exact confidence-cap rule inline in the phase REQ so it is self-contained.
> 5. **F-05** — Add an explicit negative AC: drift prompt must NOT fire when field deviation is already attributed to a proximate interference source (i.e., when REQ-DETECT-01 has set confidence to Poor).
> 6. **F-06** — Add search edge-case ACs: zero-match state, query-cleared behavior, and case/match-type rules.
> 7. **F-07** — Convert Risk P4-R2 into a measurable NFR with a frame-time threshold and a record-count test size.
> 8. **F-08** and **F-09** — Add negative ACs for prompt re-display on subsequent launches and for the absence of the interference badge on non-Poor records.
