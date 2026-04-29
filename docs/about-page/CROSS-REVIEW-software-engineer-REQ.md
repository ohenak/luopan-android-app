# Cross-Review: software-engineer — REQ

**Reviewer:** software-engineer
**Document reviewed:** docs/about-page/REQ-about-page.md
**Date:** 2026-04-28
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **Navigation implementation path is underspecified.** REQ-ABOUT-03 says "accessible via the overflow menu (⋮) from any primary screen," but the existing codebase has no global overflow menu. Only `LuopanFragment` has a fragment-level menu (`menu_luopan.xml`). `ModernCompassFragment` has no menu, and `CompassActivity` has no toolbar with options menu support. An engineer following this REQ could add the About item to `menu_luopan.xml` only and technically satisfy the wording, leaving Modern mode without an entry point. The REQ must specify either (a) "global overflow menu on the Activity toolbar" or (b) "each primary fragment exposes an overflow menu item." Also: `CompassActivity.wireTabNavigation()` maps only `dest_modern` / `dest_luopan`; if `AboutFragment` is added as a nav destination the destination-change listener silently falls through on `else -> return`. Implementation must explicitly exclude the About destination from tab-sync logic, but this constraint is not stated. | REQ-ABOUT-03 |
| F-02 | Low | **No fallback for missing browser.** REQ-ABOUT-02 requires `ACTION_VIEW` to open the website. On locked-down or browser-free devices this throws `ActivityNotFoundException`. No error message or graceful fallback is specified. | REQ-ABOUT-02 |
| F-03 | Low | **Description string content unspecified.** REQ-ABOUT-01 calls for "a one-line description of what YiJi Studio does" but supplies no copy. Implementation will block on PM input or invent text that may require a subsequent revision pass. | REQ-ABOUT-01 |
| F-04 | Low | **Screen presentation type not specified.** REQ-ABOUT-03 says back-press returns to the originating screen, which is correct for both a NavController Fragment destination and a `DialogFragment`. The implementation approach (full-screen Fragment, bottom sheet, dialog) is not stated. The choice affects layout constraints and back-stack behavior. | REQ-ABOUT-03 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Should the overflow menu entry be added at the Activity level (applies to all screens uniformly) or at each primary Fragment independently? Activity level is simpler and guarantees "any primary screen" without per-fragment work. |
| Q-02 | What string should be used for the one-line description in REQ-ABOUT-01? Suggest: "Chinese metaphysics consultations — Feng Shui, Purple Star Astrology, and I Ching." |

---

## Positive Observations

- Minimal scope is correct — no ViewModel, no database, no sensor pipeline integration required. This is a pure UI addition.
- REQ-ABOUT-02 correctly targets the device's default browser via `ACTION_VIEW` (not a WebView), consistent with the existing codebase which has no WebView infrastructure.
- "App remains open in the background" is technically accurate for `ACTION_VIEW` — the app stays in the back stack behind the browser.
- Three all-P0 requirements with clear, bounded acceptance criteria.

---

## Recommendation

**Need Attention**

> F-01 (Medium) — REQ-ABOUT-03 navigation specification is ambiguous given the existing codebase architecture. It must clarify the overflow menu placement strategy (Activity-level vs. per-fragment) and note the tab-sync exclusion constraint. All other findings are Low.
