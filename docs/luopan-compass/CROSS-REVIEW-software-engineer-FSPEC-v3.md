# Cross-Review: SE — FSPEC-luopan-p2-true-north-capture (Iteration 3)

**Reviewer role:** Senior Software Engineer
**Document reviewed:** FSPEC-luopan-p2-true-north-capture.md v0.3-draft; REQ-luopan-p2-true-north-capture.md v0.3-draft
**Date:** 2026-04-24
**Previous recommendation:** Need Attention

---

## Resolution of Scoped Findings (Iteration 2 High + Medium)

The PM addressed all six items called out in the iteration 2 review scope. The verification status of each is documented below.

| Prior ID | Severity | Finding Summary | Iteration 3 Status |
|----------|----------|-----------------|--------------------|
| N-01 | High | `calibration_version` REQ/FSPEC type conflict | **Resolved** — see §V-01 |
| N-02 | Medium | `lat`/`lon` Double? vs Float REQ divergence | **Resolved** — see §V-02 |
| N-03 | Medium | `Clock`/`TimeSource` duplication without guidance | **Resolved** — see §V-03 |
| N-05 | Medium | `onResume` lifecycle trigger for location chain | **Partially resolved** — see §V-05 |
| N-06 | Medium | AT-A-01 measurement mechanism unspecified | **Resolved** — see §V-06 |
| N-07 | Medium | AT-C-02 `±1 day tolerance` present | **Resolved** — see §V-07 |
| N-09 | Medium | `interference_flag` derived from raw thresholds | **Resolved** — see §V-09 |

---

## Verification Details

### V-01 — N-01: `calibration_version` type conflict (High → Resolved)

REQ §5.3.1 now reads:

> `calibration_version` | String | Yes | WMM model identifier at capture time — provided by `MagneticFieldModel.getModelId()`. … It is NOT the calibration record schema version (`CalibrationRecord.calibration_schema_version` is a separate integer field … and MUST NOT be conflated with this field).

FSPEC §6.1 schema row and BR-02 both reflect `String` type with identical semantics. BR-02 includes a cross-reference note confirming "REQ §5.3.1 has been updated (v0.3-draft) to reflect `String` type and WMM model-name semantics for this field, resolving the prior REQ/FSPEC contradiction." The two documents are now fully aligned on type, value space, and semantics. **Resolved.**

### V-02 — N-02: `lat`/`lon` Double? vs Float (Medium → Resolved)

REQ §5.3.1 now reads:

> `latitude` | Double? | Conditional | … IEEE 754 double precision required for WMM computation accuracy (≈11 cm precision at 6 decimal places vs ≈11 m for Float).
> `longitude` | Double? | Conditional | … (same rationale)

FSPEC §6.1 schema rows and the normative note for `lat`/`lon` are consistent. Both documents agree on `Double?`. **Resolved.**

### V-03 — N-03: `Clock`/`TimeSource` guidance (Medium → Resolved)

FSPEC §6.2 now contains an explicit guidance note:

> Implementers SHOULD reuse the existing `TimeSource` interface if it is structurally compatible … If `TimeSource` is not structurally compatible … a new `Clock` interface is preferred over adding methods to `TimeSource`. The TSPEC author MUST decide which interface to use and document the decision in TSPEC §6 or §9.

This delegates the architectural decision to TSPEC authoring with a clear policy, rather than leaving it to ad-hoc engineering judgment. The FSPEC uses `Clock` as a conceptual name that may resolve at TSPEC time. **Resolved.**

### V-05 — N-05: `onResume` lifecycle trigger (Medium → Partially resolved)

The PM has resolved the WMM expiry check trigger. FSPEC §2.1 now specifies exactly two triggers for `MagneticFieldModel.isExpired()`:
- `Application.onCreate()` — once at app startup
- `Activity.onResume()` when `northType == TRUE_NORTH`, debounced to at most once per 60 seconds

This directly addresses N-05's concern about repeated dialog flickers from screen rotation and permission-dialog-induced lifecycle transitions.

However, the location resolution chain at §2.4 step 1 still reads:

> "On foreground entry (Activity.onResume), the system initiates the location resolution chain"

