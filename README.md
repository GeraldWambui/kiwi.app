# 🥝 Kiwi Photos — Private, On-Device AI Gallery for Android

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%201.9%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![AI Engine](https://img.shields.io/badge/ML%20Engine-MobileCLIP--S2%20%2B%20MobileFaceNet-FF6F00?logo=tensorflow&logoColor=white)](https://tensorflow.org)
[![Vector Database](https://img.shields.io/badge/Database-ObjectBox%20Vector%20DB-00B0FF)](https://objectbox.io)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20First-green)](https://github.com)

**Kiwi Photos** is a modern, high-performance, 100% privacy-first photo gallery for Android. It combines offline on-device neural search, facial recognition, computational photography, geographic mapping, duplicate cleaning, and self-hosted cloud backup without sending a single byte of your personal media or biometric data to third-party cloud servers.

---

## 🌟 Core Feature Suite

### 1. 🔍 Multimodal AI Semantic Search
- **Apple MobileCLIP-S2 & OpenAI CLIP ViT-B/32 Engine**:
  - Encodes images and natural language text queries into unified 512-dimensional vector spaces.
  - Enables zero-shot queries such as *"dog jumping in pool"*, *"sunset at the beach"*, *"macro food shot"*, *"whiteboard with notes"*.
- **Sub-Millisecond Vector Search**:
  - Native ObjectBox vector similarity engine capable of scoring 50,000+ images in $< 5\text{ms}$ with flat memory overhead.
- **FTS OCR Search**:
  - Automatically indexes printed and handwritten text on receipts, IDs, whiteboards, and documents via Google ML Kit Text Recognition.

---

### 2. 👥 People & Pets Face Clustering
- **Two-Stage Biometric Pipeline**:
  - **Stage 1 (Detection)**: ML Kit Face Detection extracts high-precision facial bounding boxes and landmarks in $< 15\text{ms}$.
  - **Stage 2 (Recognition)**: MobileFaceNet generates 128-dimensional biometric embeddings.
- **Running Centroid Clustering**:
  - Automatically organizes people into recognized albums with zero cluster drift over massive libraries.
  - Allows custom naming, face merging, and favorite people prioritization.

---

### 3. ✨ Computational Photography Suite
- **iOS/Pixel-Grade "Subject Lift" (Sticker Maker)**:
  - Long-press or tap **"Lift ✨"** on any photo to isolate people, pets, or objects with a glowing shimmer outline.
  - **Share Sticker**: Exports transparent `ARGB_8888` PNGs directly to WhatsApp, Telegram, Discord, and Messages.
  - **Save Cutout**: Stores transparent cutouts under `Pictures/KiwiStickers/`.
- **Studio Portrait Mode & Depth Bokeh**:
  - **Variable Aperture Slider ($f/1.4 \rightarrow f/16$)**: Smoothly blurs background while keeping subjects tack-sharp using multi-pass $O(1)$ stack blur.
  - **Studio Lighting Styles**: *Natural Bokeh*, *Stage Mono (B&W Background)*, *Studio Spotlight*, *Golden Hour Warmth*.
  - **Save Enhanced Copy**: Saves portrait photos directly to `Pictures/KiwiPortraits/`.

---

### 4. 🗺️ Interactive Geographic Photo Map
- **Hardware-Accelerated Map**:
  - MapLibre / OpenStreetMap native spatial clustering.
  - Automatically indexes GPS metadata from EXIF headers into spatial buckets.
  - Allows exploring photos by countries, cities, and landmarks with pinch-to-zoom timeline integration.

---

### 5. 🧹 Smart Storage Cleaner & De-duplication
- **Multi-Category Storage Analysis**:
  - Visual duplicates via Perceptual Hash (PHash) hamming distance.
  - Exact byte-identical duplicates via MD5 checksums.
  - Blurry & low-quality photo detector via Laplacian Variance edge sharpness scoring.
  - Large video files and screenshots de-cluttering.
- **1-Tap Space Reclaim**: Safely moves clutter to trash with instant space reclamation gauges.

---

### 6. 🔒 Biometric Encrypted Secure Vault
- **Military-Grade Security**:
  - Hidden photos and videos encrypted with **AES-256 GCM** in private application sandbox storage.
  - Protected by Android BiometricPrompt (Fingerprint / Face Unlock) + Master PIN.
  - Decoy Vault and Auto-Lock timeout policies on app minimize.

---

### 7. ☁️ Self-Hosted Media Backup & Cloud Sync
- **100% User-Owned Cloud Sync**:
  - **Immich**: Direct REST integration supporting server ping, bulk upload deduplication check, and multipart streaming asset upload.
  - **Nextcloud & Generic WebDAV / NAS**: Compatible with Synology Photos, TrueNAS, QNAP, and ownCloud via `PROPFIND`, `MKCOL`, and streaming `PUT`.
- **Encrypted Credentials**:
  - Server URLs and API tokens stored in Android Keystore `EncryptedSharedPreferences`.
- **Intelligent Background Rules**:
  - Automatic periodic background backup (every 6 hours).
  - Wi-Fi only, Charging only, and Video inclusion rules.

---

### 8. ⚡ Zero-Latency Resumable Background Indexing
- **$O(1)$ Checkpoint Resumption**:
  - Pre-queries indexed IDs via primitive arrays, calculating pending items in $O(1)$ time without memory overhead.
  - Safely checkpoints progress after each batch; resuming after power loss or app kill starts instantly with zero duplicated work.
- **Battery & Thermal Guard**:
  - Neural indexing automatically pauses if battery is below $60\%$ without a charger connected, protecting device battery life.
- **Corrupted File Quarantine**:
  - Isolates unreadable files into placeholder records, preventing infinite retry loops.

---

### 9. 🛡️ Modern Android 14+ System Integration
- **Scoped Storage Batch Trash**:
  - Uses `MediaStore.createTrashRequest` (API 30+) so trashed items sync across all gallery apps at the OS level.
- **Android 14 Partial Photo Access**:
  - Full compatibility with `READ_MEDIA_VISUAL_USER_SELECTED` and `SelectedPhotoAccess`.
- **Resumable Model Downloader**:
  - Uses HTTP `Range: bytes=` offset headers to resume interrupted model downloads seamlessly.

---

## 🏛️ Technical Architecture

Kiwi Photos is built following **Clean Architecture**, **MVI / MVVM**, and **Unidirectional Data Flow (UDF)**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                              Presentation Layer                            │
│  Jetpack Compose • Material 3 • Navigation Compose • Coil Image Loading   │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ UI State / Events
┌─────────────────────────────────────▼──────────────────────────────────────┐
│                               ViewModel Layer                              │
│  PhotoViewModel • SearchViewModel • MediaDetailViewModel • SettingsViewModel│
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ Coroutines / StateFlow
┌─────────────────────────────────────▼──────────────────────────────────────┐
│                             Domain & Logic Layer                           │
│  SubjectSegmenter • PortraitBokehRenderer • OcrScanner • FaceClusterer     │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ Data Access
┌─────────────────────────────────────▼──────────────────────────────────────┐
│                              Data & Storage Layer                          │
│  MediaStore API • ObjectBox Vector DB • EncryptedSharedPreferences • OkHttp│
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 📂 Project Structure

```
app/src/main/java/co/juneai/june/
├── data/
│   ├── local/
│   │   ├── daos/              # Face, OCR, and Album Data Access Objects
│   │   ├── entities/          # ObjectBox Entities (ImageEmbedding, BackupRecord, FaceCluster)
│   │   └── prefs/             # EncryptedSharedPreferences & DataStore Managers
│   ├── model/                 # MediaItem and Core Data Models
│   └── repository/            # MediaStore & Album Repositories
├── domain/
│   ├── model/                 # MediaDetail, FileInfo, UI domain models
│   └── usecase/               # Business logic & Domain Use Cases
├── ml/
│   ├── clip/                  # MobileCLIP-S2 & CLIP TFLite Embedders & Tokenizer
│   ├── face/                  # FaceDetector (ML Kit) & MobileFaceNet Embedder
│   ├── ocr/                   # ML Kit Optical Character Recognition
│   ├── model/                 # ModelManager (Resumable HTTP Range Downloader)
│   ├── portrait/              # Studio Portrait Mode & Depth Bokeh Renderer
│   └── segmentation/          # Subject Lift & Sticker Cutout Segmenter
├── navigation/                # KiwiPhotosNavigation (4-Tab Bottom Nav & Route Hub)
├── network/
│   └── backup/                # SelfHostedClient (Immich REST & WebDAV / Nextcloud)
├── service/
│   ├── IndexImagesWorker.kt   # Resumable Photo Neural Indexing Background Worker
│   ├── IndexVideosWorker.kt   # Resumable Video Semantic Keyframe Indexing Worker
│   └── SelfHostedBackupWorker.kt # Streaming Cloud Backup Background Worker
├── ui/
│   ├── screen/
│   │   ├── albums/            # Collections, Folders, & Smart Albums
│   │   ├── backup/            # Self-Hosted Backup Hub & Connection Tester
│   │   ├── cleaner/           # Storage Cleaner & Duplicate Finder
│   │   ├── foryou/            # Memories, Highlights, & Throwbacks
│   │   ├── map/               # Geographic Photo Map (MapLibre/OSM)
│   │   ├── mediadetail/       # Photo/Video Viewer, Subject Lift & Portrait Sheet
│   │   ├── photos/            # Main Photo Timeline & Top App Bar
│   │   ├── search/            # Multimodal AI Search & People Carousel
│   │   ├── settings/          # 6-Section On-Device AI & Privacy Settings Hub
│   │   └── vault/             # Biometric AES-256 Encrypted Vault
│   └── theme/                 # OLED Dark Mode, Light Mode, & Material 3 Palette
└── utils/                     # BatteryStatusManager, MediaStoreTrashHelper, Formatters
```

---

## 🔒 Permissions Breakdown

| Permission | Minimum API | Purpose |
| :--- | :--- | :--- |
| `READ_MEDIA_IMAGES` | API 33 (Android 13) | Reading photo files from MediaStore |
| `READ_MEDIA_VIDEO` | API 33 (Android 13) | Reading video files from MediaStore |
| `READ_MEDIA_VISUAL_USER_SELECTED` | API 34 (Android 14) | Partial photo library access compliance |
| `READ_EXTERNAL_STORAGE` | API 26-32 | Legacy storage access on older Android devices |
| `POST_NOTIFICATIONS` | API 33 (Android 13) | Foreground indexing & backup progress updates |
| `USE_BIOMETRIC` | API 28+ | Fingerprint & Face unlock for Secure Vault |
| `RECORD_AUDIO` | API 26+ | Voice Search & "Hey Kiwi" natural language parsing |
| `INTERNET` / `ACCESS_NETWORK_STATE` | API 26+ | Self-Hosted Backup (Immich/WebDAV) & Model Download |

---

## 🛠️ Build & Setup

### Prerequisites
- Android Studio Ladybug (2024.2+) or higher
- JDK 17
- Android SDK 34+

### Building the Project
```bash
# Clone the repository
git clone https://github.com/geraldwambui/kiwi.app.git
cd kiwi.app

# Build debug APK
./gradlew assembleDebug

# Run automated unit tests
./gradlew testDebugUnitTest
```

---

## 📄 License
This project is open-source and released under the [Apache License 2.0](LICENSE).
