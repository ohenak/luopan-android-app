# FSPEC-luopan-p4-bearing-history
## Phase 4: Bearing History, Recalibration Refinements, and Sensor Diagnostics

| Field | Value |
|-------|-------|
| **Version** | 0.1-draft |
| **Date** | 2026-04-27 |
| **Status** | Draft |
| **Phase** | 4 of 5 |
| **Source REQ** | [REQ-luopan-p4-bearing-history v0.4-draft](REQ-luopan-p4-bearing-history.md) |
| **Cross-reviews addressed** | SE v4 (F-01 Medium, F-02 Low), TE v4 (F-01 Medium, F-02 Medium, F-03–F-06 Low) |

---

## Scope

This FSPEC covers the four behavioral domains from Phase 4 that involve branching logic, multi-step flows, or business rules that engineers must not decide independently:

1. **Bearing History Screen** — list view, expand, search, swipe-to-delete with undo, empty state, interference badge
2. **Recalibration Lifecycle** — age-based banner (Condition A), drift-based banner (Condition B), dismiss mechanics, re-display rules, DriftDetector state machine
3. **Sensor Capability Logging** — first-launch detection, file schema, write-failure retry
4. **Navigation Architecture** — BearingHistoryFragment as third tab, CalibrationWizardActivity result handling, ViewModel ownership

Requirements with no behavioral ambiguity (e.g., pure data schema) are not duplicated here; refer to the REQ for those fields.

---

## FSPEC Index

| ID | Title | Linked Requirements |
|----|-------|-------------------|
| FSPEC-HIST-01 | Bearing History Screen — List View | REQ-CAPTURE-03 |
| FSPEC-HIST-02 | Bearing History Screen — Search | REQ-CAPTURE-03 |
| FSPEC-HIST-03 | Bearing History Screen — Swipe to Delete with Undo | REQ-CAPTURE-03 |
| FSPEC-HIST-04 | Bearing History Screen — Empty State | REQ-CAPTURE-03 |
| FSPEC-HIST-05 | Interference Badge in History Row | REQ-DETECT-05 |
| FSPEC-CAL-01 | Recalibration Banner — Condition A (Age-Based) | REQ-CAL-05 |
| FSPEC-CAL-02 | Recalibration Banner — Condition B (Drift-Based) | REQ-CAL-05 |
| FSPEC-CAL-03 | DriftDetector State Machine | REQ-CAL-05 |
| FSPEC-NAV-01 | Navigation Architecture — BearingHistoryFragment | REQ-CAPTURE-03, REQ-CAL-05 |
| FSPEC-SENSOR-01 | Sensor Capability Logging | REQ-SENSOR-07 |

---

## FSPEC-HIST-01: Bearing History Screen — List View

**Linked requirements:** REQ-CAPTURE-03

### Behavioral Flow

1. User taps the "History" tab (third tab) in `CompassActivity`.
2. `BearingHistoryFragment` becomes visible. The DAO `Flow<List<BearingRecord>>` emits the current list sorted by `captured_at` descending (newest first).
3. The `RecyclerView` renders each row with:
   - Name (left-aligned, primary text)
   - Bearing value + north type label (e.g., "045.2° True North")
   - Confidence badge (coloured chip: Poor / Moderate / Good)
   - Date/time formatted to locale default (day, month, year, 12/24h per device locale)
   - Interference badge if `interference_flag = true` (see FSPEC-HIST-05)
4. User taps a row → row expands inline (accordion pattern) to show full record detail. Tapping again (or tapping another row) collapses it.
5. Expanded record shows:
   - Bearing value + north type
   - Confidence level (text label, not just colour)
   - `captured_at` timestamp (full ISO-8601 local time)
   - Name (repeated for readability)
   - Notes (empty string if no notes; label is still shown)
   - If `interference_flag = true`: field deviation percentage and inclination deviation (see FSPEC-HIST-05)

### Business Rules

| Rule | Detail |
|------|--------|
| Sort order | Newest `captured_at` first; deterministic secondary sort by `rowid` descending to break ties |
| Reactive updates | DAO must return `Flow<List<BearingRecord>>` (or `PagingSource`). Plain `List` return is not acceptable |
| Single expanded row | At most one row is expanded at a time. Tapping an already-expanded row collapses it; tapping a different row collapses the current and expands the new one |
| Expanded state on delete | If the expanded row is deleted by swipe, it collapses and is removed. No crash on delete-while-expanded |
| Performance | Initial list load ≤ 500 ms on `Dispatchers.IO` for 500 records; fling frame time ≤ 16 ms at 500 records |

### Edge Cases

| Case | Behavior |
|------|---------|
| Zero records | Show empty state (FSPEC-HIST-04); RecyclerView is hidden |
| Live insert during session | When a new bearing is saved from the Compass tab, the DAO Flow emits; the new record appears at the top of the list without requiring a manual refresh |
| Scroll position after undo | After a record is restored by undo, the list scrolls to bring the restored row into view (best-effort; if already visible, no forced scroll) |

### Acceptance Tests

**AT-HIST-01-A: List renders correctly**

- *Who:* Instrumented test
- *Given:* 10 bearing records seeded in Room (5 with `interference_flag=false`, 5 with `interference_flag=true`), mix of north types
- *When:* `BearingHistoryFragment` is launched
- *Then:* RecyclerView shows 10 items, ordered newest-`captured_at` first; each row has name, bearing+north-type, confidence badge, timestamp visible; interference badge present on exactly 5 rows

**AT-HIST-01-B: Expand / collapse single row**

- *Who:* Instrumented test
- *Given:* List with 3 records
- *When:* User taps row 1
- *Then:* Row 1 expands; rows 2 and 3 remain collapsed; tapping row 1 again collapses it

**AT-HIST-01-C: Only one row expanded**

- *Who:* Instrumented test
- *Given:* Row 1 is expanded
- *When:* User taps row 2
- *Then:* Row 1 collapses; row 2 expands

