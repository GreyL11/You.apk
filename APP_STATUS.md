# You.apk — Complete App Details

Written 2026-08-20 from a direct, exhaustive read of every file on disk in `C:\Users\jaswanth.sarilla\StudioProjects\You_android` — including the ~80 files of uncommitted work from a separate session (see "Working-copy state" below). This supersedes the shorter status note written earlier the same day.

**Updated 2026-08-21** with the closed-loop adaptive coach (section 11) — the first section of this document written against a **verified green build**: `./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`, **350 tests, 0 failures, 0 errors** (confirmed by reading the JUnit XML in `app/build/test-results/`, not just the exit code). Sections 1–10 predate that run and are unchanged except where section 11 supersedes them.

---

## 1. What this app is

A Kotlin/Jetpack Compose Android rewrite of a shipped Capacitor/JS fitness+health app (`C:\Users\jaswanth.sarilla\gym-trainer`, GitHub remote `GreyL11/GYM-PT-AI`). The JS app's `www/health.js`, `digest.js`, `chat.js`, `t_inputs.js`, `validate.js`, `claims.js`, `explain.js`, `exercises.js`, `coach.js`, `planner.js`, `mood_insights.js`, `www/face/*.js` are the spec — nearly every Android file's header comment names which legacy file it ports and why. GitHub: `GreyL11/You.apk` (private).

**Guiding architecture, stated repeatedly across the codebase**: the deterministic engine decides, an LLM (Gemini) only words the decision, and any number the LLM states is checked against the real record before reaching the screen. Never a fabricated score; absent data is never silently treated as zero. This is not aspirational — `Validate.kt`/`Claims.kt`/`Explain.kt` are a real, tested enforcement layer for exactly this rule.

## 2. Working-copy state

- `C:\Users\jaswanth.sarilla\Downloads\You.apk` — synced to `origin/main` on GitHub, used for git operations.
- `C:\Users\jaswanth.sarilla\StudioProjects\You_android` — the live Android Studio project, run on a real device (Motorola Edge 60 Pro).

**Resolved 2026-08-21**: the ~80-file uncommitted backlog described here is now committed and pushed. Both working copies are synced to `origin/main` at `6b88cba`. The "exists only in one working copy" risk this section used to warn about is closed.

## 3. Build environment (this machine specifically)

- JDK: Android Studio's bundled JBR (`C:\Program Files\Android\Android Studio\jbr`, JDK 25) — system default JDK 11 is too old for Gradle 9.3.1.
- **Claude's own Bash/PowerShell tools cannot run Gradle here** — every invocation fails with `Unable to establish loopback connection`, confirmed repeatedly, including with sandbox disabled. The identical command works every time when the user runs it in Android Studio. A Sophos Intercept X theory was investigated and dropped (no supporting evidence in Sophos's own logs) — root cause undetermined, but consistently reproducible as specific to Claude's tool process spawning, not the project.
- Practical consequence: **all compiling/testing must be triggered by the user** in Android Studio. Claude can drive the already-built app afterward via `adb` (screenshots, taps, logcat) — confirmed working.
- Real, fixed issues along the way: missing `.env.example` (required by the Secrets Gradle plugin), a `gradle-daemon-jvm.properties` pinning to a network-unreachable toolchain, and a `debugConfig` signing override pointing at a keystore that doesn't exist locally (the project's own README documents removing it).

## 4. Navigation map (`MainActivity.kt`, 231 lines)

Start destination is `"onboarding"` or `"home"` depending on `SettingsManager`'s `onboarding_complete` flag. An hourly `NotificationWorker` is scheduled here via `WorkManager`.

| Route | Screen | Reachable from |
|---|---|---|
| `onboarding` | `OnboardingScreen` | first launch |
| `home` | `TodayScreen` / `DashboardScreen` (2-tab, in-Scaffold, not separate routes) | after onboarding |
| `liveSession/{exId}` | `LiveSessionScreen` | tapping an exercise on Today, or `WorkoutSheet` |
| `chat` | `TalkScreen` (defined inside `MindScreen.kt`, despite the filename) | FAB on Today tab |
| `profile` | `ProfileScreen` | person icon in top bar |
| `weeklyReview` | `WeeklyReviewScreen` | "View Weekly Insights" on Today |
| `faceScan` | `FaceScanScreen` | "Guided Face Scan" card on Dashboard (only entry point) |
| `faceScanResult` | `FaceScanResultScreen` | after a successful face-scan capture |
| `faceHistory` | `FaceHistoryScreen` | "View History" on the result screen |

