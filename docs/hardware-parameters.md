# Hardware Parameters Reference

All `Camera.Parameters` keys written by `HardwareRecipeApplier.java`. Parameters are applied in two committed stages so Stage 1 base state is visible to Stage 2 reads.

---

## Stage 1 — Drive mode, creative style, picture effects, vignette

| Key | Values | Notes |
|---|---|---|
| `drive-mode` | `single` | Always forced — burst causes RAM crash on BIONZ X |
| `picture-profile` | `off` | Always forced off |
| `creative-style` | `standard`, `vivid`, `neutral`, `portrait`, `landscape`, `sunset`, `bw`, `sepia` | Base ISP tone curve |
| `color-mode` | same as creative-style | Sony uses both keys on different bodies |
| `pro-color-mode` | `off`, or a pro mode string | Takes over from creative-style when set |
| `picture-effect` | `off`, `toy-camera`, `pop-color`, `posterization`, `retro-photo`, `soft-high-key`, `part-color`, `rough-mono`, `soft-focus`, `hdr-art`, `richtone-mono`, `miniature`, `illust`, `watercolor` | Mutually exclusive with creative-style |
| `pe-toy-camera-effect` | `normal`, `cool`, `warm`, `green`, `magenta` | Sub-option for toy-camera |
| `pe-toy-camera-tuning` | integer | Vignette strength inside toy-camera (uses `vignetteHardware` value) |
| `pe-soft-focus-effect-level` | `1`–`3` | For soft-focus effect |
| `pe-hdr-art-effect-level` | `1`–`3` | For hdr-art effect |
| `pe-illust-effect-level` | `1`–`3` | For illust effect |
| `pe-watercolor-effect-level` | `1`–`3` | For watercolor effect |
| `pe-part-color-effect` | `red`, `green`, `blue`, `yellow` | For part-color effect |
| `pe-miniature-focus-area` | `auto`, `left`, `vcenter`, `right`, `upper`, `hcenter`, `lower` | For miniature effect |
| `vignetting` / `vignette` | integer | Hardware vignette strength (both keys tried for body compatibility) |

---

## Stage 2 — White balance, color matrix, DRO, tone, lens correction

| Key | Values | Notes |
|---|---|---|
| `white-balance` | `auto`, `daylight`, `shade`, `cloudy-daylight`, `incandescent`, `fluorescent`, `color-temp` | |
| `color-temperture-white-balance` | 2500–9900 (integer string) | Kelvin mode — note Sony typo in key name is intentional |
| `white-balance-shift-mode` | `true` / `false` | Must be `true` for shifts to take effect |
| `white-balance-shift-lb` | integer | Amber/Blue shift axis |
| `white-balance-shift-cc` | integer | Green/Magenta shift axis (sign is inverted in code) |
| `contrast` | integer | |
| `saturation` | −16 to +16 | |
| `sharpness` | integer | |
| `sharpness-gain` | −7 to +7 | |
| `sharpness-gain-mode` | `true` | Must be enabled for sharpness-gain to take effect |
| `dro-mode` | `off`, `auto`, `on` | Dynamic Range Optimizer |
| `dro-level` | `1`–`5` (string) | Used when `dro-mode` = `on` |
| `color-depth-red` | −7 to +7 | 6-axis per-hue saturation/density |
| `color-depth-green` | −7 to +7 | |
| `color-depth-blue` | −7 to +7 | |
| `color-depth-cyan` | −7 to +7 | |
| `color-depth-magenta` | −7 to +7 | |
| `color-depth-yellow` | −7 to +7 | |
| `rgb-matrix-mode` | `true` | Must be enabled for matrix to apply |
| `rgb-matrix` | 9 integers, comma-separated (hardware scale 0–1024) | Code converts from %-based 0–100 internally (`value * 10.24`) |
| `lens-correction` | `true` | Enables lens correction subsystem |
| `lens-correction-shading-color-red` | integer | Per-channel shading compensation |
| `lens-correction-shading-color-blue` | integer | Per-channel shading compensation |

---

## Notes

- All keys are checked for existence with `p.get(key) != null` before setting — missing keys on a given body are silently skipped.
- PARAMS.TXT (repo root, when present) is the authoritative source of valid ranges for the connected camera. These tables reflect what the code currently uses; actual hardware limits may be narrower.
- The `rgb-matrix` identity value in hardware units is `1024,0,0,0,1024,0,0,0,1024` (i.e. 100% on the diagonal in the 0–100 scale used by `RTLProfile.advMatrix`).