---

## FSPEC-HIST-02: Bearing History Screen — Search

**Linked requirements:** REQ-CAPTURE-03

### Behavioral Flow

1. A `SearchView` (or equivalent `EditText`) is visible at the top of the history screen at all times.
2. User types a query character. A 300 ms debounce timer starts. Each additional character restarts the 300 ms timer.
3. After 300 ms of no input, the search is applied: the DAO query is filtered client-side (or via SQL `LIKE '%query%'`) case-insensitively.
4. The `RecyclerView` updates to show only matching records, sorted newest-first.
5. If zero records match: empty-state view shows "No bearings match your search" (distinct from the no-records empty state).
6. User clears the query (deletes all characters, or taps the clear button): the full sorted list is restored **immediately** (no debounce delay on clear).

### Business Rules

| Rule | Detail |
|------|--------|
| Match type | Case-insensitive substring: query "north" matches "North Gate", "northeast", "Northing" |
| Minimum character count | None — single-character queries are valid |
| Debounce | 300 ms; timer restarts on each keystroke; empty query bypasses debounce (instant restore) |
| Search scope | Name field only; bearing values, notes, and other fields are not searched |
| Sort order in results | Same as full list: newest `captured_at` first |
| No network or disk I/O delay | Filtering is applied to the already-loaded in-memory list or via a new Room query on `Dispatchers.IO` |

### Edge Cases

| Case | Behavior |
|------|---------|
| Query yields zero matches | Show "No bearings match your search" empty state; list hidden |
| Query yields all records | Indistinguishable from full list; all rows shown |
| User rotates device mid-search | Query string is retained in ViewModel state (`SavedStateHandle`); list is re-filtered on view recreation |
| User navigates away and returns | Search query is cleared; full list is restored (session-level reset) |

### Acceptance Tests

**AT-HIST-02-A: Substring match**

- *Who:* Unit test (ViewModel + fake DAO)
- *Given:* Records named "North Gate", "northeast", "Southing"
- *When:* Query "north" is set
- *Then:* Emitted list contains "North Gate" and "northeast"; "Southing" is absent

**AT-HIST-02-B: Debounce — intermediate keystrokes do not trigger search**

- *Who:* Unit test (TestCoroutineDispatcher + fake DAO)
- *Given:* ViewModel with 300 ms debounce
- *When:* Characters "n", "o", "r" are typed at 100 ms intervals
- *Then:* The DAO query is invoked exactly once (after "r" + 300 ms idle)

**AT-HIST-02-C: Immediate restore on empty query**

- *Who:* Unit test
- *Given:* Query "north" is active, showing filtered list
- *When:* Query is cleared (set to "")
- *Then:* Full list is emitted immediately (no 300 ms wait)

**AT-HIST-02-D: Zero-match empty state**

- *Who:* Instrumented test
- *Given:* No record name contains "zzz"
- *When:* Query "zzz" is entered and debounce elapses
- *Then:* "No bearings match your search" label is visible; RecyclerView has 0 items

---

## FSPEC-HIST-03: Bearing History Screen — Swipe to Delete with Undo

**Linked requirements:** REQ-CAPTURE-03

### Behavioral Flow

1. User swipes a list row left (or right, per platform convention). `ItemTouchHelper` triggers the swipe callback.
2. **Immediately on swipe:** The record is deleted from the Room database (committed, not staged).
3. A Snackbar appears with message "Deleted" and an "Undo" action button. The Snackbar auto-dismisses after exactly 5 seconds.
4. **While Snackbar is visible:**
   - Tapping "Undo": The deleted record is re-inserted into Room; the Snackbar dismisses; the row reappears in its correct `captured_at`-ordered position in the `RecyclerView`.
   - Snackbar times out (5 s with no tap): deletion is permanent; no further action.
5. **Second swipe while Snackbar is active:**
   - The current Snackbar is dismissed programmatically (first deletion is now permanent).
   - The second record is deleted from Room immediately.
   - A new 5-second Snackbar appears for the second record only.
6. **Only one active undo at a time.** If a third swipe occurs while the second Snackbar is active, the same resolution applies.

### Business Rules

| Rule | Detail |
|------|--------|
| Deletion timing | Record deleted from DB immediately on swipe, not at Snackbar timeout |
| Undo re-insert position | Re-inserted record is ordered by `captured_at` — if other records were inserted during the undo window, the restored record appears at its chronologically correct position |
| Single active undo | ViewModel holds at most one pending undo record at a time; a new swipe replaces the pending undo (making the previous deletion permanent) |
| Process death | ViewModel pending-undo state is in memory only (`null` on restart); deletion committed to DB is permanent after process death |
| Expanded row swipe | Swipe on an expanded row: the expanded view collapses and the row is deleted; undo restores the record in collapsed state |
| Accessibility | The "Undo" Snackbar action is accessible to TalkBack users; focus is moved to the Snackbar when it appears |

### Edge Cases

| Case | Behavior |
|------|---------|
| Swipe last remaining record | Record is deleted; empty state appears (FSPEC-HIST-04); if undo is tapped, the record reappears and empty state is hidden |
| App backgrounded during undo window | If the OS kills the process, the deletion is permanent on next launch |
| Undo while search is active | Restored record re-appears in the filtered list only if its name matches the current query; if not, it appears on query clear |
| Multiple rapid swipes | Each new swipe immediately commits the prior undo; only the most recently swiped record has an active undo window |

### Acceptance Tests

**AT-HIST-03-A: Immediate DB commit on swipe**

- *Who:* Instrumented test
- *Given:* 5 records in DB
- *When:* Row 3 is swiped
- *Then:* Room DB contains 4 records immediately (before Snackbar times out)

**AT-HIST-03-B: Undo restores record**

