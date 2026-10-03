<p align="center">
  <img src="app/src/main/res/drawable/ic_kiwi_app.xml" width="128" height="128" alt="Kiwi Photos Logo" />
</p>

<h1 align="center">Kiwi Photos 🥝</h1>

<p align="center">
  <strong>The 100% Private, On-Device AI Photo & Video Gallery for Android</strong>
</p>

<p align="center">
  <a href="https://android.com"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin%201.9%2B-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Language" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose" /></a>
  <a href="https://objectbox.io"><img src="https://img.shields.io/badge/Vector%20DB-ObjectBox-00B0FF?style=flat-square" alt="Vector DB" /></a>
  <a href="https://cloudflare.com"><img src="https://img.shields.io/badge/CDN-Cloudflare%20R2%20%2B%20Workers-F38020?style=flat-square&logo=cloudflare&logoColor=white" alt="Cloudflare" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square" alt="License" /></a>
  <a href="SECURITY.md"><img src="https://img.shields.io/badge/Privacy-100%25%20On--Device-success?style=flat-square" alt="Privacy" /></a>
</p>

---

## 📖 Overview

**Kiwi Photos** is an open-source, high-performance, private photo and video gallery for Android. It brings modern computational photography, multimodal vision-language search, facial recognition, and spatial exploration to your device **without sending a single byte of your personal media, neural embeddings, or biometrics to third-party servers**.

Big Tech cloud galleries scan your personal moments on remote datacenters to build advertising profiles. Kiwi Photos flips that model completely: **all neural network inferences run 100% on your device silicon**, giving you state-of-the-art AI while preserving absolute privacy.

---

## 🌟 Core Feature Suite

### 1. 🔍 Multimodal AI Semantic Vision Search
- **Apple MobileCLIP-S2 Engine**:
  - Encodes images and natural language text into a shared 512-dimensional vector space on `Dispatchers.IO` using 4-thread XNNPACK CPU acceleration.
  - Understands zero-shot visual concepts, attributes, and actions: *"golden retriever in snow"*, *"sunset over mountain peaks"*, *"handwritten cafe receipt"*, *"birthday cake with candles"*.
- **Sub-5ms Vector Similarity Engine**:
  - Embedded **ObjectBox** native C++ vector database scores tens of thousands of media embeddings in $< 5\text{ms}$.
- **Hybrid Keyword & OCR Search**:
  - Google ML Kit Latin Text Recognition (OCR) feeds an on-device Room SQLite **FTS4** full-text search table.
  - Simultaneously matches visual semantics, recognized text, person tags, album names, dates, and camera metadata in a single unified query.

### 2. 👥 People & Pets Facial Recognition
- **Two-Stage Biometric Clustering**:
  - **Stage 1 (Detection)**: High-speed ML Kit Face Detection captures bounding boxes, 3D head rotation angles, and smile metrics in $< 15\text{ms}$.
  - **Stage 2 (Embedding)**: MobileFaceNet generates normalized 128-dimensional biometric vectors.
- **Drift-Free Centroid Clustering**:
  - Clusters faces using prototype centroid cosine matching ($\text{threshold} \ge 0.72$), eliminating cluster drift across massive galleries.
  - People & Pets gallery screen supports custom person naming (✏️), cluster merging (🔀), and favorite contact tagging.

### 3. ✨ Studio Computational Photography
- **iOS/Pixel-Grade "Subject Lift" (Sticker Maker)**:
  - Long-press or tap **"Lift ✨"** on any photo to segment foreground subjects (people, pets, objects) with an animated glowing shimmer outline.
  - **Share As Sticker**: Directly dispatches transparent `ARGB_8888` PNGs to WhatsApp, Telegram, Discord, and Messages via `Intent.ACTION_SEND`.
  - **Save Cutout**: Persists anti-aliased cutouts to `Pictures/KiwiStickers/`.
- **Studio Portrait Depth & Bokeh Renderer**:
  - Applies a fast $O(1)$ multi-pass separable stack blur across background masks.
  - **Variable Aperture Slider ($f/1.4 \rightarrow f/16$)**: Real-time shallow depth-of-field control.
  - **Studio Lighting Styles**: *Natural Bokeh*, *Stage Mono (B&W Background)*, *Studio Spotlight*, *Golden Hour Warmth*.

### 4. 🗺️ Interactive Geospatial Photo Map
- **Hardware-Accelerated Vector Map**:
  - Uses MapLibre / OpenStreetMap native spatial clustering.
  - Indexes GPS coordinates directly from EXIF headers into spatial buckets without network queries.
  - Seamlessly zoom between continents, cities, neighborhoods, and street-level photo clusters.

### 5. 🧹 Intelligent Storage Cleaner & Declutter
- **Multi-Vector Declutter Engine**:
  - **Visual Duplicates**: Perceptual Hash (pHash) hamming distance detection for near-identical shots.
  - **Exact Duplicates**: MD5 byte-level checksum matching.
  - **Blur & Low Quality**: Laplacian Variance edge analysis detects accidental blurry or unfocused photos.
  - **Large Files & Screenshots**: Dedicated filters for oversized video clips and ephemeral screenshots.
