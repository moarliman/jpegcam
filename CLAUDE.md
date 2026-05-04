# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About the Project Owner

The project owner is **not a professional programmer**. Always explain changes in plain language before showing code. Summarize what changed, which files were touched, and what to test on camera after each task.

## What This Project Is

JPEG.CAM is a custom Android app installed on Sony BIONZ X cameras via PMCA (PlayMemories Camera Apps). It runs on Android 2.3.7 (API 10) and applies real-time film emulation (3D LUT color grading, grain, bloom, vignette, halation) to JPEG photos at capture time, saving graded copies to `/GRADED/` on the SD card. Original files are never modified.

## Building

**All builds happen via GitHub Actions only — never suggest local `./gradlew` commands.** Pushing to any branch triggers an APK build. The workflow produces two artifacts: `JPEGCAM-legacy` (NDK r16, both `armeabi` + `armeabi-v7a`) and `JPEGCAM-modern` (NDK r21, `armeabi-v7a` only).

The build downloads libjpeg-turbo 2.1.5.1 and stb_image.h at build time — they are not vendored.

## Testing

There are no automated tests. All validation is manual testing on physical Sony camera hardware.

## Git Workflow

- **Working branch:** `dev-cli` — never commit directly to `main`
- User merges `dev-cli` → `main` manually after confirming the feature works on camera
- Every push to any branch triggers a GitHub Actions build

## Hard Constraints — Never Violate

1. **Android API 10 only.** No Kotlin, no Coroutines, no RxJava, no ConstraintLayout, no Jetpack libraries. No Java 8+ features — no lambdas. Never put heavy work on the UI thread.
2. **C++11 / NDK: NO NEON SIMD.** Scalar code only. Must compile with `gnustl_static`.
3. **Memory.** Use libjpeg-turbo (not Android built-ins) for JPEG/EXIF. Prefer raw binary parsing to avoid OOM on 24MP images.
4. **Modular assets.** LUTs and matrices live as files on the SD card — never hardcode them in Java arrays.
5. **MainActivity.java is a lightweight router only.** All new logic belongs in dedicated managers.
6. **Do not auto-import any library not already in `app/build.gradle`.**

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
    ├── InputManager          — hardware key mapping
    ├── MenuController        — 8-page settings menu
    ├── HudController         — on-screen overlay
    ├── PlaybackController    — photo review UI
    │
    ├── SonyCameraManager     — Sony BIONZ X hardware API abstraction
    ├── HardwareRecipeApplier — sets ISP params (RGB matrix, saturation, etc.)
    │
    ├── RecipeManager         — 10 recipe slots; each slot is an RTLProfile
    │       └── RTLProfile    — LUT name + all effect parameters
    ├── MatrixManager         — RGB color matrix files on SD card
    ├── LensProfileManager    — lens calibration profiles
    │
    ├── ImageProcessor        — async coordinator: detects new JPEGs, runs pipeline
    │       └── LutEngine (JNI) → native-lib.cpp → process_kernel.h
    │                               libjpeg-turbo decode → effects → encode
    │                               output: /GRADED/ on SD card
    │
    ├── SonyFileScanner       — file system watcher for new JPEGs and LUT files
    ├── ConnectivityManager   — Wi-Fi state
    └── HttpServer (NanoHTTPD) — wireless dashboard served from assets/index.html
```

### Native Layer (`app/src/main/cpp/`)

| File | Role |
|---|---|
| `native-lib.cpp` | JNI bridge — JPEG decode/encode, LUT loading (`.cube`, `.cub`, `.png` HaldCLUT), trilinear interpolation |
| `process_kernel.h` | All pixel-level effects: grain, vignette, bloom, halation, roll-off, chroma, overlay blending, color depth |

CMake disables SIMD explicitly. C++ flags: `-O3 -ffast-math`.

### Key Hardware Parameters (Sony BIONZ X target)

- `jpeg-quality-values`: **50 and 25 only** — no other values are valid
- `rgb-matrix-supported=true` — 9-value matrix
- Saturation: −16 to +16; color-depth per channel: −7 to +7; sharpness-gain: −7 to +7
- Max image size: 6000×4000 (24MP)
- `picture-effect-values`: toy-camera, pop-color, posterization, retro-photo, soft-high-key, part-color, rough-mono, soft-focus, hdr-art, richtone-mono, miniature, illust, watercolor

When proposing camera parameter values, verify they are within these hardware limits before suggesting them.

## Reference

- **OpenMemories-Framework** (`https://github.com/ma1co/openmemories-framework`) — authoritative source for Sony camera API behavior. Consult when unsure how the camera API works.
- **PARAMS.TXT** (in repo root, when present) — live hardware capability manifest for the target camera; always check it before suggesting parameter values.
