---
tags:
  - PIKXplus
  - Tech-Stack
  - Dependencies
  - Architecture
  - Hermes
  - Resume-Protocol
  - Paths
  - TODO
date: 2026-08-07
---

# Overview

## Project Summary

**App:** PIKX+
**Package:** `com.ccko.pikxplus`
**Type:** Android gallery/media viewer and browser

## #Tech-Stack

| Layer | Tech |
|-------|------|
| Language | Kotlin 2.1.0 |
| Build System | Gradle 9.0.0 (Kotlin DSL) |
| Android Gradle Plugin | 8.13.0 |
| Min SDK | 30 (Android 11) |
| Target SDK | 34 (Android 14) |
| Architecture Pattern | MVVM |

## Key #Dependencies

- **UI:** Material 3, ViewPager2, RecyclerView, FastScroll
- **Image/Video:** Coil 2.5.0 (GIF/Video), Media3 ExoPlayer 1.10.1, Glide 4.13.2
- **Camera:** CameraX 1.4.2
- **Architecture:** Lifecycle/ViewModel/LiveData, Coroutines, Navigation
- **Dependency Injection:** Manual (lazy properties)

---
## Project's File Directory and #Architecture.

```
🔄 = pending (working on it.)
❌ = removed/bug/not working as intended.

main.java.com/
│
├── helper/
│   ├── ImageMatrixController.java🔄
│   └── ImageColorAdjuster.java
│   
├── davemorrissey.labs.subscaleview/
│   ├── SubsamplingScaleImageView.java
│   └── (other files.)
│
main.kotlin.com.ccko.pikxplus/
├── MainActivity.kt
│
├── ux/
│   ├── albums/
│   │   ├── AlbumsFrg.kt 🔄
│   │   ├── AlbumsAdpt.kt 🔄
│   │   └── AlbumsVM.kt 🔄
│   │
│   ├── photos/
│   │   ├── PhotosFrg.kt
│   │   ├── PhotosAdpt.kt
│   │   └── PhotosVM.kt
│   │
│   ├── camera/
│   │   └── CamFrg.kt
│   │
│   ├── search/
│   │   └── SearchFrg.kt
│   │
│   ├── settings/
│   │   ├── SetFrg.kt
│   │   ├── PrefKeys.kt
│   │   ├── SetCtrl.kt
│   │   └── SetRepo.kt
│   │
├── shared/
│   ├── data/
│   │   ├── MediaItems.kt
│   │   ├── AlbumInfo.kt
│   │   ├── AnimationCache.kt
│   │   └── MSRepo.kt
│   │
│   ├── utils/
│   │   ├── PermissionHelper.kt
│   │   ├── PulseOrbView.kt
│   │   ├── FloatMenu.kt
│   │   ├── FloatWin.kt
│   │   ├── MediaDeletionHelper.kt 🔄 (not working/bug.)
│   │   └── Constants.kt  // holds PREFS_NAME = default SharedPreferences filename
│   │   
│   ├── MainFrgAdpt.kt
│   ├── SharedVM.kt
│   
└── viewers/
    ├── img/
    │   ├── ImgFrg.kt
    │   ├── VpAdpt.kt
    │   ├── ImgGstHandler.kt
    │   ├── SlideShowCtrl.kt
    │   └── ImgVM.kt
    └── vid/
        ├── VidFrg.kt
        ├── VidGstHandler.kt
        └── VidCtrl.kt
```

**Project's File index and a short Summary for each File:**
[[PIKX+ Project File Index]]