- *Who:* Instrumented test
- *Given:* Record "Alpha" deleted, Snackbar visible
- *When:* "Undo" is tapped
- *Then:* DB contains "Alpha"; RecyclerView shows "Alpha" in correct `captured_at` position

**AT-HIST-03-C: Second swipe cancels first undo**

- *Who:* Instrumented test
- *Given:* Record A deleted (Snackbar for A visible); user swipes record B
- *When:* Snackbar for A is dismissed; Snackbar for B appears
- *Then:* DB does not contain A; DB does not contain B; undo for A is no longer possible

**AT-HIST-03-D: Process death — no undo on restart**

- *Who:* Instrumented test
- *Given:* Record C swiped; Snackbar visible; process is terminated
- *When:* App relaunched
- *Then:* Record C is absent from DB and RecyclerView; no Snackbar is shown

---

## FSPEC-HIST-04: Bearing History Screen — Empty State

**Linked requirements:** REQ-CAPTURE-03

### Behavioral Flow

**State A — No records ever saved:**

1. User opens Bearing History tab.
2. RecyclerView is hidden; an empty-state view is shown with:
   - An illustration (compass icon or placeholder graphic)
   - Message: "No bearings yet — capture your first bearing from the compass screen"
3. Search bar is hidden or disabled (no records to search).

**State B — Search yields no results:**

1. Records exist but current search query matches none.
2. RecyclerView is hidden; empty-state view shows: "No bearings match your search"
3. The illustration in State B may be the same as State A or a distinct "no results" graphic.

**Transition: State A → Normal:**

When a new bearing is captured (from any compass tab), the DAO Flow emits; the empty-state view is replaced by the RecyclerView automatically.

### Business Rules

| Rule | Detail |
|------|--------|
| State A vs State B text | The two empty states use distinct message strings |
| RecyclerView visibility | `View.GONE` (not `INVISIBLE`) when empty state is shown |
| Search bar in State A | Hidden or disabled; revealing it serves no purpose when zero records exist |
| Live transition | State A → normal list is reactive (no manual refresh required) |

### Acceptance Tests

**AT-HIST-04-A: State A shown on zero records**

- *Who:* Instrumented test
- *Given:* DB is empty
- *When:* `BearingHistoryFragment` is launched
- *Then:* Empty-state illustration and "No bearings yet — capture your first bearing from the compass screen" are visible; RecyclerView is `GONE`

**AT-HIST-04-B: State A → List transition**

- *Who:* Instrumented test
- *Given:* Empty state is showing
- *When:* A new `BearingRecord` is inserted into Room
- *Then:* Empty state is hidden; RecyclerView shows the new record without user interaction

---

## FSPEC-HIST-05: Interference Badge in History Row

**Linked requirements:** REQ-DETECT-05

### Behavioral Flow

**In list row (collapsed):**
- If `interference_flag = true`: show "⚠ Captured under interference" badge (coloured label/chip, below or beside the confidence badge).
- If `interference_flag = false`: the badge element is `View.GONE` (not just invisible).

**In expanded record detail:**
- All records: show bearing, north type, confidence level text, `captured_at`, name, notes.
- Additionally, if `interference_flag = true`:
  - **Field deviation:** label "Field deviation", value formatted as integer percentage: `(field_deviation_pct × 100)` rounded down to nearest integer, followed by "%" (e.g., stored `0.25` → displayed "25%").
  - **Inclination deviation:** label "Inclination deviation", value formatted as integer degrees followed by "°" (e.g., stored `4.7` → displayed "4°"). No decimal places. Negative values permitted (e.g., stored `-2.3` → displayed "-2°"). Truncation toward zero (floor of absolute value, sign preserved).

### Business Rules

| Rule | Detail |
|------|--------|
| Badge visibility | Badge view is `GONE` for `interference_flag=false`; never just `INVISIBLE` |
| Field deviation format | Stored fractional × 100, integer truncation, "%" suffix; no rounding up |
| Inclination deviation format | Integer truncation toward zero, "°" suffix, label "Inclination deviation" |
| Badge absent on MODERATE/CLEAR records | Records captured under MODERATE or CLEAR interference have `interference_flag=false`; badge must not appear |

### Edge Cases

| Case | Behavior |
|------|---------|
| `field_deviation_pct = 0.0` | Displayed as "0%" |
| `inclination_deviation_deg = 0.0` | Displayed as "0°" |
| `inclination_deviation_deg` negative | Displayed with minus sign: e.g., `-2.3` → "-2°" |
| `field_deviation_pct` very large (e.g., 2.5 = 250%) | Displayed as "250%" — no cap |

### Acceptance Tests

**AT-HIST-05-A: Badge present on flagged record**

- *Who:* Instrumented test
- *Given:* Record with `interference_flag=true`, `field_deviation_pct=0.25`, `inclination_deviation_deg=4.7`
- *When:* Record is visible in list
- *Then:* "⚠ Captured under interference" badge is visible in the row; badge view is not `GONE`

**AT-HIST-05-B: Badge absent on clean record**

- *Who:* Instrumented test
- *Given:* Record with `interference_flag=false`
- *When:* Record is visible in list
- *Then:* Badge view is `GONE`

**AT-HIST-05-C: Expanded detail shows correct formatted values**

- *Who:* Instrumented test
- *Given:* Flagged record with `field_deviation_pct=0.25`, `inclination_deviation_deg=4.7`
- *When:* Row is expanded
- *Then:* Field deviation shows "25%"; inclination deviation shows "4°"

**AT-HIST-05-D: Inclination deviation — negative value**

- *Who:* Unit test
- *Given:* `inclination_deviation_deg=-2.3`
- *When:* Format function is called
- *Then:* Output is "-2°"

---

## FSPEC-CAL-01: Recalibration Banner — Condition A (Age-Based)

**Linked requirements:** REQ-CAL-05

### Banner Host

The age-based recalibration banner appears **exclusively in `BearingHistoryFragment`** (the third tab). It does not appear on the Modern or Luopan compass tabs.