The original N-05 finding covered both the expiry check and the location chain. N-06 of the v2 review specifically noted the lifecycle inconsistency with Phase 1's `onStart`/`onStop` sensor architecture. The location chain trigger is still `onResume` while Phase 1 registers sensors on `onStart`. A permission dialog invocation cycles `onPause` → `onResume`, meaning the location resolution chain restarts each time a permission dialog dismisses. This is the same architecture concern raised in iterations 1 and 2. See new finding **V3-N-01** below.

### V-06 — N-06: AT-A-01 measurement mechanism (Medium → Resolved)

AT-A-01 precise assertion now reads:

> Measured with Espresso `IdlingResource` — register an idling resource that idles when `headingTextView.text` has been updated to a value differing from the pre-tap value by ≥ (`D − 0.5°`). Assertion: elapsed wall-clock time from `performClick()` on the toggle to idle ≤ 250 ms (200 ms + 50 ms tolerance on the reference device class…). Alternatively: use Robolectric with `ShadowLooper.runToEndOfTasks()` and verify the text update is processed within 1 UI loop.

This matches the REQ §8 Scenario A measurement definition. The test is now mechanically implementable. **Resolved.**

### V-07 — N-07: AT-C-02 `±1 day tolerance` (Medium → Resolved)

AT-C-02 now reads:

> `FakeClock` injected and set to exactly `15 × 86 400 000 ms` after the cache timestamp … displayed age string equals `R.string.location_cache_age_label` formatted with `15` (whole-day floor division: `floor(elapsed_ms / 86 400 000)`). `FakeClock` eliminates DST and midnight boundary flakiness — no ±1 day tolerance is required or permitted.

The ±1 day tolerance is removed. The test is deterministic and uses `FakeClock`. **Resolved.**

A minor inconsistency between §2.3 step 7 and AT-C-02 is flagged as a new low-severity finding (**V3-N-05**) below — the formulas are mathematically equivalent but use different intermediate units.

### V-09 — N-09: `interference_flag` derivation (Medium → Resolved)

All three locations from the N-09 finding now consistently tie `interference_flag` to `InterferenceState`:

- §2.5 step 3b: "If `InterferenceState` is `MODERATE` or `WARNING` at the instant of the tap" triggers the pre-capture warning
- §2.5 step 3b normative note: "`interference_flag`… is set from interference metrics only… NOT from `OverallConfidence`"
- BR-10: "`interference_flag`… is `true` when `InterferenceState` is `MODERATE` or `WARNING`… NOT derived from raw magnitude or inclination thresholds directly"
- §6.1 `interference_flag` column: "1 if `InterferenceState` was `MODERATE` or `WARNING` at the instant of capture; 0 otherwise. NOT derived from raw deviation thresholds directly"
- §6.1 normative note: "`OverallConfidence.POOR` alone does not set this flag"

REQ §5.3.1 is fully aligned: "`true` if `InterferenceState` was `MODERATE` or `WARNING` at capture time." **Resolved.**

---

## New Findings in v0.3-draft

