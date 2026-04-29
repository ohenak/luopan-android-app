# Cross-Review: test-engineer — PLAN (Iteration 2)

**Reviewer:** test-engineer
**Document reviewed:** docs/about-page/PLAN-about-page.md
**Source of truth:** docs/about-page/REQ-about-page.md v0.2, docs/about-page/TSPEC-about-page.md v0.4
**Date:** 2026-04-28
**Iteration:** 2
**Prior review:** docs/about-page/CROSS-REVIEW-test-engineer-PLAN.md

---

## Prior-Finding Disposition

| ID | Severity | Status | Notes |
|----|----------|--------|-------|
| F-01 | Medium | **Resolved** | Phase 3 now has an explicit Red-state clarification block (line 79): "the expected Red state is a compilation failure" and "Do not stub-create `AboutFragment` before Task 3.2." The ambiguity that could have prompted an engineer to create a pre-stub is gone. |
| F-02 | Medium | **Resolved** | All test-file paths in Phase 3 tasks now use the full canonical path `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt`, matching TSPEC §2. |
| F-03 | Medium | **Resolved** | All test-file paths in Phase 5 tasks now use the full canonical path `app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt`, matching TSPEC §2. |
| F-04 | Low | **Resolved** | Phase 4 dependency note now reads: "Phase 4 has no dedicated test tasks — all Phase 4 behaviour is verified by the Phase 5 instrumented tests (`nav_fromModern_aboutScreenShown`, `tabSync_*`, `nav_launchSingleTop_noStackDuplicate`)." The coverage chain is stated explicitly. |
| F-05 | Low | **Accepted — deferred to TSPEC** | Phase 5.1 still names the three tests without per-test assertion detail. The PM cross-review v2 independently accepted this deferral. TSPEC §11.2 is the authoritative reference. No action required. |
| F-06 | Low | **Resolved** | Task 3.5 now contains the explicit timing note: "injection must happen *after* `launchFragmentInContainer` returns and *before* `perform(click())`." |
| F-07 | Low | **Resolved** | DoD now contains a dedicated line: "`nav_launchSingleTop_noStackDuplicate` passes — back from About returns to previous screen, not a duplicate About." Traceability to REQ-ABOUT-03 back-navigation AC is explicit. |
| F-08 | Low | **Carried — not addressed** | The Integration Points table still flags "Toolbar height shifts existing layout; verify NavHostFragment still fills remaining space" but no Phase 4 or Phase 5 task covers it. See new finding N-F-02 below for an updated assessment. |

---

## Q-Resolution from Prior Review

| Prior ID | Status | Resolution |
|----------|--------|-----------|
| Q-01 (`material` library dependency) | **Answered by inspection** | `com.google.android.material:material` is already declared in `app/build.gradle.kts` (confirmed in codebase). Phase 0 requires no additional entry for it. Phase 2.4 can proceed without a new dependency task. |
| Q-02 (`BearingHistoryFragment` scope) | **Answered — PM cross-review v2** | BearingHistoryFragment is confirmed out of scope. Phase 5.3 covering Modern Mode and Luopan Mode only is correct. No placeholder task needed. |
| Q-03 (`Intents.init()` ordering safety) | **Partially answered — generates new finding** | On API 26+ (minSdk = 26, confirmed in `app/build.gradle.kts`), NavController navigation between fragments uses in-process fragment transactions, not `Intent` objects. `Intents.init()` before navigation is therefore safe from intent-interception on this project's minimum SDK. However, the PLAN's `@Before` clause also includes "navigate to About" — a navigation step not present in the TSPEC §11.2 description or the `LocationPermissionTest` reference pattern. This creates a correctness conflict with tests 5.3–5.6 (see N-F-01). |

---

## Findings