### Behavioral Flow

1. `BearingHistoryFragment` becomes visible (`onResume` or `onViewCreated`).
2. `CompassViewModel.loadCalibrationAge()` is called (see FSPEC-NAV-01 for ViewModel ownership). This method loads the stored `CalibrationRecord` and computes `calibrationAgeDays`.
3. If `calibrationAgeDays > 30`:
   - Banner view becomes `VISIBLE` at the top of the screen (below the search bar, above the list).
   - Banner text: "Your calibration is [N] days old — consider recalibrating"
   - N = `floor(elapsedMs / 86_400_000L)` — integer floor division of elapsed milliseconds. Never rounded up. (31 days 23 h → N=31; 32 days exactly → N=32.)
   - Banner contains two affordances:
     - **Close/X button** (icon button, right side of banner): dismisses the banner without opening CalibrationWizard.
     - **Banner body tap** (anywhere else on the banner): opens `CalibrationWizardActivity` via `ActivityResultLauncher`.
4. If `calibrationAgeDays ≤ 30`: banner is `GONE`.

### Dismiss Mechanics — Condition A

| Affordance | Immediate Effect | Persistence |
|-----------|-----------------|-------------|
| Close/X button | Banner becomes `GONE` | Per-session only (ViewModel flag `calAgeBannerDismissed = true`); banner re-appears on next app launch if condition still holds |
| Banner body tap → CalibrationWizard → `RESULT_OK` | Banner becomes `GONE`; `loadCalibrationAge()` is called to refresh | Permanent until cal_age > 30 days again |
| Banner body tap → CalibrationWizard → `RESULT_CANCELED` / back | CalibrationWizard closes; banner remains visible | No change |

### Re-Display Rules

| Scenario | Banner behavior |
|---------|----------------|
| User dismisses via X, then navigates away from History tab and returns within the same session | Banner does NOT re-appear (ViewModel flag persists for the session) |
| User dismisses via X, then kills and relaunches the app, and cal_age is still >30 days | Banner re-appears (ViewModel flag is reset on process death) |
| User completes recalibration (RESULT_OK) | Banner does not re-appear until cal_age > 30 days again (condition is no longer met) |
| Both Condition A and Condition B are true | Both banners are shown simultaneously as separate banners; they stack vertically |

### Business Rules

| Rule | Detail |
|------|--------|
| No daily cooldown | Age-based banner has no per-day cooldown; it reappears on every launch if the condition holds |
| Confidence impact | When cal_age > 30 days, overall confidence is POOR (`ConfidenceModel.scoreCalibrationAge()` returns POOR; `minOf(scores)` produces POOR overall) |
| N precision | Integer floor; N is never incremented for partial days |

### Acceptance Tests

**AT-CAL-01-A: Banner shown at 31 days**

- *Who:* Instrumented test (FakeClock)
- *Given:* CalibrationRecord.recorded_at set to 31 days ago (via FakeClock)
- *When:* `BearingHistoryFragment` is launched
- *Then:* Banner visible with text "Your calibration is 31 days old — consider recalibrating"

**AT-CAL-01-B: Banner absent at exactly 30 days**

- *Who:* Instrumented test (FakeClock)
- *Given:* CalibrationRecord.recorded_at set to exactly 30 days ago
- *When:* `BearingHistoryFragment` is launched
- *Then:* Age-based banner is `GONE`

**AT-CAL-01-C: N uses floor division (31d 23h → 31)**

- *Who:* Unit test (FakeClock)
- *Given:* elapsed = 31 days + 23 hours in milliseconds
- *When:* `computeCalibrationAgeDays(elapsedMs)` is called
- *Then:* Returns 31

**AT-CAL-01-D: Close button dismisses within session**

- *Who:* Instrumented test
- *Given:* Age banner is visible (31-day-old calibration)
- *When:* User taps the close/X button
- *Then:* Banner becomes `GONE`; user navigates away and returns to History tab; banner is still `GONE`

**AT-CAL-01-E: Banner reappears on next launch after session dismiss**

- *Who:* Instrumented test
- *Given:* Age banner dismissed via X in prior session; cal_age still >30 days
- *When:* App is relaunched; History tab is opened
- *Then:* Banner is visible again

**AT-CAL-01-F: RESULT_OK dismisses and refreshes**

- *Who:* Instrumented test
- *Given:* Age banner visible; CalibrationWizardActivity launched via banner body tap; returns RESULT_OK
- *When:* `onActivityResult` fires
- *Then:* Banner is `GONE`; `loadCalibrationAge()` has been called; updated calibration age reflected

**AT-CAL-01-G: Both banners shown simultaneously**

- *Who:* Instrumented test
- *Given:* cal_age > 30 days AND drift condition active
- *When:* `BearingHistoryFragment` is shown
- *Then:* Both banners are visible; they are distinct, non-merged UI elements

---

## FSPEC-CAL-02: Recalibration Banner — Condition B (Drift-Based)

**Linked requirements:** REQ-CAL-05

### Banner Host

The drift-based recalibration banner appears **exclusively in `BearingHistoryFragment`** (the third tab). It does not appear on the Modern or Luopan compass tabs.

### Behavioral Flow

1. `DriftDetector` runs continuously in the background (injected into `CompassViewModel`; see FSPEC-CAL-03 for state machine).
2. When `DriftDetector` emits `DriftEvent.TRIGGERED`:
   - Check 10-minute cooldown: read `SettingsRepository.driftCooldownTimestampMs`. If `(clock.nowMs() - driftCooldownTimestampMs) < 600_000L`, suppress the banner.
   - If cooldown has elapsed (or never set): the drift banner becomes `VISIBLE` in `BearingHistoryFragment`.
   - Banner text: "Magnetic environment may have changed — recalibrate for best accuracy"
   - Banner contains two affordances:
     - **Close/X button** (icon button, right side of banner): dismisses the banner and starts the 10-minute cooldown.
     - **Banner body tap**: opens `CalibrationWizardActivity` via `ActivityResultLauncher`; on `RESULT_OK`, banner is dismissed and drift detector is fully reset.
