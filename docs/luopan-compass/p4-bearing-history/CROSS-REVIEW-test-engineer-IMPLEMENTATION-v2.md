# Cross-Review: Test Engineer — Implementation (v2)
Feature: p4-bearing-history
Date: 2026-04-28
Reviewer Role: Test Engineer
Document Reviewed: Implementation (codebase + test suite) — Second Iteration
Reference: CROSS-REVIEW-test-engineer-IMPLEMENTATION.md (v1), PROPERTIES-luopan-p4-bearing-history.md (v0.4)

---

## Purpose

This is the second iteration of the TE IMPLEMENTATION cross-review. The optimizer addressed all three High findings and all five Medium findings from v1. This review verifies each resolution and checks for issues introduced by the optimizer's changes.

---

## High Finding Verification

### TE-F-01 — PROP-HIST-064 Reclassification (Resolved)

**v1 finding:** PROP-HIST-064 was reclassified to E2E in PROPERTIES v0.4 but the implementation exposed `BearingHistoryViewModel.computeListState()` as a JVM-testable companion function; the four required Espresso sub-tests were absent.

**Optimizer action:** PROPERTIES updated (§1.7, §6.2) to reclassify PROP-HIST-064 back to Unit, citing this review as the authority. `BearingHistoryViewModelTest.kt` already contained all four branch tests.

**Verification:** Confirmed. `BearingHistoryViewModelTest.kt` lines 266–301 contain four clearly named tests:
- Branch A (`updateListState branch A - zero records and no search - shows only empty-no-records`): asserts `searchBarVisible=false`, `recyclerViewVisible=false`, `emptyNoRecordsVisible=true`, `emptyNoResultsVisible=false`. Correct.
- Branch B (`updateListState branch B - zero records and active search - shows only empty-no-results`): asserts `searchBarVisible=true`, `recyclerViewVisible=false`, `emptyNoRecordsVisible=false`, `emptyNoResultsVisible=true`. Correct.
- Branch C (`updateListState branch C - records present and no search - shows list and search bar`): asserts both visible flags true, both empty-view flags false. Correct.
- Branch D (`updateListState branch D - records present and active search - shows list`): identical visibility assertions to C with a non-empty query. Correct.

All four branches call `BearingHistoryViewModel.computeListState()` directly via a private test wrapper at line 252. The PROPERTIES pass condition matches the implementation exactly.

**Status: RESOLVED.**

---

### TE-F-02 — PROP-HIST-042 Snackbar Duration (Resolved with Qualification)

**v1 finding:** No test asserted the 5-second Snackbar duration; the constant `5000` existed in production code but was not exercised by any test.

**Optimizer action:** `UNDO_SNACKBAR_DURATION_MS = 5000` extracted as `const val` in `BearingHistoryFragment` companion object (line 49). A unit test added to `BearingAdapterFormatTest.kt` (lines 127–133) asserts `BearingHistoryFragment.UNDO_SNACKBAR_DURATION_MS == 5000`.

**Verification:** Confirmed.

- `BearingHistoryFragment.kt` line 43: `companion object` contains `const val UNDO_SNACKBAR_DURATION_MS = 5000`.
- `BearingHistoryFragment.kt` line 152: `Snackbar.make(...)` uses `UNDO_SNACKBAR_DURATION_MS`, not the literal.
- `BearingAdapterFormatTest.kt` lines 127–133: test correctly asserts the constant value equals `5000` and names PROP-HIST-042 in the javadoc.

**Qualification:** The optimizer correctly deferred the E2E user-observable timing assertion (Snackbar gone after 5100 ms) to Phase 5, with a comment in the test noting this. The constant-value unit test locks down the contract at the code level but does not verify that the Snackbar actually dismisses at 5 seconds in the running UI — that gap remains open and is explicitly noted. This is an acceptable deferral given the Hilt dependency required for full instrumented setup, but the Phase 5 ticket must include the E2E timing assertion to close PROP-HIST-042 completely. The PROPERTIES pass condition for PROP-HIST-042 still specifies an E2E behavioral assertion (Snackbar gone after 5100 ms); the constant-value unit test satisfies only a partial sub-requirement.

**Status: RESOLVED (partially — constant locked down; E2E timing deferred to Phase 5 with acknowledged gap).**

---

### TE-F-03 — AT-HIST-01-C and AT-HIST-05-C (Resolved)

