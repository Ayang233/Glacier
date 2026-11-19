# NOTICE

Glacier — Android app freezer.

Copyright (C) 2026 Ayang

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

The full license text is in `LICENSE` and is bundled into the APK as
`assets/LICENSE.txt`, where the in-app "Open source licenses" screen renders it.

## Derived work

Glacier's freeze backends, working-mode semantics and on-disk data format
(`v1/apps.json`, `v1/tags.json`) derive from **Hail**:

- Hail — https://github.com/aistra0528/Hail
- Copyright (C) aistra0528
- License: GPL-3.0-or-later

Glacier is an independent product: its package name, architecture, UI layer,
class names, resource namespace, preference keys, data directory, notification
channels, WorkManager task names, shortcut IDs, Intent actions and URI scheme
are all Glacier-specific. Hail's UI code was not carried over.

## Third-party components

| Component | License | Use |
|---|---|---|
| MIUIX (`top.yukonga.miuix.kmp`) | Apache-2.0 | OS4 theme, components, official glass surfaces |
| AndroidX / Jetpack Compose | Apache-2.0 | UI runtime, lifecycle, WorkManager |
| Shizuku (`dev.rikka.shizuku`) | Apache-2.0 | Privileged binder access without root |
| Dhizuku (`com.rosan.dhizuku`) | Apache-2.0 | Delegated device-owner access |
| AppIconLoader (`me.zhanghai.android.appiconloader`) | Apache-2.0 | Adaptive icon rendering |
| libxposed API (`io.github.libxposed`) | Apache-2.0 | Optional LSPosed module entry point (compileOnly) |
| HiddenApiBypass (`org.lsposed.hiddenapibypass`) | Apache-2.0 | Hidden API reflection |

Notes on deliberate exclusions:

- **No `androidx.compose.material3`.** Glacier uses MIUIX as its only design
  system; mixing two systems produces duplicate classes and two color systems.
- **No third-party pinyin library.** Pinyin search uses Android's built-in ICU
  `Han-Latin` transliteration, which avoids adding a GPL-2.0-only dependency to a
  GPL-3.0-or-later program.

## Branding

Hail's name, logo and brand copy are not used in Glacier's user interface.
"Hail" appears only where the license requires attribution: this NOTICE, the
README, and the in-app license screen.
