# KhelLab — Project Context for Claude Code

## What this is
An Android app (Java) for Mobile Application Development (702AI0E002), a college mini-project.
Student: Lokesh Magare, Roll No B292, Class B.Tech C.E. A, Batch A2.

## Problem
Talent identification in Indian sport depends on standardised physical tests (vertical jump,
sprint time, reaction time) usually measured with equipment (jump mats, timing gates) that
small-town schools and academies don't have. A 2023 Parliamentary Standing Committee review of
Khelo India found 544 of 1,524 sanctioned coaching posts unfilled, and noted schools/colleges
generally lack sports infrastructure. Promising athletes outside big academies often go untested,
and results that are recorded stay on paper.

## Solution
KhelLab turns a phone into a testing station. A coach records an athlete on a tripod; the app
uses on-device pose estimation to measure:
- Vertical jump height (from flight time, via pose-tracked ankle/hip position)
- Sprint split times (from video, over a marked distance)
- Reaction time / simple agility
- Basic posture/landing form — shown ONLY as a coaching hint, NEVER as a medical/injury claim

Results are stored locally (SQLite), compared to age/gender benchmarks, exportable as CSV, and
synced to Firebase when online so a whole cohort's results can be reviewed together. Test venues
are tagged on Google Maps.

## Non-negotiable constraints
- **Language: Java only.** Not Kotlin. The student's coursework and lab journal are all Java;
  keep this project in Java for consistency and so they can explain it at viva.
- **Style: plain and explainable, not over-engineered.** Use straightforward Activities with
  `findViewById`, explicit `SQLiteOpenHelper` (not Room, unless asked), explicit listeners/lambdas
  — the kind of code a 3rd-year student can read and explain line by line in a viva. Avoid heavy
  architecture (no DI frameworks, no complex MVVM) unless explicitly requested.
- **Minimum SDK 24.** Package: `com.khellab.app`.
- **No paid infrastructure.** Firebase Spark (free) plan only.
- **Explain, don't just dump code.** After making changes, briefly explain what changed and why,
  since the student needs to understand this well enough to defend it at a viva.
- **Never claim medical/diagnostic accuracy.** All fitness outputs are coaching aids.

## Target folder structure
```
KhelLab/
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/khellab/app/
        │   ├── MainActivity.java
        │   ├── ui/PoseOverlayView.java
        │   ├── data/DBHelper.java              (Milestone 3+)
        │   └── model/TestResult.java           (Milestone 3+)
        └── res/layout/activity_main.xml
```

## Tech stack (maps to course syllabus)
- CameraX — camera preview + frame analysis
- ML Kit Pose Detection — on-device pose estimation (the "advanced" element)
- SQLite (plain `SQLiteOpenHelper`) — local results storage
- File handling — CSV export of results
- Firebase (Firestore + Auth, Spark/free plan) — cohort sync
- Google Maps / Location — tagging test venues
- Flutter (optional, later) — secondary coach dashboard

## Status: Milestones 1–6 are implemented and the project builds (`gradlew assembleDebug`)
See `PROJECT_GUIDE.md` for the full file-by-file explanation, demo script and viva notes.
- Build: Groovy `build.gradle`, AGP 9.3.3, Gradle 9.5.0, compile/target SDK 37, min SDK 24, Java 11.
  Room was removed (plain SQLiteOpenHelper is used). Firebase plugin is applied only if
  `app/google-services.json` exists; Maps key comes from `MAPS_API_KEY` in `local.properties`.
- Launcher is `HomeActivity`; `MainActivity` is the vertical-jump camera screen (Milestone 1 code extended).
- M2 `analysis/JumpDetector` (hip-based state machine, h = g·t²/8) + Manual flight-time entry
- M3 `data/DBHelper`, `model/TestResult`, `ResultsActivity` + `ui/ResultAdapter`
- M4 `data/CsvExporter` + FileProvider share (full or anonymised)
- M5 `data/FirebaseSync` (anonymous auth + Firestore WriteBatch, offline-first)
- M6 `VenueMapActivity` + GPS tagging on Home (Fused Location)
- Extras from the report: `SprintTestActivity`, `ReactionTestActivity`, `analysis/BenchmarkHelper`,
  `analysis/LandingFormCheck` (coaching hint only)
- Verified on the Pixel_7a emulator (API 37): Home form, GPS tag, camera + ML Kit running, manual jump
  → 28.3 cm saved, reaction test, sprint timer, results list.

## Remaining (needs the student)
1. Connect Firebase (Tools > Firebase) + enable Anonymous auth + create Firestore DB.
2. Optional Maps API key in `local.properties`.
3. Real-athlete accuracy evaluation; signed release build (Build > Generate Signed App Bundle/APK).

## Working agreement
- After each milestone, run a Gradle build (`./gradlew assembleDebug`) and fix any errors before
  telling the student it's ready to test on their phone.
- Keep changes scoped to one milestone at a time — don't jump ahead.
- If something needs a decision only the student can make (e.g. connecting Firebase, granting
  permissions on first run), stop and tell them exactly what to do, rather than guessing.