**v1 finding:** AT-HIST-01-C (mutual exclusion when two rows tapped) and AT-HIST-05-C (expanded deviation format) were `@Ignore`d in E2E tests but could be implemented at unit level without Hilt.

**Optimizer action:** Both tests added to `BearingAdapterFormatTest.kt` as unit tests using `RecordingBearingAdapter`.

**Verification — AT-HIST-01-C:** `BearingAdapterFormatTest.kt` lines 172–191. Test expands item at position 0, then item at position 2, and asserts:
- After first expand: exactly one `notifyItemChanged` call at position 0.
- After second expand: two total `notifyItemChanged` calls (cumulative list size 2, covering collapse of 0 and expand of 2).
- `notifyDataSetChangedCount == 0` throughout.

The implementation correctly verifies mutual exclusion via the `RecordingBearingAdapter` delegation pattern. One minor observation: the test uses the cumulative `notifyItemChangedPositions` list without resetting between expands. `afterFirstExpand` has size 1 (position 0), and `afterSecondExpand` has size 2 (positions 0 and 2). The assertions at lines 185 and 188 confirm this correctly. The test is sound.

**Verification — AT-HIST-05-C:** `BearingAdapterFormatTest.kt` lines 98–113. Two sub-cases: stored `0.25f` → `"25%"` (canonical case from PROP-HIST-052), and stored `0.127f` → `"12%"` (truncation correctness). Both assert `BearingAdapter.formatFieldDeviation()` directly. These are pure function assertions with no View dependency — the unit-level implementation is equivalent to E2E for the format logic.

**Status: RESOLVED.**

---

## Medium Finding Verification

### TE-M-01 — PROP-SENSOR-038 Named Constant (Resolved)

**v1 finding:** `SettingsRepositoryTest.kt` covered the property round-trip but did not explicitly assert the constant value, so a wrong inline literal would have passed.

**Optimizer action:** Test added at `SettingsRepositoryTest.kt` lines 113–119.

**Verification:** Confirmed. Test name: `` `PROP-SENSOR-038 KEY_SENSOR_PROFILE_WRITTEN_FOR_VERSION is named constant with correct value` ``. It asserts `assertEquals("sensor_profile_written_for_version", SettingsRepository.KEY_SENSOR_PROFILE_WRITTEN_FOR_VERSION)`. The test will fail at compile time if the companion object constant is removed and at test time if the string value is changed. Exactly the contract PROP-SENSOR-038 requires.

**Status: RESOLVED.**

---

### TE-M-02 — Thread.sleep Flakiness (Mitigated)

**v1 finding:** `Thread.sleep(100L)` in `onCalibrationCompleteFromHistory` test was a flakiness risk on loaded CI machines.

**Optimizer action:** Increased to `Thread.sleep(500L)` with an explicit comment at `CompassViewModelDriftTest.kt` line 373–375: "Increased from 100 ms to 500 ms to reduce flakiness on heavily-loaded CI machines (TE CR-IMPL F-05). Full fix: inject IO dispatcher in Phase 5 Hilt migration."

**Verification:** Confirmed at line 375. The root cause (non-injected `Dispatchers.IO`) is unchanged; the mitigation is pragmatic and the TODO is clearly tracked. Acceptable for Phase 4.

**Status: MITIGATED (Phase 5 dispatcher-injection task remains open).**

---

### TE-M-03 — PROP-HIST-044 Two-Snackbar Interaction (Deferred)

**v1 finding:** PROP-HIST-044 (second swipe commits first deletion) is `@Ignore`d at line 174 of `BearingHistorySwipeTest.kt`. The ViewModel-level analog (PROP-HIST-045) is covered in `BearingHistoryViewModelTest.kt` lines 385–408.

**Optimizer action:** Deferred — declared as requiring real Activity and Hilt.

**Assessment:** The deferral is genuine. The two-Snackbar timing constraint (first Snackbar must dismiss when second swipe fires) requires the real Snackbar lifecycle, which runs on the main thread against a real Activity. `ActivityScenario` without Hilt cannot seed the DAO with controlled records deterministically enough to test concurrent Snackbar state. PROP-HIST-045 unit coverage is verified and covers the ViewModel invariant ("only one pending undo at a time"). The UI-level mutual exclusion of Snackbars is a non-trivial gap but not a data-correctness risk — the ViewModel enforces the single-undo invariant, and the Fragment's Snackbar wiring is straightforward.

