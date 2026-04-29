# Cross-Review: software-engineer — PROPERTIES v2

**Reviewer:** software-engineer
**Document reviewed:** docs/about-page/PROPERTIES-about-page.md
**Date:** 2026-04-28
**Iteration:** 2
**Prior review:** docs/about-page/CROSS-REVIEW-software-engineer-PROPERTIES.md

---

## Resolution of Prior Findings

| Prior ID | Severity | Resolution |
|----------|----------|-----------|
| F-01 | Medium | **Resolved.** §2 Test Dependencies table documents `testImplementation(libs.espresso.core)` with a rationale note. PLAN Task 0.4 added. ✅ |
| F-02 | Medium | **Resolved.** P-09, P-10, P-13, P-14, P-15 Assertion columns all now include `openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)` before the `onView(withText(R.string.menu_about)).perform(click())` step. ✅ |
| F-03 | Medium | **Resolved.** TSPEC §11.1 now includes `launched_doesNotShowSnackbar` row. PLAN Tasks 3.8 and 3.9 (Red/Green cycle) added. P-08 is fully traceable. ✅ |
| F-04 | Low | **Resolved.** P-04 and P-19 are now labeled Unit-Robolectric, consistent with the file-level `@RunWith(RobolectricTestRunner::class)` annotation. ✅ |
| F-05 | Low | **Resolved.** P-15 carries a clear note explaining that the Luopan entry path is mechanically identical (same `launchSingleTop` flag) and that one test is sufficient. ✅ |
| F-06 | Low | **Resolved.** §5 carries an explicit placement note: `FakeUrlLauncher` lives in `app/src/test/java/…`, is declared `internal`, and is never compiled into the production APK. TSPEC §2 updated accordingly. ✅ |

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Low | **P-19 test method is unresolved — no named test method commits to the `lastUrl` assertion.** The Test Method column for P-19 reads `(inline in noBrowser_showsSnackbar setup or separate)`. The TSPEC §11.1 Robolectric injection code snippet does not include a `fake.lastUrl` assertion line. The PLAN has no task that explicitly writes this assertion. An implementer following the PLAN strictly will write `noBrowser_showsSnackbar` exactly as the code snippet shows (which ends at the Snackbar `check`) and will never add `assertEquals(AboutFragment.WEBSITE_URL, fake.lastUrl)`. Either: (a) the assertion should be folded into the `noBrowser_showsSnackbar` code snippet in TSPEC §11.1 and PLAN Task 3.5 should be updated to include it, or (b) a dedicated `lastUrlCaptured_onWebsiteTap` test method should be named and added to PLAN Phase 3. Without a concrete code location, P-19 is at risk of being silently dropped during implementation. | §5 (P-19); TSPEC §11.1; PLAN Task 3.5 |
| F-02 | Low | **P-01/P-02/P-03/P-06 Assertion columns omit the navigation precondition that must execute before the assertion.** These four Instrumented properties list only the terminal check (e.g., `onView(withText(R.string.about_studio_name)).check(matches(isDisplayed()))`). The About screen is not the Activity's launch destination — the `@Before` method in `AboutScreenTest` does not navigate to About (PLAN Task 5.1 explicitly states: "do NOT navigate to About here; each test navigates as part of its own Given"). The full test body for `content_studioNameVisible` (P-01) therefore requires `openActionBarOverflowOrOptionsMenu(…); onView(withText(R.string.menu_about)).perform(click())` before the displayed-check. This step is documented in PLAN Task 5.1 prose but is absent from the PROPERTIES Assertion column and from TSPEC §11.2's Action column (which is `—` for all three content tests). An implementer reading only PROPERTIES would write a test that asserts `isDisplayed()` on the very first frame, which will fail because the Activity starts on Modern Mode. The navigation step should be added to the Assertion column for P-01, P-02, P-03, and the TSPEC §11.2 Action column for the three content tests and `websiteLink_firesActionViewIntent`. | §1 (P-01, P-02, P-03); §2 (P-06); TSPEC §11.2 |
| F-03 | Low | **`SystemUrlLauncher`'s `ActivityNotFoundException` catch clause has no dedicated property.** P-18 claims to cover REQ-ABOUT-NFR-02 ("no crash, `ActivityNotFoundException` is caught") but uses `FakeUrlLauncher`, which never throws. The test exercises the fragment's response to `NoBrowserFound` — not the production class's exception handling. The catch block in `SystemUrlLauncher` is therefore untested by any named property. Architecturally this is a gap: a future refactor that accidentally removes the `try/catch` would pass all properties. A minimal addition would be a Unit-Robolectric property `systemUrlLauncher_noBrowser_returnsNoBrowserFound` that instantiates `SystemUrlLauncher` with a mock `Context` (or a Robolectric `ApplicationProvider.getApplicationContext()`) and verifies that `launch("https://…")` returns `NoBrowserFound` when no browser is installed (using Robolectric's `Shadows.shadowOf(context).grantOrDenyPermissions(…)` or `ShadowPackageManager.addResolveInfoForIntent`). This keeps the exception-catch path explicitly in the property set. | §2 (P-18); §6 coverage matrix row "REQ-ABOUT-NFR-02" |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For F-01: the simplest fix for P-19 is to append `assertEquals(AboutFragment.WEBSITE_URL, fake.lastUrl)` to the existing `noBrowser_showsSnackbar` code snippet in TSPEC §11.1 and note it in PLAN Task 3.5. Is that the intended test location, or does the TE want a separate test method named `websiteUrl_capturedOnLaunch` or similar? |
| Q-02 | For F-03: is validating `SystemUrlLauncher`'s catch clause via a Robolectric `ShadowPackageManager` test considered in-scope for this feature, or is it intentionally deferred? If deferred, the coverage matrix row for REQ-ABOUT-NFR-02 should note "catch clause verified by code review only" alongside P-18, to be explicit about the gap. |
| Q-03 | Informational: `openActionBarOverflowOrOptionsMenu` is a static method on `androidx.test.espresso.Espresso` (part of `espresso-core`), not `espresso-contrib` as the v1 review stated. No additional `androidTestImplementation` dependency is required beyond what is already declared. This has been verified against the Android developer reference. The current PROPERTIES assertion text is correct as written. |