3. When `DriftDetector` emits `DriftEvent.RESET` (any precondition violated): banner remains as-is (it does not auto-hide when the drift condition temporarily clears).

### Dismiss Mechanics — Condition B

| Affordance | Immediate Effect | Persistence |
|-----------|-----------------|-------------|
| Close/X button | Banner becomes `GONE`; cooldown timestamp = `clock.nowMs()` written to `SettingsRepository.driftCooldownTimestampMs` | Persists through process death; cooldown expires after 10 minutes |
| Banner body tap → CalibrationWizard → `RESULT_OK` | Banner becomes `GONE`; drift detector fully reset (see "Reset semantics") | `SettingsRepository.driftCooldownTimestampMs` cleared (set to 0L); detector re-armed from fresh state |
| Banner body tap → CalibrationWizard → `RESULT_CANCELED` / back | CalibrationWizard closes; banner remains visible | No cooldown started; no reset |

### Reset Semantics — RESULT_OK

"Reset" on `RESULT_OK` has two components:

1. **60-second drift timer cleared:** `DriftDetector` internal timer is set to 0. The detector will not re-fire until a new 60-second continuous drift window elapses.
2. **10-minute cooldown cleared:** `SettingsRepository.driftCooldownTimestampMs` is set to `0L`. This means if drift conditions persist, the detector can re-arm and the banner can re-appear after a new 60-second window — it will not be blocked by the cooldown.

This ensures a user who recalibrates gets a clean slate: neither a cooldown nor a stale timer carries over from the pre-calibration session.

### Re-Display Rules

| Scenario | Banner behavior |
|---------|----------------|
| User dismisses via X | Cooldown starts; banner hidden for 10 minutes; if drift condition still holds after 10 min, a new 60-second window must elapse before banner re-appears |
| User completes calibration (RESULT_OK) | Cooldown cleared; timer cleared; banner re-arming requires a fresh 60-second drift window |
| Precondition clears during banner visibility | Banner stays visible (auto-hide only on explicit dismiss or RESULT_OK) |
| Both banners active simultaneously | Shown as separate, stacked banners |

### Business Rules

| Rule | Detail |
|------|--------|
| Cooldown scope | The 10-minute cooldown starts only on explicit dismiss (X button) or on RESULT_CANCELED (no cooldown — see table above). RESULT_OK clears the cooldown instead of starting it |
| Cooldown persistence | `SettingsRepository.driftCooldownTimestampMs` stored as Unix epoch milliseconds; survives process death |
| Banner host | BearingHistoryFragment only; not shown on compass tabs |
| Suppression during WARNING interference | `DriftDetector` does not count time toward the 60-second threshold while `InterferenceState = WARNING` (see FSPEC-CAL-03) |

### Acceptance Tests

**AT-CAL-02-A: Banner shown after 60-second drift window**

- *Who:* Unit test (FakeClock, fake DriftDetector)
- *Given:* All preconditions met; 60 seconds elapsed (via FakeClock); measured field > 10% deviation; cooldown = 0
- *When:* DriftDetector emits TRIGGERED
- *Then:* Drift banner becomes `VISIBLE`

**AT-CAL-02-B: Close button starts cooldown**

- *Who:* Instrumented test (FakeClock)
- *Given:* Drift banner visible
- *When:* User taps X button
- *Then:* Banner is `GONE`; `SettingsRepository.driftCooldownTimestampMs` equals `clock.nowMs()` at dismiss time

**AT-CAL-02-C: RESULT_OK resets detector and clears cooldown**

- *Who:* Instrumented test (FakeClock, fake DriftDetector)
- *Given:* Drift banner visible; user taps banner body; CalibrationWizard returns RESULT_OK
- *Then:* Banner is `GONE`; drift detector timer = 0; `SettingsRepository.driftCooldownTimestampMs = 0L`

**AT-CAL-02-D: Banner suppressed while cooldown active**

- *Who:* Unit test (FakeClock)
- *Given:* `driftCooldownTimestampMs` set to 5 minutes ago; DriftDetector emits TRIGGERED
- *When:* ViewModel processes the event
- *Then:* Drift banner remains `GONE` (cooldown has not elapsed)

**AT-CAL-02-E: Banner shown after cooldown expires**

- *Who:* Unit test (FakeClock)
- *Given:* `driftCooldownTimestampMs` set to 11 minutes ago; DriftDetector emits TRIGGERED
- *When:* ViewModel processes the event
- *Then:* Drift banner becomes `VISIBLE` (cooldown has elapsed)

**AT-CAL-02-F: Banner not shown on compass tabs**

- *Who:* Instrumented test
- *Given:* Drift condition active; user is on Modern Compass tab
- *When:* DriftDetector emits TRIGGERED
- *Then:* No drift banner appears on the Modern Compass tab

---

## FSPEC-CAL-03: DriftDetector State Machine

**Linked requirements:** REQ-CAL-05

### Overview

`DriftDetector` is a standalone component, injected into `CompassViewModel`. It is not implemented as an inline stateful field in the ViewModel's sensor loop. It accepts a `Clock` interface dependency, following the `FakeClock` pattern established in `CompassViewModelTest`.

### States

```
[IDLE] ──── all preconditions hold + timer starts ──────► [COUNTING]
[COUNTING] ─ any precondition violated ──────────────────► [IDLE] (timer reset to 0)
[COUNTING] ─ timer > 60s AND field deviation > 10% ──────► [TRIGGERED]
[TRIGGERED] ─ RESULT_OK reset ────────────────────────────► [IDLE] (timer reset to 0)
[TRIGGERED] ─ X dismiss ──────────────────────────────────► [IDLE] (timer reset to 0, cooldown starts)
[TRIGGERED] ─ precondition violated ──────────────────────► [IDLE] (timer reset to 0)
```

