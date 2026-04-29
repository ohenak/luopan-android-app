# Cross-Review: test-engineer — PLAN

**Reviewer:** test-engineer
**Document reviewed:** docs/about-page/PLAN-about-page.md
**Source of truth:** docs/about-page/REQ-about-page.md v0.2, docs/about-page/TSPEC-about-page.md v0.4
**Date:** 2026-04-28
**Iteration:** 1

---

## Scope

Testing-lens review: TDD ordering, test task completeness, integration test coverage at cross-module boundaries, Definition of Done sufficiency, and test specification detail sufficient for a blind implementor.

---

## Findings

| ID | Severity | Finding | Ref |
|----|----------|---------|-----|
| F-01 | Medium | **Phase 3 TDD cycle omits a Red task for `websiteUrl_isYijiStudio` compiling against a non-existent class.** Task 3.1 states "Test fails: `AboutFragment` does not exist yet," correctly marking it Red. However the task also specifies `@RunWith(RobolectricTestRunner::class)` on the same class that references `AboutFragment.WEBSITE_URL`. If `AboutFragment` does not exist, the *entire test class* fails to compile, not merely the one test method. The plan has no explicit note that compilation failure is the expected Red state for the whole file, nor a compiler-error checkpoint for the engineer. An implementor reading only 3.1 may attempt to declare a stub `AboutFragment` to allow the file to compile before 3.2, inadvertently skipping the pure Red checkpoint. The task should clarify that a compilation error is the expected Red state, or introduce a compile-only stub step as a named task preceding 3.2. | PLAN §Phase 3 |
| F-02 | Medium | **`AboutFragmentLogicTest` test-file path is not fully qualified in the plan.** Tasks 3.1–3.7 all reference the test file as `app/src/test/.../AboutFragmentLogicTest.kt` with `...` as a placeholder for the package path. The TSPEC §2 specifies the canonical path `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt`. Using a truncated path in the PLAN means an engineer relying solely on the PLAN must cross-reference the TSPEC to create the file in the correct location. The plan should spell out the full path to match TSPEC §2 and eliminate ambiguity. | PLAN §Phase 3; TSPEC §2 |
| F-03 | Medium | **`AboutScreenTest` test-file path is not fully qualified.** Same issue as F-02: tasks 5.1–5.6 reference `app/src/androidTest/.../AboutScreenTest.kt`. The TSPEC §2 canonical path is `app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt`. Full path must appear in the plan. | PLAN §Phase 5; TSPEC §2 |
| F-04 | Low | **Phase 4 tasks have no corresponding test tasks.** Tasks 4.1–4.4 implement nav graph wiring, toolbar setup, `MenuProvider` registration, and a code comment. None of these tasks carries a test file column entry. Phase 5 instrumented tests depend on all of Phase 4 being correct, but within Phase 4 itself there is no Red → Green discipline for the Activity-level changes. This is an acceptable gap for pure wiring tasks, but the plan should explicitly state that Phase 4 tasks are verified by Phase 5 tests and include a cross-reference note (e.g., "Phase 4.1–4.4: verified by Phase 5.1–5.6 instrumented tests") so reviewers and engineers know the coverage chain is intentional rather than an oversight. | PLAN §Phase 4, §Phase 5 |
| F-05 | Low | **Phase 5.1 task omits per-test assertion detail.** The task text names three tests (`content_studioNameVisible`, `content_descriptionVisible`, `content_websiteLabelVisible`) but does not state the assertion each performs. The TSPEC §11.2 table provides full Given/Action/Assert rows for all three. An engineer reading only the PLAN cannot verify which string resource each test checks without consulting the TSPEC, which undermines the PLAN's goal of being self-contained for implementation. The assertion for each test should be included inline (e.g., "`onView(withText(R.string.about_studio_name)).check(matches(isDisplayed()))`"). This finding aligns with PM cross-review F-02 on the same task. | PLAN §Phase 5; TSPEC §11.2; REQ-ABOUT-01 |
| F-06 | Low | **`noBrowser_showsSnackbar` injection timing is not stated in Phase 3.5.** The task instructs the engineer to set `fragment.urlLauncher = FakeUrlLauncher(result = NoBrowserFound)` inside `scenario.onFragment { }`, but does not note that the assignment must occur *after* `launchFragmentInContainer` returns (so `onAttach` has already run `SystemUrlLauncher` assignment) and *before* the click is dispatched. The TSPEC §11.1 code snippet and §11.1 positive observation in TSPEC cross-review v3 make this ordering clear, but the PLAN task alone does not. A misplaced `urlLauncher` assignment (e.g., inside `onAttach` via subclass) could cause the test to pass for the wrong reason or fail flakily. The task should include the note: "assignment in `scenario.onFragment { }` replaces the `SystemUrlLauncher` set in `onAttach`; it must occur after `launchFragmentInContainer` returns and before `perform(click())`." | PLAN §Phase 3, task 3.5; TSPEC §11.1 |
| F-07 | Low | **Definition of Done has no explicit verification step for the `launchSingleTop` back-stack property.** The DoD checklist covers Robolectric test passage, instrumented test passage, build success, no regressions, and REQ traceability. `nav_launchSingleTop_noStackDuplicate` (Phase 5.6) is the test that verifies the `launchSingleTop` flag works correctly, and it is exercised by the "`./gradlew :app:connectedAndroidTest` passes" line — but no DoD item calls it out by name. REQ-ABOUT-03's back-navigation AC ("About screen is removed from the back stack") maps directly to this test. Adding a specific DoD line "`nav_launchSingleTop_noStackDuplicate` passes — single back press from About returns to the originating screen, not a second About instance" would close the traceability gap identified in the TSPEC requirements traceability table §12. | PLAN §Definition of Done; REQ-ABOUT-03; TSPEC §12 |
| F-08 | Low | **No integration test task for the `MaterialToolbar` height shifting the `NavHostFragment` layout.** The Integration Points table (PLAN §Integration Points) explicitly flags "Toolbar height shifts existing layout; verify NavHostFragment still fills remaining space" as a risk. However, no Phase 4 or Phase 5 task covers visual or structural verification of this concern. There is no test assertion checking that `NavHostFragment` (or the `compassRose` / `luopanView` views within it) remains fully visible after the toolbar is added. The existing `content_studioNameVisible` and related Phase 5.1 tests partially cover this for the About screen, but not for Modern or Luopan mode after the toolbar is introduced. A single instrumented assertion on a pre-existing screen (e.g., asserting `compassRose` is displayed after toolbar addition) would close this gap, or the risk note should reference the specific Phase 5 tests that implicitly cover it. | PLAN §Integration Points; PLAN §Phase 4 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Phase 2 resources are marked parallelisable, but task 2.4 adds `MaterialToolbar` to `activity_compass.xml`. Will the `MaterialToolbar` dependency `com.google.android.material:material` already be present in `build.gradle.kts`, or does Phase 0 need a task for it? If the dependency is absent, Phase 2.4 will fail to compile before any test in Phase 5 can run. |
| Q-02 | PM cross-review Q-01 asks whether `BearingHistoryFragment` is confirmed out of scope for this delivery window. If it is in scope, Phase 5.3 (`nav_fromModern_aboutScreenShown`, `nav_fromLuopan_aboutScreenShown`) needs a third test (`nav_fromBearingHistory_aboutScreenShown`) as called out in REQ §6 Assumptions. Should a conditional placeholder task be added to Phase 5? |
| Q-03 | Phase 5.1 `@Before` specifies both `Intents.init()` and "navigate to About." `Intents.init()` stubs all outgoing intents from the point of call. If navigation to About triggers an internal NavController intent (on older API levels), stubbing before navigation might interfere. Is the `@Before` ordering — `Intents.init()` first, then navigation — confirmed safe against the project's minimum SDK level, or should `Intents.init()` be deferred to individual tests that require it (e.g., only `websiteLink_firesActionViewIntent`)? |

