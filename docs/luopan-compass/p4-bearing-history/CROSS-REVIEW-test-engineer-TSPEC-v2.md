# Cross-Review: test-engineer — TSPEC

**Reviewer:** test-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/TSPEC-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 2

---

## Iteration 1 Finding Status

| ID | Iteration 1 Severity | Resolution Status | Notes |
|----|----------------------|-------------------|-------|
| F-01 | Medium | Resolved | AT-VAR-01 added in §9.3a with concrete values pinning population variance formula |
| F-02 | Medium | Resolved | AT-VM-DRIFT-01 and AT-VM-DRIFT-01b added in §9.3b covering TRIGGERED→cooldown-check→VISIBLE wiring |
| F-03 | Medium | Resolved | deleteRecord/undoDelete error paths explicitly marked out-of-scope in §10 with rationale |
| F-04 | Medium | Resolved | Two-phase AT-CAL-INT-01 structure with pre-threshold negative assertion in §9.3 |
| F-05 | Low | Resolved | AT-UNDO-VM-01 through AT-UNDO-VM-03 added in §9.10a |
| F-06 | Low | Resolved | AT-CAL-03-B2 added in §9.7 |
| F-07 | Low | Resolved | buildJsonFromSensors() extracted as internal pure function; context dependency confined to buildJson() |
| F-08 | Low | Resolved | AT-HIST-04-A extended with search_bar GONE assertion in §9.9 |
| F-09 | Low | Resolved | elapsedCountingMs() exposed as internal in §5.3 |
| F-10 | Low | Resolved | scrollToPosition documented as mandatory inside AT-HIST-01-A loop in §9.4 |

