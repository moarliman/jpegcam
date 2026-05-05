# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About the Project Owner

The project owner is **not a professional programmer**. Always explain changes in plain language before showing code. Summarize what changed, which files were touched, and what to test on camera after each task.

## What This Project Is

JPEG.CAM is a custom Android app installed on Sony BIONZ X cameras via PMCA (PlayMemories Camera Apps). It runs on Android 2.3.7 (API 10). The phone connects to the camera's Wi-Fi and opens the web dashboard (`HttpServer`), where the user tunes hardware ISP parameters live — creative style, white balance, color matrix, DRO, picture effects, etc. The BIONZ X ISP applies every parameter at capture time, so photos are graded instantly with zero CPU cost. A 10-slot recipe system lets the user save and switch looks from the camera's control wheel without touching the phone.

**No native code. No software filters. The app is pure Java.**

## Building

**All builds happen via GitHub Actions only — never suggest local `./gradlew` commands.** Pushing to any branch triggers an APK build. The workflow produces a single APK artifact (`JPEGCAM`).

Toolchain specifics (relevant if editing CI): Gradle 4.10.3 (downloaded directly, not the wrapper), Java 8 for the build step, Java 11 for `sdkmanager`, SDK packages `platforms;android-10`, `platforms;android-25`, `build-tools;26.0.2`.

## Testing

There are no automated tests. All validation is manual testing on physical Sony camera hardware.

## Git Workflow

- **Working branch:** `dev-cli` — never commit directly to `main`
- User merges `dev-cli` → `main` manually after confirming the feature works on camera
- Every push to any branch triggers a GitHub Actions build

## Hard Constraints — Never Violate

1. **Android API 10 only.** No Kotlin, no Coroutines, no RxJava, no ConstraintLayout, no Jetpack libraries. No Java 8+ features — no lambdas. Never put heavy work on the UI thread.
2. **No native code.** The app is Java-only. Do not propose NDK, JNI, CMake, or C/C++ additions.
3. **MainActivity.java is a lightweight router only.** All new logic belongs in dedicated managers.
4. **Do not auto-import any library not already in `app/build.gradle`.**
5. **HttpServer camera writes must be dispatched to the main thread** via `runOnMainThreadAndWait()` — NanoHTTPD runs on its own thread pool and the Camera API is not thread-safe.

## Code Change Rules

1. **Surgical changes only.** Never rewrite entire files unless explicitly asked. Show ±2–3 lines of context around any edit.
2. **Show the plan first.** Describe what you'll change in plain English and wait for "go ahead" before writing any file.
3. **One file at a time.** Never edit multiple files simultaneously without asking.
4. **Stop after each file.** Wait for confirmation the code compiled before touching anything else.
5. **Multi-part changes:** number each chunk and wait for compile confirmation before the next.
6. **On compiler errors:** assume typo or missing brace first; compare carefully before assuming a logic flaw.

## Architecture Overview

```
MainActivity (lightweight router)
    │
    ├── InputManager          — hardware key mapping; control wheel switches recipe slots
    ├── MenuController        — 6-page settings menu (system/connectivity, no parameter editing)
    ├── HudController         — on-screen overlay (active slot name, battery, EXIF)
    ├── PlaybackController    — photo review UI
    │
    ├── SonyCameraManager     — Sony BIONZ X hardware API abstraction
    ├── HardwareRecipeApplier — translates RTLProfile → Camera.Parameters (two-stage ISP commit)
    │
    ├── RecipeManager         — 10 hardware-only recipe slots; each slot is an RTLProfile
    │       └── RTLProfile    — all ISP parameters (WB, matrix, color depth, effects, etc.)
    ├── MatrixManager         — named RGB matrix presets on SD card
    │
    ├── ConnectivityManager   — Wi-Fi state + HttpServer lifecycle
    └── HttpServer (NanoHTTPD) — wireless dashboard (assets/index.html)
            ├── GET/PUT /api/hardware          — read/write current RTLProfile live
            ├── GET     /api/recipes           — list all 10 slot names
            ├── GET     /api/recipes/active    — active slot index
            ├── PUT     /api/recipes/{n}/load  — switch slot and apply to ISP
            └── PUT     /api/recipes/{n}/save  — persist current params to slot n
```

### Key Hardware Parameters (Sony BIONZ X target)

All parameters are documented in `docs/hardware-parameters.md`. Key limits:

- Saturation: −16 to +16; color-depth per channel: −7 to +7; sharpness-gain: −7 to +7
- RGB matrix: 9 integers, 0–100 scale (code converts to 0–1024 hardware scale internally)
- Max image size: 6000×4000 (24MP)
- `picture-effect-values`: toy-camera, pop-color, posterization, retro-photo, soft-high-key, part-color, rough-mono, soft-focus, hdr-art, richtone-mono, miniature, illust, watercolor

When proposing camera parameter values, verify they are within these hardware limits before suggesting them.

### SD Card Directory Layout

All app files live under `JPEGCAM/` at the storage root. `Filepaths.java` probes multiple mount points (`/storage/sdcard1`, `/mnt/sdcard`, etc.) for multi-slot cameras.

```
JPEGCAM/
    RECIPES/    — recipe slot JSON files (R_SLOT01.TXT … R_SLOT10.TXT)
```

## Reference

- **OpenMemories-Framework** (`https://github.com/ma1co/openmemories-framework`) — authoritative source for Sony camera API behavior. Consult when unsure how the camera API works.
- **PARAMS.TXT** (in repo root, when present) — live hardware capability manifest for the target camera; always check it before suggesting parameter values.
- **docs/hardware-parameters.md** — full reference of all `Camera.Parameters` keys that `HardwareRecipeApplier` uses.
- **camera-recipe-hub** — companion web platform. Separate repository.
