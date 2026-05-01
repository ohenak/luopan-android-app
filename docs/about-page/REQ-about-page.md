# REQ-about-page: Product Requirements Document
## About Screen — YiJi Studio

| Field | Value |
|-------|-------|
| **Version** | 0.2-draft |
| **Date** | 2026-04-28 |
| **Revised** | 2026-04-28 — addressed SE cross-review v1 (F-01–F-04) and TE cross-review v1 (F-01–F-05) |
| **Status** | Draft |
| **Author** | Product |
| **Feature branch** | feat-about-page |

---

## 1. Goal

Add a minimal About screen that credits YiJi Studio (易機閣) as the maker of the app and gives interested users a single tap to reach the website for more information.

---

## 2. User Stories

| ID | Title | Description |
|----|-------|-------------|
| US-ABOUT-01 | Learn who made the app | As a Luopan app user, I want to see who made the app so I can learn more about the expertise behind it |
| US-ABOUT-02 | Visit the website | As an interested user, I want a direct link to the YiJi Studio website so I can explore services without manually searching |

---

## 3. Functional Requirements

### REQ-ABOUT-01 — Studio Identity

| Field | Value |
|-------|-------|
| **ID** | REQ-ABOUT-01 |
| **Title** | Studio name and description |
| **Description** | The screen displays the studio's bilingual name (易機閣 / YiJi Studio) and the fixed one-line description: *"Chinese metaphysics consultations — Feng Shui, Purple Star Astrology & I Ching."* (trailing period is intentional sentence punctuation). The description is stored in string resource `R.string.about_studio_description`. |
| **Priority** | P0 |
| **Source user stories** | US-ABOUT-01 |

**Acceptance criteria:**

> **Who:** Any user who opens the About screen  
> **Given:** The About screen is displayed  
> **When:** The user views the screen  
> **Then:** The text "易機閣 / YiJi Studio" is visible on screen, AND the text from `R.string.about_studio_description` ("Chinese metaphysics consultations — Feng Shui, Purple Star Astrology & I Ching.") is visible on screen

---

### REQ-ABOUT-02 — Website Link

| Field | Value |
|-------|-------|
| **ID** | REQ-ABOUT-02 |
| **Title** | Tappable website link |
| **Description** | The screen displays `yiji.studio` as a tappable link. Tapping it fires an `ACTION_VIEW` intent with data URI `https://yiji.studio`, opening the site in the device's default browser. If no app can handle the intent (no browser installed), a Snackbar displays the message from `R.string.about_no_browser_error` ("No browser found to open link"). |
| **Priority** | P0 |
| **Source user stories** | US-ABOUT-02 |

**Acceptance criteria — happy path:**

> **Who:** Any user on the About screen with a browser installed  
> **Given:** The About screen is displayed  
> **When:** The user taps the `yiji.studio` link  
> **Then:** An `ACTION_VIEW` intent is fired with data URI exactly `https://yiji.studio`

**Acceptance criteria — no browser installed:**

> **Who:** Any user on a device with no browser  
> **Given:** The About screen is displayed AND no app can handle `ACTION_VIEW` with an https URI  
> **When:** The user taps the `yiji.studio` link  
> **Then:** A Snackbar appears with the text from `R.string.about_no_browser_error`; no crash occurs

---

### REQ-ABOUT-03 — Navigation Entry Point

| Field | Value |
|-------|-------|
| **ID** | REQ-ABOUT-03 |
| **Title** | Reachable from the Activity toolbar overflow menu |
| **Description** | An "About" item (`R.string.menu_about`) is added to the Activity-level toolbar overflow menu, making it available on all primary screens without per-fragment changes. The About screen opens as a full-screen Fragment destination in the NavController (`dest_about`). The `dest_about` destination must be explicitly excluded from the `CompassActivity` tab-sync logic so it does not attempt to select a tab when the About screen is active. Primary screens in scope: **Modern Mode** (ModernCompassFragment) and **Luopan Mode** (LuopanFragment). |
| **Priority** | P0 |
| **Source user stories** | US-ABOUT-01, US-ABOUT-02 |

**Acceptance criteria — navigation to About:**

> **Who:** Any user on Modern Mode or Luopan Mode  
> **Given:** The user is viewing Modern Mode (ModernCompassFragment) OR Luopan Mode (LuopanFragment)  
> **When:** The user opens the overflow menu and taps the item with text from `R.string.menu_about`  
> **Then:** The About screen (AboutFragment) is displayed as a full-screen destination

**Acceptance criteria — back navigation:**

> **Who:** Any user on the About screen  
> **Given:** The About screen was opened from Modern Mode OR Luopan Mode  
> **When:** The user presses the system back button  
> **Then:** The user is returned to the screen they navigated from (Modern Mode or Luopan Mode respectively); the About screen is removed from the back stack

---

## 4. Non-Functional Requirements

| ID | Title | Description | Priority |
|----|-------|-------------|----------|
| REQ-ABOUT-NFR-01 | Static bundled content | All About screen text is bundled string resources; no network request is required to render the screen | P0 |
| REQ-ABOUT-NFR-02 | No-browser graceful fallback | `ActivityNotFoundException` on website link tap must be caught; app must not crash | P0 |

---

## 5. Out of Scope

- Service listings, bio, or consultation details (all covered on the website)
- Tap-to-call, tap-to-email, social links
- Booking CTA
- Bilingual string variants beyond the bilingual studio name displayed as a single string

---

## 6. Assumptions

- The Activity toolbar is present or can be added to `CompassActivity` without disrupting existing tab navigation
- `R.string.menu_about` = "About"; `R.string.about_studio_description` = "Chinese metaphysics consultations — Feng Shui, Purple Star Astrology & I Ching."; `R.string.about_no_browser_error` = "No browser found to open link"
- BearingHistoryFragment (Phase 4) is out of scope for this feature's navigation tests; if it ships concurrently, its screen must be added to the acceptance criteria for REQ-ABOUT-03

---

## 7. Traceability

| User Story | Requirements |
|------------|-------------|
| US-ABOUT-01 | REQ-ABOUT-01, REQ-ABOUT-03 |
| US-ABOUT-02 | REQ-ABOUT-02, REQ-ABOUT-03 |