All four Medium findings from iteration 1 (F-01 through F-04) are resolved.

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | `FakeDriftDetector` in §9.3b is defined as `class FakeDriftDetector(private val nextEvent: DriftEvent?) : DriftDetector(FakeClock(0L))`. This subclasses `DriftDetector` with a hard-coded inner `FakeClock(0L)`, but `DriftDetector` stores the `clock` parameter and uses it in `elapsedCountingMs()`. If `CompassViewModel`'s sensor loop (or the test harness) calls `elapsedCountingMs()` on the stub, it will read from the inner fixed clock, not from the test's controlling `FakeClock`. More critically, `DriftDetector.reset()` is a non-virtual method that clears `state` and `countingStartMs`; if `FakeDriftDetector` relies on `reset()` being a no-op (e.g., the subclass does not override it), the test can pass even if `resetDriftDetector()` is broken. The canonical pattern in this project is interface-based fakes (`FakeBearingCaptureUseCase` implementing an interface), not subclass overrides. `DriftDetector` should be either (a) extracted behind a `DriftDetectorContract` interface that `FakeDriftDetector` implements directly, or (b) the TSPEC must explicitly document that only `onFrame()` is overridden and `reset()` is tested via the real `DriftDetector` path. Without this clarification, AT-VM-DRIFT-01 cannot reliably catch a broken `resetDriftDetector()` call in `CompassViewModel`. | §9.3b, §5.3 |
| F-02 | Medium | AT-SENSOR-01-E (§9.10b) calls `logger.maybeWrite()` on a `SensorCapabilityLogger(mockContext, fakeSettings, throwingWriter)`. The `maybeWrite()` body calls `buildJson()` before `fileWriter.write()`. `buildJson()` calls `context.getSystemService(Context.SENSOR_SERVICE)`. In a JVM unit test environment `mockContext` is likely a Mockito or hand-written mock; if `getSystemService()` on this mock returns `null`, the cast to `SensorManager` will throw `NullPointerException` (not `IOException`) — which IS caught by `catch (e: Exception)`, meaning the test passes trivially without the `fileWriter.write()` call ever being reached. The test therefore cannot distinguish between "write failed" and "sensor enumeration failed silently," yet it asserts only that `sensorProfileWrittenForVersion` is unchanged. This produces a false positive: the test passes even if the write-failure path is unreachable. The TSPEC must either (a) specify that `AT-SENSOR-01-E` is a subclass override of `buildJson()` that returns a fixed string (allowing `throwingWriter.write()` to be reached), or (b) acknowledge that the test verifies the broad catch contract but not the specific write-failure path, and add a separate named test that verifies `throwingWriter.write()` was actually invoked (by recording invocations in the fake) before the exception propagated. | §9.10b, §5.5 |
| F-03 | Low | The coverage matrix (§9.1) lists `BearingHistoryViewModel (drift banner)` as unit-tested by `AT-CAL-02-A, D, E (FakeClock)` plus the new `AT-VM-DRIFT-01`. However, `AT-CAL-02-A` in the FSPEC is specified as a unit test of `CompassViewModel` (not `BearingHistoryViewModel`) that directly sets `driftBannerState` by processing a `DriftEvent.TRIGGERED` from a stub. The coverage matrix assignment is ambiguous: it lists the test under `BearingHistoryViewModel` but the test subject is `CompassViewModel`. Implementers looking at the coverage matrix will not know which file to place `AT-CAL-02-A` in. The matrix row should read `CompassViewModel (drift banner state)` and the file assignment should be `BearingHistoryViewModelTest.kt` or a new `CompassViewModelDriftTest.kt` as named in §9.3b — whichever is the authoritative assignment. | §9.1 |
| F-04 | Low | `AT-VM-DRIFT-01` (§9.3b) uses `viewModel.simulateSensorFrame()` described as a `@VisibleForTesting`-annotated method on `CompassViewModel`. This method is introduced in the TSPEC as a testing seam but is not defined anywhere in §6.1 (the `CompassViewModel` specification). Its signature, implementation, and which internal drift-detection block it calls are unspecified. An implementer will not know whether `simulateSensorFrame()` should directly call the drift-detection block in `startSensorCollection()`, or emit onto a fake `SensorLayer`, or something else. Without a specified implementation, engineers may add production-code complexity (e.g., a coroutine-launching dispatch that requires a test scheduler) that makes the test flaky. The TSPEC should add the full method signature and body to §6.1 under a `@VisibleForTesting` subsection, mirroring the pattern used for `CalibrationEngine.classifyQuality()` in Phase 3. | §9.3b, §6.1 |
| F-05 | Low | AT-CAL-03-B2 (§9.7) advances the clock by `clock.advance(61_001L)` in a single call before invoking `onFrame()`. The test constructs the detector at `FakeClock(0L)`, advances 61,001 ms without any intervening `onFrame()` calls, then calls `onFrame()` once. But `DriftDetector.onFrame()` only transitions from IDLE → COUNTING on the frame that first sees all preconditions hold, and the timer starts at `clock.nowMs()` at that transition point. At t=61,001 ms, the first `onFrame()` call transitions IDLE → COUNTING and sets `countingStartMs = 61001`. The elapsed check is then `clock.nowMs() - countingStartMs = 0`, which is NOT `> 60,000`. The test as written passes trivially (no trigger, as expected), but for the wrong reason: the detector never reached the elapsed-timer check because the state machine stayed IDLE until t=61,001 ms. The test must drive enough frames at regular intervals to accumulate 60+ seconds of COUNTING time, as AT-CAL-INT-01 does. The current single-advance pattern cannot verify that the `> 0.10` boundary (the strict greater-than) is correctly evaluated at the time the timer first crosses 60 s. | §9.7 |
| F-06 | Low | The coverage matrix (§9.1) notes that Room migration v2→v3 is covered by a `LuopanDatabaseMigrationTest` addition, but does not specify the three assertions that must be present (per iteration 1 F-08 which was listed as a finding but not reflected in the resolution table in §12): (a) `expected_field_ut` column is added, (b) existing rows have `expected_field_ut = 0.0`, (c) `CalibrationRecord.expected_field_ut` round-trips via the DAO. The resolution table in §12 does not include F-08 from iteration 1 (it jumps from F-07 to F-09 in the resolution table, suggesting F-08 was inadvertently dropped). Without explicit assertion specifications, the migration test may be written as a schema-only existence check, missing the data-integrity assertion (b) that is the critical safety property (existing records must not gain a non-zero `expected_field_ut` value that would silently arm drift detection for old calibrations). | §9.1, §12 (TE TSPEC-v1 resolution table) |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | §9.3b: `AT-VM-DRIFT-01b` uses `FakeAccelerometerVarianceTracker(variance = 0.001f)` — a type that does not appear elsewhere in the TSPEC and is not listed in §3.1 new files. Is this a second test double introduced by this iteration, and if so, should it be added to the file list in §3.1? Or is the intent for `CompassViewModel` to accept `AccelerometerVarianceTracker` as a constructor parameter and call `update()` on it normally, with `FakeAccelerometerVarianceTracker` overriding `update()` to return a fixed variance without processing real sensor data? This is a testability architecture question that must be resolved before implementation. |
| Q-02 | §5.3 `DriftDetector.onFrame()`: when the state is COUNTING and a precondition violation occurs, the method returns `DriftEvent.RESET`. The TSPEC says "ViewModel may ignore this event." The unit test AT-CAL-03-A (FSPEC §FSPEC-CAL-03) verifies that the timer resets and TRIGGERED is not returned — but no iteration-2 test asserts the negative ViewModel behavior: that `driftBannerState` remains `HIDDEN` (or `VISIBLE`, if it was already visible) when `DriftEvent.RESET` is received. Iteration 1 F-06 (Low) was listed as a finding about this gap but does not appear in the iteration-2 resolution table in §12. Was this finding intentionally deferred or was it inadvertently omitted? |
| Q-03 | §6.3 `BearingHistoryViewModel.deleteRecord()` reads the pending undo slot: "If a prior undo is pending, it is replaced." The `pendingUndo` field is assigned unconditionally (`pendingUndo = record`) before the IO launch. This means if a second `deleteRecord()` is called before the first IO delete completes, `pendingUndo` now points to the second record — but the DAO `delete()` for the first record is still in-flight. This is by design (first deletion becomes permanent), but there is no test that verifies the first deletion was committed to the DAO before the undo is replaced. Should AT-UNDO-VM-01 be extended to assert that `fakeDao.getAll()` no longer contains the first record before calling `deleteRecord(secondRecord)`, to guard against the replacement happening before the first IO completes? |

