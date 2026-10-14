# KhelLab — Project Guide (for demo & viva)

Lokesh Magare · Roll No B292 · B.Tech C.E. A · Batch A2 · Mobile Application Development

KhelLab turns an Android phone into an athlete fitness-testing station: vertical jump (camera + on-device
pose estimation), sprint timer, reaction time. Results are rated against age/gender benchmarks, stored in
SQLite, exported to CSV, synced to Firebase, and tagged with the venue's GPS location.

**Language: 100% Java** (no Kotlin files). Min SDK 24, target/compile SDK 37, package `com.khellab.app`.

---

## 1. How to open and run

1. Android Studio → **File → Open** → select the `KhelLab` folder (the one containing `settings.gradle`).
2. Wait for **Gradle sync** to finish (first time needs internet to download libraries).
3. Pick the **Pixel_7a** emulator (or a real phone) in the device dropdown → click **Run ▶**.
4. The app opens on the **Home** screen.

Command-line build (optional): `gradlew assembleDebug` → APK at `app/build/outputs/apk/debug/app-debug.apk`.

### Emulator tips
| Feature | What to do on the emulator |
|---|---|
| Camera permission | Tap **Allow** the first time the jump test opens |
| Camera picture | Back camera shows a virtual 3D room (no person → no skeleton). To see the skeleton live on yourself: AVD Manager → Edit Pixel_7a → **Show Advanced Settings → Camera → Back: Webcam0**, or tap **Switch camera** in the app if the front camera is set to Webcam0 |
| Jump test without a person | Tap **Manual** and type a flight time, e.g. `480` ms → 28.3 cm |
| GPS | Emulator **⋯ (Extended controls) → Location** → set a point → **Set location**, then tap **Tag GPS** in the app |
| See the CSV file | **View → Tool Windows → Device Explorer** → `/sdcard/Android/data/com.khellab.app/files/exports/` |
| See the database | **View → Tool Windows → App Inspection → Database Inspector** → `khellab.db` → `results` table |

### Suggested 5-minute demo for faculty
1. Home: enter athlete *Rahul Patil, 16, Male*, venue *ZP School Ground Nashik*, tap **Tag GPS**.
2. **Vertical Jump**: show the live camera; explain pose detection; tap **Start** (status shows it is looking
   for the athlete); tap **Manual** → `480` → **Calculate** → shows *28.3 cm (Needs work)* → **Save**.
3. **Sprint Timer**: Start → Split → Stop → shows time, speed and rating → **Save result**.
4. **Reaction Time**: 5 trials → average and rating → **Save result**.
5. **Results**: show the list; tap one for details; **Export CSV** → choose *Anonymised* → share sheet;
   show the file in Device Explorer; show the table in Database Inspector.
6. **Venue map**: show the tagged venue; tap it → opens in Google Maps.
7. **Sync to cloud**: explains Firebase setup (or uploads, if you connected Firebase — see §6).

---

## 2. Project structure

```
KhelLab/
├── settings.gradle            project name + where libraries are downloaded from
├── build.gradle               declares the Android & Google-services Gradle plugins
├── gradle.properties          Gradle memory, AndroidX switch
├── local.properties           SDK path + optional MAPS_API_KEY (machine-specific)
└── app/
    ├── build.gradle           SDK versions, dependencies (CameraX, ML Kit, Firebase, Maps)
    └── src/main/
        ├── AndroidManifest.xml          permissions, activities, FileProvider, Maps key
        ├── java/com/khellab/app/
        │   ├── HomeActivity.java         launcher: athlete + venue form, GPS tagging, menu
        │   ├── MainActivity.java         vertical jump test (camera + pose + jump detection)
        │   ├── SprintTestActivity.java   stopwatch with splits
        │   ├── ReactionTestActivity.java 5-trial tap reaction test
        │   ├── ResultsActivity.java      list, details, delete, CSV export, Firebase sync
        │   ├── VenueMapActivity.java     Google Map / list of tagged venues
        │   ├── analysis/
        │   │   ├── JumpDetector.java     state machine: calibrate → take-off → landing → height
        │   │   ├── BenchmarkHelper.java  age/gender rating tables
        │   │   └── LandingFormCheck.java knee-alignment coaching hint
        │   ├── data/
        │   │   ├── DBHelper.java         SQLiteOpenHelper: create table, insert, query, update, delete
        │   │   ├── SessionPrefs.java     SharedPreferences: current athlete & venue
        │   │   ├── CsvExporter.java      writes results to a .csv file
        │   │   └── FirebaseSync.java     uploads unsynced results to Firestore
        │   ├── model/
        │   │   └── TestResult.java       one result (plain Java object)
        │   └── ui/
        │       ├── PoseOverlayView.java  custom View: draws skeleton on the camera
        │       ├── ResultAdapter.java    ArrayAdapter for the results ListView
        │       └── UiUtils.java          edge-to-edge padding, rating colours
        └── res/
            ├── layout/   activity_home, activity_main, activity_sprint, activity_reaction,
            │             activity_results, item_result, activity_venue_map
            ├── values/   strings, colors, themes (+ reusable styles)
            ├── xml/      file_paths (FileProvider), backup rules
            └── drawable/ + mipmap-*/   app icon (jumping athlete)
```

