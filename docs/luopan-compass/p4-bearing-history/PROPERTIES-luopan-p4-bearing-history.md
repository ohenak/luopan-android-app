# PROPERTIES-luopan-p4-bearing-history
## Phase 4: Bearing History, Recalibration Refinements, and Sensor Diagnostics

| Field | Value |
|-------|-------|
| **Version** | 0.1-draft |
| **Date** | 2026-04-27 |
| **Status** | Draft |
| **Phase** | 4 of 5 |
| **Source REQ** | REQ-luopan-p4-bearing-history v0.4-draft |
| **Source FSPEC** | FSPEC-luopan-p4-bearing-history v0.2-draft |
| **Source TSPEC** | TSPEC-luopan-p4-bearing-history v0.2-draft |
| **Source PLAN** | PLAN-luopan-p4-bearing-history v0.2-draft |
| **Cross-reviews addressed** | PM PLAN-v2 (F-01: Snackbar text); TE PLAN-v2 (F-01: B-2a forward ref; F-02: TSPEC snippet conflict; F-03: AT-CAL-03-B2 sequencing bug) |

---

## Purpose

This document defines the complete set of testable system properties for Phase 4. Each property is an observable invariant that the implementation must satisfy. Properties are derived from REQ acceptance criteria, FSPEC behavioral rules, and TSPEC design contracts. Cross-review findings that identify specification defects are captured as mandatory properties to lock down the correct behavior.

---

## Table of Contents