All transitions to `[IDLE]` reset the internal elapsed timer to 0.

### Preconditions (all three must hold simultaneously)

| # | Precondition | Metric |
|---|-------------|--------|
| 1 | Device stationary | Variance of combined 3-axis magnitude `sqrt(ax²+ay²+az²)` < 0.01 (m/s²)² over rolling 5-second window (250 samples at 50 Hz). Single accumulator over magnitude series — not three per-axis accumulators |
| 2 | No active WARNING interference | `InterferenceState` from REQ-DETECT-01 is `CLEAR` or `MODERATE`; timer does not count while `InterferenceState = WARNING` |
| 3 | Valid expected field value | `CalibrationRecord.expected_field_ut > 0.0`; a value of 0.0 (from MIGRATION_2_3 default) disables Condition B entirely |

### Timer Behavior

- **No hysteresis:** A single frame where any precondition is violated immediately resets the timer to 0. There is no grace period or hysteresis window.
- **Continuous counting:** The timer increments only during frames where ALL three preconditions hold simultaneously.
- **Threshold:** Timer must exceed 60 seconds continuously. "60 seconds elapsed" means the timer value crosses 60,000 ms; it is not satisfied by 60,000 ms accumulated across non-contiguous windows.
- **Post-trigger:** Once `TRIGGERED` is emitted, the detector transitions to `IDLE` (timer reset). A new 60-second window is required for a subsequent trigger (subject to cooldown).

### Drift Threshold Formula

```
|measured_magnitude_uT − expected_field_ut| / expected_field_ut > 0.10
```

- Evaluated only when `expected_field_ut > 0.0`.
- Measured only when the timer has elapsed (> 60 s); the formula is not continuously evaluated during the counting window.
- Division by zero: prevented by precondition 3.

### Clock Dependency

`DriftDetector` is constructed with a `Clock` interface parameter:

```
interface Clock { fun nowMs(): Long }
```

This is the same `Clock` interface used in `CompassViewModelTest`. In tests, a `FakeClock` is injected to advance time deterministically. In production, `SystemClock` (or equivalent) is injected by the `CompassViewModel.Factory`.

### Acceptance Tests

**AT-CAL-03-A: Timer resets on precondition violation**

- *Who:* Unit test (FakeClock, mock InterferenceState)
- *Given:* Timer at 45 seconds; `InterferenceState` transitions to `WARNING`
- *When:* DriftDetector.onFrame() is called
- *Then:* Internal timer = 0; state = IDLE; TRIGGERED is not emitted

**AT-CAL-03-B: TRIGGERED emitted after 60-second continuous window**

- *Who:* Unit test (FakeClock)
- *Given:* All preconditions hold; `expected_field_ut = 50.0`; `measured_magnitude_uT = 56.0` (12% deviation); FakeClock advances 61 seconds
- *When:* DriftDetector.onFrame() is called at 61 s
- *Then:* DriftEvent.TRIGGERED is emitted

**AT-CAL-03-C: No TRIGGERED if field deviation ≤ 10%**

- *Who:* Unit test (FakeClock)
- *Given:* All preconditions hold; deviation = 9.9%; FakeClock advances 61 seconds
- *When:* DriftDetector.onFrame() is called at 61 s
- *Then:* DriftEvent.TRIGGERED is NOT emitted; state remains COUNTING

**AT-CAL-03-D: expected_field_ut = 0.0 disables Condition B**

- *Who:* Unit test (FakeClock)
- *Given:* `CalibrationRecord.expected_field_ut = 0.0`; all other preconditions met; 61 seconds elapsed
- *When:* DriftDetector.onFrame() is called
- *Then:* DriftEvent.TRIGGERED is NOT emitted (precondition 3 not met)

**AT-CAL-03-E: No hysteresis — single-frame violation resets timer**

- *Who:* Unit test (FakeClock)
- *Given:* Timer at 59 seconds; one frame where stationary variance = 0.015 (above threshold)
- *When:* DriftDetector.onFrame() is called with that frame
- *Then:* Timer = 0; TRIGGERED not emitted; next valid frame starts timer from 0

---

## FSPEC-NAV-01: Navigation Architecture — BearingHistoryFragment

**Linked requirements:** REQ-CAPTURE-03, REQ-CAL-05

### Tab Layout

`BearingHistoryFragment` is added as the **third tab** in `CompassActivity`'s `TabLayout` / `nav_graph.xml`:

```
Tab 0: Modern  (dest_modern  → ModernCompassFragment)
Tab 1: Luopan  (dest_luopan  → LuopanFragment)
Tab 2: History (dest_history → BearingHistoryFragment)   ← new
```

`nav_graph.xml` gains a new `<fragment>` destination `dest_history`. The existing `dest_modern` and `dest_luopan` entries are unmodified.

### ViewModel Ownership — Resolution of SE F-01

`BearingHistoryFragment` uses `activityViewModels()` to obtain the **Activity-scoped `CompassViewModel`**, consistent with `ModernCompassFragment` and `LuopanFragment`.

**Consequence:** `CompassViewModel.loadCalibrationAge()` must be promoted from `private` to `internal` visibility so that `BearingHistoryFragment` (in the same module) can call it. This is a one-line visibility change. No new ViewModel class is required for the banner and calibration-age state.

`BearingHistoryFragment` may additionally own a `BearingHistoryViewModel` for list, search, and delete state (scoped to the fragment), but the recalibration banner state and `loadCalibrationAge()` call are routed through the shared `CompassViewModel`.

### ActivityResultLauncher Registration

`BearingHistoryFragment` registers an `ActivityResultLauncher<Intent>` in `onCreate()` (not `onViewCreated`), using the `ActivityResultContracts.StartActivityForResult()` contract.

