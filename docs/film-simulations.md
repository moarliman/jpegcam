# Film Simulation Roadmap

Two implementation options for adding Fujifilm-style live film simulations to jpegcam
on the Sony a6000 (BIONZ X).

---

## Option 1: Pure ISP Film Simulations

### Summary

Each simulation is a preset of hardware camera parameters applied via
`HardwareRecipeApplier`. Because the BIONZ X ISP processes both the viewfinder feed
and the captured JPEG, the preview is pixel-accurate with zero render overhead.

This is identical to how Fujifilm implements their film simulations in firmware.

### How it works

Each film simulation maps to a bundle of `Camera.Parameters` keys already supported
by `HardwareRecipeApplier`:

| Parameter | Key | Film simulation role |
|---|---|---|
| 3×3 RGB color matrix | `rgb-matrix` | Core colour rendition |
| 6-axis colour depth | `color-depth-{red,green,blue,cyan,magenta,yellow}` | Per-hue saturation/density |
| Contrast | `contrast` | Tone curve shape |
| Saturation | `saturation` | Overall chroma strength |
| Sharpness | `sharpness`, `sharpness-gain` | Acutance / micro-contrast |
| Dynamic Range Optimizer | `dro-mode`, `dro-level` | Shadow/highlight latitude |
| White balance shift | `white-balance-shift-lb/cc` | Warm/cool/tint cast |
| Creative style | `creative-style` | Base ISP tone profile |

### Files to change

- `RTLProfile.java` — add film simulation preset field
- `HardwareRecipeApplier.java` — already handles all keys; no logic changes, just new recipe data
- `MenuController.java` — add film simulation picker UI
- `app/src/main/assets/` — JSON file containing the preset library

### Performance

Zero render-time cost. ISP applies all parameters during capture and simultaneously
to the live viewfinder. No software processing added.

### Limitations

- No per-channel S-curves (ISP has only a global contrast knob)
- No luma-dependent hue shifts
- No grain, halation, or bloom (these remain capture-only software effects)
- Covers ~80% of film looks; complex cross-processed aesthetics need Option 3

---

## Option 3: Hybrid ISP + GPU LUT + Software Finishing ⛔ NOT VIABLE

> **Dead end confirmed.** Android 2.3.7 (API 10) does not provide the
> `GL_OES_EGL_image_external` extension required to route the camera preview
> through an OpenGL ES fragment shader. This was verified by the original
> developer during early LUT development. Option 3 cannot be implemented on
> BIONZ X hardware without a platform upgrade.



### Summary

A three-layer architecture that mirrors the full Fujifilm experience:

| Layer | Where it runs | Applies to |
|---|---|---|
| ISP recipe | BIONZ X hardware | Preview + captured JPEG (free) |
| 3D LUT | OpenGL ES 2.0 GPU shader | Preview + captured JPEG |
| Grain / Bloom / Halation | C++ software (`process_kernel.h`) | Captured JPEG only |

The ISP handles the bulk of colour science at zero CPU cost. The GPU LUT covers looks
the matrix cannot express (cross-processed, split-toned, heavily desaturated). Grain
and halation remain capture-only since they are texture effects that do not affect
composition.

### Architecture

#### Layer 1 — ISP recipe
Same as Option 1. Each simulation bundles a `HardwareRecipeApplier` parameter set.

#### Layer 2 — GPU LUT preview

Replace `SurfaceView` with `GLSurfaceView`. Route the camera feed through a
`GL_OES_EGL_image_external` texture. Upload a 17³ downsampled LUT as a 289×17
`GL_TEXTURE_2D` (14 739 bytes). A fragment shader performs trilinear interpolation
using only 2 texture samples per pixel via `GL_LINEAR` filtering + one `mix()` call.

**New files:**
- `LutPreviewRenderer.java` — `GLSurfaceView.Renderer` + `OnFrameAvailableListener`
- `LutSampler.java` — resamples the loaded NxNxN LUT to 17³ for GPU upload

**Modified files:**
- `native-lib.cpp` — add `getLutDataNative()` / `getLutSizeNative()` JNI exports
- `LutEngine.java` — add `getPreviewLutData()` wrapper calling `LutSampler.resampleTo17()`
- `SonyCameraManager.java` — add `open(SurfaceTexture)` overload alongside existing `open(SurfaceHolder)`
- `MainActivity.java` — swap `SurfaceView` → `GLSurfaceView`, wire renderer callbacks,
  push LUT updates via `queueEvent()` in `onPreloadFinished`

**Fragment shader (core):**
```glsl
vec3 sampleSlice(float r, float g, float bIdx) {
    float ri = clamp(r * (N - 1.0), 0.0, N - 1.001);
    float u  = (bIdx * N + ri + 0.5) / NSq;
    float v  = (g * (N - 1.0) + 0.5) / N;
    return texture2D(uLutTexture, vec2(u, v)).rgb;
}
// Trilinear: bilinear within each B-slice (GPU hardware), linear between slices (mix)
vec3 lo  = sampleSlice(cam.r, cam.g, b0);
vec3 hi  = sampleSlice(cam.r, cam.g, b0 + 1.0);
vec3 lut = mix(lo, hi, fract(cam.b * (N - 1.0)));
```

#### Layer 3 — Software finishing (existing, capture only)

Grain Engine 1 (True Crystal), Bloom, Halation, Vignette continue to run via
`processImageNative()` / `process_kernel.h`. No changes needed.

### Performance on BIONZ X

| Layer | Overhead |
|---|---|
| ISP | 0 ms |
| GPU (720p, 30fps) | ~0 ms CPU — runs on GPU thread independently |
| Software (capture, 24 MP, all effects) | 15–30 s current → ~1–3 s after NEON + bloom IIR optimisations |

### Dependencies

- Requires Option 1 (ISP recipe system) as the colour foundation
- Requires GLES 2.0 + `GL_OES_EGL_image_external` (standard on all Android 4.0+ devices)