Bottom sheets (not routes, opened as local state from `TodayScreen`): `HydrationSheet`, `MealSheet`, `SkinSheet`, `WorkoutSheet`, `WeightSheet`, `SleepSheet`, `LabSheet` (all in `Sheets.kt`).

**Dead / unrouted screens (built but unreachable from any nav path):**
- `BoxingScreen.kt` — literal placeholder text `"Live Boxing Session: Camera feed placeholder."`
- `FaceScreen.kt` — a hardcoded, unconditional red "UNVALIDATED / Not enough data" card
- `SkinScreen()` (inside `MindScreen.kt`) — trivial static text
- `HormonalScreen()` (inside `MindScreen.kt`) — **the most fully-built dead screen in the app**: real sleep/wake/weight/training inputs, a factor-evidence list (SUPPORTED/PARTIAL/ABSENT), a labs list with `LabSheet`, and a Gemini-explained plan gated through `Explain`/`Validate`. Backed by `HormonalViewModel.kt`. Fully functional, simply never linked from anywhere.
- `LabSheet` — only caller is the unreachable `HormonalScreen`.

`StatsScreen.kt`/`StatsViewModel.kt` do not exist (an earlier session's notes referenced them; they're gone/renamed to Dashboard).

## 5. Screens & ViewModels (`ui/screens/`)