---

## 3. Screen flow

```
HomeActivity ──► MainActivity (Vertical Jump)  ─┐
     │      ──► SprintTestActivity             ─┼─► save ─► SQLite (DBHelper)
     │      ──► ReactionTestActivity           ─┘                │
     ├────────► ResultsActivity ── Export CSV ─► file + share    │
     │                         └── Sync ───────► Firebase Firestore
     └────────► VenueMapActivity (reads venues from SQLite)
```
Screens pass no data through Intents: the current athlete/venue is stored in **SharedPreferences**
(`SessionPrefs`) and every test screen reads it from there.

---

## 4. File-by-file explanation

### HomeActivity.java (launcher)
- `onCreate()` → `findViewById` for every field, loads the last athlete/venue from `SessionPrefs`, sets click listeners.
- `openTest(Class)` → **validation** (name not empty, age 5–80), saves to `SessionPrefs`, `startActivity(new Intent(...))`.
- **Location**: `ActivityResultContracts.RequestMultiplePermissions` asks for FINE + COARSE location.
  `FusedLocationProviderClient.getCurrentLocation()` gets the GPS position (falls back to `getLastLocation()`).
- `onResume()` refreshes "N results saved (M not yet synced)" every time you return to Home.

### MainActivity.java (vertical jump) — Milestone 1 + 2
- **Camera (CameraX)**: `ProcessCameraProvider` binds two *use cases* to the activity lifecycle:
  `Preview` (what you see) and `ImageAnalysis` (frames for ML Kit). `STRATEGY_KEEP_ONLY_LATEST` drops
  frames if ML Kit is busy so we never lag. `bindToLifecycle` means the camera opens/closes automatically
  with the screen.
- **Pose detection (ML Kit)**: `PoseDetectorOptions.STREAM_MODE` is for video. Each frame becomes an
  `InputImage` with its rotation; `poseDetector.process(image)` returns a `Pose` with 33 landmarks
  (nose, shoulders, hips, knees, ankles...). Runs **on-device**, no internet.
- `imageProxy.close()` in `addOnCompleteListener` — **must** be called, otherwise CameraX stops sending frames.
- **Rotation**: the sensor is sideways, so for 90°/270° the width and height are swapped before drawing.
- `onPoseDetected()`: takes the **mid-hip y** and the **torso length** (mid-shoulder to mid-hip) and feeds
  `JumpDetector.onFrame()` with the camera **timestamp** (nanoseconds, more accurate than the system clock).
- **Manual** button: an `AlertDialog` with an `EditText` → flight time in ms → height. Used with a
  slow-motion video, or on the emulator.
- **Save**: builds a `TestResult`, rating from `BenchmarkHelper`, inserts with `DBHelper`. Save is then
  disabled so the same jump is not saved twice.

### analysis/JumpDetector.java — the physics (Milestone 2)
A **state machine**: `IDLE → CALIBRATING → READY → IN_AIR → DONE`
1. **CALIBRATING**: average hip y over 20 frames = *baseline* (standing hip height).
   Threshold = 15% of the torso length (so it works near or far from the camera).
2. **READY**: hip rises above `baseline − threshold` → **take-off time** recorded.
3. **IN_AIR**: hip comes back down to that level → **landing time**.
   Flight < 0.10 s → noise, ignored. > 1.2 s → not a real jump, reset.
4. **DONE**: `t = landing − take-off`, **h = g·t²/8**.

**Why g·t²/8?** The body goes up for t/2 and comes down for t/2. Falling from rest for time t/2:
h = ½·g·(t/2)² = **g·t²/8**. Example: t = 0.48 s → 9.81 × 0.2304 / 8 = 0.283 m = **28.3 cm**.

Remember image **y grows downwards**, so "hip goes up" = y gets *smaller*.