1. [Bearing History Properties](#1-bearing-history-properties)
2. [Recalibration Properties](#2-recalibration-properties)
3. [Sensor Logging Properties](#3-sensor-logging-properties)
4. [Navigation Properties](#4-navigation-properties)
5. [Coverage Matrix](#5-coverage-matrix)
6. [Gaps and Notes](#6-gaps-and-notes)

---

## Property Notation

> **PROP-{DOMAIN}-{NNN}:** {Component} **must** / **must not** {observable behavior} **when** / **given** {condition}.

Categories: **Functional** | **Contract** | **Data Integrity** | **Error Handling** | **Idempotency** | **Integration** | **Performance** | **Observability**

Levels: **Unit** | **Integration** | **E2E**

---

## 1. Bearing History Properties

### 1.1 List Ordering

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-HIST-001 | `BearingDao.getAllFlow()` must emit records ordered by `captured_at` descending (newest first) when called with records having distinct `captured_at` values. | Contract | Unit | `BearingDaoTest.kt` | First emitted item has the maximum `captured_at` of all records; last has the minimum. |
| PROP-HIST-002 | `BearingDao.getAllFlow()` must break ties in `captured_at` by `rowid` descending when two or more records share the same `captured_at` timestamp. | Contract | Unit | `BearingDaoTest.kt` | Of two records with equal `captured_at`, the one with the higher `rowid` appears first. |
| PROP-HIST-003 | `BearingHistoryFragment` RecyclerView must display the record with the maximum `captured_at` value at position 0 when the fragment is first launched. | Functional | E2E | `BearingHistoryFragmentTest.kt` | `onView(withId(R.id.recycler_history)).check(RecyclerViewItemCountAssertion(10))` and row 0 contains the text of the record named "Newest". |
| PROP-HIST-004 | `BearingDao.searchFlow(query)` must emit results ordered by `captured_at` descending, with `rowid` descending as a tiebreaker, identical to `getAllFlow()` ordering semantics. | Contract | Unit | `BearingDaoTest.kt` | Filtered results maintain newest-first order. |

### 1.2 Row Fields

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-HIST-010 | Each RecyclerView row must display: name (left-aligned primary text), bearing value + north type label (e.g., "045.2° True North"), confidence badge (colour chip), and locale-formatted date/time. | Functional | E2E | `BearingHistoryFragmentTest.kt` | Each of the four field views is non-null and non-empty for a seeded record. |
| PROP-HIST-011 | The expanded record panel must display: bearing value + north type, confidence level text label, `captured_at` as full ISO-8601 local time, name, and notes (empty string shows the label with empty value). | Functional | E2E | `BearingHistoryFragmentTest.kt` | After tap-to-expand, all five fields are visible and match the seeded record values. |

### 1.3 Expand / Collapse

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-HIST-020 | At most one RecyclerView row must be expanded at any time. | Functional | E2E | `BearingHistoryFragmentTest.kt` | After tapping row 2 when row 1 was expanded: row 1's detail panel is `GONE`; row 2's detail panel is `VISIBLE`. |
| PROP-HIST-021 | Tapping an already-expanded row must collapse it (detail panel transitions from `VISIBLE` to `GONE`). | Functional | E2E | `BearingHistoryFragmentTest.kt` | Second tap on the same row sets detail panel to `GONE`. |
| PROP-HIST-022 | `BearingAdapter.toggleExpanded()` must call `notifyItemChanged()` on at most two positions (the previously expanded row and the newly expanded row) and must not call `notifyDataSetChanged()`. | Contract | Unit | `BearingAdapterFormatTest.kt` | Spy on adapter: `notifyDataSetChanged` call count == 0; `notifyItemChanged` call count ≤ 2. |
| PROP-HIST-023 | Swiping an expanded row must collapse it and delete it without crashing. | Error Handling | E2E | `BearingHistorySwipeTest.kt` | After swipe on expanded row: no exception; RecyclerView count decremented by 1; Snackbar visible. |

### 1.4 Search Filtering

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-HIST-030 | `BearingDao.searchFlow(query)` must return records whose `name` field contains `query` as a case-insensitive substring for ASCII characters. | Functional | Unit | `BearingDaoTest.kt` | Query "north" matches "North Gate", "northeast", "Northing"; does not match "Southing". |
| PROP-HIST-031 | `BearingHistoryViewModel` must not invoke `searchFlow()` until at least 300 ms have elapsed since the last keystroke when the query is non-empty. | Functional | Unit | `BearingHistoryViewModelTest.kt` | Characters typed at 100 ms intervals produce no `searchFlow` call at 200 ms elapsed; exactly one call at 500 ms elapsed. |
| PROP-HIST-032 | `BearingHistoryViewModel` must restart the 300 ms debounce timer on every keystroke, so a keystroke at t=250 ms suppresses the timer from t=0 and fires at t=550 ms. | Functional | Unit | `BearingHistoryViewModelTest.kt` | No `searchFlow` call at t=300 ms; exactly one `searchFlow("no")` call at t=550 ms. |
| PROP-HIST-033 | `BearingHistoryViewModel` must restore `getAllFlow()` immediately (0 ms debounce) when the query is cleared to an empty string, and must not invoke `searchFlow()` on the clear event. | Functional | Unit | `BearingHistoryViewModelTest.kt` | After clearing query: `getAllFlow` emission received immediately; `searchFlow` call count unchanged. |
| PROP-HIST-034 | `BearingHistoryFragment` must show the "No bearings match your search" empty-state label and hide the RecyclerView when `searchFlow()` emits an empty list for a non-empty query. | Functional | E2E | `BearingHistoryFragmentTest.kt` | After query "zzz" and debounce: label visible; `recycler_history` has 0 items. |
| PROP-HIST-035 | `BearingHistoryViewModel` must retain the active search query across configuration changes (e.g., device rotation) using `SavedStateHandle` or equivalent ViewModel state. | Functional | Unit | `BearingHistoryViewModelTest.kt` | After ViewModel recreation, `searchQuery.value` equals the query set before recreation. |
| PROP-HIST-036 | `BearingHistoryViewModel` must clear the search query when the Fragment is destroyed (tab navigation), exposing an empty string on re-creation. | Functional | Unit | `BearingHistoryViewModelTest.kt` | New `BearingHistoryViewModel` instance has `searchQuery.value == ""`. |

### 1.5 Swipe-to-Delete Undo Lifecycle

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-HIST-040 | The swiped record must be deleted from the Room database immediately on swipe, before the Snackbar appears or times out. | Functional | E2E | `BearingHistorySwipeTest.kt` | `dao.getAll()` count == N−1 immediately after swipe callback fires (before Snackbar timeout). |
| PROP-HIST-041 | The Snackbar body text must be exactly "Bearing deleted" (not "Deleted"). | Data Integrity | E2E | `BearingHistorySwipeTest.kt` | Snackbar message string equals `context.getString(R.string.bearing_deleted)` which resolves to "Bearing deleted". |
| PROP-HIST-042 | The Snackbar must be shown for exactly 5 seconds using `setDuration(5000)`, not `Snackbar.LENGTH_LONG`. | Contract | E2E | `BearingHistorySwipeTest.kt` | Snackbar duration field equals 5000 ms at show time (verified via reflection or wrapper). |
| PROP-HIST-043 | Tapping "Undo" within the 5-second window must re-insert the deleted record into the database and cause it to reappear in the RecyclerView at its correct `captured_at`-ordered position. | Functional | E2E | `BearingHistorySwipeTest.kt` | After undo tap: `dao.getAll()` contains the record; RecyclerView item matches expected position by `captured_at`. |
| PROP-HIST-044 | Swiping a second record while the first Snackbar is still visible must: (1) permanently commit the first deletion, (2) dismiss the first Snackbar, (3) delete the second record immediately, and (4) show a new 5-second Snackbar for the second record only. | Functional | E2E | `BearingHistorySwipeTest.kt` | After second swipe: DB does not contain record A; DB does not contain record B; exactly one Snackbar visible for B. |
| PROP-HIST-045 | `BearingHistoryViewModel` must hold at most one pending undo record at a time; setting a second pending undo must replace (not stack) the first. | Contract | Unit | `BearingHistoryViewModelTest.kt` | After two calls to `deleteRecord()`, `hasPendingUndo()` is true and `undoDelete()` re-inserts only the second record. |
| PROP-HIST-046 | After an `ActivityScenario` close (simulating ViewModel destruction), the deleted record must be absent from the database on relaunch and no Snackbar must be shown. | Functional | E2E | `BearingHistorySwipeTest.kt` (AT-HIST-03-E) | New `ActivityScenario` launched: history list does not contain the deleted record; no Snackbar visible. |
| PROP-HIST-047 | `BearingHistoryViewModel.deleteRecord()` must set `pendingUndo` to the deleted record. | Contract | Unit | `BearingHistoryViewModelTest.kt` | After `deleteRecord(record)`, `hasPendingUndo() == true`. |
| PROP-HIST-048 | `BearingHistoryViewModel.undoDelete()` must clear `pendingUndo` and trigger a DAO `insert()` call. | Contract | Unit | `BearingHistoryViewModelTest.kt` | After `undoDelete()`, `hasPendingUndo() == false`; `FakeBearingDao.insertCallCount == 1`. |
| PROP-HIST-049 | `BearingHistoryViewModel.commitDelete()` must clear `pendingUndo` without triggering any DAO insert. | Contract | Unit | `BearingHistoryViewModelTest.kt` | After `commitDelete()`, `hasPendingUndo() == false`; `FakeBearingDao.insertCallCount == 0`. |

### 1.6 Interference Badge Presence / Absence

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-HIST-050 | The "⚠ Captured under interference" badge view must be `View.VISIBLE` (not `GONE` or `INVISIBLE`) for records with `interference_flag = true`. | Functional | E2E | `BearingHistoryFragmentTest.kt` | Badge view visibility == `View.VISIBLE` for the flagged record row. |
| PROP-HIST-051 | The "⚠ Captured under interference" badge view must be `View.GONE` (not `INVISIBLE`) for records with `interference_flag = false`. | Functional | E2E | `BearingHistoryFragmentTest.kt` | Badge view visibility == `View.GONE` for the clean record row. |
| PROP-HIST-052 | The expanded detail panel for a record with `interference_flag = true` must display `field_deviation_pct` as an integer percentage using truncation toward zero (stored `0.25` → displayed "25%"; no rounding up). | Data Integrity | E2E | `BearingHistoryFragmentTest.kt` (AT-HIST-05-C) | Field deviation text view shows "25%" for stored value 0.25. |
| PROP-HIST-053 | The expanded detail panel for a record with `interference_flag = true` must display `inclination_deviation_deg` as an integer degree using truncation toward zero with sign preserved (stored `4.7` → "4°"; stored `-2.3` → "-2°"; stored `-0.9` → "0°"). | Data Integrity | Unit | `BearingAdapterFormatTest.kt` (AT-HIST-05-D) | `formatInclinationDev(4.7f)` == "4°"; `formatInclinationDev(-2.3f)` == "-2°"; `formatInclinationDev(-0.9f)` == "0°". |
| PROP-HIST-054 | `formatFieldDeviation()` must return "0%" for stored value `0.0f` and "250%" for stored value `2.5f` (no cap on the percentage). | Data Integrity | Unit | `BearingAdapterFormatTest.kt` | `formatFieldDeviation(0.0f)` == "0%"; `formatFieldDeviation(2.5f)` == "250%". |
| PROP-HIST-055 | Records captured under MODERATE or CLEAR interference (i.e., `interference_flag = false`) must not show the interference badge in the collapsed or expanded view. | Functional | E2E | `BearingHistoryFragmentTest.kt` | Badge view is `GONE` for a record explicitly created with `interference_flag = false`. |

### 1.7 Empty States

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-HIST-060 | When the database contains zero bearing records and the search query is empty, the fragment must show the "No bearings yet — capture your first bearing from the compass screen" empty-state message and hide the RecyclerView (`View.GONE`) and search bar (`View.GONE`). | Functional | E2E | `BearingHistoryFragmentTest.kt` (AT-HIST-04-A) | Empty-state view visible; RecyclerView visibility == `GONE`; search bar visibility == `GONE`. |
| PROP-HIST-061 | When zero records match an active search query, the fragment must show "No bearings match your search" and hide the RecyclerView, while keeping the search bar visible. | Functional | E2E | `BearingHistoryFragmentTest.kt` | "No bearings match your search" label visible; RecyclerView visibility == `GONE`; search bar visibility == `VISIBLE`. |
| PROP-HIST-062 | The two empty-state messages ("No bearings yet…" and "No bearings match your search") must use distinct string resources so they can never display the same text. | Contract | Unit | `BearingHistoryViewModelTest.kt` | `R.string.empty_no_bearings != R.string.empty_no_results` (string values differ). |
| PROP-HIST-063 | When a new `BearingRecord` is inserted into Room while the empty-state view is showing, `getAllFlow()` must emit the updated list reactively and the fragment must transition to the normal list view without user interaction. | Functional | E2E | `BearingHistoryFragmentTest.kt` (AT-HIST-04-B) | After Room insert: empty-state view hidden; RecyclerView visible; RecyclerView item count == 1. |
| PROP-HIST-064 | The `updateListState()` helper must produce the correct `View.VISIBLE` / `View.GONE` assignments for all four state combinations: (A) 0 records + empty query, (B) 0 records + non-empty query, (C) ≥1 records + any query with matches, (D) ≥1 records + non-empty query with zero matches. | Functional | Unit | `BearingHistoryViewModelTest.kt` (E-2a) | All four branches produce the correct visibility quadruple as specified in TSPEC §7.1. |

### 1.8 Performance

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-HIST-070 | Initial list load of 500 records via `dao.getAllFlow().first()` must complete within 500 ms on `Dispatchers.IO` using a real Room in-memory database. | Performance | Integration | `BearingHistoryPerfTest.kt` | Measured elapsed time < 500 ms. |
| PROP-HIST-071 | Frame time during fling scroll with 500 records loaded must not exceed 16 ms (60 fps) as measured by `FrameTimingMetric` in the macrobenchmark build variant. | Performance | E2E | `BearingHistoryBenchmark.kt` | Macrobenchmark `FrameTimingMetric` p99 ≤ 16 ms; result reported as CI tracked metric (non-blocking gate). |
| PROP-HIST-072 | The `BearingAdapter` must use `ListAdapter<BearingRecord, ViewHolder>` with `DiffUtil.ItemCallback` rather than `notifyDataSetChanged()` to support efficient incremental updates. | Contract | Unit | `BearingAdapterFormatTest.kt` | Adapter class extends `ListAdapter`; no `notifyDataSetChanged()` call in adapter source. |

---

## 2. Recalibration Properties

### 2.1 Age-Banner Trigger (>30 Days)

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-CAL-001 | The age-based recalibration banner must be visible when `calibration_age_days > 30` and `calAgeBannerDismissed == false`. | Functional | E2E | `RecalibrationBannerTest.kt` (AT-CAL-01-A) | Banner view visible with text "Your calibration is 31 days old — consider recalibrating" for a 31-day-old calibration. |
| PROP-CAL-002 | The age-based banner must be `View.GONE` when `calibration_age_days <= 30`. | Functional | E2E | `RecalibrationBannerTest.kt` (AT-CAL-01-B) | Banner visibility == `GONE` for a calibration that is exactly 30 days old. |
| PROP-CAL-003 | The age-banner day count N must use integer floor division of elapsed milliseconds: `floor(elapsedMs / 86_400_000L)`. A calibration 31 days and 23 hours old must display N=31, not 32. | Data Integrity | Unit | `CompassViewModelDriftTest.kt` (AT-CAL-01-C) | `computeCalibrationAgeDays(31 * 86_400_000L + 23 * 3_600_000L)` == 31. |
| PROP-CAL-004 | The age-banner text must exactly match the template "Your calibration is [N] days old — consider recalibrating" with N substituted from floor division. | Data Integrity | E2E | `RecalibrationBannerTest.kt` | Banner text string matches template with correct N value. |
| PROP-CAL-005 | The age-based banner must appear exclusively in `BearingHistoryFragment`. No banner view with the age-banner ID must exist in `ModernCompassFragment` or `LuopanFragment` layouts. | Contract | E2E | `NavigationTabTest.kt` | `onView(withId(R.id.banner_cal_age_root)).check(doesNotExist())` when Modern or Luopan tab is active. |

### 2.2 Drift-Banner Trigger (10% Deviation for 60 s)

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-CAL-010 | `DriftDetector` must transition to the COUNTING state and start the elapsed timer only when all three preconditions hold simultaneously: (1) `accVariance < 0.01f`, (2) `interferenceState != WARNING`, (3) `expectedFieldUt > 0.0f`. | Functional | Unit | `DriftDetectorTest.kt` (AT-CAL-03-A) | After one `onFrame()` with all preconditions met: `elapsedCountingMs()` is not null; state is COUNTING. |
| PROP-CAL-011 | `DriftDetector` must emit `DriftEvent.TRIGGERED` when all preconditions have held continuously for more than 60 seconds AND the drift formula `|measured − expected| / expected > 0.10` evaluates to true. | Functional | Unit | `DriftDetectorTest.kt` (AT-CAL-03-B) | After initial `onFrame()` at t=0 to start COUNTING, then `clock.advance(61_000L)`, then second `onFrame()` with 12% deviation: return value == `DriftEvent.TRIGGERED`. |
| PROP-CAL-012 | `DriftDetector` must NOT emit `DriftEvent.TRIGGERED` when field deviation is exactly 10% (i.e., the threshold is strictly greater than, not greater-than-or-equal). | Functional | Unit | `DriftDetectorTest.kt` (AT-CAL-03-C) | `onFrame()` at 61 s with deviation = 0.10 exactly: return value != `TRIGGERED`; state remains COUNTING. |
| PROP-CAL-013 | `DriftDetector` must NOT emit `DriftEvent.TRIGGERED` when `expectedFieldUt == 0.0f`, regardless of elapsed time or measured magnitude. | Functional | Unit | `DriftDetectorTest.kt` (AT-CAL-03-D) | After 61 s with `expectedFieldUt = 0.0f` and 12% deviation: return value != `TRIGGERED`. |
| PROP-CAL-014 | The drift detection timer must use a **single** variance accumulator over the combined 3-axis magnitude `sqrt(ax²+ay²+az²)`, not three independent per-axis accumulators. | Contract | Unit | `AccelerometerVarianceTrackerTest.kt` | Three frames with magnitudes 3, 5, 4 produce population variance ≈ 0.6667 (not sample variance 1.0). |
| PROP-CAL-015 | After `DriftDetector` emits `DriftEvent.TRIGGERED`, it must immediately transition to IDLE (timer reset to null / 0). A subsequent `DriftEvent.TRIGGERED` requires a new full 60-second continuous window. | Functional | Unit | `DriftDetectorTest.kt` (AT-CAL-03-F) | After first TRIGGERED at t=61 s: a second `onFrame()` 59 s later (t=120 s) does not return `TRIGGERED`; TRIGGERED returned only after t ≥ 121 s. |
| PROP-CAL-016 | When the 60-second timer has elapsed but field deviation is ≤ 10%, `DriftDetector` must NOT reset the timer. It must remain in COUNTING state and re-evaluate the deviation on every subsequent frame, emitting `TRIGGERED` on the first frame where deviation exceeds 10%. | Functional | Unit | `DriftDetectorTest.kt` (AT-CAL-03-B2 corrected) | Structure: `onFrame()` at t=0 to start COUNTING; `clock.advance(61_001L)`; `onFrame()` with deviation=10%: return != TRIGGERED; state remains COUNTING. Then `onFrame()` with deviation=11%: return == TRIGGERED. |
| PROP-CAL-017 | The drift banner must appear exclusively in `BearingHistoryFragment`. No drift banner view must exist in `ModernCompassFragment` or `LuopanFragment` layouts. | Contract | E2E | `RecalibrationBannerTest.kt` (AT-CAL-02-F) | `onView(withId(R.id.banner_drift_root)).check(doesNotExist())` from ModernCompassFragment. |
| PROP-CAL-018 | `CompassViewModel.driftBannerState` must emit `DriftBannerState.VISIBLE` when `DriftDetector` emits `TRIGGERED` and the 10-minute cooldown has elapsed (cooldown timestamp = 0L). | Integration | Unit | `CompassViewModelDriftTest.kt` (AT-VM-DRIFT-01) | ViewModel receives `FakeDriftDetector.TRIGGERED`; `driftBannerState.value == VISIBLE` after processing. |
| PROP-CAL-019 | `CompassViewModel.driftBannerState` must remain `HIDDEN` when `DriftDetector` emits `TRIGGERED` but the 10-minute cooldown has NOT elapsed. | Integration | Unit | `CompassViewModelDriftTest.kt` (AT-VM-DRIFT-01b) | Cooldown set to 5 minutes ago; ViewModel receives TRIGGERED; `driftBannerState.value == HIDDEN`. |

### 2.3 Banner Dismiss / Re-show Behavior

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-CAL-020 | Tapping the age banner's X/close button must set `calAgeBannerDismissed = true` and hide the banner within the same session. | Functional | E2E | `RecalibrationBannerTest.kt` (AT-CAL-01-D) | After X tap: banner visibility == `GONE`; `compassViewModel.calAgeBannerDismissed == true`. |
| PROP-CAL-021 | The age banner must remain hidden after X-dismiss even when the user switches tabs and returns to the History tab within the same session (same ViewModel instance). | Functional | E2E | `RecalibrationBannerTest.kt` (AT-CAL-01-D) | After X tap → switch to Modern tab → switch back to History tab: banner still `GONE`. |
| PROP-CAL-022 | The age banner must re-appear when a new session is started (new ViewModel instance) if the calibration age still exceeds 30 days. | Functional | E2E | `RecalibrationBannerTest.kt` (AT-CAL-01-E) | Close first `ActivityScenario`; launch new `ActivityScenario`; navigate to History: banner visible again. |
| PROP-CAL-023 | `dismissCalAgeBanner()` must set `calAgeBannerDismissed = true` and cause the ViewModel's age-banner StateFlow to emit a hidden state. | Contract | Unit | `CompassViewModelDriftTest.kt` (D-1b) | After `dismissCalAgeBanner()`: `calAgeBannerDismissed == true`; age banner hidden state emitted. |
| PROP-CAL-024 | Tapping the drift banner's X/close button must: (1) set `driftBannerState = HIDDEN`, and (2) write `clock.nowMs()` to `SettingsRepository.driftCooldownTimestampMs`. | Functional | E2E | `RecalibrationBannerTest.kt` (AT-CAL-02-B) | After X tap: `driftBannerState == HIDDEN`; `settings.driftCooldownTimestampMs == clockNow`. |
| PROP-CAL-025 | `dismissDriftBanner()` must write the current timestamp to `SettingsRepository.driftCooldownTimestampMs` and emit `DriftBannerState.HIDDEN`. | Contract | Unit | `CompassViewModelDriftTest.kt` (D-1b) | After `dismissDriftBanner()`: `settings.driftCooldownTimestampMs == clock.nowMs()`; `driftBannerState.value == HIDDEN`. |
| PROP-CAL-026 | Tapping the drift banner's body must open `CalibrationWizardActivity`. On `RESULT_OK`: drift detector timer is reset to 0, `driftCooldownTimestampMs` is cleared to 0L, and `driftBannerState = HIDDEN`. | Functional | E2E | `RecalibrationBannerTest.kt` (AT-CAL-02-C) | After RESULT_OK: `driftBannerState == HIDDEN`; `settings.driftCooldownTimestampMs == 0L`; detector timer reset. |
| PROP-CAL-027 | Tapping the age banner's body must open `CalibrationWizardActivity`. On `RESULT_OK`: `loadCalibrationAge()` is called and the banner is dismissed. | Functional | E2E | `RecalibrationBannerTest.kt` (AT-CAL-01-F) | After RESULT_OK: banner is `GONE`; calibration age is refreshed in `CompassUiState`. |
| PROP-CAL-028 | On `RESULT_CANCELED` (back-press from CalibrationWizard), neither the age banner nor the drift banner must be affected; both remain in their pre-launch state. | Functional | E2E | `RecalibrationBannerTest.kt` | After RESULT_CANCELED: age banner retains prior visibility; drift banner retains prior visibility. |
| PROP-CAL-029 | Both the age banner and the drift banner may be shown simultaneously as separate, vertically stacked banners when both conditions are true. | Functional | E2E | `RecalibrationBannerTest.kt` (AT-CAL-01-G) | `cal_age > 30 days` AND `driftBannerState == VISIBLE`: both banner views are `VISIBLE` simultaneously. |

### 2.4 DriftDetector State Machine Invariants

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-CAL-030 | A single frame where any precondition is violated must immediately reset `DriftDetector` to IDLE with timer = null. No hysteresis or grace period is permitted. | Functional | Unit | `DriftDetectorTest.kt` (AT-CAL-03-A, AT-CAL-03-E) | Timer at 45 s; next frame with `interferenceState = WARNING`: `elapsedCountingMs() == null`; return == `DriftEvent.RESET`. |
| PROP-CAL-031 | `DriftDetector` must not count elapsed time toward the 60-second threshold while `interferenceState == WARNING`. The drift banner (Condition B) must not appear when active interference from `InterferenceDetector` has set state to `WARNING`. | Functional | Unit | `DriftDetectorTest.kt` (AT-CAL-03-A) | Frames with `interferenceState = WARNING` do not advance `elapsedCountingMs()`. |
| PROP-CAL-032 | `DriftDetector.reset()` must transition the detector to IDLE with `countingStartMs = null` regardless of its prior state. | Contract | Unit | `DriftDetectorTest.kt` | After `reset()` when COUNTING: `elapsedCountingMs() == null`. |
| PROP-CAL-033 | `AccelerometerVarianceTracker` must use a time-based rolling window of 5,000 ms, not a fixed sample count, to remain correct regardless of actual sensor delivery rate. | Contract | Unit | `AccelerometerVarianceTrackerTest.kt` | Samples added beyond the 5,000 ms window are evicted; samples within the window are retained. |
| PROP-CAL-034 | `AccelerometerVarianceTracker` must return `0f` when fewer than 2 samples are in the window (n<2 guard). | Error Handling | Unit | `AccelerometerVarianceTrackerTest.kt` | `update()` with only one sample returns `0f`. |
| PROP-CAL-035 | `AccelerometerVarianceTracker` must use population variance (`/ n`, not `/ (n-1)`). For magnitudes [3, 5, 4], variance must equal ≈ 0.6667, not 1.0. | Data Integrity | Unit | `AccelerometerVarianceTrackerTest.kt` (AT-VAR-01) | Three samples [3, 5, 4] return variance ≈ 0.6667 (within float epsilon). |
| PROP-CAL-036 | The `IDriftDetector` interface must declare `onFrame(accVariance, measuredMagnitudeUt, interferenceState, expectedFieldUt): DriftEvent?` and `reset()`. `DriftEvent` enum must contain exactly the constants `TRIGGERED` and `RESET`. | Contract | Unit | `IDriftDetectorContractTest.kt` (B-2a) | Interface compiles; both enum constants accessible; `FakeDriftDetector` compiles as `IDriftDetector` (asserted in B-4). |
| PROP-CAL-037 | `CompassViewModel` must accept `IDriftDetector` (not `DriftDetector`) as a constructor parameter so that `FakeDriftDetector` can be injected in unit tests. | Contract | Unit | `CompassViewModelDriftTest.kt` | `CompassViewModel` instantiated with `FakeDriftDetector` does not throw; sensor loop calls `fake.onFrame()`. |
| PROP-CAL-038 | The end-to-end wiring `AccelerometerVarianceTracker → DriftDetector.onFrame() → CompassViewModel → driftBannerState` must produce `DriftBannerState.VISIBLE` after 61 seconds of continuous drift with all preconditions met, using real implementations and `FakeClock`. | Integration | Integration | `DriftDetectorIntegrationTest.kt` (AT-CAL-INT-01) | Phase 1 (58 s of frames): `triggered == false`. Phase 2 (advance past 60 s): `driftBannerState == VISIBLE`. |

### 2.5 Cooldown Persistence

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-CAL-040 | The 10-minute drift cooldown timestamp must be persisted in `SettingsRepository.driftCooldownTimestampMs` as a Unix epoch millisecond value, surviving process death. | Functional | Unit | `CompassViewModelDriftTest.kt` (AT-CAL-02-D) | After dismiss, process kill, and ViewModel recreation: `settings.driftCooldownTimestampMs > 0L`. |
| PROP-CAL-041 | When `driftCooldownTimestampMs` was set less than 10 minutes ago, a new `DriftEvent.TRIGGERED` must NOT cause `driftBannerState` to emit `VISIBLE`. | Functional | Unit | `CompassViewModelDriftTest.kt` (AT-CAL-02-D) | Cooldown set 5 min ago; TRIGGERED received: `driftBannerState.value == HIDDEN`. |
| PROP-CAL-042 | When `driftCooldownTimestampMs` was set 10 or more minutes ago, a new `DriftEvent.TRIGGERED` must cause `driftBannerState` to emit `VISIBLE`. | Functional | Unit | `CompassViewModelDriftTest.kt` (AT-CAL-02-E) | Cooldown set 11 min ago; TRIGGERED received: `driftBannerState.value == VISIBLE`. |
| PROP-CAL-043 | On `RESULT_OK` from CalibrationWizard (drift path), `driftCooldownTimestampMs` must be reset to `0L` (not left at the prior dismiss timestamp), ensuring no cooldown blocks the detector after recalibration. | Functional | Unit | `CompassViewModelDriftTest.kt` | After `resetDriftDetector()`: `settings.driftCooldownTimestampMs == 0L`. |
| PROP-CAL-044 | `onCalibrationCompleteFromHistory()` (age path) must set `calAgeBannerDismissed = true` synchronously before the IO coroutine launches, then clear the flag to `false` only if the refreshed `ageDays <= 30`. | Functional | Unit | `CompassViewModelDriftTest.kt` (D-1d) | Before coroutine completes: `calAgeBannerDismissed == true`. After IO resolves with age ≤ 30: `calAgeBannerDismissed == false`. |

---

## 3. Sensor Logging Properties

### 3.1 First-Launch Write

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-SENSOR-001 | `SensorCapabilityLogger.maybeWrite()` must write `sensor_profile.json` to `Context.getFilesDir()` and update `sensorProfileWrittenForVersion = BuildConfig.VERSION_CODE` when `VERSION_CODE > sensorProfileWrittenForVersion`. | Functional | Integration | `SensorCapabilityLoggerInstrumentedTest.kt` (AT-SENSOR-01-A) | After `maybeWrite()` with key=0, version=40: file exists; key==40; file has non-empty content. |
| PROP-SENSOR-002 | `SensorCapabilityLogger.maybeWrite()` must write `sensor_profile.json` when the app data has been cleared (key reset to 0) and the app relaunches. | Functional | Integration | `SensorCapabilityLoggerInstrumentedTest.kt` (AT-SENSOR-01-D) | After clearing SharedPreferences: file written; key updated. |
| PROP-SENSOR-003 | `SensorCapabilityLogger.maybeWrite()` must write `sensor_profile.json` on version upgrade (stored key < current VERSION_CODE). | Functional | Integration | `SensorCapabilityLoggerInstrumentedTest.kt` (AT-SENSOR-01-C) | `key=39`, `VERSION_CODE=40`: file written with `"app_version_code": 40`; key updated to 40. |

### 3.2 Version-Gate Idempotency

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-SENSOR-010 | `SensorCapabilityLogger.maybeWrite()` must NOT write or overwrite `sensor_profile.json` when `VERSION_CODE == sensorProfileWrittenForVersion` (same-version relaunch). | Idempotency | Integration | `SensorCapabilityLoggerInstrumentedTest.kt` (AT-SENSOR-01-B) | `key=40`, `VERSION_CODE=40`: file write not triggered; `written_at_iso8601` timestamp unchanged. |
| PROP-SENSOR-011 | `SensorCapabilityLogger.maybeWrite()` must NOT write when `VERSION_CODE < sensorProfileWrittenForVersion` (downgrade scenario). | Idempotency | Unit | `SensorCapabilityLoggerTest.kt` | `key=41`, `VERSION_CODE=40`: `fileWriter.write()` not called. |
| PROP-SENSOR-012 | The version gate comparison must be strictly greater-than (`>`), not greater-than-or-equal (`>=`). | Contract | Unit | `SensorCapabilityLoggerTest.kt` | `key == VERSION_CODE`: `fileWriter.write()` call count == 0. |

### 3.3 Failure Retry

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-SENSOR-020 | When `SensorFileWriter.write()` throws `IOException`, `maybeWrite()` must NOT update `sensorProfileWrittenForVersion`. | Error Handling | Unit | `SensorCapabilityLoggerTest.kt` (AT-SENSOR-01-E) | After IOException: `settings.sensorProfileWrittenForVersion` unchanged from prior value. |
| PROP-SENSOR-021 | When `SensorFileWriter.write()` throws `SecurityException`, `maybeWrite()` must NOT update `sensorProfileWrittenForVersion` and must NOT rethrow. | Error Handling | Unit | `SensorCapabilityLoggerTest.kt` | After SecurityException: key unchanged; no exception propagated to caller. |
| PROP-SENSOR-022 | Any exception thrown during `maybeWrite()` must be caught by a `catch (e: Exception)` block (not a narrow `catch (e: IOException)` block), so that both `IOException` and `SecurityException` are handled uniformly. | Contract | Unit | `SensorCapabilityLoggerTest.kt` | `SecurityException` thrown by `fileWriter.write()`: exception swallowed; method returns normally. |
| PROP-SENSOR-023 | On any write failure, `maybeWrite()` must log the failure at `Log.e` level with a non-empty message. | Observability | Unit | `SensorCapabilityLoggerTest.kt` | `Log.e` is called at least once with a non-null message when write throws. |
| PROP-SENSOR-024 | Because `sensorProfileWrittenForVersion` is not updated on failure, the next app launch must retry the write (`VERSION_CODE > 0` still true). | Idempotency | Unit | `SensorCapabilityLoggerTest.kt` | Second `maybeWrite()` call after first failure: `fileWriter.write()` called again. |

### 3.4 File Schema Completeness

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-SENSOR-030 | `buildJsonFromSensors()` must produce a JSON object containing all required top-level fields: `device_model`, `device_manufacturer`, `android_version`, `android_api_level`, `app_version_code`, `written_at_iso8601`, `sensors`. | Data Integrity | Unit | `SensorCapabilityLoggerTest.kt` (AT-SENSOR-01-G) | Parsed JSON has all seven keys present with non-null values. |
| PROP-SENSOR-031 | Each sensor entry in the `sensors` array must contain: `type_constant`, `name`, `vendor`, `resolution_ut_or_native`, `max_range_native`, `reporting_mode`. | Data Integrity | Unit | `SensorCapabilityLoggerTest.kt` | For a fake sensor object, all six per-sensor fields are present. |
| PROP-SENSOR-032 | `buildJsonFromSensors()` must produce a pretty-printed JSON string with 2-space indentation (i.e., `JSONObject.toString(2)`). | Data Integrity | Unit | `SensorCapabilityLoggerTest.kt` | Output string contains `"  "` (two-space) indentation. |
| PROP-SENSOR-033 | `written_at_iso8601` must be formatted as an ISO-8601 UTC timestamp with "Z" suffix (e.g., "2026-04-27T08:30:00Z"). Device local time must not be used. | Data Integrity | Unit | `SensorCapabilityLoggerTest.kt` | `written_at_iso8601` value ends with "Z" and parses as a valid `Instant`. |
| PROP-SENSOR-034 | When no sensors are returned by `SensorManager.getSensorList(Sensor.TYPE_ALL)`, the `sensors` array must be empty (`[]`), not absent; the file must still be written. | Error Handling | Unit | `SensorCapabilityLoggerTest.kt` | `buildJsonFromSensors(emptyList())` produces JSON with `"sensors": []`. |
| PROP-SENSOR-035 | `mapReportingMode()` must map integer constant `0` → `"CONTINUOUS"`, `1` → `"ON_CHANGE"`, `2` → `"ONE_SHOT"`, `3` → `"SPECIAL_TRIGGER"`. | Data Integrity | Unit | `SensorCapabilityLoggerTest.kt` (AT-SENSOR-01-F) | Each known constant maps to the correct string. |
| PROP-SENSOR-036 | `mapReportingMode()` must map any unrecognized integer to `"UNKNOWN(${intValue})"`, preserving the raw integer for diagnostics. | Error Handling | Unit | `SensorCapabilityLoggerTest.kt` (AT-SENSOR-01-G) | `mapReportingMode(99)` returns `"UNKNOWN(99)"`. |
| PROP-SENSOR-037 | `buildJsonFromSensors()` must be an `internal` pure function (no `Context` or `SensorManager` dependency) to enable JVM unit testing without an Android runtime. | Contract | Unit | `SensorCapabilityLoggerTest.kt` | Function callable directly with a `List<Sensor>` argument in a JVM test without robolectric or instrumentation. |
| PROP-SENSOR-038 | `sensorProfileWrittenForVersion` must be added as a named `const val` key and `var` property in `SettingsRepository` following the existing Phase 3 additions block, not as an ad-hoc SharedPreferences call at the call site. | Contract | Unit | `SettingsRepositoryTest.kt` | `SettingsRepository.KEY_SENSOR_PROFILE_WRITTEN_FOR_VERSION` constant exists; `sensorProfileWrittenForVersion` property reads/writes correctly with default 0. |

---

## 4. Navigation Properties

### 4.1 Tab Ordering

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-NAV-001 | `BearingHistoryFragment` must be the third tab (index 2) in `CompassActivity`'s `TabLayout`. Tabs 0 (Modern) and 1 (Luopan) must be unaffected. | Contract | E2E | `NavigationTabTest.kt` (AT-NAV-01-A) | Tapping the third tab: NavController current destination == `R.id.dest_history`; `BearingHistoryFragment` is current fragment. |
| PROP-NAV-002 | `CompassActivity` must have a `TAB_HISTORY = 2` constant in its companion object and a corresponding `when` branch in `wireTabNavigation()` that navigates to `R.id.dest_history`. | Contract | Unit | `NavigationTabTest.kt` | Navigating to History tab does not produce a silent no-op; destination change is triggered. |
| PROP-NAV-003 | `activity_compass.xml` must contain exactly three `TabItem` elements after Phase 4. | Contract | E2E | `NavigationTabTest.kt` | `TabLayout` has `tabCount == 3`. |
| PROP-NAV-004 | `nav_graph.xml` must contain a `<fragment>` destination with `android:id="@+id/dest_history"` pointing to `BearingHistoryFragment`. | Contract | E2E | `NavigationTabTest.kt` | NavController can navigate to `R.id.dest_history` without throwing. |

### 4.2 ActivityResultLauncher Wiring

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-NAV-010 | `BearingHistoryFragment` must register two separate `ActivityResultLauncher` instances in `Fragment.onCreate()`: one for the age banner path (`calWizardLauncherAge`) and one for the drift banner path (`calWizardLauncherDrift`). | Contract | E2E | `NavigationTabTest.kt` (AT-NAV-01-C) | After RESULT_OK on age launcher: `loadCalibrationAge()` called; age banner dismissed. After RESULT_OK on drift launcher: drift detector reset; cooldown cleared. |
| PROP-NAV-011 | The launchers must be registered in `Fragment.onCreate()`, not `onViewCreated()`, per Android Jetpack best practice. | Contract | E2E | `NavigationTabTest.kt` | No `IllegalStateException` thrown when launcher is used; registration survives Fragment recreation. |
| PROP-NAV-012 | On `RESULT_OK` from the age-banner launcher, `viewModel.onCalibrationCompleteFromHistory()` must be called, refreshing `calibration_age_days` and dismissing the age banner. | Functional | E2E | `NavigationTabTest.kt` (AT-NAV-01-C) | After RESULT_OK: age banner is `GONE`; updated `calibration_age_days` value is reflected in `CompassUiState`. |
| PROP-NAV-013 | On `RESULT_OK` from the drift-banner launcher, `viewModel.resetDriftDetector()` must be called, resetting the detector timer, clearing the cooldown, and setting `driftBannerState = HIDDEN`. | Functional | E2E | `RecalibrationBannerTest.kt` (AT-CAL-02-C) | After RESULT_OK on drift launcher: `driftBannerState == HIDDEN`; `driftCooldownTimestampMs == 0L`; detector timer == null. |
| PROP-NAV-014 | On `RESULT_CANCELED` or back-press from either CalibrationWizard launcher, no banner state must change. | Functional | E2E | `RecalibrationBannerTest.kt` | After RESULT_CANCELED: age banner and drift banner retain prior visibility; no cooldown written; no detector reset. |

### 4.3 Shared ViewModel Scope

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-NAV-020 | `BearingHistoryFragment` must obtain the same `CompassViewModel` instance as `ModernCompassFragment` and `LuopanFragment` via `activityViewModels()`. | Integration | E2E | `NavigationTabTest.kt` (AT-NAV-01-B) | `compassViewModel` in `BearingHistoryFragment` is identical (same instance) to `compassViewModel` in `ModernCompassFragment`. |
| PROP-NAV-021 | `BearingHistoryFragment` must own a separate, fragment-scoped `BearingHistoryViewModel` via `viewModels()`. This ViewModel is destroyed when the Fragment is destroyed on tab navigation. | Contract | E2E | `BearingHistoryFragmentTest.kt` | After tab-away and tab-back: new `BearingHistoryViewModel` created; search query cleared. |
| PROP-NAV-022 | `CompassViewModel.loadCalibrationAge()` must be promoted to `internal` visibility so that `BearingHistoryFragment` can call it on fragment-become-visible and on `RESULT_OK`. | Contract | Unit | `CompassViewModelDriftTest.kt` | `compassViewModel.loadCalibrationAge()` callable from another file in the same module without compilation error. |
| PROP-NAV-023 | `BearingHistoryFragment.onResume()` must call `compassViewModel.loadCalibrationAge()` to ensure the age value is fresh on every fragment-become-visible event. | Functional | E2E | `RecalibrationBannerTest.kt` | After returning to History tab: age banner state reflects the current calibration age, not a stale value. |

### 4.4 Database Migration

| ID | Property | Category | Level | Test File | Pass Condition |
|----|----------|----------|-------|-----------|----------------|
| PROP-NAV-030 | `MIGRATION_2_3` must add `expected_field_ut REAL NOT NULL DEFAULT 0.0` to `calibration_records`. Existing rows must receive `0.0` as their value. | Functional | Integration | `LuopanDatabaseMigrationTest.kt` | After migrating a v2 DB: `expected_field_ut` column exists; all pre-existing rows have value `0.0`. |
| PROP-NAV-031 | `CalibrationResult.sphereRadius_uT` must be populated by `fitEllipsoid()` from the local sphere radius `r` variable. | Data Integrity | Unit | `CalibrationRepositoryTest.kt` | After `fitEllipsoid()`: `CalibrationResult.sphereRadius_uT` equals the computed `r` value. |
| PROP-NAV-032 | `CalibrationRepository.toRecord()` must map `CalibrationResult.sphereRadius_uT` to `CalibrationRecord.expected_field_ut`. | Data Integrity | Unit | `CalibrationRepositoryTest.kt` | After `toRecord()`: `CalibrationRecord.expected_field_ut == CalibrationResult.sphereRadius_uT`. |
| PROP-NAV-033 | Existing calibration records with `expected_field_ut = 0.0` (from MIGRATION_2_3 default) must NOT trigger Condition B drift detection. | Functional | Unit | `DriftDetectorTest.kt` (AT-CAL-03-D) | `expectedFieldUt = 0.0f` with all other preconditions met: `TRIGGERED` never returned regardless of elapsed time. |

---

## 5. Coverage Matrix

### 5.1 Requirements → Properties

| Requirement | Properties | Coverage |
|-------------|-----------|---------|
| REQ-CAPTURE-03 (bearing history screen) | PROP-HIST-001–004, 010–011, 020–023, 030–036, 040–049, 050–055, 060–064, 070–072 | Full |
| REQ-DETECT-05 (interference flag) | PROP-HIST-050–055 | Full |
| REQ-CAL-05 Condition A (age-based banner) | PROP-CAL-001–005, 020–023, 027–029 | Full |
| REQ-CAL-05 Condition B (drift-based banner) | PROP-CAL-010–019, 024–026, 028–029, 030–043 | Full |
| REQ-SENSOR-07 (sensor logging) | PROP-SENSOR-001–038 | Full |
| REQ-CAPTURE-03 Navigation (§5.1) | PROP-NAV-001–023, 030–033 | Full |

### 5.2 FSPEC Acceptance Tests → Properties

| FSPEC AT | Properties | PLAN Task |
|----------|-----------|-----------|
| AT-HIST-01-A | PROP-HIST-001, PROP-HIST-003, PROP-HIST-050 | F-5 |
| AT-HIST-01-B | PROP-HIST-020, PROP-HIST-021 | F-5 |
| AT-HIST-01-C | PROP-HIST-020 | F-5 |
| AT-HIST-02-A | PROP-HIST-030 | E-2 |
| AT-HIST-02-B | PROP-HIST-031 | E-2 |
| AT-HIST-02-C | PROP-HIST-032 | E-2 |
| AT-HIST-02-D | PROP-HIST-033 | E-2 |
| AT-HIST-02-E | PROP-HIST-034 | F-8 |
| AT-HIST-03-A | PROP-HIST-040 | F-9 |
| AT-HIST-03-B | PROP-HIST-043 | F-9 |
| AT-HIST-03-C | PROP-HIST-044, PROP-HIST-045 | F-9 |
| AT-HIST-03-D (manual) | PROP-HIST-046 | G-4 |
| AT-HIST-03-E | PROP-HIST-046 | F-9 |
| AT-HIST-04-A | PROP-HIST-060 | F-6 |
| AT-HIST-04-B | PROP-HIST-063 | F-6 |
| AT-HIST-05-A | PROP-HIST-050 | F-7 |
| AT-HIST-05-B | PROP-HIST-051 | F-7 |
| AT-HIST-05-C | PROP-HIST-052 | F-7 |
| AT-HIST-05-D | PROP-HIST-053 | E-5 |
| AT-CAL-01-A | PROP-CAL-001, PROP-CAL-004 | F-10 |
| AT-CAL-01-B | PROP-CAL-002 | F-10 |
| AT-CAL-01-C | PROP-CAL-003 | D-1a |
| AT-CAL-01-D | PROP-CAL-020, PROP-CAL-021 | F-10 |
| AT-CAL-01-E | PROP-CAL-022 | F-10 |
| AT-CAL-01-F | PROP-CAL-027 | F-10 |
| AT-CAL-01-G | PROP-CAL-029 | F-10 |
| AT-CAL-02-A | PROP-CAL-018 | D-1 |
| AT-CAL-02-B | PROP-CAL-024, PROP-CAL-025 | F-11 |
| AT-CAL-02-C | PROP-CAL-026, PROP-CAL-043 | F-11 |
| AT-CAL-02-D | PROP-CAL-041 | D-1c |
| AT-CAL-02-E | PROP-CAL-042 | D-1c |
| AT-CAL-02-F | PROP-CAL-017 | F-11 |
| AT-CAL-03-A | PROP-CAL-010, PROP-CAL-030, PROP-CAL-031 | B-5 |
| AT-CAL-03-B | PROP-CAL-011 | B-5 |
| AT-CAL-03-B2 (corrected) | PROP-CAL-016 | B-5 |
| AT-CAL-03-C | PROP-CAL-012 | B-5 |
| AT-CAL-03-D | PROP-CAL-013, PROP-NAV-033 | B-5 |
| AT-CAL-03-E | PROP-CAL-030 | B-5 |
| AT-CAL-03-F | PROP-CAL-015 | B-5 |
| AT-CAL-INT-01 | PROP-CAL-038 | B-7, B-8 |
| AT-SENSOR-01-A | PROP-SENSOR-001 | C-4 |
| AT-SENSOR-01-B | PROP-SENSOR-010 | C-4 |
| AT-SENSOR-01-C | PROP-SENSOR-003 | C-4 |
| AT-SENSOR-01-D | PROP-SENSOR-002 | C-4 |
| AT-SENSOR-01-E | PROP-SENSOR-020, PROP-SENSOR-023 | C-1 |
| AT-SENSOR-01-F | PROP-SENSOR-035 | C-1 |
| AT-SENSOR-01-G | PROP-SENSOR-036 | C-1 |
| AT-NAV-01-A | PROP-NAV-001, PROP-NAV-003, PROP-NAV-004 | F-4 |
| AT-NAV-01-B | PROP-NAV-020 | F-4 |
| AT-NAV-01-C | PROP-NAV-010, PROP-NAV-012 | F-12 |
| AT-VAR-01 | PROP-CAL-035 | B-1 |
| AT-UNDO-VM-01 | PROP-HIST-047 | E-3 |
| AT-UNDO-VM-02 | PROP-HIST-048 | E-3 |
| AT-UNDO-VM-03 | PROP-HIST-049 | E-3 |

---

## 6. Gaps and Notes

### 6.1 Cross-Review Issues Addressed as Properties

The following cross-review findings from PM PLAN-v2 and TE PLAN-v2 are resolved by properties in this document, locking down the correct behavior before implementation:

| Source | Finding | Property | Resolution |
|--------|---------|----------|-----------|
| PM PLAN-v2 F-01 | FSPEC-HIST-03 Snackbar text still reads "Deleted" in the FSPEC; PLAN says "Bearing deleted" | PROP-HIST-041 | Property specifies the Snackbar body text must be exactly "Bearing deleted". This supersedes the FSPEC-HIST-03 §Behavioral Flow step 3 wording until that FSPEC is updated. |
| TE PLAN-v2 F-01 | B-2a forward-reference: `FakeDriftDetector implements IDriftDetector` cannot be a failing test at B-2a time (fake doesn't exist yet) | PROP-CAL-036 | Property covers enum constant existence and `onFrame()` signature (testable at B-2a). The `FakeDriftDetector implements IDriftDetector` assertion is part of PROP-CAL-037 and PROP-CAL-038, tested in B-4 and D-1. |
| TE PLAN-v2 F-02 | TSPEC §9.3b shows `FakeDriftDetector` subclassing concrete `DriftDetector`; PLAN B-4 correctly requires `IDriftDetector` | PROP-CAL-037 | Property explicitly requires `CompassViewModel` to accept `IDriftDetector` (not `DriftDetector`). TSPEC §9.3b code snippet is superseded. |
| TE PLAN-v2 F-03 | TSPEC §9.7 AT-CAL-03-B2 test body has a sequencing error: single `onFrame()` after `clock.advance(61_001L)` starts COUNTING at t=61s and returns null (trivially passes but doesn't test boundary) | PROP-CAL-016 | Property specifies the correct test structure: an initial `onFrame()` at t=0 to start COUNTING, then advance clock, then second `onFrame()` to evaluate the boundary. The old test structure was wrong because the detector starts in IDLE, so the first call after advancing time merely transitions to COUNTING. |

### 6.2 Properties Without Existing FSPEC Acceptance Tests (New Coverage)

The following properties are derived from requirements or TSPEC design contracts but do not correspond to a named FSPEC acceptance test. They close gaps in existing coverage:

| Property | Gap Closed |
|----------|-----------|
| PROP-HIST-022 | Adapter must use `notifyItemChanged()` not `notifyDataSetChanged()`. Specified in PLAN DoD but no FSPEC AT verifies it. |
| PROP-HIST-035 | Search query retained across configuration changes. Specified in FSPEC edge cases but no named AT. |
| PROP-HIST-041 | Snackbar text exactly "Bearing deleted". Required by PM PLAN-v2 F-01; not in FSPEC-HIST-03 current text. |
| PROP-HIST-042 | Snackbar duration must be `setDuration(5000)`. Specified in PLAN DoD but no FSPEC AT verifies duration. |
| PROP-HIST-072 | Adapter must extend `ListAdapter` with `DiffUtil`. Specified in REQ NFR but no FSPEC AT. |
| PROP-CAL-016 | COUNTING state past 60 s when deviation ≤ 10% stays COUNTING (re-evaluate-each-frame rule). Specified in TSPEC §5.3 but AT-CAL-03-B2 test body was wrong. |
| PROP-CAL-032 | `DriftDetector.reset()` resets to IDLE from any prior state. |
| PROP-CAL-044 | `onCalibrationCompleteFromHistory()` synchronous flag set + post-IO conditional clear (race mitigation). Specified in TSPEC §6.1.3 and PLAN D-1d. |
| PROP-SENSOR-011 | Version gate blocks write on downgrade scenario. |
| PROP-SENSOR-021 | SecurityException swallowed without rethrow. |
| PROP-SENSOR-022 | `catch (e: Exception)` required (not narrow IOException). |
| PROP-SENSOR-024 | Second launch retries after failure. |
| PROP-SENSOR-037 | `buildJsonFromSensors()` is a pure function (no Context dependency). |
| PROP-SENSOR-038 | SettingsRepository key must be a named constant, not an ad-hoc string. |
| PROP-NAV-002 | `TAB_HISTORY = 2` constant and `when` branch must exist (silent no-op risk). |
| PROP-NAV-021 | Fragment-scoped `BearingHistoryViewModel` destroyed on tab navigation. |
| PROP-NAV-030 | MIGRATION_2_3 column default and existing-row behavior. |

### 6.3 Deliberately Out-of-Scope

| Item | Reason |
|------|--------|
| Non-transmission of `sensor_profile.json` | Design-level guarantee enforced by existing `NoInternetPermissionCheck` lint rule; not an automated test assertion. |
| File inaccessibility via Android file manager | Implicit Android storage sandbox guarantee; not asserted in automated tests. |
| AT-HIST-03-D (process death) | Manual test only; not automatable as a standard instrumented test. Covered by PROP-HIST-046 via the `ActivityScenario` close approximation (AT-HIST-03-E). |
| Non-ASCII case-insensitive search | SQLite LIKE best-effort for non-ASCII; no automated assertion. |
| Snackbar TalkBack accessibility | FSPEC accessibility rule; requires manual or specialized TalkBack test tooling. |
| REQ-CAPTURE-05 (CSV export) | Deferred to Phase 5. |