| ID | Severity | Section | Finding | Recommendation |
|----|----------|---------|---------|----------------|
| V3-N-01 | Medium | §2.4 step 1 | **Location resolution chain still triggers on `onResume` (N-05 remnant — location chain leg unresolved).** The WMM expiry check trigger has been correctly fixed (§2.1). However, §2.4 step 1 still reads "On foreground entry (Activity.onResume), the system initiates the location resolution chain." Phase 1's `CompassActivity` registers and deregisters sensors in `onStart`/`onStop`, deliberately avoiding `onResume`/`onPause` to prevent spurious re-registration during permission dialogs (TSPEC Phase 1 §2.1 rationale). Initiating the GPS location chain in `onResume` means every permission dialog dismissal (which fires `onPause` → `onResume`) restarts the entire location resolution sequence, including the 10-second GPS fix timeout from step 9. This creates multiple overlapping `LocationManager.requestLocationUpdates` registrations if the user is navigating through permission dialogs. | Change `Activity.onResume` in §2.4 step 1 to `Activity.onStart`, matching the Phase 1 lifecycle architecture. The `onStart`/`onStop` pair is already the established pattern and avoids the permission-dialog re-entry problem. |
| V3-N-02 | Medium | §6.1, REQ §5.3.1 | **`altitude_m` type conflict between REQ and FSPEC.** REQ §5.3.1 defines `altitude_m` as `Float` (non-nullable, Conditional). FSPEC §6.1 defines `alt_m` as `Double?` (Nullable=Yes, SQLite type `REAL`). Both documents also differ on field name (`altitude_m` vs `alt_m`). The `Double?` in the FSPEC is internally consistent with §2.4 step 5 which specifies altitude as "double, meters" in the location cache. However, the approved REQ currently says `Float` and the field name `altitude_m`. A Room entity implemented against the REQ would have a `Float altitude_m` column; one implemented against the FSPEC would have a `Double? alt_m` column — a direct schema incompatibility. | PM should issue a REQ errata for §5.3.1 to (a) change `altitude_m` type from `Float` to `Double?` for WMM accuracy consistency (same argument that applies to lat/lon), and (b) confirm the column name `alt_m`. Until the REQ is updated, the TSPEC cannot be authored without an undocumented judgment call on this field. |
| V3-N-03 | Medium | §6.1, REQ §5.3.1 | **`display_mode` required/nullable conflict between REQ and FSPEC.** REQ §5.3.1 defines `display_mode` as `Enum: MODERN, LUOPAN, SIGHTING` with `Required = Yes`. FSPEC §6.1 defines `display_mode` as `String?` with `Nullable = Yes` and constraint `"MODERN" or NULL`. The FSPEC rationale for nullable is that `"MODERN"` is the only Phase 2 value and the column is included now "to avoid a schema migration when Phase 3 adds Luopan Mode" — this is a reasonable forward-compatibility argument. However, the REQ does not acknowledge this nullable semantic; an engineer reading the REQ would make the column NOT NULL. Additionally, the REQ enum value `SIGHTING` implies a third display mode (Phase 5?) that is not discussed anywhere in Phase 2 scope, while the FSPEC omits `SIGHTING` entirely. The two documents are contradictory on both nullability and the set of permitted enum values. | PM should issue a REQ errata for §5.3.1: update `display_mode` to `String?` (Nullable, default `"MODERN"` in Phase 2) and align the permitted values with FSPEC: `{"MODERN", "LUOPAN"}` for Phase 2–3 scope, deferring `SIGHTING` to Phase 5 (or explicitly noting `SIGHTING` is the Phase 5 addition). |
| V3-N-04 | Low | §2.3 step 7, AT-C-02 | **Cache age formula uses different intermediate units in §2.3 vs AT-C-02.** §2.3 step 7 specifies: "N is floor(cache_age_hours / 24)." AT-C-02 specifies: "floor(elapsed_ms / 86 400 000)." These are mathematically equivalent (since cache_age_hours = elapsed_ms / 3_600_000 and floor(elapsed_ms / 3_600_000 / 24) = floor(elapsed_ms / 86_400_000)). However, an engineer implementing §2.3 might first compute `cache_age_hours` as a floating-point intermediate and then divide by 24, introducing a double floating-point rounding step absent from the AT-C-02 formula. An engineer working directly from the AT-C-02 specification would use integer milliseconds throughout. Additionally, the REQ §8 Scenario C states "The days boundary is midnight UTC," which is a calendar-aware semantic (the day resets at UTC midnight, regardless of elapsed duration). The AT-C-02 formula `floor(elapsed_ms / 86 400 000)` is a pure duration calculation that does NOT anchor to UTC midnight — these can diverge by 1 day across a midnight boundary. The REQ and FSPEC are not fully aligned on which semantics are intended. | Standardize §2.3 step 7 to use the millisecond formula: "N = floor((Clock.nowMs() − cache.timestamp_ms) / 86 400 000)." Remove the intermediate `cache_age_hours` step. For the midnight-UTC boundary question, clarify whether the intent is calendar days (reset at UTC midnight) or duration days (elapsed ms / 86 400 000); if the latter, update REQ §8 Scenario C to remove the "midnight UTC" qualifier to avoid confusion. |
| V3-N-05 | Low | §1.3, REQ §10.2 | **§1.3 cross-reference "§10.2 of TSPEC Phase 1" still incorrect (N-04 from iteration 2, unresolved).** The precondition in §1.3 reads: "the `calibration_records` table is not altered (see §10.2 of TSPEC Phase 1)." TSPEC Phase 1 §10.2 covers heading update rate (REQ-NFR-02). The database schema is in TSPEC Phase 1 §9. This reference has been incorrect since v0.1-draft and was not addressed in v0.2 or v0.3. | Change "§10.2" to "§9" in §1.3. One-line fix. |
| V3-N-06 | Low | §2.4 step 1, §5.3, REQ §5.1 | **"GPS fix available within the last 60 seconds" freshness window conflicts with REQ-NORTH-03 (N-08 from iteration 2, unresolved).** §2.4 step 1 defines "current GPS fix" as "a GPS fix available within the last 60 seconds." The state machine at §5.3 encodes `GPS_FRESH` as "GPS fix received in last 60 s." REQ-NORTH-03 (v0.3-draft) defines "current GPS fix" as "a fix received after the app opened in the current session" with no time window. A fix received 61 seconds after the session started passes the REQ criterion but fails the FSPEC criterion, causing True North to fall back to the cached location unnecessarily. This was identified in iteration 1 (F-14) and iteration 2 (N-08) and has not been addressed in either v0.2 or v0.3-draft. | Either align §2.4 step 1 and §5.3 to the REQ definition ("fix received after the app opened in the current session"), or raise a change request against REQ-NORTH-03 to adopt the 60-second window. The discrepancy must be resolved before implementation. |
| V3-N-07 | Low | §6.3, REQ §5.1 | **`MagneticFieldModel` parameter names differ between REQ and FSPEC (N-12 from iteration 2, unresolved).** REQ §5.1 interface uses `latDeg`, `lonDeg`, `altM`; FSPEC §6.3 uses `lat`, `lon`, `altMeters`. Also unresolved from iteration 2: FSPEC §6.3 adds `getModelId()` and `isExpired()` not present in the REQ minimum interface (N-13). Both were Low findings that have not been addressed. | Align FSPEC §6.3 parameter names with REQ §5.1 (`latDeg`, `lonDeg`, `altM`). Add `getModelId()` and `isExpired()` to the REQ §5.1 interface definition via errata, or add a note in §6.3 that these extend the REQ minimum interface and will be reflected in the next REQ revision. |
| V3-N-08 | Low | §6.1 | **`notes` nullable vs empty-string semantics still ambiguous (N-18 from iteration 1, never fully resolved).** FSPEC §2.5 step 6 says "empty by default" (implying `""`), but §6.1 schema shows `Nullable = Yes` and `Constraint = "Length 0–1000 or NULL"`. The notes column on a row without notes is either `""` or `NULL` — these behave differently in a Phase 4 filter (`WHERE notes IS NULL` vs `WHERE notes = ''`). | Declare a single canonical semantic: recommend `NULL` when no notes entered (cleaner SQL semantics and consistent with the schema definition). Update §2.5 step 6 from "empty by default" to "null by default." |