| File | Real feature | Notes |
|---|---|---|
| `TodayScreen.kt` (655 ln) / `TodayViewModel.kt` (477 ln) | Home feed: training plan card, Health Coach next-best-action card, nutrition card with fat-loss/gain/maintain phase chips + kcal/macro bar, hydration/skin/sleep cards | Calls `HealthCoachEngine`, `Nutrition`, `Coach`, `Planner`, `HealthConnectSync`, `GeminiClient` (meal parse). No fake values found. |
| `DashboardScreen.kt` (377 ln) / `DashboardViewModel.kt` (143 ln) | Bodyweight trend chart, per-exercise strength/1RM trend, time-range selector, Face Scan entry card | Calls `Coach.estimate1RM`, `EXERCISES`. |
| `ProfileScreen.kt` (304 ln) / `ProfileMapping.kt` (53 ln) | Gemini API key entry, Health Connect connect/import, real "Training setup" (bar/plates/equipment/injury chips) | `ProfileMapping.Profile.toTrainingProfile()` is the real fix that made stored equipment/injuries actually reach `Planner`/`Coach` (previously silently dropped — see §8). |
| `LiveSessionScreen.kt` (262 ln) / `PoseAnalyzer.kt` (39 ln) | Live camera rep-counting + fault cues via `MovementEngine`, plate-breakdown display | ML Kit pose detection (pixel-space only, not 3D world coords — a stated, accepted gap). |
| `FaceAnalyzer.kt` (270 ln) / `FaceScanScreen.kt` (215 ln) / `FaceScanViewModel.kt` (43 ln) / `FaceScanResultScreen.kt` (120 ln) / `FaceHistoryScreen.kt` (77 ln) | Guided face scan: MediaPipe FaceLandmarker (478 landmarks), real capture-quality gating (`FaceQuality`/`Geometry`), auto-capture, real history | **"SKIN ANALYSIS" card is a hardcoded, permanent `SkinAnalysis.NotAvailable`** — the one deliberate, honestly-labeled placeholder in the reachable app. |
| `MindScreen.kt` (320 ln) — misnamed file | Contains `TalkScreen()` (the real, reachable chat UI), plus dead `SkinScreen()`/`HormonalScreen()` | See §4. |
| `TalkViewModel.kt` (139 ln) | Chat backed by Gemini, with a real evidence bundle (today's data, 28-day `TInputs`, recent workouts, next-best-action) pruned via `Digest.prune` | |
| `OnboardingScreen.kt` (300 ln) | 3-step onboarding, writes the initial `Profile` row (hardcoded starting defaults: 20kg bar, a fixed plate set, `goal="hypertrophy"`) | |
| `WeeklyReviewScreen.kt` (198 ln) | Real 7-day counts, but "strength/weakness" wording comes from hardcoded inline thresholds (`>=5`, `>=3`) rather than a tested domain engine | Working, just not as rigorously built as the rest. |
| `Sheets.kt` (579 ln) | 7 bottom sheets: Hydration, Meal (Gemini-parsed or manual), Skin, Workout/ExercisePicker, Sleep, Lab, Weight | `LabSheet` is dead (only unreachable `HormonalScreen` opens it). |
| `HormonalViewModel.kt` (184 ln) | Fully real, backs the unreachable `HormonalScreen` | Functionally complete, just unrouted. |

**Theme** (`ui/theme/`): `Color.kt` (hand-paired dark/light palettes, "one accent = one meaning" rule), `Theme.kt`, `Type.kt` (system font + a monospace `NumberStyle` for measured quantities so numbers don't jump), `Shape.kt` (rounder-than-default corner scale). All deliberate design choices, documented in comments, no issues found.

## 6. Domain / business-logic engines (`domain/`, 32 files)

The real "intelligence" layer. Overall texture: heavily commented, each file names its legacy JS counterpart, strong stated invariants ("never invent a number," "never diagnose," read-only where it matters). Grouped by area:

**Training**: `Exercises.kt` (548 ln, the 25-lift catalogue + fault rules + `calibrate()`/`cameraCheck()`), `Coach.kt` (progression: Epley 1RM, INCREASE/HOLD/DELOAD), `Planner.kt` (session scheduling + real plate-loadout math), `MovementEngine.kt` (live rep/fault state machine), `FormBaseline.kt` (learns which faults are habitual vs. correctable, never suppresses SAFETY faults). All confirmed wired to real callers, all tested.

**Nutrition**: `Nutrition.kt` (384 ln — ~44-item food catalogue, water target, macro totals, and a full fat-loss/maintain/gain feedback loop that adjusts targets from observed scale trend vs. logged intake), `MealParse.kt` (validates Gemini's food-id guesses against the real catalogue).

**Health Coach / notifications**: `HealthCoachEngine.kt` (the next-best-action selector — hub of the whole notification system, heavily wired, **but has no dedicated unit test** beyond one narrow branch), `AdaptationEngine.kt` (learns per-action time-of-day preference from history), `NotificationDecisionEngine.kt` (quiet hours/cooldown/suppression gate), `NotificationController.kt` (builds/posts the real notification), `NotificationWorker.kt` (hourly `CoroutineWorker`, confirmed actually scheduled from `MainActivity`), `NotificationActionReceiver.kt` (handles the notification's own action buttons, confirmed registered in the manifest).

**Hormonal/lifestyle**: `TInputs.kt` (359 ln — sleep/weight/training factor evidence, circular-arc wake-time math, explicitly refuses to compute a combined score), `MoodInsights.kt` (sleep-block parsing/midnight-wraparound math — **no dedicated test file**, only indirectly covered), `HealthConnectSync.kt`/`HealthImport.kt` (read-only Health Connect import, sleep never overwrites hand-logged data, steps always does).

**Face scan**: `FacePipeline.kt` (thin wrapper), `FaceQuality.kt` (443 ln — sharpness/exposure/balance/lighting-drift/framing/pose/steadiness gate; one constant, `SHARPNESS_SCALE`, is explicitly self-flagged as needing real-device calibration), `Geometry.kt` (518 ln — face-mesh math: alignment, head pose, anatomical regions), `Topology.kt` (MediaPipe's 478-point mesh index table), `FaceScan.kt` (metrics + diagnosis + JSON persistence into the existing `FaceCapture` table; `SkinAnalysis.Available` is an unused, documented future-plug-in case).

**LLM safety/plumbing**: `Digest.kt` (system-prompt rules + payload pruning), `Claims.kt` (extracts numeric claims from model text), `Validate.kt` (checks those claims against real evidence — exact/decimal/percent matching, explicit stated limits), `Explain.kt` (the full evidence→ask→validate→retry-once→render-or-fallback orchestration), `GeminiClient.kt` (237 ln, OkHttp wrapper for `explain()`/`parseMeal()`).

**⚠️ Flag**: `GeminiClient.kt` hardcodes the model id `gemini-3.5-flash-lite`. This could not be independently verified against known Gemini model naming (1.5 → 2.0 → 2.5 is the lineage as of this assistant's knowledge cutoff) — worth confirming this model id actually exists and is billed/available before relying on it. If wrong, failures are silent (bad responses become `null`, degrading to "no answer," not a crash) — no test file catches a typo here.

**Orphaned (zero production callers, only referenced by their own test)**:
- `Evidence.kt` (8 ln) — 4 status constants nothing emits or checks.
- `Reminders.kt` (30 ln) — superseded by the now-live `AdaptationEngine`/`NotificationDecisionEngine`/`NotificationWorker` hourly design.
- `SkinNoteParser.kt` (26 ln) — fully built and tested Gemini-note parser, but no `GeminiClient` method produces skin notes in the format it expects, and nothing calls it.
- `Skin.kt` (12 ln) — only consumer is the orphaned `SkinNoteParser.kt`; **all 4 `Habit.why` fields are the literal placeholder string `"..."`**, never filled in.
- `Insights.kt` (5 ln) — real but trivial (one constant); not orphaned, just thin.

## 7. Data model (Room, `data/`)

13 entities, DB version 2 (hand-written `MIGRATION_1_2`, not destructive, deliberately — added `day_row.steps` and the `lab_result` table):

`profile` (singleton row, id=1), `log_entry`, `verdict`, `round`, `meal`, `food`, `weight`, `day_row`, `check_entity`, `chat_message`, `action_outcome`, `face_capture`, `lab_result`.

**Dead schema, verified by full-tree grep**:
- `check_entity` (PHQ-9/GAD-7 scoring) — table exists, DAO exists, **nothing anywhere constructs a `CheckEntity` or calls its DAO**. Entirely unused.
- `round` table — read via `getAllSync()` in two places, but **never written** — always empty in practice, making `Round.stats` an unused field.
- Several DAO methods defined with zero callers: `LabResultDao.getByMarkerSync`, `MealDao.delete`, `LabResultDao.delete`, `ActionOutcomeDao.getLastEventSync`, `FaceCaptureDao.deleteAll`.
- **Retention caps (`enforceCap()`) are only actually called for 4 of 9 tables that define one** (`weight`, `day_row`, `face_capture`, `meal`). `log_entry`, `verdict`, `chat_message`, `action_outcome` have a cap method that's simply never invoked — those tables grow unbounded.

`SettingsManager.kt` is a generic DataStore string key/value wrapper — no fixed schema of its own; actual setting names live at call sites.

## 8. Build config

- `applicationId = "com.aistudio.trainer.qmwzrt"`, `namespace = "com.example"`, `minSdk 26` / `targetSdk 36` / `compileSdk 36.1`.
- AGP `9.1.1`, Kotlin `2.2.10`, Gradle wrapper `9.3.1`.
- Key dependencies: Compose BOM `2024.09.00`, CameraX `1.5.0`, MediaPipe `tasks-vision 0.10.14`, Room `2.7.0`, Retrofit/OkHttp/Moshi (`2.12.0`/`4.10.0`/`1.15.2`), Health Connect `1.1.0`, Firebase BOM `34.15.0` (mostly declared but commented out except `firebase-ai`/`firebase-appcheck-recaptcha`), WorkManager `2.9.0`. ML Kit face-detection was **removed** (replaced by MediaPipe); ML Kit pose-detection (`17.0.0`, both standard + accurate) remains for `LiveSessionScreen`.
- Assets: `app/src/main/assets/face_landmarker.task` — 3,758,596 bytes (3.58 MiB), packaged uncompressed via `noCompress`.
- Manifest permissions: `CAMERA`, `INTERNET`, `POST_NOTIFICATIONS`, and read-only Health Connect (`READ_SLEEP`/`READ_WEIGHT`/`READ_STEPS` — no `WRITE_*`, matching the stated design). `NotificationActionReceiver` confirmed registered, not exported.
- Real, unfixed local build friction: `debugConfig` signing block, `.env.example`, and daemon-JVM toolchain pinning were all found and fixed earlier this session — see `origin/main` commit history for specifics.

## 9. Test coverage

27 test files, **270 `@Test` functions**, 2,759 lines, JUnit4 + Robolectric/Roborazzi. Broad, deliberately refusal-focused coverage (`NutritionFatLossTest` and `FaceQualityTest` in particular test that the engine correctly *declines* to speak/pass on bad data, not just its happy path). Gaps worth noting: `HealthCoachEngine.kt` (the most-wired file in the app) has no dedicated test file; `MoodInsights.kt`'s non-trivial midnight-wraparound math is only indirectly covered.

## 10. Priority list — real issues to address (synthesized across all findings)

1. **Skin habit rationale is a placeholder** (`Skin.kt`) — 4/4 `why` fields are literally `"..."`. Low effort, real user-facing gap if `SkinNoteParser` is ever wired up.
2. **Verify the Gemini model id** (`gemini-3.5-flash-lite` in `GeminiClient.kt`) actually exists before relying on it in production — currently unverifiable and untested.
3. **`HealthCoachEngine.kt` needs a real test file** — it's the hub of the notification system and the least-tested file of its importance.
4. **Unbounded tables**: `log_entry`, `verdict`, `chat_message`, `action_outcome` have retention-cap logic that's never invoked. Either wire up their `enforceCap()` calls or remove the dead cap methods.
5. **Dead code cleanup candidates**: `Evidence.kt`, `Reminders.kt`, `SkinNoteParser.kt` (unless being wired up next), `BoxingScreen.kt`, `FaceScreen.kt`, `SkinScreen()`/dead parts of `MindScreen.kt`, `check_entity`/`round` tables.
6. **`HormonalScreen` is fully built and tested but unreachable** — either wire a nav entry point for it (it's arguably the most complete unfinished feature in the app) or intentionally retire it.
7. ~~**This entire ~80-file body of work is uncommitted**~~ — **RESOLVED 2026-08-21.** Committed and pushed; both copies synced at `6b88cba`.
8. ~~**Nothing here has been compiled**~~ — **RESOLVED 2026-08-21.** `./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`, 350 tests / 0 failures. See section 11.

Items 1–6 above were written 2026-08-20 and have **not** been re-verified against the current tree; several were addressed during 2026-08-20/21 work (item 3 in particular — `HealthCoachEngineTest.kt` now exists, and item 4's `enforceCap()` calls are wired in `TodayViewModel.refresh()`). Treat 1–6 as a 2026-08-20 snapshot needing a re-audit, not as current.

---

## 11. Closed-loop adaptive coach (2026-08-21)

The app could already observe, remember and explain. It could not **decide**. Training came from `Planner`'s fixed Mon/Wed/Fri split regardless of what had actually been trained; there was no cardio concept at all; readiness had no access to how the person actually felt.

**Verification: `./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`, 350 tests, 0 failures, 0 errors.** Confirmed by reading `app/build/test-results/testDebugUnitTest/*.xml` directly. `compileDebugKotlin` clean, zero warnings.

### Status by component

| Component | Status | Notes |
|---|---|---|
| `TrainingHistory.kt` | **COMPLETE** | Pattern recency, consecutive-day streaks, neglect detection, per-day difficulty averaging. 13 tests. |
| `Cardio.kt` | **COMPLETE** | Modes ordered by real recovery cost; persisted in the existing `DayRow.plans` JSON column (no new table). Base/volume/gap reads. 12 tests. |
| `ReadinessEngine.kt` | **COMPLETE** | 6 categories + own confidence + real named factors. Never a percentage. 11 tests. |
| `DailyDecisionEngine.kt` | **COMPLETE** | Candidate generation with hard safety floors. 24 tests. |
| `PersonalState.kt` | **COMPLETE** | Single assembler; `TodayViewModel` and `NotificationWorker` both consume it. |
| `NextActionEngine.kt` | **COMPLETE** | "What should I do right now", time-of-day aware. |
| Today's Mission UI | **COMPLETE** | Readiness + confidence + training/cardio decisions + expandable "Why?". |
| Check-in / cardio / difficulty capture | **COMPLETE** | `CheckInSheet`, `CardioSheet`, optional difficulty on manual workout logging. |
| Schema v3 migration | **COMPLETE** | Real `MIGRATION_2_3`; no destructive fallback. |
| **On-device verification** | **PARTIAL** | Installs and launches clean on the real device — see "On-device status" below. Migration proven not to throw; resulting schema not yet inspected. |
| Gemini wording layer for these decisions | **NOT STARTED** | Decisions are surfaced as engine-authored text. The `Digest`/`Claims`/`Validate`/`Explain` layer is not yet wired to them. |

### Safety floors, each covered by a test

- Very low readiness → **rest**, regardless of what is neglected.
- Intervals never appear below good readiness, **nor on a beginner/unknown aerobic base**. Asserted exhaustively across every readiness level.
- Severe soreness (≥7/10) caps cardio at an easy walk.
- A leg-focused session caps cardio to easy movement rather than stacking recovery cost.
- Progression requires **both** real readiness **and** no recent too-hard feedback — either alone is insufficient.
- A pattern trained within 2 days is never re-prescribed as today's focus.

### On-device status (2026-08-21, Motorola Edge 60 Pro `ZA222ZXDFJ`)

**Verified:**
- `./gradlew installDebug` → `Installed on 1 device`, `BUILD SUCCESSFUL`.
- App launched via `monkey`; process alive afterward (pid 4570).
- `logcat` clean — no `FATAL`, no `AndroidRuntime` crash, no Room/SQLite/migration exception.

**What that does and does not prove.** It proves `MIGRATION_2_3` **ran without throwing against the real populated database** — the strongest single signal available, since Room throws `IllegalStateException` on first DB access if a migration is missing or fails, which would have killed the process. It does **not** prove the resulting schema is correct in detail: the phone was disconnected before the column list could be read, so it is still unconfirmed that all five new columns (`log_entry.difficulty`, `day_row.energy`/`soreness`/`stress`/`refreshed`) landed with the right types and nullability.

**To finish this check** (device connected; `sqlite3` is not present on this device, so the DB must be pulled):

```bash
PKG=com.aistudio.trainer.qmwzrt
adb exec-out run-as $PKG cat /data/data/$PKG/databases/gym_trainer_database > db.sqlite
python -c "import sqlite3;c=sqlite3.connect('db.sqlite');print(c.execute('PRAGMA user_version').fetchone());[print(r) for r in c.execute('PRAGMA table_info(day_row)')]"
```

Expect `user_version` = 3.

**Not yet exercised on device at all:** the new UI paths — Today's Mission card, `CheckInSheet`, `CardioSheet`, the "Why?" expansion, and the difficulty prompt on manual workout logging. Installed and reachable, but never tapped.

### Schema change (v2 → v3)

`log_entry.difficulty`, and `day_row.energy` / `soreness` / `stress` / `refreshed`. All nullable, **no `DEFAULT`** — deliberately, so every pre-existing row reads as "not answered" rather than acquiring a neutral score the person never gave. `ReadinessEngine` distinguishes absent from average, so a default would have manufactured evidence for every historical day.

### Known limitations — stated plainly

1. **Partially verified on device.** Installs, launches, and survives — so the v3 migration demonstrably ran against the real populated database without throwing. Still unverified: the resulting column list, and every new UI surface (nothing has been tapped).
2. **Cold start is honest but thin.** `difficulty` and the check-in fields did not exist before 2026-08-21, so no historical row has them. Expect `LOW`/`INSUFFICIENT` confidence and conservative defaults until several days of real check-ins accumulate. This is correct behaviour, not a bug.
3. **Cardio base bands are unvalidated thresholds.** The 50 / 120 aerobic-min-per-week boundaries between BEGINNER/DEVELOPING/ESTABLISHED are reasonable coaching heuristics, not fitted to this person's data or to a cited source.
4. **`Planner` still generates the actual exercise list.** `DailyDecisionEngine` decides the movement-pattern *focus*, but the concrete lifts still come from `Planner`'s split, which is not yet pattern-aware. The focus decision is therefore advisory over the session content — the largest remaining gap in this section.
5. **No `LiveSessionScreen` difficulty prompt.** Only manual logging captures difficulty; camera sessions leave it null.
6. **Rest-day cardio wording.** A `daysSince >= 7` gap reason can still be appended when the mode was capped to something lighter, so the stated reason and the prescribed mode can read slightly out of step.
