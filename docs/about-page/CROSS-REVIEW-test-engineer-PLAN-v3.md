# Cross-Review: test-engineer — PLAN (Iteration 3)

**Reviewer:** test-engineer
**Document reviewed:** docs/about-page/PLAN-about-page.md
**Source of truth:** docs/about-page/REQ-about-page.md v0.2, docs/about-page/TSPEC-about-page.md v0.4
**Date:** 2026-04-28
**Iteration:** 3
**Prior review:** docs/about-page/CROSS-REVIEW-test-engineer-PLAN-v2.md

---

## Prior-Finding Disposition

| ID | Severity | Status | Notes |
|----|----------|--------|-------|
| N-F-01 | Medium | **Resolved** | Task 5.1 now reads "`@Before` (`Intents.init()` only — do NOT navigate to About here; each test navigates as part of its own Given)." The prohibition is explicit and the self-navigation pattern is named. The conflict with Phase 5.3–5.6 preconditions is gone. |
| N-F-02 | Low | **Resolved** | Tasks 4.2, 4.3, and 4.4 all now carry the full canonical path `app/src/main/java/com/luopan/compass/ui/CompassActivity.kt`, matching TSPEC §3. The `...` placeholders that were actively ambiguous (two `CompassActivity.kt` files in the repo) have been removed. |
| N-F-03 | Low | **Resolved** | The Integration Points table now explicitly states "NavHostFragment must retain `android:layout_weight=\"1\"` to fill remaining space. Verified implicitly by Phase 5.3 nav-from tests — if content is not visible, those tests fail." The documentation-silence that F-08/N-F-03 flagged across both prior iterations is now closed with a documented rationale rather than a new test task, which is the lighter of the two options offered in v2. |

---

## Findings

| ID | Severity | Finding | Ref |
|----|----------|---------|-----|
| F-01 | Low | **Task 5.5 uses a placeholder `N` instead of the concrete tab-position values from TSPEC §11.2.** The assertion snippet reads `assertEquals(N, tabLayout.selectedTabPosition)`. The TSPEC §11.2 table specifies the concrete values: `assertEquals(0, …)` for `tabSync_fromModern_tabUnchanged` and `assertEquals(1, …)` for `tabSync_fromLuopan_tabUnchanged`. Using `N` forces the engineer to cross-reference the TSPEC to determine what to assert. Every other Phase 5 task in the PLAN either names the precise view ID or literal value being asserted; `N` is the only remaining placeholder. The task should read `assertEquals(0, …)` and `assertEquals(1, …)` respectively, matching TSPEC §11.2. | PLAN §Phase 5, task 5.5; TSPEC §11.2 |
| F-02 | Low | **Task 5.5 assertion uses a bare `tabLayout` variable that is not in scope inside `onActivity { }`.** The snippet reads `activityRule.scenario.onActivity { assertEquals(N, tabLayout.selectedTabPosition) }`. There is no `tabLayout` variable in the `onActivity` lambda scope; the TSPEC §11.2 uses `it.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition`, which is the only form that compiles. An engineer who copies the PLAN snippet literally will get an unresolved reference at compile time. The task should match the TSPEC form: `it.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition`. | PLAN §Phase 5, task 5.5; TSPEC §11.2 |
| F-03 | Low | **Task 5.4 does not name the asserted view IDs.** The task says "assert originating view displayed" without naming the view. TSPEC §11.2 specifies `onView(withId(R.id.compassRose)).check(matches(isDisplayed()))` for `nav_backFromAbout_returnsToModern` and `onView(withId(R.id.luopanView)).check(matches(isDisplayed()))` for `nav_backFromAbout_returnsToLuopan`. The "originating view" shorthand is underspecified: both the view IDs and the Espresso assertion form are omitted, requiring a TSPEC cross-reference that every other Phase 5 task avoids. The task should state the view ID for each test, matching TSPEC §11.2. | PLAN §Phase 5, task 5.4; TSPEC §11.2 |
| F-04 | Low | **Task 5.2 does not state that the test must navigate to About within its body.** The `@Before` is correctly restricted to `Intents.init()` only (N-F-01 resolved). Task 5.1 explicitly notes its three tests "each open overflow and tap `menu_about` before asserting." Task 5.2, however, only describes the intent stub and the click on `tv_about_website` — it does not say that the test must first open the overflow and tap `menu_about` to reach the About screen. An engineer who reads 5.2 in isolation might write the intent-stub and tap without first navigating to About, causing the `tv_about_website` click to fail with a `NoMatchingViewException`. The task should include a "Given: navigate to About screen (open overflow → tap `menu_about`)" prefix, consistent with tasks 5.1 and 5.3–5.6. | PLAN §Phase 5, task 5.2; TSPEC §11.2 |

---

## TDD Order Verification

