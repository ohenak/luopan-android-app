# FSPEC-luopan-p1-core-compass
## Functional Specification — Phase 1: Core Compass

| Field | Value |
|-------|-------|
| **Document ID** | FSPEC-luopan-p1-core-compass |
| **Version** | 0.2-draft |
| **Status** | Draft — cross-review feedback addressed |
| **Date** | 2026-04-23 |
| **Phase** | 1 of 5 |
| **Parent REQ** | [REQ-luopan-p1-core-compass.md](REQ-luopan-p1-core-compass.md) |
| **Master REQ** | [REQ-luopan-compass.md](REQ-luopan-compass.md) |
| **Author** | Product |

> **Purpose of this document:** This FSPEC translates Phase 1 requirements into precise behavioral descriptions — the decision logic, multi-step flows, and business rules that should not be left to engineering discretion. It is a product document written from the user's perspective. It does not specify architecture, class structures, or implementation choices.

---

## Table of Contents

1. [FSPEC-LAUNCH — First-Launch and Calibration Prompt](#1-fspec-launch--first-launch-and-calibration-prompt)
2. [FSPEC-CAL — Calibration Wizard UX](#2-fspec-cal--calibration-wizard-ux)
3. [FSPEC-DETECT — Interference Detection and Warning UX](#3-fspec-detect--interference-detection-and-warning-ux)
4. [FSPEC-CONFIDENCE — Confidence Model Display](#4-fspec-confidence--confidence-model-display)
5. [FSPEC-SENSOR — Sensor Degradation Modes](#5-fspec-sensor--sensor-degradation-modes)
6. [FSPEC-DISPLAY — Modern Mode Layout and Elements](#6-fspec-display--modern-mode-layout-and-elements)
7. [FSPEC-MODE — Mode Switcher (Phase 1 Treatment)](#7-fspec-mode--mode-switcher-phase-1-treatment)
8. [FSPEC-L10N — Localization Behavior](#8-fspec-l10n--localization-behavior)

---

## 1. FSPEC-LAUNCH — First-Launch and Calibration Prompt

---

### FSPEC-LAUNCH-01: First App Launch — No Prior Calibration

**Title:** App behavior when launched for the first time with no saved calibration

**Linked requirements:** REQ-CAL-04, REQ-DISPLAY-02, REQ-DISPLAY-03, master REQ §9.1

**Actors:** User (initiates launch), System (checks calibration storage), OS (provides sensor availability)

**Preconditions:**
- App is installed and this is the first foreground launch on this device
- No calibration record exists in local storage
- The device has a magnetometer (no-magnetometer case is handled in FSPEC-SENSOR-01)

**Behavioral flow:**

1. App starts and checks local storage for a saved calibration record for this device.

2. No calibration record is found.

3. System checks sensor availability. If no magnetometer is detected, the flow diverts to FSPEC-SENSOR-01 (no-magnetometer error screen). If a magnetometer exists, continue.

4. The app displays Modern Mode as the active view. The compass rose is visible and the heading is updating in real time. The user is not blocked from seeing the heading.

5. The confidence badge displays **"Poor"** (red) because calibration quality is "Poor" (uncalibrated state). The calibration age indicator shows **"Uncalibrated"** (not a days-old value).

6. A **non-blocking first-launch prompt** appears. This is a persistent banner (not a modal dialog that blocks the heading view). The banner contains:
   - A brief explanation: the device has not been calibrated for this app, which may reduce accuracy.
   - A primary action button: **"Calibrate Now"** — tapping this enters the calibration wizard (FSPEC-CAL-01).
   - A dismiss action (e.g., a close icon or "Later" link). Tapping dismiss closes the banner without entering calibration.

7a. **If the user taps "Calibrate Now":** The calibration wizard begins (FSPEC-CAL-01). On return from the wizard (whether completed or cancelled), the user lands back on Modern Mode.

7b. **If the user dismisses the banner:** The banner disappears. The heading continues to display with the "Poor" confidence badge. No further first-launch prompt is shown in the same session. On the next app launch, if still uncalibrated, the banner reappears.

7c. **If the user takes no action:** The banner remains visible until the user interacts with it, navigates away, or the session ends.

**Business rules:**
- The first-launch banner MUST NOT use a modal dialog that requires dismissal before the user can see the heading. The compass must be usable immediately, even uncalibrated.
- The banner reappears on every cold start until the user completes at least one calibration. Once any calibration exists (even Poor quality), the first-launch banner no longer appears. A separate calibration age/quality indicator (FSPEC-DISPLAY-02) takes over that role.
- The "Poor" confidence badge is shown from the first moment, not after a delay. There is no grace period that shows a neutral state before confidence is assessed.
- The calibration age field shows "Uncalibrated" (localized) rather than a date or day count when no calibration record exists.

**Edge cases:**
- If storage read fails at launch (permission error, encrypted storage unlock failure): the app treats this as "no calibration" and shows the first-launch banner. It does not crash or display a corrupt reading as if calibrated.
- If the app is killed and restarted before calibration completes, the storage state is the same as it was before the calibration attempt started (no partial writes are possible per REQ-CAL-04 atomic write requirement). The first-launch banner reappears correctly.

**Error scenarios:**
- Storage unavailable: app proceeds in uncalibrated state, shows first-launch banner, logs the read failure internally.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Fresh install, device has magnetometer and gyroscope | User opens the app for the first time | Modern Mode displays with a live compass rose, "Uncalibrated" calibration indicator, "Poor" confidence badge, and a non-blocking banner inviting calibration |
| First-launch banner is visible | User taps "Later" | Banner disappears; compass rose and "Poor" badge remain; no modal appears |
| First-launch banner is visible | User taps "Calibrate Now" | Calibration wizard opens (FSPEC-CAL-01 flow begins) |
| App has never been calibrated | User closes and reopens the app | First-launch banner reappears on the next cold start |
| User launches app for first time (no calibration); user taps "Calibrate Now", completes calibration (any quality), and reopens the app | App relaunched | First-launch banner does NOT appear |

---

## 2. FSPEC-CAL — Calibration Wizard UX

---

### FSPEC-CAL-01: Calibration Wizard — Full Step-by-Step Flow

**Title:** Complete user journey through the calibration wizard from entry to result

**Linked requirements:** REQ-CAL-01, REQ-CAL-02, REQ-CAL-03, REQ-CAL-04, master REQ §9.1–9.5

**Actors:** User (performs figure-8 motion, makes accept/retry/cancel decisions), System (collects samples, computes fit, manages storage)

**Preconditions:**
- Device has a magnetometer (if not, this flow is never reached; see FSPEC-SENSOR-01)
- User has reached the calibration wizard either from the first-launch banner, the main menu "Calibrate" item, or a recalibration prompt (future phases)

**Behavioral flow:**

**Step 0 — Pre-calibration environment check:**

1. Before showing the figure-8 guidance, the system checks the current field magnitude against the expected value.

2a. **If the field deviation is > 20% (potential interference):** A dismissible advisory screen is shown: "Magnetic interference detected nearby. For best results, move away from metal structures, vehicles, and appliances before calibrating." The user has two options:
   - "Move and retry" — stays on the advisory until they tap "I've moved, continue."
   - "Continue anyway" — proceeds to Step 1 with a persistent low-priority banner noting that interference was detected at calibration time.

2b. **If the field deviation is ≤ 20%:** The app proceeds directly to Step 1.

3. If GPS / network location is available, the system displays the expected field magnitude for this location (e.g., "Expected field strength at your location: ~49.3 µT"). If no location is available, this value is omitted.

**Step 1 — Motion guidance:**

4. The screen shows:
   - An animation of a phone tracing a slow figure-8 (infinity symbol) in the air, tilting the device in all directions.
   - Instructions (localized): "Hold your phone flat and slowly trace a figure-8 pattern in the air. Rotate it in all directions to cover all orientations."
   - A "Start" button to begin data collection, and a "Cancel" button at the top.

5. User taps "Start." Data collection begins.

**Step 2 — Real-time data collection and coverage feedback:**

6. The guidance screen transitions to the live collection view, which shows all of the following simultaneously:
   - A **3D sphere visualization** in which each collected data point is shown as a dot on the sphere surface. Dots start grey/blue and transition to green as the region fills in.
   - A **coverage percentage** per axis: "X: 58% · Y: 43% · Z: 31%" — each with a small color indicator (red if below threshold, amber if meeting minimum, green if meeting Good threshold).
   - A **sample count**: "217 samples collected (minimum: 200)."
   - A **quality preview**: rolling residual RMS estimate shown as a number in µT (e.g., "Residual estimate: ~0.8 µT").
   - A "Done" button (initially greyed out — see business rules).
   - A "Cancel" button accessible at all times.

7. As the user moves the device, the sphere visualization updates in real time. Each new data point is added to the visualization immediately, so the user can see which regions are uncovered and respond by tilting the device toward those regions.

8. Instructional hints update dynamically based on coverage gaps: if the Z-axis coverage is below threshold, the hint changes to "Tilt your phone forward and backward more." If all axes are meeting minimums but the user continues, the hint changes to "Looking good — keep going for better accuracy."

**Completion gate logic:**

9. The "Done" button becomes active (tappable) when BOTH conditions are met:
   - At least 200 samples have been collected, AND
   - All three axes have coverage ≥ 15% of the total range (the minimum threshold for any result to be computed)

10. If the user attempts to tap "Done" before the gate is met, the button is unresponsive and a tooltip appears: "Keep rotating to improve coverage."

11. **Auto-complete:** Auto-complete fires when: sample count ≥ 200 AND axis coverage ≥ 80% on all axes AND rolling residual RMS estimate < 1.0 µT. All three conditions must be true simultaneously. When auto-complete fires, the collection phase completes automatically without the user needing to tap "Done." A brief success animation plays (the sphere turns fully green with a gentle pulse).

**Step 3 — Quality result screen:**

12. After the user taps "Done" (or auto-complete triggers), the system computes the final ellipsoid fit. During computation (which may take up to 2 seconds), a spinner is shown: "Computing calibration quality…"

13. The quality result screen is displayed with:
   - The quality score: **Good**, **Fair**, or **Poor** — shown as a large colored label (green / amber / red).
   - The residual RMS value in µT (e.g., "Fit accuracy: 0.7 µT").
   - Per-axis coverage percentages.
   - A brief plain-language explanation of what the score means for accuracy.

14. The action options vary by quality score:

   **If Good (residual ≤ 1.0 µT, all axes ≥ 20%):**
   - Primary action: **"Save Calibration"** (green button).
   - Secondary action: **"Retry for better results"** (text link).

   **If Fair (residual 1.0–2.0 µT, all axes ≥ 15%):**
   - Primary action: **"Save — Moderate Accuracy"** (amber button). A label below explains: "Confidence will be capped at Moderate. Retry for High accuracy."
   - Secondary action: **"Retry"** (text link).

   **If Poor (residual > 2.0 µT or any axis < 15%):**
   - Primary action: **"Retry"** (amber button, visually prominent).
   - Secondary action: **"Accept Anyway"** (grey button). Tapping "Accept Anyway" shows a confirmation: "This calibration is Poor quality. Your confidence level will be Poor and readings may not be reliable. Save anyway?" with "Yes, save" and "Go back" options.

15. **If user taps Retry:** Data collection restarts from Step 1 (the pre-calibration check in Step 0 is NOT repeated). The partially collected data is discarded.

**Step 4 — Confirmation and save:**

16. When the user confirms saving (via "Save Calibration," "Save — Moderate Accuracy," or "Yes, save" from the Poor acceptance dialog):
   - The calibration record is written to encrypted local storage atomically.
   - The previous calibration (if any) is retained as a rollback copy (`v(n-1)`).
   - A confirmation screen is shown: "Calibration saved. Your compass is now calibrated for this device." with a "Done" button.

17. Tapping "Done" on the confirmation screen returns the user to Modern Mode. The confidence badge updates immediately to reflect the new calibration quality. The calibration age indicator shows "Cal: 0d."

**Step 5 — Cancel at any stage:**

18. The "Cancel" button is visible throughout Steps 1, 2, and 3. Tapping it at any point shows a confirmation: "Cancel calibration? Any data collected in this session will be discarded." with "Yes, cancel" and "Continue calibrating" options.

19. On confirmation, all session data is discarded. The previous calibration (if any) remains exactly as it was — it is never touched during a cancelled session. The user returns to Modern Mode.

20. If no prior calibration existed, the user returns to Modern Mode with the "Uncalibrated" state, first-launch banner still present.

**Business rules:**
- The pre-calibration interference check uses a 20% threshold (midpoint of the Moderate band, 15%–25%) rather than the 25% Poor threshold. This is intentional: calibrating in any Moderate interference environment risks biasing the ellipsoid fit. The 20% threshold is more conservative than the 25% runtime warning threshold (FSPEC-DETECT-01) to prevent calibrating in Moderate interference environments.
- The "Done" button must not be tappable before both the 200-sample count and the ≥ 15% per-axis coverage threshold are both satisfied simultaneously. Either condition alone is not sufficient.
- Auto-complete cannot trigger before the 200-sample minimum is reached, regardless of how good the coverage looks.
- A cancelled calibration session must leave the storage state identical to what it was before the session began. The system must not write any partial calibration data during an active session.
- The ellipsoid fitting computation always runs after "Done" is tapped or auto-complete fires — there is no early-exit path that skips fitting.
- Poor-quality acceptance requires a two-tap confirmation (tap "Accept Anyway," then "Yes, save"), not a single-tap acceptance. This protects users from accidentally accepting a bad calibration.
- The calibration age clock starts at the moment the confirmed write completes, not at the moment data collection ended.
- The rolling residual RMS used for auto-complete is a live approximation computed over the most recent 50-sample window. It may differ from the final ellipsoid fit RMS computed on the complete dataset at the quality assessment step. A result that triggers auto-complete may still display as "Fair" on the quality screen if the full-fit RMS exceeds 1.0 µT.

**Edge cases:**
- User returns to the app mid-session from another app (e.g., answers a phone call): If the process survives the background period (short transient — e.g., answering a call), session data is preserved and the collection screen resumes when the user returns to foreground. Sample count continues from where it left off. If the OS kills the process during the background period, see FSPEC-CAL-02 — session data is lost and the user must recalibrate.
- Device tilted past 85° (nearly vertical) during collection: samples continue to be collected and counted, but the coverage visualization shows the user that this orientation may be duplicating already-covered regions. No special warning is required — the coverage display guides the user naturally.
- No location available at Step 0: the expected field magnitude line is omitted from the UI. The app uses the hardcoded 50 µT global-average seed for soft-iron correction. An advisory is shown in small text during collection: "Location unavailable — interference detection uses approximate field strength" (matching REQ verbatim).

**Error scenarios:**

- **OOM kill during data collection (before "Done"):** The OS kills the process. On restart, the app finds storage unchanged (no partial write). If a prior calibration existed, it is intact and the confidence badge reflects it. If no prior calibration existed, the first-launch banner reappears. The calibration wizard does not relaunch automatically — the user must navigate back to it.
- **OOM kill during the save write:** Because writes are atomic (write-to-temp-then-rename), the old calibration remains intact. The new calibration is not partially written. On restart, the app behaves as if the save never happened.
- **Storage write failure (disk full):** A dialog is shown: "Unable to save calibration — device storage may be full." with "Retry" and "Cancel" buttons. The following behavior applies:
  - (a) The quality result screen remains in the foreground when the error dialog appears. The dialog overlays the quality result screen; Modern Mode is not visible behind it.
  - (b) "Retry" retries only the write operation (not the full ellipsoid fit). The in-memory result is unchanged and the quality result screen is still shown after the dialog closes, whether the retry succeeds or fails again.
  - (c) "Cancel" discards the in-memory result and navigates back to Modern Mode with the same effect as cancelling calibration entirely. The user returns to Modern Mode with whatever calibration state existed before this session began.
  - (d) If the process is killed while the disk-full dialog is open: on restart, the prior calibration (if any) is intact (the in-memory result was never written); the in-memory result is gone; the first-launch banner reappears if no prior calibration existed.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Calibration wizard is open, user has collected fewer than 200 samples | User taps the "Done" button | Button does not respond; a tooltip appears: "Keep rotating to improve coverage" |
| User completes figure-8 with ≥ 200 samples and Good coverage; user taps "Done" | Quality result screen appears | Score shown as "Good" (green); primary button is "Save Calibration"; confidence badge on returning to main screen shows the new calibration's quality level |
| User reaches the quality screen with Poor result | User taps "Accept Anyway" | A confirmation dialog appears requiring a second tap to confirm; no calibration is saved until second confirmation |
| App is killed by OS during figure-8 data collection (before save) | User relaunches the app | Previous calibration is intact; no corrupted state; wizard does not auto-reopen |
| User collects 200+ samples with residual RMS = 1.5 µT (Fair quality) | Quality result screen displays | Primary button shows "Save — Moderate Accuracy" in amber; label states accuracy will be capped at Moderate |
| User is in calibration with 80 samples collected; user taps "Cancel" and confirms in the confirmation dialog | Cancellation confirmed | All session data is discarded, previous calibration remains intact, user returns to Modern Mode with previous confidence level |

---

### FSPEC-CAL-02: OOM Resilience During Calibration

**Title:** App state recovery when process is killed during calibration

**Linked requirements:** REQ-CAL-04, master REQ §9 (Scenario I)

**Actors:** OS (kills process), User (relaunches app)

**Preconditions:**
- User was in an active calibration session (any stage: data collection, quality screen, or save confirmation)
- The process is killed by the OS before the user confirms saving

**Behavioral flow:**

1. OS kills the process. All in-memory calibration session data is lost.

2. User relaunches the app. The app performs a fresh cold start.

3. The app reads local storage. Because no write was attempted (or the write was atomic and incomplete — resulting in the prior file remaining), the storage state is the same as before the session began.

4a. **If a prior calibration existed:** App loads the prior calibration. Modern Mode displays with the prior confidence level. The calibration age indicator reflects the saved calibration's age. No error is shown to the user regarding the killed session. The first-launch banner is not shown.

4b. **If no prior calibration existed:** App starts in the uncalibrated state. Modern Mode displays with "Poor" confidence and "Uncalibrated" indicator. The first-launch banner appears.

5. The calibration wizard does not relaunch automatically. The user must re-enter it manually if they wish to calibrate again.

**Business rules:**
- The app must never present a partial or corrupted calibration result after an OOM kill. The binary choices are: prior calibration loaded successfully, or uncalibrated state shown.
- No user-visible error message is required for an OOM kill recovery unless storage itself is found to be corrupted. In that case, the app logs internally and treats the state as uncalibrated.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| User is mid-calibration (50+ samples collected, not yet saved); OS kills the process | User relaunches the app | Prior calibration (if any) is intact; if no prior calibration existed, first-launch banner appears; no error dialog about the killed session |
| User is on the quality result screen (fit has been computed, not yet saved) | OS kills the process; user relaunches | Same behavior as above; the computed (but unsaved) result is lost; user must recalibrate |
| User has reached the save-confirmation step and the process is killed by the OS during the storage write | User relaunches | Prior calibration (if any) is intact; the partially-written new calibration is not visible or usable |

---

## 3. FSPEC-DETECT — Interference Detection and Warning UX

---

### FSPEC-DETECT-01: Interference Warning Appearance and Content

**Title:** What the interference warning shows and how it is laid out

**Linked requirements:** REQ-DETECT-01, REQ-DETECT-02, REQ-DETECT-03, master REQ §11.5

**Actors:** System (detects interference, triggers warning), User (views warning, optionally dismisses it)

**Preconditions:**
- Modern Mode is active
- The system has computed a field magnitude deviation ≥ 25% from expected, OR an inclination deviation ≥ 8° from expected

**Behavioral flow:**

1. The system evaluates field magnitude deviation and inclination deviation on every sensor update cycle (continuous, ≥ 20 Hz).

2. When deviation first crosses the "Poor" threshold (field ≥ 25% OR inclination ≥ 8°):
   - The warning must appear within **2 seconds** of the threshold crossing.
   - The confidence badge immediately changes to **"Poor"** (red).

3. The interference warning is rendered as a **red banner** positioned below the compass rose and digital readout, but above the bottom navigation area. It does NOT overlay the compass rose or the heading value — the user must always be able to read the heading even while the warning is visible.

4. The red banner contains:
   - A warning icon (e.g., triangle with exclamation mark).
   - A heading line (localized): **"Magnetic Interference Detected"**
   - An explanation line (localized): **"Move away from metal structures, appliances, or vehicles for an accurate reading."**
   - Measured vs. expected values:
     - Field magnitude: **"Field: [X] µT measured / [Y] µT expected ([Z]% deviation)"**
     - Inclination: **"Inclination: [A]° measured / [B]° expected ([C]° deviation)"**
     - If only one of the two checks is in the Poor range, only that line is shown (e.g., inclination may be fine even if magnitude is high).
   - A **dismiss control** (small "×" icon in the top-right corner of the banner). See FSPEC-DETECT-02 for behavior after dismiss.

5. The heading continues to update in real time behind the warning. The heading value is never frozen, zeroed, or hidden by the interference warning.

6. In Phase 1, there is NO capture button and NO "override" button on the interference warning. The warning is strictly informational. The absence of these controls is intentional — bearing capture does not exist in Phase 1.

**Business rules:**
- The warning must appear within 2 seconds of threshold crossing. A single-sample spike may not trigger it (see FSPEC-DETECT-03 for noise spike handling), but sustained readings above threshold do.
- The measured and expected field values shown must be the values from the most recent sensor cycle, updated in real time while the warning is visible. The numbers shown are not frozen at the moment of warning appearance.
- Both field magnitude and inclination deviation are shown if both are in abnormal ranges. If only one is triggering the warning, only that check's values are shown.
- The banner must be in a visually distinct red color with sufficient contrast for text legibility. Color alone is not sufficient — the text "Magnetic Interference Detected" must be present (for accessibility).
- In Phase 1, the interference warning is warn-only. No capture button, no override button, no action beyond dismiss.

**Edge cases:**
- Field deviation drops briefly below 25% but returns above 25% within 3 seconds: the banner remains visible (clearance hysteresis — see FSPEC-DETECT-02).
- Both checks (magnitude and inclination) trigger simultaneously: the banner shows both lines of deviation data.
- Interference is detected during the calibration wizard pre-check: see FSPEC-CAL-01, Step 0 for that handling. This FSPEC covers interference detection during normal compass use only.
- If no location is available (Phase 1 fallback), an additional small advisory line appears below the interference warning: "Location unavailable — interference detection uses approximate field strength." This advisory disappears when the interference warning clears.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Device is near an electrical panel; field magnitude deviation is 35% | System evaluates sensor data | Red interference banner appears within 2 seconds; confidence badge shows "Poor"; compass heading continues to update |
| Interference banner is visible | User reads the screen | The heading value, compass rose, and interference explanation are all visible simultaneously without scrolling |
| User is near interference with both field magnitude (30%) and inclination (10°) deviation | Banner is shown | Both field magnitude and inclination deviation lines are present in the banner |
| Only inclination deviation exceeds Poor threshold (magnitude is within Good range) | Banner is shown | Only the inclination deviation line is shown; field magnitude line is absent |
| No GPS and no last-known location; interference warning triggers | Warning banner shown | Warning banner shows the standard interference text PLUS a secondary line: "Location unavailable — interference detection uses approximate field strength" |
| Field deviation exactly 24.9% | System evaluates sensor data | No interference warning appears |
| Field deviation exactly 25% | System evaluates sensor data | Warning appears within 2 seconds |
| Inclination deviation exactly 8° | System evaluates sensor data | Warning appears within 2 seconds |

---

### FSPEC-DETECT-02: Warning Persistence, Dismissal, and Auto-Clearance

**Title:** How the interference warning behaves over time and when it clears

**Linked requirements:** REQ-DETECT-01, REQ-DETECT-03, master REQ §11.5 (Phase 1 note)

**Actors:** User (optionally dismisses warning), System (monitors field continuously, clears warning automatically)

**Preconditions:**
- Interference warning is currently displayed

**Behavioral flow:**

**Auto-clearance path (preferred):**

1. The system continuously monitors field magnitude deviation and inclination deviation even while the warning is displayed.

2. When both checks drop below their "Poor" thresholds (field < 25% AND inclination < 8°), a **3-second clearance timer** starts.

3. If field deviation stays continuously below 25% AND inclination stays continuously below 8° for the entire 3-second window: the warning banner disappears automatically. The confidence badge is re-evaluated from scratch using the full confidence model (FSPEC-CONFIDENCE-01). This may produce High, Moderate, or Poor depending on other factors (calibration age, tilt, etc.).

4. **Hysteresis reset:** If at any point during the 3-second window either check exceeds the threshold again (even a single reading), the timer resets to zero and the window must be satisfied again from the start.

**User-dismiss path:**

5. If the user taps the "×" dismiss control on the banner:
   - The banner disappears visually.
   - The confidence badge remains **"Poor"** — it does not change because the user dismissed the visible banner.
   - The system continues monitoring interference in the background.

6. When the user has dismissed the banner but interference persists:
   - The confidence badge continues to show "Poor" with no banner visible.
   - If the interference resolves (3-second clearance window satisfied), the auto-clearance logic fires the same way as if the banner were still visible: the badge is re-evaluated and may return to Moderate or High.

7. There is no mechanism in Phase 1 to re-show a dismissed banner in the same session unless:
   - The user exits to the calibration wizard and returns to Modern Mode.
   - The app is backgrounded and foregrounded again.
   In those cases, the warning re-evaluates from the current field state and re-shows if interference is still present.

   If the app is backgrounded (sensor listeners released per FSPEC-SENSOR-05 lifecycle), the clearance timer is suspended. On foreground, if the field deviation is still above 15%, the timer resets to 0. If the field deviation is already below 15% on foreground, the timer continues from 0 (a fresh 3-second window starts).

**Business rules:**
- The 3-second clearance window requires all-or-nothing continuity. A partial recovery followed by a spike resets the full 3 seconds. There is no partial credit.
- The clearance threshold is 15%, not 25%. The warning triggers at ≥ 25% deviation and only clears when deviation drops and stays below 15%. This creates a deliberate hysteresis band: a device at 20% deviation will not clear the warning. Specifically: trigger threshold is 25%, clearance threshold is 15%.
- The inclination deviation warning clears under the same hysteresis rule: inclination deviation must remain continuously below 3° (the lower-than-trigger clearance threshold, analogous to the 15% field clearance vs. 25% trigger) for ≥3 seconds. Any excursion above 3° resets the timer.
- Confidence badge color and text always reflect the current model output. When the banner auto-clears, the badge may jump from "Poor" (red) directly to "High" (green) if all other conditions are also met. This jump is correct and expected.
- The dismiss control does not constitute the user "acknowledging" or "accepting" the interference for the purpose of any bearing action. In Phase 2 when bearing capture is introduced, the override mechanism will be a separate explicit control.

**Edge cases:**
- User is walking in and out of interference zone rapidly: the banner appears and re-appears as the device crosses the threshold. Each crossing restarts the display and clearance logic. No debounce is applied to the appearance side — only clearance requires the 3-second sustained window.
- Location changes (e.g., user moves to a different city): the expected field magnitude is based on the last known coarse location or the 50 µT fallback. If the location is stale, the deviation calculation may produce false positives in regions with substantially different field strengths. This is a known Phase 1 limitation.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Interference warning is displayed (field deviation 30%) | Field deviation drops to 12% and stays there for 3.5 seconds | Warning banner disappears; confidence badge re-evaluates per full confidence model |
| Field deviation drops to 12%, then spikes to 18% after 2 seconds | 3-second clearance window | Window resets to 0 seconds; warning persists |
| User taps "×" to dismiss the banner while interference is still present | Banner dismissed | Banner disappears; confidence badge remains "Poor"; heading continues to update |
| Banner is dismissed; user backgrounds and foregrounds the app | App returns to foreground | Warning state re-evaluated; if interference still present, banner re-appears |
| Field deviation drops to exactly 15.0% | Clearance timer evaluated | Clearance timer does NOT start (15.0% is the clearance threshold; clearance requires deviation strictly below 15%, i.e., 14.9% or less) |
| Field deviation drops to 14.9% and stays there for 3.1 seconds | Clearance timer runs | Warning clears; confidence badge re-evaluates |
| Inclination deviation drops to 2.9° and stays for 3.1 seconds | Clearance timer runs | Warning clears (inclination clearance threshold is < 3°, sustained for ≥3 seconds) |

---

### FSPEC-DETECT-03: Noise Spike Rejection — User-Visible vs. Silent Effect

**Title:** How noise spike rejection appears (or does not appear) to the user

**Linked requirements:** REQ-DETECT-04

**Actors:** System (detects and silently rejects spikes)

**Preconditions:**
- Magnetometer is running and delivering data to the fusion algorithm

**Behavioral flow:**

1. On every magnetometer sample, the system computes the deviation from the rolling mean: `|sample − rolling_mean| > 10 µT`.

2a. **If the spike condition is NOT met:** The sample passes into the fusion algorithm normally. No user-visible change occurs.

2b. **If the spike condition IS met:** The sample is rejected silently. It is excluded from the fusion computation. The heading value does not jump by the spike's magnitude. The compass needle does not visually jump.

3. The spike rejection is entirely invisible to the user during normal operation. There is no badge, banner, or indicator shown for a rejected spike.

4. The rejection event is logged to an internal diagnostic buffer (not user-facing).

5. Spike rejection does NOT affect the confidence badge directly. If a brief spike is rejected, confidence is not penalized for that single sample.

6. However, if spikes are frequent (occurring multiple times within the 2-second rolling window), the noise variance metric will rise. A sustained high noise variance will cause the confidence model to produce a lower score through the normal noise_variance dimension scoring — not through any spike-specific path.

**Business rules:**
- User must never see the compass needle jump or stutter due to a single-sample outlier. The heading display must appear continuous and smooth even when spikes occur.
- Spike rejection is completely silent at the UI level. There is no "spike detected" notification, badge, or transient indicator.
- The 10 µT outlier threshold applies to the magnitude of the deviation from the rolling mean, not the absolute field value.

**Edge cases:**
- Multiple consecutive samples are outliers (e.g., the device is near a pulsed electromagnetic source): all are individually evaluated against the rolling mean. If most samples in a window are outliers, the "rolling mean" itself may drift toward the spike values over time. This is an inherent limitation of single-sample outlier rejection; the confidence model's noise variance dimension will reflect the unstable environment.
- Rolling mean has insufficient history (first few samples on session start): the rolling mean is computed from whatever samples are available. During initialization (<2 seconds of data), outlier rejection uses a partial window.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| A single magnetometer reading is 15 µT above the rolling mean | System processes the sample | Heading does not jump; no user-visible indicator appears; spike is logged internally |
| Spikes occur on every other sample for 5 seconds | Heading displayed continuously | Noise variance rises; confidence badge may degrade through the normal confidence model; no "spike rejected" message appears to the user |

---

## 4. FSPEC-CONFIDENCE — Confidence Model Display

---

### FSPEC-CONFIDENCE-01: Confidence Badge — States, Colors, and Text

**Title:** Complete specification of confidence badge appearance in all states

**Linked requirements:** REQ-DISPLAY-03, master REQ §8.1, §8.2, §8.3, phase REQ §5.5

**Actors:** System (computes and updates badge continuously)

**Preconditions:**
- Modern Mode is active and the heading is being displayed

**Behavioral flow:**

1. On every sensor update cycle, the system evaluates all five confidence dimensions and applies the tilt penalty to produce an overall score.

2. The confidence badge is updated immediately whenever the overall score changes. There is no debounce on badge updates — the badge reflects the current computed state.

3. The five dimensions and their scoring levels are:

   | Dimension | Good (score 3) | Moderate (score 2) | Poor (score 1) |
   |-----------|---------------|-------------------|----------------|
   | Field magnitude deviation | `[0%, 15%)` | `[15%, 25%)` | `[25%, ∞)` |
   | Inclination deviation | `[0°, 3°)` | `[3°, 8°)` | `[8°, ∞)` |
   | Calibration quality | `[0, 1.0 µT]` residual AND all axes ≥ 20% | `(1.0, 2.0 µT]` residual AND all axes ≥ 15% | `(2.0 µT, ∞)` residual OR any axis < 15% |
   | Calibration age | `[0, 7 days]` | `(7, 30 days]` | `(30 days, ∞)` |
   | Noise variance | `[0, 0.1 µT²]` | `(0.1, 0.5 µT²]` | `(0.5 µT², ∞)` |

   Boundary convention: intervals use the REQ §5.3 canonical notation. Calibration age: exactly 7 days is Good; exactly 30 days is Moderate; 31 days is Poor. Calibration residual: exactly 1.0 µT is Good; exactly 2.0 µT is Fair/Moderate. Noise variance: exactly 0.1 µT² is Good; exactly 0.5 µT² is Moderate.

4. The base score is the **minimum** of all five dimension scores. A single Poor dimension produces a Poor overall score regardless of the other four.

5. The tilt penalty is then applied to the base score:
   - Tilt ≤ 5°: no effect.
   - Tilt > 5° and ≤ 20°: base score is clamped to Moderate. If base score is already Moderate or Poor, the clamp has no additional effect.
   - Tilt > 20°: base score is clamped to Poor. If base score is already Poor, the clamp has no additional effect. The tilt penalty can only lower or maintain a score; it can never raise it.

6. The resulting overall score maps to badge display:

   | Score | Badge Text | Badge Color | Accessibility Note |
   |-------|-----------|-------------|-------------------|
   | High (3) | "High" (localized) | Green (#4CAF50 or equivalent Material green) | Text always present; not color-only |
   | Moderate (2) | "Moderate" (localized) | Amber (#FFC107 or equivalent Material amber) | Text always present |
   | Poor (1) | "Poor" (localized) | Red (#F44336 or equivalent Material red) | Text always present |

7. The badge is a named label, not just a color chip. Users with color blindness must be able to read the state from text alone.

**Business rules:**
- The score minimum is applied across all five dimensions without weighting. There is no partial credit or averaging.
- Tilt is not a dimension that contributes to the minimum computation. It is applied as a post-processing clamp after the minimum is calculated. This is intentional: a 10° tilt with otherwise-Good conditions produces Moderate, not Poor.
- The confidence badge must update in real time. As the user moves or as field conditions change, the badge must reflect the new state within the latency budget of the display pipeline.
- "Uncalibrated" state (no calibration record): calibration quality is forced to Poor, producing a Poor overall score.
- No gyroscope: confidence is capped at Moderate (FSPEC-SENSOR-02). This is implemented by fixing the gyroscope-missing-penalty to a Moderate ceiling, not by injecting a false dimension score.

**Edge cases:**
- All five dimensions are Good, tilt is 12°: base score = 3 (Good), tilt clamp = Moderate. Final result: **Moderate**. Correct.
- All five dimensions are Good, tilt is 2°: base score = 3 (Good), no tilt clamp. Final result: **High**. Correct.
- One dimension is Poor (e.g., field deviation 30%), tilt is 12°: base score = 1 (Poor), tilt clamp to Moderate has no effect (min(1, 2) = 1). Final result: **Poor**. Correct.
- All five dimensions are Moderate, tilt is 25°: base score = 2 (Moderate), tilt clamp to Poor. Final result: **Poor**. Correct.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| All five dimensions are Good; tilt is 2° | Stationary heading | Badge shows "High" in green |
| All dimensions Good except calibration age = 31 days | Stationary heading | Badge shows "Poor" in red (cal_age > 30 days → score = 1 = Poor; min across all dimensions = 1; tilt ≤ 5°, no clamp needed) |
| Field deviation 30%, all other dimensions Good; tilt ≤ 5° | Stationary heading | Badge shows "Poor" in red; interference warning also displayed |
| Calibration is Fair quality (residual 1.5 µT); all other dimensions Good; tilt ≤ 5° | Stationary heading | Badge shows "Moderate" in amber |
| All dimensions Moderate (score=2 for all), tilt 12° | Stationary heading | Badge shows "Moderate" (tilt clamp min(2,2)=2; no change) |
| One dimension Poor (score=1), all others Good/Moderate, tilt 12° | Stationary heading | Badge shows "Poor" (tilt clamp min(1,2)=1; no change) |
| Calibration age exactly 7 days; all other dimensions Good; tilt ≤ 5° | Stationary heading | Badge shows "High" in green (cal_age = 7 days is within `[0, 7 days]` → Good) |
| Calibration age exactly 30 days; all other dimensions Good; tilt ≤ 5° | Stationary heading | Badge shows "Moderate" in amber (cal_age = 30 days is within `(7, 30 days]` → Moderate) |
| Calibration age exactly 31 days; all other dimensions Good; tilt ≤ 5° | Stationary heading | Badge shows "Poor" in red (cal_age = 31 days is within `(30 days, ∞)` → Poor; min = 1) |

---

### FSPEC-CONFIDENCE-02: Tilt Indicator — Appearance and Thresholds

**Title:** When and what the tilt indicator shows

**Linked requirements:** REQ-DISPLAY-02, REQ-DISPLAY-03, master REQ §11.6, phase REQ §5.5

**Actors:** System (computes tilt from accelerometer continuously), User (sees indicator and corrects hold)

**Preconditions:**
- Modern Mode is active
- Accelerometer data is available

**Behavioral flow:**

1. The system computes the device tilt angle from horizontal on every accelerometer update.

2. **Tilt ≤ 5°:** No tilt indicator is shown. The heading display is in its normal state.

3. **Tilt > 5° and ≤ 20°:**
   - A **tilt advisory text** appears in the UI: "Tilt: [X.X]°" where X.X is the current measured tilt.
   - This advisory is shown in amber text, positioned below the digital heading readout and above the confidence badge.
   - The confidence badge is clamped to at most Moderate (per FSPEC-CONFIDENCE-01).
   - The advisory text is visible and updates in real time as the tilt changes.

4. **Tilt > 20°:**
   - The tilt advisory text changes to the localized warning: **"Hold flat for best accuracy"**.
   - The current tilt angle continues to be shown numerically alongside the warning text (e.g., "Hold flat for best accuracy — Tilt: 28.3°").
   - The confidence badge is clamped to Poor.

5. When tilt returns to ≤ 5°, the advisory disappears immediately (no delay). The confidence badge is re-evaluated.

**Business rules:**
- The tilt indicator text always shows the current numerical tilt value, even at > 20°. The user needs feedback to correct their hold — a static warning without a current reading is less useful.
- The 5° threshold for indicator appearance is a strict threshold. At exactly 5.0° (if the sensor reports this exactly), no indicator is shown. At 5.1°, the indicator appears. This is the "no penalty" boundary.
- The indicator disappears immediately when tilt drops below the 5° threshold, with no hysteresis on the appearance side.

**Edge cases:**
- Device is held in landscape orientation (rotated 90° around the viewing axis): the tilt calculation uses the device's departure from horizontal relative to gravity, not screen orientation. A landscape device that is held horizontally reads tilt ≤ 5° and shows no indicator.
- Device held nearly vertical (pitch ~90°): tilt may read very high values (e.g., 85°). The badge is Poor, the indicator shows the current angle. The heading continues to display (sensor fusion handles this mathematically without crashing).

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Device is held at 3° tilt | Heading displayed | No tilt indicator visible |
| Device is held at 12° tilt | Heading displayed | Amber tilt advisory appears showing "Tilt: 12.0°" (or similar); confidence capped at Moderate |
| Device is held at 25° tilt | Heading displayed | Warning text "Hold flat for best accuracy" visible with current tilt angle; confidence badge shows "Poor" |
| Device returns from 25° tilt to 3° tilt | Transition observed | Tilt indicator disappears; confidence badge re-evaluates; may return to High if other conditions allow |
| Tilt exactly 5.0° | Heading displayed | Tilt indicator is NOT shown; confidence uses raw five-dimension score with no tilt penalty |
| Tilt 5.1° | Heading displayed | Amber tilt advisory appears; confidence clamped to Moderate if currently Good |

---

### FSPEC-CONFIDENCE-03: Calibration Age Effect on Badge

**Title:** How old calibration affects the confidence badge and what supporting information is shown

**Linked requirements:** REQ-DISPLAY-02, REQ-DISPLAY-03, REQ-CAL-04, master REQ §9.5

**Actors:** System (tracks calibration age, updates badge and indicator)

**Preconditions:**
- A calibration record exists
- Modern Mode is active

**Behavioral flow:**

1. The system continuously computes calibration age as `current_UTC_time − calibration_saved_at`.

2. The calibration age indicator (a separate UI element from the confidence badge) displays:
   - Format: **"Cal: [N]d"** where N is the number of complete days since calibration. For calibrations less than 1 day old, "Cal: 0d" is shown.
   - Color coding of the indicator label:
     - `[0, 7 days]`: **green dot** + "Cal: Nd"
     - `(7, 30 days]`: **amber dot** + "Cal: Nd"
     - `(30 days, ∞)`: **red dot** + "Cal: Nd"

3. The calibration age is also one of the five confidence dimensions:
   - `[0, 7 days]` → Good (score 3)
   - `(7, 30 days]` → Moderate (score 2)
   - `(30 days, ∞)` → Poor (score 1)

4. When calibration age exceeds 30 days:
   - The cal_age dimension score becomes Poor (1).
   - This causes the overall confidence score to be Poor (1) regardless of other dimensions (minimum-of-all rule).
   - The confidence badge shows **"Poor"** in red.
   - The calibration age indicator shows a red dot.
   - No separate "calibration expired" advisory text is shown in Phase 1 (REQ-CAL-05 recalibration prompts are deferred to Phase 4). The Poor badge and red indicator provide the signal.

5. There is no separate advisory text appended to the badge for old calibration. The badge text is simply "Poor." If the user wants to understand why it is Poor, they can check the calibration age indicator, which shows the age in days.

**Business rules:**
- The calibration age indicator and the confidence badge are two independent UI elements. The indicator shows age in days; the badge shows the computed confidence level. They can convey seemingly different information (e.g., "Cal: 5d" + "Poor" is possible if another dimension is Poor).
- Calibration age > 30 days always produces a Poor confidence badge via the minimum-of-five rule. There is no "advisory-only" mode for old calibration that leaves the badge at Moderate. The threshold is a hard floor.
- In Phase 1, no automatic recalibration prompt is shown when calibration becomes old. The red dot and Poor badge are the only signals. Recalibration prompts are Phase 4 (REQ-CAL-05).
- The day count rounds down (floor). A calibration that is 1 day and 23 hours old shows "Cal: 1d."

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Calibration is 5 days old; all other conditions Good; tilt ≤ 5° | Heading displayed | Badge shows "High" (green); calibration indicator shows green dot + "Cal: 5d" |
| Calibration is 18 days old; all other conditions Good | Heading displayed | Badge shows "Moderate" (amber) due to cal_age score = 2; indicator shows amber dot + "Cal: 18d" |
| Calibration is 31 days old; all other conditions Good | Heading displayed | Badge shows "Poor" (red); indicator shows red dot + "Cal: 31d" |
| User recalibrates after an old calibration | Calibration saved | Badge immediately re-evaluates; indicator resets to "Cal: 0d" with green dot |

---

## 5. FSPEC-SENSOR — Sensor Degradation Modes

---

### FSPEC-SENSOR-01: No Magnetometer Available

**Title:** App behavior when the device has no magnetometer

**Linked requirements:** REQ-SENSOR-01, master REQ §11.1

**Actors:** OS (reports sensor availability), User (sees error screen)

**Preconditions:**
- App is launching (or has launched and discovers the sensor is absent)
- Both `TYPE_MAGNETIC_FIELD_UNCALIBRATED` and `TYPE_MAGNETIC_FIELD` are unavailable on this device

**Behavioral flow:**

1. At app start, the system queries for `TYPE_MAGNETIC_FIELD_UNCALIBRATED`. If not found, it queries for `TYPE_MAGNETIC_FIELD` (the fallback per REQ-SENSOR-01). If neither is found, the no-magnetometer path is taken.

2. The main compass UI (Modern Mode, compass rose, heading readout) is **never shown**. The app does not attempt to display a heading.

3. The app displays a dedicated **error screen** replacing the normal app UI entirely. The error screen contains:
   - A clear non-technical heading (localized): **"Compass unavailable"**
   - An explanation (localized): **"This device does not have a magnetometer. Compass functionality is unavailable."**
   - No compass rose, no heading value, no confidence badge — none of these elements are shown.
   - An optional "Report an issue" button (future enhancement; placeholder only in Phase 1 if not implemented).

4. The app does **not crash**. The error screen is a stable, non-interactive state.

5. In Phase 1, bearing history does not exist, so no "Bearing History (read-only)" fallback is needed. The error screen is the final state.

**Business rules:**
- This check must run before any compass display is attempted. The system must not display a "0.0°" or any heading value on a device without a magnetometer.
- The error screen is permanent for the session. There is no retry button, because if the sensor query returns null, the hardware is genuinely absent and a retry will return the same result. (If the OS sensor service restarts and the sensor becomes available, the user must relaunch the app.)
- The error is not a crash. Firebase Crashlytics (or equivalent) must not log this as a crash event — it is an expected, handled state.
- If a magnetometer sensor event is received after the no-magnetometer error screen has been shown, the event is logged and discarded. The error screen remains visible. The session is not automatically recovered.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Device has no magnetometer (both sensor types return null) | App launched | Error screen shown with "Compass unavailable" message; no compass rose or heading value displayed; app does not crash |
| Error screen is displayed | User waits 60 seconds | No change in screen; app remains stable |

---

### FSPEC-SENSOR-02: No Gyroscope Available

**Title:** App behavior when the device has a magnetometer but no gyroscope

**Linked requirements:** REQ-SENSOR-03, master REQ §11.2

**Actors:** OS (reports sensor availability), System (degrades fusion mode), User (sees advisory)

**Preconditions:**
- Device has a magnetometer (app proceeds past FSPEC-SENSOR-01)
- `TYPE_GYROSCOPE` is unavailable on this device

**Behavioral flow:**

1. At app start, the system queries for `TYPE_GYROSCOPE`. If not found, the no-gyroscope degradation path is taken.

2. The compass display (Modern Mode) is shown normally. The heading is computed using a magnetometer + accelerometer only fusion (no gyroscope integration). The heading continues to update in real time.

3. A **persistent advisory text** is shown in the Modern Mode UI, positioned below the confidence badge: **"No gyroscope — heading may be less stable"** (localized). This advisory is present for the entire session. It cannot be dismissed.

4. The confidence badge is capped at **"Moderate"** for the entire session, regardless of other dimensions. Even if all five confidence model dimensions score Good and tilt is ≤ 5°, the badge cannot exceed Moderate when no gyroscope is present.

5. Calibration remains available. The calibration wizard works the same way (it does not require a gyroscope). The calibration quality score is unaffected by gyroscope absence.

**Business rules:**
- The gyroscope check is performed once at app start. The result is not re-evaluated during the session (sensors do not appear and disappear on real devices while the app is running).
- The Moderate cap is a session-level constraint, not a confidence model dimension. It overrides the model output. If the model computes "High," it is capped to "Moderate" before display. If the model computes "Poor," it stays "Poor" — the cap cannot raise a score.
- The advisory is non-dismissible. It must remain visible as long as the no-gyroscope state persists (i.e., for the entire session).
- The heading may show more jitter than with a gyroscope. This is expected and acceptable. No additional jitter warning is shown beyond the advisory.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Device has magnetometer but no gyroscope | App launched | Compass rose and heading display normally; advisory "No gyroscope — heading may be less stable" is visible |
| All five confidence dimensions are Good; no gyroscope; tilt ≤ 5° | Heading displayed | Confidence badge shows "Moderate" (not "High") |
| No gyroscope; field deviation 30% | Heading displayed | Confidence badge shows "Poor" (interference overrides Moderate cap; Poor is already ≤ Moderate) |

---

### FSPEC-SENSOR-03: Broken or Stuck Sensor

**Title:** App behavior when the magnetometer appears to be returning constant or zero values

**Linked requirements:** REQ-SENSOR-01 (fallback behavior), master REQ §11.3

**Actors:** System (monitors for stuck sensor pattern), User (sees sensor error badge)

**Preconditions:**
- Magnetometer is registered and delivering data
- Sensor data exhibits a stuck pattern: identical values for > 3 seconds (variance < 0.001 µT²), or any sample returns exactly {0, 0, 0}

**Behavioral flow:**

1. The system monitors the rolling variance of magnetometer readings over a 3-second window.

2. **Detection criteria:**
   - Variance of all three axes < 0.001 µT² over a 3-second window: stuck sensor detected.
   - OR: any individual sample returns exactly {0, 0, 0}: immediate stuck detection (does not require 3-second window).

3. Within 3 seconds of detection:
   - The confidence badge changes to **"Poor"** (red).
   - A **"Sensor error"** badge or label replaces the normal confidence level text. The confidence badge text becomes **"Sensor error"** rather than "Poor." This is the only situation where badge text is not one of the three standard confidence levels.
   - An explanation text appears below: **"Sensor not responding — try restarting the app or device"** (localized).

4. The heading display freezes at the **last valid value** computed before the stuck condition was detected. The frozen value continues to be displayed — it is not zeroed or cleared. The compass rose and heading readout remain visible but static.

5. The compass rose needle does not rotate during the stuck state (since the heading is frozen).

6. Recovery: if the magnetometer readings resume changing (variance recovers above the stuck threshold), the "Sensor error" badge returns to the normal confidence badge, and the heading resumes updating. This recovery is automatic — no user action required.

**Business rules:**
- The "Sensor error" badge text is an exception to the High / Moderate / Poor naming. It is a special state that overrides the normal confidence model. Product treats it as equivalent to Poor for any decision-making purposes, but it is labeled distinctly to communicate a different cause.
- The frozen heading value must be the last heading that was computed before the stuck condition. The display must not show "0.0°" or a blank value.
- If the sensor is stuck from the very first sample (never had a valid reading), the heading display shows whatever the initial state is — likely "0.0°" or the fusion algorithm's initial estimate — and the sensor error badge appears within 3 seconds.
- This state does not trigger the interference warning. It is a hardware fault state, not an environmental interference state. The two warning systems are independent.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Magnetometer returns identical {X, Y, Z} for 3.5 seconds | System monitors variance | "Sensor error" badge appears; last valid heading is frozen; explanation text is shown |
| Sensor returns exactly {0, 0, 0} on a single sample | System processes sample | Immediate detection; same badge and freeze behavior |
| Stuck sensor recovers (variance resumes) | System detects recovery | "Sensor error" badge disappears; normal confidence badge re-evaluates; heading resumes updating |
| Magnetometer delivers the identical value from the very first sample onward | 3 seconds elapse | "Sensor error" badge appears and explanation text is shown; heading display shows last computed value (likely 0.0° or fusion initial estimate) |

---

### FSPEC-SENSOR-04: Power-Saving Mode Sensor Rate Advisory

**Title:** Advisory shown when device power-saving mode causes sensor rate to drop below target

**Linked requirements:** REQ §8, REQ §11.9

**Actors:** System, User

**Preconditions:**
- App is in foreground
- Modern Mode is active
- Device enters power-saving mode

**Behavioral flow:**

1. The system monitors effective sensor delivery rate, measured as events received per second over a 2-second sliding window.

2. If the effective rate drops below 15 Hz (from the target ≥20 Hz) while the device is in power-saving mode:
   - Show a non-blocking advisory banner below the compass rose: **"Reduced accuracy — device is in power-saving mode"**
   - Confidence is NOT changed by this advisory alone. The sensor rate reduction affects the running heading quality, which the confidence model already captures via the noise variance dimension.

3. The advisory is non-dismissible. It persists as long as the condition is true.

4. When the effective rate returns to ≥15 Hz for ≥2 seconds: the advisory clears automatically.

5. If the no-gyroscope advisory (FSPEC-SENSOR-02) is also active: both banners stack vertically below the compass rose.

**Business rules:**
- The app MUST NOT force-change the sensor sampling rate. The rate reduction is an OS decision. The app observes and reports only.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Device enters power-saving mode and sensor rate drops to 12 Hz | Condition persists for 2 seconds | Advisory banner appears with text "Reduced accuracy — device is in power-saving mode" |
| Advisory is showing | Device exits power-saving mode and rate returns to ≥15 Hz for 2 seconds | Advisory banner clears automatically |

---

### FSPEC-SENSOR-05: Rapid Rotation Stabilizing State

**Title:** "Stabilizing…" badge shown during rapid device rotation

**Linked requirements:** REQ §8, REQ §11.7, REQ-SENSOR-04

**Actors:** System, User

**Preconditions:**
- Modern Mode is active
- Gyroscope is available (if no gyroscope, this section does not apply — confidence is already capped at Moderate per FSPEC-SENSOR-02)

**Behavioral flow:**

1. The system monitors angular rate from the gyroscope sensor.

2. When angular rate exceeds 180°/s for >0.5 seconds continuously:
   - Replace the normal confidence badge with a **"Stabilizing…"** badge (amber, same visual weight as the Moderate badge).
   - Heading display continues updating but accuracy may be reduced.
   - Bearing capture remains available (not blocked).

3. The "Stabilizing…" badge persists while angular rate remains >180°/s.

4. When angular rate drops below 10°/s and stays there for ≥1 second: revert to the normal confidence badge based on the current five-dimension score.

5. If no gyroscope is available: this state never activates. The no-gyroscope advisory from FSPEC-SENSOR-02 is already permanent and FSPEC-SENSOR-05 does not apply.

**Business rules:**
- The "Stabilizing…" badge is amber text with no icon, same size as the confidence badge it replaces.
- It is NOT a separate overlay — it replaces the existing confidence badge view.
- It is accessible: text-only, no color dependency.
- If an interference warning is also active, both are shown (warning banner + Stabilizing badge).
- The 10°/s clearance threshold uses hysteresis (different from the 180°/s trigger) to prevent rapid toggling.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Gyroscope available | Angular rate exceeds 180°/s for 0.6 seconds | "Stabilizing…" amber badge replaces normal confidence badge |
| "Stabilizing…" badge is showing | Angular rate drops to 8°/s and stays there for 1.2 seconds | Normal confidence badge reappears |
| No gyroscope (FSPEC-SENSOR-02 active) | Device is rotated rapidly | "Stabilizing…" badge does NOT appear; no-gyroscope advisory remains |

---

## 6. FSPEC-DISPLAY — Modern Mode Layout and Elements

---

### FSPEC-DISPLAY-01: Modern Mode Screen Layout

**Title:** Which elements are always visible and which appear conditionally in Modern Mode

**Linked requirements:** REQ-DISPLAY-02, REQ-DISPLAY-03, REQ-DISPLAY-10, REQ-DISPLAY-11

**Actors:** System (manages layout), User (reads the screen)

**Preconditions:**
- App is in Modern Mode (the only mode in Phase 1)
- Device has a functional magnetometer

**Behavioral flow — always-visible elements:**

The following six elements are always visible in Modern Mode without scrolling, on any device with a screen of 5.0 inches or larger (as measured on a 1080×1920 density-420 viewport):

1. **Compass rose:** A 360° rotating dial with labeled cardinal points (N, E, S, W) and intercardinal points (NE, SE, SW, NW). The rose rotates in real time to reflect the current heading. The north label (N) remains at the fixed top pointer position.

2. **Digital heading readout:** Shows the current bearing to 0.1° precision (e.g., "247.3°"). This value updates at ≥ 20 Hz.

3. **North reference label:** In Phase 1, magnetic north is the only reference, so the label reads **"Magnetic N"** (localized). This label is positioned adjacent to the heading readout. It is never absent — heading values are always labeled.

4. **Confidence badge:** The High / Moderate / Poor badge as specified in FSPEC-CONFIDENCE-01. Always visible.

5. **Calibration age indicator:** The "Cal: Nd" indicator as specified in FSPEC-CONFIDENCE-03. Always visible. When uncalibrated, shows "Uncalibrated."

6. **Tilt indicator (conditional):** Appears only when tilt > 5° (see FSPEC-CONFIDENCE-02). At tilt ≤ 5°, the space is empty (no placeholder shown). This element occupies reserved space in the layout so that its appearance does not cause other elements to shift.

**Behavioral flow — conditionally visible elements:**

7. **First-launch banner** (FSPEC-LAUNCH-01): Appears only when no calibration has ever been saved. Dismissible.

8. **Interference warning banner** (FSPEC-DETECT-01): Appears only when field deviation ≥ 25% or inclination deviation ≥ 8°. Dismissible.

9. **No-gyroscope advisory** (FSPEC-SENSOR-02): Appears only when no gyroscope is present. Non-dismissible, persistent for the session.

10. **"Stabilizing…" badge** (FSPEC-SENSOR-05): Appears when angular rate > 180°/s for > 0.5 seconds. Replaces the confidence badge during rapid rotation. Returns to normal confidence badge when angular rate drops below 10°/s for ≥ 1 second. Only applies when gyroscope is available. See FSPEC-SENSOR-05 for full behavioral specification.

**Business rules:**
- Layout must be stable. When conditional elements appear or disappear, the six always-visible elements must not move or reflow. Conditional elements expand from reserved space or overlay areas. Engineers must use reserved layout regions for conditional elements.
- The reserved tilt indicator region height is sized to accommodate the longer warning string at default system font size: "Hold flat for best accuracy — Tilt: XX.X°" (approximately 2 lines at minimum supported screen width). At system font scale >1.3×, single-line overflow may wrap; this is acceptable. Text MUST NOT be truncated.
- The always-visible elements must pass Espresso `isDisplayed()` on a Pixel 4a API 34 emulator at default density (1080×1920, density-420) without scrolling.
- All text in Modern Mode must respect the system font scale setting (REQ-DISPLAY-11). At "Large" system font, the primary heading readout must remain visible and unclipped.
- The minimum primary heading font size is 16sp. The minimum for all other primary labels is 12sp.

**Screen-on wake lock behavior:**
- The app acquires the screen-on wake lock (WAKE_LOCK permission) when the app is in the foreground and Modern Mode is active.
- The wake lock is released when the app moves to the background (Activity.onStop()).
- The wake lock prevents the screen from dimming or sleeping during compass use, regardless of the device's display timeout setting.
- There is no "keep-screen-on" toggle in Phase 1 — the wake lock is always held while in foreground. The toggle is a future enhancement.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Modern Mode active; device held level; calibration exists | User reads the screen on a 5-inch 1080×1920 device | All six always-visible elements (rose, readout, north label, badge, cal indicator, tilt indicator space) are visible without scrolling |
| App is in foreground for 3 minutes with no user interaction | Screen observed | Screen remains on; does not dim or sleep |
| App is sent to background (Home button pressed) | Background state | Screen-on wake lock released; screen behaves normally per device settings |
| System font size set to "Large" | Modern Mode displayed | Heading readout and all labels are visible and not clipped |

---

## 7. FSPEC-MODE — Mode Switcher (Phase 1 Treatment)

---

### FSPEC-MODE-01: Mode Switcher Hidden in Phase 1

**Title:** How the app handles the mode switcher in a single-mode Phase 1 release

**Linked requirements:** REQ-DISPLAY-01

**Actors:** User (navigates the app), System (enforces single-mode constraint)

**Preconditions:**
- Phase 1 build is installed
- Only Modern Mode exists in this build

**Behavioral flow:**

1. The mode switcher control (tab bar, swipe gesture, or equivalent) is **entirely absent** from the Phase 1 UI. There is no tab bar, no swipe gesture to switch modes, and no menu item for "Luopan Mode" or "Sighting Mode."

2. The layout space that would be occupied by a mode switcher (e.g., a tab bar at the bottom of the screen) is not present. The screen real estate is used by the always-visible compass elements instead.

3. Modern Mode is the only mode. It is the default, permanent, and only display state. The concept of "last used mode" persistence is not applicable in Phase 1 — there is only one mode to use.

4. There is no way for a user to navigate to Luopan Mode or Sighting Mode in Phase 1. These modes do not exist in the codebase or the UI in any form accessible to users.

**Business rules:**
- The mode switcher must not appear in any form — not as a disabled button, not as a greyed-out tab, not as a "coming soon" placeholder. The UI behaves as if modes other than Modern Mode do not exist.
- The mode switcher UI is introduced in Phase 3 when Luopan Mode is delivered. At that point it will appear for the first time, with both Modern Mode and Luopan Mode available. Sighting Mode tabs are added in Phase 5.
- This constraint is a deliberate product decision: exposing a mode switcher with disabled modes creates user confusion and support burden. The Phase 1 UI is a clean single-mode compass.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| Phase 1 app is running | User examines the full screen layout | No tab bar, no mode switcher, no "Luopan" or "Sighting" label visible anywhere in the UI |
| User attempts to swipe left or right from Modern Mode | Swipe gesture performed | Nothing happens; no mode transition occurs |
| Phase 1 build | View hierarchy inspected programmatically | No view with tag or content description referencing "Luopan" or "Sighting" exists in the layout hierarchy |

---

## 8. FSPEC-L10N — Localization Behavior

---

### FSPEC-L10N-01: Locale Detection and String Selection

**Title:** How the app determines which language to use for all UI strings

**Linked requirements:** REQ-L10N-01, REQ-L10N-03

**Actors:** OS (reports system locale), User (reads localized UI)

**Preconditions:**
- App is launching
- Phase 1 supports four locales: zh-Hant, zh-Hans, ja, en

**Behavioral flow:**

1. On app start, the system reads the device's system locale setting (first locale in the user's ordered locale list in Android settings).

2. The app attempts to match the system locale to one of the four supported locales using the following priority:

   a. **Exact match:** If the system locale is `zh-Hant`, `zh-Hans`, `ja`, or `en`, that locale is used.
   
   b. **Region-variant match:** If the system locale is a regional variant of a supported locale (e.g., `zh-TW` → `zh-Hant`, `zh-CN` → `zh-Hans`, `zh-HK` → `zh-Hant`, `zh-MO` → `zh-Hant`, `ja-JP` → `ja`, `en-US` → `en`, `en-GB` → `en`), the parent locale is used. General rule: any `zh-` variant with region HK, MO, or TW maps to `zh-Hant`. All other `zh-` variants map to `zh-Hans`.
   
   c. **No match:** If the system locale is not a variant of any supported locale (e.g., `ko`, `fr`, `ar`), the app falls back to **`en`** (English).

3. In Phase 1, there is no in-app language setting. The locale is determined entirely by the system locale. An in-app override is a future enhancement.

4. The selected locale governs all user-facing strings: compass labels, warning messages, calibration instructions, confidence badge text, calibration age format, tilt advisory text, error messages, and all button and menu labels.

**Business rules:**
- There is no app-level locale override in Phase 1. The system locale is authoritative.
- English is the sole fallback for all unsupported locales. There is no partial localization (e.g., a Korean user gets English, not a mix of Korean and English).
- RTL is not supported. All four supported locales use LTR layout. If, through a future Android update, a supported locale gains RTL variants, the app may display LTR regardless. This is a known Phase 1 limitation (REQ-L10N-03).

**Edge cases:**
- System locale is `zh-Hant-TW` (Traditional Chinese, Taiwan): matched to `zh-Hant`.
- System locale is `zh-Hans-CN` (Simplified Chinese, China): matched to `zh-Hans`.
- System locale is `zh` (generic Chinese, no script tag): matched to `zh-Hans` (Android convention treats untagged `zh` as Simplified).
- System locale is `ko` (Korean): no match; fallback to `en`.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| System locale is `ja` | App launched | All UI text, warning messages, and calibration instructions appear in Japanese; no English strings visible in primary UI |
| System locale is `zh-TW` | App launched | UI displays in Traditional Chinese |
| System locale is `ko` (Korean, unsupported) | App launched | UI displays in English (fallback) |
| System locale is `en-AU` | App launched | UI displays in English |
| System locale is `zh` (no script tag) | App launched | UI displays in Simplified Chinese (`zh-Hans`) |

---

### FSPEC-L10N-02: Which Strings Are Localized vs. Always in a Fixed Script

**Title:** Which UI elements are locale-dependent and which are always displayed in a fixed language or script

**Linked requirements:** REQ-L10N-01, REQ-DISPLAY-02

**Actors:** System (renders UI), User (reads labels)

**Preconditions:**
- App is running in any supported locale

**Behavioral flow:**

**Always localized (rendered in the user's app locale):**

1. All navigational and instructional text: button labels, menu items, screen titles, calibration wizard instructions, advisory messages.
2. Confidence badge text: "High," "Moderate," "Poor" — and their equivalents in zh-Hant, zh-Hans, ja.
3. Interference warning text: the heading and explanation lines in FSPEC-DETECT-01.
4. Tilt advisory text: "Hold flat for best accuracy" and similar.
5. Calibration quality labels: "Good," "Fair," "Poor" on the quality result screen.
6. Error messages: "Compass unavailable," "Sensor not responding," and all other system error strings.
7. The calibration age indicator label: "Cal:" prefix (localized), day count (numeric, locale-independent), "d" suffix or equivalent in the user's locale.
8. The "Uncalibrated" state label.

**Always in a fixed script (not locale-adapted in Phase 1):**

9. **Cardinal direction labels on the compass rose:** N, E, S, W, NE, SE, SW, NW. In Phase 1, these are always displayed as Latin letters regardless of locale. Localized equivalents (北, 南, 東, 西 in Chinese; 北, 南, 東, 西 in Japanese) are a future enhancement.

   > **Note:** The decision to keep compass rose labels as Latin letters in Phase 1 is a deliberate simplification. The compass rose is a globally understood visual vocabulary. Localizing rose labels is deferred.

10. **Degree symbol and numeric values:** Always rendered as Arabic numerals (0–9) and "°" regardless of locale. "247.3°" is "247.3°" in all locales.

11. **µT unit:** Always rendered as "µT" (Latin characters) regardless of locale.

**Business rules:**
- Cardinal direction letters on the compass rose (N, E, S, W, NE, SE, SW, NW) are not localized in Phase 1. This is a product decision, not a technical limitation. Engineers must not substitute localized characters for these labels.
- All other UI strings must have translations in all four locales. A missing translation for any visible string in any supported locale is a release blocker.
- Numeric values (bearing degrees, residual RMS, tilt angle) always use Western Arabic numerals and standard decimal notation regardless of locale. No locale-specific number formatting (e.g., comma as decimal separator) is applied to sensor-derived numeric values.

**Acceptance tests:**

| Given | When | Then |
|-------|------|------|
| System locale is `zh-Hans` | Modern Mode displayed | Confidence badge text is in Simplified Chinese; compass rose cardinal labels are N, E, S, W (Latin) |
| System locale is `ja` | Interference warning displayed | Warning heading and explanation text are in Japanese |
| System locale is `zh-Hant` | Calibration wizard running | All calibration instruction text is in Traditional Chinese; degree values and µT values use Arabic numerals and Latin symbols |
| System locale is `en` | Quality result screen shown | "Good," "Fair," or "Poor" label displayed in English |

---

*End of FSPEC-luopan-p1-core-compass v0.2-draft*
