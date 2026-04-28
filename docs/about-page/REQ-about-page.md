# REQ-about-page: Product Requirements Document
## About Screen — YiJi Studio

| Field | Value |
|-------|-------|
| **Version** | 0.1-draft |
| **Date** | 2026-04-28 |
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
| **Title** | Studio name and tagline |
| **Description** | The screen displays the studio name (易機閣 / YiJi Studio) and a one-line description of what YiJi Studio does. |
| **Priority** | P0 |
| **Source user stories** | US-ABOUT-01 |

**Acceptance criteria:**

> **Who:** Any user who opens the About screen  
> **Given:** The About screen is displayed  
> **When:** The user views the screen  
> **Then:** The studio name (易機閣 / YiJi Studio) and a brief description are visible

---

### REQ-ABOUT-02 — Website Link

| Field | Value |
|-------|-------|
| **ID** | REQ-ABOUT-02 |
| **Title** | Tappable website link |
| **Description** | The screen displays `yiji.studio` as a tappable link that opens `https://yiji.studio` in the device's default browser. |
| **Priority** | P0 |
| **Source user stories** | US-ABOUT-02 |

**Acceptance criteria:**

> **Who:** Any user on the About screen  
> **Given:** The About screen is displayed  
> **When:** The user taps the website link  
> **Then:** `https://yiji.studio` opens in the device's default browser; the app remains open in the background

---

### REQ-ABOUT-03 — Navigation Entry Point

| Field | Value |
|-------|-------|
| **ID** | REQ-ABOUT-03 |
| **Title** | Reachable from the app's overflow menu |
| **Description** | The About screen is accessible via the overflow menu (⋮) from any primary screen. |
| **Priority** | P0 |
| **Source user stories** | US-ABOUT-01, US-ABOUT-02 |

**Acceptance criteria:**

> **Who:** Any user on any primary screen  
> **Given:** The user opens the overflow menu  
> **When:** The user selects "About"  
> **Then:** The About screen opens; back-press returns the user to the originating screen

---

## 4. Out of Scope

- Service listings, bio, or consultation details (all covered on the website)
- Tap-to-call, tap-to-email, social links
- Booking CTA
- Bilingual string support beyond the studio name bilingual display

---

## 5. Traceability

| User Story | Requirements |
|------------|-------------|
| US-ABOUT-01 | REQ-ABOUT-01, REQ-ABOUT-03 |
| US-ABOUT-02 | REQ-ABOUT-02, REQ-ABOUT-03 |