| Phase | Red task present before Green? | Status |
|-------|-------------------------------|--------|
| Phase 0 | N/A — build config only | No TDD cycle expected; unchanged |
| Phase 1 | N/A — interface + test double | Plan correctly notes no TDD cycle for infrastructure; unchanged |
| Phase 2 | N/A — XML resources only | No TDD cycle expected; unchanged |
| Phase 3 | 3.1 (Red) → 3.2 (Green) → 3.3 (Red) → 3.4 (Green) → 3.5 (Red) → 3.6 (Green) → 3.7 (Refactor) | Correct. Red-state compilation-failure clarification remains explicit and complete. |
| Phase 4 | No test tasks present | Acceptable — cross-referenced to Phase 5 in the dependency note with three specific named tests. |
| Phase 5 | All test tasks precede dependency on Phase 4 production code | Sequencing enforced by phase-dependency chain. |

---

## Integration Test Coverage at Cross-Module Boundaries

| Boundary | Test present? | Task | Change from v2 |
|----------|--------------|------|----------------|
| `CompassActivity` ↔ `AboutFragment` via NavController | Yes | Phase 5.3 | Unchanged |
| `CompassActivity` toolbar ↔ `AboutFragment` (overflow fires navigation) | Yes | Phase 5.3 | Unchanged |
| `AboutFragment` ↔ `UrlLauncher` / `SystemUrlLauncher` (happy-path intent) | Yes | Phase 5.2 | Unchanged |
| `AboutFragment` ↔ `FakeUrlLauncher` (no-browser Snackbar path) | Yes | Phase 3.5/3.6 (Robolectric) | Unchanged |
| NavController back-stack ↔ originating fragment | Yes | Phase 5.4 | Unchanged |
| NavController `launchSingleTop` flag ↔ back-stack deduplication | Yes | Phase 5.6 | Unchanged |
| `TabLayout` tab-sync ↔ `dest_about` non-tab destination | Yes | Phase 5.5 | Unchanged |
| `MaterialToolbar` height ↔ `NavHostFragment` remaining space | Implicit | Phase 5.3 (documented rationale added) | N-F-03 resolved |

---

## Definition of Done Sufficiency

| DoD item | Sufficient? | Notes |
|----------|-------------|-------|
| `AboutFragmentLogicTest` passes (`./gradlew :app:test`) | Yes | — |
| No-browser Snackbar branch covered by `noBrowser_showsSnackbar` | Yes | Redundant with item 1; harmless |
| `AboutScreenTest` passes on device (`./gradlew :app:connectedAndroidTest`) | Yes | — |
| `nav_launchSingleTop_noStackDuplicate` passes (named explicitly) | Yes | — |
| `./gradlew :app:assembleDebug` succeeds with no warnings | Yes | — |
| No regressions in existing test suites | Yes | — |
| REQ traceability: every AC in REQ-ABOUT-01/02/03 has a passing test | Yes | — |
| REQ-ABOUT-NFR-01 (no network calls) verified by code review | Yes | — |

---

## Positive Observations

- **All three prior Medium and Low findings are fully resolved.** The document has converged cleanly across three iterations of TE review. Each resolution addresses exactly the scope named in the finding — no over-correction, no new gaps introduced.
- **N-F-01 resolution is precisely worded.** The prohibition "do NOT navigate to About here" paired with "each test navigates as part of its own Given" leaves no ambiguity about the `@Before` contract. An engineer reading only the PLAN can implement `AboutScreenTest` correctly without consulting the TSPEC or the `LocationPermissionTest` reference.
- **N-F-03 resolution uses the lighter documented-rationale path.** Stating the implicit coverage chain ("if content is not visible, those tests fail") is sufficient and avoids adding a test task whose only purpose is to verify a layout attribute. The Integration Points table is now actionable as a risk register entry with a named coverage owner.
- **N-F-02 resolution is exact.** All three Phase 4 production source paths now show `app/src/main/java/com/luopan/compass/ui/CompassActivity.kt` — the ambiguity caused by two `CompassActivity.kt` files in the repo is eliminated.
- **Phase 3 TDD cycle specification remains the strongest part of the PLAN.** The Red-state clarification note, the injection-timing note in 3.5, and the explicit compiler-failure checkpoint together give an implementor everything needed to follow the TDD discipline without guessing.
- **PM cross-review v2 confirmed approved** with no new findings. All P0 functional requirements and both NFRs remain traceable to named tasks. The PLAN introduces no behaviour absent from the REQ.

---

## Recommendation

**Approved with Minor Issues**

All four new findings are Low. None blocks implementation; collectively they represent four test-task descriptions where the PLAN is less specific than the TSPEC §11.2 reference table:

- F-01 and F-02 (task 5.5) can be resolved together by replacing `assertEquals(N, tabLayout.selectedTabPosition)` with `activityRule.scenario.onActivity { assertEquals(0, it.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition) }` for the Modern variant and `assertEquals(1, …)` for the Luopan variant.
- F-03 (task 5.4) can be resolved by adding `onView(withId(R.id.compassRose)).check(matches(isDisplayed()))` for Modern and `onView(withId(R.id.luopanView)).check(matches(isDisplayed()))` for Luopan.
- F-04 (task 5.2) can be resolved by prepending "Given: navigate to About screen (open overflow → tap `menu_about`)" to the task description.

All four fixes are copy-paste from TSPEC §11.2; no new design decisions are required. The PLAN may proceed to implementation without a further review cycle if the SE author judges these documentation-precision corrections to be low risk.