**On `RESULT_OK`:**
1. Age-based banner: call `viewModel.loadCalibrationAge()` (where `viewModel` is the shared `CompassViewModel`) → banner dismissed.
2. Drift-based banner: call drift detector reset (timer cleared, cooldown cleared in `SettingsRepository`) → banner dismissed.

**On `RESULT_CANCELED` / back-press:**
- Standard Android back-stack behavior: `CalibrationWizardActivity` finishes; user returns to `BearingHistoryFragment`. No state change to banners.

### Navigation Flow

```
User on History tab
  │
  ├─ Taps age banner body ──► CalibrationWizardActivity
  │                               │
  │                               ├─ RESULT_OK ──────► loadCalibrationAge() → age banner dismissed
  │                               └─ RESULT_CANCELED ─► returns to History tab; banner unchanged
  │
  └─ Taps drift banner body ─► CalibrationWizardActivity
                                  │
                                  ├─ RESULT_OK ──────► drift detector reset → drift banner dismissed
                                  └─ RESULT_CANCELED ─► returns to History tab; banner unchanged
```

### Back-Navigation

Back-press from `BearingHistoryFragment` follows standard Android navigation back-stack behavior (tab switching via `NavController`). No custom back-press handler is required.

### Business Rules

| Rule | Detail |
|------|--------|
| `activityViewModels()` | BearingHistoryFragment uses the same CompassViewModel instance as ModernCompassFragment and LuopanFragment |
| `loadCalibrationAge()` visibility | Promoted from `private` to `internal` in CompassViewModel |
| Launcher registration | Registered in `Fragment.onCreate()` per Android Jetpack best practice |
| FakeClock precedent | DriftDetector's Clock dependency follows the FakeClock pattern from `CompassViewModelTest` (not `ConfidenceModelTest` or calibration tests — those do not use FakeClock) |

### Acceptance Tests

**AT-NAV-01-A: Third tab adds History destination**

- *Who:* Instrumented test
- *Given:* `CompassActivity` is launched
- *When:* User taps the third tab
- *Then:* `BearingHistoryFragment` is the current fragment; nav destination is `dest_history`

**AT-NAV-01-B: Shared CompassViewModel across tabs**

- *Who:* Instrumented test
- *Given:* CompassActivity is running; Modern tab active
- *When:* User switches to History tab
- *Then:* `BearingHistoryFragment` obtains the same `CompassViewModel` instance as `ModernCompassFragment` (verified via identity check or shared state)

**AT-NAV-01-C: RESULT_OK triggers loadCalibrationAge**

- *Who:* Instrumented test (spy on CompassViewModel)
- *Given:* Age banner is visible; CalibrationWizardActivity launched from banner body tap
- *When:* Wizard returns RESULT_OK
- *Then:* `loadCalibrationAge()` was called; banner is dismissed

---

## FSPEC-SENSOR-01: Sensor Capability Logging

**Linked requirements:** REQ-SENSOR-07

### Behavioral Flow

1. On each app launch, `SensorCapabilityLogger` (or equivalent component, called from `CompassActivity.onCreate()`) executes the version-gate check.
2. Read `SettingsRepository.sensorProfileWrittenForVersion` (default 0 if absent).
3. Compare to `BuildConfig.VERSION_CODE`:
   - If `VERSION_CODE > sensorProfileWrittenForVersion`: proceed to write.
   - If `VERSION_CODE ≤ sensorProfileWrittenForVersion`: skip. Do nothing.
4. **Write path:**
   1. Collect device and sensor data (see schema below).
   2. Serialize to pretty-printed JSON (2-space indent).
   3. Write to `Context.getFilesDir() / "sensor_profile.json"` (overwrite if exists).
   4. **Success:** update `SettingsRepository.sensorProfileWrittenForVersion = BuildConfig.VERSION_CODE`.
   5. **Failure (IOException or similar):** log at `Log.e` level. Do NOT update `sensorProfileWrittenForVersion`. This guarantees a retry on the next launch.
5. Return. App startup continues regardless of write success or failure.

### First-Launch Detection

| Condition | Behavior |
|-----------|---------|
| Key absent (fresh install) | Default 0; `VERSION_CODE > 0` → write triggered |
| Key = current VERSION_CODE | Skip write |
| Key < current VERSION_CODE (app upgraded) | Write triggered; file refreshed for new version |
| App data cleared | Key reset to 0 → write triggered on next launch |

### File Schema

File path: `Context.getFilesDir() / "sensor_profile.json"` (internal app storage; not accessible via standard Android file manager on non-rooted devices; not transmitted; not backed up to cloud).

```json
{
  "device_model": "Pixel 4a",
  "device_manufacturer": "Google",
  "android_version": "12",
  "android_api_level": 31,
  "app_version_code": 40,
  "written_at_iso8601": "2026-04-27T08:30:00Z",
  "sensors": [
    {
      "type_constant": 2,
      "name": "BMI160 Magnetometer",
      "vendor": "Bosch",
      "resolution_ut_or_native": 0.15,
      "max_range_native": 2500.0,
      "reporting_mode": "CONTINUOUS"
    }
  ]
}
```

Field mapping:

| JSON field | Source |
|-----------|--------|
| `device_model` | `Build.MODEL` |
| `device_manufacturer` | `Build.MANUFACTURER` |
| `android_version` | `Build.VERSION.RELEASE` |
| `android_api_level` | `Build.VERSION.SDK_INT` |
| `app_version_code` | `BuildConfig.VERSION_CODE` |
| `written_at_iso8601` | ISO-8601 UTC timestamp at write time |
| `sensors[].type_constant` | `Sensor.getType()` |
| `sensors[].name` | `Sensor.getName()` |
| `sensors[].vendor` | `Sensor.getVendor()` |
| `sensors[].resolution_ut_or_native` | `Sensor.getResolution()` in native unit |
| `sensors[].max_range_native` | `Sensor.getMaximumRange()` in native unit |
| `sensors[].reporting_mode` | String representation of `Sensor.getReportingMode()` (e.g., "CONTINUOUS", "ON_CHANGE") |