### analysis/BenchmarkHelper.java
Rating = Excellent / Good / Average / Needs work. Jump: higher is better (cut-offs × 0.70 for under 14,
× 0.85 for 14–17, lower for females). Sprint (20 m): lower is better (+0.3 s female, +0.5 s under 14,
+0.2 s 14–17). Reaction: lower is better. **These are indicative coaching values, not official SAI norms.**

### analysis/LandingFormCheck.java
For 10 frames after landing it compares *knee gap ÷ ankle gap*. Below 0.75 → hint "knees moved inwards,
cue knees over toes". Shown only as a **coaching hint, never a medical/injury claim**. Needs a front view.

### ui/PoseOverlayView.java — custom View
- Extends `View`, overrides `onDraw(Canvas)`. The 2-argument constructor `(Context, AttributeSet)` is what
  Android uses to inflate it from XML.
- `updatePose()` stores the pose and calls `postInvalidate()` → Android calls `onDraw()` again.
- Draws bones (white lines), joints (green dots), the tracked hip (yellow), and the baseline/threshold
  lines (cyan) after calibration.
- Uses the same **fill-centre** scaling as `PreviewView` (`scale = max(viewW/imgW, viewH/imgH)` + centring
  offset) so the dots sit on the body; mirrors x for the front camera.

### SprintTestActivity.java
Stopwatch using `SystemClock.elapsedRealtime()` (not affected by clock changes). A `Handler` +
`Runnable` re-runs every 30 ms to refresh the timer text (`postDelayed`). Split button stores
intermediate times. Speed = distance / time. Benchmarks only for 20 m. `onDestroy` removes callbacks
(prevents leaks). Timing is manual (coach taps); automatic video line-crossing is future work.

### ReactionTestActivity.java
States IDLE (grey) → WAITING (red, random 1.5–4 s via `handler.postDelayed`) → GO (green, clock starts)
→ tap = reaction time. Tapping on red = **false start** (callback cancelled, trial repeated). 5 trials averaged.

### model/TestResult.java
Plain Java object (fields + constructor + getters/setters): athlete, age, gender, test type, value, unit,
rating, notes, timestamp, venue, latitude, longitude, synced.

### data/DBHelper.java — SQLite (Milestone 3)
`extends SQLiteOpenHelper`. `onCreate()` runs `CREATE TABLE results (...)` the first time.
`onUpgrade()` drops and recreates when `DB_VERSION` increases.

| Method | SQL operation |
|---|---|
| `insertResult()` | INSERT (via `ContentValues`) |
| `getAllResults()` | SELECT ... ORDER BY test_date DESC |
| `getUnsyncedResults()` | SELECT ... WHERE synced = 0 |
| `markSynced()` | UPDATE ... SET synced = 1 |
| `deleteResult()` | DELETE ... WHERE id = ? |
| `getResultCount()` | SELECT COUNT(*) |

`?` placeholders + argument arrays prevent SQL injection. Cursors are closed after use.

### data/SessionPrefs.java — SharedPreferences
Key-value storage for the current athlete/venue and a random device id. `apply()` saves in the background.
Doubles are stored as strings because SharedPreferences has no `putDouble`.

### data/CsvExporter.java — file handling (Milestone 4)
Writes to `getExternalFilesDir(null)/exports/KhelLab_results_<date>.csv` (app-specific folder → **no
storage permission needed**). `FileWriter` in try-with-resources (auto-close). Values with commas/quotes
are escaped. **Anonymised** mode replaces names with "Athlete 1, 2..." (privacy for minors).
`ResultsActivity` shares it through **FileProvider** (`content://` URI + `FLAG_GRANT_READ_URI_PERMISSION`),
because Android does not allow sharing raw `file://` paths.

### data/FirebaseSync.java — cloud sync (Milestone 5)
1. If Firebase is not configured (no `google-services.json`) → friendly message.
2. If offline (`ConnectivityManager`) → "results are safe on this phone, sync later" (offline-first).
3. **Anonymous sign-in** (`FirebaseAuth.signInAnonymously`) → a user id without a password.
4. A Firestore **WriteBatch** uploads all unsynced results to collection `results`, document id
   `deviceId_rowId` (re-upload overwrites, never duplicates).
5. On success → `markSynced()` for each row in SQLite.

### VenueMapActivity.java — Google Maps (Milestone 6)
Collects distinct venues with GPS from SQLite (+ the current session venue) using a `LinkedHashMap`.
With a Maps API key: `SupportMapFragment` + `getMapAsync()` → markers, camera fitted to all venues.
Without a key: a list; tapping opens a `geo:lat,lng?q=...` Intent in the Google Maps app.

