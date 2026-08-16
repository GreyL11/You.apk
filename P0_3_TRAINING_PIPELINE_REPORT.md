# P0-3: Training Pipeline Completion Report

Scope: complete the training pipeline around the existing working components (`Coach.kt`'s progression math, ML Kit camera/pose infrastructure, Room persistence). No unrelated migration items touched.

## 1. Legacy files/functions inspected (full reads, not summaries)
`www/exercises.js` (1029 lines — all 28 `EXERCISES` entries, `benchLike`/`raiseLike`/`curlLike` factories, shared fault builders, `SAFETY` table, `calibrate()`, `cameraCheck()`, `jointsUsedBy()`, `step()`), `www/coach.js` (380 lines — `suggest()`, `warmupsFor()`, `createCoach()`'s `select/endSet/preview/finishExercise/amendReps`), `www/planner.js` (246 lines — `SPLITS`/`TRAINING_DAYS`/`SCHEME`, `achievableLoad()`/`loadout()`/`loadoutText()`/`barbellStep()`, `startingLoad()`/`increment()`, `buildSession()`/`weekPlan()`/`today()`/`doneToday()`), `www/insights.js` (329 lines — `e1rm`, `sessions()`'s chronology-by-timestamp guarantee, `stalledSessions`/`shouldDeload`/`deloadTo`), `www/filter.js` (138 lines — the One-Euro landmark smoother).

## 2. Android files/functions inspected
`domain/Coach.kt`, `domain/Exercises.kt` (pre-P0-3: 5 constants only), `domain/RepCounter.kt` (pre-P0-3: single hardcoded hip-knee-ankle state machine, now removed), `ui/screens/PoseAnalyzer.kt`, `ui/screens/LiveSessionScreen.kt`, `ui/screens/Sheets.kt`'s `WorkoutSheet`, `ui/screens/TodayViewModel.kt`'s `logTraining`, `ui/screens/TodayScreen.kt`, `MainActivity.kt`'s `NavHost`, `data/Entities.kt`'s `Verdict`/`LogEntry`, `data/Daos.kt`'s `VerdictDao`/`LogEntryDao`.

## 3. Working components preserved (not rebuilt)
- **`Coach.kt`'s core algorithm** — `estimate1RM` (Epley), `isExecutionClean` (0.34 threshold), `countStalls`, the overall `evaluateSession` branch structure — untouched. Only the two proven gaps (below) were patched, surgically, inside the existing branches.
- **ML Kit camera/pose infrastructure** — `PoseAnalyzer`'s detector setup, CameraX preview binding, permission handling all unchanged; only its callback signature was extended to also pass image width/height (needed to normalize landmark coordinates, not a behavioral rewrite).
- **Room persistence architecture** — no new tables, no schema changes. `Verdict`/`LogEntry`/`ActionOutcome` entities and their DAOs are used exactly as they already existed.
- **`BoxingScreen.kt`'s `RearCameraPreview()`** — left byte-for-byte identical inside the rewritten `LiveSessionScreen.kt` file, since Boxing depends on it and Boxing is out of this task's scope.

## 4. Exact gaps fixed (source-comparison-proven, per the audit's own list)
1. **Exercise catalogue incomplete** → all 28 exercises ported (§5).
2. **Exercise-specific/fault rules incomplete** → all fault rules ported with their real cues/thresholds/phases/views (§6).
3. **Session planning incomplete** → `Planner.kt` ports splits, rep schemes, starting loads (§7).
4. **Load handling effectively hardcoded/zero** → `LiveSessionScreen` now uses `TodayViewModel.suggestedLoad()` (last real logged load, or `Planner.startingLoad()` — never a literal) instead of the old `val load = 0.0` (§7).
5. **Plate-loadout snapping unbuilt** → `Planner.achievableLoad/loadout/loadoutText/barbellStep` ported and wired into `Coach.evaluateSession` (§7).
6. **Pose/rep logic specialized to one hardcoded exercise** → `MovementEngine` is a generalized `ExerciseDef`-driven state machine; `RepCounter.kt` (the narrow version) is deleted, not left as dead code (§4/§9).
7. **Coach.kt had no bodyweight branch** (found during source comparison, not in the original gap list, but a real, provable behavioral difference from legacy's `preview()`) → fixed (§7).

## 5. Exercises migrated
**All 28** — legacy's exact scope, nothing invented, nothing dropped: `squat, rdl, lunge, bench, inclineBench, declineBench, dbBench, inclineDbPress, chestDip, pushup, deadlift, row, cableRow, straightArmPulldown, latPulldown, ohp, lateralRaise, frontRaise, rearDeltRaise, cableLateralRaise, cableFrontRaise, curl, hammerCurl, cableCurl, pushdown, skullcrusher, overheadExtension, dip`. The 13 built from legacy's `benchLike`/`raiseLike`/`curlLike` factories are ported the same way (one factory call with the exercise's real name/cameraHint/override), the other 15 are individually transcribed — verified test asserts `EXERCISES.size == 28` and that every specific id exists.

## 6. Rules/thresholds migrated
Every fault rule (id, cue text, phase, view-gating, `bothSides` flag) and every numeric threshold for all 28 exercises is transcribed directly from `exercises.js`, including the shared builders (`lockoutFault`, `torsoLeanFault`, `fastEccentricFault`, `upperArmFault`, `fixedUpperArmFault`, `elbowPathFault`, `shortRangeFault`, `minLeanFault`, `barDriftFault`) and the `SAFETY` table (which faults are never-baselined-away vs. habituation-tolerant). Squat's `valgus` fault preserves a genuine legacy quirk verbatim rather than "fixing" it: it reads both-side raw landmarks directly rather than through the tracked-side proxy, so it carries **no visibility gating** in legacy — this is preserved exactly, with a code comment explaining why, not silently corrected.

The **visibility-self-gating mechanism itself** (`jointsUsedBy`'s runtime Proxy in legacy) is ported as `TrackingPoints` — a Kotlin class that records exactly which joints a fault's closure touched *while it ran*, so each fault still abstains only on the joints it personally reads, discovered dynamically every frame, not a hand-maintained or pre-computed list.

## 7. Load and plate behavior migrated
`Planner.kt` ports `achievableLoad`, `loadout`, `loadoutText`, `barbellStep`, `startingLoad`, `increment`, `restSeconds`, `available`, `buildSession`, `weekPlan`, `today` — all formulas and constants transcribed exactly (`EXPERIENCE` multipliers, `SCHEME` table, `SPLITS`/`TRAINING_DAYS`). `Coach.evaluateSession` now calls `Planner.increment`/`Planner.achievableLoad` for a real, plate-snapped next weight instead of a flat unsnapped `+2.5`, and bodyweight exercises (`loadRatio == 0.0` — `chestDip`/`pushup`/`dip`) progress the **rep target**, never a fabricated load. `LiveSessionScreen`'s "Finish & Save" now sends a real load (`TodayViewModel.suggestedLoad`: last logged weight, or `Planner.startingLoad` if none) — bodyweight exercises still report `0.0`, but that's now a true fact about the exercise, not a placeholder standing in for an unfinished feature.

## 8. Fault/coaching behavior migrated
`MovementEngine.step()` distinguishes exactly what legacy distinguishes: a **valid completed rep** (`repCompleted=true`, timing ≥`MIN_REP_MS`), a **rejected/too-fast movement** (`rejected` counter, matching legacy's "kit being moved, not a lift" guard), a **completed-but-faulted rep** (rep counts *and* fault events both recorded, never collapsed into each other), and **invisible/unusable data** (`visible=false`, `missing` joint list, faults skipped entirely that frame). Fault events are real `{rep, id}` pairs (`FaultEvent`), not a bare integer — `TodayViewModel.logTraining` now persists them as real JSON (`{"rep":N,"id":"..."}`) instead of the prior `"[{}]".repeat(faultCount)` placeholder.

## 9. New execution flow
```
WorkoutSheet's exercise picker (all 28, was squat/bench only)
  → onStartLiveSession(exId) → nav route "liveSession/{exId}" (was a fixed, argument-less route)
  → LiveSessionScreen(exId) → TodayViewModel.suggestedLoad(exId) [real last-load or Planner guess]
  → PoseAnalyzer (ML Kit, now also emits image width/height)
  → extractSide() maps ML Kit landmarks → Joint/Pt (both sides)
  → MovementEngine(exId).step(frame) → real rep count + real FaultEvent list
  → "Finish & Save" → TodayViewModel.logTraining(exId, reps, load, faultEvents, profile)
      → Coach.evaluateSession(history, reps, load, faultCount, exId, profile) [bodyweight-aware, plate-snapped]
      → LogEntry insert (real faultEvents JSON) + Verdict insert (previously computed and DISCARDED — now persisted)
      → ActionOutcome insert → refresh() → HealthCoachEngine recomputed
```

## 10. Files changed
**New**: `domain/MovementEngine.kt`, `domain/Planner.kt`, `test/domain/MovementEngineTest.kt`, `test/domain/PlannerTest.kt`, this report.
**Rewritten**: `domain/Exercises.kt` (5 constants → full 28-exercise catalogue + engine constants), `ui/screens/LiveSessionScreen.kt` (hardcoded → exId-driven; `RearCameraPreview` preserved verbatim).
**Surgically patched**: `domain/Coach.kt` (bodyweight branch + plate-snapped increment/deload; core algorithm otherwise untouched), `ui/screens/TodayViewModel.kt` (`logTraining` now persists `Verdict` + real fault JSON + accepts `faultEvents`/`profile`; added `suggestedLoad`), `ui/screens/Sheets.kt` (`WorkoutSheet`'s exercise picker generalized; `onStartLiveSession` now carries `exId`), `ui/screens/TodayScreen.kt` (`onNavigateToLiveSession` now carries `exId`), `ui/screens/PoseAnalyzer.kt` (callback also emits image dimensions), `MainActivity.kt` (nav route carries `exId`), `test/domain/ExercisesTest.kt` (old `Exercises.CONSTANT` API → new top-level API + catalogue-integrity tests), `test/domain/CoachTest.kt` (added bodyweight/plate-snap tests; existing tests' expected values re-verified by hand-tracing, not just assumed compatible).
**Deleted**: `domain/RepCounter.kt` (fully superseded by `MovementEngine.kt`, not left as dead code).

## 11. Tests added
`ExercisesTest.kt`: 11 tests (catalogue completeness, per-exercise threshold spot-checks, safety-fault stamping, factory-override correctness, calibration floor/range refusal, camera-view classification). `MovementEngineTest.kt`: 7 tests (valid rep, too-fast rejected rep, fault fires only at `HOLD_FRAMES` and doesn't re-fire every subsequent frame, missing/low-visibility joint gating on two different exercises, bodyweight-vs-loaded `loadRatio` sanity). `PlannerTest.kt`: 11 tests (barbell step, achievable-load snapping including a coarse-plate case, loadout biggest-plates-first + honest inexact reporting, loadout text rendering, bodyweight vs. loaded starting load, snap-idempotence, increment rules, equipment/injury filtering, no-duplicate-lift-in-a-session, rest-day handling). `CoachTest.kt`: +4 tests (clean bodyweight progresses reps not load; bodyweight never deloads; loaded lift snaps to a real plate grid; the pre-P0-3 4-arg call shape still works without crashing). All existing `CoachTest.kt` assertions were hand-traced against the patched code to confirm they still hold (see the `loadable()`/`deloadTo()` null-safety fix in §13) — not merely assumed compatible.

**SOURCE_WRITTEN_NOT_EXECUTED** — see §12.

## 12. Build/test/runtime verification status
- **Source inspection**: every file above was read in full before and after editing; every new/changed reference (`onStartLiveSession`, `logTraining`, `evaluateSession`, `Exercises.*`, `RepCounter`, `getAngle`) was re-grepped project-wide after the edits to confirm no dangling call sites remain.
- **Build**: attempted twice (`./gradlew help` and `./gradlew :app:compileDebugKotlin`, JDK 17, `--no-daemon`) — both fail identically at Gradle's own daemon bootstrap (`java.io.IOException: Unable to establish loopback connection`), the same sandbox network-isolation limitation P0-2 already documented. This occurs before any project evaluation or source compilation, so it is **not evidence of a source error** — just confirmation the environment blocker from P0-2 is unchanged.
- **Tests**: **SOURCE_WRITTEN_NOT_EXECUTED.** The same Gradle blocker prevents `./gradlew :app:testDebugUnitTest` from running. No test result is claimed as passing beyond hand-tracing shown in this report.
- **Runtime/device**: not attempted, no device available.

**Status: SOURCE_IMPLEMENTED_NOT_BUILD_VERIFIED.**

## 13. Remaining training gaps (explicit, not glossed over)
- **Landmark-space gap**: ML Kit exposes only image-pixel positions (`getPosition()`/`getPosition3D()`, z relative to the hip) — there is no ML Kit equivalent of MediaPipe's true metric `worldLandmarks`. Every angle in this port is computed on normalized-by-image-size pixel coordinates (legacy's `lm` space), not legacy's aspect-undistorted metric `w` space. Thresholds were tuned against the latter; expect more perspective/aspect distortion here than in legacy, especially off-axis.
- **Smoothing gap**: legacy's One-Euro filter (`filter.js`) runs on every landmark before `step()` sees it. Nothing here re-implements that yet — `MovementEngine` applies only the same lighter `EMA_ALPHA` smoothing legacy's own `step()` does on the primary angle. Raw-landmark fault checks (torso lean, bar drift, squat depth/valgus) will be noisier.
- **`reps`-vs-`target` distinction**: legacy's `preview()` separately tracks "were the target reps hit" from "was the fault rate clean," giving two different reasons ("reps missed" vs. "form broke down"). `Coach.evaluateSession` has no `targetReps` input to make that distinction — found during this pass, deliberately not fixed (would need a signature change beyond the two proven gaps this task was scoped to).
- **Deload rounding granularity**: the pre-existing Android `deloadTo` rounds to nearest 0.5 kg; legacy's own `insights.js round2()` rounds to nearest 2.5 kg. This predates P0-3 and was deliberately left alone (changing it would break already-passing `CoachTest.kt` expectations this task wasn't asked to touch) — real plate-snapping via `Planner.achievableLoad` still applies on top of it when a barbell `exId` is given.
- **No calibration UI, no per-exercise threshold-slider settings screen** — `calibrate()`/`cameraCheck()` are ported as pure functions but nothing in the UI calls them yet.
- **No `Profile` persistence**: `TrainingProfile()` defaults are used throughout (mirroring legacy's own `DEFAULT_PROFILE` fallback pattern), since no screen currently writes the `Profile` table — a real per-user profile (equipment, injuries, plates) would change which lifts are offered and how loads are snapped, but that's a separate, pre-existing gap (Profile table dead) this task didn't touch.
- **Warm-ups, voice cues, `amendReps` correction flow**: not ported — legacy's `coach.js` has these; this pass focused on the catalogue/engine/load gaps explicitly named in the audit.

## 14. Does ML Kit still satisfy the legacy behavioral contract?
**Partially, with a specific, named degradation — not full parity, and not silently claimed as such.** ML Kit's 33-point pose topology names every joint legacy's `IDX` table needs (shoulder/elbow/wrist/index/hip/knee/ankle/heel/foot_index), so the *catalogue and fault logic* port over structurally intact. What does **not** carry over is legacy's metric, aspect-undistorted world-coordinate angle math (§13's landmark-space gap) and the One-Euro pre-smoothing (§13's smoothing gap) — both real accuracy costs, not merely theoretical ones, most likely to show up as noisier fault triggering and thresholds that read slightly differently than they did against MediaPipe's own pose model. Per the audit's own MediaPipe rule: switching to `com.google.mediapipe:tasks-vision` for Android (officially available, not evaluated in this project's history) would close both gaps at once, since it's the same model family the legacy thresholds were actually tuned against. That switch was explicitly out of scope for this task ("Do NOT replace ML Kit with MediaPipe in this task") — this section names the cost of not doing so, rather than pretending it doesn't exist.
