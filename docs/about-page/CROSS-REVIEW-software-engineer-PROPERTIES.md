# Cross-Review: software-engineer — PROPERTIES

**Reviewer:** software-engineer
**Document reviewed:** docs/about-page/PROPERTIES-about-page.md
**Date:** 2026-04-28
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **`espresso-core` not declared in `testImplementation` scope — P-07/P-08/P-18 assertions will break at compile or runtime.** `noBrowser_showsSnackbar` and `launched_doesNotShowSnackbar` use `onView`, `withText`, `matches`, `isDisplayed`, and `doesNotExist` from `espresso-core`. These are standard Espresso APIs. Currently `espresso-core` is declared only as `androidTestImplementation` in `app/build.gradle.kts`; there is no `testImplementation(libs.espresso.core)` entry. Whether `fragment-testing` transitively exposes `espresso-core` to the JVM test sourceSet is undocumented and Gradle configuration-cache sensitive — it cannot be relied on. PLAN Phase 0 lists only `fragment-testing` as the new `testImplementation` dependency; `espresso-core` is absent. The TE should note that `testImplementation(libs.espresso.core)` must be added explicitly, and the PLAN must be updated to include it as a Phase 0 task. Without this fix, P-07, P-08, and P-18 will not compile in the `test` sourceSet. | §2 (P-07, P-08, P-18); PLAN Phase 0 |
| F-02 | Medium | **Overflow menu open action is absent from all navigation and tab-sync property assertions.** P-09, P-10, P-13, P-14, and P-15 describe their Given/Action as "Opening overflow and tapping `menu_about`" but the Assertion column contains no Espresso action for opening the overflow — it starts directly at the destination check. In practice the implementer must supply this action. The canonical Espresso approach is either `openActionBarOverflowOrOptionsMenu(context)` from `espresso-contrib` (not present in the project's dependencies) or `onView(withContentDescription("More options")).perform(click())` (locale-sensitive — breaks on non-English devices). Neither option is listed in `app/build.gradle.kts` or `gradle/libs.versions.toml`. The TSPEC describes the action in prose only ("Open overflow, tap `R.string.menu_about`") without providing Espresso code. The TE should either: (a) add `espresso-contrib` as `androidTestImplementation` and use `openActionBarOverflowOrOptionsMenu`, or (b) explicitly document the `withContentDescription("More options")` pattern with a note on its locale constraint, or (c) use `onView(withId(R.id.action_about)).perform(click())` if the item is accessible without opening the overflow UI first. Without this clarification, five tests (P-09, P-10, P-13, P-14, P-15) cannot be implemented without guesswork. | §3 (P-09, P-10, P-13, P-14, P-15) |
| F-03 | Medium | **P-08 (`launched_doesNotShowSnackbar`) is defined in PROPERTIES but absent from TSPEC §11 and PLAN Phase 3 — implementer following the PLAN will not write it.** The TSPEC §11.1 test table lists exactly three tests for `AboutFragmentLogicTest`: `websiteUrl_isYijiStudio`, `systemUrlLauncher_parsesUri_correctly`, and `noBrowser_showsSnackbar`. P-08 (`launched_doesNotShowSnackbar`) appears only in PROPERTIES and has no corresponding PLAN task in Phase 3. Because implementation is driven by the PLAN, this negative property will be silently dropped unless the discrepancy is resolved. The TE should add `launched_doesNotShowSnackbar` to the TSPEC §11.1 test table and add a PLAN Phase 3 task (Red/Green cycle) for it. | §2 (P-08); TSPEC §11.1; PLAN Phase 3 |
| F-04 | Low | **P-04 and P-19 are labeled "Unit-JVM" but will execute under `@RunWith(RobolectricTestRunner::class)`.** The test level key defines "Unit-JVM" as "plain JVM, no Android framework calls." Both P-04 (`websiteUrl_isYijiStudio`) and P-19 (implicit in `noBrowser_showsSnackbar` setup) contain only pure Kotlin assertions and would pass on plain JVM, but they live in `AboutFragmentLogicTest` which is annotated `@RunWith(RobolectricTestRunner::class)` (per TSPEC §11.1). Under Robolectric the runner bootstraps the Android SDK environment; the tests are technically Robolectric-level even though they make no Android calls. The label mismatch is cosmetic — the tests will pass — but it is misleading in a document that defines the level key precisely. Both should be labeled "Unit-Robolectric" to match the actual runner, or the test class should be split to isolate truly JVM-only assertions. | §1 key; §2 (P-04); §5 (P-19) |
| F-05 | Low | **P-15 (`nav_launchSingleTop_noStackDuplicate`) only validates the Modern Mode entry path.** The assertion checks `compassRose` (Modern Mode view) after back-press following a re-tap from `dest_about`. There is no variant that validates the same single-top behaviour when About was originally entered from Luopan Mode. The PM cross-review (Q-01) also raised this gap. From a testability standpoint, both entry paths are structurally identical to implement (swap the navigation setup step and the back-press assertion view ID), so the omission is not a blocker, but the property set is incomplete for the Luopan entry path. This should either be added as a separate property (e.g., P-15b) or explicitly noted as a known gap if the TE deems single-origin coverage sufficient. | §3 (P-15) |
| F-06 | Low | **`FakeUrlLauncher` is shipped in `src/main` production source, not `src/test`.** TSPEC §4 and PLAN Task 1.3 place `FakeUrlLauncher` in `app/src/main/java/com/luopan/compass/ui/UrlLauncher.kt`. This means the test double is compiled into the release APK, which violates the standard separation of production and test code, increases APK size, and contradicts the test level classification of P-19/P-20 as "Contract" infrastructure. The PROPERTIES accepts this placement without comment. A correct design would place `FakeUrlLauncher` in `app/src/test/java/...` (for Robolectric tests) and/or `app/src/androidTest/java/...` (if needed for instrumented tests). Robolectric tests can access `internal` members in the same Gradle module from `src/test` without reflection, so the `internal var urlLauncher` injection pattern in `AboutFragment` is fully compatible with moving `FakeUrlLauncher` to `src/test`. The TE should note that this architectural decision belongs to the TSPEC and request a correction there; the PROPERTIES should at minimum reference the placement concern. | §5 (P-19, P-20); TSPEC §4 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For F-02 (overflow menu open mechanism): does the project intend to add `espresso-contrib` as a standard instrumented-test dependency, or should all tests rely on `withContentDescription("More options")`? If the latter, the locale constraint should be documented in the test file (e.g., a `@Suppress("HardCodedStringLiteral")` comment and a note that tests assume `en` locale). |
| Q-02 | For F-01: does `fragment-testing` 1.6.1 transitively expose `espresso-core` to `testImplementation` consumers in this project's Gradle configuration? If it does, a comment to that effect in `app/build.gradle.kts` would prevent future confusion when dependency versions change. |
| Q-03 | P-19 states its assertion as `fake.lastUrl == AboutFragment.WEBSITE_URL` "after click". In the `noBrowser_showsSnackbar` test, the `FakeUrlLauncher` is injected via `scenario.onFragment { }` (after `launchFragmentInContainer` returns) and the click follows. However, `onAttach` in `AboutFragment` assigns `SystemUrlLauncher` if `urlLauncher` is null. If `onAttach` runs during `launchFragmentInContainer` (before `onFragment { }` executes), the click will invoke `SystemUrlLauncher`, not `FakeUrlLauncher`, and `lastUrl` will never be set on the fake. Is the injection timing guaranteed to occur before the click handler can be invoked? The TSPEC code snippet in §11.1 injects the fake in `scenario.onFragment { }` before the `perform(click())` call, which should be safe — but the `onAttach` guard should be confirmed to check `if (urlLauncher == null)` rather than always overwriting. |

---

## Positive Observations

- The test level key is explicit and matches the project's runner conventions (Robolectric for Android-dependent assertions without a device, Instrumented for NavController/Activity). This makes the level column actionable, not decorative.
- The `FakeUrlLauncher` design in §5 correctly models the protocol: `lastUrl` capture (P-19) and configurable `result` return (P-20) together provide both observability and controllability of the test double. These are the minimum properties needed for a correct fake.
- Separating the happy-path intent test (P-06, Instrumented using `Intents.intending`) from the no-browser branch test (P-07, Robolectric using `FakeUrlLauncher`) is technically correct and consistent with the TSPEC §11.3 rationale: `ActivityNotFoundException` is not injectable via `Intents.intending()`.
- P-16 (no `MenuProvider` in `AboutFragment`) and P-17 (no network calls) are Code Review properties backed by specific code constructs to look for. Both assertions are precise enough to be executed without ambiguity.
- The coverage matrix in §6 is accurate. Every REQ-ABOUT acceptance criterion maps to at least one property, and the Code Review annotation for REQ-ABOUT-NFR-01 is appropriately honest.
- Tab-sync properties P-13/P-14 test the exclusion path in `addOnDestinationChangedListener` via `activityRule.scenario.onActivity` — this is the correct assertion mechanism for checking Activity-owned view state after navigation, consistent with the `ModeSwitcherTest` pattern already in the codebase.

---

## Recommendation

**Need Attention**

> Three Medium findings must be resolved before implementation begins:
>
> **F-01** — Add `testImplementation(libs.espresso.core)` to `app/build.gradle.kts` (PLAN Phase 0) so P-07, P-08, and P-18 compile in the `test` sourceSet.
>
> **F-02** — Specify the exact Espresso action sequence for opening the overflow menu in the Assertion column for P-09, P-10, P-13, P-14, and P-15. Add `espresso-contrib` if using `openActionBarOverflowOrOptionsMenu`, or document the `withContentDescription("More options")` locale constraint explicitly.
>
> **F-03** — Add `launched_doesNotShowSnackbar` (P-08) to TSPEC §11.1 and add a corresponding Phase 3 Red/Green task to the PLAN so the implementer knows to write it.
>
> The three Low findings (F-04, F-05, F-06) do not block implementation but should be addressed in a follow-up revision.