---

## Positive Observations

- The two-phase AT-CAL-INT-01 structure (§9.3) correctly resolves iteration 1 F-04: a pre-threshold negative assertion (`triggeredEarly == false`) using boolean accumulation ensures the test cannot pass due to early or spurious firing. This is a well-constructed guard.
- AT-VAR-01 (§9.3a) is a best-practice formula-pinning test: using concrete input values (3, 5, 4) that produce different results under population vs. sample variance makes the assertion self-documenting. Any silent formula change immediately produces a test value of 1.0 (sample) instead of 0.6667 (population), within float tolerance.
- AT-UNDO-VM-01 through AT-UNDO-VM-03 (§9.10a) correctly isolate the `pendingUndo` state machine from the Espresso tests, following the test-pyramid principle: the ViewModel pure logic is at the unit level, end-to-end swipe behavior is at the instrumented level.
- The `buildJsonFromSensors()` extraction (§5.5, §9.10b) correctly resolves the JVM-runnability problem for sensor serialization tests. Confining the Android context dependency to the single-line `buildJson()` wrapper is a textbook testability refactor.
- The explicit "Out-of-scope for Phase 4 testing" declaration for undo/delete error paths (§10) with documented rationale (Room Flow self-heals) is the correct way to handle an accepted gap — it makes the gap visible to implementers and prevents future engineers from adding redundant test infrastructure for it.
- The two-launcher design for `ActivityResultLauncher` (§6.2) remains unchanged and continues to be the correct approach: each result path is independently testable without disambiguation.
- `DriftDetector.elapsedCountingMs()` (§5.3) exposed as `internal` for test inspection continues to be the right pattern, consistent with the project's `@VisibleForTesting` precedent.

---

## Recommendation

**Approved with Minor Issues**

> All four Medium findings from iteration 1 (F-01 through F-04) are resolved. F-01 and F-02 in this iteration are newly raised Medium findings that were not present in iteration 1 — they concern the `FakeDriftDetector` subclass pattern and the `AT-SENSOR-01-E` false-positive risk respectively. These must be addressed before implementation begins. F-03 through F-06 are Low and should be resolved but do not block the start of implementation if the Medium findings are addressed first. The TSPEC iteration 2 represents a substantial improvement over iteration 1 and is implementer-ready for all components not touched by F-01 and F-02.
