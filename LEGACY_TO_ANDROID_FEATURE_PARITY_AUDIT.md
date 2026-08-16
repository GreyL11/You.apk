# 1. Executive Verdict

MAJOR PARITY GAPS

The core Health Coach intelligence engine, Room storage, and Onboarding experience have successfully transitioned to Android. However, almost every vertical domain the coach relies on (Training, Nutrition, Face/Skin Intelligence, and Gemini integration) is either heavily stubbed, fake, or entirely missing. The Android app currently looks like a feature-complete application but functions internally as a wireframe for the majority of the legacy product's capabilities.

# 2. Complete Feature Matrix

| Legacy Feature | Legacy Evidence | Android Equivalent | Android Evidence | Status | Gap |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **APP / PRODUCT EXPERIENCE** | | | | | |
| Health Coach Action Engine | `health.js` | `HealthCoachEngine.kt` | `HealthCoachEngine.kt` | PRESENT_EQUIVALENT | None. Candidate logic ported perfectly. |
| Zero / Absent Data Handling | `evidence.js`, `health.js` | `HealthCoachEngine.kt` | `HealthCoachEngine.kt` (lines 38-51) | PRESENT_EQUIVALENT | None. |
| First-Run / Onboarding | N/A | Onboarding Flow | `OnboardingScreen.kt` | PRESENT_IMPROVED | Better structured baseline data collection flow. |
| Action Execution State Machine | `health.js` | Outcome Database | `TodayScreen.kt`, `ActionOutcome` | PRESENT_EQUIVALENT | Offered/Started/Completed/Skipped recorded well. |
| Weekly Review | `app.js` (Progress) | Weekly Review Screen | `WeeklyReviewScreen.kt` | PARTIAL | Android lacks deep trend charting, relies on static text blocks. |
| **TRAINING** | | | | | |
| Exercises & Thresholds | `exercises.js` | `Exercises.kt` | `Exercises.kt` | PARTIAL | Android only ported constants, lacks actual joint threshold configurations. |
| Live Rep Counting / Posture | `pose.js` | `LiveSessionScreen.kt` | `LiveSessionScreen.kt` | MISSING | Android screen is a raw CameraX preview; no MediaPipe Pose pipeline. |
| Progression Arithmetic | `coach.js` | `Coach.kt` | `Coach.kt` | MISSING | `Coach.kt` has 3 constants. No Epley 1RM, no stall detection, no hold/increase decisions. |
| Workout Logging | `app.js` | `TodayViewModel.kt` | `TodayViewModel.logTraining` | FAKE | Android simply inserts a hardcoded dummy log with 0 load and 1 rep when user taps "Log". |
| **FACE / APPEARANCE** | | | | | |
| Face Geometry & Mesh | `face/geometry.js` | N/A | N/A | EXPERIMENTAL_UNVALIDATED | Missing entirely in Android. |
| Image Segmentation (Veto) | `face/mask.js` | N/A | N/A | EXPERIMENTAL_UNVALIDATED | Missing entirely. |
| Skin Feature Extraction | `face/features.js` | N/A | N/A | EXPERIMENTAL_UNVALIDATED | Missing entirely. |
| Validation Protocols (A-F) | `face/protocol.js` | N/A | N/A | EXPERIMENTAL_UNVALIDATED | Missing entirely. |
| **SKINCARE** | | | | | |
| Habit Logging | `skin.js` | `Sheets.kt` | `SkinSheet` | PRESENT_DIFFERENT | Android uses checkboxes instead of chips. |
| Adherence & Associations | `skin.js` | N/A | N/A | MISSING | Android lacks the logic to correlate dairy/sugar/stress to skin scores. |
| **HYDRATION / NUTRITION** | | | | | |
| Hydration Logging | `nutrition.js` | `Sheets.kt` | `HydrationSheet` | PRESENT_EQUIVALENT | Basic fluid tracking is intact. |
| Nutrition DB & Macros | `nutrition.js` | `Nutrition.kt` | `Nutrition.kt` | MISSING | Android lacks `FOODS` table, calorie targets, macro rollups. |
| 28-Day Weight Trend | `nutrition.js` | N/A | N/A | MISSING | No `suggestion()` engine or scale variance logic. |
| **SLEEP / RECOVERY / MIND** | | | | | |
| Chat Check-In | `mood.js`, `chat.js` | `TalkScreen.kt` | `TalkScreen.kt` | PARTIAL | UI exists but lacks daily 1-5 discrete mood scoring. |
| Questionnaires (PHQ-9) | `checks.js` | N/A | N/A | MISSING | Questionnaires were not ported. |
| **HORMONAL LIFESTYLE** | | | | | |
| Factor Tracking & Boundaries | `t_inputs.js` | `TInputs.kt` | `TInputs.kt` | PARTIAL | Boundaries ported, but data rollups (e.g. median wake times) are stubbed. |
| **GEMINI / AI** | | | | | |
| Streaming Responses | `chat.js` (SSE) | `GeminiClient.kt` | `GeminiClient.kt` | MISSING | Android uses blocking REST calls. |
| Structured Output (Schema) | `chat.js`, `explain.js` | `Explain.kt`, `SkinNoteParser` | `SkinNoteParser.kt` | MISSING | Android fakes JSON schemas by searching for string substrings (e.g. "spf: true"). |
| Explain Validation Retry | `validate.js`, `explain.js`| `Explain.kt`, `Validate.kt` | `Explain.kt` | PARTIAL | Retry loop exists, but response parsing is mocked (`return fallbackExplanation()`). |
| **REMINDERS / NOTIFICATIONS**| | | | | |
| Background Scheduling | `notify.js` (Capacitor) | `NotificationWorker` | `NotificationWorker.kt` | PRESENT_IMPROVED | WorkManager handles native background execution well. |
| Adaptive Timing | `health.js` (preferredHour)| `NotificationDecisionEngine` | `NotificationDecisionEngine.kt` | MISSING | Android lacks historical hour bucketing; uses fixed cooldowns. |
| **DATA / STORAGE** | | | | | |
| Persistence Engine | `store.js` (localStorage) | `AppDatabase.kt` (Room) | `AppDatabase.kt` | PRESENT_IMPROVED | Relational DB is a significant architecture upgrade. |