- **1-Tap Safe Reclaim**: Moves unwanted media directly to trash with instant space reclamation gauges.

### 6. 🔒 Hardware-Backed Secure Vault
- **AES-256-GCM Encryption**:
  - Private photos and videos are encrypted and stored in isolated sandbox storage.
  - Master keys are generated and protected by the Android KeyStore Hardware Security Module (StrongBox / TEE).
- **Authentication Safeguards**:
  - Unlocked via Android `BiometricPrompt` (Fingerprint / Face Unlock) or PIN.
  - Auto-lock on app minimize, screenshot suppression (`FLAG_SECURE`), and decoy vault mode.

### 7. ☁️ Self-Hosted Cloud Backup (Immich, WebDAV, Nextcloud)
- **100% User-Controlled Sync**:
  - **Immich**: REST client supporting server health ping, bulk asset hash deduplication, and multipart streaming uploads.
  - **Nextcloud & WebDAV / NAS**: Compatible with Synology Photos, TrueNAS, QNAP, and ownCloud via `PROPFIND`, `MKCOL`, and chunked `PUT`.
- **Intelligent Power & Network Guard**:
  - Configurable background backup workers (default: every 6 hours).
  - Enforces Wi-Fi only, Charging only, and optional video sync policies.

### 8. 🌐 Lightweight APK with Cloudflare CDN Asset Pipeline
- **Game-Style On-Demand Loading**:
  - Offloads the large ~396 MB MobileCLIP-S2 vision model to a Cloudflare R2 bucket (`kiwi-models`) fronted by a global Cloudflare Worker.
  - Keeps the base APK ultra-compact (~20 MB for single ABI release builds).
  - On first boot, a sleek game-style asset loader downloads and stitches model chunks with resumable HTTP `Range:` headers and speed indicators (`MB/s`).
- **Resilient Fallbacks**:
  - If skipped or offline, the app operates smoothly without crashing.
  - Settings screen provides a **"Download Vision AI Model"** button and an **"Index Now"** button that bypasses all battery/charging constraints for immediate indexing.

---

## 🏛️ Technical Architecture

Kiwi Photos is designed with strict separation of concerns following **Clean Architecture**, **MVI / MVVM**, and **Unidirectional Data Flow (UDF)**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                             PRESENTATION LAYER                             │
│  Jetpack Compose • Material 3 • Navigation Compose • Coil Image Loading   │
├────────────────────────────────────────────────────────────────────────────┤
│                               VIEWMODEL LAYER                              │
│  PhotoViewModel • SearchViewModel • MediaDetailViewModel • SettingsVM     │
│  Unidirectional Data Flow (UDF) • StateFlow • Coroutine Scopes • Hilt DI   │
├────────────────────────────────────────────────────────────────────────────┤
│                              DOMAIN & ML ENGINE                            │
│  MobileCLIP-S2 (512-d) • MobileFaceNet (128-d) • ML Kit OCR & Faces       │
│  SubjectSegmenter • PortraitBokehRenderer • BatteryStatusManager           │
├────────────────────────────────────────────────────────────────────────────┤
│                                STORAGE LAYER                               │
│  ObjectBox Vector DB • MediaStore API • EncryptedSharedPreferences (AES256)│
│  Room SQLite (FTS4 Keyword Search) • AndroidX WorkManager                  │
└────────────────────────────────────────────────────────────────────────────┘
```

### Key Libraries & Components

| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **Language** | Kotlin 1.9+ | Modern, coroutine-native Android development |
| **UI Framework** | Jetpack Compose + Material 3 | Smooth 60fps declarative UI |
| **Dependency Injection** | Dagger Hilt | `@HiltViewModel`, `@AndroidEntryPoint`, `@Singleton` |
| **Vector Database** | ObjectBox (`io.objectbox`) | Ultra-fast native C++ on-device vector DB |
| **Relational Database** | Room SQLite | FTS4 full-text search index for OCR & tokens |
| **Background Processing**| AndroidX WorkManager | Battery-aware and constraint-resilient workers |
| **Image Loading** | Coil 2.7.0 (`io.coil-kt`) | Coroutine-first image loading with memory LRU cache |
| **Encrypted Storage** | AndroidX Security Crypto | `EncryptedSharedPreferences` backed by Android KeyStore |
| **Vision AI** | MobileCLIP-S2 (TFLite) | 512-dimensional multimodal vision embeddings |
| **Facial AI** | MobileFaceNet (TFLite) | 128-dimensional biometric facial embeddings |
| **Vision Utilities** | Google ML Kit | Latin Text OCR + BlazeFace detection |
| **CDN & Model Hosting** | Cloudflare Workers + R2 | High-speed global edge distribution for model weights |

---

## 📂 Project Structure

```
app/src/main/java/co/juneai/june/
├── data/
│   ├── local/              # Room DAOs, ObjectBox Entities, EncryptedSharedPreferences
│   ├── model/              # MediaItem, SearchFilter, Vault models
│   └── repository/         # MediaStoreRepository, StorageCleanerRepository, MemoriesRepository
├── di/                     # Hilt DI modules (DatabaseModule, ObjectBoxModule, RepositoryModule)
├── domain/
│   ├── intent/             # NLP Search Intent Parser & DateRangeFilters
│   └── model/              # MediaDetail, FileInfo, UI models
├── ml/
│   ├── clip/               # ClipEmbedder.kt (MobileCLIP-S2 & CLIP ViT-B/32) & ClipTokenizer.kt
│   ├── face/               # FaceDetectorAndClusterer.kt & FaceEmbedder.kt
│   ├── model/              # ModelManager.kt (Resumable chunked CDN downloader)
│   ├── nsfw/               # NsfwClassifier.kt (Local content safety filter)
│   ├── ocr/                # OcrScanner.kt (ML Kit Text Recognition)
│   ├── portrait/           # PortraitBokehRenderer.kt (Variable aperture depth & studio lights)
│   ├── quality/            # AestheticsEvaluator.kt (Laplacian variance & quality)
│   ├── segmentation/       # SubjectSegmenter.kt (Subject Lift & transparent PNG stickers)
│   └── video/              # VideoKeyframeExtractor.kt (Scene shift keyframe indexing)
├── navigation/             # Screens.kt, KiwiPhotosNavigation.kt
├── network/backup/         # SelfHostedClient.kt, ImmichClient, WebDavClient
├── service/                # IndexImagesWorker, IndexVideosWorker, SelfHostedBackupWorker
├── ui/screen/              # Compose screens (photos, search, people, albums, backup, etc.)
└── utils/                  # BatteryStatusManager, MediaStoreTrashHelper, GpsExtractor
```

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio Ladybug (2024.2+)** or newer
- **JDK 17** (e.g. OpenJDK 17 or JetBrains Runtime)
- **Android SDK API 34+** installed

### Building from Source

```bash
# 1. Clone the repository
git clone https://github.com/GeraldWambui/kiwi.app.git
cd kiwi.app

