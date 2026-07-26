# Gym Helper

Gym Helper is an Android workout application built with Kotlin and Jetpack Compose. It reads workout plans directly from Google Sheets, guides the user through exercises and sets, records completed repetitions and weights locally, and exports completed workout sessions as CSV files that can be opened through the phone's file manager.

## Features

- Loads workout days directly from Google Sheets
- Displays the first unchecked workout day
- Supports up to five workout categories per day
- Guides the user through exercises one set at a time
- Supports fixed set counts such as `3`
- Supports set ranges such as `6-8`
- Records actual repetitions and weight for every submitted set
- Saves a set only when the user presses:
  - `Next Set`
  - `Next Exercise`
  - `Finish Workout`
- Does not save warm-up exercises
- Saves mobility exercises normally
- Stores workout history locally using SQLite
- Exports completed sessions as CSV files
- Supports kilograms and pounds
- Allows navigation to previous workout days
- Prevents moving before the first day
- Shows a confirmation dialog before abandoning a workout
- Deletes the current incomplete session when the user confirms exit

## Technology

- Kotlin
- Jetpack Compose
- Navigation Compose
- SQLite
- Google Sheets API
- Android MediaStore
- Legacy public-storage support for Android 9
- Secrets Gradle Plugin

## Requirements

- Android Studio
- Android device or emulator
- Internet connection
- Google Sheets API key
- Publicly viewable Google Sheet
- A project-compatible Android SDK

Android 9 requires storage permission before files can be written to the public Downloads folder.

---

# Google Sheets Setup

The spreadsheet must contain two tabs named exactly:

```text
Days
Exercises
```

The configured range names depend on these tab names.

## `Days` Sheet Format

The `Days` sheet controls the workout schedule.

Recommended structure:

| Day | Exercise 1 | Exercise 2 | Exercise 3 | Exercise 4 | Exercise 5 | Check after the workout | Date |
|---:|---|---|---|---|---|---|---|
| 1 | Warm up | Back | Chest | Shoulders | | TRUE | |
| 2 | Warm up | Lactic system | Arms | | | FALSE | |
| 3 | Warm up + Mobility | Legs | Core | | | FALSE | |

### Column rules

- `Day` must contain a whole number.
- `Exercise 1` through `Exercise 5` contain workout category names.
- Empty exercise cells are allowed.
- `Check after the workout` must contain a Google Sheets checkbox or a value such as `TRUE` or `FALSE`.
- Do not merge cells in the data area.
- Category order is preserved from left to right.

```text
Exercise 1 → Exercise 2 → Exercise 3 → Exercise 4 → Exercise 5
```

Empty values are ignored.

## Workout-day selection

The app starts from the first row where:

```text
Check after the workout = FALSE
```

Example:

```text
Day 1 = TRUE
Day 2 = TRUE
Day 3 = FALSE
```

The app starts on Day 3.

The app currently reads the checkbox but does not update it. After completing a workout, manually check that day in Google Sheets before expecting the next day to appear.

When starting a new schedule, day numbers may be reused. Reset all checkboxes in the new schedule to `FALSE`.

---

# `Exercises` Sheet Format

The `Exercises` sheet contains the detailed exercises for each category.

## Normal exercise categories

A category heading must contain text only in column A.

Example:

| A | B | C | D | E | F |
|---|---|---|---|---|---|
| Back | | | | | |
| Exercise | Reps | Sets | Weight/Note | Previous Data | Latest Data |
| T-Bar Row | 10-12 | 3 | 30kg | 32.5kg | 35kg |
| Lat Pulldown | 10-12 | 3 | 30kg | | 35kg |

### Normal exercise columns

| Column | Meaning |
|---|---|
| A | Exercise name |
| B | Target repetitions or time |
| C | Number of sets or set range |
| D-F | Notes, previous weights, or latest weights |

The rightmost value in columns D-F containing `kg` or `lb` is used as the suggested weight.

Example:

```text
D = 30kg
E = 32.5kg
F = 35kg
```

