# Third-Party Notices

MiniOS includes or depends on the following third-party software. Each component
remains the property of its respective authors and is used under its own license.

This file is provided for attribution and compliance. License texts are available
at the URLs indicated or in the dependency artifacts distributed with the build.

---

## Summary

| Component | License | Notes |
|-----------|---------|-------|
| AndroidX (Core, Lifecycle, Activity, DataStore, etc.) | Apache License 2.0 | Google / Android Open Source Project |
| Jetpack Compose (UI, Material3, Icons) | Apache License 2.0 | Google / Android Open Source Project |
| Kotlin Standard Library | Apache License 2.0 | JetBrains |
| kotlinx-coroutines | Apache License 2.0 | JetBrains |
| Coil (coil-compose) | Apache License 2.0 | Coil Contributors |
| Android Gradle Plugin | Apache License 2.0 | Google |
| Kotlin Android Gradle Plugin | Apache License 2.0 | JetBrains |

---

## AndroidX & Jetpack Compose

Copyright © The Android Open Source Project

Licensed under the Apache License, Version 2.0.  
https://www.apache.org/licenses/LICENSE-2.0

Used components (non-exhaustive, as declared in `app/build.gradle.kts`):

- `androidx.core:core-ktx`
- `androidx.lifecycle:lifecycle-runtime-ktx`
- `androidx.activity:activity-compose`
- `androidx.compose.ui:ui` / `ui-graphics` / `ui-tooling-preview`
- `androidx.compose.material3:material3`
- `androidx.compose.material:material-icons-extended`
- `androidx.datastore:datastore-preferences`
- Compose BOM `androidx.compose:compose-bom`

---

## Kotlin & Coroutines

Copyright © JetBrains s.r.o. and Kotlin project contributors

Licensed under the Apache License, Version 2.0.  
https://github.com/JetBrains/kotlin  
https://github.com/Kotlin/kotlinx.coroutines

- Kotlin language and standard library
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`

---

## Coil

Copyright © Coil Contributors

Licensed under the Apache License, Version 2.0.  
https://github.com/coil-kt/coil

- `io.coil-kt:coil-compose`

---

## Build tools

- **Android Gradle Plugin** — Copyright © The Android Open Source Project — Apache 2.0
- **Kotlin Android Plugin** — Copyright © JetBrains s.r.o. — Apache 2.0

---

## Android platform APIs

MiniOS runs on the Android operating system and uses system APIs (MediaStore,
MediaPlayer, VideoView, storage permissions, etc.). Android is a trademark of
Google LLC. The Android Open Source Project components are subject to their
respective licenses (primarily Apache 2.0).

---

## No endorsement

The use of these libraries does not imply endorsement by their authors or
right holders. All trademarks remain the property of their respective owners.

For the full Apache License 2.0 text, see:  
https://www.apache.org/licenses/LICENSE-2.0

---

**MiniOS** © 2026 Elizier Layerti Gungui Dias — see `LICENSE` and `COPYRIGHT.md`.