# 3. Missing Features

**CRITICAL**
- **MediaPipe Pose Training Pipeline**: The camera functionality is currently a raw preview. Rep counting, joint threshold evaluation, and real-time form cueing are entirely absent.
- **Progression Intelligence**: `Coach.kt` contains constants instead of the legacy Epley arithmetic, stall detection, and load incrementing logic.
- **Gemini Structured Output**: The app attempts to regex strings instead of forcing Gemini to use `responseSchema`. This fundamentally breaks AI safety boundaries and evidence-verification pipelines.
- **Nutrition/Food Tracking**: The food database and 28-day macro/calorie tracking engine are missing.

**HIGH**
- **Face/Skin Validation Collection**: The empirical validation protocols (A-F) and segmentation pipeline are completely unported.
- **Explain Claim Validation**: `Explain.kt` mocks the response rather than reassembling valid claims from the model.

**MEDIUM**
- **PHQ-9 / GAD-7 Questionnaires**: The fortnightly mental health screeners were not migrated.
- **Skincare Association Engine**: Correlating habits with skin changes is missing.
- **Adaptive Reminder Timing**: Notifications do not bucket preferred execution hours.

**LOW**
- **Discrete Mood Logging**: Daily 1-5 scoring buttons are missing from the `TalkScreen`.

# 4. Partial Features

- **Weekly Review**: In Android, the weekly review relies on basic text strings and lacks the interactive sparkline charts and trend analysis present in the legacy app's Stats screen.
- **Hormonal Lifestyle (TInputs)**: Android contains the data structures and boundary strings but lacks the robust time-series calculations (e.g., median wake time spread, 4-week moving averages) that power the actual advice.

# 5. Features Improved in Android

- **Data Persistence**: Moving from raw `localStorage` JSON blobs to structured SQLite (Room) gives the app much needed relational integrity and type safety.
- **Background Operations**: Replacing Capacitor's limited notification plugin with Jetpack `WorkManager` provides reliable, OS-native background task execution.
- **Onboarding Experience**: The newly implemented first-run state cleanly maps user preferences directly into the deterministic coach.