---

## TDD Order Verification

| Phase | Red task present before Green? | Notes |
|-------|-------------------------------|-------|
| Phase 0 | N/A — build config only | No TDD cycle expected |
| Phase 1 | N/A — interface + test double; plan correctly notes no TDD cycle needed for infrastructure | Acceptable |
| Phase 2 | N/A — XML resources only | No TDD cycle expected |
| Phase 3 | Partial — 3.1 (Red), 3.2 (Green), 3.3 (Red), 3.4 (Green), 3.5 (Red), 3.6 (Green), 3.7 (Refactor) | TDD ordering is correct. See F-01 for compilation-error Red-state ambiguity. |
| Phase 4 | No test tasks present | See F-04 — acceptable gap if explicitly cross-referenced to Phase 5 |
| Phase 5 | All test tasks precede their dependency on Phase 4 production code | Sequencing enforced by phase-dependency chain |

---

## Integration Test Coverage at Cross-Module Boundaries

| Boundary | Test present? | Task |
|----------|--------------|------|
| `CompassActivity` ↔ `AboutFragment` via NavController | Yes | Phase 5.3 |
| `CompassActivity` toolbar ↔ `AboutFragment` (overflow menu fires navigation) | Yes | Phase 5.3 |
| `AboutFragment` ↔ `UrlLauncher` / `SystemUrlLauncher` (happy-path intent) | Yes | Phase 5.2 |
| `AboutFragment` ↔ `FakeUrlLauncher` (no-browser Snackbar path) | Yes | Phase 3.5/3.6 (Robolectric) |
| NavController back-stack ↔ originating fragment | Yes | Phase 5.4 |
| NavController `launchSingleTop` flag ↔ back-stack deduplication | Yes | Phase 5.6 |
| `TabLayout` tab-sync ↔ `dest_about` non-tab destination | Yes | Phase 5.5 |
| `MaterialToolbar` height ↔ `NavHostFragment` remaining space | No explicit test | See F-08 |