**Status: ACCEPTABLE DEFERRAL. Phase 5 ticket must include the instrumented two-Snackbar test.**

---

### TE-M-04 — PROP-HIST-072 submitList Runtime Assertion (Deferred)

**v1 finding:** PROP-HIST-072's second pass condition (after `adapter.submitList(listOf(record1, record2))`, `notifyDataSetChangedCount == 0`) was absent because `RecordingBearingAdapter` uses delegation and cannot intercept `ListAdapter.submitList()`.

**Optimizer action:** Deferred. `BearingAdapterFormatTest.kt` contains the structural assertion (`adapter is ListAdapter<*, *>`) at line 138 but does not contain the `submitList()` runtime assertion.

**Assessment:** The deferral is technically justified. `RecordingBearingAdapter` wraps `BearingAdapter.toggleExpanded()` via callback injection — it does not subclass `BearingAdapter` and therefore cannot override `ListAdapter.submitList()`. Adding the `submitList` assertion would require either (a) subclassing `BearingAdapter` under Robolectric (which requires the full Android runtime for `RecyclerView.Adapter` initialization), or (b) using `RecyclerView` directly in an instrumented test. Neither is trivially achievable in a JVM unit test without significant test infrastructure changes.

The structural `is ListAdapter` assertion confirms the architectural contract at the class level. `DiffUtil.ItemCallback` is required by `ListAdapter` at construction, so the structural check is meaningful. The absence of the runtime `submitList` assertion is a minor gap — real production behavior (efficient diffing) is implicitly guaranteed by `ListAdapter`'s superclass implementation once the structural constraint is satisfied.

**Status: ACCEPTABLE DEFERRAL. PROPERTIES pass condition note already acknowledges this gap (v0.4 implementation note on PROP-HIST-072). No new tracking action required beyond the existing note.**

---

### TE-M-05 — PROP-NAV-004 Explicit NavController Assertion (Deferred)

**v1 finding:** PROP-NAV-004 (`NavController.navigate(R.id.dest_history)` without throwing) was implicitly satisfied by AT-NAV-01-A but not explicitly tested with a named `NavController.navigate(R.id.dest_history)` call.

**Optimizer action:** Deferred. `NavigationTabTest.kt` AT-NAV-01-A (lines 48–57) navigates to History tab by tapping the "History" text, which implicitly exercises the NavController. No explicit `onActivity { navController.navigate(R.id.dest_history) }` assertion was added.

**Assessment:** The deferral is acceptable. AT-NAV-01-A already passes if and only if `R.id.dest_history` is a valid NavGraph destination — the empty-state text `empty_no_bearings` is only visible if `BearingHistoryFragment` inflated successfully, which requires a correct nav graph entry. The implicit coverage is sufficient for Phase 4. The recommendation to make this explicit (low effort) stands as a Phase 5 polish item but does not block Phase 4 sign-off.

**Status: ACCEPTABLE DEFERRAL.**

---

## New Issues from Optimizer Changes

### NI-01 — AT-HIST-01-C Cumulative Position List Assertion Style (Low)

**Location:** `BearingAdapterFormatTest.kt` lines 185–190.

**Issue:** The test asserts `afterFirstExpand.size == 1` and `afterSecondExpand.size == 2` but `afterSecondExpand` is the same cumulative list as `afterFirstExpand` extended by one more entry (captured via `toList()` snapshots). The assertion at line 189 checks `afterFirstExpand[0] == 0` correctly, but no assertion confirms that `afterSecondExpand` contains positions `{0, 2}` — only the total count of 2 is checked. A buggy adapter that called `notifyItemChanged(0)` twice would also produce `afterSecondExpand.size == 2` with the wrong positions.

**Recommendation:** Add assertions on the specific positions in `afterSecondExpand`:
```kotlin
assertTrue("Second expand must notify position 0 (collapse)", afterSecondExpand.contains(0))
assertTrue("Second expand must notify position 2 (expand)", afterSecondExpand.contains(2))
```
This is a minor gap — the existing `notifyDataSetChangedCount == 0` check prevents the worst regression — but the position-content assertion would lock down the exact mutual-exclusion contract.

**Severity: Low.**

---

### NI-02 — PROP-HIST-042 E2E Gap Not Tracked in PROPERTIES (Low)

