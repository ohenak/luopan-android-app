# Cross-Review: TE — REQ-luopan-p2-true-north-capture (Iteration 2)

**Reviewer role:** Senior Test Engineer  
**Document reviewed:** REQ-luopan-p2-true-north-capture.md v0.2-draft  
**Date:** 2026-04-24  
**Previous recommendation:** Need Attention  

---

## Summary

The PM has resolved all five High findings and all five Medium findings from Iteration 1 at a structural level. Scenario G is present with explicit pass/fail criteria. The static WMM2025 test vector is pinned. The `MagneticFieldModel` injectable interface is fully specified in §5.1 and §10.3. REQ-CAPTURE-04 now includes a byte-level encryption verifiability criterion. REQ-NFR-08 has a named measurement method and reference device class. The document is substantially improved.

Three new findings require attention before FSPEC authoring: (1) Scenario C names the string resource key and mentions "injecting a fixed clock" but does not define a `Clock` interface or injection point, leaving the test harness author to infer the design — this is partially resolved but has a residual gap; (2) REQ-NORTH-02 specifies the fallback label as a prose description rather than a named string resource, leaving Q-02 from Iteration 1 unaddressed; (3) the `north_type` enum in the BearingRecord schema includes `GRID` but no scenario or constraint asserts that GRID cannot be captured in Phase 2, leaving Q-05 from Iteration 1 unaddressed and creating a risk of an out-of-scope code path being untested.

One additional observation: REQ-NFR-08 changed the start event from `Activity.onCreate()` (TE-REQ-04 recommendation) to `Application.onCreate()`, which is not directly measurable by AndroidX Macrobenchmark's standard metrics — this is a minor but noteworthy mismatch that should be clarified before TSPEC authoring.

---

## Resolution of Prior Findings

| Prior ID | Resolution | Notes |
|----------|-----------|-------|
| TE-REQ-01 | Resolved | Scenario C now names `R.string.location_cache_age_label` with a `%d` integer substitution, states the assertion as `"Using last known location (15 days ago)"` under injected clock, and specifies midnight-UTC as the days boundary. The clock injection mechanism is referenced ("injecting a fixed clock") but the interface name is not specified — see new finding TE-REQ-11. |
| TE-REQ-02 | Resolved | A static WMM2025 test vector table is embedded after Scenario B with latitude 40.0°N, longitude −105.0°W, altitude 0 m, epoch 2025.5. Expected values: declination +8.93° E (±0.1°), inclination +66.0° (±0.5°), total field 52 300 nT (±200 nT). Source cited as "WMM2025 Technical Note (NOAA, 2024)". Tests are explicitly forbidden from calling the live NOAA calculator. The TE recommendation to place this vector as a file in `app/src/test/resources/` was not followed, but embedding the table directly in the REQ is an acceptable equivalent — the TSPEC can codify the fixture file. |
| TE-REQ-03 | Resolved | REQ-CAPTURE-04 now includes an explicit verifiability criterion: opening the bearing DB without the SQLCipher key must throw an exception or produce a file whose first 16 bytes do not match the standard SQLite magic string. This mirrors the Phase 1 PROP-PERSIST-02 contract exactly. |
| TE-REQ-04 | Resolved | REQ-NFR-08 now specifies start event (`Application.onCreate()` is entered), end event (compass heading rendered with non-STABILIZING confidence), reference device class (≥2 GB RAM, Snapdragon 600-series, API 26+), and measurement method (AndroidX Macrobenchmark `StartupMode.COLD`/`WARM`, ≥5 iterations, median reported). A minor residual issue with the start-event definition is noted in TE-REQ-12. |
| TE-REQ-05 | Resolved | Scenario G is present with explicit given/when/then and separate pass/fail criteria blocks. It covers: no crash, correct fallback to Scenario D, null GPS coordinates in saved BearingRecord, no automatic permission re-request, and a Phase 4 note on history display. |
| TE-REQ-06 | Resolved | Scenario A now specifies measurement as wall-clock time from `View.performClick()` to first frame with value differing by ≥ (declination − 0.5°), measured via Espresso `IdlingResource` or Macrobenchmark frame timing, with ±50 ms tolerance (pass ≤250 ms). The functional assertion (value changes by declination) is separate from the timing assertion. Note: the measurement point is the UI layer (TextView) rather than the ViewModel emission layer as the TE recommended — this is acceptable but introduces rendering-pipeline variability; the Espresso `IdlingResource` requirement should mitigate flakiness. |
| TE-REQ-07 | Resolved | §5.3.1 BearingRecord Field Definitions table now enumerates all fields including `field_deviation_pct`, `inclination_deviation_deg`, and `interference_flag` with explicit descriptions. Scenario E references all fields. The discrepancy between the phase REQ schema and Scenario E is closed. |
| TE-REQ-08 | Resolved | Risk P2-R3 now includes explicit boundary behavior: exactly 30 days old (to the second) is valid; 30 days + 1 second is expired. The comparison is evaluated in wall-clock seconds from `cached_at` UTC. The boundary is stated as strictly-greater-than (30 days + 1 second triggers expiry). |
| TE-REQ-09 | Resolved | §5.1 defines the `MagneticFieldModel` interface with method signatures. §10.3 mandates that `InterferenceDetector` accepts it via constructor injection, that `FakeMagneticFieldModel` is placed in the `testFixtures` source set, and that no static/singleton references to `Wmm2025Model` are permitted. Scenario F reiterates the injection contract. |
| TE-REQ-10 | Resolved | REQ-CAPTURE-02 now explicitly states that the ≤3 taps budget applies to subsequent captures (after the privacy dialog has been acknowledged once), and that the first-ever capture adds 1 tap for the mandatory privacy dialog acknowledgement. |

