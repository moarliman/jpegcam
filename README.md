# JPEG.CAM — Hardware ISP Control for Sony Alpha Cameras

> **Fork of the original JPEG.CAM project.**
> This fork replaces the software filter pipeline (LUTs, grain, bloom, halation) with direct control over the camera's built-in BIONZ X image processor via a phone web interface.

[![Build](https://github.com/moarliman/jpegcam/actions/workflows/build.yml/badge.svg)](https://github.com/moarliman/jpegcam/actions/workflows/build.yml)

## What it does

JPEG.CAM turns your Sony Alpha camera into a remotely-tunable color grading machine. Connect your phone to the camera's Wi-Fi hotspot, open the dashboard, and adjust image parameters in real time — the live preview on the camera screen updates immediately, and every photo you take is saved already-graded. No post-processing step, no waiting.

Everything runs inside the camera's BIONZ X image signal processor (ISP). The app just tells the ISP what to do.

## Why this fork exists

The original JPEG.CAM applied 3D LUTs, grain, bloom, and halation in software using an NDK image processing pipeline. On 2014-era BIONZ X hardware this takes 15–30 seconds per 24MP photo.

This fork removes that entire pipeline. The BIONZ X ISP already exposes every parameter you'd want — creative style, white balance, color matrix, saturation, DRO, picture effects, 6-axis color depth, RGB matrix — and applies them in zero CPU time, baked into the JPEG at the moment of capture.

**Result:** instant graded photos, a dramatically simpler app, and a live what-you-see workflow.

## Features

- **Recipes tab** — phone web UI with sliders and dropdowns for every ISP parameter; changes apply to the live preview within a frame
- **19 built-in film simulation presets** — Fuji-inspired starting points (Provia, Velvia, Astia, Classic Chrome, Eterna, Acros variants, and more), ready to load and tweak on first launch
- **Saved Looks** — all recipes (built-in and user-created) appear as one-tap buttons in the dashboard; load any look into the active slot and apply it live
- **10 recipe slots** — save and name looks; switch between them on the camera's control wheel without touching your phone
- **Recipe import / export** — download any slot as a JSON file, share it, upload it back on any camera running this app
- **Photos tab** — browse, filter, and download photos from the DCIM folder over Wi-Fi
- **Installable PWA** — add the dashboard to your phone's home screen for a full-screen experience with no browser chrome
- **Pure Java** — no native code, no NDK, fast CI builds (~1 min)

## What you can tune

| Section | Parameters |
|---|---|
| Base Look | Creative style (Standard, Vivid, Neutral, B&W, Sepia…), Pro Color Mode, DRO |
| Tone | Contrast, Saturation (−16 to +16), Sharpness, Micro-contrast (−7 to +7) |
| White Balance | Mode, Kelvin (2500–9900), Amber/Blue shift, Green/Magenta shift |
| Color Depth | Per-channel saturation: Red, Green, Blue, Cyan, Magenta, Yellow (−7 to +7) |
| RGB Matrix | Full 3×3 color transformation matrix |
| Picture Effects | Toy Camera, Pop Color, Soft Focus, HDR Art, Miniature, Watercolor, and more |
| Lens Shading | Per-channel shading correction, hardware vignette |

Full parameter reference: [`docs/hardware-parameters.md`](docs/hardware-parameters.md)  
Film simulation reference: [`docs/film-simulations.md`](docs/film-simulations.md)

## How to use

**1. Install**
1. Download the latest APK from the [Actions tab](../../actions) — grab the `JPEGCAM` artifact from the latest successful build
2. Install using [pmca-console](https://github.com/ma1co/Sony-PMCA-RE/releases)

**2. Connect your phone**
1. Open the app on the camera
2. Press MENU → NETWORK → Camera Hotspot (or Home Wi-Fi)
3. Connect your phone to the camera's network and open the URL shown on screen
4. Optional: tap the share icon in your browser → **Add to Home Screen** to install as a full-screen app

**3. Start from a preset**
1. Tap **Recipes** in the dashboard
2. Under **Saved Looks**, tap any film simulation preset to load it instantly
3. Adjust sliders to taste, then hit **Save** to store the look into a named slot

**4. Shoot**
Photos are saved already graded. The JPEG coming out of the camera is colour-graded at capture time — no separate processing folder, no waiting.

**5. Switch recipes on camera**
Spin the camera's control wheel to cycle through your 10 saved slots without touching the phone.

**6. Share recipes**
Tap **Download** in any recipe slot to save it as a `.json` file. Send it to a friend or load it back with **Upload**.

## Supported cameras

Any Sony BIONZ X camera that supports PMCA (Android 2.3.7 / API 10):
**a5100, a6000, a6300, a6500, a7S II, a7R II, RX100 III/IV/V** and others.

## Installation

Connect the camera via USB, then use [pmca-gui](https://github.com/ma1co/Sony-PMCA-RE/releases) or pmca-console:

```
pmca-console install JPEGCAM-v2.01.apk
```