# 6. Features Intentionally Excluded

- **Legacy DOM Manipulation**: Direct DOM generation (`node('div', ...)` from JS) was intentionally dropped in favor of declarative Jetpack Compose UI.
- **Web-based SpeechSynthesis**: Replaced by standard Android TTS / visual cues.
- **Local Storage File Exports**: Handled inherently by standard Android app data backups.

# 7. Experimental / Unvalidated Features

**Face Intelligence / Appearance Logging**
This feature is heavily flagged in the legacy codebase as `UNVALIDATED` and experimental. The legacy app was essentially functioning as a validation data-collector for Protocol A-F. In Android, the entire stack (segmenter, piecewise-affine registration, optical density sampling) has been dropped, replaced by a `FaceScreen.kt` CameraX placeholder that explicitly prints "UNVALIDATED". This feature is not production-ready in either codebase.

# 8. Dead / Fake / Unwired Functionality

The Android app contains several dangerous "fake" implementations that look complete to the user but do nothing:
1. **`LiveSessionScreen.kt`**: Displays the camera but runs no tracking.
2. **`TodayViewModel.logTraining()`**: Clicking "Log Workout" inserts a `LogEntry` with 1 rep and 0 load, completely bypassing any actual session building.
3. **`Explain.kt`**: The AI explanation fallback is hardcoded to return a success string rather than actually performing the 1-retry verification loop.
4. **`SkinNoteParser.kt`**: Instead of schema enforcement, it blindly checks if the AI's string contains "spf: true", which is extremely fragile.

# 9. Data Model Parity

The underlying concepts survived the migration nicely. `DayRow`, `ActionOutcome`, `LogEntry`, and `Meal` entities exist in Room. However, the legacy `faultEvents` array (which tracks exactly which rep caused a fault) is stored as a raw JSON string in Android. This works but requires careful parsing to match legacy's granular movement intelligence.

# 10. Intelligence Parity

- **Deterministic Coach**: Excellent parity. The tiering system and evidence logic migrated successfully.
- **Gemini Safety / Grounding**: Poor parity. The legacy app's strict structured `responseMimeType` enforcement to prevent hallucination is missing, relying instead on blocking REST calls and crude string parsing.

# 11. User Experience Parity

- **First Install -> Setup**: Android wins. The onboarding flow feels like a polished consumer product.
- **Daily Use (Action Execution)**: Android is equal for simple actions (Hydration, Sleep).
- **Training**: Android is severely degraded. The guided workout experience is a dead end.
- **Long-term Use (Trends)**: Android is degraded. The lack of visual charting and deep historical progression tracking hurts the retention loop.

# 12. Verification Results

- **Code Audited**: Mapped legacy JS files (`www/*.js`) against Android Kotlin files (`app/src/main/java/com/example/**`).
- **Build Verified**: The Android app compiles successfully.
- **Flows Traced**: Inspected `LiveSessionScreen.kt`, `Coach.kt`, `SkinNoteParser.kt`, `Explain.kt`, and `GeminiClient.kt` via shell commands.
- **Unverified on Real Device**: Because the internal wiring is mostly stubbed, attempting a real-device training session or skin check-in will immediately reveal the "fake" endpoints.

# 13. Final Migration Checklist

- [x] Health Coach Deterministic Engine
- [x] Room Database & Persistence
- [x] Onboarding & First-Run States
- [x] Background Notifications (Basic)
- [ ] Training Progression Math
- [ ] MediaPipe Pose Camera Tracking
- [ ] Gemini Streaming & Structured Output
- [ ] Nutrition & Food Database
- [ ] Charting & Weekly Trends
- [ ] Face/Skin Validation Protocols

# 14. FINAL ANSWER

**Can the legacy app now be considered fully migrated to Android?**

**NO.**

The migration has successfully ported the UI layer, the database architecture, and the high-level `HealthCoachEngine` orchestrator. However, the *actual health features* that the coach relies on to do its job—Training logic, MediaPipe pose tracking, Nutrition databases, and Gemini structured-output safety boundaries—are currently just stubs or wireframes in the Android codebase. Deleting the legacy app now would mean permanently losing the core progression math, the computer vision pipeline, and the AI hallucination protections.