---

## Definition of Done Sufficiency

The current DoD contains five items. Assessment:

| DoD item | Sufficient? | Gap |
|----------|-------------|-----|
| `AboutFragmentLogicTest` passes | Yes | — |
| `noBrowser_showsSnackbar` covered | Yes | Redundant with item 1 but harmless |
| `AboutScreenTest` passes on device | Yes | — |
| `assembleDebug` succeeds | Yes | — |
| No regressions in full test suites | Yes | — |
| REQ traceability: every AC in REQ-ABOUT-01/02/03 | Partial | `launchSingleTop` back-stack test not named explicitly (F-07); NFR-01 static-content verification not named (PM F-01) |

---

## Positive Observations

- **Strict phase-dependency chain enforces TDD sequencing at the macro level.** Phase 0 → 1 → 2 → 3 → 4 → 5 prevents any test in Phase 3 from being written after its production code, and Phase 5 instrumented tests cannot be written until Phase 4 integration wiring exists. This is a sound structure.
- **All three P0 ACs have named test tasks.** REQ-ABOUT-01 → Phase 5.1; REQ-ABOUT-02 happy path → Phase 5.2; REQ-ABOUT-02 no-browser → Phase 3.5/3.6; REQ-ABOUT-03 navigation → Phase 5.3/5.4/5.6. No acceptance criterion is left without a corresponding test.
- **Two test levels (Robolectric + instrumented) are correctly assigned per TSPEC §11.3 rationale.** `ActivityNotFoundException` branch uses `FakeUrlLauncher` + Robolectric (correct — not injectable via `Intents.intending()`); happy-path intent uses Espresso Intents (correct — requires real Activity). The split is architecturally sound and matches the approved TSPEC.
- **`noBrowser_showsSnackbar` injection pattern in task 3.5 matches the TSPEC §11.1 code snippet exactly**, giving the implementor a ready-to-copy test body. This is the most complex Robolectric test in the plan, and the detail level is appropriate.
- **Tab-sync coverage is three-layered**: production comment (Phase 4.4), two tab-unchanged instrumented tests (Phase 5.5), and the Integration Points risk note. This is thorough for a behavioural invariant that is easy to break during refactors.
- **Phase 3.7 Refactor task** explicitly requires re-running all three Robolectric tests and passing, closing the TDD cycle cleanly before Phase 4 begins.
- **`nav_launchSingleTop_noStackDuplicate` (Phase 5.6)** is a valuable test that most plans omit; including it demonstrates awareness of NavController back-stack semantics.

---

## Recommendation

**Approved with Minor Issues**

Two Medium findings (F-01: Red-state ambiguity for compilation error; F-02/F-03: truncated test file paths) should be addressed before implementation begins to avoid implementor confusion. Six Low findings are documentation-precision gaps; none blocks implementation but several (F-05, F-07) close known traceability gaps also flagged by the PM reviewer. Q-01 should be verified against `build.gradle.kts` before Phase 2.4 is started to rule out a missing `material` library dependency.