---

## New Findings

| ID | Severity | Section | Finding | Recommendation |
|----|----------|---------|---------|----------------|
| TE-REQ-11 | Medium | §8 Scenario C | Scenario C says the acceptance criterion "is assertable by injecting a fixed clock" but does not name the clock abstraction interface, its injection point, or which source set provides the fake. Without a named `Clock` (or `TimeSource`) interface and an explicit contract that the age-formatting component accepts it via constructor or DI, the TSPEC author must invent the design. This risks the clock injection being added late or inconsistently (e.g., as a test-only static field rather than a proper seam). By contrast, §10.3 fully specifies `MagneticFieldModel` and `FakeMagneticFieldModel` — the same level of specificity is missing for the clock seam. | Add to §10 (or annotate Scenario C): name the clock interface (e.g., `interface Clock { fun nowUtcMillis(): Long }`), specify that the location-age formatting component accepts it via constructor injection, and require `FakeClock` in the `testFixtures` source set. This mirrors the completeness of the §10.3 MagneticFieldModel contract. |
| TE-REQ-12 | Minor | §5.4 REQ-NFR-08 | The start event is defined as "`Application.onCreate()` is entered." AndroidX Macrobenchmark's `StartupMode.COLD` measures from process fork (before `Application.onCreate()`), and its built-in `timeToFullDisplay` / `timeToInitialDisplay` metrics are anchored to the Activity display frame, not to `Application.onCreate()`. A test using Macrobenchmark as prescribed cannot directly observe the `Application.onCreate()` entry point without custom tracing instrumentation. The TE recommendation was `Activity.onCreate()` specifically because that event is instrumentable via `ActivityScenario` or Macrobenchmark's custom metric via `Trace.beginSection`. | Clarify whether "start event = `Application.onCreate()` is entered" means the measurement is to be implemented via a custom `Trace.beginSection("app_startup")` call at `Application.onCreate()` and a corresponding `Trace.endSection()` at first heading render, or whether the start event should be revised to `Activity.onCreate()` to align with Macrobenchmark's native metric anchors. Document the trace tag name if custom tracing is intended. |
| TE-REQ-13 | Minor | §5.3.1, §8 Scenario E, §6 | The `north_type` field in the BearingRecord schema is typed as `Enum: TRUE, MAGNETIC, GRID` (§5.3.1). REQ-DECL-03 (Grid north) is deferred to Phase 5 (§6), and the north type toggle in Phase 2 offers only True N / Magnetic N. Q-05 from the Iteration 1 review asked whether Scenario E should assert that `north_type` cannot be `GRID` in Phase 2 — this question is not addressed in v0.2-draft. Without an explicit negative constraint, the TSPEC author cannot write a test asserting that GRID is unavailable as a capture option, and engineering has no explicit requirement preventing accidental implementation of a GRID capture path. | Add to Scenario E (or as a new acceptance criterion on REQ-CAPTURE-01): "In Phase 2, `north_type=GRID` MUST NOT be capturable — the UI MUST NOT offer GRID as a north reference option, and any attempt to save a BearingRecord with `north_type=GRID` via the production capture flow is out of scope." This constraint should also appear in §6 alongside the REQ-DECL-03 deferral note. |
| TE-REQ-14 | Minor | §5.1 REQ-NORTH-02 | REQ-NORTH-02 specifies the fallback label as the prose string `"Android model — may be less accurate"`. Q-02 from Iteration 1 asked whether this is an exact string or a description, and if exact, it must be codified as a string resource. v0.2-draft does not name a string resource ID for this label. The label in REQ-NORTH-02 is not aligned with the pattern established for the cache-age label in Scenario C (`R.string.location_cache_age_label`). If the string is hardcoded in the View layer without a resource key, it cannot be asserted in a JVM unit test without view binding. | Add a string resource name (e.g., `R.string.wmm_fallback_source_label`) to REQ-NORTH-02 for the "Android model — may be less accurate" label, consistent with the Scenario C string resource approach. This allows the TSPEC to assert the fallback label without instrumentation. |