---

## Summary of All Open Findings

### Findings resolved in v0.3-draft

| Iteration 2 ID | Finding | Status |
|---------------|---------|--------|
| N-01 | `calibration_version` REQ/FSPEC type conflict | Resolved |
| N-02 | `lat`/`lon` Double? vs Float | Resolved |
| N-03 | `Clock`/`TimeSource` guidance | Resolved |
| N-05 | WMM expiry check trigger | Resolved (expiry check only) |
| N-06 | AT-A-01 measurement mechanism | Resolved |
| N-07 | AT-C-02 `±1 day tolerance` | Resolved |
| N-09 | `interference_flag` derivation | Resolved |

### Findings remaining open across all iterations

| Iteration 2 ID | Severity | Section | Summary | Notes |
|---------------|----------|---------|---------|-------|
| N-05 (remnant) | Medium | §2.4 step 1 | Location chain triggers on `onResume` | See V3-N-01 |
| N-08 | Low | §2.4 step 1, §5.3 | 60-second GPS freshness window not in REQ | See V3-N-06 |
| N-10 | Low | §2.4 step 6, §6 | Location cache storage mechanism unspecified | Not addressed in v0.3 — Low, deferred to TSPEC |
| N-11 | Medium | §2.3, §5.1, AT-C | Main-screen "Using last known location (N days ago)" advisory absent | Unresolved — AT-C-02 only asserts info panel, not main screen |
| N-12 | Low | §6.3, REQ §5.1 | Parameter name mismatch | See V3-N-07 |
| N-13 | Low | §6.3 | `getModelId()`/`isExpired()` not in REQ | See V3-N-07 |
| N-14 | Low | §6.3 | `epochYears` formula placement | Not addressed in v0.3 |
| N-15 | Low | AT-NFR | NFR reference device not pinned | Not addressed in v0.3 |