**Location:** `PROPERTIES-luopan-p4-bearing-history.md`, PROP-HIST-042 pass condition; `BearingAdapterFormatTest.kt` lines 127–133.

**Issue:** The optimizer's constant-value unit test partially satisfies PROP-HIST-042 but the PROPERTIES pass condition still specifies the full E2E behavioral assertion (Snackbar gone after 5100 ms). The test file comment acknowledges the E2E gap, but PROPERTIES does not record an interim pass state or note that Phase 4 delivery satisfies the constant-level sub-condition only. A future reviewer reading PROPERTIES would see a mismatch between the pass condition and what is actually tested.

**Recommendation:** Add a note to PROP-HIST-042 in PROPERTIES (analogous to the TE CR-IMPL resolution notes in §6.1) stating: "Phase 4 delivers partial satisfaction via constant-value unit test in `BearingAdapterFormatTest.kt`; E2E timing assertion (Snackbar gone at 5100 ms) deferred to Phase 5."

**Severity: Low.**

---

## Summary of All Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| TE-F-01 (PROP-HIST-064) | High | RESOLVED | All four `computeListState` branches verified in `BearingHistoryViewModelTest.kt` |
| TE-F-02 (PROP-HIST-042) | High | RESOLVED (partial) | Constant locked down; E2E timing deferred to Phase 5 with explicit comment |
| TE-F-03 (AT-HIST-01-C, AT-HIST-05-C) | High | RESOLVED | Both tests added to `BearingAdapterFormatTest.kt` as unit tests |
| TE-M-01 (PROP-SENSOR-038) | Medium | RESOLVED | Named constant assertion added and verified |
| TE-M-02 (Thread.sleep) | Medium | MITIGATED | Increased to 500 ms; Phase 5 dispatcher injection tracked |
| TE-M-03 (PROP-HIST-044) | Medium | ACCEPTABLE DEFERRAL | Hilt required; PROP-HIST-045 unit coverage confirmed |
| TE-M-04 (PROP-HIST-072 submitList) | Medium | ACCEPTABLE DEFERRAL | Delegation pattern prevents interception; structural check sufficient |
| TE-M-05 (PROP-NAV-004) | Medium | ACCEPTABLE DEFERRAL | Implicit coverage via AT-NAV-01-A confirmed |
| NI-01 (AT-HIST-01-C positions) | Low | NEW — Open | Position-content assertions missing; count-only assertion allows wrong-position bug |
| NI-02 (PROP-HIST-042 PROPERTIES gap) | Low | NEW — Open | PROPERTIES pass condition not updated to reflect partial Phase 4 delivery |

---

## Recommendation

**Approved with Minor Issues**

All three High findings from v1 are resolved. The `computeListState` four-branch unit tests exist and are complete (TE-F-01). The `UNDO_SNACKBAR_DURATION_MS` constant is extracted and its value is locked by a named unit test (TE-F-02). AT-HIST-01-C and AT-HIST-05-C are implemented as unit tests using `RecordingBearingAdapter` (TE-F-03).

The five Medium findings are either resolved (TE-M-01) or represent genuine Phase 5 blockers correctly deferred with Hilt dependency acknowledgment (TE-M-02 through TE-M-05). No deferred Medium finding conceals a data-correctness risk — the ViewModel invariants that underpin each deferred UI test are verified at unit level.

Two new Low findings (NI-01 and NI-02) were introduced by the optimizer's changes. NI-01 (missing position-content assertions in the AT-HIST-01-C test) is a minor test-completeness gap that does not affect production code. NI-02 (PROPERTIES pass condition not updated to reflect partial PROP-HIST-042 delivery) is a documentation gap. Neither blocks Phase 4 delivery.

**Phase 4 can be declared done. The following must be tracked as Phase 5 mandatory work:**

1. E2E Snackbar timing test for PROP-HIST-042 (Snackbar gone at 5100 ms, requires Hilt).
2. Two-Snackbar concurrent-state instrumented test for PROP-HIST-044.
3. IO dispatcher injection into `CompassViewModel` to replace `Thread.sleep(500L)` with `advanceUntilIdle()`.
4. Explicit `NavController.navigate(R.id.dest_history)` assertion for PROP-NAV-004.
5. Position-content assertions in AT-HIST-01-C (NI-01 — low effort, can be done in Phase 4 as a polish item).
6. PROPERTIES note on PROP-HIST-042 partial delivery (NI-02 — one-line PROPERTIES update).
