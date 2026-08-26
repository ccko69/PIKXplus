## Source Code Structure

### `/shared/` — Core Infrastructure
| File                           | Purpose                                                                                   |
| ------------------------------ | ----------------------------------------------------------------------------------------- |
| `MainActivity.kt`              | App entry point, navigation host, global `prefs` (SharedPreferences), permission handling |
| `SharedVM.kt`                  | Shared ViewModel across fragments (media lists, UI state)                                 |
| `MainFrgAdpt.kt`               | ViewPager adapter for main fragments (Albums, Photos, Camera, Settings)                   |
| `Constants.kt`                 | Not Important, should delete later.     |
| `PermissionHelper.kt`          | Runtime permission requests (storage, camera, etc.)                                       |
| `MediaDeletionHelper.kt`       | Safe file deletion with MediaStore                                                        |
| `FloatWin.kt` / `FloatMenu.kt` | Floating window/menu utilities (used in viewers)                                          |
| `ImgMatrixCtrl.kt`             | Image matrix transformations (zoom, pan, rotate)                                          |
| `PulseOrbView.kt`              | Custom animated view                                                                      |
| `AnimationCache.kt`            | Caches animation configs                                                                  |

### `/shared/data/` — Data Layer
| File | Purpose |
|------|---------|
| `MSRepo.kt` | **MediaStore Repository** — Queries images/videos/albums via ContentResolver |
| `MediaItems.kt` | Data classes: `MediaItem` (base), `ImageItem`, `VideoItem`, `AlbumInfo` |
| `AlbumInfo.kt` | Album metadata model |
| `AnimationCache.kt` | Caches animation configs |

### `/viewers/img/` — Image Viewer
| File | Purpose |
|------|---------|
| `ImgFrg.kt` | **Main image viewer fragment** — ViewPager2, slideshow, gestures, floating menu |
| `ImgVM.kt` | ViewModel for image viewer (media list, current position) |
| `VpAdpt.kt` | ViewPager2 adapter for zoomable images (`ImageViewZoomablePage`) |
| `SlideShowCtrl.kt` | **Slideshow engine** — timing, transitions (fade/slide/scale/combined), auto-rotate |
| `ImgGstHandler.kt` | Gesture handling (pinch zoom, double-tap, drag) |

### `/viewers/vid/` — Video Viewer
| File | Purpose |
|------|---------|
| `VidFrg.kt` | Video viewer fragment (ExoPlayer) |
| `VidCtrl.kt` | Video playback controller |
| `VidGstHandler.kt` | Video gesture handling |

### `/ux/photos/` — Photos Grid
| File | Purpose |
|------|---------|
| `PhotosFrg.kt` | Main photos grid fragment (RecyclerView + GridLayoutManager) |
| `PhotosFrgUi.kt` | UI helpers for PhotosFrg |
| `PhotosVM.kt` | ViewModel for photos grid (loading, filtering, selection) |
| `PhotosAdpt.kt` | RecyclerView adapter for photo thumbnails (Coil) |

### `/ux/albums/` — Albums
| File | Purpose |
|------|---------|
| `AlbumsFrg.kt` | Albums list fragment |
| `AlbumsVM.kt` | ViewModel for albums |
| `AlbumsAdpt.kt` | RecyclerView adapter for album thumbnails |

### `/ux/camera/` — Camera
| File | Purpose |
|------|---------|
| `CamFrg.kt` | CameraX fragment (photo/video capture) |

### `/ux/search/` — Search
| File | Purpose |
|------|---------|
| `SearchFrg.kt` | Search fragment (query MediaStore) |

### `/ux/settings/` — Settings (Unified Controller Pattern)
| File | Purpose |
|------|---------|
| `SetFrg.kt` | PreferenceFragmentCompat (XML-based settings screen) |
| `SetRepo.kt` | **Settings Repository** — Exposes `Flow<T>` for each pref key (reactive) |
| `SetCtrl.kt` | **ImgSetDlg** — Unified settings controller for image viewer; binds to `SlideShowCtrl`, hotspots, applies changes reactively via `SetRepo` flows |
| `PrefKeys.kt` | **Constants for all SharedPreferences keys** — Single source of truth |