---

## Positive Observations

- The `MagneticFieldModel` interface specification in §5.1 and §10.3 is complete and exemplary: named interface, method signatures with units in comments, concrete implementations named, injection point specified, `testFixtures` fake mandated. This directly addresses the untestability problem raised in TE-REQ-09.
- Scenario G (GPS permission denial) is thorough: it covers crash prevention, fallback path, null GPS fields in the saved record, re-request suppression, and a Phase 4 note. The separate explicit pass/fail blocks are valuable.
- The 30-day boundary specification in Risk P2-R3 is precise (seconds granularity, inclusive lower bound, exclusive upper bound) and directly testable with an injected clock.
- §10.2 (Room DB migration constraints) is new and substantive: it prohibits `fallbackToDestructiveMigration` in production builds, mandates a specific `Migration(1, 2)` DDL, and requires an instrumented `MigrationTestHelper` test. This is exactly the right level of constraint for a TE to be able to write a migration property test.
- The `interference_flag` disambiguation in §5.3.1 and Scenario E (flag driven by `InterferenceState`, not `OverallConfidence`) resolves Q-03 from Iteration 1 clearly.
- Risk P2-R4 (Room DB migration) is a new, well-specified risk that was absent from v0.1-draft. Its inclusion demonstrates that the revision cycle caught a real implementation risk.

---

## Recommendation: Approved with Minor Issues

All five prior High findings are resolved. All five prior Medium findings are resolved. The four new findings are three Minor and one Medium; none blocks FSPEC authoring. The PM and FSPEC author should address TE-REQ-11 (clock seam contract) in the FSPEC's technical constraints section, and TE-REQ-13 (GRID north negative constraint) can be closed with a single sentence in §6. TE-REQ-12 and TE-REQ-14 are informational and can be resolved during TSPEC authoring.

**Conditions for FSPEC authoring to proceed:**
1. TE-REQ-11: Document the `Clock` interface name and injection requirement (in FSPEC §10 or equivalent) before the TSPEC is authored — otherwise the clock seam will be underdefined.
2. TE-REQ-13: Add the GRID-negative constraint to Scenario E or REQ-CAPTURE-01 — this is a one-line addition.
3. TE-REQ-12 and TE-REQ-14: Resolve during TSPEC authoring; do not block FSPEC.