| ID | Severity | Finding | Ref |
|----|----------|---------|-----|
| N-F-01 | Medium | **`@Before` navigation-to-About conflicts with Phase 5.3–5.6 test preconditions.** Task 5.1 specifies a `@Before` that calls `Intents.init()` *and* navigates to the About screen. The TSPEC §11.2 description ("following the `LocationPermissionTest` pattern") describes a `@Before` that only calls `Intents.init()` — no navigation is part of that pattern. If `@Before` pre-navigates to About, the Activity is already on the About screen at the start of every test method. Tests 5.3 (`nav_fromModern_aboutScreenShown`, `nav_fromLuopan_aboutScreenShown`) require "Modern Mode active" and "Luopan Mode active" as preconditions — both are violated. Tests 5.4 (`nav_backFromAbout_returnsToModern`, `nav_backFromAbout_returnsToLuopan`) navigate to About from a specific originating screen to test back-press — if already on About, the back-stack origin is wrong. Tests 5.5 and 5.6 similarly require a known starting destination. Only tests 5.1 and 5.2 benefit from a pre-navigated About screen. The correct approach is either (a) remove "navigate to About" from `@Before` and let each test that needs About navigate there itself as part of its body, or (b) keep the navigation in `@Before` and add explicit back-navigation setup to 5.3–5.6 (which duplicates setup and is error-prone). Option (a) matches the TSPEC and the `LocationPermissionTest` reference. | PLAN §Phase 5, task 5.1; TSPEC §11.2 |
| N-F-02 | Low | **Production source paths in Phase 4 tasks still use `...` placeholders.** Tasks 4.2, 4.3, and 4.4 all reference `app/src/main/java/.../CompassActivity.kt` with `...` as a path shorthand. Prior F-02 and F-03 resolved this for test file paths in Phases 3 and 5, but the fix was not applied to the production source column in Phase 4. The canonical path per TSPEC §3 (Modified Files) is `app/src/main/java/com/luopan/compass/ui/CompassActivity.kt`. An engineer who navigates only by the PLAN would need to search the project to identify the correct file; there are in fact two `CompassActivity.kt` files in the codebase (one in `com.luopan.compass` and one in `com.luopan.compass.ui`), making the `...` placeholder actively ambiguous. The full path should replace `...` in all three rows. | PLAN §Phase 4, tasks 4.2–4.4; TSPEC §3 |
| N-F-03 | Low | **F-08 (toolbar layout regression) remains unaddressed with no documented rationale.** The Integration Points table correctly identifies "Toolbar height shifts existing layout; verify NavHostFragment still fills remaining space" as a risk. In iteration 1, F-08 was raised as a Low finding with no test task to close it. In this iteration the PLAN is unchanged on this point. The PM cross-review v2 confirms no new findings were raised, but the TE concern was not resolved. The options are: (a) add a single instrumented assertion in Phase 5 (e.g., `toolbarAdded_navHostFragmentFullyVisible`: assert `compassRose` is displayed after toolbar addition from Modern Mode), which closes the coverage gap; or (b) document an explicit rationale for why no test is needed (for example, "any layout regression caused by the toolbar addition would be caught by Phase 5.3 `nav_fromModern_aboutScreenShown` and `nav_fromLuopan_aboutScreenShown` which assert that the primary-screen content views remain visible after the toolbar is added"). Either resolution is acceptable; the current silence is not. | PLAN §Integration Points; PLAN §Phase 4, Phase 5 |

---

## TDD Order Verification

| Phase | Red task present before Green? | Status |
|-------|-------------------------------|--------|
| Phase 0 | N/A — build config only | No TDD cycle expected; unchanged from v1 |
| Phase 1 | N/A — interface + test double | Plan correctly notes no TDD cycle for infrastructure; unchanged |
| Phase 2 | N/A — XML resources only | No TDD cycle expected; unchanged |
| Phase 3 | 3.1 (Red) → 3.2 (Green) → 3.3 (Red) → 3.4 (Green) → 3.5 (Red) → 3.6 (Green) → 3.7 (Refactor) | Correct. Red-state compilation-failure clarification is now explicit. |
| Phase 4 | No test tasks present | Acceptable — explicitly cross-referenced to Phase 5 in the dependency note. |
| Phase 5 | All test tasks precede dependency on Phase 4 production code | Sequencing enforced by phase chain. |

---

## Integration Test Coverage at Cross-Module Boundaries