---

## Positive Observations

All prior positive observations remain valid. The following are specific to v2:

- The §2 Test Dependencies table is well-structured: it maps each dependency to the properties that need it and to the PLAN task that adds it. This is the right level of cross-referencing for a pre-implementation document.
- The resolution of F-02 is technically precise. Using `openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)` is the correct idiom — it is a method on `androidx.test.espresso.Espresso` (in `espresso-core`, already present as `androidTestImplementation`) and works regardless of locale, unlike `withContentDescription("More options")`. No additional dependency is needed.
- The §2 "Test Level Key" note about `@RunWith(RobolectricTestRunner::class)` applying to all methods in the file is a useful pre-emptive clarification. It correctly explains why P-04 (a pure string equality check) is still classified as Unit-Robolectric rather than Unit-JVM.
- P-15's reasoning ("Luopan entry path is mechanically identical — same `launchSingleTop` flag — one test is sufficient") is a sound application of mechanical equivalence. The rationale traces directly to TSPEC §7, providing an auditable justification for the omission of a second property.
- P-19 and P-20 are correctly separated: P-19 verifies observability (`lastUrl` capture) and P-20 verifies controllability (configurable `result`). Together they constitute a minimal but sufficient contract test for the fake.

---

## Recommendation

**Approved with Minor Issues**

> All three Medium findings from v1 are resolved. No new Medium or High findings exist. The three remaining findings are all Low:
>
> **F-01** — Resolve P-19's ambiguous test method placement by adding the `fake.lastUrl` assertion line to the TSPEC §11.1 code snippet and updating PLAN Task 3.5 to include it. This prevents the assertion from being silently dropped during TDD implementation.
>
> **F-02** — Add the navigation precondition (`openActionBarOverflowOrOptionsMenu(…); tap menu_about`) to the Assertion column for P-01, P-02, P-03, and to the TSPEC §11.2 Action column for the corresponding content tests and `websiteLink_firesActionViewIntent`. Without this, the Assertion column is incomplete as a standalone implementation guide.
>
> **F-03** — Consider adding a dedicated property for `SystemUrlLauncher`'s `ActivityNotFoundException` catch path, or explicitly annotate the REQ-ABOUT-NFR-02 coverage matrix row to acknowledge that the catch clause is verified by code review rather than automated test.
>
> None of these findings are blockers to implementation start. The core property set is correct, complete at the fragment-behavior level, and directly implementable.