The app suggests:

```text
35 kg
```

## Weight formatting

Valid examples:

```text
30kg
30 kg
7.5kg
65lb
65 lb
```

Avoid:

```text
30
thirty kilograms
```

The parser requires the `kg` or `lb` unit.

## Repetition formatting

Valid examples:

```text
10-12
8-10
Failure
30 seconds
20 minutes
```

Timed targets containing words such as `seconds`, `minutes`, or `time` do not require an actual-repetitions value.

## Set formatting

Valid values:

```text
3
4
6-8
6 – 8
6 to 8
```

The recommended format is:

```text
6-8
```

For set ranges, the app allows the user to choose any value inside the range.

Example:

```text
6-8
```

Allowed:

```text
6
7
8
```

Rejected:

```text
5
9
```

Set the Reps and Sets columns to:

```text
Format → Number → Plain text
```

This prevents Google Sheets from converting values such as `6-8` into dates.

---

# Cardio and Energy-System Formatting

The cardio section uses a different row format.

Example:

| A | B | C | D | E | F |
|---|---|---|---|---|---|
| Cardio and Endurance | | | | | |
| Aerobic system | 20 minutes | | 1 | 65%-75% HR | Steady pace |
| Lactic system | 30 seconds | 60 seconds | 6-8 | 85%-95% HR | Fast intervals |
| Alactic system | 10 seconds | 120 seconds | 5 | Maximum effort | Full recovery |

### Cardio columns

| Column | Meaning |
|---|---|
| A | System name |
| B | Exercise duration |
| C | Rest duration |
| D | Sets or set range |
| E | Heart-rate zone |
| F | Notes |

Category matching is exact.

```text
Lactic system
```

loads only the Lactic system row. It does not load Alactic system.

```text
Aerobic system
```

loads only the Aerobic system row.

---

# Category Name Matching

Category names in the `Days` sheet should match headings or rows in the `Exercises` sheet.

Examples:

```text
Arms ↔ Arms
Chest ↔ Chest
Legs ↔ Legs
Core ↔ Core
Mobility ↔ Mobility
Lactic system ↔ Lactic system
```

Special mappings supported by the app include:

```text
Back → Back (heavy weight)

Warm up + Mobility
→ Warm up section
→ Mobility section

20min Aerobic system
→ Aerobic system
```

Avoid unnecessary spelling differences such as:

```text
Shoulder
Shoulders
Shoulder exercises
```

Use one consistent category name.

---

# Google Sheets API Setup

## 1. Create an API key

In Google Cloud Console:

1. Create or select a project.
2. Enable the Google Sheets API.
3. Open `APIs & Services`.
4. Open `Credentials`.
5. Create an API key.
6. Restrict the key to the Google Sheets API.

For initial testing, application restrictions may be left unrestricted. Do not publish an unrestricted production key.

## 2. Make the spreadsheet viewable

The current app uses API-key access without Google account authentication.

Set spreadsheet sharing to:

```text
Anyone with the link → Viewer
```

A private spreadsheet requires OAuth or another authenticated system.

## 3. Find the spreadsheet ID

Example URL:

```text
https://docs.google.com/spreadsheets/d/1ABC123XYZ/edit
```

The spreadsheet ID is:

```text
1ABC123XYZ
```

Copy only the value between `/d/` and `/edit`.

---

# Adding the API Key and Spreadsheet ID

Create this file in the project root:

```text
secrets.properties
```

