# Cross-Review: test-engineer — TSPEC

**Reviewer:** test-engineer
**Document reviewed:** docs/about-page/TSPEC-about-page.md (v0.3)
**Date:** 2026-04-28
**Iteration:** 3

---

## Prior Finding Resolution

| Prior ID | Prior Severity | Status | Evidence |
|----------|---------------|--------|---------|
| F-01 | Medium | **Resolved** | §3 Modified Files now declares both the `gradle/libs.versions.toml` library entry (`fragment-testing = { group = "androidx.fragment", name = "fragment-testing", version.ref = "fragment" }`) and the `app/build.gradle.kts` `testImplementation(libs.fragment.testing)` declaration. The dependency is fully documented at the point where the implementor must add it. |
| F-02 | Low | **Resolved** | §5.1 class snippet now declares `internal var urlLauncher: UrlLauncher? = null` with an explicit rationale ("so Robolectric tests in the same module can assign `fragment.urlLauncher = FakeUrlLauncher()` inside `scenario.onFragment { }` without reflection"). The field visibility and mutability are unambiguous. |
| F-03 | Low | **Resolved** | §11.2 `tabSync_fromModern_tabUnchanged` and `tabSync_fromLuopan_tabUnchanged` now specify `activityRule.scenario.onActivity { assertEquals(N, it.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition) }`, which matches the only safe way to read `selectedTabPosition` from a test thread. |
| F-04 | Low | **Resolved** | §11.3 summary table row for `Uri.parse` correctness is now classified "Robolectric" with the rationale "`android.net.Uri` is a stubbed Android class — requires Robolectric runtime even for a parse check." |
| F-05 | Low | **Resolved** | §11.2 `websiteLink_firesActionViewIntent` row now defines `val okResult = Instrumentation.ActivityResult(Activity.RESULT_OK, null)` inline in the Given column, matching the `LocationPermissionTest` project pattern. |

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Low | **`systemUrlLauncher_parsesUri_correctly` still labelled "JVM" in the §11.1 table.** The §11.3 summary table correctly reclassifies this test as "Robolectric" (the fix for prior F-04), but the §11.1 test table — which is the primary specification engineers read when writing the test — still shows `Level = JVM` for this row. The test lives in `AboutFragmentLogicTest` under `@RunWith(RobolectricTestRunner::class)`, so it will run correctly, but a reader of §11.1 alone sees "JVM" and may conclude the test can be moved to a plain `@RunWith(JUnit4::class)` class, where `Uri.parse()` would return null under the default Robolectric SDK stub or throw `RuntimeException: Method not mocked` without the runner. The §11.1 table cell should be changed from "JVM" to "Robolectric" to match §11.3 and the class-level runner annotation. | TSPEC §11.1 table, §11.3 |

---

## Questions

None.

---

## Positive Observations

- **All five iteration-2 findings are addressed.** The `fragment-testing` dependency is now declared in both `libs.versions.toml` and `build.gradle.kts`, removing the only blocker to compiling `noBrowser_showsSnackbar`.
- **`internal var urlLauncher: UrlLauncher?` with explicit rationale** is the correct and minimal seam for Robolectric injection. The accompanying prose in §5.1 explains why `private val` would break the test, giving future maintainers the context to keep the field visible.
- **`noBrowser_showsSnackbar` injection timing is sound.** `launchFragmentInContainer` completes the full `onAttach → onViewCreated` lifecycle before returning, so `onAttach` assigns `SystemUrlLauncher`; the subsequent `scenario.onFragment { fragment.urlLauncher = fake }` then replaces it on the main thread before the click is dispatched. The override arrives in time and exercises the §5.3 click handler with the fake in place.
- **`FakeUrlLauncher` design remains clean.** Configurable `result` with a safe default (`Launched`), captures `lastUrl`, no shared state between tests, protocol-based — consistent with the project's fake-over-mock convention across all modules.
- **`nav_launchSingleTop_noStackDuplicate`** provides concrete single-press back-navigation verification, which is the most robust observable proxy for the absence of a duplicate back-stack entry.
- **§11.3 rationale table** now correctly distinguishes three test levels (JVM, Robolectric, Instrumented) with accurate justifications for each assignment; the Robolectric vs. instrumented split is architecturally sound.
- **REQ traceability in §12** is complete. Every acceptance criterion across REQ-ABOUT-01, REQ-ABOUT-02, REQ-ABOUT-03, REQ-ABOUT-NFR-01, and REQ-ABOUT-NFR-02 maps to a named component. No requirement is silently dropped.

---

## Recommendation

**Approved with Minor Fix**

> F-01 (Low): Change the `Level` cell for `systemUrlLauncher_parsesUri_correctly` in the §11.1 table from "JVM" to "Robolectric" to match the §11.3 classification and the `@RunWith(RobolectricTestRunner::class)` class annotation. This is a one-cell documentation fix with no impact on test logic or infrastructure.
>
> All prior Medium findings are resolved. This document may proceed to implementation once F-01 is corrected; no re-review is required for a single-cell change.