> **Note on N-11:** REQ §8 Scenario C states "UI shows 'Using last known location (N days ago)'." The FSPEC places the "N days ago" label only in the declination info panel (§2.3) and the state machine table (§5.3). No main compass screen UI element is specified for this string. AT-C-02 asserts only the info panel content. The main-screen advisory string required by REQ Scenario C remains unspecified in the FSPEC. An implementation engineer will not know where to render it. This was raised as N-11 in iteration 2 and is still unresolved in v0.3-draft.

### New findings introduced in v0.3-draft

| ID | Severity | Section | Summary |
|----|----------|---------|---------|
| V3-N-01 | Medium | §2.4 step 1 | Location chain still on `onResume` (N-05 remnant) |
| V3-N-02 | Medium | §6.1, REQ §5.3.1 | `altitude_m` type conflict: REQ=Float, FSPEC=Double? |
| V3-N-03 | Medium | §6.1, REQ §5.3.1 | `display_mode` nullability and enum values conflict |
| V3-N-04 | Low | §2.3 step 7, AT-C-02 | Cache age formula uses different units; REQ midnight-UTC vs FSPEC duration-floor |
| V3-N-05 | Low | §1.3 | TSPEC §10.2 cross-reference still incorrect (should be §9) |
| V3-N-06 | Low | §2.4, §5.3, REQ §5.1 | 60-second GPS freshness window conflicts with REQ-NORTH-03 |
| V3-N-07 | Low | §6.3, REQ §5.1 | Parameter names differ; `getModelId()`/`isExpired()` not in REQ |
| V3-N-08 | Low | §6.1, §2.5 | `notes` empty-string vs NULL semantic unresolved |

---

## Positive Observations in v0.3-draft

- The `calibration_version` resolution is thorough: the REQ now contains the canonical definition with semantics, a disambiguation note distinguishing it from `CalibrationRecord.calibration_schema_version`, and concrete examples. The FSPEC BR-02 cross-reference closes the loop. This eliminates the highest-priority blocker from iteration 2.
- The `Clock`/`TimeSource` guidance in §6.2 is pragmatic and actionable: it gives the TSPEC author a clear policy and explicit deference point, rather than mandating an architectural decision that belongs in the TSPEC.
- AT-C-02 is now a well-formed, deterministic test: `FakeClock` injection, exact millisecond setup, exact string resource assertion, and explicit prohibition of tolerance. This is a model for how the other acceptance tests should be written.
- `interference_flag` semantics are now coherent across all five locations where the flag is referenced. The explicit "NOT from raw deviation thresholds" clause in BR-10 and §6.1 directly prevents the hysteresis-window disagreement identified in prior reviews.
- The WMM expiry check trigger specification in §2.1 is precise and technically sound: separating `onCreate` (unconditional) from `onResume` (conditioned on `northType == TRUE_NORTH` with 60-second debounce) cleanly handles screen rotation, dialog lifecycle, and session-start scenarios.

---

## Recommendation: Need Attention

Three new Medium findings (V3-N-01, V3-N-02, V3-N-03) and the still-open N-11 from iteration 2 require attention before TSPEC authoring.

**V3-N-02** (`altitude_m` Float vs Double?) and **V3-N-03** (`display_mode` nullability/enum) are new REQ/FSPEC type conflicts analogous to the N-01 and N-02 issues resolved in v0.3 — they will produce the same ambiguous schema if left open for TSPEC. **V3-N-01** (location chain `onResume`) will produce a latent bug where permission dialog cycling restarts the GPS update chain repeatedly. **N-11** (main-screen cached-location advisory) means the REQ acceptance criterion for Scenario C cannot be verified by the AT-C tests as written.

The document has improved substantially across three iterations. The remaining Medium items are narrower in scope than prior iterations — two are one-line REQ errata (`altitude_m` type, `display_mode` nullable) and one is a single word change in §2.4 (`onResume` → `onStart`). Resolving these four Medium items would clear the path to TSPEC authoring. The Low findings are non-blocking but should be batched into a v0.4-draft pass before TSPEC sign-off.