### ResultsActivity.java / ui/ResultAdapter.java
`ListView` + custom `ArrayAdapter` (`getView` inflates `item_result.xml`, re-uses `convertView`).
Tap → details `AlertDialog`; long-press → confirm-delete dialog. Export & Sync buttons.

### UiUtils.java
Android 15+ draws apps edge-to-edge; `applySystemBarPadding()` adds the status/navigation bar sizes as
padding (same code as the original Milestone 1 `MainActivity`, reused on every screen).

### AndroidManifest.xml
Permissions: CAMERA, INTERNET, ACCESS_NETWORK_STATE, FINE/COARSE LOCATION. Six activities
(HomeActivity is the LAUNCHER). `FileProvider` for CSV sharing. `com.google.android.geo.API_KEY`
meta-data filled from `local.properties`. Camera `uses-feature required="false"`.

---

## 5. Syllabus / CO mapping

| CO | Where it is in KhelLab |
|---|---|
| CO1 UI design | 6 screens; LinearLayout, FrameLayout, ScrollView, MaterialCardView, RadioGroup, EditText, ListView, AlertDialogs, Toasts, custom View |
| CO2 File handling (text & images) | CSV export + FileProvider sharing; camera frames processed for pose estimation |
| CO3 Database & location | SQLite (SQLiteOpenHelper), SharedPreferences, Firebase Firestore, GPS (Fused Location), Google Maps |
| CO4 Full application | Complete capture → rate → store → export → sync flow, runs on emulator and phone |
| Advanced element | On-device ML Kit Pose Detection (33 body landmarks in real time) |

---

## 6. Optional setup (needs your own Google account)

### Firebase (to make "Sync to cloud" upload)
1. Android Studio → **Tools → Firebase → Cloud Firestore → Connect to Firebase** (or download
   `google-services.json` from console.firebase.google.com for package `com.khellab.app` and put it in `app/`).
2. Firebase console → **Authentication → Sign-in method → enable Anonymous**.
3. **Firestore Database → Create database** (test mode is fine for the demo).
4. Rebuild & run → Results → **Sync to cloud** → data appears in the `results` collection.
`app/build.gradle` applies the google-services plugin automatically once the json file exists.

### Google Maps key (to show the in-app map)
console.cloud.google.com → enable **Maps SDK for Android** → create API key → in `local.properties` add
`MAPS_API_KEY=your_key` → rebuild. Without a key the venue list + "open in Google Maps" still works.

---

## 7. Accuracy notes & limitations (be honest at the viva)
- Analysis runs at roughly 15–30 frames/s → each frame is 33–66 ms, so flight time can be off by about
  ±1 frame (≈ ±3–4 cm at a 30 cm jump). Slow-motion video (120/240 fps) + Manual entry is more precise.
- Take-off/landing are detected at the threshold line (not the exact toe-off), which slightly shortens the
  measured flight time; the formula also assumes the athlete lands with the same posture as take-off
  (bending knees/legs early inflates flight time — a known issue of all flight-time methods).
- The phone must be still (tripod), the full body visible, good lighting.
- Reaction time includes touchscreen delay (~50 ms); benchmarks account for that roughly.
- Sprint timing is manual (human reaction ~0.1–0.2 s error).
- Benchmarks are indicative, not official norms. **Nothing in the app is a medical assessment.**
- Evaluation plan: compare app jump height with a tape-measured reach jump on ~10 athletes, two lighting
  conditions, report mean error in cm.

## 8. Likely viva questions
- *Why CameraX and not Camera2?* Lifecycle-aware, far less code, handles device differences.
- *Why STREAM_MODE?* Tracks between frames → faster and smoother than SINGLE_IMAGE_MODE for video.
- *Why close ImageProxy?* CameraX has a small frame buffer; unclosed frames block new ones.
- *Why SQLiteOpenHelper not Room?* Explicit SQL is easier to explain; Room would add annotations/codegen.
- *Why SharedPreferences for the session?* Small key-value data that must survive app restarts.
- *Why FileProvider?* Since Android 7, sharing `file://` URIs throws `FileUriExposedException`.
- *Offline?* Everything works offline; SQLite is the source of truth, Firebase is an optional upload.
- *Where does ML run?* On the phone (ML Kit bundled model) — no internet, no data leaves the device.
- *What is `postInvalidate()`?* Requests a redraw (onDraw) and is safe from any thread.
- *Why the camera timestamp?* It is the exact capture time of the frame, not when ML Kit finished.