| Boundary | Test present? | Task | Change from v1 |
|----------|--------------|------|---------------|
| `CompassActivity` ↔ `AboutFragment` via NavController | Yes | Phase 5.3 | Unchanged |
| `CompassActivity` toolbar ↔ `AboutFragment` (overflow fires navigation) | Yes | Phase 5.3 | Unchanged |
| `AboutFragment` ↔ `UrlLauncher` / `SystemUrlLauncher` (happy-path intent) | Yes | Phase 5.2 | Unchanged |
| `AboutFragment` ↔ `FakeUrlLauncher` (no-browser Snackbar path) | Yes | Phase 3.5/3.6 (Robolectric) | Unchanged |
| NavController back-stack ↔ originating fragment | Yes | Phase 5.4 | Unchanged |
| NavController `launchSingleTop` flag ↔ back-stack deduplication | Yes | Phase 5.6 | Unchanged |
| `TabLayout` tab-sync ↔ `dest_about` non-tab destination | Yes | Phase 5.5 | Unchanged |
| `MaterialToolbar` height ↔ `NavHostFragment` remaining space | No explicit test | — | N-F-03 (carried from F-08) |

---

## Definition of Done Sufficiency

| DoD item | Sufficient? | Notes |
|----------|-------------|-------|
| `AboutFragmentLogicTest` passes (`./gradlew :app:test`) | Yes | — |
| No-browser Snackbar branch covered by `noBrowser_showsSnackbar` | Yes | Redundant with item 1; harmless |
| `AboutScreenTest` passes on device (`./gradlew :app:connectedAndroidTest`) | Yes | — |
| `nav_launchSingleTop_noStackDuplicate` passes (named explicitly) | Yes | F-07 from v1 resolved |
| `./gradlew :app:assembleDebug` succeeds with no warnings | Yes | — |
| No regressions in existing test suites | Yes | — |
| REQ traceability: every AC in REQ-ABOUT-01/02/03 has a passing test | Yes | — |
| REQ-ABOUT-NFR-01 (no network calls) verified by code review | Yes | F-01 from PM review v1 resolved |

---

## Positive Observations

- **All three prior Medium findings are fully resolved.** The Red-state clarification note for Phase 3 is precise and actionable; it names both the expected state ("compilation failure") and the anti-pattern to avoid ("do not stub-create `AboutFragment` before Task 3.2"). An engineer reading only the PLAN can follow the TDD cycle correctly without cross-referencing the TSPEC.
- **Injection timing is now unambiguous.** Task 3.5 states the `scenario.onFragment { }` assignment must happen "after `launchFragmentInContainer` returns and before `perform(click())`" — this exactly matches the TSPEC §11.1 code sample and the positive observation in TSPEC cross-review v3. The most complex Robolectric pattern in the plan is now fully specified.
- **Phase 4 test coverage rationale is explicit and correct.** The dependency note names three specific Phase 5 tests (`nav_fromModern_aboutScreenShown`, `tabSync_*`, `nav_launchSingleTop_noStackDuplicate`) as the coverage chain for Phase 4 activity integration tasks. This gives reviewers and engineers a clear traceability path.
- **DoD is now comprehensive.** The addition of the `nav_launchSingleTop_noStackDuplicate` named line and the REQ-ABOUT-NFR-01 code-review gate closes the traceability gaps flagged by both prior TE and PM reviewers. The DoD can now be used as a direct implementation checklist.
- **PM cross-review v2 confirms no product-scope drift.** Every P0 functional requirement and both NFRs remain fully traceable to named tasks. The PLAN introduces no behaviour absent from the REQ.
- **Q-01 and Q-02 are both cleanly closed.** The `material` library dependency is confirmed already present; no Phase 0 addition is needed. BearingHistoryFragment is confirmed out of scope; Phase 5.3 coverage is complete as written.

---

## Recommendation

**Approved with Minor Issues**

N-F-01 (Medium) must be addressed before implementation begins: "navigate to About" should be removed from the `@Before` body of Task 5.1 so that tests 5.3–5.6 start with the correct Activity state. The `@Before` should contain only `Intents.init()`, matching the TSPEC §11.2 description and the `LocationPermissionTest` reference pattern; each test that requires the About screen as a precondition must navigate there within its own body.

N-F-02 and N-F-03 are documentation-precision gaps. N-F-02 (truncated `CompassActivity.kt` path) risks file-location confusion given two `CompassActivity.kt` files exist in the repo; it should be corrected before implementation. N-F-03 (toolbar layout risk with no test or documented rationale) may be closed with a one-line rationale rather than a new test task. Neither blocks implementation if N-F-01 is resolved first.