### SettingsRepository Integration

`sensorProfileWrittenForVersion` must be added as a new `const val` key and `var` property in `SettingsRepository`, consistent with the existing Phase 3 additions block. It must not be implemented as an ad-hoc call at the use site.

### Business Rules

| Rule | Detail |
|------|--------|
| Version gate | `VERSION_CODE > storedVersion` triggers write (strict greater-than; equal skips) |
| Write-failure contract | `Log.e` on failure; version key NOT updated; retry guaranteed on next launch |
| Silent data loss | Not acceptable; failure must be observable in logcat |
| Non-transmission | Guaranteed by existing `NoInternetPermissionCheck` lint rule; not asserted in automated tests |
| File accessibility | Implicit Android storage sandbox guarantee; not asserted in automated tests |
| Sensor list | All sensors returned by `SensorManager.getSensorList(Sensor.TYPE_ALL)` are included |

### Edge Cases

| Case | Behavior |
|------|---------|
| Storage full on first write | Log.e; version key stays 0; retry on next launch |
| Storage full on upgrade write | Log.e; version key stays at prior version; retry on next launch |
| No sensors returned | `sensors` array is empty; file is still written with empty array |
| `written_at_iso8601` timezone | Always UTC ("Z" suffix); device local time is not used |

### Acceptance Tests

**AT-SENSOR-01-A: File written on first launch (key absent)**

- *Who:* Instrumented test
- *Given:* `sensorProfileWrittenForVersion = 0`; `BuildConfig.VERSION_CODE = 40`
- *When:* App launches
- *Then:* `sensor_profile.json` exists in `getFilesDir()`; `sensorProfileWrittenForVersion = 40`; file contains expected top-level fields and non-empty sensors array

**AT-SENSOR-01-B: File not rewritten on same-version relaunch**

- *Who:* Instrumented test
- *Given:* `sensorProfileWrittenForVersion = 40`; `VERSION_CODE = 40`
- *When:* App launches
- *Then:* `sensor_profile.json` is unchanged (write not triggered; timestamp same as prior launch)

**AT-SENSOR-01-C: File rewritten on version upgrade**

- *Who:* Instrumented test
- *Given:* `sensorProfileWrittenForVersion = 39`; `VERSION_CODE = 40`
- *When:* App launches
- *Then:* `sensor_profile.json` contains `"app_version_code": 40`; `sensorProfileWrittenForVersion = 40`

**AT-SENSOR-01-D: File rewritten after data clear**

- *Who:* Instrumented test
- *Given:* `sensorProfileWrittenForVersion` cleared (SharedPreferences cleared); `VERSION_CODE = 40`
- *When:* App launches
- *Then:* `sensor_profile.json` written; `sensorProfileWrittenForVersion = 40`

**AT-SENSOR-01-E: Write failure — key not updated (retry guaranteed)**

- *Who:* Unit test (mock file writer injected into logger)
- *Given:* File write throws `IOException`
- *When:* Logger runs
- *Then:* `sensorProfileWrittenForVersion` is NOT updated (remains at prior value); `Log.e` is called

---

## Open Questions

All open questions from the SE and TE cross-reviews have been resolved in this FSPEC:

| Source | ID | Resolution |
|--------|-----|-----------|
| SE v4 F-01 | ViewModel ownership | Resolved: `BearingHistoryFragment` uses `activityViewModels()` / shared `CompassViewModel`; `loadCalibrationAge()` promoted to `internal`. See FSPEC-NAV-01. |
| SE v4 F-02 | FakeClock reference file | Resolved: FSPEC-CAL-03 references `CompassViewModelTest` as the correct precedent (not `ConfidenceModelTest` or calibration tests). |
| TE v4 F-01 | Banner dismiss affordance | Resolved: Both banners have a close/X button for dismiss-without-navigation. See FSPEC-CAL-01 and FSPEC-CAL-02 dismiss mechanics tables. |
| TE v4 F-02 | "Reset" semantics on RESULT_OK | Resolved: RESULT_OK clears both the 60-second timer and the 10-minute cooldown timestamp (`driftCooldownTimestampMs = 0L`). See FSPEC-CAL-02 reset semantics. |
| TE v4 F-03 | `inclination_deviation_deg` display format | Resolved: integer truncation toward zero, "°" suffix, label "Inclination deviation". See FSPEC-HIST-05. |
| TE v4 F-04 | In-session banner suppression scenario | Resolved: AT-CAL-01-D covers within-session suppression after X dismiss. |
| TE v4 F-05 | Scenario F4 logcat assertion | Resolved: AT-SENSOR-01-E asserts `sensorProfileWrittenForVersion` key state instead of logcat. |
| TE v4 F-06 | Which screen hosts banners | Resolved: Banners appear only in `BearingHistoryFragment`. See FSPEC-CAL-01 and FSPEC-CAL-02 banner host sections. |

---

## Traceability Summary

| User Story | Requirements | FSPECs |
|-----------|-------------|--------|
| US-06 — Reviewing saved bearings | REQ-CAPTURE-03 | FSPEC-HIST-01, FSPEC-HIST-02, FSPEC-HIST-03, FSPEC-HIST-04, FSPEC-HIST-05, FSPEC-NAV-01 |
| US-03 — Calibrating in the field | REQ-CAL-05 | FSPEC-CAL-01, FSPEC-CAL-02, FSPEC-CAL-03, FSPEC-NAV-01 |
| US-04 — Detecting magnetic interference | REQ-DETECT-05 | FSPEC-HIST-05 |
| US-03 (indirect) | REQ-SENSOR-07 | FSPEC-SENSOR-01 |