It must be beside:

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
app/
```

Add:

```properties
SHEETS_API_KEY=YOUR_REAL_API_KEY
SPREADSHEET_ID=YOUR_REAL_SPREADSHEET_ID
```

Do not use quotation marks.

Correct:

```properties
SHEETS_API_KEY=AIzaSyExample
SPREADSHEET_ID=1ABC123XYZ
```

Incorrect:

```properties
SHEETS_API_KEY="AIzaSyExample"
SPREADSHEET_ID="1ABC123XYZ"
```

## Default properties file

Create or keep:

```text
local.defaults.properties
```

Add placeholder values:

```properties
SHEETS_API_KEY=NO_API_KEY
SPREADSHEET_ID=NO_SPREADSHEET_ID
```

This file may be committed to GitHub.

## Protecting the real key

Add this to `.gitignore`:

```gitignore
secrets.properties
```

Never upload the real API key to GitHub.

The Kotlin code reads the values through:

```kotlin
BuildConfig.SHEETS_API_KEY
BuildConfig.SPREADSHEET_ID
```

After changing the secret values, run:

```text
File → Sync Project with Gradle Files
Build → Clean Project
Build → Rebuild Project
```

---

# Android Permissions

The manifest must include internet permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Android 9 and older also require:

```xml
<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

On Android 9, accept the storage permission popup when the app starts.

---

# How Workout Sessions Are Saved

When the user presses `Start Workout`, the app creates a local SQLite session.

Each set is saved only when the user presses:

```text
Next Set
Next Exercise
Finish Workout
```

Typing values into a field does not save them.

Each saved set contains:

```text
Day number
Session ID
Category
Exercise name
Set number
Actual repetitions
Weight
Weight unit
Target
Completion time
```

## Warm-up rules

Warm-up exercises:

```text
Do not display actual repetitions
Do not display weight
Are not saved locally
```

Mobility exercises are saved normally.

## Leaving a workout

Pressing Back displays a confirmation dialog.

```text
Cancel
→ remain in the workout
→ keep the session

Delete and Exit
→ delete the current session
→ delete every submitted set from that session
→ return to the home screen
```

---

# Exported Workout Files

Completed workouts are exported as CSV files.

Filename format:

```text
dayNumber#CurrentDate.csv
```

Example:

```text
3#2026-07-26.csv
```

Location:

```text
My Files
→ Internal storage
→ Download
→ Gym Helper
```

The CSV contains information such as:

```csv
Day,Date,Session ID,Category,Exercise,Set,Actual Reps,Weight,Unit,Target,Completed At
3,2026-07-26,18,Back,T-Bar Row,1,12,30,KG,10-12,2026-07-26 18:35:20
3,2026-07-26,18,Back,T-Bar Row,2,11,30,KG,10-12,2026-07-26 18:37:10
```

If the same day number is completed twice on the same calendar date, the newer CSV replaces the older file because both use the same filename.

---

# Running the Project

1. Clone the repository.
2. Open the project root in Android Studio.
3. Create `secrets.properties`.
4. Add the API key and spreadsheet ID.
5. Make the spreadsheet publicly viewable as Viewer.
6. Confirm the tab names are exactly `Days` and `Exercises`.
7. Sync Gradle.
8. Connect a device or start an emulator.
9. Run the `app` module.
10. Accept storage permission on Android 9 or older.

---

# Main App Flow

```text
Open app
↓
Download Days sheet
↓
Find first unchecked workout day
↓
Display workout categories
↓
Press Start Workout
↓
Create local session
↓
Download detailed exercises
↓
Enter repetitions and weight
↓
Press Next Set
↓
Save the submitted set
↓
Continue through all exercises
↓
Press Finish Workout
↓
Mark local session complete
↓
Export CSV to Downloads/Gym Helper
↓
Return to home screen
```

---

# Current Limitations

- Internet is required to load workout days and exercises.
- The app reads Google Sheets but does not update checkbox values.
- Completed checkboxes must be changed manually in Google Sheets.
- The sheet must be publicly viewable with the API-key setup.
- There is currently no authenticated Google login.
- Local SQLite history is removed if the app is uninstalled or its data is cleared.
- CSV files remain in Downloads unless manually deleted.
- A history screen may still be needed to view local sessions inside the app.
- Actual duration is not recorded for timed exercises.
- Reusing the same day number and date replaces the previous CSV file.
- Sheet category names and formatting must remain consistent.
- Warm-up exercises are intentionally excluded from saved history.