# 2. Build the Debug APK
./gradlew assembleDebug

# 3. Run automated tests
./gradlew testDebugUnitTest

# 4. Install onto a connected device or running emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ Self-Hosted Backup Setup

### Connecting to Immich
1. Open Kiwi Photos $\rightarrow$ **Settings** $\rightarrow$ **Self-Hosted Backup**.
2. Select **Immich** as provider.
3. Enter your server URL (e.g., `https://immich.yourdomain.com`).
4. Paste your Immich API Key (generated under *Account Settings $\rightarrow$ API Keys* in the Immich web UI).
5. Tap **Test Connection** $\rightarrow$ **Enable Periodic Backup**.

### Connecting to Nextcloud / WebDAV
1. Select **Nextcloud / WebDAV** as provider.
2. Enter your WebDAV endpoint:
   - Nextcloud: `https://nextcloud.yourdomain.com/remote.php/dav/files/<username>/Photos`
   - Generic WebDAV / NAS: `https://nas.local:5006/volume1/photos`
3. Enter your username and application password.
4. Tap **Test Connection** $\rightarrow$ configure Wi-Fi and charging constraints.

---

## 🛡️ Permissions Transparency

| Permission | Min API | Justification |
| :--- | :--- | :--- |
| `READ_MEDIA_IMAGES` | API 33 | Read and index user photos from device storage |
| `READ_MEDIA_VIDEO` | API 33 | Read and extract video keyframes from device storage |
| `READ_MEDIA_VISUAL_USER_SELECTED` | API 34 | Full compliance with Android 14 partial photo access |
| `READ_EXTERNAL_STORAGE` | API 26-32 | Read photo storage on Android 8.0 through 12 |
| `POST_NOTIFICATIONS` | API 33 | Show background indexing and backup sync progress |
| `USE_BIOMETRIC` | API 28 | Fingerprint and face unlock for Secure Vault |
| `INTERNET` / `ACCESS_NETWORK_STATE` | API 26 | Download vision model assets and sync with user's self-hosted server |

---

## 🤝 Contributing

We welcome contributions from the open-source community! Whether you are fixing bugs, optimizing vector search, improving UI aesthetics, or adding translations:

1. Please read our [**Contributing Guide**](CONTRIBUTING.md) for code standards and workflow.
2. Review our [**Architecture Guide**](docs/ARCHITECTURE.md) for module boundaries and directory structure.
3. Review our [**Code of Conduct**](CODE_OF_CONDUCT.md) to keep our community welcoming.
4. For automated agent context and deep system invariants, see [**AGENTS.md**](AGENTS.md).

---

## 🔒 Security & Privacy Policy

If you discover a security vulnerability or potential privacy leak, please review our [**Security Policy**](SECURITY.md) and report it responsibly via email to **`geraldwainaina25@gmail.com`**.

---

## 📄 License

Kiwi Photos is open-source software licensed under the **[Apache License 2.0](LICENSE)**.

```
Copyright 2026 Gerald Wambui and Kiwi Photos Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
